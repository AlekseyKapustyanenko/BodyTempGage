package com.bodytempgage.core

/**
 * Parser for the Xiaomi MiBeacon format: the payload of BLE service data with
 * UUID 0000FE95-0000-1000-8000-00805F9B34FB.
 *
 * Frame layout (little-endian):
 * ```
 * frame control (2) | product id (2) | frame counter (1)
 *   [ MAC (6, reversed) ]           if frame control bit 4
 *   [ capability (1) [ I/O (2) ] ]  if frame control bit 5 (I/O if capability bit 5)
 *   [ objects... ]                  if frame control bit 6
 * object = id (2) | length (1) | payload (length)
 * ```
 */
object MiBeaconParser {

    /** Returns null when [serviceData] is not a structurally valid MiBeacon frame. */
    fun parse(serviceData: ByteArray): MiBeaconFrame? {
        if (serviceData.size < 5) return null

        val frameControl = readUInt16Le(serviceData, 0)
        val productId = readUInt16Le(serviceData, 2)
        val frameCounter = serviceData[4].toInt() and 0xFF

        val version = frameControl ushr 12
        val isEncrypted = frameControl and 0x0008 != 0
        val hasMac = frameControl and 0x0010 != 0
        val hasCapability = frameControl and 0x0020 != 0
        val hasObjects = frameControl and 0x0040 != 0

        var offset = 5

        var mac: String? = null
        if (hasMac) {
            if (serviceData.size < offset + 6) return null
            // MAC is transmitted reversed (LSB first)
            mac = (offset + 5 downTo offset).joinToString(":") { i ->
                String.format("%02X", serviceData[i])
            }
            offset += 6
        }

        if (hasCapability) {
            if (serviceData.size < offset + 1) return null
            val capability = serviceData[offset].toInt() and 0xFF
            offset += 1
            // capability bit 5 signals two extra I/O bytes
            if (capability and 0x20 != 0) offset += 2
        }

        val objects = mutableListOf<MiBeaconObject>()
        if (hasObjects && !isEncrypted) {
            while (offset + 3 <= serviceData.size) {
                val objId = readUInt16Le(serviceData, offset)
                val length = serviceData[offset + 2].toInt() and 0xFF
                offset += 3
                if (offset + length > serviceData.size) break
                objects += MiBeaconObject(objId, serviceData.copyOfRange(offset, offset + length))
                offset += length
            }
        }

        return MiBeaconFrame(
            version = version,
            productId = productId,
            frameCounter = frameCounter,
            isEncrypted = isEncrypted,
            mac = mac,
            objects = objects,
        )
    }

    private fun readUInt16Le(data: ByteArray, offset: Int): Int =
        (data[offset].toInt() and 0xFF) or ((data[offset + 1].toInt() and 0xFF) shl 8)
}
