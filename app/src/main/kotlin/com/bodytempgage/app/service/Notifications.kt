package com.bodytempgage.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.bodytempgage.app.R
import com.bodytempgage.app.ui.MainActivity

object Notifications {

    const val CHANNEL_STATUS = "status"
    const val CHANNEL_ALERTS = "alerts"
    const val CHANNEL_WARNINGS = "warnings"

    const val STATUS_NOTIFICATION_ID = 1
    const val ALERT_NOTIFICATION_ID = 2
    const val WARNING_NOTIFICATION_ID = 3
    const val BATTERY_NOTIFICATION_ID = 4

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_STATUS,
                context.getString(R.string.notif_channel_status),
                NotificationManager.IMPORTANCE_LOW,
            ),
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ALERTS,
                context.getString(R.string.notif_channel_alerts),
                NotificationManager.IMPORTANCE_HIGH,
            ).apply {
                enableVibration(true)
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_WARNINGS,
                context.getString(R.string.notif_channel_warnings),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
    }

    fun contentIntent(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    fun statusNotification(context: Context, text: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_STATUS)
            .setSmallIcon(R.drawable.ic_stat_thermometer)
            .setContentTitle(context.getString(R.string.notif_monitoring_title))
            .setContentText(text)
            .setContentIntent(contentIntent(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()

    fun alertNotification(context: Context, title: String, tempText: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_stat_thermometer)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_temp_text, tempText))
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

    /** Shown once when the gauge battery drops below the low threshold. */
    fun batteryNotification(context: Context, batteryPercent: Int): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_stat_thermometer)
            .setContentTitle(context.getString(R.string.notif_battery_title))
            .setContentText(context.getString(R.string.notif_battery_text, batteryPercent))
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()

    fun warningNotification(context: Context, title: String, tempText: String): android.app.Notification =
        NotificationCompat.Builder(context, CHANNEL_WARNINGS)
            .setSmallIcon(R.drawable.ic_stat_thermometer)
            .setContentTitle(title)
            .setContentText(context.getString(R.string.notif_temp_text, tempText))
            .setContentIntent(contentIntent(context))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setAutoCancel(true)
            .build()
}
