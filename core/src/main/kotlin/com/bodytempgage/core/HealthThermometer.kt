package com.bodytempgage.core

import kotlin.math.pow

/**
 * Parser for the Bluetooth SIG Health Thermometer measurement payload, as sent by the
 * Intermediate Temperature (0x2A1E) and Temperature Measurement (0x2A1C) characteristics.
 *
 * Layout: flags (1 byte) | IEEE-11073 32-bit FLOAT (4 bytes, little-endian)
 *         [ timestamp (7 bytes) if flags bit 1 ] [ temperature type (1 byte) if flags bit 2 ]
 *
 * The FLOAT is a 24-bit signed mantissa plus an 8-bit signed exponent:
 * value = mantissa * 10^exponent.
 */
object HealthThermometer {

    /**
     * Returns the temperature in °C (converted when the device reports °F),
     * or null for malformed payloads, IEEE special values, or implausible readings.
     */
    fun parseTemperatureMeasurement(data: ByteArray): Double? {
        if (data.size < 5) return null
        val flags = data[0].toInt() and 0xFF
        val fahrenheit = flags and 0x01 != 0

        val raw = (data[1].toInt() and 0xFF) or
            ((data[2].toInt() and 0xFF) shl 8) or
            ((data[3].toInt() and 0xFF) shl 16) or
            ((data[4].toInt() and 0xFF) shl 24)

        val mantissa24 = raw and 0x00FFFFFF
        if (mantissa24 in SPECIAL_VALUES) return null

        val mantissa = (mantissa24 shl 8) shr 8 // sign-extend 24 -> 32 bits
        val exponent = raw shr 24 // arithmetic shift sign-extends

        val value = mantissa * 10.0.pow(exponent)
        val celsius = if (fahrenheit) (value - 32.0) * 5.0 / 9.0 else value
        return if (celsius in -40.0..85.0) celsius else null
    }

    /** IEEE-11073 special mantissas: NaN, +INFINITY, NRes, reserved, -INFINITY. */
    private val SPECIAL_VALUES = setOf(0x007FFFFF, 0x007FFFFE, 0x00800000, 0x00800001, 0x00800002)
}
