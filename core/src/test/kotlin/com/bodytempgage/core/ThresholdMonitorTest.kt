package com.bodytempgage.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ThresholdMonitorTest {

    private val thresholds = AlertThresholds(
        alertHighC = 37.5,
        warnHighC = 37.0,
        warnLowC = 35.5,
        alertLowC = 35.0,
    )

    private fun monitor() = ThresholdMonitor(rearmHysteresisC = 0.2, cooldownMillis = 300_000L)

    @Test
    fun `normal temperature fires nothing`() {
        val m = monitor()
        assertNull(m.check(36.6, thresholds, 0))
        assertNull(m.check(36.9, thresholds, 1_000))
        assertNull(m.check(35.6, thresholds, 2_000))
    }

    @Test
    fun `rising temperature escalates from warning to alert`() {
        val m = monitor()
        assertNull(m.check(36.8, thresholds, 0))
        assertEquals(TempEvent.HIGH_WARNING, m.check(37.1, thresholds, 1_000))
        assertEquals(TempEvent.HIGH_ALERT, m.check(37.6, thresholds, 2_000))
    }

    @Test
    fun `falling temperature escalates from warning to alert`() {
        val m = monitor()
        assertNull(m.check(36.0, thresholds, 0))
        assertEquals(TempEvent.LOW_WARNING, m.check(35.4, thresholds, 1_000))
        assertEquals(TempEvent.LOW_ALERT, m.check(34.9, thresholds, 2_000))
    }

    @Test
    fun `event does not refire while temperature stays past the threshold`() {
        val m = monitor()
        assertEquals(TempEvent.HIGH_ALERT, m.check(38.0, thresholds, 0))
        assertNull(m.check(38.2, thresholds, 400_000))
        assertNull(m.check(37.6, thresholds, 800_000))
    }

    @Test
    fun `event rearms after retreating past the hysteresis band`() {
        val m = monitor()
        assertEquals(TempEvent.HIGH_ALERT, m.check(37.6, thresholds, 0))
        // Back below threshold but within hysteresis: still disarmed.
        assertNull(m.check(37.4, thresholds, 400_000))
        // Past threshold - hysteresis: the alert rearms (the warning stays
        // suppressed on the way down after an alert).
        assertNull(m.check(37.2, thresholds, 500_000))
        assertEquals(TempEvent.HIGH_ALERT, m.check(37.6, thresholds, 700_000))
    }

    @Test
    fun `cooldown blocks a refire even after rearm`() {
        val m = monitor()
        assertEquals(TempEvent.HIGH_ALERT, m.check(37.6, thresholds, 0))
        assertNull(m.check(37.2, thresholds, 60_000)) // rearms (37.2 <= 37.3)
        assertNull(m.check(37.6, thresholds, 120_000)) // within cooldown
        assertEquals(TempEvent.HIGH_ALERT, m.check(37.6, thresholds, 301_000))
    }

    @Test
    fun `jump straight into alert suppresses the warning on the way down`() {
        val m = monitor()
        assertEquals(TempEvent.HIGH_ALERT, m.check(38.5, thresholds, 0))
        // Dropping back through the warning band stays quiet.
        assertNull(m.check(37.2, thresholds, 400_000))
        assertNull(m.check(36.9, thresholds, 500_000))
        // A fresh rise fires the warning again.
        assertNull(m.check(36.7, thresholds, 600_000))
        assertEquals(TempEvent.HIGH_WARNING, m.check(37.1, thresholds, 900_000))
    }

    @Test
    fun `low side mirrors the suppression`() {
        val m = monitor()
        assertEquals(TempEvent.LOW_ALERT, m.check(34.5, thresholds, 0))
        assertNull(m.check(35.3, thresholds, 400_000))
        assertNull(m.check(35.8, thresholds, 500_000))
        assertEquals(TempEvent.LOW_WARNING, m.check(35.4, thresholds, 900_000))
    }

    @Test
    fun `high and low events are independent`() {
        val m = monitor()
        assertEquals(TempEvent.HIGH_WARNING, m.check(37.1, thresholds, 0))
        assertEquals(TempEvent.LOW_WARNING, m.check(35.4, thresholds, 1_000))
        assertEquals(TempEvent.LOW_ALERT, m.check(34.8, thresholds, 2_000))
    }
}
