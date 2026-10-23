package dev.healthapp.ui.trends

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.healthapp.container
import dev.healthapp.domain.HealthMath
import dev.healthapp.ui.components.ChartSeries
import dev.healthapp.ui.components.LegendItem
import dev.healthapp.ui.components.MarkKind
import dev.healthapp.ui.components.Reference
import dev.healthapp.ui.components.SectionCard
import dev.healthapp.ui.components.StatTile
import dev.healthapp.ui.components.TimeChart
import dev.healthapp.ui.components.fmt0
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz
import java.util.Locale
import kotlin.math.abs

@Composable
fun TrendsScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val vm: TrendsViewModel = viewModel(factory = viewModelFactory { initializer { TrendsViewModel(context.container) } })
    val s by vm.state.collectAsStateWithLifecycle()
    val viz = LocalViz.current

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Trends", style = MaterialTheme.typography.titleLarge)
            Text("Up to yesterday · tap or drag a chart for daily values", style = LabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        // One filter row above everything it scopes.
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Range.entries.forEach { r -> FilterChip(selected = s.range == r, onClick = { vm.setRange(r) }, label = { Text(r.label) }) }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Avg eaten", s.avgEaten?.fmt0() ?: "–", Modifier.weight(1f), unit = "kcal",
                    detail = "${s.loggedDays} of ${s.days.size} days logged")
                StatTile("Avg burned", s.avgBurned?.fmt0() ?: "–", Modifier.weight(1f), unit = "kcal",
                    detail = "${s.trackedDays} tracked days")
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                val bal = s.avgBalance
                StatTile(
                    "Avg balance", bal?.let { (if (it > 0) "+" else "−") + abs(it).fmt0() } ?: "–", Modifier.weight(1f),
                    unit = if (bal != null) "kcal" else null,
                    detail = bal?.let { "≈ %+.2f kg/week".format(Locale.getDefault(), HealthMath.kgFromEnergyBalance(it * 7)) }
                        ?: "Needs logged + tracked days",
                )
                StatTile("Avg sleep", s.avgSleep?.let { HealthMath.formatMinutes((it * 60).toInt()) } ?: "–", Modifier.weight(1f),
                    detail = "${s.sleepHours.count { it != null }} nights")
            }
        }

        item {
            SectionCard(title = "Calories") {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    LegendItem(viz.eaten, "Eaten")
                    LegendItem(viz.burned, "Burned")
                }
                TimeChart(
                    days = s.days,
                    series = listOf(
                        ChartSeries("eaten", viz.eaten, s.eaten, MarkKind.BAR),
                        ChartSeries("burned", viz.burned, s.burned, MarkKind.LINE),
                    ),
                    format = { it.fmt0() }, unit = "kcal",
                    reference = Reference("Target ${s.settings.kcalTarget.toDouble().fmt0()}", s.settings.kcalTarget.toDouble()),
                    summary = if (s.loggedDays == 0) "No food logged in this range yet; burned is shown on its own" else null,
                )
            }
        }

        item {
            SectionCard(title = "Sleep") {
                TimeChart(
                    days = s.days,
                    series = listOf(ChartSeries("asleep", viz.rem, s.sleepHours, MarkKind.BAR)),
                    format = { HealthMath.formatMinutes((it * 60).toInt()) },
                    reference = Reference("8h", 8.0),
                    tickFormat = { dev.healthapp.ui.components.compact(it) + "h" },
                    summary = s.avgSleep?.let { "Average ${HealthMath.formatMinutes((it * 60).toInt())} a night, naps included" },
                )
            }
        }

        item {
            SectionCard(title = "Steps") {
                TimeChart(
                    days = s.days,
                    series = listOf(ChartSeries("steps", viz.eaten, s.steps, MarkKind.BAR)),
                    format = { it.fmt0() },
                    reference = Reference("Goal 7,000", 7000.0),
                    summary = s.avgSteps?.let { "Average ${it.fmt0()} a day over ${s.steps.count { v -> v != null }} days" },
                )
            }
        }

        item {
            SectionCard(title = "Resting heart rate") {
                TimeChart(
                    days = s.days, series = listOf(ChartSeries("resting HR", viz.burned, s.restingHr, MarkKind.LINE)),
                    format = { it.fmt0() }, unit = "bpm",
                    summary = HealthMath.meanOrNull(s.restingHr)?.let { "Average ${it.fmt0()} bpm. Lower is usually better." },
                )
            }
        }

        item {
            SectionCard(title = "Heart rate variability") {
                TimeChart(
                    days = s.days, series = listOf(ChartSeries("HRV", viz.fat, s.hrv, MarkKind.LINE)),
                    format = { it.fmt0() }, unit = "ms",
                    summary = HealthMath.meanOrNull(s.hrv)?.let { "Average ${it.fmt0()} ms (RMSSD, measured in sleep)" },
                )
            }
        }

        item {
            val weighIns = s.weight.count { it != null }
            SectionCard(title = "Weight") {
                if (weighIns == 0) {
                    Text("No weigh-ins in this range. Log one from Today, or pick a longer range.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        LegendItem(viz.eaten, "Weigh-in")
                        LegendItem(viz.burned, "Trend")
                    }
                    TimeChart(
                        days = s.days,
                        series = listOf(
                            ChartSeries("weigh-in", viz.eaten, s.weight, MarkKind.DOTS),
                            ChartSeries("trend", viz.burned, s.weightTrend, MarkKind.LINE, connectGaps = true),
                        ),
                        format = { "%.1f".format(it) }, unit = "kg",
                        reference = s.settings.goalWeightKg?.let { Reference("Goal %.1f".format(it), it) },
                        summary = s.weightRatePerWeek?.let { "%+.2f kg/week over $weighIns weigh-ins".format(Locale.getDefault(), it) }
                            ?: "$weighIns weigh-in${if (weighIns > 1) "s" else ""} in this range",
                    )
                }
            }
        }
        item { androidx.compose.foundation.layout.Spacer(Modifier.fillMaxWidth().padding(8.dp)) }
    }
}
