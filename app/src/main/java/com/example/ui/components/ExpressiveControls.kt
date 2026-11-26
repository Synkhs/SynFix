package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import com.example.domain.model.ArtworkShapeStyle
import com.example.domain.model.LibraryTab
import com.example.domain.model.Song
import com.example.ui.theme.StateMorphPolygonShape
import com.example.ui.theme.SynFixMotion
import com.example.ui.theme.SynFixPolygons
import kotlin.math.abs
import kotlin.math.hypot

/**
 * #2 Magnetic Shape-Shifting Play/Pause Button:
 * - Morphs smoothly between Squircle and Clover using M3 Expressive spatial springs.
 * - Tracks finger position inside pointerInput and applies magnetic attraction offset
 *   purely in the draw phase (graphicsLayer) with synchronized micro-haptics.
 */
@Composable
fun ShapeShiftingPlayButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    iconSize: Dp = 34.dp
) {
    val morph = remember {
        Morph(SynFixPolygons.squircle(), SynFixPolygons.clover())
    }
    val progressState = animateFloatAsState(
        targetValue = if (isPlaying) 1f else 0f,
        animationSpec = SynFixMotion.spatialSpring(),
        label = "playShapeMorph"
    )
    var isPressed by remember { mutableStateOf(false) }
    var targetMagnetX by remember { mutableFloatStateOf(0f) }
    var targetMagnetY by remember { mutableFloatStateOf(0f) }

    val magnetXState = animateFloatAsState(
        targetValue = targetMagnetX,
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "magnetX"
    )
    val magnetYState = animateFloatAsState(
        targetValue = targetMagnetY,
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "magnetY"
    )
    val scaleState = animateFloatAsState(
        targetValue = when {
            isPressed -> 0.94f
            isPlaying -> 1.05f
            else -> 1.0f
        },
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "playScale"
    )

    val dynamicShape = remember {
        StateMorphPolygonShape(morph) { progressState.value }
    }
    val haptics = LocalHapticFeedback.current
    val maxPullPx = with(LocalDensity.current) { 14.dp.toPx() }

    Surface(
        onClick = {
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            onClick()
        },
        shape = dynamicShape,
        color = MaterialTheme.colorScheme.primary,
        contentColor = MaterialTheme.colorScheme.onPrimary,
        shadowElevation = 6.dp,
        modifier = modifier
            .size(size)
            .pointerInput(maxPullPx) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    isPressed = true
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    val centerX = this.size.width / 2f
                    val centerY = this.size.height / 2f

                    fun updateMagnet(posX: Float, posY: Float) {
                        val dx = posX - centerX
                        val dy = posY - centerY
                        val dist = hypot(dx, dy).coerceAtLeast(1f)
                        val clamped = (dist * 0.42f).coerceAtMost(maxPullPx)
                        targetMagnetX = (dx / dist) * clamped
                        targetMagnetY = (dy / dist) * clamped
                    }

                    updateMagnet(down.position.x, down.position.y)

                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        updateMagnet(change.position.x, change.position.y)
                    }

                    isPressed = false
                    targetMagnetX = 0f
                    targetMagnetY = 0f
                }
            }
            .graphicsLayer {
                translationX = magnetXState.value
                translationY = magnetYState.value
                scaleX = scaleState.value
                scaleY = scaleState.value
            }
            .testTag("play_pause_button")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

/**
 * M3 Expressive HorizontalFloatingToolbar for primary library navigation (always visible & clickable).
 */
