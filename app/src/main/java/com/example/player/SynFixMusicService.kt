package com.example.player

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import androidx.annotation.OptIn
import androidx.media3.common.*
import androidx.media3.common.TrackSelectionParameters.AudioOffloadPreferences
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.PlayerMessage
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.example.MainActivity
import com.example.SynFixApplication
import com.example.domain.model.AbLoopState
import com.example.domain.model.AudioPipelineTelemetry
import com.example.domain.model.DrClippingTelemetry
import com.example.domain.model.FocusFilterBand
import com.example.domain.model.SleepTimerState
import com.example.domain.model.VenueReverbPreset
import com.example.service.SynFixQuickSettingsTileService
import com.example.widget.SynFixGlanceWidget
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.pow

object PlaybackRuntimeCoordinator {
    private val _sleepTimerState = MutableStateFlow(SleepTimerState())
    val sleepTimerState: StateFlow<SleepTimerState> = _sleepTimerState.asStateFlow()

    // #27 A-B Beat Looper state
    private val _abLoopState = MutableStateFlow(AbLoopState())
    val abLoopState: StateFlow<AbLoopState> = _abLoopState.asStateFlow()

    // #42 Real-Time Audio Pipeline HUD state (passive AnalyticsListener)
    private val _pipelineTelemetry = MutableStateFlow(AudioPipelineTelemetry())
    val pipelineTelemetry: StateFlow<AudioPipelineTelemetry> = _pipelineTelemetry.asStateFlow()

    // #59 Live DR & Clipping Guard telemetry
    private val _drTelemetry = MutableStateFlow(DrClippingTelemetry())
    val drTelemetry: StateFlow<DrClippingTelemetry> = _drTelemetry.asStateFlow()

    @Volatile var isServicePlaying: Boolean = false
    @Volatile var currentTrackTitle: String = ""

    @Volatile var crossfadeSeconds: Int = 0
    @Volatile var beatMatchedCrossfade: Boolean = false
    @Volatile var gaplessEnabled: Boolean = true
    @Volatile var deadAirTrimmerEnabled: Boolean = false
    @Volatile var replayGainEnabled: Boolean = false
    @Volatile var slowedReverbEnabled: Boolean = false
    @Volatile var baseSpeed: Float = 1.0f
    @Volatile var basePitch: Float = 1.0f
    @Volatile var semitoneShift: Int = 0
    @Volatile var turntableBrakeEnabled: Boolean = false
    @Volatile var partySyncSpeedFactor: Float = 1.0f
        private set
    @Volatile var scratchSpeedOverride: Float? = null

    val songBpmMap = java.util.concurrent.ConcurrentHashMap<Long, Int>()
    val songReplayGainMap = java.util.concurrent.ConcurrentHashMap<Long, Float>()

    private var timerJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    var onRequestFadeVolume: ((Float) -> Unit)? = null
    var onRequestPauseForTimer: (() -> Unit)? = null
    var onArmAbLoop: ((AbLoopState) -> Unit)? = null
    var onApplyEffectivePlaybackParams: (() -> Unit)? = null
    var onSeekRelativeOrAbsolute: ((Long) -> Unit)? = null
    var onRequestTurntableTogglePlayPause: (() -> Unit)? = null

    fun setPointA(positionMs: Long) {
        val current = _abLoopState.value
        val next = current.copy(
            pointAMs = positionMs.coerceAtLeast(0L),
            pointBMs = current.pointBMs?.takeIf { it > positionMs + 350L },
            active = false
        )
        _abLoopState.value = next
        onArmAbLoop?.invoke(next)
    }

    fun setPointB(positionMs: Long) {
        val current = _abLoopState.value
        val pointA = current.pointAMs ?: (positionMs - 4000L).coerceAtLeast(0L)
        if (positionMs <= pointA + 350L) return
        val next = AbLoopState(active = true, pointAMs = pointA, pointBMs = positionMs)
        _abLoopState.value = next
        onArmAbLoop?.invoke(next)
    }

