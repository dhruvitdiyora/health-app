package dev.healthapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.roundToInt

enum class MarkKind { BAR, LINE, DOTS }

/**
 * One series over the chart's days. [values] is index-aligned with the days; null means no data.
 * [connectGaps] joins a line across missing days (sparse data such as weigh-ins).
 */
data class ChartSeries(
    val label: String,
    val color: Color,
    val values: List<Double?>,
    val kind: MarkKind,
    val connectGaps: Boolean = false,
)

data class Reference(val label: String, val value: Double)

/**
 * Daily time-series chart with one y-axis. Bars grow from zero; a chart of only lines and dots
 * fits its axis to the data. Tap or drag to move a crosshair; the readout above lists every series.
 */
@Composable
fun TimeChart(
    days: List<LocalDate>,
    series: List<ChartSeries>,
    format: (Double) -> String,
    modifier: Modifier = Modifier,
    unit: String = "",
    reference: Reference? = null,
    height: Dp = 168.dp,
    summary: String? = null,
    tickFormat: (Double) -> String = ::compact,
) {
    val viz = LocalViz.current
    val ink = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    val measurer = rememberTextMeasurer()
    var selected by remember(days.size, days.firstOrNull()) { mutableStateOf<Int?>(null) }

    val all = series.flatMap { it.values.filterNotNull() } + listOfNotNull(reference?.value)
    val hasBars = series.any { it.kind == MarkKind.BAR }
    val axis = remember(all, hasBars) { niceAxis(all, zeroBased = hasBars) }

    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        // Readout: values lead, labels follow; a short line key carries identity.
        Readout(days, series, selected, format, unit, summary)

        val tickStyle = TextStyle(fontSize = 10.sp, color = viz.muted)
        val leftPad = 36.dp
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(height)
                .pointerInput(days.size) {
                    detectTapGestures { o -> selected = indexAt(o.x, size.width.toFloat(), leftPad.toPx(), days.size).takeIf { it != selected } }
                }
                .pointerInput(days.size) {
                    detectHorizontalDragGestures(
                        onDragEnd = {},
                        onHorizontalDrag = { change, _ -> selected = indexAt(change.position.x, size.width.toFloat(), leftPad.toPx(), days.size) },
                    )
                },
        ) {
            if (days.isEmpty()) return@Canvas
            val left = leftPad.toPx()
            val bottomPad = 18.dp.toPx()
            val top = 6.dp.toPx()
            val plotW = size.width - left
            val plotH = size.height - bottomPad - top
            val slot = plotW / days.size
            fun x(i: Int) = left + slot * (i + 0.5f)
            fun y(v: Double) = top + plotH * (1 - ((v - axis.min) / (axis.max - axis.min))).toFloat()

            // Gridlines and y ticks: hairline, solid, recessive.
            axis.ticks.forEach { t ->
                val yy = y(t)
                drawLine(if (t == axis.min) viz.axis else viz.grid, Offset(left, yy), Offset(size.width, yy), 1.dp.toPx())
                val label = measurer.measure(tickFormat(t), tickStyle)
                drawText(label, topLeft = Offset(left - label.size.width - 6.dp.toPx(), yy - label.size.height / 2))
            }

            // X labels: first, middle, last day.
            val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())
            listOf(0, days.lastIndex / 2, days.lastIndex).distinct().forEach { i ->
                val label = measurer.measure(days[i].format(fmt), tickStyle)
                val lx = (x(i) - label.size.width / 2).coerceIn(left, size.width - label.size.width)
                drawText(label, topLeft = Offset(lx, size.height - label.size.height))
            }

            // The reference line is drawn under the data; its label is drawn last, over it (see below).
            reference?.let { r -> drawLine(viz.muted, Offset(left, y(r.value)), Offset(size.width, y(r.value)), 1.dp.toPx()) }

            selected?.let { i ->
                drawLine(ink.copy(alpha = 0.35f), Offset(x(i), top), Offset(x(i), top + plotH), 1.dp.toPx())
            }

            series.filter { it.kind == MarkKind.BAR }.forEach { s ->
                val barW = minOf(24.dp.toPx(), slot * 0.7f).coerceAtLeast(1f)
                s.values.forEachIndexed { i, v ->
                    if (v == null || v <= 0) return@forEachIndexed
                    val yTop = y(v)
                    val yBase = y(maxOf(axis.min, 0.0))
                    val color = if (selected == null || selected == i) s.color else s.color.copy(alpha = 0.45f)
                    roundedTopBar(color, x(i) - barW / 2, yTop, barW, yBase - yTop, minOf(4.dp.toPx(), barW / 2))
                }
            }
            series.filter { it.kind != MarkKind.BAR }.forEach { s ->
                if (s.kind == MarkKind.LINE) line(s, ::x, ::y)
                s.values.forEachIndexed { i, v ->
                    if (v == null) return@forEachIndexed
                    val isolated = s.kind == MarkKind.LINE && !s.connectGaps &&
                        s.values.getOrNull(i - 1) == null && s.values.getOrNull(i + 1) == null
                    if (s.kind == MarkKind.DOTS || isolated || selected == i) {
                        drawCircle(surface, radius = 6.dp.toPx(), center = Offset(x(i), y(v)))
                        drawCircle(s.color, radius = 4.dp.toPx(), center = Offset(x(i), y(v)))
                    }
                }
            }

            // Reference label on a surface-coloured pill so bars and lines never hide it.
            reference?.let { r ->
                val label = measurer.measure(r.label, tickStyle.copy(color = ink.copy(alpha = 0.75f)))
                val padH = 4.dp.toPx()
                val w = label.size.width + padH * 2
                val h = label.size.height.toFloat()
                val ly = (y(r.value) - h / 2).coerceIn(top, top + plotH - h)
                drawRoundRect(surface, Offset(size.width - w, ly), Size(w, h), CornerRadius(4.dp.toPx()))
                drawText(label, topLeft = Offset(size.width - w + padH, ly))
            }
        }
    }
}

