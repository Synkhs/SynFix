package com.example.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.model.*
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "synfix_preferences")

class SynFixPreferences(private val context: Context) {

    private object Keys {
        val COLOR_MODE = stringPreferencesKey("color_mode")
        val BRIGHTNESS_MODE = stringPreferencesKey("brightness_mode")
        val SEED_COLOR_ARGB = longPreferencesKey("seed_color_argb")
        val NOW_PLAYING_STYLE = stringPreferencesKey("now_playing_style")
        val LIBRARY_LAYOUT = stringPreferencesKey("library_layout")
        val ARTWORK_SHAPE = stringPreferencesKey("artwork_shape")
        val BEAT_SYNCED_MORPH = booleanPreferencesKey("beat_synced_morph")
        val PROCEDURAL_ART_FALLBACK = booleanPreferencesKey("procedural_art_fallback")
        val FLOATING_BUBBLE_ENABLED = booleanPreferencesKey("floating_bubble_enabled")
        val SUB_BASS_HAPTICS_ENABLED = booleanPreferencesKey("sub_bass_haptics_enabled")

        val GAPLESS_ENABLED = booleanPreferencesKey("gapless_enabled")
        val CROSSFADE_SECONDS = intPreferencesKey("crossfade_seconds")
        val BEAT_MATCHED_CROSSFADE = booleanPreferencesKey("beat_matched_crossfade")
        val PLAYBACK_SPEED = floatPreferencesKey("playback_speed")
        val PLAYBACK_PITCH = floatPreferencesKey("playback_pitch")
        val CHANNEL_BALANCE = floatPreferencesKey("channel_balance")
        val DEAD_AIR_TRIMMER = booleanPreferencesKey("dead_air_trimmer")
        val NIGHT_MODE_DRC = booleanPreferencesKey("night_mode_drc")
        val REPLAY_GAIN = booleanPreferencesKey("replay_gain")
        val MID_SIDE_BALANCE = floatPreferencesKey("mid_side_balance")
        val VENUE_REVERB_PRESET = stringPreferencesKey("venue_reverb_preset")
        val FOCUS_FILTER_BAND = stringPreferencesKey("focus_filter_band")
        val LOFI_TAPE_ENABLED = booleanPreferencesKey("lofi_tape_enabled")
        val SLOWED_REVERB_ENABLED = booleanPreferencesKey("slowed_reverb_enabled")
        val ADAPTIVE_AMBIENT_ENABLED = booleanPreferencesKey("adaptive_ambient_enabled")

        val MIN_DURATION_SECONDS = intPreferencesKey("min_duration_seconds")
        val EXCLUDED_FOLDERS = stringSetPreferencesKey("excluded_folders")

        val EQ_ENABLED = booleanPreferencesKey("eq_enabled")
        val EQ_ACTIVE_PRESET_ID = stringPreferencesKey("eq_active_preset_id")
        val EQ_BANDS_CSV = stringPreferencesKey("eq_bands_csv")
        val EQ_PREAMP_DB = floatPreferencesKey("eq_preamp_db")
        val EQ_BASS_BOOST = intPreferencesKey("eq_bass_boost")
        val EQ_VIRTUALIZER = intPreferencesKey("eq_virtualizer")
        val EQ_AUTO_OUTPUT_PROFILE = booleanPreferencesKey("eq_auto_output_profile")
        val EQ_SPEAKER_PRESET = stringPreferencesKey("eq_speaker_preset")
        val EQ_WIRED_PRESET = stringPreferencesKey("eq_wired_preset")
        val EQ_BLUETOOTH_PRESET = stringPreferencesKey("eq_bluetooth_preset")

        // Phase 8 keys
        val GYRO_PARALLAX = booleanPreferencesKey("gyro_parallax")
        val SHAKE_TO_SKIP = booleanPreferencesKey("shake_to_skip")
        val PARTY_STROBE = booleanPreferencesKey("party_strobe")
        val PARTY_STROBE_TORCH = booleanPreferencesKey("party_strobe_torch")
        val CUSTOM_VIBE_CHIPS = stringPreferencesKey("custom_vibe_chips")
        val PINNED_QUEUE_IDS = stringPreferencesKey("pinned_queue_ids")

