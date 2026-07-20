package com.bodytempgage.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BodyTempPredictorTest {

    /** Feed the same sample until the slew limiter settles, return the settled value. */
    private fun BodyTempPredictor.settle(
        skin: Double,
        outer: Double,
        startMillis: Long = 0L,
        params: BodyTempPredictor.Params = BodyTempPredictor.Params.T201,
    ): Double {
        var t = startMillis
        var value = predict(skin, outer, t, params)
        repeat(100) {
            t += 1_000
            val next = predict(skin, outer, t, params)
            if (next == value) return next
            value = next
        }
        return value
    }

    @Test
    fun `passes skin temperature through below the prediction threshold`() {
        val p = BodyTempPredictor()
        // room temperature, gauge not worn; threshold is ≈ 31.3 °C
        assertEquals(26.22, p.predict(26.22, 26.0, 0L), 1e-9)
    }

    @Test
    fun `passes skin temperature through when the outer sensor is invalid`() {
        val p = BodyTempPredictor()
        assertEquals(36.0, p.predict(36.0, 0.0, 0L), 1e-9)
    }

    @Test
    fun `documented worked example converges to about 36_9`() {
        // Skin 35.0, outer 30.0 -> gradient 5.0 clamped to 1.2:
        // 110.98 − 4.864·35 + 0.07764·35² + 0.90594·1.2 ≈ 36.94
        val settled = BodyTempPredictor().settle(35.0, 30.0)
        assertEquals(36.94, settled, 0.01)
    }

    @Test
    fun `second documented worked example converges to about 37_6`() {
        val settled = BodyTempPredictor().settle(36.0, 31.0)
        assertEquals(37.59, settled, 0.01)
    }

    @Test
    fun `output rises at most the slew step per sample`() {
        val p = BodyTempPredictor()
        // First sample: peak-hold primes prev with the skin temp, so the first
        // output can exceed it by at most one step.
        assertEquals(35.3, p.predict(35.0, 30.0, 0L), 1e-9)
        assertEquals(35.6, p.predict(35.0, 30.0, 1_000L), 1e-9)
    }

    @Test
    fun `gradient clamp relaxes after the stable timeout`() {
        val p = BodyTempPredictor()
        val before = p.settle(35.0, 30.0)
        // Same sensors five minutes later: clamp goes 1.2 -> 1.5 °C.
        val after = p.settle(35.0, 30.0, startMillis = 301_000L)
        assertEquals(0.90594 * (1.5 - 1.2), after - before, 0.01)
    }

    @Test
    fun `never displays below the skin temperature`() {
        val settled = BodyTempPredictor().settle(31.4, 31.3)
        assertTrue(settled >= 31.4, "settled=$settled")
    }

    @Test
    fun `stops predicting with hysteresis below the threshold`() {
        val p = BodyTempPredictor()
        p.settle(35.0, 30.0)
        // 31.0 °C is below threshold − 0.2 ≈ 31.12 °C, so the gate opens and the
        // raw skin temperature comes back (falling output is not slew-limited).
        assertEquals(31.0, p.predict(31.0, 28.0, 200_000L), 1e-9)
    }

    @Test
    fun `t201_2 params weight the gradient about half as much`() {
        val t201 = BodyTempPredictor().settle(35.0, 30.0)
        val t2012 = BodyTempPredictor().settle(35.0, 30.0, params = BodyTempPredictor.Params.T201_2)
        assertEquals((0.90594 - 0.45436) * 1.2, t201 - t2012, 0.01)
    }

    @Test
    fun `isPredicting tracks the gate`() {
        val p = BodyTempPredictor()
        p.predict(26.22, 26.0, 0L)
        assertTrue(!p.isPredicting)
        p.predict(35.0, 30.0, 1_000L)
        assertTrue(p.isPredicting)
        p.predict(31.0, 28.0, 2_000L)
        assertTrue(!p.isPredicting)
    }

    @Test
    fun `reset forgets the peak hold`() {
        val p = BodyTempPredictor()
        p.settle(35.0, 30.0)
        p.reset()
        assertEquals(35.3, p.predict(35.0, 30.0, 500_000L), 1e-9)
    }
}
