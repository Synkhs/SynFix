package com.example.player

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.runtime.Immutable
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink
import com.example.domain.model.Song
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * #51 Hold-to-Peek Scrubber:
 * Auxiliary ExoPlayer allocated only while user holds & drags across a song row,
 * and immediately stopped & released on finger lift so Audio Offload / RAM stay intact.
 */
class SharedAuxPlayer(private val context: Context) {
    private var auxPlayer: ExoPlayer? = null
    private var activeSongId: Long? = null

    fun startPeek(song: Song, fraction: Float) {
        val player = auxPlayer ?: ExoPlayer.Builder(context)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build().also { auxPlayer = it }

        val targetMs = (song.durationMs * fraction.coerceIn(0f, 0.96f)).toLong()
        if (activeSongId != song.id) {
            activeSongId = song.id
            player.setMediaItem(MediaItem.fromUri(song.contentUri))
            player.prepare()
        }
        player.seekTo(targetMs)
        player.play()
    }

    fun scrubPeek(song: Song, fraction: Float) {
        val player = auxPlayer ?: return
        val targetMs = (song.durationMs * fraction.coerceIn(0f, 0.96f)).toLong()
        player.seekTo(targetMs)
    }

    fun stopAndRelease() {
        auxPlayer?.run {
            stop()
            release()
        }
        auxPlayer = null
        activeSongId = null
    }
}

@Immutable
data class PocketDjState(
    val deckASong: Song? = null,
    val deckBSong: Song? = null,
    val deckAPlaying: Boolean = false,
    val deckBPlaying: Boolean = false,
    val crossfader: Float = 0.5f, // 0f = Deck A, 1f = Deck B
    val acapellaMashupEnabled: Boolean = false, // #55 Deck A = Vocals (Mid), Deck B = Beat (Side)
    val syncBpmEnabled: Boolean = true,
    val deckAPositionMs: Long = 0L,
    val deckBPositionMs: Long = 0L
)

/**
 * #44 Pocket DJ Dual-Deck Crossfader & #55 Acapella Mashup Lab:
 * Allocates dual ExoPlayers with per-deck MidSideAudioProcessor on demand,
 * and releases both players immediately on dialog exit.
 */
class PocketDjEngine(private val context: Context) {

    private var deckA: ExoPlayer? = null
    private var deckB: ExoPlayer? = null
    private val midSideA = MidSideAudioProcessor()
    private val midSideB = MidSideAudioProcessor()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var tickerJob: Job? = null

    private val _state = MutableStateFlow(PocketDjState())
    val state: StateFlow<PocketDjState> = _state.asStateFlow()

    @OptIn(UnstableApi::class)
    private fun ensureDecksInitialized() {
        if (deckA == null) {
            deckA = buildDeckPlayer(midSideA)
        }
        if (deckB == null) {
            deckB = buildDeckPlayer(midSideB)
        }
        if (tickerJob == null) {
            tickerJob = scope.launch {
                while (isActive) {
                    delay(120L)
                    _state.value = _state.value.copy(
                        deckAPlaying = deckA?.isPlaying == true,
                        deckBPlaying = deckB?.isPlaying == true,
                        deckAPositionMs = deckA?.currentPosition?.coerceAtLeast(0L) ?: 0L,
                        deckBPositionMs = deckB?.currentPosition?.coerceAtLeast(0L) ?: 0L
                    )
                }
            }
        }
    }

