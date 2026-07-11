package com.bodytempgage.common.sync

import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.core.DisplayMode
import com.bodytempgage.core.SettingsSnapshot
import com.google.android.gms.wearable.DataMap

/** The synced subset of the current settings, stamped with [updatedAt]. */
fun AppSettings.toSnapshot(updatedAt: Long): SettingsSnapshot = SettingsSnapshot(
    selectedMac = selectedMac,
    selectedName = selectedName,
    displayMode = displayMode,
    useFahrenheit = useFahrenheit,
    alertEnabled = alertEnabled,
    alertHighC = alertHighC,
    warnHighC = warnHighC,
    warnLowC = warnLowC,
    alertLowC = alertLowC,
    updatedAt = updatedAt,
)

fun SettingsSnapshot.toDataMap(): DataMap = DataMap().also { dm ->
    // Absent mac/name keys mean "not selected"; putDataItem replaces the whole item, so a
    // cleared device propagates as the missing key.
    selectedMac?.let { dm.putString(SyncContract.KEY_SELECTED_MAC, it) }
    selectedName?.let { dm.putString(SyncContract.KEY_SELECTED_NAME, it) }
    dm.putString(SyncContract.KEY_DISPLAY_MODE, displayMode.name)
    dm.putBoolean(SyncContract.KEY_USE_FAHRENHEIT, useFahrenheit)
    dm.putBoolean(SyncContract.KEY_ALERT_ENABLED, alertEnabled)
    dm.putDouble(SyncContract.KEY_ALERT_HIGH_C, alertHighC)
    dm.putDouble(SyncContract.KEY_WARN_HIGH_C, warnHighC)
    dm.putDouble(SyncContract.KEY_WARN_LOW_C, warnLowC)
    dm.putDouble(SyncContract.KEY_ALERT_LOW_C, alertLowC)
    dm.putLong(SyncContract.KEY_UPDATED_AT, updatedAt)
}

/** Reads a snapshot from a received [DataMap], filling any missing scalar with [fallback]. */
fun DataMap.toSettingsSnapshot(fallback: SettingsSnapshot): SettingsSnapshot {
    val mode = getString(SyncContract.KEY_DISPLAY_MODE)
        ?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() }
        ?: fallback.displayMode
    return SettingsSnapshot(
        selectedMac = getString(SyncContract.KEY_SELECTED_MAC),
        selectedName = getString(SyncContract.KEY_SELECTED_NAME),
        displayMode = mode,
        useFahrenheit = getBoolean(SyncContract.KEY_USE_FAHRENHEIT, fallback.useFahrenheit),
        alertEnabled = getBoolean(SyncContract.KEY_ALERT_ENABLED, fallback.alertEnabled),
        alertHighC = getDouble(SyncContract.KEY_ALERT_HIGH_C, fallback.alertHighC),
        warnHighC = getDouble(SyncContract.KEY_WARN_HIGH_C, fallback.warnHighC),
        warnLowC = getDouble(SyncContract.KEY_WARN_LOW_C, fallback.warnLowC),
        alertLowC = getDouble(SyncContract.KEY_ALERT_LOW_C, fallback.alertLowC),
        updatedAt = getLong(SyncContract.KEY_UPDATED_AT, 0L),
    )
}
