package com.example.player

import android.content.Context
import android.net.Uri
import com.example.domain.model.Song
import kotlin.math.abs

data class M3u8Entry(
    val title: String,
    val artist: String?,
    val durationMs: Long,
    val rawPath: String
)

data class M3u8RelinkResult(
    val playlistName: String,
    val totalEntries: Int,
    val exactMatches: Int,
    val fingerprintRelinked: Int,
    val unmatchedCount: Int,
    val resolvedSongs: List<Song>
)

/**
 * #41 Smart .m3u8 Playlist Relinker:
 * Imports & exports UTF-8 .m3u8 playlists via SAF Uri without broad storage permissions,
 * and relinks moved/renamed tracks using fast fingerprints (fileSize + duration + normalized title).
 */
object M3u8PlaylistManager {

    fun parseM3u8Content(rawText: String): List<M3u8Entry> {
        val entries = mutableListOf<M3u8Entry>()
        var pendingDurationMs = -1L
        var pendingTitle: String? = null
        var pendingArtist: String? = null

        rawText.lineSequence().forEach { rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEach

            if (line.startsWith("#EXTM3U", ignoreCase = true)) {
                return@forEach
            }
            if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                val payload = line.removePrefix("#EXTINF:")
                val commaIdx = payload.indexOf(',')
                val secPart = if (commaIdx >= 0) payload.substring(0, commaIdx).trim() else payload.trim()
                val sec = secPart.toLongOrNull() ?: -1L
                pendingDurationMs = if (sec > 0) sec * 1000L else -1L
                val info = if (commaIdx >= 0) payload.substring(commaIdx + 1).trim() else ""
                if (info.contains(" - ")) {
                    pendingArtist = info.substringBefore(" - ").trim()
                    pendingTitle = info.substringAfter(" - ").trim()
                } else if (info.isNotBlank()) {
                    pendingTitle = info
                }
                return@forEach
            }
            if (line.startsWith("#")) return@forEach

            val fileName = line.substringAfterLast('/').substringAfterLast('\\')
            val fallbackTitle = fileName.substringBeforeLast('.').ifBlank { fileName }
            entries.add(
                M3u8Entry(
                    title = pendingTitle ?: fallbackTitle,
                    artist = pendingArtist,
                    durationMs = pendingDurationMs,
                    rawPath = line
                )
            )
            pendingDurationMs = -1L
            pendingTitle = null
            pendingArtist = null
        }

        return entries
    }

    fun relinkEntries(
        playlistName: String,
        entries: List<M3u8Entry>,
        librarySongs: List<Song>
    ): M3u8RelinkResult {
        val byPath = librarySongs.associateBy { it.dataPath }
        val byFileName = librarySongs.associateBy {
            it.dataPath.substringAfterLast('/').lowercase()
        }
        val byNormTitle = librarySongs.groupBy { normalizeTitle(it.title) }

        var exact = 0
        var relinked = 0
        var unmatched = 0
        val resolved = mutableListOf<Song>()
        val seenIds = HashSet<Long>()

        for (entry in entries) {
            val direct = byPath[entry.rawPath]
            if (direct != null) {
                exact++
                if (seenIds.add(direct.id)) resolved.add(direct)
                continue
            }

            // Fingerprint relink: filename OR (normalized title + duration proximity)
            val entryFileName = entry.rawPath.substringAfterLast('/').substringAfterLast('\\').lowercase()
            val fileCandidate = byFileName[entryFileName]
            if (fileCandidate != null && (entry.durationMs <= 0 || abs(fileCandidate.durationMs - entry.durationMs) <= 2500L)) {
                relinked++
                if (seenIds.add(fileCandidate.id)) resolved.add(fileCandidate)
                continue
            }

            val normTitle = normalizeTitle(entry.title)
            val titleCandidates = byNormTitle[normTitle].orEmpty()
            val bestCandidate = when {
                titleCandidates.isEmpty() -> null
                entry.durationMs > 0L -> titleCandidates.minByOrNull { abs(it.durationMs - entry.durationMs) }
                    ?.takeIf { abs(it.durationMs - entry.durationMs) <= 3000L }
                !entry.artist.isNullOrBlank() -> titleCandidates.firstOrNull {
                    it.artist.equals(entry.artist, ignoreCase = true)
                } ?: titleCandidates.firstOrNull()
                else -> titleCandidates.firstOrNull()
            }

            if (bestCandidate != null) {
                relinked++
                if (seenIds.add(bestCandidate.id)) resolved.add(bestCandidate)
            } else {
                unmatched++
            }
        }

        return M3u8RelinkResult(
            playlistName = playlistName,
            totalEntries = entries.size,
            exactMatches = exact,
            fingerprintRelinked = relinked,
            unmatchedCount = unmatched,
            resolvedSongs = resolved
        )
    }

    fun buildM3u8Text(songs: List<Song>): String = buildString {
        appendLine("#EXTM3U")
        songs.forEach { song ->
            val sec = (song.durationMs / 1000L).coerceAtLeast(0L)
            appendLine("#EXTINF:$sec,${song.artist} - ${song.title}")
            appendLine(song.dataPath)
        }
    }

    fun readTextFromUri(context: Context, uri: Uri): String? = runCatching {
        context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
    }.getOrNull()

    fun writeTextToUri(context: Context, uri: Uri, content: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use {
            it.write(content)
        }
        true
    }.getOrDefault(false)

    private fun normalizeTitle(raw: String): String =
        raw.lowercase()
            .replace(Regex("\\([^)]*\\)|\\[[^]]*]"), "")
            .replace(Regex("[^a-z0-9\\p{L}]+"), "")
            .trim()
}
