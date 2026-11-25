package com.example.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.example.domain.model.Song

@Entity(
    tableName = "songs",
    indices = [
        Index(value = ["bpm", "energyScore"]),
        Index(value = ["playCount"]),
        Index(value = ["isHidden"]),
        Index(value = ["camelotKey"])
    ]
)
data class SongEntity(
    @PrimaryKey val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val duration: Long,
    val dataPath: String,
    val folderPath: String = "",
    val dateAdded: Long,
    val dateModified: Long = 0L,
    val trackNumber: Int = 0,
    val year: Int = 0,
    val genre: String = "Unknown",
    val mimeType: String = "",
    val isFavorite: Boolean = false,
    val playCount: Int = 0,
    val lastPlayed: Long = 0L,
    val lyrics: String? = null,
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
    fun toDomain(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        album = album,
        albumId = albumId,
        durationMs = duration,
        dataPath = dataPath,
        folderPath = folderPath,
        dateAdded = dateAdded,
        trackNumber = trackNumber,
        year = year,
        genre = genre,
        mimeType = mimeType,
        isFavorite = isFavorite,
        playCount = playCount,
        lastPlayed = lastPlayed,
        embeddedLyrics = lyrics,
        customArtUri = customArtUri,
        silenceStartMs = silenceStartMs,
        silenceEndMs = silenceEndMs,
        lufsDb = lufsDb,
        replayGainDb = replayGainDb,
        bpm = bpm,
        energyScore = energyScore,
        fileSizeBytes = fileSizeBytes,
        bitrateKbps = bitrateKbps,
        sampleRateHz = sampleRateHz,
        camelotKey = camelotKey.ifBlank { "8A" },
        isHidden = isHidden,
        artistAlias = artistAlias
    )
}
