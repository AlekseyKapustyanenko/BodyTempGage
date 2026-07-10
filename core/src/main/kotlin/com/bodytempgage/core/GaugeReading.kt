package com.bodytempgage.core

/**
 * One decoded measurement from an MMC-T201 thermometer.
 *
 * @param gaugeTempC   skin-side sensor temperature, °C (the raw "gauge" reading)
 * @param ambientTempC outer (environment-side) sensor temperature, °C
 * @param bodyTempC    estimated core body temperature, °C (dual-heat-flux compensation)
 * @param batteryPercent gauge battery level, 0–100
 */
data class GaugeReading(
    val gaugeTempC: Double,
    val ambientTempC: Double,
    val bodyTempC: Double,
    val batteryPercent: Int,
    val timestampMillis: Long,
)
