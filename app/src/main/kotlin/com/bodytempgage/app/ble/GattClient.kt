package com.bodytempgage.app.ble

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.os.Build
import com.bodytempgage.app.data.ReadingRepository
import com.bodytempgage.core.HealthThermometer
import java.text.SimpleDateFormat
import java.util.ArrayDeque
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Optional GATT connection to the thermometer.
 *
 * The primary source is the Health Thermometer service (0x1809) Intermediate Temperature
 * (0x2A1E) notifications — the value the official app shows. Because the exact behaviour of
 * the MMC-T201-2 firmware is undocumented, this client also subscribes to every other
 * notify/indicate-capable characteristic (e.g. Xiaomi's custom ebe0ccb0 service) and keeps a
 * human-readable event log, so unknown data streams can be inspected from the Details panel.
 *
 * GATT operations are strictly serialized: Android silently drops a read/write issued while
 * another is in flight, so descriptor writes and reads go through a queue that advances on
 * each callback.
 */
@SuppressLint("MissingPermission") // callers are gated on BleEngine.hasScanPermission
class GattClient(
    private val context: Context,
    private val readings: ReadingRepository,
) {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val _log = MutableStateFlow<List<String>>(emptyList())
    val log: StateFlow<List<String>> = _log.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var gatt: BluetoothGatt? = null
    private var requestedMac: String? = null
    private var retryJob: Job? = null
    private var retryDelayMs = INITIAL_RETRY_MS

    private sealed interface GattOp {
        data class EnableNotifications(val characteristic: BluetoothGattCharacteristic) : GattOp
        data class ReadCharacteristic(val characteristic: BluetoothGattCharacteristic) : GattOp
    }

    private val opQueue = ArrayDeque<GattOp>()
    private var opInFlight = false

    @Synchronized
    fun connect(mac: String) {
        requestedMac = mac
        retryJob?.cancel()
        closeGatt()
        _log.value = emptyList()

        val adapter =
            (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
        if (adapter == null || !adapter.isEnabled || !BleEngine.hasScanPermission(context)) {
            _state.value = State.FAILED
            return
        }
        val device = try {
            adapter.getRemoteDevice(mac)
        } catch (e: IllegalArgumentException) {
            _state.value = State.FAILED
            return
        }
        _state.value = State.CONNECTING
        logEvent("connect $mac")
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @Synchronized
    fun disconnect() {
        requestedMac = null
        retryJob?.cancel()
        closeGatt()
        readings.clearLiveBodyTemp()
        _state.value = State.DISCONNECTED
        logEvent("disconnected by user")
    }

    private fun closeGatt() {
        runCatching { gatt?.close() }
        gatt = null
        synchronized(opQueue) {
            opQueue.clear()
            opInFlight = false
        }
    }

    @Synchronized
    private fun onLinkLost(reason: String) {
        closeGatt()
        readings.clearLiveBodyTemp()
        logEvent("link lost: $reason")
        val mac = requestedMac
        if (mac == null) {
            _state.value = State.DISCONNECTED
            return
        }
        _state.value = State.CONNECTING
        retryJob?.cancel()
        val delayMs = retryDelayMs
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(MAX_RETRY_MS)
        retryJob = scope.launch {
            delay(delayMs)
            synchronized(this@GattClient) {
                if (requestedMac == mac) connect(mac)
            }
        }
    }

    // region operation queue

    private fun enqueueOps(g: BluetoothGatt) {
        synchronized(opQueue) {
            opQueue.clear()
            for (service in g.services) {
                for (ch in service.characteristics) {
                    val props = ch.properties
                    if (props and (BluetoothGattCharacteristic.PROPERTY_NOTIFY or
                            BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                    ) {
                        opQueue.add(GattOp.EnableNotifications(ch))
                    }
                }
            }
            // Readable HTS info: temperature type, measurement interval
            g.getService(HEALTH_THERMOMETER_SERVICE)?.let { hts ->
                for (uuid in listOf(TEMPERATURE_TYPE, MEASUREMENT_INTERVAL)) {
                    hts.getCharacteristic(uuid)?.let { opQueue.add(GattOp.ReadCharacteristic(it)) }
                }
            }
        }
        driveQueue(g)
    }

    private fun driveQueue(g: BluetoothGatt) {
        val op: GattOp
        synchronized(opQueue) {
            if (opInFlight) return
            op = opQueue.poll() ?: run {
                _state.value = State.CONNECTED
                logEvent("setup done, waiting for data…")
                return
            }
            opInFlight = true
        }
        val started = when (op) {
            is GattOp.EnableNotifications -> startEnableNotifications(g, op.characteristic)
            is GattOp.ReadCharacteristic -> g.readCharacteristic(op.characteristic)
        }
        if (!started) {
            synchronized(opQueue) { opInFlight = false }
            driveQueue(g)
        }
    }

    private fun opFinished(g: BluetoothGatt) {
        synchronized(opQueue) { opInFlight = false }
        driveQueue(g)
    }

    private fun startEnableNotifications(
        g: BluetoothGatt,
        ch: BluetoothGattCharacteristic,
    ): Boolean {
        if (!g.setCharacteristicNotification(ch, true)) return false
        val cccd = ch.getDescriptor(CCCD) ?: return false
        val value = if (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY != 0) {
            BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
        } else {
            BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
        }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            g.writeDescriptor(cccd, value) == android.bluetooth.BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            cccd.value = value
            @Suppress("DEPRECATION")
            g.writeDescriptor(cccd)
        }
    }

    // endregion

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    retryDelayMs = INITIAL_RETRY_MS
                    logEvent("connected, discovering services")
                    if (!g.discoverServices()) onLinkLost("discoverServices failed")
                }

                BluetoothProfile.STATE_DISCONNECTED -> onLinkLost("status $status")
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                onLinkLost("discovery status $status")
                return
            }
            logEvent("services: " + g.services.joinToString(" ") { shortUuid(it.uuid) })
            enqueueOps(g)
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            val ch = descriptor.characteristic
            logEvent("subscribe ${shortUuid(ch.uuid)}: ${gattStatus(status)}")
            opFinished(g)
        }

        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleRead(characteristic.uuid, characteristic.value ?: ByteArray(0), status)
                opFinished(g)
            }
        }

        override fun onCharacteristicRead(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
            status: Int,
        ) {
            handleRead(characteristic.uuid, value, status)
            opFinished(g)
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleNotification(characteristic.uuid, value)
        }

        // pre-33; on API 33+ the framework may invoke both overloads, so gate this one
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                handleNotification(characteristic.uuid, characteristic.value ?: return)
            }
        }
    }

    private fun handleRead(uuid: UUID, value: ByteArray, status: Int) {
        logEvent("read ${shortUuid(uuid)} [${gattStatus(status)}]: ${hex(value)}")
    }

    private fun handleNotification(uuid: UUID, value: ByteArray) {
        val parsed = if (uuid == INTERMEDIATE_TEMPERATURE || uuid == TEMPERATURE_MEASUREMENT) {
            HealthThermometer.parseTemperatureMeasurement(value)
        } else {
            null
        }
        if (parsed != null) {
            _state.value = State.CONNECTED
            readings.reportLiveBodyTemp(parsed, System.currentTimeMillis())
            logEvent("notif ${shortUuid(uuid)}: ${hex(value)} → %.2f°C".format(Locale.US, parsed))
        } else {
            logEvent("notif ${shortUuid(uuid)}: ${hex(value)}")
        }
    }

    private fun logEvent(message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale.US).format(Date())
        _log.value = (_log.value + "$time $message").takeLast(LOG_LINES)
    }

    companion object {
        private const val INITIAL_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 30_000L
        private const val LOG_LINES = 30

        val HEALTH_THERMOMETER_SERVICE: UUID =
            UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
        val INTERMEDIATE_TEMPERATURE: UUID =
            UUID.fromString("00002a1e-0000-1000-8000-00805f9b34fb")
        val TEMPERATURE_MEASUREMENT: UUID =
            UUID.fromString("00002a1c-0000-1000-8000-00805f9b34fb")
        val TEMPERATURE_TYPE: UUID =
            UUID.fromString("00002a1d-0000-1000-8000-00805f9b34fb")
        val MEASUREMENT_INTERVAL: UUID =
            UUID.fromString("00002a21-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")

        private val BASE_UUID_SUFFIX = "-0000-1000-8000-00805f9b34fb"

        fun shortUuid(uuid: UUID): String {
            val s = uuid.toString()
            return if (s.endsWith(BASE_UUID_SUFFIX) && s.startsWith("0000")) {
                s.substring(4, 8)
            } else {
                s.substring(0, 8)
            }
        }

        fun hex(bytes: ByteArray): String =
            bytes.joinToString("") { "%02x".format(it) }

        fun gattStatus(status: Int): String =
            if (status == BluetoothGatt.GATT_SUCCESS) "OK" else "err $status"
    }
}
