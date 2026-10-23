package dev.healthapp.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.healthapp.container
import dev.healthapp.data.db.FoodEntry
import dev.healthapp.data.db.Meal
import dev.healthapp.data.health.sourceLabel
import dev.healthapp.domain.HealthMath
import dev.healthapp.ui.components.LegendItem
import dev.healthapp.ui.components.Meter
import dev.healthapp.ui.components.ProgressRing
import dev.healthapp.ui.components.SectionCard
import dev.healthapp.ui.components.StageBar
import dev.healthapp.ui.components.StatTile
import dev.healthapp.ui.components.fmt0
import dev.healthapp.ui.components.fmtThousands
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(
    onConnectHealth: () -> Unit,
    contentPadding: PaddingValues,
    sharedPhoto: android.net.Uri? = null,
    onSharedPhotoUsed: () -> Unit = {},
) {
    val context = LocalContext.current
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(context.container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    var editing by remember { mutableStateOf<FoodEntry?>(null) }
    var addingMeal by remember { mutableStateOf<Meal?>(null) }
    var weightDialog by remember { mutableStateOf(false) }
    var initialPhoto by remember { mutableStateOf<android.net.Uri?>(null) }

    // A photo shared to the app opens Log food on today with the estimate already running.
    LaunchedEffect(sharedPhoto) {
        if (sharedPhoto != null) {
            vm.goToToday()
            editing = null
            initialPhoto = sharedPhoto
            addingMeal = Meal.forHour(java.time.LocalTime.now().hour)
            onSharedPhotoUsed()
        }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.onResume() }
    LaunchedEffect(message) {
        message?.let { snackbar.showSnackbar(it); vm.messageShown() }
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        Column(Modifier.fillMaxSize()) {
            DayHeader(state, onPrev = vm::previousDay, onNext = vm::nextDay, onToday = vm::goToToday, onRefresh = vm::refresh)
            PullToRefreshBox(isRefreshing = state.syncing, onRefresh = vm::refresh, modifier = Modifier.fillMaxSize()) {
                LazyColumn(
                    Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.loaded && state.health.granted.none { it.startsWith("android.permission.health.READ") }) {
                        item { ConnectBanner(state, onConnectHealth) }
                    }
                    item { CaloriesCard(state) }
                    item { MacrosCard(state) }
                    item { ActivityRow(state) }
                    item { SleepCard(state) }
                    item { BodyRow(state, onWeight = { weightDialog = true }) }
                    item {
                        FoodLog(state, onEdit = { editing = it }, onAdd = { addingMeal = it })
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { addingMeal = if (state.isToday) Meal.forHour(java.time.LocalTime.now().hour) else Meal.LUNCH },
            icon = { Icon(Icons.Default.Add, contentDescription = null) },
            text = { Text("Log food") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp))
    }

    if (addingMeal != null || editing != null) {
        FoodSheet(
            existing = editing,
            initialMeal = editing?.meal ?: addingMeal ?: Meal.SNACK,
            recent = state.recent,
            initialPhoto = initialPhoto,
            onDismiss = { editing = null; addingMeal = null; initialPhoto = null },
            onSave = { name, meal, kcal, p, c, f ->
                val base = editing
                vm.saveFood(
                    base?.copy(name = name, meal = meal, kcal = kcal, protein = p, carbs = c, fat = f)
                        ?: FoodEntry(day = state.day.toString(), time = vm.timestampFor(state.day), name = name, meal = meal,
                            kcal = kcal, protein = p, carbs = c, fat = f)
                )
                editing = null; addingMeal = null; initialPhoto = null
            },
            onDelete = editing?.let { e -> { vm.deleteFood(e); editing = null } },
        )
    }
    if (weightDialog) {
        WeightDialog(
            initial = state.weight?.kg,
            onDismiss = { weightDialog = false },
            onSave = { vm.logWeight(it); weightDialog = false },
        )
    }
}

@Composable
private fun DayHeader(state: TodayState, onPrev: () -> Unit, onNext: () -> Unit, onToday: () -> Unit, onRefresh: () -> Unit) {
    val title = when (state.day) {
        LocalDate.now() -> "Today"
        LocalDate.now().minusDays(1) -> "Yesterday"
        else -> state.day.format(DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault()))
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Previous day") }
        Column(Modifier.weight(1f).clickable(enabled = !state.isToday, onClick = onToday), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, style = MaterialTheme.typography.titleLarge)
            Text(
                if (state.isToday) state.day.format(DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault())) else "Tap to go to today",
                style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext, enabled = !state.isToday) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Next day") }
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            if (state.syncing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
            else IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, "Sync Health Connect") }
        }
    }
}

