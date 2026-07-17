package com.bodytempgage.core

/**
 * Decoder for the Xiaomi Miaomiaoce MMC-T201-1 / MMC-T201-2 body thermometer.
 *
 * The device broadcasts MiBeacon object 0x2000 with two raw thermistor readings
 * (skin side and environment side) plus a battery byte. Body temperature is not
 * transmitted; it is estimated from both sensors by [MeawowPredictor] — the official
 * Meawow app's algorithm. The predictor is stateful (it tracks each reading stream over
 * time), so it runs where the streams live, not here: the decoder only extracts the raw
 * sensor values and leaves [GaugeReading.bodyTempC] null.
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

    private fun decodeRaw(p: ByteArray, offset: Int, timestampMillis: Long): GaugeReading? {
        if (p.size < offset + 5) return null

        val temp1 = readInt16Le(p, offset) / 100.0
        val temp2 = readInt16Le(p, offset + 2) / 100.0
        val battery = p[offset + 4].toInt() and 0xFF

        if (temp1 !in -40.0..85.0 || temp2 !in -40.0..85.0 || battery > 100) return null

        return GaugeReading(
            gaugeTempC = temp1,
            ambientTempC = temp2,
            bodyTempC = null,
            batteryPercent = battery,
            timestampMillis = timestampMillis,
        )
    }

    private fun readInt16Le(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xFF) or (data[offset + 1].toInt() shl 8)).toShort().toInt()
}
