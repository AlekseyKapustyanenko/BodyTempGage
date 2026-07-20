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
import com.bodytempgage.common.ble.BleEngine
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.common.TempFormat
import com.bodytempgage.core.OffBodyMonitor
import com.bodytempgage.core.TempEvent
import com.bodytempgage.core.ThresholdMonitor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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

    /** Notify about a low gauge battery once per discharge, re-armed on recovery. */
    private var lowBatteryNotified = false

    /** Notify once per wear session when the gauge comes off the skin. */
    private val offBodyMonitor = OffBodyMonitor()

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
            combine(
                container.readings.latest,
                container.settings.flow,
                ::Pair,
            ).collect { (reading, settings) ->
                reading ?: return@collect
                val bodyTempC = reading.bodyTempC
                val text = if (bodyTempC != null) {
                    getString(
                        R.string.notif_status_text,
                        TempFormat.format(bodyTempC, settings.useFahrenheit),
                        TempFormat.format(reading.gaugeTempC, settings.useFahrenheit),
                        reading.batteryPercent,
                    )
                } else {
                    // Gauge visible but off the body: no valid body estimate.
                    getString(
                        R.string.notif_status_gauge_only,
                        TempFormat.format(reading.gaugeTempC, settings.useFahrenheit),
                        reading.batteryPercent,
                    )
                }
                manager.notify(
                    Notifications.STATUS_NOTIFICATION_ID,
                    Notifications.statusNotification(this@MonitorService, text),
                )

                if (settings.alertEnabled && bodyTempC != null) {
                    checkThresholds(manager, bodyTempC, settings)
                }
                checkBattery(manager, reading.batteryPercent)
                checkOffBody(manager, bodyTempC != null, reading.timestampMillis)
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

    private fun checkOffBody(
        manager: NotificationManager,
        hasBodyEstimate: Boolean,
        timestampMillis: Long,
    ) {
        if (offBodyMonitor.check(hasBodyEstimate, timestampMillis)) {
            manager.notify(
                Notifications.NOT_WORN_NOTIFICATION_ID,
                Notifications.notWornNotification(this),
            )
        }
    }

    private fun checkBattery(manager: NotificationManager, batteryPercent: Int) {
        if (batteryPercent < LOW_BATTERY_PERCENT) {
            if (!lowBatteryNotified) {
                lowBatteryNotified = true
                manager.notify(
                    Notifications.BATTERY_NOTIFICATION_ID,
                    Notifications.batteryNotification(this, batteryPercent),
                )
            }
        } else if (batteryPercent >= LOW_BATTERY_REARM_PERCENT) {
            // Hysteresis so a reading flapping around the threshold can't re-notify.
            lowBatteryNotified = false
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
        private const val LOW_BATTERY_PERCENT = 15
        private const val LOW_BATTERY_REARM_PERCENT = 20

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
