package com.example.ui.screens.pro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.worker.CamelotWheelAnalyzer
import com.example.domain.model.ContextTimeSlot
import com.example.domain.model.Song
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * #30 Harmonic Key & Camelot Wheel + #37 Context-Aware Time Mixes Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CamelotAndTimeMixSheet(
    selectedCamelotKey: String,
    harmonicMatches: ImmutableList<Song>,
    contextTimeSlot: ContextTimeSlot,
    contextMixSongs: ImmutableList<Song>,
    onSelectCamelotKey: (String) -> Unit,
    onScheduleCamelotScan: () -> Unit,
    onSelectTimeSlot: (ContextTimeSlot) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    var modeTab by remember { mutableIntStateOf(0) } // 0 = Camelot, 1 = Context Time Mixes
    val compatibleKeys = remember(selectedCamelotKey) {
        CamelotWheelAnalyzer.getCompatibleKeys(selectedCamelotKey)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = if (modeTab == 0) "Rueda Armónica Camelot" else "Mezclas Contextuales por Hora",
                style = MaterialTheme.typography.headlineSmall
            )

            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = modeTab == 0,
                    onClick = { modeTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(0, 2)
                ) { Text("Camelot DJ") }
                SegmentedButton(
                    selected = modeTab == 1,
                    onClick = { modeTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(1, 2)
                ) { Text("Mix por Horario") }
            }

            if (modeTab == 0) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "$selectedCamelotKey • ${CamelotWheelAnalyzer.getKeyDisplayName(selectedCamelotKey)}",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Compatibles: ${compatibleKeys.joinToString(" • ")}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    FilledTonalButton(onClick = onScheduleCamelotScan) {
                        Text("Analizar al cargar")
                    }
                }

                // Interactive Camelot 12-hour wheel
                val primary = MaterialTheme.colorScheme.primary
                val secondary = MaterialTheme.colorScheme.secondaryContainer
                val outline = MaterialTheme.colorScheme.outlineVariant

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier
                            .size(185.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { offset ->
                                    val cx = size.width / 2f
                                    val cy = size.height / 2f
                                    val dx = offset.x - cx
                                    val dy = offset.y - cy
                                    val dist = hypot(dx, dy)
                                    val maxR = minOf(size.width, size.height) / 2f
                                    val letter = if (dist < maxR * 0.62f) 'A' else 'B'
                                    var angle = ((atan2(dy, dx) * (180.0 / PI)) + 90.0 + 360.0) % 360.0
                                    val hour = ((angle / 30.0).toInt() % 12) + 1
                                    onSelectCamelotKey("$hour$letter")
                                }
                            }
                    ) {
                        val center = Offset(size.width / 2f, size.height / 2f)
                        val outerR = size.minDimension / 2f - 6.dp.toPx()
                        val innerR = outerR * 0.62f

                        drawCircle(outline, radius = outerR, center = center, style = Stroke(2.dp.toPx()))
                        drawCircle(outline, radius = innerR, center = center, style = Stroke(2.dp.toPx()))

                        for (n in 1..12) {
                            val angleRad = Math.toRadians(((n - 1) * 30.0 - 90.0))
                            val bPos = Offset(
                                center.x + (outerR * 0.81f * cos(angleRad)).toFloat(),
                                center.y + (outerR * 0.81f * sin(angleRad)).toFloat()
                            )
                            val aPos = Offset(
                                center.x + (innerR * 0.72f * cos(angleRad)).toFloat(),
                                center.y + (innerR * 0.72f * sin(angleRad)).toFloat()
                            )

                            val keyB = "${n}B"
                            val keyA = "${n}A"
                            drawCircle(
                                color = if (keyB == selectedCamelotKey) primary else if (keyB in compatibleKeys) secondary else outline.copy(alpha = 0.4f),
                                radius = 12.dp.toPx(),
                                center = bPos
                            )
                            drawCircle(
                                color = if (keyA == selectedCamelotKey) primary else if (keyA in compatibleKeys) secondary else outline.copy(alpha = 0.4f),
                                radius = 10.dp.toPx(),
                                center = aPos
                            )
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier.heightIn(max = 220.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (harmonicMatches.isEmpty()) {
                        item {
                            Text(
                                text = "Toca una posición de la rueda para ver mezclas armónicas en tu biblioteca.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(harmonicMatches, key = { it.id }) { song ->
                            SheetSongMiniRow(
                                song = song,
                                badge = "${song.camelotKey} • ${song.bpm} BPM",
                                onClick = { onPlaySong(song, harmonicMatches) }
                            )
                        }
                    }
                }
            } else {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ContextTimeSlot.entries.forEach { slot ->
                        FilterChip(
                            selected = contextTimeSlot == slot,
                            onClick = { onSelectTimeSlot(slot) },
                            leadingIcon = { Icon(Icons.Filled.Schedule, contentDescription = null, modifier = Modifier.size(16.dp)) },
                            label = { Text(slot.label) }
                        )
                    }
                }

                Text(
                    text = contextTimeSlot.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                LazyColumn(
                    modifier = Modifier.heightIn(max = 280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(contextMixSongs, key = { it.id }) { song ->
                        SheetSongMiniRow(
                            song = song,
                            badge = "${song.bpm} BPM",
                            onClick = { onPlaySong(song, contextMixSongs) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetSongMiniRow(
    song: Song,
    badge: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ArtworkImage(
                artworkUri = song.artworkUri,
                contentDescription = song.album,
                targetSizeDp = 42.dp,
                proceduralSeedKey = "${song.album}|${song.artist}",
                modifier = Modifier.size(42.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            Text(badge, style = ExpressiveTypography.MonoTimecode, color = MaterialTheme.colorScheme.primary)
        }
    }
}
