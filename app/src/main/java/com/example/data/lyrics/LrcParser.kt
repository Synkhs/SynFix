package com.example.data.lyrics

import com.example.domain.model.LyricLine
import com.example.domain.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.RandomAccessFile
import java.nio.charset.Charset

object LrcParser {

    private val TIME_TAG_REGEX = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET_REGEX = Regex("""\[offset:([+-]?\d+)]""", RegexOption.IGNORE_CASE)

    suspend fun loadLyricsForSong(song: Song): ImmutableList<LyricLine> = withContext(Dispatchers.IO) {
        // 1. Check sibling .lrc file on disk (same filename, .lrc extension)
        val audioFile = File(song.dataPath)
        val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
        if (lrcFile.exists() && lrcFile.canRead()) {
            val text = runCatching { lrcFile.readText(Charsets.UTF_8) }.getOrNull()
            if (!text.isNullOrBlank()) {
                return@withContext parse(text)
            }
        }

        // 2. Check lyrics stored in Room DB
        if (!song.embeddedLyrics.isNullOrBlank()) {
            return@withContext parse(song.embeddedLyrics)
        }

        // 3. Try reading embedded ID3v2 USLT frame for MP3 files
        if (song.dataPath.endsWith(".mp3", ignoreCase = true) && audioFile.exists() && audioFile.canRead()) {
            val embedded = runCatching { extractId3v2Uslt(audioFile) }.getOrNull()
            if (!embedded.isNullOrBlank()) {
                return@withContext parse(embedded)
            }
        }

        persistentListOf()
    }

    fun parse(rawContent: String): ImmutableList<LyricLine> {
        if (rawContent.isBlank()) return persistentListOf()

        var offsetMs = 0L
        OFFSET_REGEX.find(rawContent)?.let { match ->
            offsetMs = match.groupValues[1].toLongOrNull() ?: 0L
        }

        val timedLines = mutableListOf<LyricLine>()
        val untimedLines = mutableListOf<String>()

        rawContent.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isEmpty()) return@forEach

            val matches = TIME_TAG_REGEX.findAll(trimmed).toList()
            if (matches.isNotEmpty()) {
                val lyricText = TIME_TAG_REGEX.replace(trimmed, "").trim()
                if (lyricText.isNotEmpty()) {
                    matches.forEach { m ->
                        val min = m.groupValues[1].toLongOrNull() ?: 0L
                        val sec = m.groupValues[2].toLongOrNull() ?: 0L
                        val fracStr = m.groupValues[3]
                        val ms = when (fracStr.length) {
                            0 -> 0L
                            1 -> (fracStr.toLongOrNull() ?: 0L) * 100L
                            2 -> (fracStr.toLongOrNull() ?: 0L) * 10L
                            else -> fracStr.take(3).toLongOrNull() ?: 0L
                        }
                        val timestamp = (min * 60_000L + sec * 1_000L + ms + offsetMs).coerceAtLeast(0L)
                        timedLines.add(LyricLine(timestampMs = timestamp, text = lyricText))
                    }
                }
            } else if (!trimmed.startsWith("[") || !trimmed.endsWith("]")) {
                untimedLines.add(trimmed)
            }
        }

        if (timedLines.isNotEmpty()) {
            return timedLines.sortedBy { it.timestampMs }.toImmutableList()
        }

        // Fallback for unsynced lyrics: distribute evenly or mark timestamp 0
        return untimedLines.mapIndexed { index, text ->
            LyricLine(timestampMs = index * 4000L, text = text)
        }.toImmutableList()
    }

    fun findActiveLineIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        var low = 0
        var high = lines.lastIndex
        var result = -1
        while (low <= high) {
            val mid = (low + high) ushr 1
            if (lines[mid].timestampMs <= positionMs) {
                result = mid
                low = mid + 1
            } else {
                high = mid - 1
            }
        }
        return result
    }

    private fun extractId3v2Uslt(file: File): String? {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return null
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] != 'I'.code.toByte() || header[1] != 'D'.code.toByte() || header[2] != '3'.code.toByte()) {
                return null
            }
            val majorVersion = header[3].toInt() and 0xFF
            val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                ((header[7].toInt() and 0x7F) shl 14) or
                ((header[8].toInt() and 0x7F) shl 7) or
                (header[9].toInt() and 0x7F)
            val endOffset = minOf(raf.length(), 10L + tagSize)
            while (raf.filePointer + 10 < endOffset) {
                val frameHeader = ByteArray(10)
                raf.readFully(frameHeader)
                val frameId = String(frameHeader, 0, 4, Charsets.ISO_8859_1)
                if (frameId.all { it == '\u0000' }) break
                val frameSize = if (majorVersion >= 4) {
                    ((frameHeader[4].toInt() and 0x7F) shl 21) or
                        ((frameHeader[5].toInt() and 0x7F) shl 14) or
                        ((frameHeader[6].toInt() and 0x7F) shl 7) or
                        (frameHeader[7].toInt() and 0x7F)
                } else {
                    ((frameHeader[4].toInt() and 0xFF) shl 24) or
                        ((frameHeader[5].toInt() and 0xFF) shl 16) or
                        ((frameHeader[6].toInt() and 0xFF) shl 8) or
                        (frameHeader[7].toInt() and 0xFF)
                }
                if (frameSize <= 0 || raf.filePointer + frameSize > endOffset) break
                if (frameId == "USLT") {
                    val data = ByteArray(frameSize)
                    raf.readFully(data)
                    if (data.size < 5) return null
                    val encoding = data[0].toInt() and 0xFF
                    val charset: Charset = when (encoding) {
                        1 -> Charsets.UTF_16
                        2 -> Charsets.UTF_16BE
                        3 -> Charsets.UTF_8
                        else -> Charsets.ISO_8859_1
                    }
                    // Skip 1 byte encoding + 3 bytes language + null-terminated content descriptor
                    var pos = 4
                    if (encoding == 1 || encoding == 2) {
                        while (pos + 1 < data.size && !(data[pos] == 0.toByte() && data[pos + 1] == 0.toByte())) {
                            pos += 2
                        }
                        pos += 2
                    } else {
                        while (pos < data.size && data[pos] != 0.toByte()) {
                            pos++
                        }
                        pos++
                    }
                    if (pos < data.size) {
                        return String(data, pos, data.size - pos, charset).trim()
                    }
                } else {
                    raf.seek(raf.filePointer + frameSize)
                }
            }
        }
        return null
    }
}
