package com.example.ui.theme

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import com.example.domain.model.BrightnessMode
import com.example.domain.model.ColorThemeMode

@Composable
fun SynFixTheme(
    colorMode: ColorThemeMode = ColorThemeMode.SYSTEM_DYNAMIC,
    brightnessMode: BrightnessMode = BrightnessMode.SYSTEM,
    seedColor: Color = SynFixViolet,
    albumSeedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val useDark = when (brightnessMode) {
        BrightnessMode.SYSTEM -> systemDark
        BrightnessMode.LIGHT -> false
        BrightnessMode.DARK, BrightnessMode.AMOLED -> true
    }
    val amoled = brightnessMode == BrightnessMode.AMOLED

    val rawScheme = when {
        colorMode == ColorThemeMode.ALBUM_DYNAMIC && albumSeedColor != null -> {
            generateColorSchemeFromSeed(albumSeedColor, darkTheme = useDark, amoledMode = amoled)
        }
        colorMode == ColorThemeMode.CUSTOM_SEED -> {
            generateColorSchemeFromSeed(seedColor, darkTheme = useDark, amoledMode = amoled)
        }
        colorMode == ColorThemeMode.SYSTEM_DYNAMIC && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            val base = if (useDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            if (amoled) {
                base.copy(
                    background = AmoledBlack,
                    onBackground = Color(0xFFF2F0FA),
                    surface = AmoledBlack,
                    onSurface = Color(0xFFF2F0FA),
                    onSurfaceVariant = Color(0xFFC8C5D6),
                    surfaceContainerLowest = AmoledBlack,
                    surfaceContainerLow = Color(0xFF09090D),
                    surfaceContainer = AmoledSurfaceContainer,
                    surfaceContainerHigh = AmoledSurfaceHigh,
                    surfaceContainerHighest = Color(0xFF21212B)
                )
            } else {
                base
            }
        }
        else -> {
            generateColorSchemeFromSeed(seedColor, darkTheme = useDark, amoledMode = amoled)
        }
    }

    val animatedScheme = animateColorScheme(rawScheme)

    MaterialTheme(
        colorScheme = animatedScheme,
        shapes = SynFixShapes,
        typography = SynFixTypography
    ) {
        CompositionLocalProvider(
            LocalSynFixMotion provides SynFixMotion,
            LocalContentColor provides animatedScheme.onBackground
        ) {
            Surface(
                color = animatedScheme.background,
                contentColor = animatedScheme.onBackground,
                modifier = Modifier.fillMaxSize()
            ) {
                content()
            }
        }
    }
}

@Composable
private fun animateColorScheme(target: ColorScheme): ColorScheme {
    val primary by animateColorAsState(target.primary, SynFixMotion.EffectsColorSpring, label = "primary")
    val onPrimary by animateColorAsState(target.onPrimary, SynFixMotion.EffectsColorSpring, label = "onPrimary")
    val primaryContainer by animateColorAsState(target.primaryContainer, SynFixMotion.EffectsColorSpring, label = "primaryContainer")
    val onPrimaryContainer by animateColorAsState(target.onPrimaryContainer, SynFixMotion.EffectsColorSpring, label = "onPrimaryContainer")
    val secondary by animateColorAsState(target.secondary, SynFixMotion.EffectsColorSpring, label = "secondary")
    val onSecondary by animateColorAsState(target.onSecondary, SynFixMotion.EffectsColorSpring, label = "onSecondary")
    val secondaryContainer by animateColorAsState(target.secondaryContainer, SynFixMotion.EffectsColorSpring, label = "secondaryContainer")
    val onSecondaryContainer by animateColorAsState(target.onSecondaryContainer, SynFixMotion.EffectsColorSpring, label = "onSecondaryContainer")
    val tertiary by animateColorAsState(target.tertiary, SynFixMotion.EffectsColorSpring, label = "tertiary")
    val onTertiary by animateColorAsState(target.onTertiary, SynFixMotion.EffectsColorSpring, label = "onTertiary")
    val tertiaryContainer by animateColorAsState(target.tertiaryContainer, SynFixMotion.EffectsColorSpring, label = "tertiaryContainer")
    val onTertiaryContainer by animateColorAsState(target.onTertiaryContainer, SynFixMotion.EffectsColorSpring, label = "onTertiaryContainer")
    val background by animateColorAsState(target.background, SynFixMotion.EffectsColorSpring, label = "background")
    val onBackground by animateColorAsState(target.onBackground, SynFixMotion.EffectsColorSpring, label = "onBackground")
    val surface by animateColorAsState(target.surface, SynFixMotion.EffectsColorSpring, label = "surface")
    val onSurface by animateColorAsState(target.onSurface, SynFixMotion.EffectsColorSpring, label = "onSurface")
    val surfaceVariant by animateColorAsState(target.surfaceVariant, SynFixMotion.EffectsColorSpring, label = "surfaceVariant")
    val onSurfaceVariant by animateColorAsState(target.onSurfaceVariant, SynFixMotion.EffectsColorSpring, label = "onSurfaceVariant")
    val surfaceContainerLow by animateColorAsState(target.surfaceContainerLow, SynFixMotion.EffectsColorSpring, label = "surfaceContainerLow")
    val surfaceContainer by animateColorAsState(target.surfaceContainer, SynFixMotion.EffectsColorSpring, label = "surfaceContainer")
    val surfaceContainerHigh by animateColorAsState(target.surfaceContainerHigh, SynFixMotion.EffectsColorSpring, label = "surfaceContainerHigh")
    val surfaceContainerHighest by animateColorAsState(target.surfaceContainerHighest, SynFixMotion.EffectsColorSpring, label = "surfaceContainerHighest")

    return target.copy(
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
        background = background,
        onBackground = onBackground,
        surface = surface,
        onSurface = onSurface,
        surfaceVariant = surfaceVariant,
        onSurfaceVariant = onSurfaceVariant,
        surfaceContainerLowest = background,
        surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer,
        surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest
    )
}
