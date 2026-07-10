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
import com.bodytempgage.app.ui.TempFormat
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Foreground service that keeps the BLE scan alive with the app closed, mirrors the latest
 * reading into a persistent notification, and raises a fever alert when body temperature
 * crosses the configured threshold.
 */
class MonitorService : LifecycleService() {

    private var feverArmed = true
    private var lastAlertAtMillis = 0L

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

        lifecycleScope.launch {
            combine(container.readings.latest, container.settings.flow, ::Pair)
                .collect { (reading, settings) ->
                    if (reading == null) return@collect

                    val bodyText = TempFormat.format(reading.bodyTempC, settings.useFahrenheit)
                    val gaugeText = TempFormat.format(reading.gaugeTempC, settings.useFahrenheit)
                    manager.notify(
                        Notifications.STATUS_NOTIFICATION_ID,
                        Notifications.statusNotification(
                            this@MonitorService,
                            getString(
                                R.string.notif_status_text,
                                bodyText,
                                gaugeText,
                                reading.batteryPercent,
                            ),
                        ),
                    )

                    if (settings.alertEnabled) {
                        checkFever(manager, reading.bodyTempC, settings.alertThresholdC, settings.useFahrenheit)
                    }
                }
        }

        // Flag stale data in the notification when the gauge disappears.
        lifecycleScope.launch {
            while (true) {
                delay(60_000)
                val last = container.readings.latest.value?.timestampMillis ?: 0L
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

    private fun checkFever(
        manager: NotificationManager,
        bodyTempC: Double,
        thresholdC: Double,
        fahrenheit: Boolean,
    ) {
        val now = System.currentTimeMillis()
        if (bodyTempC >= thresholdC) {
            if (feverArmed && now - lastAlertAtMillis > ALERT_COOLDOWN_MILLIS) {
                manager.notify(
                    Notifications.ALERT_NOTIFICATION_ID,
                    Notifications.feverNotification(this, TempFormat.format(bodyTempC, fahrenheit)),
                )
                lastAlertAtMillis = now
                feverArmed = false
            }
        } else if (bodyTempC <= thresholdC - REARM_HYSTERESIS_C) {
            feverArmed = true
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
