package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.ArtistStatItem
import com.example.domain.model.EqualizerPreset
import com.example.domain.model.TrackNote
import kotlinx.collections.immutable.toImmutableList

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val dateCreated: Long = System.currentTimeMillis()
)

@Entity(tableName = "playlist_song_cross_ref", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongCrossRef(
    val playlistId: Long,
    val songId: Long,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "eq_presets")
data class EqualizerPresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val bandsCsv: String,
    val preampDb: Float = 0f,
    val bassBoost: Int = 0,
    val virtualizer: Int = 0
) {
    fun toDomain(): EqualizerPreset = EqualizerPreset(
        id = id,
        name = name,
        bandsDb = bandsCsv.split(",").mapNotNull { it.toFloatOrNull() }.toImmutableList(),
        preampDb = preampDb,
        bassBoost = bassBoost,
        virtualizer = virtualizer,
        isCustom = true
    )

    companion object {
        fun fromDomain(preset: EqualizerPreset): EqualizerPresetEntity = EqualizerPresetEntity(
            id = preset.id,
            name = preset.name,
            bandsCsv = preset.bandsDb.joinToString(","),
            preampDb = preset.preampDb,
            bassBoost = preset.bassBoost,
            virtualizer = preset.virtualizer
        )
    }
}

@Entity(
    tableName = "track_notes",
    indices = [Index(value = ["songId", "timestampMs"])]
)
data class TrackNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val songId: Long,
    val timestampMs: Long,
    val noteText: String,
    val createdAt: Long = System.currentTimeMillis()
) {
    fun toDomain(): TrackNote = TrackNote(
        id = id,
        songId = songId,
        timestampMs = timestampMs,
        noteText = noteText,
        createdAt = createdAt
    )
}

@Entity(
    tableName = "listening_history",
    indices = [Index(value = ["month", "dayOfMonth", "hourOfDay"])]
)
data class ListeningHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val songId: Long,
    val playedAtMillis: Long,
    val hourOfDay: Int,
    val dayOfMonth: Int,
    val month: Int,
    val year: Int
)

/**
 * #33 Live Local Wrapped & Streaks precalculated summary tables.
 */
@Entity(tableName = "wrapped_summary")
data class WrappedSummaryEntity(
    @PrimaryKey val id: Int = 1,
    val totalPlays: Int = 0,
    val totalListeningMs: Long = 0L,
    val streakDays: Int = 0,
    val lastActiveEpochDay: Long = 0L
)

@Entity(tableName = "artist_stats")
data class ArtistStatsEntity(
    @PrimaryKey val artistName: String,
    val playCount: Int = 0,
    val totalMs: Long = 0L
) {
    fun toDomain(): ArtistStatItem = ArtistStatItem(
        artistName = artistName,
        playCount = playCount,
        totalMinutes = (totalMs / 60_000L).coerceAtLeast(1L)
    )
}