    fun clearAbLoop() {
        val cleared = AbLoopState()
        _abLoopState.value = cleared
        onArmAbLoop?.invoke(cleared)
    }

    fun setVinylScratchState(speedFactor: Float?, seekTargetMs: Long?) {
        scratchSpeedOverride = speedFactor
        onApplyEffectivePlaybackParams?.invoke()
        if (seekTargetMs != null) {
            onSeekRelativeOrAbsolute?.invoke(seekTargetMs)
        }
    }

    fun setPartySyncSpeedFactor(factor: Float) {
        partySyncSpeedFactor = factor.coerceIn(0.99f, 1.01f)
        onApplyEffectivePlaybackParams?.invoke()
    }

    fun updatePipelineTelemetry(transform: (AudioPipelineTelemetry) -> AudioPipelineTelemetry) {
        _pipelineTelemetry.value = transform(_pipelineTelemetry.value)
    }

    fun updateDrTelemetry(telemetry: DrClippingTelemetry) {
        _drTelemetry.value = telemetry
    }

    fun startSleepTimer(minutes: Int, stopAfterCurrentTrack: Boolean) {
        timerJob?.cancel()
        if (stopAfterCurrentTrack) {
            _sleepTimerState.value = SleepTimerState(
                isActive = true,
                stopAfterCurrentTrack = true,
                remainingTracks = 1
            )
            return
        }

        val totalMs = minutes.coerceAtLeast(1) * 60_000L
        _sleepTimerState.value = SleepTimerState(isActive = true, remainingMs = totalMs)

        timerJob = scope.launch {
            var remaining = totalMs
            while (remaining > 0L) {
                delay(500L)
                remaining = (remaining - 500L).coerceAtLeast(0L)
                val fading = remaining <= 15_000L
                if (fading) {
                    val volumeFactor = (remaining / 15_000f).coerceIn(0.05f, 1f)
                    onRequestFadeVolume?.invoke(volumeFactor)
                }
                _sleepTimerState.value = SleepTimerState(
                    isActive = true,
                    remainingMs = remaining,
                    isFadingOut = fading
                )
            }
            onRequestPauseForTimer?.invoke()
            delay(150L)
            onRequestFadeVolume?.invoke(1f)
            _sleepTimerState.value = SleepTimerState()
        }
    }

    // #83 Track-Count Sleep Timer
    fun startTrackSleepTimer(trackCount: Int) {
        timerJob?.cancel()
        val count = trackCount.coerceIn(1, 30)
        _sleepTimerState.value = SleepTimerState(
            isActive = true,
            stopAfterCurrentTrack = count == 1,
            remainingTracks = count
        )
    }

    fun cancelSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        onRequestFadeVolume?.invoke(1f)
        _sleepTimerState.value = SleepTimerState()
    }

    fun handleMediaItemTransitionIfTimerPending(): Boolean {
        val current = _sleepTimerState.value
        if (!current.isActive) return false
        if (current.remainingTracks > 0) {
            val nextRemaining = current.remainingTracks - 1
            return if (nextRemaining <= 0) {
                onRequestPauseForTimer?.invoke()
                _sleepTimerState.value = SleepTimerState()
                true
            } else {
                _sleepTimerState.value = current.copy(
                    remainingTracks = nextRemaining,
                    stopAfterCurrentTrack = nextRemaining == 1
                )
                false
            }
        }
        if (current.stopAfterCurrentTrack) {
            onRequestPauseForTimer?.invoke()
            _sleepTimerState.value = SleepTimerState()
            return true
        }
        return false
    }
}

class SynFixMusicService : MediaSessionService() {
    companion object {
        const val ACTION_WIDGET_TOGGLE_PLAY = "com.example.synfix.ACTION_WIDGET_TOGGLE_PLAY"
        const val ACTION_WIDGET_NEXT = "com.example.synfix.ACTION_WIDGET_NEXT"
        const val ACTION_TILE_PLAY_MIX = "com.example.synfix.ACTION_TILE_PLAY_MIX"
        private const val LONG_TRACK_THRESHOLD_MS = 15 * 60_000L
    }

    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var secondaryPlayer: ExoPlayer? = null

