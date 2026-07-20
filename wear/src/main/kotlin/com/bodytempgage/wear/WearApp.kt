package com.bodytempgage.wear

import android.app.Application
import android.bluetooth.le.ScanSettings
import android.content.Context
import com.bodytempgage.common.ble.BleEngine
import com.bodytempgage.common.data.HistoryStore
import com.bodytempgage.common.data.ReadingRepository
import com.bodytempgage.common.data.SettingsRepository
import com.bodytempgage.common.sync.SettingsSync
import com.bodytempgage.common.sync.SettingsSyncProvider
import com.bodytempgage.wear.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Dependency container for the watch app. Reuses the shared BLE scan, reading hub, settings
 * store and Data-Layer sync from `:common-android`. There is no GATT client — the watch reads
 * the gauge from BLE advertisements only.
 */
class WearContainer(context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val settings = SettingsRepository(context)
    val readings = ReadingRepository(HistoryStore(context), scope)

    // BALANCED even while the UI is open: scan callbacks arrive on the main looper, and
    // LOW_LATENCY floods the watch's UI thread enough to make scrolling stutter.
    val bleEngine = BleEngine(context, readings, uiScanMode = ScanSettings.SCAN_MODE_BALANCED)

    /** Mirrors settings changes to/from the paired phone over the Data Layer. */
    val settingsSync = SettingsSync(context, settings)

    init {
        scope.launch {
            settings.flow.collect { readings.selectedMac = it.selectedMac }
        }
        settingsSync.start()
    }
}

class WearApp : Application(), SettingsSyncProvider {

    lateinit var container: WearContainer
        private set

    override val settingsSync: SettingsSync
        get() = container.settingsSync

    override fun onCreate() {
        super.onCreate()
        container = WearContainer(this)
        Notifications.createChannels(this)
    }

    companion object {
        fun container(context: Context): WearContainer =
            (context.applicationContext as WearApp).container
    }
}
