package com.bodytempgage.common.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bodytempgage.core.AlertThresholds
import com.bodytempgage.core.DisplayMode
import com.bodytempgage.core.SettingsSnapshot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val selectedMac: String? = null,
    val selectedName: String? = null,
    val displayMode: DisplayMode = DisplayMode.BODY,
    val useFahrenheit: Boolean = false,
    val alertEnabled: Boolean = true,
    /** High alert: fever. */
    val alertHighC: Double = 37.5,
    /** High warning: elevated, below the alert threshold. */
    val warnHighC: Double = 37.0,
    /** Low warning: lowered, above the low alert threshold. */
    val warnLowC: Double = 35.5,
    /** Low alert: hypothermia risk. */
    val alertLowC: Double = 35.0,
    /**
     * Background monitoring is on: the foreground service keeps the BLE scan alive with the app
     * closed. Turning it off stops the scan (and the service) to save battery. Device-local —
     * each device runs its own scan, so this must not follow the user across devices.
     */
    val monitoringEnabled: Boolean = true,
    /**
     * Wear only: minutes without any reading after which background monitoring auto-disables to
     * save battery (the watch stops scanning once the gauge is clearly out of range or off). `0`
     * turns the auto-disable off. Device-local, like [monitoringEnabled].
     */
    val autoDisableMinutes: Int = DEFAULT_AUTO_DISABLE_MINUTES,
    /**
     * The user ticked all first-run consent boxes (app info, disclaimer, privacy policy).
     * Device-local by design: each device shows its own consent screen once.
     */
    val consentAccepted: Boolean = false,
) {
    val thresholds: AlertThresholds
        get() = AlertThresholds(
            alertHighC = alertHighC,
            warnHighC = warnHighC,
            warnLowC = warnLowC,
            alertLowC = alertLowC,
        )

    companion object {
        const val DEFAULT_AUTO_DISABLE_MINUTES = 5
    }
}

class SettingsRepository(private val context: Context) {

    private object Keys {
        val selectedMac = stringPreferencesKey("selected_mac")
        val selectedName = stringPreferencesKey("selected_name")
        val displayMode = stringPreferencesKey("display_mode")
        val useFahrenheit = booleanPreferencesKey("use_fahrenheit")
        val alertEnabled = booleanPreferencesKey("alert_enabled")

        // The high-alert key predates the other thresholds, hence the generic name.
        val alertHighC = doublePreferencesKey("alert_threshold_c")
        val warnHighC = doublePreferencesKey("warn_high_c")
        val warnLowC = doublePreferencesKey("warn_low_c")
        val alertLowC = doublePreferencesKey("alert_low_c")
        val monitoringEnabled = booleanPreferencesKey("monitoring_enabled")
        val autoDisableMinutes = intPreferencesKey("auto_disable_minutes")
        val consentAccepted = booleanPreferencesKey("consent_accepted")
    }

    val flow: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            selectedMac = p[Keys.selectedMac],
            selectedName = p[Keys.selectedName],
            displayMode = p[Keys.displayMode]
                ?.let { runCatching { DisplayMode.valueOf(it) }.getOrNull() }
                ?: DisplayMode.BODY,
            useFahrenheit = p[Keys.useFahrenheit] ?: false,
            alertEnabled = p[Keys.alertEnabled] ?: true,
            alertHighC = p[Keys.alertHighC] ?: 37.5,
            warnHighC = p[Keys.warnHighC] ?: 37.0,
            warnLowC = p[Keys.warnLowC] ?: 35.5,
            alertLowC = p[Keys.alertLowC] ?: 35.0,
            monitoringEnabled = p[Keys.monitoringEnabled] ?: true,
            autoDisableMinutes = p[Keys.autoDisableMinutes] ?: AppSettings.DEFAULT_AUTO_DISABLE_MINUTES,
            consentAccepted = p[Keys.consentAccepted] ?: false,
        )
    }

    suspend fun setSelectedDevice(mac: String, name: String?) {
        context.dataStore.edit {
            it[Keys.selectedMac] = mac
            if (name != null) it[Keys.selectedName] = name else it.remove(Keys.selectedName)
        }
    }

    suspend fun clearSelectedDevice() {
        context.dataStore.edit {
            it.remove(Keys.selectedMac)
            it.remove(Keys.selectedName)
        }
    }

    suspend fun setDisplayMode(mode: DisplayMode) {
        context.dataStore.edit { it[Keys.displayMode] = mode.name }
    }

    suspend fun setUseFahrenheit(value: Boolean) {
        context.dataStore.edit { it[Keys.useFahrenheit] = value }
    }

    suspend fun setAlertEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.alertEnabled] = value }
    }

    suspend fun setAlertHighC(value: Double) {
        context.dataStore.edit { it[Keys.alertHighC] = value }
    }

    suspend fun setWarnHighC(value: Double) {
        context.dataStore.edit { it[Keys.warnHighC] = value }
    }

    suspend fun setWarnLowC(value: Double) {
        context.dataStore.edit { it[Keys.warnLowC] = value }
    }

    suspend fun setAlertLowC(value: Double) {
        context.dataStore.edit { it[Keys.alertLowC] = value }
    }

    suspend fun setAutoDisableMinutes(value: Int) {
        context.dataStore.edit { it[Keys.autoDisableMinutes] = value }
    }

    suspend fun setMonitoringEnabled(value: Boolean) {
        context.dataStore.edit { it[Keys.monitoringEnabled] = value }
    }

    suspend fun setConsentAccepted(value: Boolean) {
        context.dataStore.edit { it[Keys.consentAccepted] = value }
    }

    /**
     * Writes every synced field of [snapshot] in a single transaction (one emission), used when
     * applying a settings change received from the paired device. [SettingsSnapshot.updatedAt] and
     * the device-local flags (`monitoringEnabled`, `autoDisableMinutes`) are intentionally not
     * persisted.
     */
    suspend fun applySyncedSnapshot(snapshot: SettingsSnapshot) {
        context.dataStore.edit { p ->
            snapshot.selectedMac?.let { p[Keys.selectedMac] = it } ?: p.remove(Keys.selectedMac)
            snapshot.selectedName?.let { p[Keys.selectedName] = it } ?: p.remove(Keys.selectedName)
            p[Keys.displayMode] = snapshot.displayMode.name
            p[Keys.useFahrenheit] = snapshot.useFahrenheit
            p[Keys.alertEnabled] = snapshot.alertEnabled
            p[Keys.alertHighC] = snapshot.alertHighC
            p[Keys.warnHighC] = snapshot.warnHighC
            p[Keys.warnLowC] = snapshot.warnLowC
            p[Keys.alertLowC] = snapshot.alertLowC
        }
    }
}
