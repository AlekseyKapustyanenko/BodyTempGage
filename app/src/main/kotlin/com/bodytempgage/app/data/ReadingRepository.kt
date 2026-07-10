package com.bodytempgage.app.data

import com.bodytempgage.core.GaugeReading
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** A thermometer seen during scanning. */
data class DiscoveredGauge(
    val mac: String,
    val name: String?,
    val rssi: Int,
    val lastSeenMillis: Long,
    val lastReading: GaugeReading?,
)

/**
 * In-memory hub for decoded advertisements. Fed by [com.bodytempgage.app.ble.BleEngine],
 * observed by both the UI and [com.bodytempgage.app.service.MonitorService].
 */
class ReadingRepository {

    /** MAC of the device the user picked; null means "accept the first thermometer seen". */
    @Volatile
    var selectedMac: String? = null

    private val _latest = MutableStateFlow<GaugeReading?>(null)
    val latest: StateFlow<GaugeReading?> = _latest.asStateFlow()

    private val _latestRssi = MutableStateFlow<Int?>(null)
    val latestRssi: StateFlow<Int?> = _latestRssi.asStateFlow()

    private val _devices = MutableStateFlow<Map<String, DiscoveredGauge>>(emptyMap())
    val devices: StateFlow<Map<String, DiscoveredGauge>> = _devices.asStateFlow()

    fun report(mac: String, name: String?, rssi: Int, reading: GaugeReading) {
        _devices.value = _devices.value + (
            mac to DiscoveredGauge(
                mac = mac,
                name = name ?: _devices.value[mac]?.name,
                rssi = rssi,
                lastSeenMillis = reading.timestampMillis,
                lastReading = reading,
            )
            )
        val selected = selectedMac
        if (selected == null || selected.equals(mac, ignoreCase = true)) {
            _latest.value = reading
            _latestRssi.value = rssi
        }
    }

    /** Drop the current reading (e.g. after switching to another device). */
    fun resetLatest() {
        _latest.value = null
        _latestRssi.value = null
    }
}
