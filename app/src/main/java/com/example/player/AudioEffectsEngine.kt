package com.example.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.PresetReverb
import android.media.audiofx.Virtualizer
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import com.example.domain.model.AudioOutputType
import com.example.domain.model.EqualizerPreset
import com.example.domain.model.FocusFilterBand
import com.example.domain.model.VenueReverbPreset
import kotlinx.collections.immutable.persistentListOf
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.log10
import kotlin.math.sqrt

object BuiltInEqPresets {
    val BAND_FREQUENCIES_HZ = floatArrayOf(31f, 62f, 125f, 250f, 500f, 1000f, 2000f, 4000f, 8000f, 16000f)
    val BAND_LABELS = listOf("31", "62", "125", "250", "500", "1k", "2k", "4k", "8k", "16k")

    val PRESETS: List<EqualizerPreset> = listOf(
        EqualizerPreset("flat", "Plano", persistentListOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
        EqualizerPreset("bass_boost", "Bass Boost", persistentListOf(7f, 6f, 4.5f, 2.5f, 0.5f, 0f, 0f, 1f, 1.5f, 2f), -1f, 550),
        EqualizerPreset("trap_urban", "Trap / Urbano", persistentListOf(6.5f, 7.5f, 5f, 1.5f, -1f, 0f, 2f, 4f, 4.5f, 3.5f), -1.5f, 650, 250),
        EqualizerPreset("rock", "Rock", persistentListOf(4.5f, 3.5f, 2f, 0f, -1.5f, -1f, 1.5f, 3.5f, 5f, 5.5f), 0f, 250, 200),
        EqualizerPreset("vocal", "Vocal", persistentListOf(-3f, -2f, -1f, 1.5f, 4f, 5f, 4.5f, 3f, 1f, 0f), 0f, 0, 150),
        EqualizerPreset("electronic", "Electrónica", persistentListOf(5.5f, 4.5f, 2f, 0f, -2f, 1f, 3f, 4.5f, 5.5f, 6f), -1f, 400, 450),
        EqualizerPreset("acoustic", "Acústica", persistentListOf(2f, 2.5f, 1.5f, 0.5f, 1f, 1.5f, 2.5f, 3f, 2.5f, 2f), 0f, 0, 200)
    )
}

/**
 * Audio DSP Engine attached to ExoPlayer's audioSessionId:
 * - DynamicsProcessing 10-band EQ + Limiter + #7 Night Mode DRC (MBC hardware compressor on API 28+)
 * - #28 4-Band Focus Filter (Sub, Kick, Vocal, Air) reusing DynamicsProcessing / Equalizer bands
 * - #9 Venue & Acoustic Virtualizer (EnvironmentalReverb)
 * - #31 1-Tap Slowed + Reverb (PresetReverb hardware DSP)
 * - #32 Adaptive Ambient Volume (LoudnessEnhancer + 1s mic sampling every 30s)
 */
class AudioEffectsEngine(private val context: Context) {

    private var audioSessionId: Int = 0
    private var dynamicsProcessing: DynamicsProcessing? = null
    private var legacyEqualizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var environmentalReverb: EnvironmentalReverb? = null
    private var presetReverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var enabled: Boolean = true
    private var currentBandsDb: List<Float> = List(10) { 0f }
    private var currentPreampDb: Float = 0f
    private var currentBassBoost: Int = 0
    private var currentVirtualizer: Int = 0
    private var currentBalance: Float = 0f
    private var nightModeDrcEnabled: Boolean = false
    private var venueReverbPreset: VenueReverbPreset = VenueReverbPreset.OFF
    private var focusFilterBand: FocusFilterBand = FocusFilterBand.NONE
    private var slowedReverbEnabled: Boolean = false
    private var adaptiveAmbientEnabled: Boolean = false
    private var earSafeLimiterEnabled: Boolean = true