    private val silenceProcessor = SilenceSkippingAudioProcessor().apply {
        setEnabled(false)
    }
    private val midSideProcessor = MidSideAudioProcessor()
    private val lofiProcessor = LoFiTapeAudioProcessor()
    private val audio8dProcessor = Audio8dProcessor()
    private val harmonicExciterProcessor = HarmonicBassExciterProcessor()
    private val drClippingProcessor = LiveDrClippingGuardProcessor()
    private val ambienceProcessor = AmbienceMixerAudioProcessor()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var crossfadeJob: Job? = null
    private var turntableJob: Job? = null
    private var telemetryJob: Job? = null
    private var sleepVolumeFactor: Float = 1f
    private var primaryCrossfadeFactor: Float = 1f
    private var replayGainLinearFactor: Float = 1f
    private var beatMatchActive: Boolean = false
    private var lastTelemetrySavedSec = 0L
    private var pendingResumeSongId: Long? = null

    @OptIn(UnstableApi::class)
    private var abLoopMessage: PlayerMessage? = null

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()
        val app = application as SynFixApplication

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.CONTENT_TYPE_MUSIC)
            .build()

        val renderersFactory = object : DefaultRenderersFactory(this) {
            override fun buildAudioSink(
                context: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink {
                return DefaultAudioSink.Builder(context)
                    .setAudioProcessors(
                        arrayOf(
                            silenceProcessor,
                            midSideProcessor,
                            harmonicExciterProcessor,
                            audio8dProcessor,
                            lofiProcessor,
                            ambienceProcessor,
                            drClippingProcessor
                        )
                    )
                    .setEnableFloatOutput(enableFloatOutput)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .build()
            }
        }

        player = ExoPlayer.Builder(this, renderersFactory)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                pauseAtEndOfMediaItems = false
                trackSelectionParameters = trackSelectionParameters
                    .buildUpon()
                    .setAudioOffloadPreferences(
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED)
                            .setIsGaplessSupportRequired(true)
                            .build()
                    )
                    .build()
            }

        // #42 Real-Time Audio Pipeline HUD via passive AnalyticsListener
        player.addAnalyticsListener(object : AnalyticsListener {
            override fun onAudioInputFormatChanged(
                eventTime: AnalyticsListener.EventTime,
                format: Format,
                decoderReuseEvaluation: androidx.media3.exoplayer.DecoderReuseEvaluation?
            ) {
                val codecLabel = when (val mime = format.sampleMimeType ?: "") {
                    "audio/mpeg" -> "MP3"
                    "audio/flac" -> "FLAC"
                    "audio/mp4a-latm" -> "AAC"
                    "audio/opus" -> "OPUS"
                    "audio/vorbis" -> "OGG"
                    "audio/raw" -> "PCM"
                    else -> mime.substringAfter('/').uppercase().ifBlank { "AUDIO" }
                }
                val bitDepth = when (format.pcmEncoding) {
                    C.ENCODING_PCM_24BIT -> "24-bit"
                    C.ENCODING_PCM_32BIT, C.ENCODING_PCM_FLOAT -> "32-bit"
                    else -> if (codecLabel == "FLAC") "16/24-bit" else "16-bit"
                }
                PlaybackRuntimeCoordinator.updatePipelineTelemetry { prev ->
                    prev.copy(
                        codec = codecLabel,
                        sampleRateHz = format.sampleRate.takeIf { it > 0 } ?: prev.sampleRateHz,
                        bitrateKbps = if (format.bitrate > 0) format.bitrate / 1000 else prev.bitrateKbps,
                        channelCount = format.channelCount.takeIf { it > 0 } ?: prev.channelCount,
                        bitDepthLabel = bitDepth
                    )
                }
            }

            override fun onAudioTrackInitialized(
                eventTime: AnalyticsListener.EventTime,
                audioTrackConfig: AudioSink.AudioTrackConfig
            ) {
                val isOffloaded = audioTrackConfig.offload
                PlaybackRuntimeCoordinator.updatePipelineTelemetry { prev ->
                    prev.copy(
                        sampleRateHz = audioTrackConfig.sampleRate.takeIf { it > 0 } ?: prev.sampleRateHz,
                        isOffloadActive = isOffloaded
                    )
                }
            }
        })

        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                app.audioEffectsEngine.attachToSession(audioSessionId)
                app.visualizerController.attachToSession(audioSessionId)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                saveLongTrackPositionIfNeeded(player.currentMediaItem?.mediaId?.toLongOrNull(), player.currentPosition)
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    PlaybackRuntimeCoordinator.handleMediaItemTransitionIfTimerPending()
                }
                PlaybackRuntimeCoordinator.clearAbLoop()
                releaseSecondaryPlayer()
                beatMatchActive = false
                PlaybackRuntimeCoordinator.currentTrackTitle =
                    mediaItem?.mediaMetadata?.title?.toString() ?: ""

                applyReplayGainForItem(mediaItem)
                applyEffectivePlaybackParameters()
                restoreLongTrackPositionIfEligible(mediaItem)

                if (PlaybackRuntimeCoordinator.crossfadeSeconds > 0) {
                    fadeInPrimaryAfterTransition()
                } else {
                    primaryCrossfadeFactor = 1f
                    applyCombinedVolume()
                }
                pushWidgetAndTileUpdate()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                PlaybackRuntimeCoordinator.isServicePlaying = isPlaying
                app.ambienceStandaloneEngine.update(ambienceProcessor.mixState, isPlaying)

                if (isPlaying) {
                    startCrossfadeMonitor()
                    startTelemetryLoop()
                } else {
                    crossfadeJob?.cancel()
                    telemetryJob?.cancel()
                    releaseSecondaryPlayer()
                    saveLongTrackPositionIfNeeded(
                        player.currentMediaItem?.mediaId?.toLongOrNull(),
                        player.currentPosition
                    )
                }
                pushWidgetAndTileUpdate()
            }
        })

        if (player.audioSessionId != C.AUDIO_SESSION_ID_UNSET) {
            app.audioEffectsEngine.attachToSession(player.audioSessionId)
            app.visualizerController.attachToSession(player.audioSessionId)
        }

        PlaybackRuntimeCoordinator.onRequestFadeVolume = { factor ->
            sleepVolumeFactor = factor
            applyCombinedVolume()
        }
        PlaybackRuntimeCoordinator.onRequestPauseForTimer = {
            saveLongTrackPositionIfNeeded(player.currentMediaItem?.mediaId?.toLongOrNull(), player.currentPosition)
            player.pause()
        }
        PlaybackRuntimeCoordinator.onArmAbLoop = { state ->
            armNativeAbLoopMessage(state)
        }
        PlaybackRuntimeCoordinator.onApplyEffectivePlaybackParams = {
            applyEffectivePlaybackParameters()
        }
        PlaybackRuntimeCoordinator.onSeekRelativeOrAbsolute = { targetMs ->
            val dur = player.duration.takeIf { it > 0 } ?: Long.MAX_VALUE
            player.seekTo(targetMs.coerceIn(0L, dur))
        }
        PlaybackRuntimeCoordinator.onRequestTurntableTogglePlayPause = {
            togglePlayPauseWithTurntable()
        }

        val launchIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityPendingIntent)
            .build()

        serviceScope.launch {
            kotlinx.coroutines.flow.combine(
                app.preferences.playbackSettings,
                app.preferences.ambienceMixState,
                app.preferences.eqState
            ) { settings, ambience, eq -> Triple(settings, ambience, eq) }.collect { (settings, ambience, eq) ->
                PlaybackRuntimeCoordinator.gaplessEnabled = settings.gaplessEnabled
                PlaybackRuntimeCoordinator.crossfadeSeconds = settings.crossfadeSeconds
                PlaybackRuntimeCoordinator.beatMatchedCrossfade = settings.beatMatchedCrossfade
                PlaybackRuntimeCoordinator.deadAirTrimmerEnabled = settings.deadAirTrimmerEnabled
                PlaybackRuntimeCoordinator.replayGainEnabled = settings.replayGainEnabled
                PlaybackRuntimeCoordinator.slowedReverbEnabled = settings.slowedReverbEnabled
                PlaybackRuntimeCoordinator.baseSpeed = settings.playbackSpeed
                PlaybackRuntimeCoordinator.basePitch = settings.playbackPitch
                PlaybackRuntimeCoordinator.semitoneShift = settings.semitoneShift
                PlaybackRuntimeCoordinator.turntableBrakeEnabled = settings.turntableBrakeEnabled

                player.pauseAtEndOfMediaItems = !settings.gaplessEnabled
                silenceProcessor.setEnabled(settings.deadAirTrimmerEnabled)
                midSideProcessor.stemBalance = settings.midSideStemBalance
                lofiProcessor.tapeEnabled = settings.lofiTapeEnabled
                audio8dProcessor.enabled = settings.audio8dEnabled
                audio8dProcessor.orbitSpeed = settings.audio8dSpeed
                harmonicExciterProcessor.level = settings.harmonicExciterLevel
                drClippingProcessor.guardEnabled = settings.drClippingGuardEnabled
                ambienceProcessor.mixState = ambience
                app.ambienceStandaloneEngine.update(ambience, player.isPlaying)

                val ambienceActive = ambience.enabled &&
                    (ambience.rainLevel + ambience.fireLevel + ambience.pinkNoiseLevel +
                        ambience.binaural40HzLevel + ambience.binaural4HzLevel) > 0.02f

                // Preserve hardware Audio Offload whenever EQ & software PCM processors are off
                val needsPcmProcessing = settings.deadAirTrimmerEnabled ||
                    abs(settings.midSideStemBalance) > 0.03f ||
                    settings.lofiTapeEnabled ||
                    settings.audio8dEnabled ||
                    settings.harmonicExciterLevel > 0.02f ||
                    settings.drClippingGuardEnabled ||
                    ambienceActive

                val hasHardwareDspEffects = eq.enabled ||
                    settings.nightModeDrcEnabled ||
                    settings.venueReverbPreset != VenueReverbPreset.OFF ||
                    settings.focusFilterBand != FocusFilterBand.NONE ||
                    settings.slowedReverbEnabled ||
                    settings.adaptiveAmbientEnabled ||
                    abs(settings.channelBalance) > 0.01f

                val disableOffload = needsPcmProcessing || hasHardwareDspEffects

                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setAudioOffloadPreferences(
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(
                                if (disableOffload) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                                else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                            )
                            .setIsGaplessSupportRequired(settings.gaplessEnabled)
                            .build()
                    )
                    .build()

                if (disableOffload) {
                    PlaybackRuntimeCoordinator.updatePipelineTelemetry { it.copy(isOffloadActive = false) }
                }

                if (!beatMatchActive) {
                    applyEffectivePlaybackParameters()
                }
                applyReplayGainForItem(player.currentMediaItem)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_WIDGET_TOGGLE_PLAY -> {
                if (player.mediaItemCount == 0) {
                    startAutoMixFallback()
                } else {
                    togglePlayPauseWithTurntable()
                }
            }
            ACTION_WIDGET_NEXT -> {
                if (player.hasNextMediaItem()) {
                    player.seekToNextMediaItem()
                    player.play()
                }
            }
            ACTION_TILE_PLAY_MIX -> {
                if (player.mediaItemCount == 0) {
                    startAutoMixFallback()
                } else {
                    togglePlayPauseWithTurntable()
                }
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun startAutoMixFallback() {
        val app = application as SynFixApplication
        serviceScope.launch {
            val songs = withContext(Dispatchers.IO) {
                app.database.musicDao().getAllSongsOnce().filter { !it.isHidden }.map { it.toDomain() }
            }
            if (songs.isNotEmpty()) {
                val picked = songs.shuffled().take(30)
                val items = picked.map { s ->
                    MediaItem.Builder()
                        .setUri(s.contentUri)
                        .setMediaId(s.id.toString())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(s.title)
                                .setArtist(s.artist)
                                .setAlbumTitle(s.album)
                                .setArtworkUri(s.artworkUri)
                                .build()
                        )
                        .build()
                }
                player.setMediaItems(items)
                player.prepare()
                player.play()
            }
        }
    }

    /**
     * #60 Turntable Brake Stop / Start:
     * Smoothly ramps speed over ~220ms instead of abrupt pause/resume when enabled.
     */
    private fun togglePlayPauseWithTurntable() {
        turntableJob?.cancel()
        if (!PlaybackRuntimeCoordinator.turntableBrakeEnabled) {
            if (player.isPlaying) player.pause() else player.play()
            return
        }
        if (player.isPlaying) {
            turntableJob = serviceScope.launch {
                val base = PlaybackRuntimeCoordinator.baseSpeed
                val steps = 7
                for (i in steps downTo 1) {
                    val factor = (i.toFloat() / steps.toFloat()).coerceIn(0.18f, 1f)
                    player.playbackParameters = PlaybackParameters((base * factor).coerceIn(0.18f, 2.5f), effectivePitch())
                    delay(30L)
                }
                player.pause()
                applyEffectivePlaybackParameters()
            }
        } else {
            turntableJob = serviceScope.launch {
                val base = PlaybackRuntimeCoordinator.baseSpeed
                player.playbackParameters = PlaybackParameters((base * 0.22f).coerceIn(0.18f, 2.5f), effectivePitch())
                player.play()
                val steps = 7
                for (i in 1..steps) {
                    val factor = (i.toFloat() / steps.toFloat()).coerceIn(0.22f, 1f)
                    player.playbackParameters = PlaybackParameters((base * factor).coerceIn(0.18f, 2.5f), effectivePitch())
                    delay(30L)
                }
                applyEffectivePlaybackParameters()
            }
        }
    }

    // #74 Smart Long-Track Resume (>= 15 min)
    private fun restoreLongTrackPositionIfEligible(item: MediaItem?) {
        val songId = item?.mediaId?.toLongOrNull() ?: return
        if (pendingResumeSongId == songId) return
        pendingResumeSongId = songId
        val app = application as SynFixApplication
        serviceScope.launch {
            val entity = withContext(Dispatchers.IO) { app.database.musicDao().getSongById(songId) } ?: return@launch
            if (entity.duration >= LONG_TRACK_THRESHOLD_MS &&
                entity.lastPositionMs in 10_000L until (entity.duration - 15_000L)
            ) {
                if (player.currentMediaItem?.mediaId?.toLongOrNull() == songId && player.currentPosition < 4_000L) {
                    player.seekTo(entity.lastPositionMs)
                }
            }
        }
    }

    private fun saveLongTrackPositionIfNeeded(songId: Long?, positionMs: Long) {
        if (songId == null || positionMs < 0L) return
        val app = application as SynFixApplication
        serviceScope.launch(Dispatchers.IO) {
            val entity = app.database.musicDao().getSongById(songId) ?: return@launch
            if (entity.duration >= LONG_TRACK_THRESHOLD_MS) {
                val toSave = if (positionMs >= entity.duration - 15_000L) 0L else positionMs
                app.repository.updateSongResumePosition(songId, toSave)
            }
        }
    }

    private fun startTelemetryLoop() {
        telemetryJob?.cancel()
        val app = application as SynFixApplication
        telemetryJob = serviceScope.launch {
            var tickCount = 0
            while (isActive && player.isPlaying) {
                delay(500L)
                tickCount++
                if (drClippingProcessor.guardEnabled) {
                    PlaybackRuntimeCoordinator.updateDrTelemetry(drClippingProcessor.snapshotTelemetry())
                }
                // Every 5 seconds persist Offload vs DSP time (#85) and save long-track position (#74)
                if (tickCount % 10 == 0) {
                    val isOffload = PlaybackRuntimeCoordinator.pipelineTelemetry.value.isOffloadActive
                    app.preferences.addOffloadOrDspSeconds(isOffload, 5L)
                    saveLongTrackPositionIfNeeded(
                        player.currentMediaItem?.mediaId?.toLongOrNull(),
                        player.currentPosition
                    )
                    pushWidgetAndTileUpdate()
                }
            }
        }
    }

    private fun pushWidgetAndTileUpdate() {
        val item = player.currentMediaItem
        val title = item?.mediaMetadata?.title?.toString()?.ifBlank { "SynFix" } ?: "SynFix"
        val artist = item?.mediaMetadata?.artist?.toString()?.ifBlank { "Offline Hi-Fi" } ?: "Toca para reproducir"
        val dur = player.duration.coerceAtLeast(1L)
        val pos = player.currentPosition.coerceAtLeast(0L)
        val progress = (pos.toFloat() / dur.toFloat()).coerceIn(0f, 1f)
        val playing = player.isPlaying

        PlaybackRuntimeCoordinator.currentTrackTitle = title
        PlaybackRuntimeCoordinator.isServicePlaying = playing

        serviceScope.launch {
            SynFixGlanceWidget.pushUpdate(this@SynFixMusicService, title, artist, playing, progress)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                runCatching {
                    TileService.requestListeningState(
                        this@SynFixMusicService,
                        ComponentName(this@SynFixMusicService, SynFixQuickSettingsTileService::class.java)
                    )
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun armNativeAbLoopMessage(state: AbLoopState) {
        abLoopMessage?.cancel()
        abLoopMessage = null
        val pointA = state.pointAMs ?: return
        val pointB = state.pointBMs ?: return
        if (!state.active || pointB <= pointA + 350L) return

        val currentItemIndex = player.currentMediaItemIndex
        val msg = player.createMessage { _, _ ->
            player.seekTo(pointA)
            armNativeAbLoopMessage(PlaybackRuntimeCoordinator.abLoopState.value)
        }
        msg.setLooper(player.applicationLooper)
        msg.setPosition(currentItemIndex, pointB)
        msg.setDeleteAfterDelivery(true)
        msg.send()
        abLoopMessage = msg
    }

    private fun applyReplayGainForItem(item: MediaItem?) {
        val songId = item?.mediaId?.toLongOrNull()
        replayGainLinearFactor = if (PlaybackRuntimeCoordinator.replayGainEnabled && songId != null) {
            val gainDb = PlaybackRuntimeCoordinator.songReplayGainMap[songId] ?: 0f
            10f.pow(gainDb.coerceIn(-12f, 6f) / 20f)
        } else {
            1f
        }
        applyCombinedVolume()
    }

    private fun effectivePitch(): Float {
        if (PlaybackRuntimeCoordinator.slowedReverbEnabled) return 0.85f
        val semitoneFactor = 2.0.pow(PlaybackRuntimeCoordinator.semitoneShift / 12.0).toFloat()
        return (PlaybackRuntimeCoordinator.basePitch * semitoneFactor).coerceIn(0.5f, 2.0f)
    }

    private fun applyEffectivePlaybackParameters() {
        val pitch = effectivePitch()
        val scratchOverride = PlaybackRuntimeCoordinator.scratchSpeedOverride
        if (scratchOverride != null) {
            player.playbackParameters = PlaybackParameters(
                scratchOverride.coerceIn(0.25f, 2.5f),
                pitch
            )
            return
        }
        val speed = if (PlaybackRuntimeCoordinator.slowedReverbEnabled) {
            0.85f
        } else {
            PlaybackRuntimeCoordinator.baseSpeed
        }
        val partyAdjustedSpeed = (speed * PlaybackRuntimeCoordinator.partySyncSpeedFactor).coerceIn(0.25f, 2.5f)
        player.playbackParameters = PlaybackParameters(partyAdjustedSpeed, pitch)
    }

    private fun startCrossfadeMonitor() {
        crossfadeJob?.cancel()
        crossfadeJob = serviceScope.launch {
            while (isActive) {
                delay(120L)
                val cfSeconds = PlaybackRuntimeCoordinator.crossfadeSeconds
                if (cfSeconds > 0 && player.isPlaying && player.hasNextMediaItem()) {
                    val duration = player.duration
                    val position = player.currentPosition
                    val effectiveCfSec = if (PlaybackRuntimeCoordinator.beatMatchedCrossfade) cfSeconds.coerceAtMost(8) else cfSeconds
                    val cfMs = effectiveCfSec * 1000L

                    if (duration > cfMs && position > 0L) {
                        val remainingMs = duration - position
                        if (remainingMs in 1..cfMs) {
                            val progress = 1f - (remainingMs.toFloat() / cfMs.toFloat())
                            primaryCrossfadeFactor = (1f - progress).coerceIn(0.05f, 1f)
                            applyCombinedVolume()

                            if (PlaybackRuntimeCoordinator.beatMatchedCrossfade && !beatMatchActive) {
                                beatMatchActive = true
                                applyBeatMatchTempoAdjustment()
                            }
                        } else if (primaryCrossfadeFactor < 1f) {
                            primaryCrossfadeFactor = 1f
                            applyCombinedVolume()
                        }
                    }
                } else if (primaryCrossfadeFactor < 1f) {
                    primaryCrossfadeFactor = 1f
                    applyCombinedVolume()
                }
            }
        }
    }

    private fun applyBeatMatchTempoAdjustment() {
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull() ?: return
        val nextIndex = player.nextMediaItemIndex
        if (nextIndex == C.INDEX_UNSET) return
        val nextId = player.getMediaItemAt(nextIndex).mediaId.toLongOrNull() ?: return

        val curBpm = PlaybackRuntimeCoordinator.songBpmMap[currentId]?.toFloat() ?: return
        val nextBpm = PlaybackRuntimeCoordinator.songBpmMap[nextId]?.toFloat() ?: return
        if (curBpm <= 0f || nextBpm <= 0f) return

        val ratio = (nextBpm / curBpm).coerceIn(0.96f, 1.04f)
        player.playbackParameters = PlaybackParameters(
            PlaybackRuntimeCoordinator.baseSpeed * ratio,
            effectivePitch()
        )
    }

    private fun fadeInPrimaryAfterTransition() {
        serviceScope.launch {
            val steps = 8
            for (i in 1..steps) {
                primaryCrossfadeFactor = i / steps.toFloat()
                applyCombinedVolume()
                delay(55L)
            }
            primaryCrossfadeFactor = 1f
            applyCombinedVolume()
        }
    }

    private fun releaseSecondaryPlayer() {
        secondaryPlayer?.run {
            stop()
            release()
        }
        secondaryPlayer = null
    }

    private fun applyCombinedVolume() {
        player.volume = (sleepVolumeFactor * primaryCrossfadeFactor * replayGainLinearFactor).coerceIn(0f, 1f)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val currentPlayer = mediaSession?.player
        if (currentPlayer == null || !currentPlayer.playWhenReady || currentPlayer.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveLongTrackPositionIfNeeded(player.currentMediaItem?.mediaId?.toLongOrNull(), player.currentPosition)
        abLoopMessage?.cancel()
        crossfadeJob?.cancel()
        turntableJob?.cancel()
        telemetryJob?.cancel()
        serviceScope.cancel()
        releaseSecondaryPlayer()
        PlaybackRuntimeCoordinator.onRequestFadeVolume = null
        PlaybackRuntimeCoordinator.onRequestPauseForTimer = null
        PlaybackRuntimeCoordinator.onArmAbLoop = null
        PlaybackRuntimeCoordinator.onApplyEffectivePlaybackParams = null
        PlaybackRuntimeCoordinator.onSeekRelativeOrAbsolute = null
        PlaybackRuntimeCoordinator.onRequestTurntableTogglePlayPause = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
