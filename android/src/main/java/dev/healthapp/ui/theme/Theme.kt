package dev.healthapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Chart and data colours. Values come from the validated reference palette (dataviz skill):
 * categorical slots 1-3 (blue, orange, aqua) pass colourblind checks as a set in both modes,
 * so they carry protein / carbs / fat. Sleep depth uses an ordinal blue ramp.
 */
@Immutable
data class VizColors(
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val eaten: Color,
    val burned: Color,
    val track: Color,
    val deep: Color,
    val rem: Color,
    val light: Color,
    val awake: Color,
    val grid: Color,
    val axis: Color,
    val muted: Color,
    val border: Color,
    val good: Color,
    val critical: Color,
)

private val LightViz = VizColors(
    protein = Color(0xFF2A78D6), carbs = Color(0xFFEB6834), fat = Color(0xFF1BAF7A),
    eaten = Color(0xFF2A78D6), burned = Color(0xFFEB6834), track = Color(0xFFE1E0D9),
    deep = Color(0xFF184F95), rem = Color(0xFF2A78D6), light = Color(0xFF86B6EF), awake = Color(0xFFC3C2B7),
    grid = Color(0xFFE1E0D9), axis = Color(0xFFC3C2B7), muted = Color(0xFF898781),
    border = Color(0x1A0B0B0B), good = Color(0xFF006300), critical = Color(0xFFD03B3B),
)

private val DarkViz = VizColors(
    protein = Color(0xFF3987E5), carbs = Color(0xFFD95926), fat = Color(0xFF199E70),
    eaten = Color(0xFF3987E5), burned = Color(0xFFD95926), track = Color(0xFF2C2C2A),
    deep = Color(0xFF256ABF), rem = Color(0xFF5598E7), light = Color(0xFF9EC5F4), awake = Color(0xFF52514E),
    grid = Color(0xFF2C2C2A), axis = Color(0xFF383835), muted = Color(0xFF898781),
    border = Color(0x1AFFFFFF), good = Color(0xFF0CA30C), critical = Color(0xFFE66767),
)

val LocalViz = staticCompositionLocalOf { LightViz }

private val LightScheme = lightColorScheme(
    primary = Color(0xFF2A78D6), onPrimary = Color.White,
    primaryContainer = Color(0xFFCDE2FB), onPrimaryContainer = Color(0xFF0D366B),
    secondaryContainer = Color(0xFFE6EEF9), onSecondaryContainer = Color(0xFF104281),
    background = Color(0xFFF4F3F0), onBackground = Color(0xFF0B0B0B),
    surface = Color(0xFFFCFCFB), onSurface = Color(0xFF0B0B0B),
    surfaceVariant = Color(0xFFF0EFEC), onSurfaceVariant = Color(0xFF52514E),
    surfaceContainer = Color(0xFFFCFCFB), surfaceContainerLow = Color(0xFFFCFCFB),
    surfaceContainerHigh = Color(0xFFF0EFEC), surfaceContainerHighest = Color(0xFFEAE9E5),
    outline = Color(0xFFC3C2B7), outlineVariant = Color(0xFFE1E0D9),
    error = Color(0xFFD03B3B),
)

private val DarkScheme = darkColorScheme(
    primary = Color(0xFF3987E5), onPrimary = Color.White,
    primaryContainer = Color(0xFF104281), onPrimaryContainer = Color(0xFFCDE2FB),
    secondaryContainer = Color(0xFF1C2A3D), onSecondaryContainer = Color(0xFFB7D3F6),
    background = Color(0xFF0D0D0D), onBackground = Color(0xFFF0EFEC),
    surface = Color(0xFF1A1A19), onSurface = Color(0xFFF0EFEC),
    surfaceVariant = Color(0xFF242423), onSurfaceVariant = Color(0xFFC3C2B7),
    surfaceContainer = Color(0xFF1A1A19), surfaceContainerLow = Color(0xFF1A1A19),
    surfaceContainerHigh = Color(0xFF242423), surfaceContainerHighest = Color(0xFF2C2C2A),
    outline = Color(0xFF52514E), outlineVariant = Color(0xFF2C2C2A),
    error = Color(0xFFE66767),
)

private val base = Typography()
private val AppTypography = base.copy(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
)

/** Small all-caps-free label style for stat tiles and axes. */
val LabelStyle = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium)

@Composable
fun HealthTheme(dark: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalViz provides if (dark) DarkViz else LightViz) {
        MaterialTheme(
            colorScheme = if (dark) DarkScheme else LightScheme,
            typography = AppTypography,
            content = content,
        )
    }
}
