package dev.healthapp.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.healthapp.BuildConfig
import dev.healthapp.PrivacyActivity
import dev.healthapp.container
import dev.healthapp.data.health.ReadType
import dev.healthapp.ui.components.SectionCard
import dev.healthapp.ui.components.fmt0
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun SettingsScreen(onConnectHealth: () -> Unit, contentPadding: PaddingValues) {
    val context = LocalContext.current
    val app = context.container
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(app) } })
    val s by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refreshStatus() }
    LaunchedEffect(message) { message?.let { snackbar.showSnackbar(it); vm.messageShown() } }

    val sharePermission = rememberLauncherForActivityResult(app.health.permissionContract()) { granted ->
        vm.setShareFood(vm.shareFoodPermission in granted)
    }
    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri != null) vm.export(context.contentResolver, uri)
    }

    Box(Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("Settings", style = MaterialTheme.typography.titleLarge) }
            item { HealthConnectCard(s, vm, onConnectHealth, onManage = { runCatching { context.startActivity(app.health.manageAccessIntent()) } }) }
            item {
                SectionCard(title = "Share food with Health Connect") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "Write what you log as nutrition records, so apps you allow (for example WHOOP or Fitbit) can see calories eaten.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = s.settings.shareFoodToHealthConnect,
                            enabled = s.health.available,
                            onCheckedChange = { on ->
                                when {
                                    !on -> vm.setShareFood(false)
                                    vm.shareFoodPermission in s.health.granted -> vm.setShareFood(true)
                                    else -> sharePermission.launch(setOf(vm.shareFoodPermission))
                                }
                            },
                        )
                    }
                    Text("Applies to food logged from now on.", style = LabelStyle, color = LocalViz.current.muted)
                }
            }
            item { PhotoModelCard() }
            item { TargetsCard(s, vm) }
            item {
                SectionCard(title = "Your data") {
                    Text(
                        "Everything is stored on this phone only. Export a zip of CSV files (food, weight, daily metrics, sleep).",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = { exporter.launch("health-export-${LocalDate.now()}.zip") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Export all data")
                    }
                }
            }
            item {
                SectionCard(title = "About") {
                    Text("Health ${BuildConfig.VERSION_NAME}${if (BuildConfig.DEBUG) " (debug)" else ""}",
                        style = MaterialTheme.typography.bodyMedium)
                    TextButton(onClick = { context.startActivity(Intent(context, PrivacyActivity::class.java)) }) {
                        Text("Privacy")
                    }
                }
            }
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(16.dp))
    }
}

