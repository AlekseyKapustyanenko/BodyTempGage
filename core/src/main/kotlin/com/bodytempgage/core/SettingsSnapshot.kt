package com.bodytempgage.core

/**
 * The subset of user settings mirrored between the phone and the watch over the Wearable Data
 * Layer. Kept as pure data (no Android types) so the merge policy is unit-testable off-device;
 * the Android sync layer converts between this and a `DataMap`.
 *
 * `gattRequested` is intentionally excluded — holding a GATT connection is a device-local
 * decision that should not follow the user across devices.
 *
 * @param updatedAt wall-clock time (millis) the snapshot was produced; drives last-write-wins.
 */
data class SettingsSnapshot(
    val selectedMac: String?,
    val selectedName: String?,
    val displayMode: DisplayMode,
    val useFahrenheit: Boolean,
    val alertEnabled: Boolean,
    val alertHighC: Double,
    val warnHighC: Double,
    val warnLowC: Double,
    val alertLowC: Double,
    val updatedAt: Long,
) {
    /** True when every synced field *except* [updatedAt] matches [other]. */
    fun sameContentAs(other: SettingsSnapshot): Boolean =
        selectedMac == other.selectedMac &&
            selectedName == other.selectedName &&
            displayMode == other.displayMode &&
            useFahrenheit == other.useFahrenheit &&
            alertEnabled == other.alertEnabled &&
            alertHighC == other.alertHighC &&
            warnHighC == other.warnHighC &&
            warnLowC == other.warnLowC &&
            alertLowC == other.alertLowC
}

/**
 * Pure merge policy for phone ↔ watch settings sync.
 *
 * Both sides publish their local settings to one shared Data Layer item and apply items they
 * receive. These rules keep that convergent and loop-free:
 *  - **publish** only when the local content actually differs from what was last synced, so
 *    applying a remote change never bounces straight back;
 *  - **apply** an incoming snapshot only when it carries different content and is at least as
 *    recent as the current one (last-write-wins by [SettingsSnapshot.updatedAt]; ties resolve in
 *    favour of the incoming snapshot so both devices converge deterministically).
 */
object SettingsSyncPolicy {

    fun shouldPublish(local: SettingsSnapshot, lastSynced: SettingsSnapshot?): Boolean =
        lastSynced == null || !local.sameContentAs(lastSynced)

    fun shouldApply(incoming: SettingsSnapshot, current: SettingsSnapshot): Boolean =
        !incoming.sameContentAs(current) && incoming.updatedAt >= current.updatedAt
}
