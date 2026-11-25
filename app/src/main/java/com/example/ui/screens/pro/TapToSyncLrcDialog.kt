package com.example.ui.screens.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.domain.model.Song
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography

/**
 * #38 Tap-to-Sync LRC Maker:
 * Interactive lyric synchronizer where tapping stamps [mm:ss.xx] onto each verse
 * at the live playback position and saves directly to Room.
 */
@Composable
fun TapToSyncLrcDialog(
    song: Song,
    positionMsProvider: () -> Long,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSaveLyrics: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var rawText by remember(song.id) {
        val clean = song.embeddedLyrics.orEmpty()
            .replace(Regex("\\[\\d{1,2}:\\d{2}(?:[.:]\\d{1,3})?]"), "")
            .trim()
        mutableStateOf(clean.ifBlank { "Escribe o pega la letra aquí\nUna línea por cada verso\nToca Sincronizar mientras suena" })
    }
    var syncingMode by remember { mutableStateOf(false) }
    var activeIndex by remember { mutableIntStateOf(0) }
    val lines = remember(rawText) { rawText.lines().map { it.trim() }.filter { it.isNotEmpty() } }
    val timestamps = remember(song.id, lines.size) { mutableStateListOf<Long?>().apply { repeat(lines.size) { add(null) } } }

    fun formatLrcTag(ms: Long): String {
        val totalSec = ms / 1000L
        val min = totalSec / 60L
        val sec = totalSec % 60L
        val cs = (ms % 1000L) / 10L
        return String.format("[%02d:%02d.%02d]", min, sec, cs)
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Tap-to-Sync LRC", style = MaterialTheme.typography.headlineSmall)
                        Text(song.title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = onTogglePlayPause) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = if (isPlaying) "Pausar" else "Reproducir")
                    }
                }

                if (!syncingMode) {
                    OutlinedTextField(
                        value = rawText,
                        onValueChange = { rawText = it },
                        label = { Text("Letra en texto plano (1 verso por línea)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = onDismiss) { Text("Cancelar") }
                        Button(
                            onClick = {
                                timestamps.clear()
                                repeat(lines.size) { timestamps.add(null) }
                                activeIndex = 0
                                syncingMode = true
                            },
                            enabled = lines.isNotEmpty()
                        ) {
                            Text("Comenzar Sync (${lines.size} versos)")
                        }
                    }
                } else {
                    Text(
                        text = "Tiempo actual: ${formatDuration(positionMsProvider())} • Verso ${activeIndex + 1}/${lines.size}",
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.primary
                    )

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        itemsIndexed(lines) { idx, line ->
                            val isCurrent = idx == activeIndex
                            val stamped = timestamps.getOrNull(idx)
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerLow,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(line, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                    if (stamped != null) {
                                        Text(
                                            text = formatLrcTag(stamped),
                                            style = ExpressiveTypography.MonoTimecode,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (activeIndex in lines.indices) {
                                timestamps[activeIndex] = positionMsProvider()
                                if (activeIndex < lines.lastIndex) activeIndex++
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                    ) {
                        Icon(Icons.Filled.TouchApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Marcar Verso Actual")
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        TextButton(onClick = { syncingMode = false }) { Text("Editar texto") }
                        Button(onClick = {
                            val builtLrc = lines.mapIndexed { idx, line ->
                                val ts = timestamps.getOrNull(idx) ?: (idx * 3000L)
                                "${formatLrcTag(ts)} $line"
                            }.joinToString("\n")
                            onSaveLyrics(song.id, builtLrc)
                            onDismiss()
                        }) {
                            Text("Guardar LRC")
                        }
                    }
                }
            }
        }
    }
}
