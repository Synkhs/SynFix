package com.example.ui.screens.pro

import androidx.compose.animation.core.AnimationState
import androidx.compose.animation.core.animateDecay
import androidx.compose.animation.core.exponentialDecay
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.Song
import com.example.ui.theme.MorphPolygonShape
import com.example.ui.theme.SynFixPolygons
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * #25 Physics Spin-Wheel Queue:
 * Expressive fortune wheel with Canvas sectors, M3 polygon center hub,
 * realistic angular friction via DecayAnimation, and haptic sector ticks.
 */
@Composable
fun PhysicsSpinWheelDialog(
    songs: List<Song>,
    onPlaySelected: (Song, List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    val pool = remember(songs) { songs.shuffled().take(8) }
    var wheelAngle by remember { mutableFloatStateOf(0f) }
    var isSpinning by remember { mutableStateOf(false) }
    var selectedSong by remember { mutableStateOf<Song?>(null) }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hubShape = remember { MorphPolygonShape(androidx.graphics.shapes.Morph(SynFixPolygons.clover(), SynFixPolygons.sunburst()), 0.35f) }

    val sectorColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer,
        MaterialTheme.colorScheme.surfaceContainerHighest
    )
    val onTextColors = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer,
        MaterialTheme.colorScheme.onSurface
    )

    fun spinWheel() {
        if (pool.isEmpty() || isSpinning) return
        scope.launch {
            isSpinning = true
            selectedSong = null
            val initialVel = Random.nextFloat() * 1400f + 1800f
            val animState = AnimationState(initialValue = wheelAngle, initialVelocity = initialVel)
            var lastSector = -1
            val sectorDeg = 360f / pool.size

            animState.animateDecay(exponentialDecay(frictionMultiplier = 0.72f)) {
                wheelAngle = value % 360f
                val normalizedTop = ((270f - wheelAngle) % 360f + 360f) % 360f
                val sector = (normalizedTop / sectorDeg).toInt().coerceIn(0, pool.lastIndex)
                if (sector != lastSector) {
                    lastSector = sector
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
            }

            val finalNorm = ((270f - wheelAngle) % 360f + 360f) % 360f
            val winIndex = (finalNorm / sectorDeg).toInt().coerceIn(0, pool.lastIndex)
            selectedSong = pool[winIndex]
            isSpinning = false
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 8.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text("Ruleta Física de Cola", style = MaterialTheme.typography.headlineSmall)

                if (pool.isEmpty()) {
                    Text(
                        "Agrega canciones a tu biblioteca para girar la ruleta.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Box(
                        modifier = Modifier.size(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val pointerColor = MaterialTheme.colorScheme.primary
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val radius = size.minDimension / 2f - 10.dp.toPx()
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val sweep = 360f / pool.size

                            rotate(degrees = wheelAngle, pivot = center) {
                                pool.forEachIndexed { idx, s ->
                                    val start = idx * sweep
                                    val bg = sectorColors[idx % sectorColors.size]
                                    drawArc(
                                        color = bg,
                                        startAngle = start,
                                        sweepAngle = sweep,
                                        useCenter = true,
                                        topLeft = Offset(center.x - radius, center.y - radius),
                                        size = Size(radius * 2f, radius * 2f)
                                    )
                                    val midRad = Math.toRadians((start + sweep / 2f).toDouble())
                                    val tx = center.x + (radius * 0.62f * cos(midRad)).toFloat()
                                    val ty = center.y + (radius * 0.62f * sin(midRad)).toFloat()
                                    val paintColor = onTextColors[idx % onTextColors.size]
                                    drawContext.canvas.nativeCanvas.apply {
                                        val paint = android.graphics.Paint().apply {
                                            color = android.graphics.Color.argb(
                                                (paintColor.alpha * 255).toInt(),
                                                (paintColor.red * 255).toInt(),
                                                (paintColor.green * 255).toInt(),
                                                (paintColor.blue * 255).toInt()
                                            )
                                            textSize = 24f
                                            textAlign = android.graphics.Paint.Align.CENTER
                                            isAntiAlias = true
                                        }
                                        val shortTitle = if (s.title.length > 9) s.title.take(8) + "…" else s.title
                                        drawText(shortTitle, tx, ty + 8f, paint)
                                    }
                                }
                            }

                            // Top indicator needle
                            drawCircle(
                                color = pointerColor,
                                radius = 8.dp.toPx(),
                                center = Offset(center.x, 8.dp.toPx())
                            )
                        }

                        Surface(
                            onClick = { spinWheel() },
                            shape = hubShape,
                            color = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.size(58.dp).testTag("spin_wheel_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Filled.Casino, contentDescription = "Girar ruleta")
                            }
                        }
                    }

                    selectedSong?.let { win ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        win.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        win.artist,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                        maxLines = 1
                                    )
                                }
                                FilledTonalButton(onClick = { onPlaySelected(win, pool) }) {
                                    Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Sonar")
                                }
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cerrar") }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = { spinWheel() }, enabled = pool.isNotEmpty() && !isSpinning) {
                        Text(if (isSpinning) "Girando..." else "Girar Ruleta")
                    }
                }
            }
        }
    }
}
