package com.bodytempgage.core

import kotlin.math.exp

/**
 * Decoder for the Xiaomi Miaomiaoce MMC-T201-1 / MMC-T201-2 body thermometer.
 *
 * The device broadcasts MiBeacon object 0x2000 with two raw thermistor readings
 * (skin side and environment side) plus a battery byte. Body temperature is not
 * transmitted; it is estimated from both sensors with the empirical fit derived in
 * https://github.com/custom-components/ble_monitor/issues/264 (the same formula
 * Home Assistant's ble_monitor ships).
 */
object T201Decoder {

    const val OBJECT_BODY_TEMPERATURE = 0x2000

    /** MiBeacon product id of the MMC-T201-1 (the T201-2 broadcasts the same object). */
    const val PRODUCT_ID_T201_1 = 0x00DB

    const val DEVICE_NAME_PREFIX = "MMC-T201"

    /** Returns null when the frame carries no valid 0x2000 object. */
    fun decode(frame: MiBeaconFrame, timestampMillis: Long): GaugeReading? {
        val obj = frame.objects.firstOrNull { it.id == OBJECT_BODY_TEMPERATURE } ?: return null
        return decodeRaw(obj.payload, 0, timestampMillis)
    }

    /**
     * Decodes the MMC-T201-2's GATT notification payload (Intermediate Temperature, 0x2A1E).
     *
     * The firmware does not follow the Health Thermometer spec: instead of an IEEE-11073
     * float it streams `status (1) | int16 temp1 | int16 temp2 | uint8 battery` about every
     * 2 seconds — the same raw sensors as the advertisement, just faster. Observed on real
     * hardware, e.g. `00 840d 5c0d 61` = 34.60 °C / 34.20 °C / 97 %.
     */
    fun decodeGattPayload(payload: ByteArray, timestampMillis: Long): GaugeReading? {
        if (payload.size < 6) return null
        return decodeRaw(payload, 1, timestampMillis)
    }

    private fun decodeRaw(p: ByteArray, offset: Int, timestampMillis: Long): GaugeReading? {
        if (p.size < offset + 5) return null

        val temp1 = readInt16Le(p, offset) / 100.0
        val temp2 = readInt16Le(p, offset + 2) / 100.0
        val battery = p[offset + 4].toInt() and 0xFF

        if (temp1 !in -40.0..85.0 || temp2 !in -40.0..85.0 || battery > 100) return null

        return GaugeReading(
            gaugeTempC = temp1,
            ambientTempC = temp2,
            bodyTempC = bodyTemperature(temp1, temp2),
            batteryPercent = battery,
            timestampMillis = timestampMillis,
        )
    }

    /**
     * Dual-heat-flux body temperature estimate from the skin-side ([temp1]) and
     * environment-side ([temp2]) sensor temperatures, °C.
     */
    fun bodyTemperature(temp1: Double, temp2: Double): Double =
        3.71934e-11 * exp(0.69314 * temp1) -
            1.02801e-8 * exp(0.53871 * temp2) +
            36.413

    private fun readInt16Le(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)).toShort().toInt()
}
