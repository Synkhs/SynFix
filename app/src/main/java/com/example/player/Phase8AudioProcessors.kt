package com.example.player

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import com.example.domain.model.AmbienceMixState
import com.example.domain.model.DrClippingTelemetry
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tanh

/**
 * #53 Audio 8D Orbital:
 * Precalculated 512-entry sin/cos LUT + 256-sample Haas delay ring buffer.
 * Zero heap allocations inside queueInput().
 */
@OptIn(UnstableApi::class)
class Audio8dProcessor : BaseAudioProcessor() {

    @Volatile var enabled: Boolean = false
    @Volatile var orbitSpeed: Float = 1.0f

    private var sampleRateHz: Float = 44100f
    private var phase: Float = 0f

    // Precalculated 512-entry LUT for orbital L/R panning & Haas modulation
    private val sinLut = FloatArray(512) { idx -> sin((idx / 512.0) * 2.0 * PI).toFloat() }
    private val cosLut = FloatArray(512) { idx -> cos((idx / 512.0) * 2.0 * PI).toFloat() }

    // Fixed 256-sample stereo ring buffer for subtle micro-Haas ITD spatial depth
    private val delayLeft = FloatArray(256)
    private val delayRight = FloatArray(256)
    private var delayCursor: Int = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        sampleRateHz = inputAudioFormat.sampleRate.coerceAtLeast(8000).toFloat()
        return inputAudioFormat
    }

    override fun isActive(): Boolean = super.isActive() && enabled

    override fun onFlush() {
        delayLeft.fill(0f)
        delayRight.fill(0f)
        delayCursor = 0
        phase = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size <= 0) return

        val outputBuffer = replaceOutputBuffer(size)
        val stepRad = ((0.18f * orbitSpeed.coerceIn(0.3f, 2.5f)) / sampleRateHz) * 512f

        var offset = position
        while (offset + 4 <= limit) {
            val l = inputBuffer.getShort(offset).toFloat()
            val r = inputBuffer.getShort(offset + 2).toFloat()

            delayLeft[delayCursor] = l
            delayRight[delayCursor] = r

            val lutIdx = phase.toInt() and 511
            val pan = sinLut[lutIdx] // -1..+1
            val elevation = 0.88f + 0.12f * cosLut[lutIdx]

            val haasOffsetL = (if (pan > 0f) (pan * 18f).toInt() else 1).coerceIn(1, 250)
            val haasOffsetR = (if (pan < 0f) (-pan * 18f).toInt() else 1).coerceIn(1, 250)

            val delayedL = delayLeft[(delayCursor - haasOffsetL) and 255]
            val delayedR = delayRight[(delayCursor - haasOffsetR) and 255]

            val leftGain = (0.72f - pan * 0.42f) * elevation
            val rightGain = (0.72f + pan * 0.42f) * elevation

            val outL = (delayedL * leftGain + r * 0.14f).coerceIn(-32768f, 32767f).toInt().toShort()
            val outR = (delayedR * rightGain + l * 0.14f).coerceIn(-32768f, 32767f).toInt().toShort()

            outputBuffer.putShort(outL)
            outputBuffer.putShort(outR)

            delayCursor = (delayCursor + 1) and 255
            phase += stepRad
            if (phase >= 512f) phase -= 512f
            offset += 4
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
    }
}

/**
 * #57 Harmonic Bass Exciter:
 * 1-pole low-pass filter (< 95 Hz) feeding a gentle odd/even harmonic wave-shaper
 * mixed back cleanly so tiny phone speakers perceive deep sub-bass fundamentals.
 * Zero heap allocations inside queueInput().
 */
@OptIn(UnstableApi::class)
class HarmonicBassExciterProcessor : BaseAudioProcessor() {

    @Volatile var level: Float = 0f

