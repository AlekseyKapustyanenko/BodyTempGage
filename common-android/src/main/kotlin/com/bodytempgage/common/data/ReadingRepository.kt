package com.bodytempgage.common.data

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

/** One point of the temperature history shown in the chart. */
data class TempSample(
    val timestampMillis: Long,
    /** Null while the gauge was off the body (no valid estimate). */
    val bodyTempC: Double?,
    val gaugeTempC: Double?,
)

/**
 * In-memory hub for decoded advertisements and GATT notifications. Fed by
 * [com.bodytempgage.common.ble.BleEngine] and [com.bodytempgage.common.ble.GattClient],
 * observed by both the UI and the foreground monitor service.
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

    /** Rolling temperature history of the selected device, oldest first. */
    private val _history = MutableStateFlow<List<TempSample>>(emptyList())
    val history: StateFlow<List<TempSample>> = _history.asStateFlow()

    fun reportLiveBodyTemp(tempC: Double, timestampMillis: Long) {
        _liveBodyTemp.value = LiveBodyTemp(tempC, timestampMillis)
        recordSample(timestampMillis, tempC, gaugeTempC = _latest.value?.gaugeTempC)
    }

    /** A reading received over GATT — always from the selected (connected) device. */
    fun reportSelected(reading: GaugeReading) {
        _latest.value = reading
        recordSample(reading.timestampMillis, reading.bodyTempC, reading.gaugeTempC)
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
            recordSample(reading.timestampMillis, reading.bodyTempC, reading.gaugeTempC)
        }
    }

    /** Drop the current readings (e.g. after switching to another device). */
    fun resetLatest() {
        _latest.value = null
        _latestRssi.value = null
        _liveBodyTemp.value = null
        _history.value = emptyList()
    }

    private fun recordSample(timestampMillis: Long, bodyTempC: Double?, gaugeTempC: Double?) {
        val samples = _history.value
        val last = samples.lastOrNull()
        if (last != null && timestampMillis - last.timestampMillis < MIN_SAMPLE_INTERVAL_MILLIS) return
        val cutoff = timestampMillis - HISTORY_WINDOW_MILLIS
        _history.value = samples.dropWhile { it.timestampMillis < cutoff } +
            TempSample(timestampMillis, bodyTempC, gaugeTempC)
    }

    private companion object {
        const val MIN_SAMPLE_INTERVAL_MILLIS = 15_000L
        const val HISTORY_WINDOW_MILLIS = 12 * 60 * 60_000L
    }
}
