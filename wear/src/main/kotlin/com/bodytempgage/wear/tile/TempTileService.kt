package com.bodytempgage.wear.tile

import androidx.concurrent.futures.CallbackToFutureAdapter
import androidx.wear.protolayout.ActionBuilders
import androidx.wear.protolayout.ColorBuilders.argb
import androidx.wear.protolayout.DimensionBuilders
import androidx.wear.protolayout.LayoutElementBuilders
import androidx.wear.protolayout.ModifiersBuilders
import androidx.wear.protolayout.ResourceBuilders
import androidx.wear.protolayout.TimelineBuilders
import androidx.wear.protolayout.material.Text
import androidx.wear.protolayout.material.Typography
import androidx.wear.tiles.RequestBuilders
import androidx.wear.tiles.TileBuilders
import androidx.wear.tiles.TileService
import com.bodytempgage.common.TempFormat
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.core.GaugeReading
import com.bodytempgage.wear.R
import com.bodytempgage.wear.WearApp
import com.bodytempgage.wear.ui.MainActivity
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Tile showing the current temperature at a glance. Reads the in-process
 * [com.bodytempgage.common.data.ReadingRepository] (fed by the monitoring service's BLE scan),
 * so it costs nothing extra to keep fresh: the monitor service pokes the tile when the values
 * change, and [FRESHNESS_MILLIS] lets the system re-render it while visible. Tapping the tile
 * opens the app.
 */
class TempTileService : TileService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onTileRequest(
        requestParams: RequestBuilders.TileRequest,
    ): ListenableFuture<TileBuilders.Tile> =
        CallbackToFutureAdapter.getFuture { completer ->
            scope.launch {
                runCatching { tile() }
                    .onSuccess(completer::set)
                    .onFailure(completer::setException)
            }
            "TempTileService.onTileRequest"
        }

    override fun onTileResourcesRequest(
        requestParams: RequestBuilders.ResourcesRequest,
    ): ListenableFuture<ResourceBuilders.Resources> =
        CallbackToFutureAdapter.getFuture { completer ->
            completer.set(ResourceBuilders.Resources.Builder().setVersion(RESOURCES_VERSION).build())
            "TempTileService.onTileResourcesRequest"
        }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun tile(): TileBuilders.Tile {
        val container = WearApp.container(this)
        val settings = container.settings.flow.first()
        // Ignore readings older than the staleness window rather than pinning an outdated value.
        val reading = container.readings.latest.value
            ?.takeIf { System.currentTimeMillis() - it.timestampMillis <= FRESH_MILLIS }

        return TileBuilders.Tile.Builder()
            .setResourcesVersion(RESOURCES_VERSION)
            .setFreshnessIntervalMillis(FRESHNESS_MILLIS)
            .setTileTimeline(TimelineBuilders.Timeline.fromLayoutElement(layout(reading, settings)))
            .build()
    }

    private fun layout(
        reading: GaugeReading?,
        settings: AppSettings,
    ): LayoutElementBuilders.LayoutElement {
        val column = LayoutElementBuilders.Column.Builder()
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)

        val bodyC = reading?.bodyTempC
        when {
            reading == null -> column
                .addContent(text("—", Typography.TYPOGRAPHY_DISPLAY1, COLOR_DEFAULT))
                .addContent(spacer())
                .addContent(text(getString(R.string.tile_no_data), Typography.TYPOGRAPHY_CAPTION1, COLOR_DIM))

            bodyC != null -> column
                .addContent(
                    text(
                        TempFormat.format(bodyC, settings.useFahrenheit),
                        Typography.TYPOGRAPHY_DISPLAY1,
                        bodyColor(bodyC, settings),
                    ),
                )
                .addContent(spacer())
                .addContent(text(getString(R.string.label_body_temp), Typography.TYPOGRAPHY_CAPTION1, COLOR_DIM))
                .addContent(spacer())
                .addContent(
                    text(
                        getString(R.string.battery, reading.batteryPercent),
                        Typography.TYPOGRAPHY_CAPTION2,
                        COLOR_DIM,
                    ),
                )

            // Gauge visible but off the body: show the skin sensor instead.
            else -> column
                .addContent(
                    text(
                        TempFormat.format(reading.gaugeTempC, settings.useFahrenheit),
                        Typography.TYPOGRAPHY_DISPLAY1,
                        COLOR_DEFAULT,
                    ),
                )
                .addContent(spacer())
                .addContent(text(getString(R.string.label_gauge_temp), Typography.TYPOGRAPHY_CAPTION1, COLOR_DIM))
                .addContent(spacer())
                .addContent(
                    text(
                        getString(R.string.battery, reading.batteryPercent),
                        Typography.TYPOGRAPHY_CAPTION2,
                        COLOR_DIM,
                    ),
                )
        }

        return LayoutElementBuilders.Box.Builder()
            .setWidth(DimensionBuilders.expand())
            .setHeight(DimensionBuilders.expand())
            .setHorizontalAlignment(LayoutElementBuilders.HORIZONTAL_ALIGN_CENTER)
            .setVerticalAlignment(LayoutElementBuilders.VERTICAL_ALIGN_CENTER)
            .setModifiers(
                ModifiersBuilders.Modifiers.Builder()
                    .setClickable(
                        ModifiersBuilders.Clickable.Builder()
                            .setId(CLICK_OPEN_APP)
                            .setOnClick(
                                ActionBuilders.LaunchAction.Builder()
                                    .setAndroidActivity(
                                        ActionBuilders.AndroidActivity.Builder()
                                            .setPackageName(packageName)
                                            .setClassName(MainActivity::class.java.name)
                                            .build(),
                                    )
                                    .build(),
                            )
                            .build(),
                    )
                    .build(),
            )
            .addContent(column.build())
            .build()
    }

    private fun text(value: String, typography: Int, color: Int): LayoutElementBuilders.LayoutElement =
        Text.Builder(this, value)
            .setTypography(typography)
            .setColor(argb(color))
            .build()

    private fun spacer(): LayoutElementBuilders.LayoutElement =
        LayoutElementBuilders.Spacer.Builder()
            .setHeight(DimensionBuilders.dp(4f))
            .build()

    /** Mirrors the main screen: error colour in the alert bands, amber in the warning bands. */
    private fun bodyColor(bodyC: Double, settings: AppSettings): Int = when {
        !settings.alertEnabled -> COLOR_DEFAULT
        bodyC >= settings.alertHighC || bodyC <= settings.alertLowC -> COLOR_ALERT
        bodyC >= settings.warnHighC || bodyC <= settings.warnLowC -> COLOR_WARNING
        else -> COLOR_DEFAULT
    }

    companion object {
        private const val RESOURCES_VERSION = "1"
        private const val CLICK_OPEN_APP = "open_app"

        /** Ask the system to re-render roughly every minute while the tile is visible. */
        private const val FRESHNESS_MILLIS = 60_000L

        /** Readings older than this are treated as "no data" (matches the monitor service). */
        private const val FRESH_MILLIS = 5 * 60_000L

        private const val COLOR_DEFAULT = 0xFFFFFFFF.toInt()
        private const val COLOR_DIM = 0xB3FFFFFF.toInt()
        private const val COLOR_ALERT = 0xFFFF5252.toInt()
        private const val COLOR_WARNING = 0xFFFFB300.toInt()
    }
}