@Composable
private fun Readout(
    days: List<LocalDate>, series: List<ChartSeries>, selected: Int?, format: (Double) -> String, unit: String, summary: String?,
) {
    val viz = LocalViz.current
    Box(Modifier.fillMaxWidth().heightIn(min = 20.dp)) {
        if (selected == null || selected !in days.indices) {
            Text(summary ?: "Tap or drag the chart for daily values", style = LabelStyle, color = viz.muted)
        } else {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(days[selected].format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())),
                    style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                series.forEach { s ->
                    val v = s.values.getOrNull(selected)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Canvas(Modifier.width(10.dp).height(2.dp)) { drawRect(s.color) }
                        Spacer(Modifier.width(4.dp))
                        Text(if (v == null) "–" else format(v) + if (unit.isNotEmpty()) " $unit" else "",
                            style = LabelStyle.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurface)
                        if (series.size > 1) {
                            Spacer(Modifier.width(3.dp))
                            Text(s.label, style = LabelStyle, color = viz.muted)
                        }
                    }
                }
            }
        }
    }
}

private fun indexAt(px: Float, width: Float, left: Float, n: Int): Int? {
    if (n == 0) return null
    val slot = (width - left) / n
    return ((px - left) / slot).toInt().coerceIn(0, n - 1)
}

/** Bar with a 4px rounded data end and a square base on the baseline. */
private fun DrawScope.roundedTopBar(color: Color, x: Float, y: Float, w: Float, h: Float, r: Float) {
    if (h <= 0) return
    if (h <= r) {
        drawRect(color, Offset(x, y), Size(w, h)); return
    }
    drawRoundRect(color, Offset(x, y), Size(w, h), CornerRadius(r))
    drawRect(color, Offset(x, y + h - r), Size(w, r))
}

private fun DrawScope.line(s: ChartSeries, x: (Int) -> Float, y: (Double) -> Float) {
    val path = Path()
    var penDown = false
    s.values.forEachIndexed { i, v ->
        if (v == null) {
            if (!s.connectGaps) penDown = false
            return@forEachIndexed
        }
        if (penDown) path.lineTo(x(i), y(v)) else path.moveTo(x(i), y(v))
        penDown = true
    }
    drawPath(path, s.color, style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

data class Axis(val min: Double, val max: Double, val ticks: List<Double>)

/** Round, readable ticks: about four steps of 1, 2, 2.5 or 5 times a power of ten. */
fun niceAxis(values: List<Double>, zeroBased: Boolean): Axis {
    if (values.isEmpty()) return Axis(0.0, 1.0, listOf(0.0, 1.0))
    var lo = if (zeroBased) minOf(0.0, values.min()) else values.min()
    var hi = values.max()
    if (hi - lo < 1e-9) { hi += 1.0; if (!zeroBased) lo -= 1.0 }
    val raw = (hi - lo) / 4
    val mag = 10.0.pow(floor(log10(raw)))
    val step = listOf(1.0, 2.0, 2.5, 5.0, 10.0).map { it * mag }.first { it >= raw }
    val min = floor(lo / step) * step
    val max = ceil(hi / step) * step
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
    return Axis(min, max, ticks)
}

/** 0 / 950 / 1.5K / 12K style labels for axis ticks. */
fun compact(v: Double): String = when {
    abs(v) >= 10_000 -> "${(v / 1000).roundToInt()}K"
    abs(v) >= 1_000 -> if (v % 1000 == 0.0) "${(v / 1000).toInt()}K" else "%.1fK".format(Locale.ROOT, v / 1000)
    v % 1.0 == 0.0 -> v.toLong().toString()
    else -> "%.1f".format(Locale.ROOT, v)
}
