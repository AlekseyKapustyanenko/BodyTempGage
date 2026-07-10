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
 * Optional GATT connection to the thermometer's Health Thermometer service (0x1809).
 *
 * When connected, the gauge streams its own computed body temperature via Intermediate
 * Temperature (0x2A1E) notifications — the exact value the official Miaomiaoce app shows.
 * Connection is user-initiated (only one central can hold it at a time; while we are
 * connected the official app cannot connect, and vice versa). While a connect is requested,
 * lost links are retried with backoff until [disconnect] is called.
 */
@SuppressLint("MissingPermission") // callers are gated on BleEngine.hasScanPermission
class GattClient(
    private val context: Context,
    private val readings: ReadingRepository,
) {
    enum class State { DISCONNECTED, CONNECTING, CONNECTED, FAILED }

    private val _state = MutableStateFlow(State.DISCONNECTED)
    val state: StateFlow<State> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private var gatt: BluetoothGatt? = null
    private var requestedMac: String? = null
    private var retryJob: Job? = null
    private var retryDelayMs = INITIAL_RETRY_MS

    @Synchronized
    fun connect(mac: String) {
        requestedMac = mac
        retryJob?.cancel()
        closeGatt()

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
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
    }

    @Synchronized
    fun disconnect() {
        requestedMac = null
        retryJob?.cancel()
        closeGatt()
        readings.clearLiveBodyTemp()
        _state.value = State.DISCONNECTED
    }

    private fun closeGatt() {
        runCatching { gatt?.close() }
        gatt = null
    }

    @Synchronized
    private fun onLinkLost() {
        closeGatt()
        readings.clearLiveBodyTemp()
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

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    retryDelayMs = INITIAL_RETRY_MS
                    if (!g.discoverServices()) onLinkLost()
                }

                BluetoothProfile.STATE_DISCONNECTED -> onLinkLost()
            }
        }

        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) {
            val characteristic = g.getService(HEALTH_THERMOMETER_SERVICE)
                ?.getCharacteristic(INTERMEDIATE_TEMPERATURE)
            if (characteristic == null) {
                onLinkLost()
                return
            }
            g.setCharacteristicNotification(characteristic, true)
            val cccd = characteristic.getDescriptor(CCCD) ?: run {
                onLinkLost()
                return
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                g.writeDescriptor(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE)
            } else {
                @Suppress("DEPRECATION")
                cccd.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                @Suppress("DEPRECATION")
                g.writeDescriptor(cccd)
            }
        }

        override fun onDescriptorWrite(
            g: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                _state.value = State.CONNECTED
            } else {
                onLinkLost()
            }
        }

        // API 33+
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            handleMeasurement(value)
        }

        // pre-33; on API 33+ the framework may invoke both overloads, so gate this one
        @Deprecated("Deprecated in Java")
        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            g: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                characteristic.value?.let(::handleMeasurement)
            }
        }
    }

    private fun handleMeasurement(value: ByteArray) {
        val tempC = HealthThermometer.parseTemperatureMeasurement(value) ?: return
        _state.value = State.CONNECTED
        readings.reportLiveBodyTemp(tempC, System.currentTimeMillis())
    }

    companion object {
        private const val INITIAL_RETRY_MS = 5_000L
        private const val MAX_RETRY_MS = 30_000L

        val HEALTH_THERMOMETER_SERVICE: UUID =
            UUID.fromString("00001809-0000-1000-8000-00805f9b34fb")
        val INTERMEDIATE_TEMPERATURE: UUID =
            UUID.fromString("00002a1e-0000-1000-8000-00805f9b34fb")
        val CCCD: UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
    }
}
