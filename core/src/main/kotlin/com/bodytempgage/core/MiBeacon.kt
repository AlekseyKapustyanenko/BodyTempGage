package com.bodytempgage.core

/** A single typed object carried in a MiBeacon advertisement. */
data class MiBeaconObject(
    val id: Int,
    val payload: ByteArray,
) {
    override fun equals(other: Any?): Boolean =
        other is MiBeaconObject && other.id == id && other.payload.contentEquals(payload)

    override fun hashCode(): Int = 31 * id + payload.contentHashCode()
}

/**
 * Parsed Xiaomi MiBeacon frame (service data of UUID 0xFE95).
 *
 * @param mac device MAC in standard "AA:BB:CC:DD:EE:FF" form, if the frame carries one
 */
data class MiBeaconFrame(
    val version: Int,
    val productId: Int,
    val frameCounter: Int,
    val isEncrypted: Boolean,
    val mac: String?,
    val objects: List<MiBeaconObject>,
)
