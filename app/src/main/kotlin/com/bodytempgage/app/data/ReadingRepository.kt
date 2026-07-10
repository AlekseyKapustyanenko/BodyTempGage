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

/** Body temperature reported by the gauge itself over GATT (matches the official app). */
data class LiveBodyTemp(
    val tempC: Double,
    val timestampMillis: Long,
)

/**
 * In-memory hub for decoded advertisements and GATT notifications. Fed by
 * [com.bodytempgage.app.ble.BleEngine] and [com.bodytempgage.app.ble.GattClient],
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

    private val _liveBodyTemp = MutableStateFlow<LiveBodyTemp?>(null)
    val liveBodyTemp: StateFlow<LiveBodyTemp?> = _liveBodyTemp.asStateFlow()

    fun reportLiveBodyTemp(tempC: Double, timestampMillis: Long) {
        _liveBodyTemp.value = LiveBodyTemp(tempC, timestampMillis)
    }

    fun clearLiveBodyTemp() {
        _liveBodyTemp.value = null
    }

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

    /** Drop the current readings (e.g. after switching to another device). */
    fun resetLatest() {
        _latest.value = null
        _latestRssi.value = null
        _liveBodyTemp.value = null
    }
}
