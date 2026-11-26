package com.example.ui.screens.pro

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.tags.JunkCleanupCandidate
import com.example.domain.model.DspBatteryStats
import com.example.domain.model.QrServerState
import com.example.domain.model.Song
import com.example.player.BlindTestAbState
import com.example.player.LocalQrWebServer
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * #70 Tap-Tempo Finder:
 * Tap BPM calculator that filters local tracks matching the tapped tempo.
 */
@Composable
fun TapTempoFinderDialog(
    songs: ImmutableList<Song>,
    onPlaySong: (Song, List<Song>) -> Unit,
    onDismiss: () -> Unit
) {
    val tapTimes = remember { mutableStateListOf<Long>() }
    var detectedBpm by remember { mutableIntStateOf(120) }
    val haptics = LocalHapticFeedback.current

    val matches = remember(songs, detectedBpm) {
        songs.filter { abs(it.bpm - detectedBpm) <= 8 }
            .sortedBy { abs(it.bpm - detectedBpm) }
            .take(25)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tap-Tempo Finder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(
                    onClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        val now = System.currentTimeMillis()
                        if (tapTimes.isNotEmpty() && now - tapTimes.last() > 2200L) {
                            tapTimes.clear()
                        }
                        tapTimes.add(now)
                        if (tapTimes.size > 8) tapTimes.removeAt(0)
                        if (tapTimes.size >= 2) {
                            val intervals = tapTimes.zipWithNext { a, b -> b - a }.filter { it in 250L..2000L }
                            if (intervals.isNotEmpty()) {
                                val avgMs = intervals.average()
                                detectedBpm = (60_000.0 / avgMs).roundToInt().coerceIn(50, 210)
                            }
                        }
                    },
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth().height(64.dp)
                ) {
                    Text("PULSA AL RITMO • $detectedBpm BPM", style = ExpressiveTypography.MonoTimecode)
                }

                Text("Pistas cercanas (±8 BPM): ${matches.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                LazyColumn(modifier = Modifier.heightIn(max = 220.dp)) {
                    items(matches, key = { it.id }) { s ->
                        ListItem(
                            headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            supportingContent = { Text("${s.artist} • ${s.bpm} BPM") },
                            modifier = Modifier.clickable { onPlaySong(s, matches) }
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #76 Junk Tag & URL Stripper:
 * Previews Regex cleanup for [Official Video], URLs, and broken separators,
 * then executes a single atomic Room @Transaction.
 */
@Composable
fun JunkTagStripperDialog(
    candidates: ImmutableList<JunkCleanupCandidate>,
    onScan: () -> Unit,
    onApplyBulkCleanup: () -> Unit,
    onDismiss: () -> Unit
) {
    LaunchedEffect(Unit) { onScan() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Limpiador Junk Tags & URLs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (candidates.isEmpty()) {
                    Text("¡Biblioteca limpia! No se detectaron marcas de agua, URLs ni etiquetas [Official Video].")
                } else {
                    Text("Detectadas ${candidates.size} pistas con basura en metadatos:", style = MaterialTheme.typography.bodySmall)
                    LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                        items(candidates, key = { it.song.id }) { c ->
                            ListItem(
                                headlineContent = { Text(c.cleanedTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                supportingContent = { Text("Antes: ${c.song.title}", maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                    Button(onClick = onApplyBulkCleanup, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Limpiar ${candidates.size} pistas ahora")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}

/**
 * #77 Blind Test A/B:
 * Compares two versions (e.g. FLAC vs MP3) synchronized without visual cues until revealed.
 */
@Composable
fun BlindTestAbDialog(
    songs: ImmutableList<Song>,
    state: BlindTestAbState,
    onStartTest: (Song, Song) -> Unit,
    onSwitchSlot: (String) -> Unit,
    onTogglePlay: () -> Unit,
    onSubmitGuess: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var firstPick by remember { mutableStateOf<Song?>(songs.firstOrNull()) }
    var secondPick by remember { mutableStateOf<Song?>(songs.getOrNull(1)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Blind Test A/B Audiófilo") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (state.songA == null || state.songB == null) {
                    if (songs.size < 2) {
                        Text("Necesitas al menos 2 canciones locales para realizar una prueba ciega A/B.")
                    } else {
                        Text("Compara 2 archivos en el mismo milisegundo sin ver cuál es cuál.", style = MaterialTheme.typography.bodySmall)
                        Text("Archivo 1: ${firstPick?.title ?: "--"} (${firstPick?.bitrateKbps ?: 0} kbps)", style = MaterialTheme.typography.labelMedium)
                        Text("Archivo 2: ${secondPick?.title ?: "--"} (${secondPick?.bitrateKbps ?: 0} kbps)", style = MaterialTheme.typography.labelMedium)
                        LazyColumn(modifier = Modifier.heightIn(max = 170.dp)) {
                            items(songs.take(40), key = { it.id }) { s ->
                                ListItem(
                                    headlineContent = { Text(s.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                                    supportingContent = { Text("${s.bitrateKbps} kbps • ${s.mimeType}") },
                                    modifier = Modifier.clickable {
                                        if (firstPick == null || (firstPick != s && secondPick != null)) {
                                            firstPick = s
                                            secondPick = null
                                        } else {
                                            secondPick = s
                                        }
                                    }
                                )
                            }
                        }
                        Button(
                            onClick = {
                                val f = firstPick
                                val s = secondPick
                                if (f != null && s != null) onStartTest(f, s)
                            },
                            enabled = firstPick != null && secondPick != null,
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Iniciar Blind Test Sincronizado") }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        listOf("A", "B").forEach { slot ->
                            val selected = state.activeSlot == slot
                            if (selected) {
                                Button(onClick = { onSwitchSlot(slot) }, modifier = Modifier.weight(1f)) {
                                    Text("Escuchando $slot")
                                }
                            } else {
                                OutlinedButton(onClick = { onSwitchSlot(slot) }, modifier = Modifier.weight(1f)) {
                                    Text("Cambiar a $slot")
                                }
                            }
                        }
                    }
                    OutlinedButton(onClick = onTogglePlay, modifier = Modifier.fillMaxWidth()) {
                        Icon(if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (state.isPlaying) "Pausar ambos" else "Reanudar ambos")
                    }

                    if (!state.revealed) {
                        Text("¿Cuál suena con mayor fidelidad?", style = MaterialTheme.typography.labelLarge)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilledTonalButton(onClick = { onSubmitGuess("A") }, modifier = Modifier.weight(1f)) { Text("Votar A") }
                            FilledTonalButton(onClick = { onSubmitGuess("B") }, modifier = Modifier.weight(1f)) { Text("Votar B") }
                        }
                    } else {
                        val a = state.songA
                        val b = state.songB
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text("Resultado Revelado (Tu voto: ${state.userGuessHigherQuality})", style = MaterialTheme.typography.labelLarge)
                                Text("A: ${a.title} (${a.bitrateKbps} kbps, ${a.sampleRateHz} Hz)", style = MaterialTheme.typography.bodySmall)
                                Text("B: ${b.title} (${b.bitrateKbps} kbps, ${b.sampleRateHz} Hz)", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Salir y Liberar") } }
    )
}

/**
 * #80 Local QR Web Server Dialog:
 * Generates ZXing QR bitmap pointing to temporary local HTTP server.
 */
@Composable
fun LocalQrWebServerDialog(
    state: QrServerState,
    onStartServer: () -> Unit,
    onStopServer: () -> Unit,
    onDismiss: () -> Unit
) {
    val qrBitmap: Bitmap? = remember(state.url) {
        if (state.isRunning && state.url.isNotBlank()) LocalQrWebServer.generateQrBitmap(state.url, 440)
        else null
    }

    AlertDialog(
        onDismissRequest = {
            onStopServer()
            onDismiss()
        },
        title = { Text("Servidor QR Wi-Fi Local") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (!state.isRunning) {
                    Text(
                        "Transfiere música desde cualquier navegador en la misma red Wi-Fi sin cables ni internet.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onStartServer, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Encender Servidor Temporal")
                    }
                } else {
                    qrBitmap?.let { bmp ->
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Código QR de acceso local",
                            modifier = Modifier.size(190.dp)
                        )
                    }
                    Text(state.url, style = ExpressiveTypography.MonoTimecode, color = MaterialTheme.colorScheme.primary)
                    Text("Archivos recibidos: ${state.uploadedCount}", style = MaterialTheme.typography.labelMedium)
                    OutlinedButton(onClick = onStopServer, modifier = Modifier.fillMaxWidth()) {
                        Text("Apagar Servidor")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onStopServer()
                onDismiss()
            }) { Text("Cerrar") }
        }
    )
}

/**
 * #81 Export / Import Backup .synfix + #85 DSP & Battery Efficiency Center.
 */
@Composable
fun BackupAndEfficiencyDialog(
    stats: DspBatteryStats,
    onExportBackup: (Uri) -> Unit,
    onImportBackup: (Uri) -> Unit,
    onDismiss: () -> Unit
) {
    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) onExportBackup(uri)
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) onImportBackup(uri)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backup .synfix & Eficiencia DSP") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // #85 Battery & Offload Telemetry
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("Centro de Eficiencia DSP", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        Text(
                            "Audio Offload Hardware: ${stats.offloadPercentage}% (${stats.offloadSeconds / 60}m vs ${stats.dspSeconds / 60}m DSP)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Text(
                            "Ahorro estimado: ${String.format("%.1f mAh", stats.estimatedBatterySavedMah)}",
                            style = ExpressiveTypography.MonoTimecode,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (stats.formatBreakdown.isNotEmpty()) {
                    Text("Espacio por formato de audio", style = MaterialTheme.typography.labelLarge)
                    stats.formatBreakdown.take(5).forEach { fmt ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${fmt.format} (${fmt.trackCount} pistas)", style = MaterialTheme.typography.bodySmall)
                            Text(String.format("%.1f MB", fmt.totalMb), style = ExpressiveTypography.MonoTimecode)
                        }
                    }
                }

                HorizontalDivider()

                // #81 .synfix Backup Export / Import
                Text("Copia portátil comprimida (.synfix)", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { exportLauncher.launch("synfix_backup.synfix") },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Exportar")
                    }
                    FilledTonalButton(
                        onClick = { importLauncher.launch(arrayOf("*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Importar")
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } }
    )
}
