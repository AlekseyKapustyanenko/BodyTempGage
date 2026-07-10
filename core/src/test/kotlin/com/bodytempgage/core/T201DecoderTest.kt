package com.bodytempgage.core

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class T201DecoderTest {

    private fun frameWithPayload(payload: ByteArray, objId: Int = 0x2000) = MiBeaconFrame(
        version = 2,
        productId = T201Decoder.PRODUCT_ID_T201_1,
        frameCounter = 0,
        isEncrypted = false,
        mac = "00:81:F9:DD:6F:C1",
        objects = listOf(MiBeaconObject(objId, payload)),
    )

    @Test
    fun `decodes issue 264 packet`() {
        // temp1 = 0x0DC8 = 3528 -> 35.28 °C, temp2 = 0x0D64 = 3428 -> 34.28 °C, battery = 0x51 = 81 %
        val payload = MiBeaconParserTest.hex("c80d640d51")
        val reading = T201Decoder.decode(frameWithPayload(payload), timestampMillis = 42L)!!

        assertEquals(35.28, reading.gaugeTempC, 1e-9)
        assertEquals(34.28, reading.ambientTempC, 1e-9)
        assertEquals(81, reading.batteryPercent)
        assertEquals(42L, reading.timestampMillis)
        // expected body temperature from the ble_monitor formula
        assertTrue(
            abs(reading.bodyTempC - 36.888) < 0.02,
            "body temp was ${reading.bodyTempC}",
        )
    }

    @Test
    fun `body temperature stays in a plausible range for wearable use`() {
        // sweep sensor pairs inside the formula's fitted domain:
        // skin 32–37 °C, outer sensor 0.5–1.5 °C below skin
        for (t1x100 in 3200..3700 step 25) {
            for (delta in 50..150 step 50) {
                val t1 = t1x100 / 100.0
                val t2 = (t1x100 - delta) / 100.0
                val body = T201Decoder.bodyTemperature(t1, t2)
                assertTrue(body in 30.0..45.0, "t1=$t1 t2=$t2 -> $body")
            }
        }
    }

    @Test
    fun `ignores other object ids`() {
        val payload = MiBeaconParserTest.hex("c80d640d51")
        assertNull(T201Decoder.decode(frameWithPayload(payload, objId = 0x1004), 0L))
    }

    @Test
    fun `rejects short payload`() {
        assertNull(T201Decoder.decode(frameWithPayload(MiBeaconParserTest.hex("c80d64")), 0L))
    }

    @Test
    fun `rejects implausible sensor values`() {
        // temp1 = 0x7FFF -> 327.67 °C
        assertNull(T201Decoder.decode(frameWithPayload(MiBeaconParserTest.hex("ff7f640d51")), 0L))
    }

    @Test
    fun `end to end from raw service data`() {
        val serviceData = MiBeaconParserTest.hex("7022db0063c16fddf9810009002005c80d640d51")
        val frame = MiBeaconParser.parse(serviceData)
        assertNotNull(frame)
        val reading = T201Decoder.decode(frame, 0L)
        assertNotNull(reading)
        assertEquals(81, reading.batteryPercent)
    }
}
