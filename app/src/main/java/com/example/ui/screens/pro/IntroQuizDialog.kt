package com.example.ui.screens.pro

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import com.example.domain.model.Song
import com.example.ui.theme.ExpressiveTypography
import kotlinx.coroutines.delay

/**
 * #34 3-Second Intro Quiz:
 * Local mini-game using a lightweight secondary ExoPlayer that seeks to a random point,
 * plays a 3-second snippet, and presents 4 M3 Expressive answer options.
 */
@Composable
fun IntroQuizDialog(
    songs: List<Song>,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val quizPool = remember(songs) { songs.filter { it.durationMs > 8000L } }

    val secondaryPlayer = remember {
        ExoPlayer.Builder(context).build()
    }
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                secondaryPlayer.stop()
                secondaryPlayer.release()
            }
        }
    }

    var round by remember { mutableIntStateOf(1) }
    var score by remember { mutableIntStateOf(0) }
    var targetSong by remember { mutableStateOf<Song?>(null) }
    var options by remember { mutableStateOf<List<Song>>(emptyList()) }
    var selectedAnswerId by remember { mutableStateOf<Long?>(null) }
    var snippetPlaying by remember { mutableStateOf(false) }

    fun loadNewRound() {
        if (quizPool.size < 4) return
        val shuffled = quizPool.shuffled()
        val chosen = shuffled.first()
        val choices = shuffled.take(4).shuffled()
        targetSong = chosen
        options = choices
        selectedAnswerId = null

        val maxSeek = (chosen.durationMs - 5000L).coerceAtLeast(1000L)
        val randomPos = (1000L..maxSeek).random()
        runCatching {
            secondaryPlayer.setMediaItem(MediaItem.fromUri(chosen.contentUri))
            secondaryPlayer.prepare()
            secondaryPlayer.seekTo(randomPos)
            secondaryPlayer.play()
            snippetPlaying = true
        }
    }

    LaunchedEffect(round, quizPool.size) {
        if (quizPool.size >= 4) loadNewRound()
    }

    LaunchedEffect(snippetPlaying, round) {
        if (snippetPlaying) {
            delay(3000L)
            runCatching { secondaryPlayer.pause() }
            snippetPlaying = false
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
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Intro Quiz (3s)", style = MaterialTheme.typography.headlineSmall)
                    AssistChip(
                        onClick = {},
                        label = { Text("Puntos: $score", style = ExpressiveTypography.MonoTimecode) }
                    )
                }

                if (quizPool.size < 4) {
                    Text(
                        "Necesitas al menos 4 canciones en tu biblioteca para jugar al Intro Quiz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Filled.Headphones, contentDescription = null)
                                Text(
                                    text = if (snippetPlaying) "Escuchando fragmento 3s..." else "Ronda #$round — ¿Cuál suena?",
                                    style = MaterialTheme.typography.titleSmall
                                )
                            }
                            IconButton(onClick = {
                                val t = targetSong ?: return@IconButton
                                val pos = (1000L..(t.durationMs - 5000L).coerceAtLeast(1000L)).random()
                                runCatching {
                                    secondaryPlayer.seekTo(pos)
                                    secondaryPlayer.play()
                                    snippetPlaying = true
                                }
                            }) {
                                Icon(Icons.Filled.Replay, contentDescription = "Repetir 3s")
                            }
                        }
                    }

                    options.forEach { opt ->
                        val answered = selectedAnswerId != null
                        val isCorrect = opt.id == targetSong?.id
                        val isChosen = opt.id == selectedAnswerId
                        val containerColor = when {
                            !answered -> MaterialTheme.colorScheme.surfaceContainerLow
                            isCorrect -> MaterialTheme.colorScheme.primaryContainer
                            isChosen -> MaterialTheme.colorScheme.errorContainer
                            else -> MaterialTheme.colorScheme.surfaceContainerLow
                        }
                        Surface(
                            onClick = {
                                if (!answered) {
                                    selectedAnswerId = opt.id
                                    if (isCorrect) score++
                                    runCatching { secondaryPlayer.pause() }
                                    snippetPlaying = false
                                }
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = containerColor,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(opt.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(opt.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Salir") }
                    if (selectedAnswerId != null) {
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = { round++ }) { Text("Siguiente Ronda") }
                    }
                }
            }
        }
    }
}
