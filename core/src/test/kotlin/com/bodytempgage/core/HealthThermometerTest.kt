package com.bodytempgage.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HealthThermometerTest {

    private fun parse(hex: String) =
        HealthThermometer.parseTemperatureMeasurement(MiBeaconParserTest.hex(hex))

    @Test
    fun `parses celsius value`() {
        // flags 0x00, mantissa 3472 (0x000D90), exponent -2 (0xFE) -> 34.72 °C
        assertEquals(34.72, parse("00900d00fe")!!, 1e-9)
    }

    @Test
    fun `parses fahrenheit and converts`() {
        // flags 0x01, mantissa 986, exponent -1 -> 98.6 °F -> 37.0 °C
        assertEquals(37.0, parse("01da0300ff")!!, 1e-9)
    }

    @Test
    fun `extra timestamp and type bytes are ignored`() {
        // flags 0x06 (timestamp + type present); parser only needs the leading float
        assertEquals(34.72, parse("06900d00fee907010100000001")!!, 1e-9)
    }

    @Test
    fun `negative mantissa`() {
        // mantissa -25 (0xFFFFE7), exponent 0 -> -25 °C
        assertEquals(-25.0, parse("00e7ffff00")!!, 1e-9)
    }

    @Test
    fun `special values return null`() {
        assertNull(parse("00ffff7f00")) // NaN
        assertNull(parse("00feff7f00")) // +INFINITY
        assertNull(parse("0002008000")) // -INFINITY
    }

    @Test
    fun `short payload returns null`() {
        assertNull(parse("00900d00"))
    }

    @Test
    fun `implausible temperature returns null`() {
        // mantissa 3472, exponent 0 -> 3472 °C
        assertNull(parse("00900d0000"))
    }
}
