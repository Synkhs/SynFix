package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.example.domain.model.Song
import com.example.ui.theme.SynFixMotion
import kotlin.math.roundToInt

/**
 * #23 M3 Floating Mini-Bubble:
 * Draggable floating mini-player bubble with screen-edge collision physics
 * and animated magnetic edge snapping purely inside Modifier.graphicsLayer.
 */
@Composable
fun FloatingMiniBubble(
    song: Song,
    isPlaying: Boolean,
    useProceduralFallback: Boolean,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val bubbleWidthPx = with(density) { 164.dp.toPx() }
        val bubbleHeightPx = with(density) { 64.dp.toPx() }
        val marginPx = with(density) { 12.dp.toPx() }
        val topSafePx = with(density) { 88.dp.toPx() }
        val bottomSafePx = with(density) { 96.dp.toPx() }

        val maxX = (constraints.maxWidth.toFloat() - bubbleWidthPx - marginPx).coerceAtLeast(marginPx)
        val maxY = (constraints.maxHeight.toFloat() - bubbleHeightPx - bottomSafePx).coerceAtLeast(topSafePx)

        var rawX by remember(constraints.maxWidth) { mutableFloatStateOf(maxX) }
        var rawY by remember(constraints.maxHeight) { mutableFloatStateOf(maxY - with(density) { 84.dp.toPx() }) }
        var isDragging by remember { mutableStateOf(false) }

        val animX by animateFloatAsState(
            targetValue = rawX,
            animationSpec = SynFixMotion.fastSpatialSpring(),
            label = "bubbleX"
        )
        val animY by animateFloatAsState(
            targetValue = rawY,
            animationSpec = SynFixMotion.fastSpatialSpring(),
            label = "bubbleY"
        )
        val dragScale by animateFloatAsState(
            targetValue = if (isDragging) 1.07f else 1f,
            animationSpec = SynFixMotion.fastSpatialSpring(),
            label = "bubbleScale"
        )

        Surface(
            shape = RoundedCornerShape(32.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.96f),
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = 10.dp,
            shadowElevation = 12.dp,
            modifier = Modifier
                .width(164.dp)
                .height(64.dp)
                .graphicsLayer {
                    translationX = animX
                    translationY = animY
                    scaleX = dragScale
                    scaleY = dragScale
                }
                .pointerInput(maxX, maxY) {
                    detectDragGestures(
                        onDragStart = {
                            isDragging = true
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        },
                        onDragEnd = {
                            isDragging = false
                            // Magnetic snap to nearest horizontal edge with gentle bounce
                            val center = rawX + bubbleWidthPx / 2f
                            rawX = if (center < constraints.maxWidth / 2f) marginPx else maxX
                            rawY = rawY.coerceIn(topSafePx, maxY)
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        },
                        onDragCancel = {
                            isDragging = false
                            rawX = if (rawX < constraints.maxWidth / 2f) marginPx else maxX
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            rawX = (rawX + dragAmount.x).coerceIn(marginPx, maxX)
                            rawY = (rawY + dragAmount.y).coerceIn(topSafePx, maxY)
                        }
                    )
                }
                .border(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f), RoundedCornerShape(32.dp))
                .clickable(onClick = onExpand)
                .testTag("floating_mini_bubble")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                ArtworkImage(
                    artworkUri = song.artworkUri,
                    contentDescription = song.album,
                    targetSizeDp = 46.dp,
                    circular = true,
                    proceduralSeedKey = "${song.album}|${song.artist}",
                    useProceduralFallback = useProceduralFallback,
                    modifier = Modifier
                        .size(46.dp)
                        .clip(CircleShape)
                        .synFixSharedElement("shared_now_playing_art")
                )

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.size(44.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente"
                    )
                }
            }
        }
    }
}
