package com.example.data.repository

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import com.example.data.db.*
import com.example.data.worker.CamelotWheelAnalyzer
import com.example.domain.model.*
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Calendar
import java.util.concurrent.TimeUnit
import kotlin.math.abs

class MusicRepository(
    private val context: Context,
    private val musicDao: MusicDao
) {
    val allSongs: Flow<ImmutableList<Song>> = musicDao.getAllSongs()
        .map { list -> list.map { it.toDomain() }.toImmutableList() }
        .flowOn(Dispatchers.Default)

    val hiddenSongs: Flow<ImmutableList<Song>> = musicDao.getHiddenSongs()
        .map { list -> list.map { it.toDomain() }.toImmutableList() }
        .flowOn(Dispatchers.Default)

    val favoriteSongs: Flow<ImmutableList<Song>> = musicDao.getFavoriteSongs()
        .map { list -> list.map { it.toDomain() }.toImmutableList() }
        .flowOn(Dispatchers.Default)

    val mostPlayedSongs: Flow<ImmutableList<Song>> = musicDao.getMostPlayedSongs()
        .map { list -> list.map { it.toDomain() }.toImmutableList() }
        .flowOn(Dispatchers.Default)

    val recentSongs: Flow<ImmutableList<Song>> = musicDao.getRecentSongs()
        .map { list -> list.map { it.toDomain() }.toImmutableList() }
        .flowOn(Dispatchers.Default)

    val neverPlayedSongs: Flow<ImmutableList<Song>> = musicDao.getNeverPlayedSongs()
        .map { list -> list.map { it.toDomain() }.toImmutableList() }
        .flowOn(Dispatchers.Default)

    val userPlaylists: Flow<ImmutableList<Playlist>> = combine(
        musicDao.getAllPlaylists(),
        musicDao.getAllPlaylistCrossRefs(),
        musicDao.getAllSongs()
    ) { playlists, refs, songs ->
        val songMap = songs.associateBy { it.id }
        playlists.map { entity ->
            val playlistSongIds = refs.filter { it.playlistId == entity.id }.map { it.songId }
            val firstSong = playlistSongIds.firstNotNullOfOrNull { songMap[it]?.toDomain() }
            Playlist(
                id = entity.id,
                name = entity.name,
                dateCreated = entity.dateCreated,
                songCount = playlistSongIds.size,
                previewArtUri = firstSong?.artworkUri,
                isSmart = false
            )
        }.toImmutableList()
    }.flowOn(Dispatchers.Default)

    val customEqPresets: Flow<ImmutableList<EqualizerPreset>> = musicDao.getCustomEqPresets()
        .map { list -> list.map { it.toDomain() }.toImmutableList() }
        .flowOn(Dispatchers.Default)

    // #33 Live Local Wrapped & Streaks backed by precalculated Room summary tables
    val wrappedStats: Flow<WrappedStats> = combine(
        musicDao.observeWrappedSummary(),
        musicDao.observeTopArtists(),
        musicDao.getMostPlayedSongs()
    ) { summary, artists, topSongs ->
        WrappedStats(
            totalMinutes = ((summary?.totalListeningMs ?: 0L) / 60_000L),
            totalPlays = summary?.totalPlays ?: 0,
            streakDays = summary?.streakDays ?: 0,
            topArtists = artists.map { it.toDomain() }.toImmutableList(),
            topSongs = topSongs.take(5).map { it.toDomain() }.toImmutableList()
        )
    }.flowOn(Dispatchers.Default)

    private val _discoveredFolders = MutableStateFlow<List<Pair<String, Int>>>(emptyList())
    val discoveredFolders: StateFlow<List<Pair<String, Int>>> = _discoveredFolders.asStateFlow()

    suspend fun scanMediaStoreIncremental(
        minDurationSeconds: Int,
        excludedFolders: Set<String>
    ): Int = withContext(Dispatchers.IO) {
        val existingMap = musicDao.getAllSongsOnce().associateBy { it.id }
        val scannedEntities = mutableListOf<SongEntity>()
        val folderCounts = mutableMapOf<String, Int>()
        val validIds = HashSet<Long>()

        val hasGenreColumn = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.DATE_MODIFIED)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.SIZE)
            if (hasGenreColumn) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()

        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
                val albumIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
                val durationCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
                val dateModifiedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
                val trackCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
                val yearCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.MIME_TYPE)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.SIZE)
                val genreCol = if (hasGenreColumn) cursor.getColumnIndex(MediaStore.Audio.Media.GENRE) else -1

                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataCol) ?: continue
                    val file = File(path)
                    if (!file.exists()) continue

                    val parentFolder = file.parentFile?.absolutePath ?: ""
                    folderCounts[parentFolder] = (folderCounts[parentFolder] ?: 0) + 1

                    val duration = cursor.getLong(durationCol)
                    if (duration < minDurationSeconds * 1000L) continue
                    if (excludedFolders.any { excluded -> parentFolder.startsWith(excluded, ignoreCase = true) }) continue

                    val id = cursor.getLong(idCol)
                    validIds.add(id)
                    val dateModified = cursor.getLong(dateModifiedCol)
                    val existing = existingMap[id]

                    if (existing != null && existing.dateModified == dateModified && existing.folderPath == parentFolder) {
                        continue
                    }

                    val title = cursor.getString(titleCol)?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension
                    val artist = cursor.getString(artistCol)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Artista desconocido"
                    val album = cursor.getString(albumCol)?.takeIf { it != "<unknown>" && it.isNotBlank() } ?: "Álbum desconocido"
                    val albumId = cursor.getLong(albumIdCol)
                    val dateAdded = cursor.getLong(dateAddedCol)
                    val track = cursor.getInt(trackCol) % 1000
                    val year = cursor.getInt(yearCol)
                    val mime = cursor.getString(mimeCol) ?: ""
                    val fileSize = cursor.getLong(sizeCol).takeIf { it > 0 } ?: file.length()
                    val genre = if (genreCol >= 0) cursor.getString(genreCol)?.takeIf { it.isNotBlank() } ?: "Música" else "Música"
                    val estBitrate = if (duration > 0) ((fileSize * 8L) / duration).toInt() else 0

                    val (estBpm, estEnergy) = estimateMoodAndTempo(genre, title, duration)
                    val estCamelot = CamelotWheelAnalyzer.estimateFromMetadata(genre, title)

                    scannedEntities.add(
                        SongEntity(
                            id = id,
                            title = existing?.title?.takeIf { existing.dateModified > dateModified } ?: title,
                            artist = existing?.artist?.takeIf { existing.dateModified > dateModified } ?: artist,
                            album = existing?.album?.takeIf { existing.dateModified > dateModified } ?: album,
                            albumId = albumId,
                            duration = duration,
                            dataPath = path,
                            folderPath = parentFolder,
                            dateAdded = dateAdded,
                            dateModified = dateModified,
                            trackNumber = track,
                            year = year,
                            genre = existing?.genre ?: genre,
                            mimeType = mime,
                            isFavorite = existing?.isFavorite ?: false,
                            playCount = existing?.playCount ?: 0,
                            lastPlayed = existing?.lastPlayed ?: 0L,
                            lyrics = existing?.lyrics,
                            customArtUri = existing?.customArtUri,
                            silenceStartMs = existing?.silenceStartMs ?: 0L,
                            silenceEndMs = existing?.silenceEndMs ?: 0L,
                            lufsDb = existing?.lufsDb,
                            replayGainDb = existing?.replayGainDb ?: 0f,
                            bpm = existing?.bpm ?: estBpm,
                            energyScore = existing?.energyScore ?: estEnergy,
                            fileSizeBytes = fileSize,
                            bitrateKbps = existing?.bitrateKbps?.takeIf { it > 0 } ?: estBitrate,
                            sampleRateHz = existing?.sampleRateHz ?: 44100,
                            camelotKey = existing?.camelotKey?.takeIf { it.isNotBlank() } ?: estCamelot,
                            isHidden = existing?.isHidden ?: false,
                            artistAlias = existing?.artistAlias
                        )
                    )
                }
            }
        }

        _discoveredFolders.value = folderCounts.entries
            .sortedByDescending { it.value }
            .map { it.key to it.value }

        if (scannedEntities.isNotEmpty()) {
            musicDao.insertSongs(scannedEntities)
        }

        val staleIds = existingMap.keys.filter { !validIds.contains(it) }
        if (staleIds.isNotEmpty()) {
            staleIds.chunked(500).forEach { chunk ->
                musicDao.deleteSongsByIds(chunk)
            }
        }

        validIds.size
    }

    private fun estimateMoodAndTempo(genre: String, title: String, durationMs: Long): Pair<Int, Float> {
        val g = genre.lowercase()
        val hash = abs((genre + title).hashCode())
        val baseBpm = when {
            g.contains("trap") || g.contains("hip") || g.contains("rap") -> 85 + (hash % 30)
            g.contains("rock") || g.contains("metal") || g.contains("punk") -> 125 + (hash % 40)
            g.contains("electro") || g.contains("edm") || g.contains("house") || g.contains("techno") -> 124 + (hash % 24)
            g.contains("jazz") || g.contains("acoustic") || g.contains("ambient") || g.contains("lo-fi") -> 70 + (hash % 28)
            else -> 95 + (hash % 45)
        }
        val baseEnergy = when {
            g.contains("metal") || g.contains("edm") || g.contains("techno") || g.contains("rock") -> 0.72f + ((hash % 25) / 100f)
            g.contains("ambient") || g.contains("acoustic") || g.contains("classical") || g.contains("lo-fi") -> 0.18f + ((hash % 25) / 100f)
            durationMs > 360_000L -> 0.35f + ((hash % 30) / 100f)
            else -> 0.42f + ((hash % 40) / 100f)
        }.coerceIn(0.05f, 0.98f)
        return baseBpm to baseEnergy
    }

    suspend fun updateFavorite(songId: Long, isFavorite: Boolean) {
        musicDao.updateFavorite(songId, isFavorite)
    }

    suspend fun recordSongPlayed(songId: Long) = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        musicDao.incrementPlayCount(songId, now)
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        musicDao.insertListeningHistory(
            ListeningHistoryEntity(
                songId = songId,
                playedAtMillis = now,
                hourOfDay = cal.get(Calendar.HOUR_OF_DAY),
                dayOfMonth = cal.get(Calendar.DAY_OF_MONTH),
                month = cal.get(Calendar.MONTH) + 1,
                year = cal.get(Calendar.YEAR)
            )
        )

        // #33 Update precalculated WrappedSummaryEntity + ArtistStatsEntity
        val song = musicDao.getSongById(songId)
        val durationMs = song?.duration?.coerceAtLeast(30_000L) ?: 180_000L
        val artistName = song?.artist?.ifBlank { "Artista desconocido" } ?: "Artista desconocido"

        val todayEpochDay = TimeUnit.MILLISECONDS.toDays(now)
        val prevSummary = musicDao.getWrappedSummaryOnce()
        val newStreak = when {
            prevSummary == null || prevSummary.lastActiveEpochDay == 0L -> 1
            prevSummary.lastActiveEpochDay == todayEpochDay -> prevSummary.streakDays.coerceAtLeast(1)
            prevSummary.lastActiveEpochDay == todayEpochDay - 1L -> prevSummary.streakDays + 1
            else -> 1
        }
        musicDao.upsertWrappedSummary(
            WrappedSummaryEntity(
                id = 1,
                totalPlays = (prevSummary?.totalPlays ?: 0) + 1,
                totalListeningMs = (prevSummary?.totalListeningMs ?: 0L) + durationMs,
                streakDays = newStreak,
                lastActiveEpochDay = todayEpochDay
            )
        )

        val prevArtist = musicDao.getArtistStatsOnce(artistName)
        musicDao.upsertArtistStats(
            ArtistStatsEntity(
                artistName = artistName,
                playCount = (prevArtist?.playCount ?: 0) + 1,
                totalMs = (prevArtist?.totalMs ?: 0L) + durationMs
            )
        )
    }

    suspend fun updateSong(song: SongEntity) = musicDao.updateSong(song)

    suspend fun updateLyrics(songId: Long, lyrics: String?) = musicDao.updateLyrics(songId, lyrics)

    suspend fun updateSilenceOffsets(songId: Long, startMs: Long, endMs: Long) {
        musicDao.updateSilenceOffsets(songId, startMs, endMs)
    }

    suspend fun updateReplayGain(songId: Long, lufsDb: Float, replayGainDb: Float) {
        musicDao.updateReplayGain(songId, lufsDb, replayGainDb)
    }

    suspend fun updateAudioTechnicalInfo(songId: Long, bitrateKbps: Int, sampleRateHz: Int, fileSizeBytes: Long) {
        musicDao.updateAudioTechnicalInfo(songId, bitrateKbps, sampleRateHz, fileSizeBytes)
    }

    suspend fun queryMoodAndTempo(centerBpm: Int, centerEnergy: Float): ImmutableList<Song> = withContext(Dispatchers.IO) {
        val minBpm = (centerBpm - 25).coerceAtLeast(40)
        val maxBpm = (centerBpm + 25).coerceAtMost(220)
        val minEnergy = (centerEnergy - 0.25f).coerceAtLeast(0f)
        val maxEnergy = (centerEnergy + 0.25f).coerceAtMost(1f)
        musicDao.getSongsByMoodAndTempo(minBpm, maxBpm, minEnergy, maxEnergy)
            .map { it.toDomain() }
            .toImmutableList()
    }

    // #30 Harmonic Key & Camelot Wheel lookup
    suspend fun getHarmonicMatches(camelotKey: String): ImmutableList<Song> = withContext(Dispatchers.IO) {
        val compatible = CamelotWheelAnalyzer.getCompatibleKeys(camelotKey)
        musicDao.getSongsByCamelotKeys(compatible)
            .map { it.toDomain() }
            .toImmutableList()
    }

    // #37 Context-Aware Time Mixes (zero GPS, lightweight Room query)
    suspend fun getContextTimeMix(slot: ContextTimeSlot): ImmutableList<Song> = withContext(Dispatchers.IO) {
        val exact = musicDao.getSongsByMoodAndTempo(slot.minBpm, slot.maxBpm, slot.minEnergy, slot.maxEnergy)
        val list = if (exact.size >= 4) exact else musicDao.getSongsByMoodAndTempo(50, 210, 0f, 1f).take(20)
        list.map { it.toDomain() }.toImmutableList()
    }

    // #40 Biometric Hidden Vault
    suspend fun setSongHidden(songId: Long, hidden: Boolean) = withContext(Dispatchers.IO) {
        musicDao.setSongHidden(songId, hidden)
    }

    suspend fun setFolderHidden(folderPath: String, hidden: Boolean) = withContext(Dispatchers.IO) {
        musicDao.setFolderHidden(folderPath, hidden)
    }

    // #39 Virtual Artist Alias & Batch Cleaner
    suspend fun findArtistAliasSuggestions(): ImmutableList<ArtistAliasGroup> = withContext(Dispatchers.IO) {
        val songs = musicDao.getAllSongsOnce()
        val groups = songs.groupBy { normalizeArtistForAlias(it.artist) }
        groups.values
            .mapNotNull { groupSongs ->
                val variants = groupSongs.map { it.artist }.distinct()
                if (variants.size > 1 || variants.any { it != it.trim() || it.contains("  ") }) {
                    val canonical = variants.maxByOrNull { name ->
                        groupSongs.count { it.artist == name } * 100 - name.length
                    }?.trim() ?: return@mapNotNull null
                    ArtistAliasGroup(
                        canonicalName = canonical,
                        variants = variants.toImmutableList(),
                        songCount = groupSongs.size
                    )
                } else null
            }
            .sortedByDescending { it.songCount }
            .toImmutableList()
    }

    suspend fun mergeArtistAliasAndCleanTags(oldNames: List<String>, canonicalName: String) = withContext(Dispatchers.IO) {
        musicDao.mergeArtistAliasAndCleanTags(oldNames, canonicalName)
    }

    private fun normalizeArtistForAlias(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\s*(feat\\.|ft\\.|featuring|&|,)\\s+.*$"), "")
            .replace(Regex("[^a-z0-9\\p{L}]"), "")
            .trim()

    suspend fun getZeroPlayStreak(): ImmutableList<Song> = withContext(Dispatchers.IO) {
        musicDao.getRandomZeroPlayStreak().map { it.toDomain() }.toImmutableList()
    }

    suspend fun getThrowbackForSlot(month: Int, dayOfMonth: Int, slotIndex: Int): ImmutableList<ThrowbackEntry> =
        withContext(Dispatchers.IO) {
            val (startHour, endHour, slotLabel) = when (slotIndex) {
                0 -> Triple(6, 11, "Mañana")
                1 -> Triple(12, 17, "Tarde")
                2 -> Triple(18, 22, "Noche")
                else -> Triple(0, 5, "Madrugada")
            }
            val songMap = musicDao.getAllSongsOnce().associateBy { it.id }
            val exact = musicDao.getThrowbackHistory(month, dayOfMonth, startHour, endHour)
            val entries = if (exact.isNotEmpty()) exact else musicDao.getRecentListeningHistory()
            entries.mapNotNull { h ->
                val song = songMap[h.songId]?.toDomain() ?: return@mapNotNull null
                ThrowbackEntry(
                    song = song,
                    playedAtMillis = h.playedAtMillis,
                    year = h.year,
                    timeSlotLabel = if (exact.isNotEmpty()) slotLabel else "Reciente"
                )
            }.distinctBy { it.song.id }.toImmutableList()
        }

    fun getTrackNotesForSong(songId: Long): Flow<ImmutableList<TrackNote>> =
        musicDao.getTrackNotesForSong(songId)
            .map { list -> list.map { it.toDomain() }.toImmutableList() }
            .flowOn(Dispatchers.Default)

    suspend fun addTrackNote(songId: Long, timestampMs: Long, noteText: String) {
        if (noteText.isBlank()) return
        musicDao.insertTrackNote(
            TrackNoteEntity(
                songId = songId,
                timestampMs = timestampMs,
                noteText = noteText.trim()
            )
        )
    }

    suspend fun deleteTrackNote(noteId: Long) {
        musicDao.deleteTrackNote(noteId)
    }

    suspend fun createPlaylist(name: String): Long = musicDao.insertPlaylist(PlaylistEntity(name = name))
    suspend fun renamePlaylist(playlistId: Long, name: String) = musicDao.renamePlaylist(playlistId, name)
    suspend fun deletePlaylist(playlist: Playlist) = musicDao.deletePlaylist(PlaylistEntity(playlist.id, playlist.name, playlist.dateCreated))
    suspend fun addSongToPlaylist(playlistId: Long, songId: Long) = musicDao.insertPlaylistSong(PlaylistSongCrossRef(playlistId, songId))
    suspend fun removeSongFromPlaylist(playlistId: Long, songId: Long) = musicDao.deletePlaylistSong(PlaylistSongCrossRef(playlistId, songId))
    fun getSongsForPlaylist(playlistId: Long): Flow<ImmutableList<Song>> =
        musicDao.getSongsForPlaylist(playlistId).map { list -> list.map { it.toDomain() }.toImmutableList() }.flowOn(Dispatchers.Default)

    suspend fun saveCustomEqPreset(preset: EqualizerPreset) = musicDao.insertEqPreset(EqualizerPresetEntity.fromDomain(preset))
    suspend fun deleteCustomEqPreset(id: String) = musicDao.deleteEqPreset(id)
}
