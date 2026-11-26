package com.example.domain.model

import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.serialization.Serializable

@Immutable
data class AutoEqProfile(
    val id: String,
    val model: String,
    val category: String,
    val preampDb: Float,
    val bandsDb: ImmutableList<Float>
)

@Immutable
data class AmbienceMixState(
    val enabled: Boolean = false,
    val rainLevel: Float = 0f,
    val fireLevel: Float = 0f,
    val pinkNoiseLevel: Float = 0f,
    val binaural40HzLevel: Float = 0f,
    val binaural4HzLevel: Float = 0f
)

@Immutable
data class TrackVersionGroup(
    val baseTitle: String,
    val primarySong: Song,
    val versions: ImmutableList<Song>
)

@Immutable
data class TrophyItem(
    val id: String,
    val title: String,
    val description: String,
    val unlocked: Boolean,
    val unlockedAt: Long,
    val progress: Int,
    val target: Int
)

@Immutable
data class BluetoothMacProfile(
    val macAddress: String,
    val deviceName: String,
    val eqPresetId: String,
    val initialVolumePercent: Int
)

@Immutable
data class FormatSpaceBreakdown(
    val format: String,
    val trackCount: Int,
    val totalMb: Float
)

@Immutable
data class DspBatteryStats(
    val formatBreakdown: ImmutableList<FormatSpaceBreakdown> = persistentListOf(),
    val offloadSeconds: Long = 0L,
    val dspSeconds: Long = 0L,
    val offloadPercentage: Int = 100,
    val estimatedBatterySavedMah: Float = 0f
)

@Immutable
data class QrServerState(
    val isRunning: Boolean = false,
    val url: String = "",
    val port: Int = 0,
    val uploadedCount: Int = 0
)

@Immutable
data class DrClippingTelemetry(
    val estimatedDrDb: Float = 12f,
    val peakNormalized: Float = 0f,
    val clippingGuardActive: Boolean = false
)

@Serializable
data class SynFixBackupPlaylist(
    val name: String,
    val songPaths: List<String>
)

@Serializable
data class SynFixBackupEqPreset(
    val id: String,
    val name: String,
    val bandsDb: List<Float>,
    val preampDb: Float,
    val bassBoost: Int,
    val virtualizer: Int
)

@Serializable
data class SynFixBackupNote(
    val songPath: String,
    val timestampMs: Long,
    val noteText: String
)

@Serializable
data class SynFixBackupVibeEntry(
    val songPath: String,
    val vibeTags: String
)

@Serializable
data class SynFixBackupBtProfile(
    val macAddress: String,
    val deviceName: String,
    val eqPresetId: String,
    val initialVolumePercent: Int
)

@Serializable
data class SynFixBackupSnapshot(
    val version: Int = 1,
    val exportedAtMillis: Long,
    val customVibeChipsCsv: String,
    val playlists: List<SynFixBackupPlaylist>,
    val eqPresets: List<SynFixBackupEqPreset>,
    val notes: List<SynFixBackupNote>,
    val songVibes: List<SynFixBackupVibeEntry>,
    val btProfiles: List<SynFixBackupBtProfile>,
    val totalPlays: Int = 0,
    val totalListeningMs: Long = 0L,
    val streakDays: Int = 0
)
