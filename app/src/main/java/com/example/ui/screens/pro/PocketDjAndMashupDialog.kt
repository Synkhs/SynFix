package com.example.ui.screens.pro

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Song
import com.example.player.PocketDjState
import com.example.ui.components.ArtworkImage
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.roundToInt

/**
 * #44 Pocket DJ Dual-Deck Crossfader & #55 Acapella Mashup Lab:
 * Real dual ExoPlayer decks with equal-power crossfader, BPM tempo sync,
 * and 1-tap Mid (Vocals Deck A) + Side (Beat Deck B) real-time Mashup isolation.
 */
@Composable
fun PocketDjAndMashupDialog(
    songs: ImmutableList<Song>,
    djState: PocketDjState,
    onLoadDeckA: (Song) -> Unit,
    onLoadDeckB: (Song) -> Unit,
    onToggleDeckA: () -> Unit,
    onToggleDeckB: () -> Unit,
    onSeekDeckA: (Float) -> Unit,
    onSeekDeckB: (Float) -> Unit,
    onCrossfadeChange: (Float) -> Unit,
    onToggleMashup: (Boolean) -> Unit,
    onToggleSyncBpm: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    var pickingForDeck by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(songs.size) {
        if (songs.size >= 2 && djState.deckASong == null && djState.deckBSong == null) {
            onLoadDeckA(songs[0])
            onLoadDeckB(songs[1])
        } else if (songs.size == 1 && djState.deckASong == null) {
            onLoadDeckA(songs[0])
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Pocket DJ & Mashup Lab")
                Text(
                    "Motor Dual-Deck en RAM (se libera al salir)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                DeckMiniCard(
                    deckLabel = if (djState.acapellaMashupEnabled) "DECK A • VOCAL (MID)" else "DECK A",
                    song = djState.deckASong,
                    isPlaying = djState.deckAPlaying,
                    positionMs = djState.deckAPositionMs,
                    onPickSong = { pickingForDeck = "A" },
                    onTogglePlay = onToggleDeckA,
                    onSeek = onSeekDeckA
                )

                DeckMiniCard(
                    deckLabel = if (djState.acapellaMashupEnabled) "DECK B • BEAT (SIDE)" else "DECK B",
                    song = djState.deckBSong,
                    isPlaying = djState.deckBPlaying,
                    positionMs = djState.deckBPositionMs,
                    onPickSong = { pickingForDeck = "B" },
                    onTogglePlay = onToggleDeckB,
                    onSeek = onSeekDeckB
                )

                // Equal-power Crossfader
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("A (${((1f - djState.crossfader) * 100).roundToInt()}%)", style = ExpressiveTypography.MonoTimecode)
                            Text("CROSSFADER", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                            Text("B (${(djState.crossfader * 100).roundToInt()}%)", style = ExpressiveTypography.MonoTimecode)
                        }
                        Slider(
                            value = djState.crossfader,
                            onValueChange = onCrossfadeChange,
                            valueRange = 0f..1f
                        )
                    }
                }

                // #55 Acapella Mashup Mode + BPM Sync
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Mashup Acapella + Beat", style = MaterialTheme.typography.labelLarge)
                        Text(
                            "Aísla Mid (voz Deck A) + Side (base Deck B)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = djState.acapellaMashupEnabled, onCheckedChange = onToggleMashup)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Sincronizar BPM (Deck B → A)", style = MaterialTheme.typography.labelLarge)
                    Switch(checked = djState.syncBpmEnabled, onCheckedChange = onToggleSyncBpm)
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) { Text("Salir y Liberar") }
        }
    )

    pickingForDeck?.let { deck ->
        AlertDialog(
            onDismissRequest = { pickingForDeck = null },
            title = { Text("Elegir pista para Deck $deck") },
            text = {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(songs.take(80), key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${s.artist} • ${s.bpm} BPM") },
                            leadingContent = {
                                ArtworkImage(
                                    artworkUri = s.artworkUri,
                                    contentDescription = s.title,
                                    targetSizeDp = 40.dp,
                                    modifier = Modifier.size(40.dp)
                                )
                            },
                            modifier = Modifier.clickable {
                                if (deck == "A") onLoadDeckA(s) else onLoadDeckB(s)
                                pickingForDeck = null
                            }
                        )
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickingForDeck = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun DeckMiniCard(
    deckLabel: String,
    song: Song?,
    isPlaying: Boolean,
    positionMs: Long,
    onPickSong: () -> Unit,
    onTogglePlay: () -> Unit,
    onSeek: (Float) -> Unit
) {
    val dur = (song?.durationMs ?: 1L).coerceAtLeast(1L)
    val frac = (positionMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f)

    ElevatedCard(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(deckLabel, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (song != null) {
                    Text("${song.bpm} BPM • ${song.camelotKey}", style = ExpressiveTypography.MonoTimecode)
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(onClick = onTogglePlay, enabled = song != null, modifier = Modifier.size(44.dp)) {
                    Icon(if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = "Play/Pause")
                }
                Column(modifier = Modifier.weight(1f).clickable(onClick = onPickSong)) {
                    Text(
                        text = song?.title ?: "Toca para cargar pista",
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = song?.let { "${it.artist} • ${formatDuration(positionMs)}" } ?: "Biblioteca local",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                IconButton(onClick = onPickSong) {
                    Icon(Icons.Filled.SwapHoriz, contentDescription = "Cambiar pista")
                }
            }
            if (song != null) {
                Slider(value = frac, onValueChange = onSeek, valueRange = 0f..1f)
            }
        }
    }
}
