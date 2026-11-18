package dev.healthapp.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsRun
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.MonitorWeight
import androidx.compose.material.icons.filled.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.healthapp.container
import dev.healthapp.domain.Coach
import dev.healthapp.domain.HealthMath
import dev.healthapp.domain.Insights
import dev.healthapp.ui.components.SectionCard
import dev.healthapp.ui.components.fmt0
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz
import kotlin.math.abs

@Composable
fun InsightsScreen(contentPadding: PaddingValues) {
    val context = LocalContext.current
    val vm: InsightsViewModel = viewModel(factory = viewModelFactory { initializer { InsightsViewModel(context.container) } })
    val s by vm.state.collectAsStateWithLifecycle()

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Insights", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp)) }
        if (s.loaded) {
            item { CoachCard(s.tips) }
            s.calibration?.let { item { CalibrationCard(it) } }
            s.sleep?.let { item { SleepCard(it) } }
        }
    }
}

private fun icon(k: Coach.Kind): ImageVector = when (k) {
    Coach.Kind.DATA -> Icons.Default.EditNote
    Coach.Kind.FOOD -> Icons.Default.Restaurant
    Coach.Kind.SLEEP -> Icons.Default.Bedtime
    Coach.Kind.RECOVERY -> Icons.Default.Favorite
    Coach.Kind.ACTIVITY -> Icons.AutoMirrored.Filled.DirectionsRun
    Coach.Kind.WEIGHT -> Icons.Default.MonitorWeight
    Coach.Kind.GOOD -> Icons.Default.CheckCircle
}

@Composable
private fun CoachCard(tips: List<Coach.Tip>) {
    val viz = LocalViz.current
    SectionCard(title = "This week") {
        tips.forEach { t ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(icon(t.kind), null, Modifier.size(20.dp).padding(top = 2.dp),
                    tint = if (t.kind == Coach.Kind.GOOD) viz.good else MaterialTheme.colorScheme.onSurfaceVariant)
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(t.title, style = MaterialTheme.typography.titleSmall)
                    Text(t.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Text("Simple rules over the last 7 complete days, worked out on this phone. Suggestions, not medical advice.",
            style = LabelStyle, color = viz.muted)
    }
}

private fun kcal(v: Double) = "${v.fmt0()} kcal"
private fun signed(v: Double) = (if (v >= 0) "+" else "−") + abs(v).fmt0()

@Composable
private fun CalibrationCard(c: Insights.Calibration) {
    val viz = LocalViz.current
    SectionCard(title = "Eaten vs burned") {
        when (c) {
            is Insights.Calibration.Missing -> {
                Text("Compares what you log and what your devices say you burn with what the scale actually did, over the last " +
                    "${InsightWindows.CALIBRATION_DAYS} days.", style = MaterialTheme.typography.bodyMedium)
                Text(c.reason, style = LabelStyle, color = viz.muted)
            }
            is Insights.Calibration.Result -> {
                Text("Your logs: about ${kcal(c.avgEaten)} eaten and ${kcal(c.avgBurned)} burned a day, a balance of " +
                    "${signed(c.loggedBalance)} kcal a day (${c.loggedDays} days with both).", style = MaterialTheme.typography.bodyMedium)
                Text("The scale: your weight trend points to ${signed(c.weightBalanceLow)} to ${signed(c.weightBalanceHigh)} kcal a day.",
                    style = MaterialTheme.typography.bodyMedium)
                Text("Your maintenance looks like roughly ${c.maintenanceLow.fmt0()}–${c.maintenanceHigh.fmt0()} kcal a day.",
                    style = MaterialTheme.typography.titleSmall)
                Text(
                    when (c.verdict) {
                        Insights.Verdict.CONSISTENT -> "Your logs and the scale agree, within the noise."
                        Insights.Verdict.LOGS_UNDERSTATE_BALANCE -> "The scale shows about ${abs(c.gap).fmt0()} kcal a day more than your logs. " +
                            "Usually some food isn't logged, or devices over-estimate burn (wrist devices are often 20–90% off on calories)."
                        Insights.Verdict.LOGS_OVERSTATE_BALANCE -> "The scale shows about ${abs(c.gap).fmt0()} kcal a day less than your logs. " +
                            "Portions may be logged generously, or devices under-count your burn."
                    },
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text("Uses about 7,700 kcal per kg, a rough rule; water and glycogen swings make a few weeks noisy, so this is a range.",
                    style = LabelStyle, color = viz.muted)
            }
        }
    }
}

@Composable
private fun SleepCard(l: Insights.SleepLink) {
    val viz = LocalViz.current
    SectionCard(title = "Sleep and your mornings") {
        when (l) {
            is Insights.SleepLink.Missing -> {
                Text("Compares your resting heart rate and HRV after shorter and longer nights, over the last ${InsightWindows.SLEEP_DAYS} days.",
                    style = MaterialTheme.typography.bodyMedium)
                Text(l.reason, style = LabelStyle, color = viz.muted)
            }
            is Insights.SleepLink.Result -> {
                val split = HealthMath.formatMinutes(l.splitMinutes)
                Text("${l.nights} nights, split at your middle value of $split asleep.", style = MaterialTheme.typography.bodyMedium)
                l.restingHr?.let { ComparisonLine("Resting heart rate", it, "bpm", higherIsBetter = false) }
                l.hrv?.let { ComparisonLine("HRV", it, "ms", higherIsBetter = true) }
                if (l.restingHr == null && l.hrv == null) Text("Not enough mornings with a reading on both sides yet.", style = LabelStyle, color = viz.muted)
                Text("In large wearable datasets this link is small and differs a lot between people, so \"no clear difference\" is a common answer.",
                    style = LabelStyle, color = viz.muted)
            }
        }
    }
}

@Composable
private fun ComparisonLine(name: String, c: Insights.Comparison, unit: String, higherIsBetter: Boolean) {
    val better = (c.difference > 0) == higherIsBetter
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(name, style = MaterialTheme.typography.labelLarge)
        Text("After shorter nights ${"%.1f".format(c.shortMean)} $unit, after longer ${"%.1f".format(c.longMean)} $unit " +
            "(${c.shortNights} and ${c.longNights} mornings).", style = MaterialTheme.typography.bodySmall)
        Text(
            if (c.clear) "A clear difference: ${if (better) "better" else "worse"} after longer sleep for you."
            else "No clear difference: within your normal day-to-day spread.",
            style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
