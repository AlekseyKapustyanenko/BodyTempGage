package com.bodytempgage.core

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class OffBodyMonitorTest {

    @Test
    fun `does not fire before the gauge was ever worn`() {
        val m = OffBodyMonitor(confirmMillis = 60_000L)
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 0L))
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 120_000L))
    }

    @Test
    fun `fires once after the confirmation window`() {
        val m = OffBodyMonitor(confirmMillis = 60_000L)
        m.check(hasBodyEstimate = true, nowMillis = 0L)
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 1_000L))
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 30_000L))
        assertTrue(m.check(hasBodyEstimate = false, nowMillis = 61_000L))
        // Already fired for this session: stays quiet.
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 200_000L))
    }

    @Test
    fun `a body estimate during the window resets it`() {
        val m = OffBodyMonitor(confirmMillis = 60_000L)
        m.check(hasBodyEstimate = true, nowMillis = 0L)
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 30_000L))
        // Brief dip ends: the estimate is back, the window starts over.
        assertFalse(m.check(hasBodyEstimate = true, nowMillis = 50_000L))
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 100_000L))
        assertTrue(m.check(hasBodyEstimate = false, nowMillis = 161_000L))
    }

    @Test
    fun `re-arms after the gauge is worn again`() {
        val m = OffBodyMonitor(confirmMillis = 60_000L)
        m.check(hasBodyEstimate = true, nowMillis = 0L)
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 1_000L))
        assertTrue(m.check(hasBodyEstimate = false, nowMillis = 61_000L))
        m.check(hasBodyEstimate = true, nowMillis = 120_000L)
        assertFalse(m.check(hasBodyEstimate = false, nowMillis = 121_000L))
        assertTrue(m.check(hasBodyEstimate = false, nowMillis = 181_000L))
    }
}
