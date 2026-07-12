package com.bodytempgage.app

import android.app.Application
import android.content.Context
import com.bodytempgage.common.ble.BleEngine
import com.bodytempgage.common.data.ReadingRepository
import com.bodytempgage.common.data.SettingsRepository
import com.bodytempgage.common.sync.SettingsSync
import com.bodytempgage.app.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val readings = ReadingRepository()
    val bleEngine = BleEngine(context, readings)

    /** Mirrors settings changes to/from the paired Wear OS watch over the Data Layer. */
    val settingsSync = SettingsSync(context, settings)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            settings.flow.collect { readings.selectedMac = it.selectedMac }
        }
        settingsSync.start()
    }
}

class BodyTempGageApp : Application(), com.bodytempgage.common.sync.SettingsSyncProvider {

    lateinit var container: AppContainer
        private set

    override val settingsSync: SettingsSync
        get() = container.settingsSync

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }

    companion object {
        fun container(context: Context): AppContainer =
            (context.applicationContext as BodyTempGageApp).container
    }
}
