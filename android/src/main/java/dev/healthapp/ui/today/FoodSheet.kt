package dev.healthapp.ui.today

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import dev.healthapp.container
import dev.healthapp.data.db.FoodEntry
import dev.healthapp.data.db.Meal
import dev.healthapp.data.db.RecentFood
import dev.healthapp.domain.HealthMath
import dev.healthapp.ml.PhotoEstimate
import dev.healthapp.ui.components.fmt0
import dev.healthapp.ui.theme.LabelStyle
import dev.healthapp.ui.theme.LocalViz
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private fun Double.field(): String = if (this == 0.0) "" else if (this % 1.0 == 0.0) toLong().toString() else "%.1f".format(this)
private fun String.num(): Double? = replace(',', '.').toDoubleOrNull()

/** Values a portion multiplier scales from: set when a recent food is picked or a photo is estimated. */
private data class Base(val kcal: Double, val p: Double, val c: Double, val f: Double)

private val RECENT_PORTIONS = listOf("½×" to 0.5, "1×" to 1.0, "1½×" to 1.5, "2×" to 2.0)

/** The photo model tends to underestimate big meals (docs/food-model-benchmark.md), so "bigger" is offered. */
private val PHOTO_PORTIONS = listOf("Smaller" to 0.75, "As estimated" to 1.0, "Bigger" to 1.25, "Much bigger" to 1.5)

