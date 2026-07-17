package com.bodytempgage.core

/**
 * Port of the core-temperature predictor from the official Meawow app
 * (`com.miaomiaoce.mmc.owl`, `MMC_Algorithm_xp.predict_v2`) for the MMC-T201 thermometer.
 *
 * The estimate is a second-order polynomial in the skin temperature plus a correction
 * proportional to the skin-to-ambient gradient (a proxy for heat-loss rate):
 *
 * ```
 * core = A + B·Tskin + C·Tskin² + D·clamp(Tskin − Touter, 0, maxDelta) + Offset
 * ```
 *
 * gated to the body-temperature range (prediction engages once the skin sensor reaches
 * `−B/(2C)` ≈ 31.3 °C, below that the raw skin temperature is passed through), floored at
 * the skin temperature, and smoothed by a per-sample rise limit.
 *
 * The predictor is stateful (peak-hold tracker, prediction gate with hysteresis, slew
 * limiter), so keep one instance per reading stream and [reset] it when the stream changes.
 */
class MeawowPredictor {

    /** The five model coefficients. */
    data class Params(
        val a: Double,
        val b: Double,
        val c: Double,
        val d: Double,
        val offset: Double,
    ) {
        companion object {
            /** Hardcoded coefficients the Meawow app uses for the MMC-T201(-1). */
            val T201 = Params(a = 110.98, b = -4.864, c = 0.07764, d = 0.90594, offset = 0.0)

            /** Default coefficients for the MMC-T201-2 (gradient weighted about half as much). */
            val T201_2 = Params(a = 110.98, b = -4.864, c = 0.07764, d = 0.45436, offset = 0.0)
        }
    }

    private var prev = Double.NEGATIVE_INFINITY
    private var predictingSinceMillis: Long? = null

    /** Forget all state (e.g. after switching to another device). */
    fun reset() {
        prev = Double.NEGATIVE_INFINITY
        predictingSinceMillis = null
    }

    /**
     * Feed one sample and get the temperature the Meawow app would display, °C.
     *
     * @param skinTempC       inner (skin-side) sensor temperature
     * @param outerTempC      outer (ambient-side) sensor temperature
     * @param timestampMillis sample time; drives the gradient-clamp relaxation
     */
    fun predict(
        skinTempC: Double,
        outerTempC: Double,
        timestampMillis: Long,
        params: Params = Params.T201,
    ): Double {
        if (prev < skinTempC) prev = skinTempC

        // No valid outer reading: pass the skin temperature straight through.
        if (outerTempC < 0.001) return skinTempC

        // Quadratic vertex: the skin temperature at which prediction switches on.
        val thresholdC = -params.b / (2 * params.c)
        if (predictingSinceMillis == null) {
            if (skinTempC >= thresholdC) predictingSinceMillis = timestampMillis
        } else {
            if (skinTempC <= thresholdC - STOP_HYSTERESIS_C) predictingSinceMillis = null
        }

        val since = predictingSinceMillis
        var result = if (since != null) {
            val elapsedSec = (timestampMillis - since) / 1000
            val maxDelta =
                if (elapsedSec > PREDICTOR_STABLE_TIMEOUT_SEC) MAX_T_DELTA_END_C else MAX_T_DELTA_START_C
            val gradient = (skinTempC - outerTempC).coerceIn(0.0, maxDelta)
            val core = params.a +
                params.b * skinTempC +
                params.c * skinTempC * skinTempC +
                params.d * gradient +
                params.offset
            // Never display below the measured skin temperature.
            maxOf(core, skinTempC)
        } else {
            skinTempC
        }

        // Slew-rate limit: the output rises at most MAX_TEMPERATURE_STEP_C per sample.
        if (result - prev > MAX_TEMPERATURE_STEP_C) result = prev + MAX_TEMPERATURE_STEP_C
        prev = result
        return result
    }

    companion object {
        /** Prediction stops once the skin temp falls this far below the engage threshold. */
        const val STOP_HYSTERESIS_C = 0.2

        /** Gradient clamp during the first [PREDICTOR_STABLE_TIMEOUT_SEC] of prediction. */
        const val MAX_T_DELTA_START_C = 1.2

        /** Gradient clamp after the readings are considered stable. */
        const val MAX_T_DELTA_END_C = 1.5

        const val PREDICTOR_STABLE_TIMEOUT_SEC = 300L

        /** Maximum rise of the displayed value per sample. */
        const val MAX_TEMPERATURE_STEP_C = 0.3
    }
}