        val AUDIO_8D_ENABLED = booleanPreferencesKey("audio_8d_enabled")
        val AUDIO_8D_SPEED = floatPreferencesKey("audio_8d_speed")
        val HARMONIC_EXCITER = floatPreferencesKey("harmonic_exciter")
        val SEMITONE_SHIFT = intPreferencesKey("semitone_shift")
        val EAR_SAFE_LIMITER = booleanPreferencesKey("ear_safe_limiter")
        val DR_CLIPPING_GUARD = booleanPreferencesKey("dr_clipping_guard")
        val TURNTABLE_BRAKE = booleanPreferencesKey("turntable_brake")

        val AMBIENCE_ENABLED = booleanPreferencesKey("ambience_enabled")
        val AMBIENCE_RAIN = floatPreferencesKey("ambience_rain")
        val AMBIENCE_FIRE = floatPreferencesKey("ambience_fire")
        val AMBIENCE_PINK = floatPreferencesKey("ambience_pink")
        val AMBIENCE_BINAURAL_40 = floatPreferencesKey("ambience_binaural_40")
        val AMBIENCE_BINAURAL_4 = floatPreferencesKey("ambience_binaural_4")

        val OFFLOAD_SECONDS_ACCUM = longPreferencesKey("offload_seconds_accum")
        val DSP_SECONDS_ACCUM = longPreferencesKey("dsp_seconds_accum")
    }

    private val safeData: Flow<Preferences> = context.dataStore.data
        .catch { e ->
            if (e is IOException) emit(emptyPreferences()) else throw e
        }

    val appearanceSettings: Flow<AppearanceSettings> = safeData.map { parseAppearance(it) }
    val playbackSettings: Flow<PlaybackSettings> = safeData.map { parsePlayback(it) }
    val ambienceMixState: Flow<AmbienceMixState> = safeData.map { prefs ->
        AmbienceMixState(
            enabled = prefs[Keys.AMBIENCE_ENABLED] ?: false,
            rainLevel = prefs[Keys.AMBIENCE_RAIN] ?: 0f,
            fireLevel = prefs[Keys.AMBIENCE_FIRE] ?: 0f,
            pinkNoiseLevel = prefs[Keys.AMBIENCE_PINK] ?: 0f,
            binaural40HzLevel = prefs[Keys.AMBIENCE_BINAURAL_40] ?: 0f,
            binaural4HzLevel = prefs[Keys.AMBIENCE_BINAURAL_4] ?: 0f
        )
    }
    val pinnedQueueIds: Flow<Set<Long>> = safeData.map { prefs ->
        (prefs[Keys.PINNED_QUEUE_IDS] ?: "")
            .split(',')
            .mapNotNull { it.toLongOrNull() }
            .toSet()
    }
    val offloadDspSeconds: Flow<Pair<Long, Long>> = safeData.map { prefs ->
        (prefs[Keys.OFFLOAD_SECONDS_ACCUM] ?: 0L) to (prefs[Keys.DSP_SECONDS_ACCUM] ?: 0L)
    }

    val minDurationSeconds: Flow<Int> = safeData.map { it[Keys.MIN_DURATION_SECONDS] ?: 15 }
    val excludedFolders: Flow<Set<String>> = safeData.map {
        it[Keys.EXCLUDED_FOLDERS] ?: defaultExcludedFolders()
    }

    data class StoredEqState(
        val enabled: Boolean,
        val activePresetId: String,
        val bandsDb: List<Float>,
        val preampDb: Float,
        val bassBoost: Int,
        val virtualizer: Int,
        val autoOutputProfile: Boolean,
        val devicePresetMap: ImmutableMap<AudioOutputType, String>
    )

    val eqState: Flow<StoredEqState> = safeData.map { prefs ->
        val bands = prefs[Keys.EQ_BANDS_CSV]
            ?.split(",")
            ?.mapNotNull { it.toFloatOrNull() }
            ?.takeIf { it.size == 10 }
            ?: List(10) { 0f }

        StoredEqState(
            enabled = prefs[Keys.EQ_ENABLED] ?: true,
            activePresetId = prefs[Keys.EQ_ACTIVE_PRESET_ID] ?: "flat",
            bandsDb = bands,
            preampDb = prefs[Keys.EQ_PREAMP_DB] ?: 0f,
            bassBoost = prefs[Keys.EQ_BASS_BOOST] ?: 0,
            virtualizer = prefs[Keys.EQ_VIRTUALIZER] ?: 0,
            autoOutputProfile = prefs[Keys.EQ_AUTO_OUTPUT_PROFILE] ?: false,
            devicePresetMap = persistentMapOf(
                AudioOutputType.SPEAKER to (prefs[Keys.EQ_SPEAKER_PRESET] ?: "flat"),
                AudioOutputType.WIRED_HEADSET to (prefs[Keys.EQ_WIRED_PRESET] ?: "vocal"),
                AudioOutputType.BLUETOOTH to (prefs[Keys.EQ_BLUETOOTH_PRESET] ?: "bass_boost")
            )
        )
    }

