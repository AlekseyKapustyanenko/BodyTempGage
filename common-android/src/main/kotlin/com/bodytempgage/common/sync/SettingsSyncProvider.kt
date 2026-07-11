package com.bodytempgage.common.sync

/**
 * Implemented by each app's `Application` so [SettingsSyncService] can reach the single
 * [SettingsSync] instance (and its loop-guard state) that the app already runs.
 */
interface SettingsSyncProvider {
    val settingsSync: SettingsSync
}
