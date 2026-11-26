package com.example.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.SleepTimerState
import com.example.domain.model.Song
import com.example.ui.components.ArtworkImage
import com.example.ui.components.CardStackQueue
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepTimerSheet(
    state: SleepTimerState,
    onStartTimer: (minutes: Int, stopAfterTrack: Boolean) -> Unit,
    onStartTrackTimer: (trackCount: Int) -> Unit = {},
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit
) {
    var customMinutes by remember { mutableFloatStateOf(25f) }
    var trackCount by remember { mutableIntStateOf(3) }

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
                Icon(Icons.Filled.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Temporizador de Apagado", style = MaterialTheme.typography.titleLarge)
            }

            if (state.isActive) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    state.remainingTracks > 0 -> "Quedan ${state.remainingTracks} pista(s)"
                                    state.stopAfterCurrentTrack -> "Al finalizar la canción actual"
                                    else -> "Restante: ${formatDuration(state.remainingMs)}"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = if (state.isFadingOut) "Reduciendo volumen gradualmente..." else "Fade-out automático en los últimos 15s",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                            )
                        }
                        FilledTonalButton(onClick = {
                            onCancelTimer()
                            onDismiss()
                        }) {
                            Text("Cancelar")
                        }
                    }
                }
            }

            Text("Preajustes rápidos", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(10, 15, 30, 45, 60).forEach { mins ->
                    FilledTonalButton(
                        onClick = {
                            onStartTimer(mins, false)
                            onDismiss()
                        }
                    ) {
                        Text("$mins min")
                    }
                }
                OutlinedButton(
                    onClick = {
                        onStartTimer(0, true)
                        onDismiss()
                    }
                ) {
                    Text("Fin de canción")
                }
            }

            // #83 Track-Count Sleep Timer
            HorizontalDivider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Por número de pistas", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "Detener automáticamente tras $trackCount pista(s)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilledTonalIconButton(
                        onClick = { if (trackCount > 1) trackCount-- },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Remove, contentDescription = "Menos pistas", modifier = Modifier.size(18.dp))
                    }
                    Text(
                        text = "$trackCount",
                        style = ExpressiveTypography.MonoTimecode,
                        modifier = Modifier.widthIn(min = 24.dp)
                    )
                    FilledTonalIconButton(
                        onClick = { if (trackCount < 20) trackCount++ },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Más pistas", modifier = Modifier.size(18.dp))
                    }
                }
            }
            FilledTonalButton(
                onClick = {
                    onStartTrackTimer(trackCount)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Apagar después de $trackCount pista(s)")
            }

            HorizontalDivider()
            Text(
                text = "Personalizado: ${customMinutes.roundToInt()} min",
                style = MaterialTheme.typography.labelLarge
            )
            Slider(
                value = customMinutes,
                onValueChange = { customMinutes = it },
                valueRange = 5f..120f
            )
            Button(
                onClick = {
                    onStartTimer(customMinutes.roundToInt(), false)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Iniciar temporizador de ${customMinutes.roundToInt()} min")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueBottomSheet(
    queue: ImmutableList<Song>,
    currentIndex: Int,
    pinnedIds: Set<Long> = emptySet(),
    onTogglePin: (Long) -> Unit = {},
    onSmartShufflePreservingPins: () -> Unit = {},
    onJumpToIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var stackViewMode by remember { mutableStateOf(true) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Cola de reproducción", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "${queue.size} pistas • ${pinnedIds.size} ancladas",
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    // #73 Smart Shuffle preserving pinned anchors
                    FilledTonalButton(
                        onClick = onSmartShufflePreservingPins,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.Shuffle, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Mezclar", style = MaterialTheme.typography.labelMedium)
                    }

                    FilterChip(
                        selected = stackViewMode,
                        onClick = { stackViewMode = !stackViewMode },
                        leadingIcon = { Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp)) },
                        label = { Text(if (stackViewMode) "Pila" else "Lista") }
                    )
                }
            }
            HorizontalDivider()

            if (stackViewMode) {
                CardStackQueue(
                    queue = queue,
                    currentIndex = currentIndex,
                    onJumpToIndex = onJumpToIndex,
                    onRemoveIndex = onRemoveIndex,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)
                )
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
            }

            LazyColumn(modifier = Modifier.heightIn(max = if (stackViewMode) 260.dp else 440.dp)) {
                itemsIndexed(
                    items = queue,
                    key = { index, song -> "${index}_${song.id}" },
                    contentType = { _, _ -> "queue_item" }
                ) { index, song ->
                    val isCurrent = index == currentIndex
                    val isPinned = song.id in pinnedIds
                    ListItem(
                        headlineContent = {
                            Text(
                                text = song.title,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        supportingContent = {
                            Text(song.artist, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        leadingContent = {
                            ArtworkImage(
                                artworkUri = song.artworkUri,
                                contentDescription = song.album,
                                targetSizeDp = 44.dp,
                                proceduralSeedKey = "${song.album}|${song.artist}",
                                modifier = Modifier.size(44.dp)
                            )
                        },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                IconButton(onClick = { onTogglePin(song.id) }) {
                                    Icon(
                                        imageVector = Icons.Filled.PushPin,
                                        contentDescription = if (isPinned) "Desanclar pista" else "Fijar posición",
                                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    )
                                }
                                if (isCurrent) {
                                    Icon(
                                        Icons.Filled.MusicNote,
                                        contentDescription = "Reproduciendo",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                } else {
                                    IconButton(onClick = { onRemoveIndex(index) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Quitar de la cola")
                                    }
                                }
                            }
                        },
                        modifier = Modifier.clickable { onJumpToIndex(index) }
                    )
                }
            }
        }
    }
}