    suspend fun updateAppearance(transform: (AppearanceSettings) -> AppearanceSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(parseAppearance(prefs))
            prefs[Keys.COLOR_MODE] = updated.colorMode.name
            prefs[Keys.BRIGHTNESS_MODE] = updated.brightnessMode.name
            prefs[Keys.SEED_COLOR_ARGB] = updated.seedColorArgb
            prefs[Keys.NOW_PLAYING_STYLE] = updated.nowPlayingStyle.name
            prefs[Keys.LIBRARY_LAYOUT] = updated.libraryLayout.name
            prefs[Keys.ARTWORK_SHAPE] = updated.artworkShape.name
            prefs[Keys.BEAT_SYNCED_MORPH] = updated.beatSyncedMorphEnabled
            prefs[Keys.PROCEDURAL_ART_FALLBACK] = updated.proceduralArtFallback
            prefs[Keys.FLOATING_BUBBLE_ENABLED] = updated.floatingBubbleEnabled
            prefs[Keys.SUB_BASS_HAPTICS_ENABLED] = updated.subBassHapticsEnabled
            prefs[Keys.GYRO_PARALLAX] = updated.gyroParallaxEnabled
            prefs[Keys.SHAKE_TO_SKIP] = updated.shakeToSkipEnabled
            prefs[Keys.PARTY_STROBE] = updated.partyStrobeEnabled
            prefs[Keys.PARTY_STROBE_TORCH] = updated.partyStrobeTorchEnabled
            prefs[Keys.CUSTOM_VIBE_CHIPS] = updated.customVibeChipsCsv
        }
    }

    suspend fun updatePlaybackSettings(transform: (PlaybackSettings) -> PlaybackSettings) {
        context.dataStore.edit { prefs ->
            val updated = transform(parsePlayback(prefs))
            prefs[Keys.GAPLESS_ENABLED] = updated.gaplessEnabled
            prefs[Keys.CROSSFADE_SECONDS] = updated.crossfadeSeconds
            prefs[Keys.BEAT_MATCHED_CROSSFADE] = updated.beatMatchedCrossfade
            prefs[Keys.PLAYBACK_SPEED] = updated.playbackSpeed
            prefs[Keys.PLAYBACK_PITCH] = updated.playbackPitch
            prefs[Keys.CHANNEL_BALANCE] = updated.channelBalance
            prefs[Keys.DEAD_AIR_TRIMMER] = updated.deadAirTrimmerEnabled
            prefs[Keys.NIGHT_MODE_DRC] = updated.nightModeDrcEnabled
            prefs[Keys.REPLAY_GAIN] = updated.replayGainEnabled
            prefs[Keys.MID_SIDE_BALANCE] = updated.midSideStemBalance
            prefs[Keys.VENUE_REVERB_PRESET] = updated.venueReverbPreset.name
            prefs[Keys.FOCUS_FILTER_BAND] = updated.focusFilterBand.name
            prefs[Keys.LOFI_TAPE_ENABLED] = updated.lofiTapeEnabled
            prefs[Keys.SLOWED_REVERB_ENABLED] = updated.slowedReverbEnabled
            prefs[Keys.ADAPTIVE_AMBIENT_ENABLED] = updated.adaptiveAmbientEnabled
            prefs[Keys.AUDIO_8D_ENABLED] = updated.audio8dEnabled
            prefs[Keys.AUDIO_8D_SPEED] = updated.audio8dSpeed
            prefs[Keys.HARMONIC_EXCITER] = updated.harmonicExciterLevel
            prefs[Keys.SEMITONE_SHIFT] = updated.semitoneShift
            prefs[Keys.EAR_SAFE_LIMITER] = updated.earSafeLimiterEnabled
            prefs[Keys.DR_CLIPPING_GUARD] = updated.drClippingGuardEnabled
            prefs[Keys.TURNTABLE_BRAKE] = updated.turntableBrakeEnabled
        }
    }

    suspend fun updateAmbienceMix(state: AmbienceMixState) {
        context.dataStore.edit { prefs ->
            prefs[Keys.AMBIENCE_ENABLED] = state.enabled
            prefs[Keys.AMBIENCE_RAIN] = state.rainLevel
            prefs[Keys.AMBIENCE_FIRE] = state.fireLevel
            prefs[Keys.AMBIENCE_PINK] = state.pinkNoiseLevel
            prefs[Keys.AMBIENCE_BINAURAL_40] = state.binaural40HzLevel
            prefs[Keys.AMBIENCE_BINAURAL_4] = state.binaural4HzLevel
        }
    }

    suspend fun setPinnedQueueIds(ids: Set<Long>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.PINNED_QUEUE_IDS] = ids.joinToString(",")
        }
    }

    suspend fun addOffloadOrDspSeconds(isOffload: Boolean, deltaSeconds: Long) {
        if (deltaSeconds <= 0L) return
        context.dataStore.edit { prefs ->
            if (isOffload) {
                prefs[Keys.OFFLOAD_SECONDS_ACCUM] = (prefs[Keys.OFFLOAD_SECONDS_ACCUM] ?: 0L) + deltaSeconds
            } else {
                prefs[Keys.DSP_SECONDS_ACCUM] = (prefs[Keys.DSP_SECONDS_ACCUM] ?: 0L) + deltaSeconds
            }
        }
    }

    suspend fun setMinDurationSeconds(seconds: Int) {
        context.dataStore.edit { it[Keys.MIN_DURATION_SECONDS] = seconds }
    }

    suspend fun toggleExcludedFolder(folderPath: String) {
        context.dataStore.edit { prefs ->
            val current = (prefs[Keys.EXCLUDED_FOLDERS] ?: defaultExcludedFolders()).toMutableSet()
            if (current.contains(folderPath)) current.remove(folderPath) else current.add(folderPath)
            prefs[Keys.EXCLUDED_FOLDERS] = current
        }
    }

    suspend fun saveEqState(
        enabled: Boolean,
        presetId: String,
        bandsDb: List<Float>,
        preampDb: Float,
        bassBoost: Int,
        virtualizer: Int
    ) {
        context.dataStore.edit { prefs ->
            prefs[Keys.EQ_ENABLED] = enabled
            prefs[Keys.EQ_ACTIVE_PRESET_ID] = presetId
            prefs[Keys.EQ_BANDS_CSV] = bandsDb.joinToString(",")
            prefs[Keys.EQ_PREAMP_DB] = preampDb
            prefs[Keys.EQ_BASS_BOOST] = bassBoost
            prefs[Keys.EQ_VIRTUALIZER] = virtualizer
        }
    }

    suspend fun setAutoOutputProfile(enabled: Boolean) {
        context.dataStore.edit { it[Keys.EQ_AUTO_OUTPUT_PROFILE] = enabled }
    }

    suspend fun assignPresetToOutput(outputType: AudioOutputType, presetId: String) {
        context.dataStore.edit { prefs ->
            when (outputType) {
                AudioOutputType.SPEAKER -> prefs[Keys.EQ_SPEAKER_PRESET] = presetId
                AudioOutputType.WIRED_HEADSET -> prefs[Keys.EQ_WIRED_PRESET] = presetId
                AudioOutputType.BLUETOOTH -> prefs[Keys.EQ_BLUETOOTH_PRESET] = presetId
            }
        }
    }

    private fun parseAppearance(prefs: Preferences) = AppearanceSettings(
        colorMode = prefs[Keys.COLOR_MODE]?.let { runCatching { ColorThemeMode.valueOf(it) }.getOrNull() }
            ?: ColorThemeMode.SYSTEM_DYNAMIC,
        brightnessMode = prefs[Keys.BRIGHTNESS_MODE]?.let { runCatching { BrightnessMode.valueOf(it) }.getOrNull() }
            ?: BrightnessMode.SYSTEM,
        seedColorArgb = prefs[Keys.SEED_COLOR_ARGB] ?: 0xFF7C4DFF,
        nowPlayingStyle = prefs[Keys.NOW_PLAYING_STYLE]?.let { runCatching { NowPlayingStyle.valueOf(it) }.getOrNull() }
            ?: NowPlayingStyle.EXPRESSIVE_CLASSIC,
        libraryLayout = prefs[Keys.LIBRARY_LAYOUT]?.let { runCatching { LibraryViewLayout.valueOf(it) }.getOrNull() }
            ?: LibraryViewLayout.LIST_COMFORTABLE,
        artworkShape = prefs[Keys.ARTWORK_SHAPE]?.let { runCatching { ArtworkShapeStyle.valueOf(it) }.getOrNull() }
            ?: ArtworkShapeStyle.EXPRESSIVE_ROUNDED,
        beatSyncedMorphEnabled = prefs[Keys.BEAT_SYNCED_MORPH] ?: true,
        proceduralArtFallback = prefs[Keys.PROCEDURAL_ART_FALLBACK] ?: true,
        floatingBubbleEnabled = prefs[Keys.FLOATING_BUBBLE_ENABLED] ?: false,
        subBassHapticsEnabled = prefs[Keys.SUB_BASS_HAPTICS_ENABLED] ?: false,
        gyroParallaxEnabled = prefs[Keys.GYRO_PARALLAX] ?: true,
        shakeToSkipEnabled = prefs[Keys.SHAKE_TO_SKIP] ?: false,
        partyStrobeEnabled = prefs[Keys.PARTY_STROBE] ?: false,
        partyStrobeTorchEnabled = prefs[Keys.PARTY_STROBE_TORCH] ?: false,
        customVibeChipsCsv = prefs[Keys.CUSTOM_VIBE_CHIPS] ?: "Gym,Coche,Focus,Chill"
    )

    private fun parsePlayback(prefs: Preferences) = PlaybackSettings(
        gaplessEnabled = prefs[Keys.GAPLESS_ENABLED] ?: true,
        crossfadeSeconds = prefs[Keys.CROSSFADE_SECONDS] ?: 0,
        beatMatchedCrossfade = prefs[Keys.BEAT_MATCHED_CROSSFADE] ?: false,
        playbackSpeed = prefs[Keys.PLAYBACK_SPEED] ?: 1.0f,
        playbackPitch = prefs[Keys.PLAYBACK_PITCH] ?: 1.0f,
        channelBalance = prefs[Keys.CHANNEL_BALANCE] ?: 0.0f,
        deadAirTrimmerEnabled = prefs[Keys.DEAD_AIR_TRIMMER] ?: false,
        nightModeDrcEnabled = prefs[Keys.NIGHT_MODE_DRC] ?: false,
        replayGainEnabled = prefs[Keys.REPLAY_GAIN] ?: false,
        midSideStemBalance = prefs[Keys.MID_SIDE_BALANCE] ?: 0.0f,
        venueReverbPreset = prefs[Keys.VENUE_REVERB_PRESET]?.let { runCatching { VenueReverbPreset.valueOf(it) }.getOrNull() }
            ?: VenueReverbPreset.OFF,
        focusFilterBand = prefs[Keys.FOCUS_FILTER_BAND]?.let { runCatching { FocusFilterBand.valueOf(it) }.getOrNull() }
            ?: FocusFilterBand.NONE,
        lofiTapeEnabled = prefs[Keys.LOFI_TAPE_ENABLED] ?: false,
        slowedReverbEnabled = prefs[Keys.SLOWED_REVERB_ENABLED] ?: false,
        adaptiveAmbientEnabled = prefs[Keys.ADAPTIVE_AMBIENT_ENABLED] ?: false,
        audio8dEnabled = prefs[Keys.AUDIO_8D_ENABLED] ?: false,
        audio8dSpeed = prefs[Keys.AUDIO_8D_SPEED] ?: 1.0f,
        harmonicExciterLevel = prefs[Keys.HARMONIC_EXCITER] ?: 0f,
        semitoneShift = prefs[Keys.SEMITONE_SHIFT] ?: 0,
        earSafeLimiterEnabled = prefs[Keys.EAR_SAFE_LIMITER] ?: true,
        drClippingGuardEnabled = prefs[Keys.DR_CLIPPING_GUARD] ?: false,
        turntableBrakeEnabled = prefs[Keys.TURNTABLE_BRAKE] ?: false
    )

    private fun defaultExcludedFolders(): Set<String> = setOf(
        "/storage/emulated/0/WhatsApp/Media/WhatsApp Voice Notes",
        "/storage/emulated/0/Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes",
        "/storage/emulated/0/Ringtones",
        "/storage/emulated/0/Notifications",
        "/storage/emulated/0/Alarms"
    )
}
