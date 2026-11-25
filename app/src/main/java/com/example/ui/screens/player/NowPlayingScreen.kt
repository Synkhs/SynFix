package com.example.ui.screens.player

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.domain.model.*
import com.example.player.PaletteExtractor
import com.example.ui.components.*
import com.example.ui.screens.lyrics.LyricsEditorDialog
import com.example.ui.screens.lyrics.SyncedLyricsView
import com.example.ui.screens.pro.SessionCardExportDialog
import com.example.ui.screens.pro.TrackNotesSheet
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.MorphPolygonShape
import com.example.ui.theme.SynFixMotion
import com.example.ui.theme.SynFixPolygons
import com.example.ui.viewmodel.MusicViewModel
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

@Composable
fun NowPlayingScreen(
    viewModel: MusicViewModel,
    onBack: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onEditTags: (Song) -> Unit
) {
    BackHandler(onBack = onBack)

    val playerState by viewModel.playerState.collectAsStateWithLifecycle()
    val positionMs by viewModel.playbackPositionMs.collectAsStateWithLifecycle()
    val appearance by viewModel.appearanceSettings.collectAsStateWithLifecycle()
    val sleepTimer by viewModel.sleepTimerState.collectAsStateWithLifecycle()
    val subBassEnergy by viewModel.subBassEnergy.collectAsStateWithLifecycle()
    val rmsAmplitude by viewModel.rmsAmplitude.collectAsStateWithLifecycle()
    val trackNotes by viewModel.currentSongNotes.collectAsStateWithLifecycle()
    val albumSeedColor by viewModel.albumSeedColor.collectAsStateWithLifecycle()
    val abLoopState by viewModel.abLoopState.collectAsStateWithLifecycle()
    val telemetry by viewModel.pipelineTelemetry.collectAsStateWithLifecycle()

    val song = playerState.currentSong
    val context = LocalContext.current

    // Pre-warm neighbor 64x64 px palettes for #5 Liquid Color Drag Transition
    val prevSong = remember(playerState.queue, playerState.currentIndex) {
        playerState.queue.getOrNull(playerState.currentIndex - 1)
    }
    val nextSong = remember(playerState.queue, playerState.currentIndex) {
        playerState.queue.getOrNull(playerState.currentIndex + 1)
    }
    LaunchedEffect(prevSong?.artworkUri, nextSong?.artworkUri) {
        prevSong?.artworkUri?.let { PaletteExtractor.extractSeedColor(context, it) }
        nextSong?.artworkUri?.let { PaletteExtractor.extractSeedColor(context, it) }
    }

    var showLyrics by remember { mutableStateOf(false) }
    var showSleepTimerSheet by remember { mutableStateOf(false) }
    var showQueueSheet by remember { mutableStateOf(false) }
    var showLyricsEditor by remember { mutableStateOf(false) }
    var showNotesSheet by remember { mutableStateOf(false) }
    var showZeroLookMode by remember { mutableStateOf(false) }
    var showSessionCardDialog by remember { mutableStateOf(false) }
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    var dismissDragY by remember { mutableFloatStateOf(0f) }
    val animatedDismissY by animateFloatAsState(
        targetValue = dismissDragY,
        animationSpec = SynFixMotion.fastSpatialSpring(),
        label = "nowPlayingDismissY"
    )

    val durationMs = playerState.durationMs.coerceAtLeast(1L)
    val effectivePosMs = if (isScrubbing) (scrubFraction * durationMs).toLong() else positionMs
    val noteFractions = remember(trackNotes, durationMs) {
        if (trackNotes.isEmpty()) persistentListOf()
        else trackNotes.map { (it.timestampMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) }.toImmutableList()
    }

    val currentAccent = albumSeedColor ?: MaterialTheme.colorScheme.primary
    val prevAccent = prevSong?.artworkUri?.let { PaletteExtractor.getCachedSeedColor(it) } ?: MaterialTheme.colorScheme.tertiary
    val nextAccent = nextSong?.artworkUri?.let { PaletteExtractor.getCachedSeedColor(it) } ?: MaterialTheme.colorScheme.secondary

    if (showZeroLookMode) {
        ZeroLookGestureScreen(
            song = song,
            isPlaying = playerState.isPlaying,
            onTogglePlayPause = viewModel::togglePlayPause,
            onNext = viewModel::skipNext,
            onPrevious = viewModel::skipPrevious,
            onBookmarkNote = { viewModel.addTrackNote(positionMs, "Marcador rápido") },
            onExit = { showZeroLookMode = false }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer { translationY = animatedDismissY.coerceAtLeast(0f) }
            .pointerInput(Unit) {
                detectVerticalDragGestures(
                    onDragEnd = {
                        if (dismissDragY > 180f) onBack()
                        dismissDragY = 0f
                    },
                    onDragCancel = { dismissDragY = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        if (dismissDragY + dragAmount >= 0f) {
                            change.consume()
                            dismissDragY += dragAmount
                        }
                    }
                )
            }
    ) {
        when (appearance.nowPlayingStyle) {
            NowPlayingStyle.EXPRESSIVE_CLASSIC -> {
                ExpressiveClassicNowPlaying(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    shuffleEnabled = playerState.shuffleEnabled,
                    repeatMode = playerState.repeatMode,
                    lyrics = playerState.lyrics,
                    showLyrics = showLyrics,
                    sleepTimerActive = sleepTimer.isActive,
                    notesCount = trackNotes.size,
                    noteFractions = noteFractions,
                    abLoopState = abLoopState,
                    telemetry = telemetry,
                    beatSyncedMorphEnabled = appearance.beatSyncedMorphEnabled,
                    useProceduralFallback = appearance.proceduralArtFallback,
                    currentAccent = currentAccent,
                    prevAccent = prevAccent,
                    nextAccent = nextAccent,
                    beatEnergyProvider = { subBassEnergy },
                    rmsProvider = { rmsAmplitude },
                    progressProvider = { (effectivePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                    positionMsProvider = { effectivePosMs },
                    durationMs = durationMs,
                    onBack = onBack,
                    onCycleStyle = {
                        val next = NowPlayingStyle.entries[(appearance.nowPlayingStyle.ordinal + 1) % NowPlayingStyle.entries.size]
                        viewModel.updateAppearance { it.copy(nowPlayingStyle = next) }
                    },
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onOpenLyricsEditor = { showLyricsEditor = true },
                    onToggleFavorite = { song?.let(viewModel::toggleFavorite) },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::skipNext,
                    onPrevious = viewModel::skipPrevious,
                    onSeekFraction = { frac -> viewModel.seekTo((frac * durationMs).toLong()) },
                    onSeekMs = viewModel::seekTo,
                    onScrubbingChanged = { scrubbing, frac ->
                        isScrubbing = scrubbing
                        scrubFraction = frac
                    },
                    onSetPointA = viewModel::setAbLoopPointA,
                    onSetPointB = viewModel::setAbLoopPointB,
                    onClearAbLoop = viewModel::clearAbLoop,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onOpenQueue = { showQueueSheet = true },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenNotes = { showNotesSheet = true },
                    onOpenZeroLook = { showZeroLookMode = true },
                    onShareCard = { showSessionCardDialog = true },
                    onEditTags = { song?.let(onEditTags) }
                )
            }

            NowPlayingStyle.IMMERSIVE_FULL_BLEED -> {
                ImmersiveFullBleedNowPlaying(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    shuffleEnabled = playerState.shuffleEnabled,
                    repeatMode = playerState.repeatMode,
                    lyrics = playerState.lyrics,
                    showLyrics = showLyrics,
                    sleepTimerActive = sleepTimer.isActive,
                    notesCount = trackNotes.size,
                    noteFractions = noteFractions,
                    abLoopState = abLoopState,
                    telemetry = telemetry,
                    useProceduralFallback = appearance.proceduralArtFallback,
                    currentAccent = currentAccent,
                    prevAccent = prevAccent,
                    nextAccent = nextAccent,
                    rmsProvider = { rmsAmplitude },
                    progressProvider = { (effectivePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                    positionMsProvider = { effectivePosMs },
                    durationMs = durationMs,
                    onBack = onBack,
                    onCycleStyle = {
                        val next = NowPlayingStyle.entries[(appearance.nowPlayingStyle.ordinal + 1) % NowPlayingStyle.entries.size]
                        viewModel.updateAppearance { it.copy(nowPlayingStyle = next) }
                    },
                    onToggleLyrics = { showLyrics = !showLyrics },
                    onOpenLyricsEditor = { showLyricsEditor = true },
                    onToggleFavorite = { song?.let(viewModel::toggleFavorite) },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::skipNext,
                    onPrevious = viewModel::skipPrevious,
                    onSeekFraction = { frac -> viewModel.seekTo((frac * durationMs).toLong()) },
                    onSeekMs = viewModel::seekTo,
                    onScrubbingChanged = { scrubbing, frac ->
                        isScrubbing = scrubbing
                        scrubFraction = frac
                    },
                    onSetPointA = viewModel::setAbLoopPointA,
                    onSetPointB = viewModel::setAbLoopPointB,
                    onClearAbLoop = viewModel::clearAbLoop,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onOpenQueue = { showQueueSheet = true },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenNotes = { showNotesSheet = true },
                    onOpenZeroLook = { showZeroLookMode = true },
                    onShareCard = { showSessionCardDialog = true },
                    onEditTags = { song?.let(onEditTags) }
                )
            }

            NowPlayingStyle.VINYL_MINIMAL -> {
                VinylMinimalNowPlaying(
                    song = song,
                    isPlaying = playerState.isPlaying,
                    shuffleEnabled = playerState.shuffleEnabled,
                    repeatMode = playerState.repeatMode,
                    lyrics = playerState.lyrics,
                    sleepTimerActive = sleepTimer.isActive,
                    notesCount = trackNotes.size,
                    noteFractions = noteFractions,
                    abLoopState = abLoopState,
                    telemetry = telemetry,
                    useProceduralFallback = appearance.proceduralArtFallback,
                    rmsProvider = { rmsAmplitude },
                    progressProvider = { (effectivePosMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f) },
                    positionMsProvider = { effectivePosMs },
                    durationMs = durationMs,
                    onBack = onBack,
                    onCycleStyle = {
                        val next = NowPlayingStyle.entries[(appearance.nowPlayingStyle.ordinal + 1) % NowPlayingStyle.entries.size]
                        viewModel.updateAppearance { it.copy(nowPlayingStyle = next) }
                    },
                    onOpenLyricsEditor = { showLyricsEditor = true },
                    onToggleFavorite = { song?.let(viewModel::toggleFavorite) },
                    onTogglePlayPause = viewModel::togglePlayPause,
                    onNext = viewModel::skipNext,
                    onPrevious = viewModel::skipPrevious,
                    onSeekFraction = { frac -> viewModel.seekTo((frac * durationMs).toLong()) },
                    onSeekMs = viewModel::seekTo,
                    onScrubbingChanged = { scrubbing, frac ->
                        isScrubbing = scrubbing
                        scrubFraction = frac
                    },
                    onSetPointA = viewModel::setAbLoopPointA,
                    onSetPointB = viewModel::setAbLoopPointB,
                    onClearAbLoop = viewModel::clearAbLoop,
                    onVinylScratchTick = viewModel::onVinylScratchTick,
                    onVinylScratchScrub = viewModel::onVinylScratchScrub,
                    onToggleShuffle = viewModel::toggleShuffle,
                    onCycleRepeat = viewModel::cycleRepeatMode,
                    onOpenQueue = { showQueueSheet = true },
                    onOpenSleepTimer = { showSleepTimerSheet = true },
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenNotes = { showNotesSheet = true },
                    onOpenZeroLook = { showZeroLookMode = true },
                    onShareCard = { showSessionCardDialog = true },
                    onEditTags = { song?.let(onEditTags) }
                )
            }
        }
    }

    if (showSleepTimerSheet) {
        SleepTimerSheet(
            state = sleepTimer,
            onStartTimer = viewModel::startSleepTimer,
            onCancelTimer = viewModel::cancelSleepTimer,
            onDismiss = { showSleepTimerSheet = false }
        )
    }

    if (showQueueSheet) {
        QueueBottomSheet(
            queue = playerState.queue,
            currentIndex = playerState.currentIndex,
            onJumpToIndex = viewModel::jumpToQueueIndex,
            onRemoveIndex = viewModel::removeFromQueue,
            onDismiss = { showQueueSheet = false }
        )
    }

    if (showLyricsEditor && song != null) {
        LyricsEditorDialog(
            initialRawLyrics = song.embeddedLyrics.orEmpty(),
            onDismiss = { showLyricsEditor = false },
            onSave = { raw ->
                viewModel.saveLyricsForSong(song.id, raw)
                showLyricsEditor = false
            }
        )
    }

    if (showNotesSheet && song != null) {
        TrackNotesSheet(
            songTitle = song.title,
            currentPositionMs = positionMs,
            notes = trackNotes,
            onAddNote = viewModel::addTrackNote,
            onSeekToNote = { ts ->
                viewModel.seekTo(ts)
                showNotesSheet = false
            },
            onDeleteNote = viewModel::deleteTrackNote,
            onDismiss = { showNotesSheet = false }
        )
    }

    if (showSessionCardDialog && song != null) {
        SessionCardExportDialog(
            song = song,
            positionMs = positionMs,
            onDismiss = { showSessionCardDialog = false },
            onExportedMessage = viewModel::emitSnackbar
        )
    }
}

@Composable
private fun ExpressiveClassicNowPlaying(
    song: Song?,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatModeState,
    lyrics: ImmutableList<LyricLine>,
    showLyrics: Boolean,
    sleepTimerActive: Boolean,
    notesCount: Int,
    noteFractions: ImmutableList<Float>,
    abLoopState: AbLoopState,
    telemetry: AudioPipelineTelemetry,
    beatSyncedMorphEnabled: Boolean,
    useProceduralFallback: Boolean,
    currentAccent: Color,
    prevAccent: Color,
    nextAccent: Color,
    beatEnergyProvider: () -> Float,
    rmsProvider: () -> Float,
    progressProvider: () -> Float,
    positionMsProvider: () -> Long,
    durationMs: Long,
    onBack: () -> Unit,
    onCycleStyle: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenLyricsEditor: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekMs: (Long) -> Unit,
    onScrubbingChanged: (Boolean, Float) -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenZeroLook: () -> Unit,
    onShareCard: () -> Unit,
    onEditTags: () -> Unit
) {
    val morph = remember { Morph(SynFixPolygons.squircle(), SynFixPolygons.clover()) }
    val baseMorphProgress by animateFloatAsState(
        targetValue = if (isPlaying) 0.55f else 0f,
        animationSpec = SynFixMotion.slowSpatialSpring(),
        label = "artMorph"
    )
    val subBass = if (beatSyncedMorphEnabled && isPlaying) beatEnergyProvider() else 0f
    val dynamicMorphProgress = (baseMorphProgress + subBass * 0.45f).coerceIn(0f, 1f)
    val artShape = remember(dynamicMorphProgress) { MorphPolygonShape(morph, dynamicMorphProgress) }

    var swipeX by remember { mutableFloatStateOf(0f) }
    val animatedSwipeX by animateFloatAsState(swipeX, SynFixMotion.fastSpatialSpring(), label = "artSwipeX")

    // #5 Liquid Color Drag Transition via real-time lerp() across cached 64x64 px palettes
    val dragFraction = (abs(animatedSwipeX) / 200f).coerceIn(0f, 1f)
    val targetSwipeColor = if (animatedSwipeX < 0f) nextAccent else prevAccent
    val liquidColor = lerp(currentAccent, targetSwipeColor, dragFraction)

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to liquidColor.copy(alpha = 0.22f + dragFraction * 0.16f),
                        0.55f to MaterialTheme.colorScheme.background.copy(alpha = 0.92f),
                        1f to MaterialTheme.colorScheme.background
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                NowPlayingTopBar(
                    styleLabel = "Clásico",
                    onBack = onBack,
                    onCycleStyle = onCycleStyle,
                    onOpenZeroLook = onOpenZeroLook,
                    onShareCard = onShareCard,
                    onEditTags = onEditTags
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    AnimatedContent(targetState = showLyrics, label = "classicLyricsSwitch") { lyricsMode ->
                        if (lyricsMode) {
                            SyncedLyricsView(
                                lyrics = lyrics,
                                positionProvider = positionMsProvider,
                                onSeekToLine = onSeekMs,
                                onOpenLyricsEditor = onOpenLyricsEditor,
                                centeredText = true
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.84f)
                                    .aspectRatio(1f)
                                    .graphicsLayer {
                                        translationX = animatedSwipeX
                                        rotationZ = (animatedSwipeX / 28f).coerceIn(-7f, 7f)
                                    }
                                    .pointerInput(song?.id) {
                                        detectHorizontalDragGestures(
                                            onDragEnd = {
                                                if (abs(swipeX) > 100f) {
                                                    if (swipeX < 0f) onNext() else onPrevious()
                                                }
                                                swipeX = 0f
                                            },
                                            onDragCancel = { swipeX = 0f },
                                            onHorizontalDrag = { change, dragAmount ->
                                                change.consume()
                                                swipeX = (swipeX + dragAmount).coerceIn(-200f, 200f)
                                            }
                                        )
                                    }
                            ) {
                                ArtworkImage(
                                    artworkUri = song?.artworkUri,
                                    contentDescription = song?.album,
                                    targetSizeDp = 320.dp,
                                    shape = artShape,
                                    proceduralSeedKey = "${song?.album}|${song?.artist}",
                                    useProceduralFallback = useProceduralFallback,
                                    beatEnergyProvider = if (beatSyncedMorphEnabled) beatEnergyProvider else null,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .synFixSharedElement("shared_now_playing_art")
                                )
                            }
                        }
                    }
                }

                // Song Info + Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = song?.title ?: "Sin reproducción",
                            style = ExpressiveTypography.SongHeroTitle,
                            color = MaterialTheme.colorScheme.onBackground,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.synFixSharedElement("shared_now_playing_title")
                        )
                        Text(
                            text = "${song?.artist ?: "SynFix"} • ${song?.album ?: ""}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onToggleFavorite) {
                        Icon(
                            imageVector = if (song?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                            contentDescription = "Favorita",
                            tint = if (song?.isFavorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                PipelineAndAbLoopBar(
                    abLoopState = abLoopState,
                    telemetry = telemetry,
                    onSetPointA = onSetPointA,
                    onSetPointB = onSetPointB,
                    onClearAbLoop = onClearAbLoop
                )

                Spacer(modifier = Modifier.height(6.dp))

                LinearWavyProgressIndicator(
                    progressProvider = progressProvider,
                    isPlaying = isPlaying,
                    rmsProvider = rmsProvider,
                    noteFractions = noteFractions,
                    onSeekProgress = onSeekFraction,
                    onScrubbingChanged = onScrubbingChanged
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        formatDuration(positionMsProvider()),
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        formatDuration(durationMs),
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                TransportControlsRow(
                    isPlaying = isPlaying,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode,
                    onToggleShuffle = onToggleShuffle,
                    onPrevious = onPrevious,
                    onTogglePlayPause = onTogglePlayPause,
                    onNext = onNext,
                    onCycleRepeat = onCycleRepeat
                )

                Spacer(modifier = Modifier.height(10.dp))

                NowPlayingQuickActionsRow(
                    showLyrics = showLyrics,
                    sleepTimerActive = sleepTimerActive,
                    notesCount = notesCount,
                    onToggleLyrics = onToggleLyrics,
                    onOpenEqualizer = onOpenEqualizer,
                    onOpenNotes = onOpenNotes,
                    onOpenSleepTimer = onOpenSleepTimer,
                    onOpenQueue = onOpenQueue
                )
            }
        }
    }
}

@Composable
private fun ImmersiveFullBleedNowPlaying(
    song: Song?,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatModeState,
    lyrics: ImmutableList<LyricLine>,
    showLyrics: Boolean,
    sleepTimerActive: Boolean,
    notesCount: Int,
    noteFractions: ImmutableList<Float>,
    abLoopState: AbLoopState,
    telemetry: AudioPipelineTelemetry,
    useProceduralFallback: Boolean,
    currentAccent: Color,
    prevAccent: Color,
    nextAccent: Color,
    rmsProvider: () -> Float,
    progressProvider: () -> Float,
    positionMsProvider: () -> Long,
    durationMs: Long,
    onBack: () -> Unit,
    onCycleStyle: () -> Unit,
    onToggleLyrics: () -> Unit,
    onOpenLyricsEditor: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekMs: (Long) -> Unit,
    onScrubbingChanged: (Boolean, Float) -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenZeroLook: () -> Unit,
    onShareCard: () -> Unit,
    onEditTags: () -> Unit
) {
    val bgBase = MaterialTheme.colorScheme.surface
    var swipeX by remember { mutableFloatStateOf(0f) }
    val dragFrac = (abs(swipeX) / 200f).coerceIn(0f, 1f)
    val liquidAccent = lerp(currentAccent, if (swipeX < 0f) nextAccent else prevAccent, dragFrac)

    Surface(
        color = bgBase,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.fillMaxSize()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(song?.id) {
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (abs(swipeX) > 100f) {
                                if (swipeX < 0f) onNext() else onPrevious()
                            }
                            swipeX = 0f
                        },
                        onDragCancel = { swipeX = 0f },
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            swipeX = (swipeX + dragAmount).coerceIn(-200f, 200f)
                        }
                    )
                }
        ) {
            ArtworkImage(
                artworkUri = song?.artworkUri,
                contentDescription = song?.album,
                targetSizeDp = 500.dp,
                shape = RoundedCornerShape(0.dp),
                proceduralSeedKey = "${song?.album}|${song?.artist}",
                useProceduralFallback = useProceduralFallback,
                modifier = Modifier
                    .fillMaxSize()
                    .synFixSharedElement("shared_now_playing_art")
            )

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to bgBase.copy(alpha = 0.65f),
                            0.38f to liquidAccent.copy(alpha = 0.28f + dragFrac * 0.22f),
                            0.68f to bgBase.copy(alpha = 0.88f),
                            1f to bgBase.copy(alpha = 0.98f)
                        )
                    )
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                NowPlayingTopBar(
                    styleLabel = "Inmersivo",
                    onBack = onBack,
                    onCycleStyle = onCycleStyle,
                    onOpenZeroLook = onOpenZeroLook,
                    onShareCard = onShareCard,
                    onEditTags = onEditTags
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    if (showLyrics) {
                        SyncedLyricsView(
                            lyrics = lyrics,
                            positionProvider = positionMsProvider,
                            onSeekToLine = onSeekMs,
                            onOpenLyricsEditor = onOpenLyricsEditor,
                            centeredText = true
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(30.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    tonalElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = song?.title ?: "Sin reproducción",
                                    style = ExpressiveTypography.SongHeroTitle,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.synFixSharedElement("shared_now_playing_title")
                                )
                                Text(
                                    text = song?.artist ?: "SynFix",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1
                                )
                            }
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    imageVector = if (song?.isFavorite == true) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                                    contentDescription = "Favorita",
                                    tint = if (song?.isFavorite == true) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        PipelineAndAbLoopBar(
                            abLoopState = abLoopState,
                            telemetry = telemetry,
                            onSetPointA = onSetPointA,
                            onSetPointB = onSetPointB,
                            onClearAbLoop = onClearAbLoop
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        LinearWavyProgressIndicator(
                            progressProvider = progressProvider,
                            isPlaying = isPlaying,
                            rmsProvider = rmsProvider,
                            noteFractions = noteFractions,
                            onSeekProgress = onSeekFraction,
                            onScrubbingChanged = onScrubbingChanged
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                formatDuration(positionMsProvider()),
                                style = ExpressiveTypography.MonoTimecode,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                formatDuration(durationMs),
                                style = ExpressiveTypography.MonoTimecode,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        TransportControlsRow(
                            isPlaying = isPlaying,
                            shuffleEnabled = shuffleEnabled,
                            repeatMode = repeatMode,
                            onToggleShuffle = onToggleShuffle,
                            onPrevious = onPrevious,
                            onTogglePlayPause = onTogglePlayPause,
                            onNext = onNext,
                            onCycleRepeat = onCycleRepeat
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        NowPlayingQuickActionsRow(
                            showLyrics = showLyrics,
                            sleepTimerActive = sleepTimerActive,
                            notesCount = notesCount,
                            onToggleLyrics = onToggleLyrics,
                            onOpenEqualizer = onOpenEqualizer,
                            onOpenNotes = onOpenNotes,
                            onOpenSleepTimer = onOpenSleepTimer,
                            onOpenQueue = onOpenQueue
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun VinylMinimalNowPlaying(
    song: Song?,
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatModeState,
    lyrics: ImmutableList<LyricLine>,
    sleepTimerActive: Boolean,
    notesCount: Int,
    noteFractions: ImmutableList<Float>,
    abLoopState: AbLoopState,
    telemetry: AudioPipelineTelemetry,
    useProceduralFallback: Boolean,
    rmsProvider: () -> Float,
    progressProvider: () -> Float,
    positionMsProvider: () -> Long,
    durationMs: Long,
    onBack: () -> Unit,
    onCycleStyle: () -> Unit,
    onOpenLyricsEditor: () -> Unit,
    onToggleFavorite: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onPrevious: () -> Unit,
    onSeekFraction: (Float) -> Unit,
    onSeekMs: (Long) -> Unit,
    onScrubbingChanged: (Boolean, Float) -> Unit,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit,
    onVinylScratchTick: () -> Unit,
    onVinylScratchScrub: (Float?, Long?) -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenZeroLook: () -> Unit,
    onShareCard: () -> Unit,
    onEditTags: () -> Unit
) {
    var vinylAngle by remember { mutableFloatStateOf(0f) }
    var angularVelocity by remember { mutableFloatStateOf(0f) }
    var isScratching by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying, isScratching) {
        if (isScratching) return@LaunchedEffect
        var lastFrameNanos = withFrameNanos { it }
        val targetSpeed = if (isPlaying) 28f else 0f
        while (true) {
            val now = withFrameNanos { it }
            val dt = ((now - lastFrameNanos) / 1_000_000_000f).coerceIn(0f, 0.05f)
            lastFrameNanos = now

            val accel = if (isPlaying) 3.5f else 1.8f
            angularVelocity += (targetSpeed - angularVelocity) * (accel * dt).coerceAtMost(1f)
            if (abs(angularVelocity) > 0.05f) {
                vinylAngle = (vinylAngle + angularVelocity * dt) % 360f
            } else if (!isPlaying) {
                angularVelocity = 0f
                break
            }
        }
    }

    Surface(
        color = MaterialTheme.colorScheme.background,
        contentColor = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            NowPlayingTopBar(
                styleLabel = "Vinilo",
                onBack = onBack,
                onCycleStyle = onCycleStyle,
                onOpenZeroLook = onOpenZeroLook,
                onShareCard = onShareCard,
                onEditTags = onEditTags
            )

            // #22 Haptic Vinyl Scratch: circular touch scrubbing + 60Hz haptics + PlaybackParameters interpolation
            Box(
                modifier = Modifier
                    .size(210.dp)
                    .padding(vertical = 8.dp)
                    .pointerInput(song?.id) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            val cx = size.width / 2f
                            val cy = size.height / 2f
                            var prevAngle = (atan2(down.position.y - cy, down.position.x - cx) * (180.0 / PI)).toFloat()
                            var accumulatedHapticDeg = 0f
                            var lastHapticTimeMs = 0L
                            var scratchPosMs = positionMsProvider()
                            isScratching = true
                            onVinylScratchTick()

                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: break
                                if (!change.pressed) break
                                change.consume()

                                val newAngle = (atan2(change.position.y - cy, change.position.x - cx) * (180.0 / PI)).toFloat()
                                var delta = newAngle - prevAngle
                                if (delta > 180f) delta -= 360f
                                if (delta < -180f) delta += 360f
                                prevAngle = newAngle

                                if (abs(delta) > 0.25f) {
                                    vinylAngle = (vinylAngle + delta) % 360f
                                    angularVelocity = delta * 18f
                                    scratchPosMs = (scratchPosMs + (delta * 26f).toLong()).coerceIn(0L, durationMs)
                                    val speedInterp = (1.0f + delta * 0.045f).coerceIn(0.35f, 2.1f)
                                    onVinylScratchScrub(speedInterp, scratchPosMs)

                                    accumulatedHapticDeg += abs(delta)
                                    val nowMs = System.currentTimeMillis()
                                    if (accumulatedHapticDeg >= 16f && nowMs - lastHapticTimeMs >= 16L) { // ~60 Hz haptic cap
                                        accumulatedHapticDeg = 0f
                                        lastHapticTimeMs = nowMs
                                        onVinylScratchTick()
                                    }
                                }
                            }
                            isScratching = false
                            onVinylScratchScrub(null, null)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .synFixSharedElement("shared_now_playing_art")
                        .graphicsLayer { rotationZ = vinylAngle }
                        .clip(CircleShape)
                        .background(Color(0xFF14141A))
                        .border(4.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    ArtworkImage(
                        artworkUri = song?.artworkUri,
                        contentDescription = song?.album,
                        targetSizeDp = 160.dp,
                        circular = true,
                        proceduralSeedKey = "${song?.album}|${song?.artist}",
                        useProceduralFallback = useProceduralFallback,
                        modifier = Modifier.size(134.dp)
                    )
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.background)
                            .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    )
                }
            }

            Text(
                text = song?.title ?: "Sin reproducción",
                style = ExpressiveTypography.SongHeroTitle,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.synFixSharedElement("shared_now_playing_title")
            )
            Text(
                text = if (isScratching) "Scratch Háptico activo" else (song?.artist ?: "SynFix"),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isScratching) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )

            SyncedLyricsView(
                lyrics = lyrics,
                positionProvider = positionMsProvider,
                onSeekToLine = onSeekMs,
                onOpenLyricsEditor = onOpenLyricsEditor,
                centeredText = true,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            )

            PipelineAndAbLoopBar(
                abLoopState = abLoopState,
                telemetry = telemetry,
                onSetPointA = onSetPointA,
                onSetPointB = onSetPointB,
                onClearAbLoop = onClearAbLoop
            )

            Spacer(modifier = Modifier.height(4.dp))

            LinearWavyProgressIndicator(
                progressProvider = progressProvider,
                isPlaying = isPlaying,
                rmsProvider = rmsProvider,
                noteFractions = noteFractions,
                onSeekProgress = onSeekFraction,
                onScrubbingChanged = onScrubbingChanged
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatDuration(positionMsProvider()),
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatDuration(durationMs),
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            TransportControlsRow(
                isPlaying = isPlaying,
                shuffleEnabled = shuffleEnabled,
                repeatMode = repeatMode,
                onToggleShuffle = onToggleShuffle,
                onPrevious = onPrevious,
                onTogglePlayPause = onTogglePlayPause,
                onNext = onNext,
                onCycleRepeat = onCycleRepeat
            )

            Spacer(modifier = Modifier.height(6.dp))

            NowPlayingQuickActionsRow(
                showLyrics = true,
                sleepTimerActive = sleepTimerActive,
                notesCount = notesCount,
                onToggleLyrics = onToggleFavorite,
                onOpenEqualizer = onOpenEqualizer,
                onOpenNotes = onOpenNotes,
                onOpenSleepTimer = onOpenSleepTimer,
                onOpenQueue = onOpenQueue,
                thirdIconIsFavorite = song?.isFavorite == true
            )
        }
    }
}

@Composable
private fun PipelineAndAbLoopBar(
    abLoopState: AbLoopState,
    telemetry: AudioPipelineTelemetry,
    onSetPointA: () -> Unit,
    onSetPointB: () -> Unit,
    onClearAbLoop: () -> Unit
) {
    var showPipelineDetails by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // #42 Real-Time Audio Pipeline HUD pill
            Surface(
                onClick = { showPipelineDetails = !showPipelineDetails },
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Tune,
                        contentDescription = "Pipeline HUD",
                        tint = if (telemetry.isOffloadActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "${telemetry.codec} ${(telemetry.sampleRateHz / 1000f)}k • " +
                            if (telemetry.isOffloadActive) "Offload" else "DSP",
                        style = ExpressiveTypography.MonoTimecode,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // #27 A-B Beat Looper controls (ExoPlayer PlayerMessage)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilledTonalButton(
                    onClick = onSetPointA,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = abLoopState.pointAMs?.let { "A:${formatDuration(it)}" } ?: "A",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                FilledTonalButton(
                    onClick = onSetPointB,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text(
                        text = abLoopState.pointBMs?.let { "B:${formatDuration(it)}" } ?: "B",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                if (abLoopState.pointAMs != null || abLoopState.active) {
                    IconButton(
                        onClick = onClearAbLoop,
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Limpiar bucle A-B", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        if (showPipelineDetails) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Codec: ${telemetry.codec} | ${telemetry.sampleRateHz} Hz | ${telemetry.bitDepthLabel} | " +
                        "${if (telemetry.bitrateKbps > 0) "${telemetry.bitrateKbps} kbps" else "Lossless"} | " +
                        if (telemetry.isOffloadActive) "Hardware Audio Offload activo" else "Ruta PCM DSP activa",
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun NowPlayingTopBar(
    styleLabel: String,
    onBack: () -> Unit,
    onCycleStyle: () -> Unit,
    onOpenZeroLook: () -> Unit,
    onShareCard: () -> Unit,
    onEditTags: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack, modifier = Modifier.testTag("now_playing_back")) {
            Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Minimizar")
        }

        FilledTonalButton(
            onClick = onCycleStyle,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            modifier = Modifier.testTag("cycle_now_playing_style")
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(styleLabel, style = MaterialTheme.typography.labelLarge)
        }

        Row {
            IconButton(onClick = onOpenZeroLook, modifier = Modifier.testTag("zero_look_button")) {
                Icon(Icons.Filled.VisibilityOff, contentDescription = "Modo Ciego Zero-Look")
            }
            IconButton(onClick = onShareCard, modifier = Modifier.testTag("share_session_card_button")) {
                Icon(Icons.Filled.Share, contentDescription = "Compartir tarjeta de sesión")
            }
            IconButton(onClick = onEditTags, modifier = Modifier.testTag("now_playing_edit_tags")) {
                Icon(Icons.Filled.Edit, contentDescription = "Editar etiquetas")
            }
        }
    }
}

@Composable
private fun TransportControlsRow(
    isPlaying: Boolean,
    shuffleEnabled: Boolean,
    repeatMode: RepeatModeState,
    onToggleShuffle: () -> Unit,
    onPrevious: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleShuffle) {
            Icon(
                imageVector = Icons.Filled.Shuffle,
                contentDescription = "Aleatorio",
                tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        FilledTonalIconButton(
            onClick = onPrevious,
            modifier = Modifier.size(52.dp).testTag("previous_button")
        ) {
            Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior", modifier = Modifier.size(28.dp))
        }

        ShapeShiftingPlayButton(
            isPlaying = isPlaying,
            onClick = onTogglePlayPause,
            size = 74.dp,
            iconSize = 36.dp
        )

        FilledTonalIconButton(
            onClick = onNext,
            modifier = Modifier.size(52.dp).testTag("next_button")
        ) {
            Icon(Icons.Filled.SkipNext, contentDescription = "Siguiente", modifier = Modifier.size(28.dp))
        }

        IconButton(onClick = onCycleRepeat) {
            Icon(
                imageVector = when (repeatMode) {
                    RepeatModeState.ONE -> Icons.Filled.RepeatOne
                    else -> Icons.Filled.Repeat
                },
                contentDescription = "Repetir",
                tint = if (repeatMode != RepeatModeState.OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NowPlayingQuickActionsRow(
    showLyrics: Boolean,
    sleepTimerActive: Boolean,
    notesCount: Int,
    onToggleLyrics: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    thirdIconIsFavorite: Boolean? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onToggleLyrics) {
            Icon(
                imageVector = if (thirdIconIsFavorite != null) {
                    if (thirdIconIsFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder
                } else {
                    Icons.Filled.Lyrics
                },
                contentDescription = if (thirdIconIsFavorite != null) "Favorita" else "Letras",
                tint = if (thirdIconIsFavorite == true || (thirdIconIsFavorite == null && showLyrics)) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }

        IconButton(onClick = onOpenEqualizer) {
            Icon(Icons.Filled.GraphicEq, contentDescription = "Ecualizador")
        }

        BadgedBox(
            badge = {
                if (notesCount > 0) {
                    Badge { Text("$notesCount") }
                }
            }
        ) {
            IconButton(onClick = onOpenNotes, modifier = Modifier.testTag("track_notes_button")) {
                Icon(
                    imageVector = Icons.Filled.Bookmark,
                    contentDescription = "Notas por segundo",
                    tint = if (notesCount > 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onOpenSleepTimer) {
            Icon(
                imageVector = Icons.Filled.Bedtime,
                contentDescription = "Temporizador",
                tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        IconButton(onClick = onOpenQueue) {
            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = "Cola")
        }
    }
}
