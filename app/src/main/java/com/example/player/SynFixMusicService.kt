package com.example.player

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
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
import com.example.domain.model.SleepTimerState
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

    @Volatile var crossfadeSeconds: Int = 0
    @Volatile var beatMatchedCrossfade: Boolean = false
    @Volatile var gaplessEnabled: Boolean = true
    @Volatile var deadAirTrimmerEnabled: Boolean = false
    @Volatile var replayGainEnabled: Boolean = false
    @Volatile var slowedReverbEnabled: Boolean = false
    @Volatile var baseSpeed: Float = 1.0f
    @Volatile var basePitch: Float = 1.0f
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

    fun startSleepTimer(minutes: Int, stopAfterCurrentTrack: Boolean) {
        timerJob?.cancel()
        if (stopAfterCurrentTrack) {
            _sleepTimerState.value = SleepTimerState(isActive = true, stopAfterCurrentTrack = true)
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

    fun cancelSleepTimer() {
        timerJob?.cancel()
        timerJob = null
        onRequestFadeVolume?.invoke(1f)
        _sleepTimerState.value = SleepTimerState()
    }

    fun handleMediaItemTransitionIfTimerPending(): Boolean {
        val current = _sleepTimerState.value
        if (current.isActive && current.stopAfterCurrentTrack) {
            onRequestPauseForTimer?.invoke()
            _sleepTimerState.value = SleepTimerState()
            return true
        }
        return false
    }
}

class SynFixMusicService : MediaSessionService() {
    private var mediaSession: MediaSession? = null
    private lateinit var player: ExoPlayer
    private var secondaryPlayer: ExoPlayer? = null

    private val silenceProcessor = SilenceSkippingAudioProcessor().apply {
        setEnabled(false)
    }
    private val midSideProcessor = MidSideAudioProcessor()
    private val lofiProcessor = LoFiTapeAudioProcessor()

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var crossfadeJob: Job? = null
    private var sleepVolumeFactor: Float = 1f
    private var primaryCrossfadeFactor: Float = 1f
    private var replayGainLinearFactor: Float = 1f
    private var beatMatchActive: Boolean = false
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
                    .setAudioProcessors(arrayOf(silenceProcessor, midSideProcessor, lofiProcessor))
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

        // #42 Real-Time Audio Pipeline HUD via passive AnalyticsListener (zero overhead)
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
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    PlaybackRuntimeCoordinator.handleMediaItemTransitionIfTimerPending()
                }
                PlaybackRuntimeCoordinator.clearAbLoop()
                releaseSecondaryPlayer()
                beatMatchActive = false
                applyReplayGainForItem(mediaItem)
                applyEffectivePlaybackParameters()

                if (PlaybackRuntimeCoordinator.crossfadeSeconds > 0) {
                    fadeInPrimaryAfterTransition()
                } else {
                    primaryCrossfadeFactor = 1f
                    applyCombinedVolume()
                }
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) {
                    startCrossfadeMonitor()
                } else {
                    crossfadeJob?.cancel()
                    releaseSecondaryPlayer()
                }
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
            app.preferences.playbackSettings.collect { settings ->
                PlaybackRuntimeCoordinator.gaplessEnabled = settings.gaplessEnabled
                PlaybackRuntimeCoordinator.crossfadeSeconds = settings.crossfadeSeconds
                PlaybackRuntimeCoordinator.beatMatchedCrossfade = settings.beatMatchedCrossfade
                PlaybackRuntimeCoordinator.deadAirTrimmerEnabled = settings.deadAirTrimmerEnabled
                PlaybackRuntimeCoordinator.replayGainEnabled = settings.replayGainEnabled
                PlaybackRuntimeCoordinator.slowedReverbEnabled = settings.slowedReverbEnabled
                PlaybackRuntimeCoordinator.baseSpeed = settings.playbackSpeed
                PlaybackRuntimeCoordinator.basePitch = settings.playbackPitch

                player.pauseAtEndOfMediaItems = !settings.gaplessEnabled
                silenceProcessor.setEnabled(settings.deadAirTrimmerEnabled)
                midSideProcessor.stemBalance = settings.midSideStemBalance
                lofiProcessor.tapeEnabled = settings.lofiTapeEnabled

                // Preserve hardware Audio Offload whenever software PCM processors are bypassed
                val needsPcmProcessing = settings.deadAirTrimmerEnabled ||
                    abs(settings.midSideStemBalance) > 0.03f ||
                    settings.lofiTapeEnabled
                player.trackSelectionParameters = player.trackSelectionParameters
                    .buildUpon()
                    .setAudioOffloadPreferences(
                        AudioOffloadPreferences.Builder()
                            .setAudioOffloadMode(
                                if (needsPcmProcessing) AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_DISABLED
                                else AudioOffloadPreferences.AUDIO_OFFLOAD_MODE_ENABLED
                            )
                            .setIsGaplessSupportRequired(settings.gaplessEnabled)
                            .build()
                    )
                    .build()

                if (needsPcmProcessing) {
                    PlaybackRuntimeCoordinator.updatePipelineTelemetry { it.copy(isOffloadActive = false) }
                }

                if (!beatMatchActive) {
                    applyEffectivePlaybackParameters()
                }
                applyReplayGainForItem(player.currentMediaItem)
            }
        }
    }

    /**
     * #27 A-B Beat Looper using native ExoPlayer PlayerMessage on player's Looper (zero UI polling).
     */
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

    private fun applyEffectivePlaybackParameters() {
        val scratchOverride = PlaybackRuntimeCoordinator.scratchSpeedOverride
        if (scratchOverride != null) {
            player.playbackParameters = PlaybackParameters(
                scratchOverride.coerceIn(0.25f, 2.5f),
                PlaybackRuntimeCoordinator.basePitch
            )
            return
        }
        val (speed, pitch) = if (PlaybackRuntimeCoordinator.slowedReverbEnabled) {
            0.85f to 0.85f
        } else {
            PlaybackRuntimeCoordinator.baseSpeed to PlaybackRuntimeCoordinator.basePitch
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
            PlaybackRuntimeCoordinator.basePitch
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
        abLoopMessage?.cancel()
        crossfadeJob?.cancel()
        serviceScope.cancel()
        releaseSecondaryPlayer()
        PlaybackRuntimeCoordinator.onRequestFadeVolume = null
        PlaybackRuntimeCoordinator.onRequestPauseForTimer = null
        PlaybackRuntimeCoordinator.onArmAbLoop = null
        PlaybackRuntimeCoordinator.onApplyEffectivePlaybackParams = null
        PlaybackRuntimeCoordinator.onSeekRelativeOrAbsolute = null
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