@Composable
private fun ConnectBanner(state: TodayState, onConnect: () -> Unit) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.MonitorHeart, null, tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("Connect your wearables", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (state.health.available) "Read Fitbit and WHOOP data from Health Connect. Steps, sleep, heart rate and weight stay on this phone."
                    else "Health Connect isn't available on this phone yet. Install or update it to pull Fitbit and WHOOP data.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
            Text(if (state.health.available) "Connect Health Connect" else "Get Health Connect")
        }
    }
}

@Composable
private fun CaloriesCard(state: TodayState) {
    val viz = LocalViz.current
    val target = state.settings.kcalTarget.toDouble()
    val remaining = target - state.eaten
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ProgressRing(fraction = (state.eaten / target).toFloat(), color = viz.eaten) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(abs(remaining).fmt0(), style = MaterialTheme.typography.displaySmall)
                    Text(if (remaining >= 0) "kcal left" else "kcal over", style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                KeyValue("Eaten", "${state.eaten.fmt0()} kcal", "Target ${target.fmt0()}")
                val burned = state.burned
                KeyValue(
                    "Burned", burned?.let { "${it.fmt0()} kcal" } ?: "No data",
                    state.data.metric?.sources?.takeIf { it.isNotBlank() }?.split(',')?.joinToString(", ") { sourceLabel(it) }
                        ?: "From Health Connect",
                )
                if (burned != null && state.eaten > 0) {
                    val balance = state.eaten - burned
                    KeyValue(
                        "Balance", (if (balance > 0) "+" else "−") + abs(balance).fmt0() + " kcal",
                        if (balance > 0) "Surplus so far" else "Deficit so far",
                    )
                }
            }
        }
        if (remaining < 0) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.WarningAmber, null, tint = viz.critical, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text("Over today's target by ${abs(remaining).fmt0()} kcal", style = LabelStyle, color = MaterialTheme.colorScheme.onSurface)
            }
        }
    }
}

