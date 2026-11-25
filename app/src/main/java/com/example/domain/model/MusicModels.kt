package com.example.domain.model

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val dataPath: String,
    val folderPath: String,
    val dateAdded: Long,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "Unknown",
    val mimeType: String = "",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
    val embeddedLyrics: String? = null,
    val customArtUri: String? = null,
    val silenceStartMs: Long = 0L,
    val silenceEndMs: Long = 0L,
    val lufsDb: Float? = null,
    val replayGainDb: Float = 0f,
    val bpm: Int = 110,
    val energyScore: Float = 0.5f,
    val fileSizeBytes: Long = 0L,
    val bitrateKbps: Int = 0,
    val sampleRateHz: Int = 44100,
    val camelotKey: String = "8A",
    val isHidden: Boolean = false,
    val artistAlias: String? = null
) {
    val contentUri: Uri
        get() = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

    val artworkUri: Uri
        get() = if (!customArtUri.isNullOrBlank()) {
            Uri.parse(customArtUri)
        } else {
            ContentUris.withAppendedId(
                Uri.parse("content://media/external/audio/albumart"),
                albumId
            )
        }
}

@Immutable
data class Album(
    val id: Long,
    val name: String,
    val artist: String,
    val year: Int,
    val songCount: Int,
    val artworkUri: Uri,
    val songs: ImmutableList<Song> = persistentListOf()
)

@Immutable
data class Artist(
    val name: String,
    val albumCount: Int,
    val songCount: Int,
    val artworkUri: Uri?,
    val songs: ImmutableList<Song> = persistentListOf()
)

@Immutable
data class Playlist(
    val id: Long,
    val name: String,
    val dateCreated: Long,
    val songCount: Int = 0,
    val previewArtUri: Uri? = null,
    val isSmart: Boolean = false,
    val smartType: SmartPlaylistType? = null
)

enum class SmartPlaylistType(val displayName: String) {
    MOST_PLAYED("Más escuchadas"),
    RECENTLY_ADDED("Añadidas recientemente"),
    FAVORITES("Favoritas"),
    NEVER_PLAYED("Nunca reproducidas")
}

@Immutable
data class LyricLine(
    val timestampMs: Long,
    val text: String
)

@Immutable
data class TrackNote(
    val id: Long,
    val songId: Long,
    val timestampMs: Long,
    val noteText: String,
    val createdAt: Long
)

@Immutable
data class ThrowbackEntry(
    val song: Song,
    val playedAtMillis: Long,
    val year: Int,
    val timeSlotLabel: String
)

@Immutable
data class AuditFinding(
    val song: Song,
    val issueType: AuditIssueType,
    val details: String
)

enum class AuditIssueType(val label: String) {
    FAKE_FLAC("Posible Fake-FLAC / Re-encode"),
    DUPLICATE("Duplicado detectado"),
    LOW_BITRATE("Bitrate bajo")
}

@Immutable
data class EqualizerPreset(
    val id: String,
    val name: String,
    val bandsDb: ImmutableList<Float>,
    val preampDb: Float = 0f,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    val isCustom: Boolean = false
)

enum class AudioOutputType(val label: String) {
    SPEAKER("Altavoz"),
    WIRED_HEADSET("Auriculares"),
    BLUETOOTH("Bluetooth")
}

enum class VenueReverbPreset(
    val label: String,
    val decayMs: Int,
    val roomLevelMb: Short,
    val reverbLevelMb: Short,
    val density: Short,
    val diffusion: Short
) {
    OFF("Apagado", 100, -9000, -9000, 0, 0),
    INTIMATE_ROOM("Sala Íntima", 650, -800, -600, 750, 750),
    NIGHT_CLUB("Club Nocturno", 1100, -500, -250, 900, 850),
    CONCERT_HALL("Sala de Conciertos", 2100, -600, -350, 950, 950),
    CATHEDRAL("Catedral", 3800, -700, -150, 1000, 1000),
    STADIUM("Estadio", 2900, -900, -400, 800, 900)
}

enum class FocusFilterBand(val label: String) {
    NONE("Full"),
    SUB("Sub"),
    KICK("Kick"),
    VOCAL("Vocal"),
    AIR("Air")
}

@Immutable
data class AudioPipelineTelemetry(
    val codec: String = "--",
    val sampleRateHz: Int = 44100,
    val bitrateKbps: Int = 0,
    val channelCount: Int = 2,
    val bitDepthLabel: String = "16-bit",
    val isOffloadActive: Boolean = false
)

@Immutable
data class AbLoopState(
    val active: Boolean = false,
    val pointAMs: Long? = null,
    val pointBMs: Long? = null
)

@Immutable
data class ArtistStatItem(
    val artistName: String,
    val playCount: Int,
    val totalMinutes: Long
)

@Immutable
data class WrappedStats(
    val totalMinutes: Long = 0L,
    val totalPlays: Int = 0,
    val streakDays: Int = 0,
    val topArtists: ImmutableList<ArtistStatItem> = persistentListOf(),
    val topSongs: ImmutableList<Song> = persistentListOf()
)

enum class ContextTimeSlot(val label: String, val subtitle: String, val minBpm: Int, val maxBpm: Int, val minEnergy: Float, val maxEnergy: Float) {
    MORNING_FOCUS("Enfoque Matinal", "Ritmo constante para empezar", 95, 130, 0.35f, 0.72f),
    AFTERNOON_FLOW("Flow de Tarde", "Energía equilibrada y dinámica", 105, 145, 0.50f, 0.88f),
    EVENING_CHILL("Atardecer Chill", "Texturas suaves para desconectar", 65, 112, 0.12f, 0.52f),
    LATE_NIGHT("Sesión Nocturna", "Atmósfera profunda y envolvente", 60, 105, 0.05f, 0.45f)
}

enum class PartySyncMode(val label: String) {
    OFF("Apagado"),
    HOST("Anfitrión (UDP)"),
    GUEST("Invitado (Sincro ±1%)")
}

@Immutable
data class PartySyncState(
    val mode: PartySyncMode = PartySyncMode.OFF,
    val statusLabel: String = "Sin conexión activa",
    val speedCorrection: Float = 1.0f,
    val lastDriftMs: Long = 0L
)

@Immutable
data class WakeUpAlarmState(
    val enabled: Boolean = false,
    val hour: Int = 7,
    val minute: Int = 30,
    val pomodoroActive: Boolean = false,
    val pomodoroRemainingMs: Long = 25 * 60_000L,
    val pomodoroIsBreak: Boolean = false
)

@Immutable
data class ArtistAliasGroup(
    val canonicalName: String,
    val variants: ImmutableList<String>,
    val songCount: Int
)
