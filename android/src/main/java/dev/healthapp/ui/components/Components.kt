package dev.healthapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz

/** Surface card with a hairline ring instead of a shadow. */
@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    trailing: @Composable (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, LocalViz.current.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (title != null || trailing != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (title != null) Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                trailing?.invoke()
            }
        }
        content()
    }
}

/** Stat tile: label, value, optional unit and a secondary line (delta or source). */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    detail: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, LocalViz.current.border, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, maxLines = 1)
            if (unit != null) {
                Spacer(Modifier.width(3.dp))
                Text(unit, style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 3.dp))
            }
        }
        Text(
            detail ?: " ", style = LabelStyle, color = LocalViz.current.muted,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Progress meter: the track is a recessive step, the fill carries the series colour. */
@Composable
fun Meter(label: String, value: Double, target: Double, unit: String, color: Color, modifier: Modifier = Modifier) {
    val viz = LocalViz.current
    Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(color))
            Spacer(Modifier.width(6.dp))
            Text(label, style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(
                "${value.toInt()} / ${target.toInt()} $unit", style = LabelStyle,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Canvas(Modifier.fillMaxWidth().height(8.dp)) {
            val r = CornerRadius(size.height / 2)
            drawRoundRect(viz.track, cornerRadius = r)
            val frac = if (target > 0) (value / target).coerceIn(0.0, 1.0).toFloat() else 0f
            if (frac > 0) drawRoundRect(color, size = Size(size.width * frac, size.height), cornerRadius = r)
            if (value > target && target > 0) {
                // Past the target: a 2px surface notch marks where the target sits.
                val x = size.width * (target / value).toFloat()
                drawRoundRect(color, size = size, cornerRadius = r)
                drawLine(viz.track, Offset(x, 0f), Offset(x, size.height), strokeWidth = 2.dp.toPx())
            }
        }
    }
}

/** Ring for eaten vs. target; the centre holds the hero number. */
@Composable
fun ProgressRing(
    fraction: Float,
    color: Color,
    modifier: Modifier = Modifier,
    ringSize: Dp = 148.dp,
    stroke: Dp = 12.dp,
    center: @Composable () -> Unit,
) {
    val viz = LocalViz.current
    Box(modifier.size(ringSize), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(ringSize)) {
            val s = stroke.toPx()
            val inset = s / 2
            val arcSize = Size(size.width - s, size.height - s)
            drawArc(viz.track, 0f, 360f, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(s))
            val sweep = 360f * fraction.coerceIn(0f, 1f)
            if (sweep > 0f) {
                drawArc(color, -90f, sweep, false, topLeft = Offset(inset, inset), size = arcSize, style = Stroke(s, cap = StrokeCap.Round))
            }
        }
        center()
    }
}

/** Horizontal stacked bar of sleep stages, with 2px surface gaps between segments. */
@Composable
fun StageBar(segments: List<Pair<Color, Int>>, modifier: Modifier = Modifier) {
    val total = segments.sumOf { it.second }.coerceAtLeast(1)
    val gapColor = MaterialTheme.colorScheme.surface
    Canvas(modifier.fillMaxWidth().height(14.dp)) {
        var x = 0f
        val gap = 2.dp.toPx()
        val visible = segments.filter { it.second > 0 }
        visible.forEachIndexed { i, (color, minutes) ->
            val w = size.width * minutes / total
            drawRoundRect(color, topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = CornerRadius(4.dp.toPx()))
            x += w
            if (i < visible.lastIndex) drawRect(gapColor, topLeft = Offset(x - gap / 2, 0f), size = Size(gap, size.height))
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun Double.fmt0(): String = "%,d".format(Math.round(this))
fun Long.fmtThousands(): String = "%,d".format(this)
