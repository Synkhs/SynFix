package com.example.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset

/**
 * Material 3 Expressive spring physics:
 * - Spatial springs: expressive bounce for screen transitions, mini-player expansion, and shape morphing.
 * - Effects springs: critically damped smooth transitions for color, alpha, and indicator states.
 */
object SynFixMotion {
    fun <T> spatialSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )

    fun <T> fastSpatialSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.76f,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> slowSpatialSpring(): SpringSpec<T> = spring(
        dampingRatio = 0.80f,
        stiffness = Spring.StiffnessLow
    )

    fun <T> effectsSpring(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val SpatialIntOffsetSpring: FiniteAnimationSpec<IntOffset> = spring(
        dampingRatio = 0.74f,
        stiffness = Spring.StiffnessMediumLow
    )

    val SpatialDpSpring: FiniteAnimationSpec<Dp> = spring(
        dampingRatio = 0.72f,
        stiffness = Spring.StiffnessMediumLow
    )

    val EffectsColorSpring: FiniteAnimationSpec<Color> = spring(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessLow
    )
}

val LocalSynFixMotion = staticCompositionLocalOf { SynFixMotion }
