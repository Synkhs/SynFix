package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsOnce(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE isHidden = 1 ORDER BY title COLLATE NOCASE ASC")
    fun getHiddenSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE id = :songId LIMIT 1")
    suspend fun getSongById(songId: Long): SongEntity?

    @Query("SELECT * FROM songs WHERE isFavorite = 1 AND isHidden = 0 ORDER BY title COLLATE NOCASE ASC")
    fun getFavoriteSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount > 0 AND isHidden = 0 ORDER BY playCount DESC, lastPlayed DESC LIMIT 50")
    fun getMostPlayedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE isHidden = 0 ORDER BY dateAdded DESC LIMIT 50")
    fun getRecentSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE playCount = 0 AND isHidden = 0 ORDER BY dateAdded DESC")
    fun getNeverPlayedSongs(): Flow<List<SongEntity>>

    // #13 Zero-Play Streak: lightweight random selection of 3 unplayed tracks
    @Query("SELECT * FROM songs WHERE playCount = 0 AND isHidden = 0 ORDER BY RANDOM() LIMIT 3")
    suspend fun getRandomZeroPlayStreak(): List<SongEntity>

    // #11 Mood & Tempo Radar 2D & #37 Context-Aware Time Mixes
    @Query(
        "SELECT * FROM songs " +
            "WHERE isHidden = 0 AND bpm BETWEEN :minBpm AND :maxBpm " +
            "AND energyScore BETWEEN :minEnergy AND :maxEnergy " +
            "ORDER BY playCount DESC, title COLLATE NOCASE ASC"
    )
    suspend fun getSongsByMoodAndTempo(
        minBpm: Int,
        maxBpm: Int,
        minEnergy: Float,
        maxEnergy: Float
    ): List<SongEntity>

    // #30 Harmonic Key & Camelot Wheel indexed lookup
    @Query("SELECT * FROM songs WHERE isHidden = 0 AND camelotKey IN (:keys) ORDER BY bpm ASC, title COLLATE NOCASE ASC")
    suspend fun getSongsByCamelotKeys(keys: List<String>): List<SongEntity>

    @Query("UPDATE songs SET camelotKey = :camelotKey WHERE id = :songId")
    suspend fun updateCamelotKey(songId: Long, camelotKey: String)

    // #40 Biometric Hidden Vault
    @Query("UPDATE songs SET isHidden = :isHidden WHERE id = :songId")
    suspend fun setSongHidden(songId: Long, isHidden: Boolean)

    @Query("UPDATE songs SET isHidden = :isHidden WHERE folderPath = :folderPath")
    suspend fun setFolderHidden(folderPath: String, isHidden: Boolean)

    // #39 Virtual Artist Alias & Batch Cleaner in a single atomic Transaction
    @Query("UPDATE songs SET artist = :canonicalName, artistAlias = :canonicalName WHERE artist IN (:oldNames)")
    suspend fun updateArtistBatch(oldNames: List<String>, canonicalName: String)

    @Query("UPDATE songs SET title = TRIM(title), album = TRIM(album), artist = TRIM(artist)")
    suspend fun trimAllMetadataWhitespace()

    @Transaction
    suspend fun mergeArtistAliasAndCleanTags(oldNames: List<String>, canonicalName: String) {
        if (oldNames.isNotEmpty()) {
            updateArtistBatch(oldNames, canonicalName.trim())
        }
        trimAllMetadataWhitespace()
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<SongEntity>)

    @Query("DELETE FROM songs WHERE id IN (:ids)")
    suspend fun deleteSongsByIds(ids: List<Long>)

    @Update
    suspend fun updateSong(song: SongEntity)

    @Query("UPDATE songs SET isFavorite = :isFavorite WHERE id = :songId")
    suspend fun updateFavorite(songId: Long, isFavorite: Boolean)

    @Query("UPDATE songs SET playCount = playCount + 1, lastPlayed = :timestamp WHERE id = :songId")
    suspend fun incrementPlayCount(songId: Long, timestamp: Long = System.currentTimeMillis())

    @Query("UPDATE songs SET lyrics = :lyrics WHERE id = :songId")
    suspend fun updateLyrics(songId: Long, lyrics: String?)

    // #6 Smart Dead-Air Trimmer offset caching
    @Query("UPDATE songs SET silenceStartMs = :startMs, silenceEndMs = :endMs WHERE id = :songId")
    suspend fun updateSilenceOffsets(songId: Long, startMs: Long, endMs: Long)

    // #8 True LUFS ReplayGain caching
    @Query("UPDATE songs SET lufsDb = :lufsDb, replayGainDb = :replayGainDb WHERE id = :songId")
    suspend fun updateReplayGain(songId: Long, lufsDb: Float, replayGainDb: Float)

    // #17 Audio audit metadata cache
    @Query("UPDATE songs SET bitrateKbps = :bitrateKbps, sampleRateHz = :sampleRateHz, fileSizeBytes = :fileSizeBytes WHERE id = :songId")
    suspend fun updateAudioTechnicalInfo(songId: Long, bitrateKbps: Int, sampleRateHz: Int, fileSizeBytes: Long)

    // Playlists
    @Query("SELECT * FROM playlists ORDER BY dateCreated DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: Long, name: String)

    @Delete
    suspend fun deletePlaylist(playlist: PlaylistEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(crossRef: PlaylistSongCrossRef)

    @Delete
    suspend fun deletePlaylistSong(crossRef: PlaylistSongCrossRef)

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query(
        "SELECT songs.* FROM songs " +
            "INNER JOIN playlist_song_cross_ref ON songs.id = playlist_song_cross_ref.songId " +
            "WHERE playlist_song_cross_ref.playlistId = :playlistId " +
            "ORDER BY playlist_song_cross_ref.addedAt ASC"
    )
    fun getSongsForPlaylist(playlistId: Long): Flow<List<SongEntity>>

    @Query("SELECT * FROM playlist_song_cross_ref")
    fun getAllPlaylistCrossRefs(): Flow<List<PlaylistSongCrossRef>>

    // EQ Presets
    @Query("SELECT * FROM eq_presets ORDER BY name COLLATE NOCASE ASC")
    fun getCustomEqPresets(): Flow<List<EqualizerPresetEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEqPreset(preset: EqualizerPresetEntity)

    @Query("DELETE FROM eq_presets WHERE id = :id")
    suspend fun deleteEqPreset(id: String)

    // #19 Timestamped Track Notes
    @Query("SELECT * FROM track_notes WHERE songId = :songId ORDER BY timestampMs ASC")
    fun getTrackNotesForSong(songId: Long): Flow<List<TrackNoteEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackNote(note: TrackNoteEntity): Long

    @Query("DELETE FROM track_notes WHERE id = :noteId")
    suspend fun deleteTrackNote(noteId: Long)

    // #12 Throwback Time-Machine
    @Insert
    suspend fun insertListeningHistory(entry: ListeningHistoryEntity)

    @Query(
        "SELECT * FROM listening_history " +
            "WHERE month = :month AND dayOfMonth = :dayOfMonth " +
            "AND hourOfDay BETWEEN :startHour AND :endHour " +
            "ORDER BY playedAtMillis DESC LIMIT 25"
    )
    suspend fun getThrowbackHistory(
        month: Int,
        dayOfMonth: Int,
        startHour: Int,
        endHour: Int
    ): List<ListeningHistoryEntity>

    @Query("SELECT * FROM listening_history ORDER BY playedAtMillis DESC LIMIT 30")
    suspend fun getRecentListeningHistory(): List<ListeningHistoryEntity>

    // #33 Live Local Wrapped & Streaks
    @Query("SELECT * FROM wrapped_summary WHERE id = 1 LIMIT 1")
    fun observeWrappedSummary(): Flow<WrappedSummaryEntity?>

    @Query("SELECT * FROM wrapped_summary WHERE id = 1 LIMIT 1")
    suspend fun getWrappedSummaryOnce(): WrappedSummaryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWrappedSummary(summary: WrappedSummaryEntity)

    @Query("SELECT * FROM artist_stats ORDER BY playCount DESC LIMIT 10")
    fun observeTopArtists(): Flow<List<ArtistStatsEntity>>

    @Query("SELECT * FROM artist_stats WHERE artistName = :artistName LIMIT 1")
    suspend fun getArtistStatsOnce(artistName: String): ArtistStatsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArtistStats(stats: ArtistStatsEntity)
}
