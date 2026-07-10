package com.bodytempgage.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.bodytempgage.core.DisplayMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "settings")

data class AppSettings(
    val selectedMac: String? = null,
    val selectedName: String? = null,
    val displayMode: DisplayMode = DisplayMode.BODY,
    val useFahrenheit: Boolean = false,
    val alertEnabled: Boolean = true,
    val alertThresholdC: Double = 37.5,
    /** User asked for a GATT connection to the gauge (exact device-computed reading). */
    val gattRequested: Boolean = false,
)

class SettingsRepository(private val context: Context) {

    private object Keys {
        val selectedMac = stringPreferencesKey("selected_mac")
        val selectedName = stringPreferencesKey("selected_name")
        val displayMode = stringPreferencesKey("display_mode")
        val useFahrenheit = booleanPreferencesKey("use_fahrenheit")
        val alertEnabled = booleanPreferencesKey("alert_enabled")
        val alertThresholdC = doublePreferencesKey("alert_threshold_c")
        val gattRequested = booleanPreferencesKey("gatt_requested")
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
            alertThresholdC = p[Keys.alertThresholdC] ?: 37.5,
            gattRequested = p[Keys.gattRequested] ?: false,
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

    suspend fun setAlertThresholdC(value: Double) {
        context.dataStore.edit { it[Keys.alertThresholdC] = value }
    }

    suspend fun setGattRequested(value: Boolean) {
        context.dataStore.edit { it[Keys.gattRequested] = value }
    }
}