    private var lpfAlpha: Float = 0.015f
    private var lpfStateL: Float = 0f
    private var lpfStateR: Float = 0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        val sr = inputAudioFormat.sampleRate.coerceAtLeast(8000).toFloat()
        val rc = 1.0f / (2.0f * PI.toFloat() * 95f)
        val dt = 1.0f / sr
        lpfAlpha = (dt / (rc + dt)).coerceIn(0.002f, 0.25f)
        return inputAudioFormat
    }

    override fun isActive(): Boolean = super.isActive() && level > 0.02f

    override fun onFlush() {
        lpfStateL = 0f
        lpfStateR = 0f
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size <= 0) return

        val outputBuffer = replaceOutputBuffer(size)
        val amount = level.coerceIn(0f, 1f) * 0.52f
        val alpha = lpfAlpha

        var offset = position
        while (offset + 4 <= limit) {
            val lNorm = inputBuffer.getShort(offset) / 32768f
            val rNorm = inputBuffer.getShort(offset + 2) / 32768f

            lpfStateL += alpha * (lNorm - lpfStateL)
            lpfStateR += alpha * (rNorm - lpfStateR)

            // Even + odd upper harmonic generation from sub-bass envelope
            val harmL = tanh((lpfStateL * 2.6f).toDouble()).toFloat() + 0.35f * (lpfStateL * lpfStateL)
            val harmR = tanh((lpfStateR * 2.6f).toDouble()).toFloat() + 0.35f * (lpfStateR * lpfStateR)

            val outL = ((lNorm + harmL * amount) * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            val outR = ((rNorm + harmR * amount) * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()

            outputBuffer.putShort(outL)
            outputBuffer.putShort(outR)
            offset += 4
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
    }
}

/**
 * #59 Live DR & Clipping Guard:
 * Measures real-time crest factor (DR dB) and consecutive clipping peaks (0 dBFS),
 * applying transparent -3 dB protection attenuation when clipping is detected.
 * Zero heap allocations inside queueInput().
 */
@OptIn(UnstableApi::class)
class LiveDrClippingGuardProcessor : BaseAudioProcessor() {

    @Volatile var guardEnabled: Boolean = true

    @Volatile var latestDrDb: Float = 12f
        private set
    @Volatile var latestPeakNorm: Float = 0f
        private set
    @Volatile var isAttenuating: Boolean = false
        private set

    private var peakHold: Float = 0f
    private var sumSq: Double = 0.0
    private var sampleCounter: Int = 0
    private var clipRun: Int = 0
    private var attenuationHoldSamples: Int = 0

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        return inputAudioFormat
    }

    override fun isActive(): Boolean = super.isActive() && guardEnabled

    override fun onFlush() {
        peakHold = 0f
        sumSq = 0.0
        sampleCounter = 0
        clipRun = 0
        attenuationHoldSamples = 0
        isAttenuating = false
    }

    fun snapshotTelemetry(): DrClippingTelemetry = DrClippingTelemetry(
        estimatedDrDb = latestDrDb,
        peakNormalized = latestPeakNorm,
        clippingGuardActive = isAttenuating
    )

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size <= 0) return

        val outputBuffer = replaceOutputBuffer(size)

        var offset = position
        while (offset + 4 <= limit) {
            val l = inputBuffer.getShort(offset) / 32768f
            val r = inputBuffer.getShort(offset + 2) / 32768f

            val absMax = maxOf(abs(l), abs(r))
            if (absMax > peakHold) peakHold = absMax
            sumSq += (l * l + r * r) * 0.5

            if (absMax >= 0.992f) {
                clipRun++
                if (clipRun >= 3) {
                    attenuationHoldSamples = 22_050 // ~0.5s smooth protection hold
                }
            } else {
                clipRun = 0
            }

            val gain = if (attenuationHoldSamples > 0) {
                attenuationHoldSamples--
                0.7079f // -3.0 dB headroom safety
            } else {
                1.0f
            }

            sampleCounter++
            if (sampleCounter >= 4096) {
                val rms = sqrt(sumSq / sampleCounter).toFloat().coerceAtLeast(1e-4f)
                val dr = (20.0 * log10((peakHold.coerceAtLeast(rms) / rms).toDouble())).toFloat()
                    .coerceIn(1f, 24f)
                latestDrDb = dr
                latestPeakNorm = peakHold.coerceIn(0f, 1f)
                isAttenuating = attenuationHoldSamples > 0
                peakHold = 0f
                sumSq = 0.0
                sampleCounter = 0
            }

            val outL = (l * gain * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            val outR = (r * gain * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            outputBuffer.putShort(outL)
            outputBuffer.putShort(outR)
            offset += 4
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
    }
}

/**
 * Shared procedural audio generator for #52 Focus & Sleep Ambience Mixer:
 * Generates Rain, Warm Fireplace crackle, Pink Noise, 40Hz Focus Gamma binaural,
 * and 4Hz Deep Sleep Theta binaural inside a fixed 8192-byte circular state without allocations.
 */
class ProceduralAmbienceSynthesizer(private var sampleRateHz: Float = 44100f) {
    private var rngState: Int = 0x5EED1234
    private var b0 = 0f; private var b1 = 0f; private var b2 = 0f
    private var rainLp = 0f; private var fireLp = 0f
    private var crackleEnv = 0f
    private var phaseL40 = 0f; private var phaseR40 = 0f
    private var phaseL4 = 0f; private var phaseR4 = 0f

    // Pre-allocated 8192-byte circular scratch buffer (2048 stereo frames)
    val ringBufferBytes = ByteArray(8192)
    private var ringIdx = 0

    fun setSampleRate(rate: Int) {
        sampleRateHz = rate.coerceAtLeast(8000).toFloat()
    }

    fun reset() {
        b0 = 0f; b1 = 0f; b2 = 0f; rainLp = 0f; fireLp = 0f; crackleEnv = 0f
        phaseL40 = 0f; phaseR40 = 0f; phaseL4 = 0f; phaseR4 = 0f
        ringIdx = 0
    }

    private fun nextWhite(): Float {
        var x = rngState
        x = x xor (x shl 13)
        x = x xor (x ushr 17)
        x = x xor (x shl 5)
        rngState = x
        return (x.toFloat() / Int.MAX_VALUE.toFloat())
    }

    fun nextStereoSample(mix: AmbienceMixState, outPair: FloatArray) {
        val white = nextWhite()

        // Paul Kellet fast 3-tap pink noise approximation
        b0 = 0.99765f * b0 + white * 0.0990460f
        b1 = 0.96300f * b1 + white * 0.2965164f
        b2 = 0.57000f * b2 + white * 1.0526913f
        val pink = (b0 + b1 + b2 + white * 0.1848f) * 0.12f

        // Rain: soft band-smoothed noise
        rainLp += 0.18f * (pink * 1.35f - rainLp)
        val rainSample = rainLp * mix.rainLevel

        // Fire: low warm rumble + random micro-pops
        fireLp += 0.035f * (pink * 1.6f - fireLp)
        if (abs(white) > 0.985f) crackleEnv = 0.55f
        crackleEnv *= 0.96f
        val fireSample = (fireLp + white * crackleEnv * 0.28f) * mix.fireLevel

        val pinkSample = pink * mix.pinkNoiseLevel

        // Binaural 40 Hz (L=200Hz, R=240Hz)
        var binL = 0f
        var binR = 0f
        if (mix.binaural40HzLevel > 0.01f) {
            val incL = (2f * PI.toFloat() * 200f) / sampleRateHz
            val incR = (2f * PI.toFloat() * 240f) / sampleRateHz
            phaseL40 = (phaseL40 + incL).let { if (it > 6.2831855f) it - 6.2831855f else it }
            phaseR40 = (phaseR40 + incR).let { if (it > 6.2831855f) it - 6.2831855f else it }
            val g = mix.binaural40HzLevel * 0.18f
            binL += sin(phaseL40) * g
            binR += sin(phaseR40) * g
        }

        // Binaural 4 Hz Theta (L=140Hz, R=144Hz)
        if (mix.binaural4HzLevel > 0.01f) {
            val incL = (2f * PI.toFloat() * 140f) / sampleRateHz
            val incR = (2f * PI.toFloat() * 144f) / sampleRateHz
            phaseL4 = (phaseL4 + incL).let { if (it > 6.2831855f) it - 6.2831855f else it }
            phaseR4 = (phaseR4 + incR).let { if (it > 6.2831855f) it - 6.2831855f else it }
            val g = mix.binaural4HzLevel * 0.18f
            binL += sin(phaseL4) * g
            binR += sin(phaseR4) * g
        }

        val common = rainSample + fireSample + pinkSample
        outPair[0] = (common + binL).coerceIn(-0.6f, 0.6f)
        outPair[1] = (common + binR).coerceIn(-0.6f, 0.6f)

        val byteSample = ((outPair[0] + 1f) * 127f).toInt().toByte()
        ringBufferBytes[ringIdx] = byteSample
        ringIdx = (ringIdx + 1) and 8191
    }
}

/**
 * #52 Focus & Sleep Ambience Mixer (inline DSP processor when ExoPlayer is playing):
 * Zero allocations inside queueInput().
 */
@OptIn(UnstableApi::class)
class AmbienceMixerAudioProcessor : BaseAudioProcessor() {

    @Volatile var mixState: AmbienceMixState = AmbienceMixState()

    private val synth = ProceduralAmbienceSynthesizer()
    private val samplePair = FloatArray(2)

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.channelCount != 2 || inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            return AudioProcessor.AudioFormat.NOT_SET
        }
        synth.setSampleRate(inputAudioFormat.sampleRate)
        return inputAudioFormat
    }

    override fun isActive(): Boolean {
        val m = mixState
        return super.isActive() && m.enabled &&
            (m.rainLevel + m.fireLevel + m.pinkNoiseLevel + m.binaural40HzLevel + m.binaural4HzLevel) > 0.02f
    }

    override fun onFlush() {
        synth.reset()
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val position = inputBuffer.position()
        val limit = inputBuffer.limit()
        val size = limit - position
        if (size <= 0) return

        val outputBuffer = replaceOutputBuffer(size)
        val m = mixState

        var offset = position
        while (offset + 4 <= limit) {
            val l = inputBuffer.getShort(offset) / 32768f
            val r = inputBuffer.getShort(offset + 2) / 32768f
            synth.nextStereoSample(m, samplePair)

            val outL = ((l + samplePair[0]) * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()
            val outR = ((r + samplePair[1]) * 32767f).coerceIn(-32768f, 32767f).toInt().toShort()

            outputBuffer.putShort(outL)
            outputBuffer.putShort(outR)
            offset += 4
        }

        inputBuffer.position(limit)
        outputBuffer.flip()
    }
}