@Composable
private fun KeyValue(label: String, value: String, detail: String?) {
    Column {
        Text(label, style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        if (detail != null) Text(detail, style = LabelStyle, color = LocalViz.current.muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun MacrosCard(state: TodayState) {
    val viz = LocalViz.current
    val s = state.settings
    SectionCard(title = "Macros") {
        Meter("Protein", state.protein, s.proteinTarget.toDouble(), "g", viz.protein)
        Meter("Carbs", state.carbs, s.carbsTarget.toDouble(), "g", viz.carbs)
        Meter("Fat", state.fat, s.fatTarget.toDouble(), "g", viz.fat)
    }
}

@Composable
private fun ActivityRow(state: TodayState) {
    val m = state.data.metric
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile("Steps", m?.steps?.fmtThousands() ?: "–", Modifier.weight(1f), detail = "Goal 7,000")
        StatTile("Active", m?.activeKcal?.fmt0() ?: "–", Modifier.weight(1f), unit = "kcal")
        StatTile("Workouts", m?.exerciseMinutes?.toString() ?: "–", Modifier.weight(1f), unit = "min")
    }
}

@Composable
private fun SleepCard(state: TodayState) {
    val viz = LocalViz.current
    val sleep = state.data.sleep
    val zone = ZoneId.systemDefault()
    val hm = DateTimeFormatter.ofPattern("HH:mm")
    SectionCard(
        title = if (state.isToday) "Last night" else "Sleep",
        trailing = sleep?.let { { Text(sourceLabel(it.source), style = LabelStyle, color = viz.muted) } },
    ) {
        if (sleep == null) {
            Text("No sleep recorded for this night.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(HealthMath.formatMinutes(sleep.asleepMinutes), style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(10.dp))
                Text(
                    "${Instant.ofEpochMilli(sleep.start).atZone(zone).format(hm)} – ${Instant.ofEpochMilli(sleep.end).atZone(zone).format(hm)}",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 4.dp),
                )
            }
            val hasStages = sleep.deepMinutes + sleep.remMinutes + sleep.lightMinutes > 0
            if (hasStages) {
                StageBar(
                    listOf(viz.deep to sleep.deepMinutes, viz.rem to sleep.remMinutes, viz.light to sleep.lightMinutes, viz.awake to sleep.awakeMinutes)
                )
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem(viz.deep, "Deep ${HealthMath.formatMinutes(sleep.deepMinutes)}")
                    LegendItem(viz.rem, "REM ${HealthMath.formatMinutes(sleep.remMinutes)}")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    LegendItem(viz.light, "Light ${HealthMath.formatMinutes(sleep.lightMinutes)}")
                    LegendItem(viz.awake, "Awake ${HealthMath.formatMinutes(sleep.awakeMinutes)}")
                }
            }
        }
    }
}

@Composable
private fun BodyRow(state: TodayState, onWeight: () -> Unit) {
    val m = state.data.metric
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatTile(
            "Resting HR", m?.restingHr?.fmt0() ?: "–", Modifier.weight(1f), unit = "bpm",
            detail = delta(m?.restingHr, state.rhrBaseline, "bpm"),
        )
        StatTile(
            "HRV", m?.hrvMs?.fmt0() ?: "–", Modifier.weight(1f), unit = "ms",
            detail = delta(m?.hrvMs, state.hrvBaseline, "ms"),
        )
        val w = state.weight
        StatTile(
            "Weight", w?.let { "%.1f".format(it.kg) } ?: "Log", Modifier.weight(1f), unit = if (w != null) "kg" else null,
            detail = when {
                w == null -> "Tap to add"
                state.weightTrend != null -> "Trend %.1f".format(state.weightTrend)
                w.day == state.day.toString() -> sourceLabel(w.source)
                else -> "on " + LocalDate.parse(w.day).format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))
            },
            onClick = onWeight,
        )
    }
}

/** "+4 vs 7d" style comparison with the previous week's average. Text only: the arrow carries direction. */
private fun delta(value: Double?, baseline: Double?, unit: String): String? {
    if (value == null || baseline == null) return null
    val d = value - baseline
    val arrow = when {
        abs(d) < 0.5 -> "="
        d > 0 -> "↑"
        else -> "↓"
    }
    return "$arrow ${abs(d).fmt0()} $unit vs 7d"
}

@Composable
private fun FoodLog(state: TodayState, onEdit: (FoodEntry) -> Unit, onAdd: (Meal) -> Unit) {
    SectionCard(title = "Food") {
        val byMeal = state.data.food.groupBy { it.meal }
        Meal.entries.forEachIndexed { i, meal ->
            if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            val entries = byMeal[meal].orEmpty()
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(meal.label, style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                if (entries.isNotEmpty()) {
                    Text("${entries.sumOf { it.kcal }.fmt0()} kcal", style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = { onAdd(meal) }) { Text("Add") }
            }
            entries.forEach { e ->
                Row(
                    Modifier.fillMaxWidth().clickable { onEdit(e) }.padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(e.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            "P ${e.protein.fmt0()} g · C ${e.carbs.fmt0()} g · F ${e.fat.fmt0()} g",
                            style = LabelStyle, color = LocalViz.current.muted,
                        )
                    }
                    Text(e.kcal.fmt0(), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        if (state.data.food.isEmpty()) {
            Spacer(Modifier.height(2.dp))
            Text(
                "Nothing logged yet. Tap Log food, or Add under a meal.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