@Composable
private fun HealthConnectCard(s: SettingsState, vm: SettingsViewModel, onConnect: () -> Unit, onManage: () -> Unit) {
    val viz = LocalViz.current
    val grantedReads = ReadType.entries.count { it.permission in s.health.granted }
    SectionCard(title = "Health Connect") {
        Text(
            when {
                !s.health.available -> "Not available on this phone. Install or update Health Connect."
                grantedReads == 0 -> "Not connected"
                else -> "Connected: reading $grantedReads of ${ReadType.entries.size} data types" +
                    if (vm.historyPermission in s.health.granted) ", including past data" else ""
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            ReadType.entries.forEach { t ->
                val ok = t.permission in s.health.granted
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (ok) Icons.Default.Check else Icons.Default.Close, contentDescription = if (ok) "Allowed" else "Not allowed",
                        tint = if (ok) viz.good else viz.muted, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(t.label, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    Text(if (ok) "Allowed" else "Not allowed", style = LabelStyle, color = viz.muted)
                }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        val last = s.settings.lastSyncMillis
        Text(
            if (s.syncing) "Syncing…"
            else if (last == 0L) "Never synced"
            else "Last sync " + Instant.ofEpochMilli(last).atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.getDefault())),
            style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (s.settings.lastSyncMessage.isNotBlank()) {
            Text(s.settings.lastSyncMessage, style = LabelStyle, color = viz.muted)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (s.health.available && grantedReads < ReadType.entries.size) {
                Button(onClick = onConnect, modifier = Modifier.weight(1f)) { Text(if (grantedReads == 0) "Connect" else "Allow all") }
            } else {
                Button(onClick = vm::syncNow, enabled = s.health.available && !s.syncing, modifier = Modifier.weight(1f)) { Text("Sync now") }
            }
            OutlinedButton(onClick = onManage, enabled = s.health.available, modifier = Modifier.weight(1f)) { Text("Manage access") }
        }
    }
}

@Composable
private fun PhotoModelCard() {
    val estimator = LocalContext.current.container.calories
    SectionCard(title = "Photo estimates") {
        if (estimator.isInstalled) {
            Text("CalorieK model installed (${estimator.modelFile.length() / 1_000_000} MB). Photos are estimated on this phone and never uploaded.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Expect roughly ±25–35% per meal; it tends to underestimate big portions.", style = LabelStyle,
                color = LocalViz.current.muted)
        } else {
            Text("The photo model isn't installed. Copy caloriek_fp32.onnx to:\n${estimator.modelFile.absolutePath}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TargetsCard(s: SettingsState, vm: SettingsViewModel) {
    // Fields start from saved settings; keyed so they refresh once the saved values load.
    val st = s.settings
    var kcal by rememberSaveable(st.kcalTarget) { mutableStateOf(st.kcalTarget.toString()) }
    var protein by rememberSaveable(st.proteinTarget) { mutableStateOf(st.proteinTarget.toString()) }
    var carbs by rememberSaveable(st.carbsTarget) { mutableStateOf(st.carbsTarget.toString()) }
    var fat by rememberSaveable(st.fatTarget) { mutableStateOf(st.fatTarget.toString()) }
    var goal by rememberSaveable(st.goalWeightKg) { mutableStateOf(st.goalWeightKg?.let { "%.1f".format(it) } ?: "") }

    val values = listOf(kcal, protein, carbs, fat).map { it.toIntOrNull() }
    val goalValue = goal.replace(',', '.').toDoubleOrNull()
    val valid = values.all { it != null && it > 0 } && (goal.isBlank() || goalValue != null)
    val changed = values != listOf(st.kcalTarget, st.proteinTarget, st.carbsTarget, st.fatTarget) ||
        (if (goal.isBlank()) null else goalValue) != st.goalWeightKg

    SectionCard(title = "Daily targets") {
        s.burn?.let { b ->
            Text(
                "Over your last ${b.days} tracked days you burned about ${b.avgKcal.fmt0()} kcal a day. Pick a starting point:",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // 0.25 and 0.5 kg a week of fat is roughly 275 and 550 kcal a day.
                listOf("Maintain" to 0, "−0.25 kg/wk" to 275, "−0.5 kg/wk" to 550, "+0.25 kg/wk" to -275).forEach { (label, cut) ->
                    val target = ((b.avgKcal - cut) / 10).toInt() * 10
                    AssistChip(onClick = { kcal = target.toString() }, label = { Text("$label · ${target.toDouble().fmt0()}") })
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Calories", kcal, { kcal = it }, Modifier.weight(1f))
            NumberField("Protein g", protein, { protein = it }, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Carbs g", carbs, { carbs = it }, Modifier.weight(1f))
            NumberField("Fat g", fat, { fat = it }, Modifier.weight(1f))
        }
        val p = protein.toIntOrNull() ?: 0; val c = carbs.toIntOrNull() ?: 0; val f = fat.toIntOrNull() ?: 0
        Text("Macros add up to ${(p * 4 + c * 4 + f * 9).toDouble().fmt0()} kcal", style = LabelStyle, color = LocalViz.current.muted)
        OutlinedTextField(
            value = goal, onValueChange = { goal = it }, label = { Text("Goal weight kg (optional)") }, singleLine = true,
            modifier = Modifier.fillMaxWidth(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        )
        Button(
            onClick = { vm.saveTargets(values[0]!!, values[1]!!, values[2]!!, values[3]!!, if (goal.isBlank()) null else goalValue) },
            enabled = valid && changed, modifier = Modifier.fillMaxWidth(),
        ) { Text("Save targets") }
    }
}

@Composable
private fun NumberField(label: String, value: String, onChange: (String) -> Unit, modifier: Modifier) {
    OutlinedTextField(
        value = value, onValueChange = { v -> if (v.all(Char::isDigit) && v.length <= 5) onChange(v) },
        label = { Text(label) }, singleLine = true, modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
    )
}