    private var currentAmbientGainMb: Int = 0
    private val ambientSampleBuffer = ShortArray(8000)
    private val reusableEffectiveBands = FloatArray(10)
    private val ambientScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var ambientJob: Job? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val _currentOutput = MutableStateFlow(detectCurrentOutput())
    val currentOutput: StateFlow<AudioOutputType> = _currentOutput.asStateFlow()

    private val _usingDynamicsProcessing = MutableStateFlow(false)
    val usingDynamicsProcessing: StateFlow<Boolean> = _usingDynamicsProcessing.asStateFlow()

    private val _adaptiveGainDb = MutableStateFlow(0f)
    val adaptiveGainDb: StateFlow<Float> = _adaptiveGainDb.asStateFlow()

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _currentOutput.value = detectCurrentOutput()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _currentOutput.value = detectCurrentOutput()
        }
    }

    init {
        runCatching {
            audioManager.registerAudioDeviceCallback(deviceCallback, null)
        }
    }

    fun attachToSession(sessionId: Int) {
        if (sessionId <= 0 || sessionId == audioSessionId) return
        releaseEffects()
        audioSessionId = sessionId

        var dpCreated = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dpCreated = initDynamicsProcessing(sessionId)
        }
        _usingDynamicsProcessing.value = dpCreated

        if (!dpCreated) {
            runCatching { legacyEqualizer = Equalizer(0, sessionId) }
        }
        runCatching { bassBoost = BassBoost(0, sessionId) }
        runCatching { virtualizer = Virtualizer(0, sessionId) }
        runCatching { environmentalReverb = EnvironmentalReverb(0, sessionId) }
        runCatching { presetReverb = PresetReverb(0, sessionId) }
        runCatching { loudnessEnhancer = LoudnessEnhancer(sessionId) }

        applyAllSettings()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun initDynamicsProcessing(sessionId: Int): Boolean {
        return runCatching {
            val config = DynamicsProcessing.Config.Builder(
                DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
                2,
                true,
                10,
                true,
                3,
                false,
                0,
                true
            ).build()

            val dp = DynamicsProcessing(0, sessionId, config)
            for (ch in 0 until 2) {
                val eq = dp.getPreEqByChannelIndex(ch)
                eq.isEnabled = true
                BuiltInEqPresets.BAND_FREQUENCIES_HZ.forEachIndexed { idx, freq ->
                    val band = eq.getBand(idx)
                    band.isEnabled = true
                    band.cutoffFrequency = freq
                    band.gain = 0f
                    eq.setBand(idx, band)
                }
                dp.setPreEqByChannelIndex(ch, eq)

                val mbc = dp.getMbcByChannelIndex(ch)
                mbc.isEnabled = false
                val mbcCutoffs = floatArrayOf(250f, 2500f, 20000f)
                for (b in 0 until 3) {
                    val band = mbc.getBand(b)
                    band.isEnabled = true
                    band.cutoffFrequency = mbcCutoffs[b]
                    band.attackTime = 8f
                    band.releaseTime = 180f
                    band.ratio = 4.5f
                    band.threshold = -24f
                    band.kneeWidth = 6f
                    band.noiseGateThreshold = -70f
                    band.expanderRatio = 1f
                    band.preGain = 2f
                    band.postGain = 4f
                    mbc.setBand(b, band)
                }
                dp.setMbcByChannelIndex(ch, mbc)

                val limiter = dp.getLimiterByChannelIndex(ch)
                limiter.isEnabled = true
                limiter.threshold = -0.5f
                limiter.attackTime = 1f
                limiter.releaseTime = 60f
                limiter.ratio = 4f
                dp.setLimiterByChannelIndex(ch, limiter)
            }
            dynamicsProcessing = dp
            true
        }.getOrElse { false }
    }

    fun updateSettings(
        enabled: Boolean,
        bandsDb: List<Float>,
        preampDb: Float,
        bassBoostStrength: Int,
        virtualizerStrength: Int,
        channelBalance: Float,
        nightModeDrc: Boolean,
        venueReverb: VenueReverbPreset,
        focusFilter: FocusFilterBand,
        slowedReverb: Boolean,
        adaptiveAmbient: Boolean,
        earSafeLimiter: Boolean = true
    ) {
        this.enabled = enabled
        this.currentBandsDb = if (bandsDb.size == 10) bandsDb else List(10) { bandsDb.getOrElse(it) { 0f } }
        this.currentPreampDb = preampDb.coerceIn(-12f, 12f)
        this.currentBassBoost = bassBoostStrength.coerceIn(0, 1000)
        this.currentVirtualizer = virtualizerStrength.coerceIn(0, 1000)
        this.currentBalance = channelBalance.coerceIn(-1f, 1f)
        this.nightModeDrcEnabled = nightModeDrc
        this.venueReverbPreset = venueReverb
        this.focusFilterBand = focusFilter
        this.slowedReverbEnabled = slowedReverb
        this.earSafeLimiterEnabled = earSafeLimiter

        if (this.adaptiveAmbientEnabled != adaptiveAmbient) {
            this.adaptiveAmbientEnabled = adaptiveAmbient
            updateAdaptiveAmbientLoop(adaptiveAmbient)
        }
        applyAllSettings()
    }

    private fun focusBandDeltaDb(bandIndex: Int): Float = when (focusFilterBand) {
        FocusFilterBand.NONE -> 0f
        FocusFilterBand.SUB -> if (bandIndex <= 1) 4.5f else -10f
        FocusFilterBand.KICK -> if (bandIndex in 1..3) 4.0f else -9f
        FocusFilterBand.VOCAL -> if (bandIndex in 4..6) 4.5f else -9.5f
        FocusFilterBand.AIR -> if (bandIndex >= 7) 5.0f else -9f
    }

    private fun effectiveBandDb(index: Int): Float {
        val base = if (enabled) currentBandsDb.getOrElse(index) { 0f } else 0f
        return (base + focusBandDeltaDb(index)).coerceIn(-15f, 15f)
    }

    private fun applyAllSettings() {
        if (audioSessionId <= 0) return

        val eqOrFocusActive = enabled || focusFilterBand != FocusFilterBand.NONE
        val hasActiveBalanceOrPreamp = kotlin.math.abs(currentBalance) > 0.01f || kotlin.math.abs(currentPreampDb) > 0.05f
        val dpNeeded = eqOrFocusActive || nightModeDrcEnabled || hasActiveBalanceOrPreamp ||
            (earSafeLimiterEnabled && (eqOrFocusActive || nightModeDrcEnabled || currentPreampDb > 0f))
        val dp = dynamicsProcessing
        if (dp != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching {
                dp.enabled = dpNeeded
                if (!dpNeeded) return@runCatching
                val leftAttenuationDb = if (currentBalance > 0f) -24f * currentBalance else 0f
                val rightAttenuationDb = if (currentBalance < 0f) -24f * (-currentBalance) else 0f

                dp.setInputGainbyChannel(0, currentPreampDb + leftAttenuationDb)
                dp.setInputGainbyChannel(1, currentPreampDb + rightAttenuationDb)

                for (ch in 0 until 2) {
                    val eq = dp.getPreEqByChannelIndex(ch)
                    eq.isEnabled = eqOrFocusActive
                    for (i in 0 until 10) {
                        val band = eq.getBand(i)
                        band.isEnabled = eqOrFocusActive
                        band.cutoffFrequency = BuiltInEqPresets.BAND_FREQUENCIES_HZ[i]
                        band.gain = effectiveBandDb(i)
                        eq.setBand(i, band)
                    }
                    dp.setPreEqByChannelIndex(ch, eq)

                    val mbc = dp.getMbcByChannelIndex(ch)
                    mbc.isEnabled = nightModeDrcEnabled
                    dp.setMbcByChannelIndex(ch, mbc)

                    // #56 Ear-Safe SPL Limiter + #7 Night Mode DRC Limiter
                    val limiter = dp.getLimiterByChannelIndex(ch)
                    limiter.isEnabled = earSafeLimiterEnabled || nightModeDrcEnabled
                    when {
                        nightModeDrcEnabled -> {
                            limiter.threshold = -6.0f
                            limiter.ratio = 6.0f
                            limiter.postGain = 2.0f
                        }
                        earSafeLimiterEnabled -> {
                            limiter.threshold = -1.5f
                            limiter.ratio = 8.0f
                            limiter.postGain = 0.0f
                        }
                        else -> {
                            limiter.threshold = -0.2f
                            limiter.ratio = 2.0f
                            limiter.postGain = 0.0f
                        }
                    }
                    dp.setLimiterByChannelIndex(ch, limiter)
                }
            }
        }

        legacyEqualizer?.let { eq ->
            runCatching {
                eq.enabled = eqOrFocusActive
                if (eqOrFocusActive) {
                    val numBands = eq.numberOfBands.toInt()
                    val range = eq.bandLevelRange
                    val minMb = range[0].toFloat()
                    val maxMb = range[1].toFloat()
                    for (bandIdx in 0 until numBands) {
                        val centerFreqHz = eq.getCenterFreq(bandIdx.toShort()) / 1000f
                        val gainDb = interpolateGainForFreq(centerFreqHz) + currentPreampDb
                        val targetMb = (gainDb * 100f).coerceIn(minMb, maxMb).toInt().toShort()
                        eq.setBandLevel(bandIdx.toShort(), targetMb)
                    }
                }
            }
        }

        bassBoost?.let { bb ->
            runCatching {
                bb.enabled = enabled && currentBassBoost > 0
                if (bb.strengthSupported && enabled) bb.setStrength(currentBassBoost.toShort())
            }
        }

        virtualizer?.let { virt ->
            runCatching {
                virt.enabled = enabled && currentVirtualizer > 0
                if (virt.strengthSupported && enabled) virt.setStrength(currentVirtualizer.toShort())
            }
        }

        // #9 Venue & Acoustic Virtualizer (EnvironmentalReverb)
        environmentalReverb?.let { reverb ->
            runCatching {
                if (venueReverbPreset == VenueReverbPreset.OFF) {
                    reverb.enabled = false
                } else {
                    reverb.roomLevel = venueReverbPreset.roomLevelMb
                    reverb.reverbLevel = venueReverbPreset.reverbLevelMb
                    reverb.decayTime = venueReverbPreset.decayMs
                    reverb.density = venueReverbPreset.density
                    reverb.diffusion = venueReverbPreset.diffusion
                    reverb.enabled = true
                }
            }
        }

        // #31 1-Tap Slowed + Reverb via hardware PresetReverb
        presetReverb?.let { pr ->
            runCatching {
                if (slowedReverbEnabled) {
                    pr.preset = PresetReverb.PRESET_LARGEHALL
                    pr.enabled = true
                } else {
                    pr.enabled = false
                }
            }
        }

        // #32 Adaptive Ambient Volume via hardware LoudnessEnhancer
        loudnessEnhancer?.let { le ->
            runCatching {
                if (adaptiveAmbientEnabled && currentAmbientGainMb > 0) {
                    le.setTargetGain(currentAmbientGainMb)
                    le.enabled = true
                } else {
                    le.setTargetGain(0)
                    le.enabled = false
                }
            }
        }
    }

    /**
     * #32 Adaptive Ambient Volume:
     * Samples 1s of ambient mic audio every 30s (only if RECORD_AUDIO is granted)
     * and adjusts hardware LoudnessEnhancer gain (+0 to +9 dB).
     */
    private fun updateAdaptiveAmbientLoop(active: Boolean) {
        ambientJob?.cancel()
        if (!active) {
            currentAmbientGainMb = 0
            _adaptiveGainDb.value = 0f
            applyAllSettings()
            return
        }
        ambientJob = ambientScope.launch {
            while (isActive) {
                val hasPerm = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.RECORD_AUDIO
                ) == PackageManager.PERMISSION_GRANTED
                if (hasPerm) {
                    val ambientDb = sampleAmbientNoiseDb()
                    // Quiet (< -42 dB) -> 0 dB boost; Noisy (-18 dB) -> up to +9 dB boost
                    val boostDb = (((ambientDb + 42f) / 24f) * 9f).coerceIn(0f, 9f)
                    currentAmbientGainMb = (boostDb * 100f).toInt()
                    _adaptiveGainDb.value = boostDb
                    withContext(Dispatchers.Main) { applyAllSettings() }
                } else {
                    currentAmbientGainMb = 250
                    _adaptiveGainDb.value = 2.5f
                    withContext(Dispatchers.Main) { applyAllSettings() }
                }
                delay(30_000L)
            }
        }
    }

    private fun sampleAmbientNoiseDb(): Float {
        val sampleRate = 8000
        val minBuf = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        ).coerceAtLeast(2048)
        var recorder: AudioRecord? = null
        return try {
            recorder = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                minBuf
            )
            if (recorder.state != AudioRecord.STATE_INITIALIZED) return -40f
            val buf = ambientSampleBuffer
            recorder.startRecording()
            val read = recorder.read(buf, 0, buf.size)
            recorder.stop()
            if (read <= 0) return -40f
            var sq = 0.0
            for (i in 0 until read) {
                val norm = buf[i] / 32768.0
                sq += norm * norm
            }
            val rms = sqrt(sq / read).coerceAtLeast(1e-5)
            (20.0 * log10(rms)).toFloat().coerceIn(-60f, 0f)
        } catch (_: Throwable) {
            -40f
        } finally {
            runCatching { recorder?.release() }
        }
    }

    private fun interpolateGainForFreq(freqHz: Float): Float {
        val freqs = BuiltInEqPresets.BAND_FREQUENCIES_HZ
        for (i in 0 until 10) {
            reusableEffectiveBands[i] = effectiveBandDb(i)
        }
        if (freqHz <= freqs.first()) return reusableEffectiveBands[0]
        if (freqHz >= freqs.last()) return reusableEffectiveBands[9]
        for (i in 0 until freqs.lastIndex) {
            if (freqHz >= freqs[i] && freqHz <= freqs[i + 1]) {
                val ratio = (freqHz - freqs[i]) / (freqs[i + 1] - freqs[i])
                return reusableEffectiveBands[i] + ratio * (reusableEffectiveBands[i + 1] - reusableEffectiveBands[i])
            }
        }
        return 0f
    }

    private fun detectCurrentOutput(): AudioOutputType {
        val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        var hasWired = false
        var hasBluetooth = false
        for (device in devices) {
            when (device.type) {
                AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
                AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
                AudioDeviceInfo.TYPE_BLE_HEADSET,
                AudioDeviceInfo.TYPE_BLE_SPEAKER -> hasBluetooth = true
                AudioDeviceInfo.TYPE_WIRED_HEADSET,
                AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
                AudioDeviceInfo.TYPE_USB_HEADSET -> hasWired = true
            }
        }
        return when {
            hasBluetooth -> AudioOutputType.BLUETOOTH
            hasWired -> AudioOutputType.WIRED_HEADSET
            else -> AudioOutputType.SPEAKER
        }
    }

    fun releaseEffects() {
        runCatching { dynamicsProcessing?.release() }
        runCatching { legacyEqualizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        runCatching { environmentalReverb?.release() }
        runCatching { presetReverb?.release() }
        runCatching { loudnessEnhancer?.release() }
        dynamicsProcessing = null
        legacyEqualizer = null
        bassBoost = null
        virtualizer = null
        environmentalReverb = null
        presetReverb = null
        loudnessEnhancer = null
    }

    fun destroy() {
        ambientJob?.cancel()
        ambientScope.cancel()
        runCatching { audioManager.unregisterAudioDeviceCallback(deviceCallback) }
        releaseEffects()
    }
}
