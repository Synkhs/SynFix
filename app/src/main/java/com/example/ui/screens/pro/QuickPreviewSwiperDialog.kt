package com.example.ui.screens.pro

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Song
import com.example.ui.components.ArtworkImage
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.SynFixMotion
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.abs

/**
 * #15 Quick-Preview Swiper:
 * Rapidly sorts tracks with swipe gestures while automatically jumping (`seekTo`) to 0:45 of each track.
 */
@Composable
fun QuickPreviewSwiperDialog(
    songs: ImmutableList<Song>,
    onPreviewAt45s: (Song, List<Song>) -> Unit,
    onFavoriteAndQueue: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    val shuffled = remember(songs) { songs.shuffled() }
    var index by remember { mutableIntStateOf(0) }
    val currentSong = shuffled.getOrNull(index)

    LaunchedEffect(currentSong?.id) {
        if (currentSong != null) {
            onPreviewAt45s(currentSong, shuffled)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.FastForward, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Quick-Preview 0:45")
            }
        },
        text = {
            if (currentSong == null) {
                Text(
                    text = "Has revisado todas las pistas disponibles en esta sesión.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                var dragX by remember(currentSong.id) { mutableFloatStateOf(0f) }
                val animatedDragX by animateFloatAsState(dragX, SynFixMotion.fastSpatialSpring(), label = "swiperDrag")

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Reproduciendo drop en 0:45 • ${index + 1}/${shuffled.size}",
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.primary
                    )

                    ElevatedCard(
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                        modifier = Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                translationX = animatedDragX
                                rotationZ = (animatedDragX / 24f).coerceIn(-10f, 10f)
                            }
                            .pointerInput(currentSong.id) {
                                detectHorizontalDragGestures(
                                    onDragEnd = {
                                        when {
                                            dragX > 110f -> {
                                                onFavoriteAndQueue(currentSong)
                                                index++
                                            }
                                            dragX < -110f -> {
                                                index++
                                            }
                                        }
                                        dragX = 0f
                                    },
                                    onDragCancel = { dragX = 0f },
                                    onHorizontalDrag = { change, amount ->
                                        change.consume()
                                        dragX = (dragX + amount).coerceIn(-240f, 240f)
                                    }
                                )
                            }
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            ArtworkImage(
                                artworkUri = currentSong.artworkUri,
                                contentDescription = currentSong.album,
                                targetSizeDp = 160.dp,
                                shape = RoundedCornerShape(20.dp),
                                proceduralSeedKey = "${currentSong.album}|${currentSong.artist}",
                                modifier = Modifier.size(160.dp)
                            )
                            Text(
                                text = currentSong.title,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                textAlign = TextAlign.Center,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${currentSong.artist} • ${currentSong.bpm} BPM",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        FilledTonalIconButton(
                            onClick = { index++ },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Saltar")
                        }
                        FilledIconButton(
                            onClick = {
                                onFavoriteAndQueue(currentSong)
                                index++
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.Filled.Favorite, contentDescription = "Guardar y encolar")
                        }
                        FilledTonalIconButton(
                            onClick = {
                                onFavoriteAndQueue(currentSong)
                                index++
                            },
                            modifier = Modifier.size(52.dp)
                        ) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Cola")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
