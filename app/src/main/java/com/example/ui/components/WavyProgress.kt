package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.ui.theme.SynFixMotion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlin.math.sin

fun formatDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}

/**
 * #3 Dynamic Amplitude Wavy Seekbar + #19 Timestamped Track Note Pins:
 * - Animates wave phase continuously while playing and flattens smoothly when paused.
 * - Modulates wave amplitude in real time using live RMS via a pre-allocated reusable FloatArray (zero allocations).
 * - Renders personal TrackNote timestamps directly on the Canvas as interactive pins.
 */
@Composable
fun LinearWavyProgressIndicator(
    progressProvider: () -> Float,
    isPlaying: Boolean,
    onSeekProgress: (Float) -> Unit,
    modifier: Modifier = Modifier,
    rmsProvider: () -> Float = { 0.45f },
    noteFractions: ImmutableList<Float> = persistentListOf(),
    energyHeatmap: ImmutableList<Float> = persistentListOf(),
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    notePinColor: Color = MaterialTheme.colorScheme.tertiary,
    hotColor: Color = MaterialTheme.colorScheme.tertiary,
    onScrubbingChanged: (Boolean, Float) -> Unit = { _, _ -> }
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    // Pre-allocated reusable ring buffer (zero per-frame allocations)
    val rmsRing = remember { FloatArray(16) { 0.45f } }
    var ringCursor by remember { mutableIntStateOf(0) }
    val wavePath = remember { Path() }
    val pinPath = remember { Path() }

    val amplitudeFactor by animateFloatAsState(
        targetValue = if (isPlaying && !isDragging) 1f else 0f,
        animationSpec = SynFixMotion.effectsSpring(),
        label = "waveAmplitude"
    )

    val thumbRadiusFactor by animateFloatAsState(
        targetValue = if (isDragging) 1.35f else 1f,
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "thumbRadius"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "wavyPhase")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    val latestOnSeek by rememberUpdatedState(onSeekProgress)
    val latestOnScrub by rememberUpdatedState(onScrubbingChanged)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(38.dp)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val fraction = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                    latestOnSeek(fraction)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        dragProgress = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                        latestOnScrub(true, dragProgress)
                    },
                    onDragEnd = {
                        latestOnSeek(dragProgress)
                        isDragging = false
                        latestOnScrub(false, dragProgress)
                    },
                    onDragCancel = {
                        isDragging = false
                        latestOnScrub(false, dragProgress)
                    },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragProgress = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                        latestOnScrub(true, dragProgress)
                    }
                )
            }
    ) {
        val width = size.width
        val centerY = size.height / 2f
        val activeProgress = (if (isDragging) dragProgress else progressProvider()).coerceIn(0f, 1f)
        val activeX = width * activeProgress

        // Push current RMS sample into reusable FloatArray without allocations
        val liveRms = rmsProvider().coerceIn(0f, 1f)
        rmsRing[ringCursor] = liveRms
        ringCursor = (ringCursor + 1) and 15

        var rmsAvg = 0f
        for (i in rmsRing.indices) rmsAvg += rmsRing[i]
        rmsAvg /= rmsRing.size

        // Inactive track (renders faint energy heatmap bars if precomputed #69)
        if (energyHeatmap.isNotEmpty()) {
            val count = energyHeatmap.size
            val segW = width / count.toFloat()
            for (i in 0 until count) {
                val sx = i * segW
                val ex = sx + segW * 0.78f
                if (ex > activeX) {
                    val startX = sx.coerceAtLeast(activeX)
                    val energy = energyHeatmap[i].coerceIn(0.1f, 1f)
                    drawLine(
                        color = androidx.compose.ui.graphics.lerp(trackColor, hotColor, energy * 0.45f)
                            .copy(alpha = 0.42f + energy * 0.32f),
                        start = Offset(startX, centerY),
                        end = Offset(ex.coerceAtLeast(startX), centerY),
                        strokeWidth = (2.6.dp.toPx() + energy * 3.0.dp.toPx()),
                        cap = StrokeCap.Round
                    )
                }
            }
        } else {
            drawLine(
                color = trackColor.copy(alpha = 0.55f),
                start = Offset(activeX, centerY),
                end = Offset(width, centerY),
                strokeWidth = 4.dp.toPx(),
                cap = StrokeCap.Round
            )
        }

        // Active RMS-modulated wavy path
        if (activeX > 1f) {
            val dynamicModulation = 0.52f + (rmsAvg * 1.15f)
            val maxAmplitude = (4.6.dp.toPx() * dynamicModulation).coerceAtMost(9.5.dp.toPx()) * amplitudeFactor
            val wavelength = 26.dp.toPx()

            wavePath.reset()
            wavePath.moveTo(0f, centerY)
            if (maxAmplitude < 0.25f) {
                wavePath.lineTo(activeX, centerY)
            } else {
                var x = 0f
                val step = 3.dp.toPx()
                while (x <= activeX) {
                    val edgeFade = ((activeX - x) / 14.dp.toPx()).coerceIn(0f, 1f)
                    val localBin = rmsRing[((x / width) * 15f).toInt().coerceIn(0, 15)]
                    val heatmapScale = if (energyHeatmap.isNotEmpty()) {
                        val hIdx = ((x / width) * (energyHeatmap.size - 1)).toInt().coerceIn(0, energyHeatmap.lastIndex)
                        0.68f + energyHeatmap[hIdx] * 0.65f
                    } else 1f
                    val localScale = (0.72f + localBin * 0.55f) * heatmapScale
                    val angle = (x / wavelength) * (2f * Math.PI.toFloat()) - phase
                    val y = centerY + sin(angle) * maxAmplitude * edgeFade * localScale
                    wavePath.lineTo(x, y)
                    x += step
                }
                wavePath.lineTo(activeX, centerY)
            }

            val currentEnergy = if (energyHeatmap.isNotEmpty()) {
                val idx = (activeProgress * (energyHeatmap.size - 1)).toInt().coerceIn(0, energyHeatmap.lastIndex)
                energyHeatmap[idx]
            } else 0.35f
            val activeStrokeColor = if (energyHeatmap.isNotEmpty()) {
                androidx.compose.ui.graphics.lerp(color, hotColor, (currentEnergy - 0.35f).coerceIn(0f, 1f))
            } else color

            drawPath(
                path = wavePath,
                color = activeStrokeColor,
                style = Stroke(width = 4.5.dp.toPx(), cap = StrokeCap.Round)
            )
        }

        // #19 Timestamped Track Note diamond pins
        val pinRadius = 4.5.dp.toPx()
        val pinTopOffset = 9.5.dp.toPx()
        for (idx in noteFractions.indices) {
            val frac = noteFractions[idx].coerceIn(0f, 1f)
            val px = width * frac
            val py = centerY - pinTopOffset
            pinPath.reset()
            pinPath.moveTo(px, py - pinRadius)
            pinPath.lineTo(px + pinRadius, py)
            pinPath.lineTo(px, py + pinRadius)
            pinPath.lineTo(px - pinRadius, py)
            pinPath.close()
            drawPath(path = pinPath, color = notePinColor)
            drawLine(
                color = notePinColor.copy(alpha = 0.7f),
                start = Offset(px, py + pinRadius),
                end = Offset(px, centerY),
                strokeWidth = 1.5.dp.toPx()
            )
        }

        // Expressive pill/dot thumb
        drawCircle(
            color = color,
            radius = 7.5.dp.toPx() * thumbRadiusFactor,
            center = Offset(activeX, centerY)
        )
    }
}

@Composable
fun MiniProgressBar(
    progressProvider: () -> Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant
) {
    Canvas(modifier = modifier.fillMaxWidth().height(3.dp)) {
        val progress = progressProvider().coerceIn(0f, 1f)
        drawLine(
            color = trackColor.copy(alpha = 0.4f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = size.height,
            cap = StrokeCap.Round
        )
        if (progress > 0f) {
            drawLine(
                color = color,
                start = Offset(0f, size.height / 2f),
                end = Offset(size.width * progress, size.height / 2f),
                strokeWidth = size.height,
                cap = StrokeCap.Round
            )
        }
    }
}