    @OptIn(UnstableApi::class)
    private fun buildDeckPlayer(processor: MidSideAudioProcessor): ExoPlayer {
        val factory = object : DefaultRenderersFactory(context) {
            override fun buildAudioSink(
                ctx: Context,
                enableFloatOutput: Boolean,
                enableAudioTrackPlaybackParams: Boolean
            ): AudioSink = DefaultAudioSink.Builder(ctx)
                .setAudioProcessors(arrayOf(processor))
                .setEnableFloatOutput(enableFloatOutput)
                .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                .build()
        }
        return ExoPlayer.Builder(context, factory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.CONTENT_TYPE_MUSIC)
                    .build(),
                false
            )
            .build().apply {
                repeatMode = Player.REPEAT_MODE_ONE
            }
    }

    fun loadDeckA(song: Song) {
        ensureDecksInitialized()
        deckA?.apply {
            setMediaItem(MediaItem.fromUri(song.contentUri))
            prepare()
        }
        _state.value = _state.value.copy(deckASong = song, deckAPositionMs = 0L)
        applyMixAndSync()
    }

    fun loadDeckB(song: Song) {
        ensureDecksInitialized()
        deckB?.apply {
            setMediaItem(MediaItem.fromUri(song.contentUri))
            prepare()
        }
        _state.value = _state.value.copy(deckBSong = song, deckBPositionMs = 0L)
        applyMixAndSync()
    }

    fun toggleDeckA() {
        ensureDecksInitialized()
        val p = deckA ?: return
        if (p.isPlaying) p.pause() else p.play()
        _state.value = _state.value.copy(deckAPlaying = p.isPlaying)
    }

    fun toggleDeckB() {
        ensureDecksInitialized()
        val p = deckB ?: return
        if (p.isPlaying) p.pause() else p.play()
        _state.value = _state.value.copy(deckBPlaying = p.isPlaying)
    }

    fun seekDeckA(fraction: Float) {
        val s = _state.value.deckASong ?: return
        deckA?.seekTo((s.durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun seekDeckB(fraction: Float) {
        val s = _state.value.deckBSong ?: return
        deckB?.seekTo((s.durationMs * fraction.coerceIn(0f, 1f)).toLong())
    }

    fun setCrossfader(value: Float) {
        _state.value = _state.value.copy(crossfader = value.coerceIn(0f, 1f))
        applyMixAndSync()
    }

    fun setAcapellaMashup(enabled: Boolean) {
        _state.value = _state.value.copy(acapellaMashupEnabled = enabled)
        applyMixAndSync()
    }

    fun setSyncBpm(enabled: Boolean) {
        _state.value = _state.value.copy(syncBpmEnabled = enabled)
        applyMixAndSync()
    }

    private fun applyMixAndSync() {
        val st = _state.value
        val cf = st.crossfader
        // Equal-power crossfade curve
        val volA = kotlin.math.cos(cf * Math.PI * 0.5).toFloat().coerceIn(0f, 1f)
        val volB = kotlin.math.sin(cf * Math.PI * 0.5).toFloat().coerceIn(0f, 1f)
        deckA?.volume = volA
        deckB?.volume = volB

        // #55 Acapella Mashup Lab: Deck A isolated Mid Vocals (+1f), Deck B isolated Side Instrumental (-1f)
        if (st.acapellaMashupEnabled) {
            midSideA.stemBalance = 0.96f
            midSideB.stemBalance = -0.96f
        } else {
            midSideA.stemBalance = 0f
            midSideB.stemBalance = 0f
        }

        val bpmA = st.deckASong?.bpm?.toFloat() ?: 0f
        val bpmB = st.deckBSong?.bpm?.toFloat() ?: 0f
        if (st.syncBpmEnabled && bpmA > 0f && bpmB > 0f) {
            val ratio = (bpmA / bpmB).coerceIn(0.85f, 1.18f)
            deckB?.playbackParameters = PlaybackParameters(ratio, 1.0f)
        } else {
            deckB?.playbackParameters = PlaybackParameters.DEFAULT
        }
    }

    fun releaseAll() {
        tickerJob?.cancel()
        tickerJob = null
        deckA?.run { stop(); release() }
        deckB?.run { stop(); release() }
        deckA = null
        deckB = null
        _state.value = PocketDjState()
    }
}

@Immutable
data class BlindTestAbState(
    val songA: Song? = null,
    val songB: Song? = null,
    val activeSlot: String = "A", // "A" or "B"
    val isPlaying: Boolean = false,
    val revealed: Boolean = false,
    val userGuessHigherQuality: String? = null
)

/**
 * #77 Blind Test A/B:
 * Compares two local tracks (e.g. FLAC vs MP3) synchronized at the same millisecond
 * with zero visual hints until revealed. Releases both players immediately on close.
 */
class BlindTestAbEngine(private val context: Context) {
    private var playerA: ExoPlayer? = null
    private var playerB: ExoPlayer? = null

    private val _state = MutableStateFlow(BlindTestAbState())
    val state: StateFlow<BlindTestAbState> = _state.asStateFlow()

    fun startTest(first: Song, second: Song) {
        releaseAll()
        // Randomize slot A/B order so user cannot guess by order
        val shuffled = listOf(first, second).shuffled()
        val sA = shuffled[0]
        val sB = shuffled[1]

        val pA = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(sA.contentUri))
            prepare()
            volume = 1f
            playWhenReady = true
        }
        val pB = ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(sB.contentUri))
            prepare()
            volume = 0f
            playWhenReady = true
        }
        playerA = pA
        playerB = pB
        _state.value = BlindTestAbState(
            songA = sA,
            songB = sB,
            activeSlot = "A",
            isPlaying = true,
            revealed = false
        )
    }

    fun switchTo(slot: String) {
        val pA = playerA ?: return
        val pB = playerB ?: return
        val currentPos = if (_state.value.activeSlot == "A") pA.currentPosition else pB.currentPosition
        if (slot == "A") {
            if (abs(pA.currentPosition - currentPos) > 45L) pA.seekTo(currentPos)
            pA.volume = 1f
            pB.volume = 0f
        } else {
            if (abs(pB.currentPosition - currentPos) > 45L) pB.seekTo(currentPos)
            pA.volume = 0f
            pB.volume = 1f
        }
        _state.value = _state.value.copy(activeSlot = slot)
    }

    fun togglePlayPause() {
        val pA = playerA ?: return
        val pB = playerB ?: return
        val next = !_state.value.isPlaying
        if (next) {
            pA.play()
            pB.play()
        } else {
            pA.pause()
            pB.pause()
        }
        _state.value = _state.value.copy(isPlaying = next)
    }

    fun submitGuess(slot: String) {
        _state.value = _state.value.copy(revealed = true, userGuessHigherQuality = slot)
    }

    fun releaseAll() {
        playerA?.run { stop(); release() }
        playerB?.run { stop(); release() }
        playerA = null
        playerB = null
        _state.value = BlindTestAbState()
    }
}