/**
 * #52 Standalone on-demand AudioTrack generator when ambience mixer is enabled while music is paused.
 * Freed immediately when disabled or when ExoPlayer takes over mixing.
 */
class AmbienceStandaloneEngine {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var playJob: Job? = null
    private val _isStandaloneRunning = MutableStateFlow(false)
    val isStandaloneRunning: StateFlow<Boolean> = _isStandaloneRunning.asStateFlow()

    @Volatile
    private var currentMix = AmbienceMixState()

    fun update(mix: AmbienceMixState, isMusicPlaying: Boolean) {
        currentMix = mix
        val hasLevel = (mix.rainLevel + mix.fireLevel + mix.pinkNoiseLevel + mix.binaural40HzLevel + mix.binaural4HzLevel) > 0.02f
        val shouldRunStandalone = mix.enabled && hasLevel && !isMusicPlaying
        if (shouldRunStandalone && playJob == null) {
            startStandalone()
        } else if (!shouldRunStandalone && playJob != null) {
            stopStandalone()
        }
    }

    private fun startStandalone() {
        playJob?.cancel()
        playJob = scope.launch {
            val sr = 44100
            val minBytes = AudioTrack.getMinBufferSize(
                sr,
                AudioFormat.CHANNEL_OUT_STEREO,
                AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(4096)

            val track = runCatching {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(sr)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .build()
                    )
                    .setBufferSizeInBytes(minBytes)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            }.getOrNull() ?: return@launch

            val synth = ProceduralAmbienceSynthesizer(sr.toFloat())
            val pcmShort = ShortArray(2048)
            val pair = FloatArray(2)
            _isStandaloneRunning.value = true

            try {
                track.play()
                while (isActive) {
                    val m = currentMix
                    var idx = 0
                    while (idx < pcmShort.size) {
                        synth.nextStereoSample(m, pair)
                        pcmShort[idx] = (pair[0] * 32767f).toInt().toShort()
                        pcmShort[idx + 1] = (pair[1] * 32767f).toInt().toShort()
                        idx += 2
                    }
                    track.write(pcmShort, 0, pcmShort.size)
                }
            } finally {
                _isStandaloneRunning.value = false
                runCatching { track.stop() }
                runCatching { track.release() }
            }
        }
    }

    fun stopStandalone() {
        playJob?.cancel()
        playJob = null
        _isStandaloneRunning.value = false
    }

    fun destroy() {
        stopStandalone()
        scope.cancel()
    }
}
