package com.example.ui.screens.pro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Song
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

/**
 * #11 Mood & Tempo Radar 2D:
 * Circular 2D touch pad mapping Chill/Intenso (Y) vs BPM 60..180 (X),
 * backed by indexed Room SQL queries.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoodRadarSheet(
    currentBpm: Int,
    currentEnergy: Float,
    matches: ImmutableList<Song>,
    onQueryMood: (bpm: Int, energy: Float) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    var normX by remember(currentBpm) { mutableFloatStateOf(((currentBpm - 60f) / 120f).coerceIn(0f, 1f)) }
    var normY by remember(currentEnergy) { mutableFloatStateOf((1f - currentEnergy).coerceIn(0f, 1f)) }

    val selectedBpm = (60 + (normX * 120f)).roundToInt()
    val selectedEnergy = (1f - normY).coerceIn(0.05f, 0.98f)

    LaunchedEffect(Unit) {
        if (matches.isEmpty()) onQueryMood(selectedBpm, selectedEnergy)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.Radar, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Radar Mood & Tempo 2D", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "$selectedBpm BPM • Energía ${(selectedEnergy * 100).roundToInt()}%",
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            val primary = MaterialTheme.colorScheme.primary
            val tertiary = MaterialTheme.colorScheme.tertiary
            val surfaceHigh = MaterialTheme.colorScheme.surfaceContainerHighest
            val outline = MaterialTheme.colorScheme.outlineVariant

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .size(210.dp)
                        .pointerInput(Unit) {
                            detectTapGestures { pos ->
                                normX = (pos.x / size.width.toFloat()).coerceIn(0f, 1f)
                                normY = (pos.y / size.height.toFloat()).coerceIn(0f, 1f)
                                val bpm = (60 + normX * 120f).roundToInt()
                                val eng = (1f - normY).coerceIn(0.05f, 0.98f)
                                onQueryMood(bpm, eng)
                            }
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragEnd = {
                                    val bpm = (60 + normX * 120f).roundToInt()
                                    val eng = (1f - normY).coerceIn(0.05f, 0.98f)
                                    onQueryMood(bpm, eng)
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    normX = (change.position.x / size.width.toFloat()).coerceIn(0f, 1f)
                                    normY = (change.position.y / size.height.toFloat()).coerceIn(0f, 1f)
                                }
                            )
                        }
                ) {
                    val radius = size.minDimension / 2f
                    val center = Offset(size.width / 2f, size.height / 2f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(primary.copy(alpha = 0.22f), surfaceHigh),
                            center = center,
                            radius = radius
                        ),
                        radius = radius,
                        center = center
                    )

                    // Concentric radar rings
                    listOf(0.33f, 0.66f, 1.0f).forEach { ring ->
                        drawCircle(
                            color = outline.copy(alpha = 0.55f),
                            radius = radius * ring,
                            center = center,
                            style = Stroke(width = 1.5.dp.toPx())
                        )
                    }

                    // Crosshair axes
                    drawLine(outline, Offset(0f, center.y), Offset(size.width, center.y), strokeWidth = 1.dp.toPx())
                    drawLine(outline, Offset(center.x, 0f), Offset(center.x, size.height), strokeWidth = 1.dp.toPx())

                    // Selected point indicator clamped inside circle
                    val target = Offset(normX * size.width, normY * size.height)
                    drawCircle(
                        color = tertiary.copy(alpha = 0.25f),
                        radius = 20.dp.toPx(),
                        center = target
                    )
                    drawCircle(
                        color = primary,
                        radius = 9.dp.toPx(),
                        center = target
                    )
                }

                // Quadrant edge labels
                Text(
                    "INTENSO",
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
                Text(
                    "CHILL",
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
                Text(
                    "60 BPM",
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterStart)
                )
                Text(
                    "180 BPM",
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Coincidencias (${matches.size})", style = MaterialTheme.typography.titleMedium)
                if (matches.isNotEmpty()) {
                    FilledTonalButton(onClick = { onPlaySong(matches.first(), matches) }) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mezclar todo")
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.heightIn(max = 220.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(items = matches, key = { it.id }) { song ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPlaySong(song, matches) }
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            ArtworkImage(
                                artworkUri = song.artworkUri,
                                contentDescription = song.album,
                                targetSizeDp = 44.dp,
                                proceduralSeedKey = "${song.album}|${song.artist}",
                                modifier = Modifier.size(44.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "${song.artist} • ${song.bpm} BPM",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
