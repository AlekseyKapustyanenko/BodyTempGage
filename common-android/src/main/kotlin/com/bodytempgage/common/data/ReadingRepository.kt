package com.bodytempgage.common.data

import com.bodytempgage.core.GaugeReading
import com.bodytempgage.core.MeawowPredictor
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

/** One point of the temperature history shown in the chart. */
data class TempSample(
    val timestampMillis: Long,
    /** Null while the gauge was off the body (no valid estimate). */
    val bodyTempC: Double?,
    val gaugeTempC: Double?,
    /** What the official Meawow app would display for this sample ([MeawowPredictor]). */
    val meawowTempC: Double? = null,
)

/**
 * In-memory hub for decoded advertisements. Fed by [com.bodytempgage.common.ble.BleEngine],
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

    /** Temperature the official Meawow app would display for the latest reading, °C. */
    private val _latestMeawow = MutableStateFlow<Double?>(null)
    val latestMeawow: StateFlow<Double?> = _latestMeawow.asStateFlow()

    /** Stateful (peak-hold + slew limit), so one instance tied to the selected stream. */
    private val meawowPredictor = MeawowPredictor()

    private val _devices = MutableStateFlow<Map<String, DiscoveredGauge>>(emptyMap())
    val devices: StateFlow<Map<String, DiscoveredGauge>> = _devices.asStateFlow()

    /** Rolling temperature history of the selected device, oldest first. */
    private val _history = MutableStateFlow<List<TempSample>>(emptyList())
    val history: StateFlow<List<TempSample>> = _history.asStateFlow()

    fun report(mac: String, name: String?, rssi: Int, reading: GaugeReading) {
        val resolvedName = name ?: _devices.value[mac]?.name
        _devices.value = _devices.value + (
            mac to DiscoveredGauge(
                mac = mac,
                name = resolvedName,
                rssi = rssi,
                lastSeenMillis = reading.timestampMillis,
                lastReading = reading,
            )
            )
        val selected = selectedMac
        if (selected == null || selected.equals(mac, ignoreCase = true)) {
            val meawowTempC = meawowPredictor.predict(
                skinTempC = reading.gaugeTempC,
                outerTempC = reading.ambientTempC,
                timestampMillis = reading.timestampMillis,
                params = meawowParams(resolvedName),
            )
            _latest.value = reading
            _latestRssi.value = rssi
            _latestMeawow.value = meawowTempC
            recordSample(reading.timestampMillis, reading.bodyTempC, reading.gaugeTempC, meawowTempC)
        }
    }

    /** Drop the current readings (e.g. after switching to another device). */
    fun resetLatest() {
        _latest.value = null
        _latestRssi.value = null
        _latestMeawow.value = null
        _history.value = emptyList()
        meawowPredictor.reset()
    }

    /** The Meawow app uses a lighter gradient weight for the MMC-T201-2 model. */
    private fun meawowParams(name: String?): MeawowPredictor.Params =
        if (name?.contains("T201-2") == true) {
            MeawowPredictor.Params.T201_2
        } else {
            MeawowPredictor.Params.T201
        }

    private fun recordSample(
        timestampMillis: Long,
        bodyTempC: Double?,
        gaugeTempC: Double?,
        meawowTempC: Double?,
    ) {
        val samples = _history.value
        val last = samples.lastOrNull()
        if (last != null && timestampMillis - last.timestampMillis < MIN_SAMPLE_INTERVAL_MILLIS) return
        val cutoff = timestampMillis - HISTORY_WINDOW_MILLIS
        _history.value = samples.dropWhile { it.timestampMillis < cutoff } +
            TempSample(timestampMillis, bodyTempC, gaugeTempC, meawowTempC)
    }

    private companion object {
        const val MIN_SAMPLE_INTERVAL_MILLIS = 15_000L
        const val HISTORY_WINDOW_MILLIS = 12 * 60 * 60_000L
    }
}
