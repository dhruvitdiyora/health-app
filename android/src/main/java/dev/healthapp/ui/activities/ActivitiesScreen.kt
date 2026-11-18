package dev.healthapp.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.healthapp.container
import dev.healthapp.data.db.ActivityGoal
import dev.healthapp.data.health.sourceLabel
import dev.healthapp.domain.Activities
import dev.healthapp.ui.components.Meter
import dev.healthapp.ui.components.SectionCard
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ActivitiesScreen(onConnectHealth: () -> Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val vm: ActivitiesViewModel = viewModel(factory = viewModelFactory { initializer { ActivitiesViewModel(context.container) } })
    val s by vm.state.collectAsStateWithLifecycle()
    // null = closed; Pair(null, _) = a new goal.
    var editing by remember { mutableStateOf<Pair<ActivityGoal?, Unit>?>(null) }

    LazyColumn(
        Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { Text("Activities", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(start = 4.dp)) }
        if (!s.hasAccess) {
            item {
                SectionCard(title = "Needs access") {
                    Text("Allow Workouts in Health Connect to see the sessions Fitbit, Strava, Hevy and others record.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onConnectHealth) { Text("Connect Health Connect") }
                }
            }
        }
        item { GoalsCard(s, onAdd = { editing = null to Unit }, onEdit = { editing = it to Unit }) }
        if (s.totals.isNotEmpty()) item { TotalsCard(s) }
        if (s.loaded && s.recent.isEmpty() && s.hasAccess) {
            item {
                SectionCard {
                    Text("No workouts in the last $RECENT_DAYS days. They appear here once an app writes them to Health Connect.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (s.recent.isNotEmpty()) {
            item { Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) }
            items(s.recent, key = { it.session.id }) { ActivityRow(it) }
            item {
                Text(
                    "When two apps record the same workout (they overlap by at least half of the shorter one), it's shown " +
                        "once, keeping the longer copy" + (if (s.merged > 0) "; ${s.merged} duplicate${if (s.merged == 1) "" else "s"} merged here." else "."),
                    style = LabelStyle, color = LocalViz.current.muted, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }

    editing?.let { (goal, _) ->
        GoalDialog(
            goal = goal,
            onDismiss = { editing = null },
            onSave = { c, m, t -> vm.saveGoal(goal, c, m, t); editing = null },
            onDelete = goal?.let { g -> { vm.deleteGoal(g); editing = null } },
        )
    }
}

private fun goalTitle(g: Activities.Goal): String {
    val what = g.category?.label ?: "Any activity"
    return "$what · ${g.weeklyTarget} ${g.metric.label} a week"
}

@Composable
private fun GoalsCard(s: ActivitiesState, onAdd: () -> Unit, onEdit: (ActivityGoal) -> Unit) {
    val viz = LocalViz.current
    SectionCard(
        title = "Weekly goals",
        trailing = { TextButton(onClick = onAdd) { Icon(Icons.Default.Add, null, Modifier.size(18.dp)); Text(" Add") } },
    ) {
        if (s.goals.isEmpty()) {
            Text("Set a goal, like 3 runs a week or 150 active minutes. Weeks run Monday to Sunday.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.goals.forEach { (entity, p) ->
            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(goalTitle(p.goal), style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                    TextButton(onClick = { onEdit(entity) }) { Text("Edit") }
                }
                Meter("This week", p.thisWeek.toDouble(), p.goal.weeklyTarget.toDouble(), p.goal.metric.label, viz.burned)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (p.met) Icon(Icons.Default.CheckCircle, "Met", Modifier.size(16.dp), tint = viz.good)
                    val left = p.goal.weeklyTarget - p.thisWeek
                    Text(
                        when {
                            p.met -> "Met this week"
                            p.daysLeft == 0 -> "$left ${p.goal.metric.label} to go today"
                            else -> "$left ${p.goal.metric.label} to go, ${p.daysLeft} day${if (p.daysLeft == 1) "" else "s"} left"
                        } + " · streak ${p.streak} week${if (p.streak == 1) "" else "s"}" + (if (p.best > p.streak) " (best ${p.best})" else ""),
                        style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                WeekDots(p)
            }
        }
    }
}

/** The last 8 weeks: a filled dot = goal met, a hollow ring = this week so far, a plain track dot = missed. */
@Composable
private fun WeekDots(p: Activities.Progress) {
    val viz = LocalViz.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        p.weeks.forEachIndexed { i, w ->
            val current = i == p.weeks.lastIndex
            val m = Modifier.size(10.dp).clip(CircleShape)
            Box(
                when {
                    w.met -> m.background(viz.burned)
                    current -> m.border(1.5.dp, viz.burned, CircleShape)
                    else -> m.background(viz.track)
                }
            )
        }
        Text("last ${p.weeks.size} weeks", style = LabelStyle, color = viz.muted, modifier = Modifier.padding(start = 4.dp))
    }
}

@Composable
private fun TotalsCard(s: ActivitiesState) {
    SectionCard(title = "Last $RECENT_DAYS days") {
        s.totals.forEach { t ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(t.category.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                Text("${t.sessions} × · ${hm(t.minutes)}", style = MaterialTheme.typography.bodyMedium)
            }
        }
        Text("${s.recent.size} workouts, ${hm(s.recent.sumOf { it.minutes })} in total.", style = LabelStyle, color = LocalViz.current.muted)
    }
}

private val dateFmt = DateTimeFormatter.ofPattern("EEE d MMM · HH:mm", Locale.getDefault())

@Composable
private fun ActivityRow(a: Activities.Activity) {
    val viz = LocalViz.current
    val s = a.session
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(Activities.label(s.type, s.title), style = MaterialTheme.typography.titleSmall)
                Text(Instant.ofEpochMilli(s.start).atZone(ZoneId.systemDefault()).format(dateFmt),
                    style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    sourceLabel(s.source) + (if (a.alsoRecordedBy.isNotEmpty()) " · also recorded by " + a.alsoRecordedBy.joinToString(", ") { sourceLabel(it) } else ""),
                    style = LabelStyle, color = viz.muted,
                )
            }
            Text(hm(a.minutes), style = MaterialTheme.typography.titleMedium)
        }
    }
}

private fun hm(minutes: Int): String = if (minutes < 60) "$minutes min" else "${minutes / 60}h ${(minutes % 60).toString().padStart(2, '0')}m"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDialog(
    goal: ActivityGoal?,
    onDismiss: () -> Unit,
    onSave: (Activities.Category?, Activities.Metric, Int) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val start = goal?.toDomain()
    var category by remember { mutableStateOf(start?.category) }
    var metric by remember { mutableStateOf(start?.metric ?: Activities.Metric.SESSIONS) }
    var target by remember { mutableStateOf(start?.weeklyTarget?.toString() ?: "3") }
    val value = target.toIntOrNull()?.takeIf { it in 1..10_000 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (goal == null) "New weekly goal" else "Edit goal") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Activity", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (listOf<Activities.Category?>(null) + Activities.Category.entries).forEach { c ->
                        FilterChip(selected = category == c, onClick = { category = c }, label = { Text(c?.label ?: "Any") })
                    }
                }
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    Activities.Metric.entries.forEachIndexed { i, m ->
                        SegmentedButton(
                            selected = metric == m,
                            onClick = { if (metric != m) { metric = m; target = if (m == Activities.Metric.MINUTES) "150" else "3" } },
                            shape = SegmentedButtonDefaults.itemShape(i, Activities.Metric.entries.size),
                        ) { Text(m.label.replaceFirstChar { it.uppercase() }) }
                    }
                }
                OutlinedTextField(
                    value = target, onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 5) target = v },
                    label = { Text("${metric.label.replaceFirstChar { it.uppercase() }} a week") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(category, metric, value!!) }, enabled = value != null) { Text("Save") } },
        dismissButton = {
            Row {
                onDelete?.let { TextButton(onClick = it) { Text("Delete") } }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
