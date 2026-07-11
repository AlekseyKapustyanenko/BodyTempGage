package com.bodytempgage.core

/** The four user-configurable body temperature thresholds, in °C. */
data class AlertThresholds(
    val alertHighC: Double,
    val warnHighC: Double,
    val warnLowC: Double,
    val alertLowC: Double,
)

/** A threshold crossing that should be reported to the user. */
enum class TempEvent {
    HIGH_ALERT,
    HIGH_WARNING,
    LOW_WARNING,
    LOW_ALERT,
}

/**
 * Decides when a body-temperature reading should raise a warning or an alert.
 *
 * Each event fires once when its threshold is crossed and re-arms only after the
 * temperature retreats past the threshold by [rearmHysteresisC], so readings that
 * oscillate around a threshold don't spam notifications. A per-event cooldown
 * additionally spaces out repeats. Entering an alert zone silences the matching
 * warning: a jump straight from normal to high-alert raises one alert, and the
 * way back down through the warning band stays quiet.
 */
class ThresholdMonitor(
    private val rearmHysteresisC: Double = 0.2,
    private val cooldownMillis: Long = 5 * 60_000L,
) {
    private val armed = TempEvent.entries.associateWith { true }.toMutableMap()
    private val lastFiredAtMillis = mutableMapOf<TempEvent, Long>()

    fun check(tempC: Double, thresholds: AlertThresholds, nowMillis: Long): TempEvent? {
        if (tempC <= thresholds.alertHighC - rearmHysteresisC) armed[TempEvent.HIGH_ALERT] = true
        if (tempC <= thresholds.warnHighC - rearmHysteresisC) armed[TempEvent.HIGH_WARNING] = true
        if (tempC >= thresholds.warnLowC + rearmHysteresisC) armed[TempEvent.LOW_WARNING] = true
        if (tempC >= thresholds.alertLowC + rearmHysteresisC) armed[TempEvent.LOW_ALERT] = true

        val event = when {
            tempC >= thresholds.alertHighC -> TempEvent.HIGH_ALERT
            tempC >= thresholds.warnHighC -> TempEvent.HIGH_WARNING
            tempC <= thresholds.alertLowC -> TempEvent.LOW_ALERT
            tempC <= thresholds.warnLowC -> TempEvent.LOW_WARNING
            else -> return null
        }

        when (event) {
            TempEvent.HIGH_ALERT -> armed[TempEvent.HIGH_WARNING] = false
            TempEvent.LOW_ALERT -> armed[TempEvent.LOW_WARNING] = false
            else -> Unit
        }

        if (armed[event] != true) return null
        val lastFired = lastFiredAtMillis[event]
        if (lastFired != null && nowMillis - lastFired < cooldownMillis) return null
        armed[event] = false
        lastFiredAtMillis[event] = nowMillis
        return event
    }
}
