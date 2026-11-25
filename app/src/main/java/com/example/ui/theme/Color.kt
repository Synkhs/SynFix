package com.example.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

val SynFixViolet = Color(0xFF7C4DFF)
val SynFixCyan = Color(0xFF00E5FF)
val SynFixCoral = Color(0xFFFF5E7E)
val SynFixEmerald = Color(0xFF00E676)
val SynFixAmber = Color(0xFFFFB300)
val SynFixIndigo = Color(0xFF3D5AFE)

val AmoledBlack = Color(0xFF000000)
val AmoledSurfaceContainer = Color(0xFF0D0D12)
val AmoledSurfaceHigh = Color(0xFF171720)

@Immutable
data class SeedColorOption(
    val name: String,
    val color: Color
)

val SeedColorPresets = listOf(
    SeedColorOption("Electric Violet", SynFixViolet),
    SeedColorOption("Cyber Cyan", SynFixCyan),
    SeedColorOption("Sunset Coral", SynFixCoral),
    SeedColorOption("Emerald Mint", SynFixEmerald),
    SeedColorOption("Amber Gold", SynFixAmber),
    SeedColorOption("Deep Indigo", SynFixIndigo)
)

/**
 * Generates an accessible Material 3 ColorScheme from any seed color (custom or extracted from album art).
 */
fun generateColorSchemeFromSeed(
    seed: Color,
    darkTheme: Boolean,
    amoledMode: Boolean = false
): ColorScheme {
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(seed.toArgb(), hsl)
    val hue = hsl[0]
    val sat = hsl[1].coerceIn(0.45f, 0.90f)

    fun hslColor(h: Float, s: Float, l: Float): Color {
        val wrappedH = ((h % 360f) + 360f) % 360f
        return Color(ColorUtils.HSLToColor(floatArrayOf(wrappedH, s.coerceIn(0f, 1f), l.coerceIn(0f, 1f))))
    }

    return if (darkTheme) {
        val primary = hslColor(hue, sat, 0.78f)
        val onPrimary = hslColor(hue, sat, 0.16f)
        val primaryContainer = hslColor(hue, sat * 0.78f, 0.28f)
        val onPrimaryContainer = hslColor(hue, sat, 0.92f)

        val secondary = hslColor(hue + 28f, sat * 0.55f, 0.76f)
        val onSecondary = hslColor(hue + 28f, sat * 0.55f, 0.18f)
        val secondaryContainer = hslColor(hue + 28f, sat * 0.45f, 0.26f)
        val onSecondaryContainer = hslColor(hue + 28f, sat * 0.55f, 0.90f)

        val tertiary = hslColor(hue + 145f, sat * 0.75f, 0.76f)
        val onTertiary = hslColor(hue + 145f, sat * 0.75f, 0.16f)
        val tertiaryContainer = hslColor(hue + 145f, sat * 0.65f, 0.28f)
        val onTertiaryContainer = hslColor(hue + 145f, sat * 0.75f, 0.92f)

        val bg = if (amoledMode) AmoledBlack else hslColor(hue, 0.16f, 0.07f)
        val surface = if (amoledMode) AmoledBlack else hslColor(hue, 0.16f, 0.08f)
        val surfaceContainerLow = if (amoledMode) Color(0xFF09090D) else hslColor(hue, 0.16f, 0.11f)
        val surfaceContainer = if (amoledMode) AmoledSurfaceContainer else hslColor(hue, 0.18f, 0.13f)
        val surfaceContainerHigh = if (amoledMode) AmoledSurfaceHigh else hslColor(hue, 0.18f, 0.17f)
        val surfaceContainerHighest = if (amoledMode) Color(0xFF21212B) else hslColor(hue, 0.18f, 0.21f)

        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = bg,
            onBackground = Color(0xFFECEAF4),
            surface = surface,
            onSurface = Color(0xFFECEAF4),
            surfaceVariant = surfaceContainerHigh,
            onSurfaceVariant = Color(0xFFC5C2D4),
            surfaceContainerLowest = bg,
            surfaceContainerLow = surfaceContainerLow,
            surfaceContainer = surfaceContainer,
            surfaceContainerHigh = surfaceContainerHigh,
            surfaceContainerHighest = surfaceContainerHighest,
            outline = hslColor(hue, 0.14f, 0.45f),
            outlineVariant = hslColor(hue, 0.14f, 0.24f)
        )
    } else {
        val primary = hslColor(hue, sat, 0.42f)
        val onPrimary = Color.White
        val primaryContainer = hslColor(hue, sat * 0.85f, 0.89f)
        val onPrimaryContainer = hslColor(hue, sat, 0.14f)

        val secondary = hslColor(hue + 28f, sat * 0.55f, 0.44f)
        val onSecondary = Color.White
        val secondaryContainer = hslColor(hue + 28f, sat * 0.45f, 0.90f)
        val onSecondaryContainer = hslColor(hue + 28f, sat * 0.55f, 0.15f)

        val tertiary = hslColor(hue + 145f, sat * 0.72f, 0.44f)
        val onTertiary = Color.White
        val tertiaryContainer = hslColor(hue + 145f, sat * 0.70f, 0.90f)
        val onTertiaryContainer = hslColor(hue + 145f, sat * 0.75f, 0.14f)

        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = hslColor(hue, 0.20f, 0.98f),
            onBackground = Color(0xFF181722),
            surface = hslColor(hue, 0.20f, 0.98f),
            onSurface = Color(0xFF181722),
            surfaceVariant = hslColor(hue, 0.18f, 0.91f),
            onSurfaceVariant = Color(0xFF474556),
            surfaceContainerLow = hslColor(hue, 0.18f, 0.96f),
            surfaceContainer = hslColor(hue, 0.18f, 0.93f),
            surfaceContainerHigh = hslColor(hue, 0.18f, 0.90f),
            surfaceContainerHighest = hslColor(hue, 0.18f, 0.87f),
            outline = hslColor(hue, 0.14f, 0.50f),
            outlineVariant = hslColor(hue, 0.14f, 0.80f)
        )
    }
}
