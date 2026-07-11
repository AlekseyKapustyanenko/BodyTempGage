package com.bodytempgage.common.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import com.bodytempgage.common.data.ReadingRepository
import com.bodytempgage.core.MiBeaconParser
import com.bodytempgage.core.T201Decoder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Owns the BLE scan. The UI and the monitor service register as clients; scanning runs while
 * at least one client is active, in low-latency mode when the UI is among them.
 *
 * The scan filters on Xiaomi MiBeacon service data (0xFE95) — a hardware-level filter, which
 * Android requires for scanning to continue while the screen is off.
 */
class BleEngine(
    private val context: Context,
    private val readings: ReadingRepository,
) {
    enum class Client { UI, SERVICE }

    enum class ScanState { IDLE, SCANNING, BLUETOOTH_OFF, NO_PERMISSION, FAILED }

    private val clients = mutableSetOf<Client>()

    private val _state = MutableStateFlow(ScanState.IDLE)
    val state: StateFlow<ScanState> = _state.asStateFlow()

    private val manager get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    private var scanning = false

    private val callback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            handleResult(result)
        }

        override fun onBatchScanResults(results: List<ScanResult>) {
            results.forEach(::handleResult)
        }

        override fun onScanFailed(errorCode: Int) {
            scanning = false
            _state.value = ScanState.FAILED
        }
    }

    @Synchronized
    fun start(client: Client) {
        clients += client
        restart()
    }

    @Synchronized
    fun stop(client: Client) {
        clients -= client
        restart()
    }

    /** Re-evaluate permissions/adapter state and restart the scan, e.g. after a grant. */
    @Synchronized
    fun refresh() = restart()

    @SuppressLint("MissingPermission")
    private fun restart() {
        val adapter = manager.adapter
        if (scanning) {
            runCatching { adapter?.bluetoothLeScanner?.stopScan(callback) }
            scanning = false
        }
        if (clients.isEmpty()) {
            _state.value = ScanState.IDLE
            return
        }
        if (!hasScanPermission(context)) {
            _state.value = ScanState.NO_PERMISSION
            return
        }
        if (adapter == null || !adapter.isEnabled) {
            _state.value = ScanState.BLUETOOTH_OFF
            return
        }
        val scanner = adapter.bluetoothLeScanner ?: run {
            _state.value = ScanState.BLUETOOTH_OFF
            return
        }

        val withUi = Client.UI in clients
        val filters = listOf(
            ScanFilter.Builder()
                .setServiceData(MIBEACON_UUID, ByteArray(0))
                .build(),
        )
        val settings = ScanSettings.Builder()
            .setScanMode(
                if (withUi) ScanSettings.SCAN_MODE_LOW_LATENCY
                else ScanSettings.SCAN_MODE_BALANCED,
            )
            .build()

        scanner.startScan(filters, settings, callback)
        scanning = true
        _state.value = ScanState.SCANNING
    }

    private fun handleResult(result: ScanResult) {
        val record = result.scanRecord ?: return
        val serviceData = record.getServiceData(MIBEACON_UUID) ?: return
        val frame = MiBeaconParser.parse(serviceData) ?: return
        // Any MiBeacon carrying object 0x2000 is a body thermometer (MMC-T201-1/-2).
        val reading = T201Decoder.decode(frame, System.currentTimeMillis()) ?: return
        readings.report(
            mac = result.device.address,
            name = record.deviceName,
            rssi = result.rssi,
            reading = reading,
        )
    }

    companion object {
        val MIBEACON_UUID: ParcelUuid =
            ParcelUuid.fromString("0000fe95-0000-1000-8000-00805f9b34fb")

        fun scanPermissions(): Array<String> =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
            } else {
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
            }

        fun hasScanPermission(context: Context): Boolean =
            scanPermissions().all {
                ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
            }
    }
}
