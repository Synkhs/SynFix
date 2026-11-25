package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SwipeRight
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.domain.model.Song
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.SynFixMotion
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.abs

/**
 * #4 Expressive Card Stack Queue:
 * Composes strictly the 4 visible upcoming cards in a stacked deck.
 * Swiping the top card right removes it from the queue; swiping left skips directly to it.
 */
@Composable
fun CardStackQueue(
    queue: ImmutableList<Song>,
    currentIndex: Int,
    onJumpToIndex: (Int) -> Unit,
    onRemoveIndex: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val startIdx = (currentIndex + 1).coerceAtLeast(0)
    val visibleUpcoming = remember(queue, startIdx) {
        if (startIdx >= queue.size) emptyList()
        else queue.subList(startIdx, minOf(startIdx + 4, queue.size))
            .mapIndexed { offset, song -> (startIdx + offset) to song }
    }

    if (visibleUpcoming.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .height(170.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "No hay más pistas próximas en la pila",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Desliza ← reproducir • → descartar",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Icon(
                imageVector = Icons.Filled.SwipeRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp),
            contentAlignment = Alignment.TopCenter
        ) {
            // Render back-to-front so index 0 sits on top
            visibleUpcoming.asReversed().forEach { (queueIndex, song) ->
                val depth = queueIndex - startIdx // 0..3
                key(queueIndex, song.id) {
                    var dragX by remember { mutableFloatStateOf(0f) }
                    val animatedDragX by animateFloatAsState(
                        targetValue = dragX,
                        animationSpec = SynFixMotion.fastSpatialSpring(),
                        label = "cardStackDrag"
                    )

                    val scale = 1f - (depth * 0.045f)
                    val offsetYDp = depth * 14f

                    ElevatedCard(
                        onClick = { if (depth == 0) onJumpToIndex(queueIndex) },
                        shape = RoundedCornerShape(26.dp),
                        colors = CardDefaults.elevatedCardColors(
                            containerColor = when (depth) {
                                0 -> MaterialTheme.colorScheme.surfaceContainerHighest
                                1 -> MaterialTheme.colorScheme.surfaceContainerHigh
                                else -> MaterialTheme.colorScheme.surfaceContainer
                            }
                        ),
                        elevation = CardDefaults.elevatedCardElevation(defaultElevation = (8 - depth * 2).dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(132.dp)
                            .zIndex((4 - depth).toFloat())
                            .graphicsLayer {
                                translationY = offsetYDp * density
                                translationX = if (depth == 0) animatedDragX else 0f
                                rotationZ = if (depth == 0) (animatedDragX / 40f).coerceIn(-8f, 8f) else 0f
                                scaleX = scale
                                scaleY = scale
                                alpha = (1f - depth * 0.14f).coerceIn(0.4f, 1f)
                            }
                            .then(
                                if (depth == 0) {
                                    Modifier.pointerInput(queueIndex, song.id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                when {
                                                    dragX < -120f -> onJumpToIndex(queueIndex)
                                                    dragX > 120f -> onRemoveIndex(queueIndex)
                                                }
                                                dragX = 0f
                                            },
                                            onDragCancel = { dragX = 0f },
                                            onHorizontalDrag = { change, amount ->
                                                change.consume()
                                                dragX = (dragX + amount).coerceIn(-260f, 260f)
                                            }
                                        )
                                    }
                                } else Modifier
                            )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            ArtworkImage(
                                artworkUri = song.artworkUri,
                                contentDescription = song.album,
                                targetSizeDp = 82.dp,
                                shape = RoundedCornerShape(18.dp),
                                proceduralSeedKey = "${song.album}|${song.artist}",
                                modifier = Modifier.size(82.dp)
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (depth == 0) "SIGUIENTE" else "EN +${depth + 1}",
                                    style = ExpressiveTypography.MonoTimecode,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = song.title,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "${song.artist} • ${song.album}",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            if (depth == 0) {
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(onClick = { onJumpToIndex(queueIndex) }) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = "Reproducir ahora")
                                    }
                                    IconButton(onClick = { onRemoveIndex(queueIndex) }) {
                                        Icon(Icons.Filled.Close, contentDescription = "Quitar")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
