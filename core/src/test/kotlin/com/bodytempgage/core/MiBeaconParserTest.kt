package com.bodytempgage.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Test vector reconstructed from the raw advertisement logs in
 * https://github.com/custom-components/ble_monitor/issues/264 (device MAC 00:81:F9:DD:6F:C1):
 * service data 0xFE95 = 7022 db00 63 c16fddf98100 09 0020 05 c80d640d51
 */
class MiBeaconParserTest {

    private val issue264Frame = hex("7022db0063c16fddf9810009002005c80d640d51")

    @Test
    fun `parses frame control fields`() {
        val frame = MiBeaconParser.parse(issue264Frame)!!
        assertEquals(2, frame.version)
        assertEquals(0x00DB, frame.productId)
        assertEquals(0x63, frame.frameCounter)
        assertEquals(false, frame.isEncrypted)
        assertEquals("00:81:F9:DD:6F:C1", frame.mac)
    }

    @Test
    fun `extracts body temperature object`() {
        val frame = MiBeaconParser.parse(issue264Frame)!!
        assertEquals(1, frame.objects.size)
        val obj = frame.objects.single()
        assertEquals(0x2000, obj.id)
        assertTrue(obj.payload.contentEquals(hex("c80d640d51")))
    }

    @Test
    fun `rejects short data`() {
        assertNull(MiBeaconParser.parse(hex("7022db00")))
    }

    @Test
    fun `rejects truncated mac`() {
        assertNull(MiBeaconParser.parse(hex("7022db0063c16f")))
    }

    @Test
    fun `encrypted frame yields no objects`() {
        // same frame with the encryption bit (bit 3) set in frame control
        val encrypted = issue264Frame.copyOf()
        encrypted[0] = (encrypted[0].toInt() or 0x08).toByte()
        val frame = MiBeaconParser.parse(encrypted)!!
        assertTrue(frame.isEncrypted)
        assertTrue(frame.objects.isEmpty())
    }

    @Test
    fun `frame without mac parses objects at correct offset`() {
        // frame control 0x2260: object + capability, no MAC
        val data = hex("6022db006309002005c80d640d51")
        val frame = MiBeaconParser.parse(data)!!
        assertNull(frame.mac)
        assertEquals(0x2000, frame.objects.single().id)
    }

    @Test
    fun `truncated object payload is dropped`() {
        val truncated = issue264Frame.copyOfRange(0, issue264Frame.size - 2)
        val frame = MiBeaconParser.parse(truncated)!!
        assertTrue(frame.objects.isEmpty())
    }

    companion object {
        fun hex(s: String): ByteArray =
            s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
    }
}
