package com.bodytempgage.common.data

import com.bodytempgage.core.GaugeReading
import com.bodytempgage.core.BodyTempPredictor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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
)

/**
 * In-memory hub for decoded advertisements. Fed by [com.bodytempgage.common.ble.BleEngine],
 * observed by both the UI and the foreground monitor service.
 *
 * With a [historyStore] and [scope] the temperature history additionally persists to disk:
 * loaded once at construction and appended on every recorded sample, so the chart survives
 * process restarts.
 */
class ReadingRepository(
    private val historyStore: HistoryStore? = null,
    private val scope: CoroutineScope? = null,
) {

    /** MAC of the device the user picked; null means "accept the first thermometer seen". */
    @Volatile
    var selectedMac: String? = null

    private val _latest = MutableStateFlow<GaugeReading?>(null)
    val latest: StateFlow<GaugeReading?> = _latest.asStateFlow()

    private val _latestRssi = MutableStateFlow<Int?>(null)
    val latestRssi: StateFlow<Int?> = _latestRssi.asStateFlow()

    /**
     * Raw predictor output for the latest reading of the selected device, °C.
     * Unlike [GaugeReading.bodyTempC] (null while off the body) this always carries the
     * value the algorithm produced — the skin temperature when it is not predicting.
     */
    private val _latestPredicted = MutableStateFlow<Double?>(null)
    val latestPredicted: StateFlow<Double?> = _latestPredicted.asStateFlow()

    /**
     * One predictor per gauge, keyed by MAC: the predictor is stateful (peak-hold, gate,
     * slew limit) and its state belongs to the physical reading stream, so it survives
     * device switching and also serves the picker previews.
     */
    private val predictors = mutableMapOf<String, BodyTempPredictor>()

    private val _devices = MutableStateFlow<Map<String, DiscoveredGauge>>(emptyMap())
    val devices: StateFlow<Map<String, DiscoveredGauge>> = _devices.asStateFlow()

    /** Rolling temperature history of the selected device, oldest first. */
    private val _history = MutableStateFlow<List<TempSample>>(emptyList())
    val history: StateFlow<List<TempSample>> = _history.asStateFlow()

    init {
        if (historyStore != null && scope != null) {
            scope.launch(Dispatchers.IO) {
                val loaded = historyStore.load(System.currentTimeMillis(), HISTORY_WINDOW_MILLIS)
                if (loaded.isNotEmpty()) {
                    // Samples may already be arriving; keep them and prepend the older
                    // persisted ones so the list stays sorted oldest-first.
                    _history.update { current ->
                        val firstLive = current.firstOrNull()?.timestampMillis ?: Long.MAX_VALUE
                        loaded.filter { it.timestampMillis < firstLive } + current
                    }
                }
            }
        }
    }

    fun report(mac: String, name: String?, rssi: Int, reading: GaugeReading) {
        val resolvedName = name ?: _devices.value[mac]?.name

        val predictor = predictors.getOrPut(mac.uppercase()) { BodyTempPredictor() }
        val predictedTempC = predictor.predict(
            skinTempC = reading.gaugeTempC,
            outerTempC = reading.ambientTempC,
            timestampMillis = reading.timestampMillis,
            params = predictorParams(resolvedName),
        )
        // Below the algorithm's engage threshold the gauge is off the body and the
        // predictor just echoes the skin temperature — not a body estimate.
        val enriched = reading.copy(
            bodyTempC = if (predictor.isPredicting) predictedTempC else null,
        )

        _devices.value = _devices.value + (
            mac to DiscoveredGauge(
                mac = mac,
                name = resolvedName,
                rssi = rssi,
                lastSeenMillis = reading.timestampMillis,
                lastReading = enriched,
            )
            )
        val selected = selectedMac
        if (selected == null || selected.equals(mac, ignoreCase = true)) {
            _latest.value = enriched
            _latestRssi.value = rssi
            _latestPredicted.value = predictedTempC
            recordSample(reading.timestampMillis, enriched.bodyTempC, reading.gaugeTempC)
        }
    }

    /** Drop the current readings (e.g. after switching to another device). */
    fun resetLatest() {
        _latest.value = null
        _latestRssi.value = null
        _latestPredicted.value = null
        _history.value = emptyList()
        if (historyStore != null) {
            scope?.launch(Dispatchers.IO) { historyStore.clear() }
        }
    }

    /** The MMC-T201-2 model needs a lighter gradient weight than the T201(-1). */
    private fun predictorParams(name: String?): BodyTempPredictor.Params =
        if (name?.contains("T201-2") == true) {
            BodyTempPredictor.Params.T201_2
        } else {
            BodyTempPredictor.Params.T201
        }

    private fun recordSample(timestampMillis: Long, bodyTempC: Double?, gaugeTempC: Double?) {
        val samples = _history.value
        val last = samples.lastOrNull()
        if (last != null && timestampMillis - last.timestampMillis < MIN_SAMPLE_INTERVAL_MILLIS) return
        val cutoff = timestampMillis - HISTORY_WINDOW_MILLIS
        val sample = TempSample(timestampMillis, bodyTempC, gaugeTempC)
        _history.update { it.dropWhile { s -> s.timestampMillis < cutoff } + sample }
        if (historyStore != null) {
            scope?.launch(Dispatchers.IO) { historyStore.append(sample, _history.value) }
        }
    }

    private companion object {
        const val MIN_SAMPLE_INTERVAL_MILLIS = 15_000L
        const val HISTORY_WINDOW_MILLIS = 12 * 60 * 60_000L
    }
}
