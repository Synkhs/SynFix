@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.example.ui.components

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import com.example.ui.theme.SynFixMotion

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedVisibilityScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/**
 * #26 Shared Element Liquid Morphing helper:
 * Binds sharedBounds / sharedElement smoothly using M3 Expressive spatial springs
 * when rendered inside SharedTransitionLayout + NavHost AnimatedVisibilityScope.
 */
@Composable
fun Modifier.synFixSharedElement(key: String): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    val animScope = LocalAnimatedVisibilityScope.current
    if (sharedScope == null || animScope == null) return this
    return with(sharedScope) {
        this@synFixSharedElement.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animScope,
            boundsTransform = { _, _ -> SynFixMotion.spatialSpring() }
        )
    }
}
