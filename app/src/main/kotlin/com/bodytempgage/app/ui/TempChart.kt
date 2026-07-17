package com.bodytempgage.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.bodytempgage.app.R
import com.bodytempgage.common.data.AppSettings
import com.bodytempgage.common.data.TempSample
import com.bodytempgage.common.TempFormat
import com.bodytempgage.core.DisplayMode
import java.util.Date
import java.util.Locale
import kotlin.math.ceil

/** Break the line instead of bridging gaps where the gauge was out of reach. */
private const val LINE_BREAK_GAP_MILLIS = 10 * 60_000L

/** Stretch the x-axis to at least this span so the first minutes aren't zoomed in. */
private const val MIN_SPAN_MILLIS = 10 * 60_000L

/** Amber used for the warning state — readable on both light and dark surfaces. */
@Composable
fun warningColor(): Color =
    if (isSystemInDarkTheme()) Color(0xFFFFB74D) else Color(0xFF9A6700)

/**
 * Temperature history line chart: body temperature (and the raw gauge reading when
 * available) over time, with dashed warning/alert threshold lines.
 */
@Composable
fun TempChart(
    samples: List<TempSample>,
    settings: AppSettings,
    nowMillis: Long,
    modifier: Modifier = Modifier,
) {
    // Chart only the series picked in the display-mode selector; the Meawow estimate is
    // a body-temperature series, so it follows the body selection.
    val showBody = settings.displayMode != DisplayMode.GAUGE
    val showGauge = settings.displayMode != DisplayMode.BODY
    val bodyCount = if (showBody) samples.count { it.bodyTempC != null } else 0
    val gaugeCount = if (showGauge) samples.count { it.gaugeTempC != null } else 0
    val meawowCount = if (showBody) samples.count { it.meawowTempC != null } else 0

    if (bodyCount < 2 && gaugeCount < 2 && meawowCount < 2) {
        Box(modifier = modifier, contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.chart_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        return
    }
    val drawBody = bodyCount > 0
    val drawGauge = gaugeCount > 0
    val drawMeawow = meawowCount > 0

    val context = LocalContext.current
    val timeFormat = remember { android.text.format.DateFormat.getTimeFormat(context) }
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall
        .copy(color = MaterialTheme.colorScheme.onSurfaceVariant)

    val bodyColor = MaterialTheme.colorScheme.primary
    val gaugeColor = MaterialTheme.colorScheme.tertiary
    val meawowColor = MaterialTheme.colorScheme.secondary
    val alertColor = MaterialTheme.colorScheme.error
    val warnColor = warningColor()
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    val fahrenheit = settings.useFahrenheit
    fun display(tempC: Double): Double =
        if (fahrenheit) TempFormat.celsiusToFahrenheit(tempC) else tempC

    // Threshold lines apply to the body estimate, so hide them with it.
    val thresholds = if (settings.alertEnabled && drawBody) {
        listOf(
            settings.alertHighC to alertColor,
            settings.warnHighC to warnColor,
            settings.warnLowC to warnColor,
            settings.alertLowC to alertColor,
        )
    } else {
        emptyList()
    }

    // Y bounds cover the visible series plus the enabled thresholds, with a little padding.
    val allValues = (if (drawBody) samples.mapNotNull { s -> s.bodyTempC?.let { display(it) } } else emptyList()) +
        (if (drawGauge) samples.mapNotNull { s -> s.gaugeTempC?.let { display(it) } } else emptyList()) +
        (if (drawMeawow) samples.mapNotNull { s -> s.meawowTempC?.let { display(it) } } else emptyList()) +
        thresholds.map { display(it.first) }
    val pad = if (fahrenheit) 0.4 else 0.2
    val yMin = allValues.min() - pad
    val yMax = allValues.max() + pad
    val gridStep = listOf(0.2, 0.5, 1.0, 2.0, 5.0).firstOrNull { (yMax - yMin) / it <= 6.0 } ?: 10.0

    val xEnd = nowMillis
    val xStart = minOf(samples.first().timestampMillis, xEnd - MIN_SPAN_MILLIS)

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            val yLabels = buildList {
                var v = ceil(yMin / gridStep) * gridStep
                while (v <= yMax) {
                    add(v)
                    v += gridStep
                }
            }
            val yLabelLayouts = yLabels.map {
                textMeasurer.measure(
                    AnnotatedString(String.format(Locale.getDefault(), "%.1f°", it)),
                    labelStyle,
                )
            }
            val xLabelHeight = textMeasurer
                .measure(AnnotatedString("00:00"), labelStyle).size.height

            val chartLeft = (yLabelLayouts.maxOfOrNull { it.size.width } ?: 0) + 6.dp.toPx()
            val chartRight = size.width - 2.dp.toPx()
            val chartTop = 2.dp.toPx()
            val chartBottom = size.height - xLabelHeight - 4.dp.toPx()

            fun xPos(t: Long): Float =
                chartLeft + (t - xStart).toFloat() / (xEnd - xStart).toFloat() *
                    (chartRight - chartLeft)

            fun yPos(v: Double): Float =
                chartBottom - ((v - yMin) / (yMax - yMin)).toFloat() * (chartBottom - chartTop)

            // Grid lines and y labels.
            yLabels.forEachIndexed { i, v ->
                val y = yPos(v)
                drawLine(
                    color = gridColor.copy(alpha = 0.5f),
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx(),
                )
                drawText(
                    yLabelLayouts[i],
                    topLeft = Offset(0f, y - yLabelLayouts[i].size.height / 2f),
                )
            }

            // Threshold lines (dashed, status colors).
            val dash = PathEffect.dashPathEffect(floatArrayOf(10f, 8f))
            thresholds.forEach { (tempC, color) ->
                val y = yPos(display(tempC))
                drawLine(
                    color = color.copy(alpha = 0.85f),
                    start = Offset(chartLeft, y),
                    end = Offset(chartRight, y),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = dash,
                )
            }

            // Series lines; gaps in the data break the line.
            fun seriesPath(value: (TempSample) -> Double?): Path {
                val path = Path()
                var prevMillis: Long? = null
                samples.forEach { sample ->
                    val v = value(sample) ?: return@forEach
                    val x = xPos(sample.timestampMillis)
                    val y = yPos(display(v))
                    val prev = prevMillis
                    if (prev == null || sample.timestampMillis - prev > LINE_BREAK_GAP_MILLIS) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                    prevMillis = sample.timestampMillis
                }
                return path
            }

            if (drawGauge) {
                drawPath(
                    path = seriesPath { it.gaugeTempC },
                    color = gaugeColor,
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            if (drawMeawow) {
                drawPath(
                    path = seriesPath { it.meawowTempC },
                    color = meawowColor,
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }
            if (drawBody) {
                drawPath(
                    path = seriesPath { it.bodyTempC },
                    color = bodyColor,
                    style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
                )
            }

            // X labels: window start and now.
            val startLayout = textMeasurer.measure(
                AnnotatedString(timeFormat.format(Date(xStart))),
                labelStyle,
            )
            val endLayout = textMeasurer.measure(
                AnnotatedString(timeFormat.format(Date(xEnd))),
                labelStyle,
            )
            drawText(startLayout, topLeft = Offset(chartLeft, size.height - xLabelHeight))
            drawText(
                endLayout,
                topLeft = Offset(chartRight - endLayout.size.width, size.height - xLabelHeight),
            )
        }

        // With a single series the selector above already names it — no legend needed.
        val legend = buildList {
            if (drawBody) add(bodyColor to stringResource(R.string.mode_body))
            if (drawGauge) add(gaugeColor to stringResource(R.string.mode_gauge))
            if (drawMeawow) add(meawowColor to stringResource(R.string.legend_meawow))
        }
        if (legend.size > 1) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                legend.forEach { (color, label) -> LegendItem(color, label) }
            }
        }
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, CircleShape),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
