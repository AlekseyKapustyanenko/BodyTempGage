package com.bodytempgage.app.service

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import com.bodytempgage.app.BodyTempGageApp
import com.bodytempgage.app.R
import com.bodytempgage.app.ble.BleEngine
import com.bodytempgage.app.ble.GattClient
import com.bodytempgage.app.data.AppSettings
import com.bodytempgage.app.ui.TempFormat
import com.bodytempgage.core.TempEvent
import com.bodytempgage.core.ThresholdMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the BLE scan alive with the app closed, mirrors the latest
 * reading into a persistent notification, and raises warnings/alerts when body temperature
 * crosses the configured high or low thresholds.
 */
class MonitorService : LifecycleService() {

    private val thresholdMonitor = ThresholdMonitor(
        rearmHysteresisC = REARM_HYSTERESIS_C,
        cooldownMillis = ALERT_COOLDOWN_MILLIS,
    )

    override fun onCreate() {
        super.onCreate()
        val container = BodyTempGageApp.container(this)

        ServiceCompat.startForeground(
            this,
            Notifications.STATUS_NOTIFICATION_ID,
            Notifications.statusNotification(this, getString(R.string.notif_waiting)),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        container.bleEngine.start(BleEngine.Client.SERVICE)
        _isRunning.value = true

        val manager = getSystemService(NotificationManager::class.java)

        // Restore the user-requested GATT connection when monitoring starts.
        lifecycleScope.launch {
            val settings = container.settings.flow.first()
            if (settings.gattRequested && settings.selectedMac != null &&
                container.gattClient.state.value == GattClient.State.DISCONNECTED
            ) {
                container.gattClient.connect(settings.selectedMac)
            }
        }

        lifecycleScope.launch {
            combine(
                container.readings.latest,
                container.readings.liveBodyTemp,
                container.settings.flow,
                ::Triple,
            ).collect { (reading, live, settings) ->
                // The gauge's own reading (GATT) wins over the advertisement estimate.
                val liveFresh = live?.takeIf {
                    System.currentTimeMillis() - it.timestampMillis < LIVE_FRESH_MILLIS
                }
                val bodyTempC = liveFresh?.tempC ?: reading?.bodyTempC ?: return@collect

                val bodyText = TempFormat.format(bodyTempC, settings.useFahrenheit)
                val text = if (reading != null) {
                    getString(
                        R.string.notif_status_text,
                        bodyText,
                        TempFormat.format(reading.gaugeTempC, settings.useFahrenheit),
                        reading.batteryPercent,
                    )
                } else {
                    getString(R.string.notif_status_body_only, bodyText)
                }
                manager.notify(
                    Notifications.STATUS_NOTIFICATION_ID,
                    Notifications.statusNotification(this@MonitorService, text),
                )

                if (settings.alertEnabled) {
                    checkThresholds(manager, bodyTempC, settings)
                }
            }
        }

        // Flag stale data in the notification when the gauge disappears.
        lifecycleScope.launch {
            while (true) {
                delay(60_000)
                val last = maxOf(
                    container.readings.latest.value?.timestampMillis ?: 0L,
                    container.readings.liveBodyTemp.value?.timestampMillis ?: 0L,
                )
                if (System.currentTimeMillis() - last > STALE_AFTER_MILLIS) {
                    manager.notify(
                        Notifications.STATUS_NOTIFICATION_ID,
                        Notifications.statusNotification(
                            this@MonitorService,
                            getString(R.string.notif_waiting),
                        ),
                    )
                }
            }
        }
    }

    private fun checkThresholds(
        manager: NotificationManager,
        bodyTempC: Double,
        settings: AppSettings,
    ) {
        val event = thresholdMonitor.check(bodyTempC, settings.thresholds, System.currentTimeMillis())
            ?: return
        val tempText = TempFormat.format(bodyTempC, settings.useFahrenheit)
        when (event) {
            TempEvent.HIGH_ALERT -> manager.notify(
                Notifications.ALERT_NOTIFICATION_ID,
                Notifications.alertNotification(this, getString(R.string.notif_fever_title), tempText),
            )

            TempEvent.LOW_ALERT -> manager.notify(
                Notifications.ALERT_NOTIFICATION_ID,
                Notifications.alertNotification(this, getString(R.string.notif_low_title), tempText),
            )

            TempEvent.HIGH_WARNING -> manager.notify(
                Notifications.WARNING_NOTIFICATION_ID,
                Notifications.warningNotification(this, getString(R.string.notif_warn_high_title), tempText),
            )

            TempEvent.LOW_WARNING -> manager.notify(
                Notifications.WARNING_NOTIFICATION_ID,
                Notifications.warningNotification(this, getString(R.string.notif_warn_low_title), tempText),
            )
        }
    }

    override fun onDestroy() {
        BodyTempGageApp.container(this).bleEngine.stop(BleEngine.Client.SERVICE)
        _isRunning.value = false
        super.onDestroy()
    }

    companion object {
        private const val STALE_AFTER_MILLIS = 5 * 60_000L
        private const val ALERT_COOLDOWN_MILLIS = 5 * 60_000L
        private const val REARM_HYSTERESIS_C = 0.2
        const val LIVE_FRESH_MILLIS = 60_000L

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        fun start(context: Context) {
            ContextCompat.startForegroundService(context, Intent(context, MonitorService::class.java))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, MonitorService::class.java))
        }
    }
}
