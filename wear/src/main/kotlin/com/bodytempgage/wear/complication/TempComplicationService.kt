package com.bodytempgage.wear.complication

import android.app.PendingIntent
import android.content.Intent
import android.graphics.drawable.Icon
import androidx.wear.watchface.complications.data.ComplicationData
import androidx.wear.watchface.complications.data.ComplicationType
import androidx.wear.watchface.complications.data.LongTextComplicationData
import androidx.wear.watchface.complications.data.MonochromaticImage
import androidx.wear.watchface.complications.data.PlainComplicationText
import androidx.wear.watchface.complications.data.ShortTextComplicationData
import androidx.wear.watchface.complications.datasource.ComplicationRequest
import androidx.wear.watchface.complications.datasource.SuspendingComplicationDataSourceService
import com.bodytempgage.common.TempFormat
import com.bodytempgage.core.GaugeReading
import com.bodytempgage.wear.R
import com.bodytempgage.wear.WearApp
import com.bodytempgage.wear.ui.MainActivity
import kotlinx.coroutines.flow.first

/**
 * Watch-face complication ("widget" on the clock screen) showing the current body temperature.
 * Assignable to any SHORT_TEXT slot (the small round ones, e.g. where heart rate sits) or a
 * LONG_TEXT slot, which additionally shows the gauge/skin reading.
 *
 * Like the tile, it reads the in-process ReadingRepository fed by the monitoring service's
 * scan — no scanning of its own. The monitor service pushes an update whenever the values
 * change; UPDATE_PERIOD_SECONDS in the manifest is only the slow fallback.
 */
class TempComplicationService : SuspendingComplicationDataSourceService() {

    override fun getPreviewData(type: ComplicationType): ComplicationData? = when (type) {
        ComplicationType.SHORT_TEXT -> shortText("36.8°C")
        ComplicationType.LONG_TEXT -> longText("36.75°C · 34.60°C")
        else -> null
    }

    override suspend fun onComplicationRequest(request: ComplicationRequest): ComplicationData? {
        val container = WearApp.container(this)
        val settings = container.settings.flow.first()
        val reading = container.readings.latest.value
            ?.takeIf { System.currentTimeMillis() - it.timestampMillis <= FRESH_MILLIS }
        val bodyC = reading?.bodyTempC

        return when (request.complicationType) {
            ComplicationType.SHORT_TEXT -> shortText(
                // 1-decimal format keeps within the short-text length budget.
                bodyC?.let { TempFormat.formatThreshold(it, settings.useFahrenheit) } ?: "—",
            )

            ComplicationType.LONG_TEXT -> longText(longTextFor(reading, bodyC, settings.useFahrenheit))

            else -> null
        }
    }

    private fun longTextFor(reading: GaugeReading?, bodyC: Double?, fahrenheit: Boolean): String =
        when {
            reading == null -> getString(R.string.tile_no_data)
            bodyC != null ->
                TempFormat.format(bodyC, fahrenheit) + " · " +
                    TempFormat.format(reading.gaugeTempC, fahrenheit)
            else ->
                getString(R.string.label_gauge_temp) + " " +
                    TempFormat.format(reading.gaugeTempC, fahrenheit)
        }

    private fun shortText(value: String): ComplicationData =
        ShortTextComplicationData.Builder(
            text = PlainComplicationText.Builder(value).build(),
            contentDescription = PlainComplicationText.Builder(getString(R.string.label_body_temp)).build(),
        )
            .setMonochromaticImage(thermometerIcon())
            .setTapAction(openAppIntent())
            .build()

    private fun longText(value: String): ComplicationData =
        LongTextComplicationData.Builder(
            text = PlainComplicationText.Builder(value).build(),
            contentDescription = PlainComplicationText.Builder(getString(R.string.label_body_temp)).build(),
        )
            .setTitle(PlainComplicationText.Builder(getString(R.string.label_body_temp)).build())
            .setMonochromaticImage(thermometerIcon())
            .setTapAction(openAppIntent())
            .build()

    private fun thermometerIcon(): MonochromaticImage =
        MonochromaticImage.Builder(
            Icon.createWithResource(this, R.drawable.ic_stat_thermometer),
        ).build()

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    companion object {
        /** Readings older than this are treated as "no data" (matches tile and monitor). */
        private const val FRESH_MILLIS = 5 * 60_000L
    }
}
