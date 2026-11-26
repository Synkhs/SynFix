package com.example.data.tags

import com.example.domain.model.Song
import com.example.domain.model.TrackVersionGroup
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList

data class JunkCleanupCandidate(
    val song: Song,
    val cleanedTitle: String,
    val cleanedArtist: String,
    val cleanedAlbum: String
)

/**
 * #76 Junk Tag & URL Stripper + #72 Track Version Stack:
 * Precompiled regex engine for removing download watermarks/URLs/broken separators,
 * and grouping alternate versions (Acoustic, Remix, Live, Slowed) cleanly.
 */
object JunkTagCleaner {

    private val URL_REGEX = Regex("""(?i)\b(?:https?://|www\.)\S+|\b[a-z0-9-]+\.(?:com|net|org|mp3|info|me|fm|io)\b""")
    private val BRACKET_JUNK_REGEX = Regex(
        """(?i)\s*[\[(](?:official\s*(?:music\s*)?(?:video|audio|lyric\s*video|visualizer)?|hd|hq|4k|lyrics?|sub\s*español|descargar?|free\s*download|mp3|flac|320\s*kbps|rip|www\.[^\])]+)[\])]"""
    )
    private val TRAILING_WATERMARK_REGEX = Regex("""(?i)\s*[-_|•]\s*(?:official\s*(?:video|audio)|lyrics?|hq|hd|320kbps)\s*$""")
    private val MULTI_SPACE_REGEX = Regex("""\s{2,}""")
    private val BROKEN_DASH_REGEX = Regex("""^[\s\-_|]+|[\s\-_|]+$""")

    // #72 Version extraction (e.g., "(Acoustic)", "(Live)", "(Slowed + Reverb)", "[Remix]")
    private val VERSION_SUFFIX_REGEX = Regex(
        """(?i)\s*[\[(]([^)\]]*(?:acoustic|acústic[ao]|remix|live|en vivo|slowed|reverb|sped up|nightcore|instrumental|karaoke|radio edit|extended|vip|demo|remaster(?:ed)?|unplugged|deluxe|edit|version|versión)[^)\]]*)[\])]"""
    )
    private val VERSION_DASH_REGEX = Regex(
        """(?i)\s+-\s+(acoustic|remix|live|slowed(?:\s*\+\s*reverb)?|instrumental|radio edit|remastered.*)$"""
    )

    fun cleanText(raw: String): String {
        if (raw.isBlank()) return raw
        var s = URL_REGEX.replace(raw, "")
        s = BRACKET_JUNK_REGEX.replace(s, "")
        s = TRAILING_WATERMARK_REGEX.replace(s, "")
        s = MULTI_SPACE_REGEX.replace(s, " ")
        s = BROKEN_DASH_REGEX.replace(s, "")
        return s.trim().ifBlank { raw.trim() }
    }

    fun findCleanupCandidates(songs: List<Song>): ImmutableList<JunkCleanupCandidate> {
        return songs.mapNotNull { song ->
            val t = cleanText(song.title)
            val a = cleanText(song.artist)
            val al = cleanText(song.album)
            if (t != song.title || a != song.artist || al != song.album) {
                JunkCleanupCandidate(
                    song = song,
                    cleanedTitle = t,
                    cleanedArtist = a,
                    cleanedAlbum = al
                )
            } else {
                null
            }
        }.toImmutableList()
    }

    fun extractCanonicalBaseTitle(title: String): String {
        var base = VERSION_SUFFIX_REGEX.replace(title, "")
        base = VERSION_DASH_REGEX.replace(base, "")
        base = MULTI_SPACE_REGEX.replace(base, " ").trim()
        return base.ifBlank { title.trim() }
    }

    fun groupByVersionStack(songs: List<Song>): ImmutableList<TrackVersionGroup> {
        val grouped = songs.groupBy { song ->
            val baseTitle = extractCanonicalBaseTitle(song.title).lowercase()
            val normArtist = song.artist.lowercase()
                .replace(Regex("""\s*(feat\.|ft\.|featuring|&|,).*$"""), "")
                .trim()
            "$normArtist::$baseTitle"
        }

        return grouped.values.map { groupSongs ->
            val sorted = groupSongs.sortedWith(
                compareBy<Song> { extractCanonicalBaseTitle(it.title).length != it.title.trim().length }
                    .thenBy { it.title.length }
            )
            val primary = sorted.first()
            TrackVersionGroup(
                baseTitle = extractCanonicalBaseTitle(primary.title),
                primarySong = primary,
                versions = sorted.toImmutableList()
            )
        }.toImmutableList()
    }

    fun extractCollaborators(artistField: String): List<String> {
        if (artistField.isBlank()) return emptyList()
        return artistField
            .split(Regex("""(?i)\s*(?:,|&|\bx\b|\bfeat\.?\b|\bft\.?\b|\bfeaturing\b|\bwith\b|\bcon\b)\s*"""))
            .map { it.trim() }
            .filter { it.length >= 2 && !it.equals("unknown", ignoreCase = true) && !it.equals("artista desconocido", ignoreCase = true) }
            .distinctBy { it.lowercase() }
    }
}
