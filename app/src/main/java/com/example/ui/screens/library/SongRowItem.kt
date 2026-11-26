package com.example.ui.screens.library

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.PlaylistPlay
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Song
import com.example.ui.components.ArtworkImage
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.SynFixMotion

@Composable
fun SongRowItem(
    song: Song,
    isActive: Boolean,
    isCompact: Boolean,
    artworkShape: Shape,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onEditTags: () -> Unit,
    onToggleHideSong: (() -> Unit)? = null,
    onHoldToPeekStart: ((Float) -> Unit)? = null,
    onHoldToPeekScrub: ((Float) -> Unit)? = null,
    onHoldToPeekEnd: (() -> Unit)? = null,
    onQuickAssignVibe: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var menuExpanded by remember { mutableStateOf(false) }
    var swipeOffset by remember { mutableFloatStateOf(0f) }
    var isPeeking by remember { mutableStateOf(false) }
    val swipeState = animateFloatAsState(
        targetValue = swipeOffset,
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "songSwipe"
    )
    val haptics = LocalHapticFeedback.current
    val latestAddToQueue by rememberUpdatedState(onAddToQueue)
    val latestPeekStart by rememberUpdatedState(onHoldToPeekStart)
    val latestPeekScrub by rememberUpdatedState(onHoldToPeekScrub)
    val latestPeekEnd by rememberUpdatedState(onHoldToPeekEnd)

    val vibeSubtitleSuffix = remember(song.vibeTags) {
        if (song.vibeTags.isBlank()) ""
        else " • #" + song.vibeTags.split(',').map { it.trim() }.filter { it.isNotBlank() }.take(2).joinToString(" #")
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = if (isCompact) 2.dp else 4.dp)
    ) {
        // Background revealed purely in draw phase when swiping right to queue
        Row(
            modifier = Modifier
                .matchParentSize()
                .graphicsLayer {
                    alpha = if (swipeState.value > 8f) 1f else 0f
                }
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = "Añadir a la cola",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }

        Surface(
            shape = RoundedCornerShape(18.dp),
            color = when {
                isPeeking -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
                isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                else -> MaterialTheme.colorScheme.surfaceContainerLow
            },
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    translationX = swipeState.value
                }
                .then(
                    if (onHoldToPeekStart != null) {
                        Modifier.pointerInput(song.id) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { offset ->
                                    isPeeking = true
                                    swipeOffset = 0f
                                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                    val frac = (offset.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    latestPeekStart?.invoke(frac)
                                },
                                onDragEnd = {
                                    isPeeking = false
                                    latestPeekEnd?.invoke()
                                },
                                onDragCancel = {
                                    isPeeking = false
                                    latestPeekEnd?.invoke()
                                },
                                onDrag = { change, _ ->
                                    change.consume()
                                    val frac = (change.position.x / size.width.toFloat().coerceAtLeast(1f)).coerceIn(0f, 1f)
                                    latestPeekScrub?.invoke(frac)
                                }
                            )
                        }
                    } else Modifier
                )
                .pointerInput(song.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (!isPeeking && swipeOffset > 120f) {
                                latestAddToQueue()
                            }
                            swipeOffset = 0f
                        },
                        onDragCancel = { swipeOffset = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            if (!isPeeking && swipeOffset + dragAmount >= 0f) {
                                change.consume()
                                swipeOffset = (swipeOffset + dragAmount).coerceIn(0f, 180f)
                            }
                        }
                    )
                }
                .clip(RoundedCornerShape(18.dp))
                .clickable(onClick = onClick)
                .testTag("song_item_${song.id}")
        ) {
            val artSize = if (isCompact) 44.dp else 54.dp
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = if (isCompact) 8.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArtworkImage(
                    artworkUri = song.artworkUri,
                    contentDescription = song.album,
                    targetSizeDp = artSize,
                    shape = artworkShape,
                    modifier = Modifier.size(artSize)
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = if (isCompact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "${song.artist} • ${song.album}$vibeSubtitleSuffix",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = formatDuration(song.durationMs),
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(
                        imageVector = if (song.isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                        contentDescription = "Favorita",
                        tint = if (song.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MoreVert,
                            contentDescription = "Opciones",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Reproducir a continuación") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistPlay, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onPlayNext()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Añadir a la cola") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToQueue()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Añadir a playlist") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onAddToPlaylist()
                            }
                        )
                        if (onQuickAssignVibe != null) {
                            DropdownMenuItem(
                                text = { Text("Etiquetar Vibe / Mood") },
                                leadingIcon = { Icon(Icons.Filled.Label, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onQuickAssignVibe()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Editar etiquetas y portada") },
                            leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                            onClick = {
                                menuExpanded = false
                                onEditTags()
                            }
                        )
                        if (onToggleHideSong != null) {
                            DropdownMenuItem(
                                text = { Text("Mover a Bóveda Oculta") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                onClick = {
                                    menuExpanded = false
                                    onToggleHideSong()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