@Composable
fun HorizontalFloatingToolbar(
    selectedTab: LibraryTab,
    onSelectTab: (LibraryTab) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 8.dp,
        shadowElevation = 10.dp,
        modifier = modifier
            .padding(horizontal = 12.dp)
            .testTag("horizontal_floating_toolbar")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            LibraryTab.entries.forEach { tab ->
                val selected = tab == selectedTab
                val cornerRadius by animateDpAsState(
                    targetValue = if (selected) 24.dp else 14.dp,
                    animationSpec = SynFixMotion.fastSpatialSpring(),
                    label = "tabRadius"
                )
                val icon = when (tab) {
                    LibraryTab.SONGS -> if (selected) Icons.Filled.MusicNote else Icons.Outlined.MusicNote
                    LibraryTab.ALBUMS -> if (selected) Icons.Filled.Album else Icons.Outlined.Album
                    LibraryTab.ARTISTS -> if (selected) Icons.Filled.Person else Icons.Outlined.Person
                    LibraryTab.PLAYLISTS -> Icons.AutoMirrored.Filled.QueueMusic
                }

                Surface(
                    onClick = { onSelectTab(tab) },
                    shape = RoundedCornerShape(cornerRadius),
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                    contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("tab_${tab.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = if (selected) 14.dp else 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(icon, contentDescription = tab.label, modifier = Modifier.size(20.dp))
                        AnimatedVisibility(visible = selected) {
                            Text(
                                text = tab.label,
                                style = MaterialTheme.typography.labelLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Expandable Mini-Player with:
 * - Continuous vertical drag-up gesture to open Now Playing
 * - Horizontal swipe left/right to skip Next / Previous
 * - Real-time progress bar (isolated state read)
 */
@Composable
fun ExpandableMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    artworkShapeStyle: ArtworkShapeStyle,
    useProceduralFallback: Boolean,
    positionProvider: () -> Long,
    durationMs: Long,
    onExpand: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    modifier: Modifier = Modifier
) {
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    var dragOffsetX by remember { mutableFloatStateOf(0f) }
    val animatedOffsetYState = animateFloatAsState(
        targetValue = dragOffsetY,
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "miniDragY"
    )
    val animatedOffsetXState = animateFloatAsState(
        targetValue = dragOffsetX,
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "miniDragX"
    )

    val artworkShape = rememberArtworkShape(artworkShapeStyle)

    Surface(
        onClick = onExpand,
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .graphicsLayer {
                translationY = animatedOffsetYState.value.coerceIn(-120f, 20f)
                translationX = animatedOffsetXState.value.coerceIn(-140f, 140f)
            }
            .pointerInput(song.id) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dragOffsetY < -48f) {
                            onExpand()
                        }
                        dragOffsetY = 0f
                    },
                    onDragCancel = { dragOffsetY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetY += dragAmount
                    }
                )
            }
            .pointerInput(song.id) {
                detectHorizontalDragGestures(
                    onDragEnd = {
                        if (abs(dragOffsetX) > 90f) {
                            if (dragOffsetX < 0f) onNext() else onPrevious()
                        }
                        dragOffsetX = 0f
                    },
                    onDragCancel = { dragOffsetX = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragOffsetX += dragAmount
                    }
                )
            }
            .testTag("mini_player")
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ArtworkImage(
                    artworkUri = song.artworkUri,
                    contentDescription = song.album,
                    targetSizeDp = 48.dp,
                    shape = artworkShape,
                    proceduralSeedKey = "${song.album}|${song.artist}",
                    useProceduralFallback = useProceduralFallback,
                    modifier = Modifier
                        .size(48.dp)
                        .synFixSharedElement("shared_now_playing_art")
                )

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = song.title,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.synFixSharedElement("shared_now_playing_title")
                    )
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.testTag("mini_play_pause")
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir"
                    )
                }

                IconButton(
                    onClick = onNext,
                    modifier = Modifier.testTag("mini_next")
                ) {
                    Icon(
                        imageVector = Icons.Filled.SkipNext,
                        contentDescription = "Siguiente"
                    )
                }
            }

            MiniProgressBar(
                progressProvider = {
                    val dur = durationMs.coerceAtLeast(1L)
                    (positionProvider().toFloat() / dur.toFloat()).coerceIn(0f, 1f)
                },
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .padding(bottom = 4.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
    }
}
