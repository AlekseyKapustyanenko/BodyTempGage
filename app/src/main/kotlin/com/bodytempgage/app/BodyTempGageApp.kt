package com.bodytempgage.app

import android.app.Application
import android.content.Context
import com.bodytempgage.app.ble.BleEngine
import com.bodytempgage.app.ble.GattClient
import com.bodytempgage.app.data.ReadingRepository
import com.bodytempgage.app.data.SettingsRepository
import com.bodytempgage.app.service.Notifications
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class AppContainer(context: Context) {
    val settings = SettingsRepository(context)
    val readings = ReadingRepository()
    val bleEngine = BleEngine(context, readings)
    val gattClient = GattClient(context, readings)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        scope.launch {
            settings.flow.collect { readings.selectedMac = it.selectedMac }
        }
    }
}

class BodyTempGageApp : Application() {

    lateinit var container: AppContainer
        private set

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