private sealed interface PhotoState {
    data object Analyzing : PhotoState
    data class Ready(val estimate: PhotoEstimate) : PhotoState
    data class Failed(val message: String) : PhotoState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodSheet(
    existing: FoodEntry?,
    initialMeal: Meal,
    recent: List<RecentFood>,
    initialPhoto: Uri? = null,
    onDismiss: () -> Unit,
    onSave: (name: String, meal: Meal, kcal: Double, protein: Double, carbs: Double, fat: Double) -> Unit,
    onDelete: (() -> Unit)?,
) {
    val context = LocalContext.current
    val estimator = context.container.calories
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var meal by remember { mutableStateOf(initialMeal) }
    var name by remember { mutableStateOf(existing?.name ?: "") }
    var kcal by remember { mutableStateOf(existing?.kcal?.field() ?: "") }
    var protein by remember { mutableStateOf(existing?.protein?.field() ?: "") }
    var carbs by remember { mutableStateOf(existing?.carbs?.field() ?: "") }
    var fat by remember { mutableStateOf(existing?.fat?.field() ?: "") }
    var base by remember { mutableStateOf<Base?>(null) }
    var multiplier by remember { mutableStateOf(1.0) }

    var photoUri by remember { mutableStateOf(initialPhoto) }
    var photoThumb by remember { mutableStateOf<ImageBitmap?>(null) }
    var photoState by remember { mutableStateOf<PhotoState?>(null) }
    var pendingCamera by remember { mutableStateOf<Uri?>(null) }

    // Scaled and estimated values are rough, so whole numbers: "637 kcal", not "637.4".
    fun apply(b: Base, m: Double) {
        fun r(x: Double) = Math.round(x).toDouble().field()
        kcal = r(b.kcal * m); protein = r(b.p * m); carbs = r(b.c * m); fat = r(b.f * m)
    }

    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) photoUri = pendingCamera
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) photoUri = uri
    }

    // Load the 375 MB model while the user frames the shot, so the estimate itself is quick.
    LaunchedEffect(Unit) {
        if (existing == null) runCatching { estimator.warmUp() }
    }
    LaunchedEffect(photoUri) {
        val uri = photoUri ?: return@LaunchedEffect
        if (!estimator.isInstalled) {
            photoState = PhotoState.Failed("The photo model isn't on this phone yet. See Settings → Photo estimates.")
            return@LaunchedEffect
        }
        photoState = PhotoState.Analyzing
        photoState = try {
            val bitmap = withContext(Dispatchers.Default) { estimator.decode(uri) }
            photoThumb = bitmap.asImageBitmap()
            val e = withContext(Dispatchers.Default) { estimator.estimate(bitmap) }
            val b = Base(e.kcal, e.proteinG, e.carbG, e.fatG)
            base = b; multiplier = 1.0; apply(b, 1.0)
            PhotoState.Ready(e)
        } catch (t: Throwable) {
            PhotoState.Failed("Couldn't estimate this photo: ${t.message ?: t.javaClass.simpleName}")
        }
    }

    val p = protein.num() ?: 0.0
    val c = carbs.num() ?: 0.0
    val f = fat.num() ?: 0.0
    val fromMacros = HealthMath.kcalFromMacros(p, c, f)
    val kcalValue = kcal.num() ?: fromMacros.takeIf { it > 0 }
    val canSave = name.isNotBlank() && kcalValue != null
    val fromPhoto = photoState is PhotoState.Ready

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 16.dp)
                .navigationBarsPadding().imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(if (existing == null) "Log food" else "Edit food", style = MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Meal.entries.forEach { m ->
                    FilterChip(selected = meal == m, onClick = { meal = m }, label = { Text(m.label) })
                }
            }

            if (existing == null) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        val dir = File(context.cacheDir, "photos").apply { mkdirs() }
                        val file = File(dir, "meal_${System.currentTimeMillis()}.jpg")
                        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
                        pendingCamera = uri
                        camera.launch(uri)
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoCamera, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Take photo")
                    }
                    OutlinedButton(onClick = {
                        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }, modifier = Modifier.weight(1f)) {
                        Icon(Icons.Default.PhotoLibrary, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp)); Text("Choose photo")
                    }
                }
            }

            photoState?.let { state -> PhotoCard(photoThumb, state, multiplier) }

            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text(if (fromPhoto) "Name it (e.g. chicken curry)" else "What did you eat?") },
                singleLine = true, modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Next),
            )

            // Recent foods, filtered by what's typed. Picking one fills every field.
            val matches = if (existing != null || fromPhoto) emptyList() else recent.filter {
                name.isBlank() || it.name.contains(name.trim(), ignoreCase = true)
            }.take(6)
            if (matches.isNotEmpty() && base == null) {
                Text(if (name.isBlank()) "Recent" else "Matches", style = LabelStyle, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    matches.forEachIndexed { i, r ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                name = r.name
                                val b = Base(r.kcal, r.protein, r.carbs, r.fat)
                                base = b; multiplier = 1.0; apply(b, 1.0)
                            }.padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(r.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("P ${r.protein.fmt0()} · C ${r.carbs.fmt0()} · F ${r.fat.fmt0()} · logged ${r.timesLogged}×",
                                    style = LabelStyle, color = LocalViz.current.muted)
                            }
                            Text("${r.kcal.fmt0()} kcal", style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            base?.let { b ->
                Text(if (fromPhoto) "How big was your portion?" else "Portion", style = LabelStyle,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    (if (fromPhoto) PHOTO_PORTIONS else RECENT_PORTIONS).forEach { (label, m) ->
                        FilterChip(selected = multiplier == m, onClick = { multiplier = m; apply(b, m) }, label = { Text(label) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NumberField("Protein g", protein, { protein = it; base = null }, Modifier.weight(1f))
                NumberField("Carbs g", carbs, { carbs = it; base = null }, Modifier.weight(1f))
                NumberField("Fat g", fat, { fat = it; base = null }, Modifier.weight(1f))
            }
            NumberField(
                "Calories (kcal)", kcal, { kcal = it; base = null }, Modifier.fillMaxWidth(),
                supporting = when {
                    fromMacros > 0 && kcal.isBlank() -> "Will use ${fromMacros.fmt0()} kcal from macros"
                    fromMacros > 0 && kcal.num() != null && kotlin.math.abs(fromMacros - kcal.num()!!) > 0.25 * kcal.num()!! ->
                        "Macros add up to ${fromMacros.fmt0()} kcal"
                    else -> null
                },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (onDelete != null) {
                    OutlinedButton(onClick = onDelete) { Text("Delete") }
                }
                Button(
                    onClick = { onSave(name.trim(), meal, kcalValue!!, p, c, f) },
                    enabled = canSave,
                    modifier = Modifier.weight(1f),
                ) { Text(if (fromPhoto && name.isBlank()) "Name it to save" else "Save") }
            }
        }
    }
}

/** Thumbnail plus the estimate as a range; the point value is secondary because the model is rough. */
@Composable
private fun PhotoCard(thumb: ImageBitmap?, state: PhotoState, multiplier: Double) {
    val viz = LocalViz.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            when {
                thumb != null -> Image(thumb, null, contentScale = ContentScale.Crop, modifier = Modifier.size(88.dp))
                state is PhotoState.Analyzing -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                else -> Icon(Icons.Default.PhotoLibrary, null, tint = viz.muted, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            when (state) {
                PhotoState.Analyzing -> {
                    Text("Estimating…", style = MaterialTheme.typography.titleMedium)
                    Text("On this phone; the photo isn't sent anywhere.", style = LabelStyle, color = viz.muted)
                }
                is PhotoState.Ready -> {
                    val e = state.estimate
                    Text("≈ ${(e.kcal * multiplier).fmt0()} kcal", style = MaterialTheme.typography.titleLarge)
                    Text("likely ${(e.low * multiplier).fmt0()}–${(e.high * multiplier).fmt0()} kcal · about ${(e.massG * multiplier).fmt0()} g",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("A rough estimate: check it, and adjust the portion. ${"%.1f".format(e.inferenceMs / 1000.0)} s on device.",
                        style = LabelStyle, color = viz.muted)
                }
                is PhotoState.Failed -> Text(state.message, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String, value: String, onChange: (String) -> Unit, modifier: Modifier, supporting: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { v -> if (v.isEmpty() || v.replace(',', '.').toDoubleOrNull() != null) onChange(v) },
        label = { Text(label, maxLines = 1) },
        singleLine = true,
        modifier = modifier,
        supportingText = supporting?.let { { Text(it) } },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
    )
}

@Composable
fun WeightDialog(initial: Double?, onDismiss: () -> Unit, onSave: (Double) -> Unit) {
    var text by remember { mutableStateOf(initial?.let { "%.1f".format(it) } ?: "") }
    val kg = text.num()?.takeIf { it in 20.0..400.0 }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Log weight") },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, label = { Text("Weight (kg)") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            )
        },
        confirmButton = { TextButton(onClick = { onSave(kg!!) }, enabled = kg != null) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
