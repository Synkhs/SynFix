package com.example.data.tags

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.db.SongEntity
import com.example.domain.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile

data class TagUpdateRequest(
    val song: Song,
    val title: String,
    val artist: String,
    val album: String,
    val genre: String,
    val year: Int,
    val trackNumber: Int,
    val lyrics: String?,
    val newCoverArtUri: Uri?
)

class AudioTagEditor(private val context: Context) {

    suspend fun applyTagUpdates(request: TagUpdateRequest): SongEntity = withContext(Dispatchers.IO) {
        val song = request.song
        var persistedArtUri = song.customArtUri

        // Copy picked image to durable internal app storage so permissions never expire
        if (request.newCoverArtUri != null) {
            val savedPath = persistCustomArtwork(song.id, request.newCoverArtUri)
            if (savedPath != null) {
                persistedArtUri = Uri.fromFile(File(savedPath)).toString()
            }
        }

        // Write sibling .lrc file if user provided synced LRC lines and directory is writable
        if (!request.lyrics.isNullOrBlank()) {
            runCatching {
                val audioFile = File(song.dataPath)
                if (audioFile.parentFile?.canWrite() == true) {
                    val lrcFile = File(audioFile.parentFile, "${audioFile.nameWithoutExtension}.lrc")
                    lrcFile.writeText(request.lyrics, Charsets.UTF_8)
                }
            }
        }

        // Attempt direct file tag write for MP3 (ID3v2) when file is writable
        val audioFile = File(song.dataPath)
        if (audioFile.exists() && audioFile.canWrite() && song.dataPath.endsWith(".mp3", ignoreCase = true)) {
            runCatching {
                writeId3v2TagsToMp3(
                    file = audioFile,
                    title = request.title,
                    artist = request.artist,
                    album = request.album,
                    genre = request.genre,
                    year = request.year,
                    track = request.trackNumber,
                    coverArtBytes = persistedArtUri?.let { readArtBytes(Uri.parse(it)) }
                )
            }
        }

        // Attempt MediaStore metadata update (best-effort across Android versions)
        runCatching {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.TITLE, request.title)
                put(MediaStore.Audio.Media.ARTIST, request.artist)
                put(MediaStore.Audio.Media.ALBUM, request.album)
                if (request.year > 0) put(MediaStore.Audio.Media.YEAR, request.year)
                if (request.trackNumber > 0) put(MediaStore.Audio.Media.TRACK, request.trackNumber)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    put(MediaStore.Audio.Media.GENRE, request.genre)
                }
            }
            context.contentResolver.update(song.contentUri, values, null, null)
        }

        runCatching {
            MediaScannerConnection.scanFile(context, arrayOf(song.dataPath), null, null)
        }

        SongEntity(
            id = song.id,
            title = request.title.ifBlank { song.title },
            artist = request.artist.ifBlank { song.artist },
            album = request.album.ifBlank { song.album },
            albumId = song.albumId,
            duration = song.durationMs,
            dataPath = song.dataPath,
            folderPath = song.folderPath,
            dateAdded = song.dateAdded,
            dateModified = System.currentTimeMillis() / 1000L,
            trackNumber = request.trackNumber,
            year = request.year,
            genre = request.genre.ifBlank { "Unknown" },
            mimeType = song.mimeType,
            isFavorite = song.isFavorite,
            playCount = song.playCount,
            lastPlayed = song.lastPlayed,
            lyrics = request.lyrics?.takeIf { it.isNotBlank() },
            customArtUri = persistedArtUri
        )
    }

    private fun persistCustomArtwork(songId: Long, sourceUri: Uri): String? {
        return runCatching {
            val artDir = File(context.filesDir, "custom_artwork").apply { mkdirs() }
            val target = File(artDir, "cover_${songId}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(target).use { output ->
                    input.copyTo(output)
                }
            }
            if (target.exists() && target.length() > 0) target.absolutePath else null
        }.getOrNull()
    }

    private fun readArtBytes(uri: Uri): ByteArray? {
        return runCatching {
            if (uri.scheme == "file") {
                uri.path?.let { File(it).readBytes() }
            } else {
                context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            }
        }.getOrNull()
    }

    /**
     * Writes clean ID3v2.3 UTF-16 frames (TIT2, TPE1, TALB, TCON, TYER, TRCK, APIC)
     * preserving the underlying MPEG audio stream intact.
     */
    private fun writeId3v2TagsToMp3(
        file: File,
        title: String,
        artist: String,
        album: String,
        genre: String,
        year: Int,
        track: Int,
        coverArtBytes: ByteArray?
    ) {
        val framesOut = ByteArrayOutputStream()

        fun writeTextFrame(frameId: String, value: String) {
            if (value.isBlank()) return
            val encoded = value.toByteArray(Charsets.UTF_16)
            val payloadSize = 1 + encoded.size
            framesOut.write(frameId.toByteArray(Charsets.ISO_8859_1))
            framesOut.write(intToBigEndianBytes(payloadSize))
            framesOut.write(byteArrayOf(0, 0)) // flags
            framesOut.write(1) // UTF-16 with BOM
            framesOut.write(encoded)
        }

        writeTextFrame("TIT2", title)
        writeTextFrame("TPE1", artist)
        writeTextFrame("TALB", album)
        writeTextFrame("TCON", genre)
        if (year > 0) writeTextFrame("TYER", year.toString())
        if (track > 0) writeTextFrame("TRCK", track.toString())

        if (coverArtBytes != null && coverArtBytes.isNotEmpty()) {
            val mime = "image/jpeg".toByteArray(Charsets.ISO_8859_1)
            val payloadSize = 1 + mime.size + 1 + 1 + 1 + coverArtBytes.size
            framesOut.write("APIC".toByteArray(Charsets.ISO_8859_1))
            framesOut.write(intToBigEndianBytes(payloadSize))
            framesOut.write(byteArrayOf(0, 0))
            framesOut.write(0) // ISO-8859-1
            framesOut.write(mime)
            framesOut.write(0) // null terminator
            framesOut.write(3) // Cover (front)
            framesOut.write(0) // empty description null terminator
            framesOut.write(coverArtBytes)
        }

        val frameBytes = framesOut.toByteArray()
        val existingHeaderOffset = detectId3v2AudioOffset(file)
        val audioStreamBytes = RandomAccessFile(file, "r").use { raf ->
            raf.seek(existingHeaderOffset)
            val remaining = (raf.length() - existingHeaderOffset).toInt().coerceAtLeast(0)
            ByteArray(remaining).also { raf.readFully(it) }
        }

        val tempFile = File(file.parentFile, "${file.name}.tmp")
        FileOutputStream(tempFile).use { fos ->
            fos.write("ID3".toByteArray(Charsets.ISO_8859_1))
            fos.write(3) // v2.3
            fos.write(0)
            fos.write(0)
            fos.write(encodeSyncSafeInt(frameBytes.size))
            fos.write(frameBytes)
            fos.write(audioStreamBytes)
        }
        if (tempFile.exists() && tempFile.length() > audioStreamBytes.size) {
            tempFile.copyTo(file, overwrite = true)
            tempFile.delete()
        }
    }

    private fun detectId3v2AudioOffset(file: File): Long {
        RandomAccessFile(file, "r").use { raf ->
            if (raf.length() < 10) return 0L
            val header = ByteArray(10)
            raf.readFully(header)
            if (header[0] == 'I'.code.toByte() && header[1] == 'D'.code.toByte() && header[2] == '3'.code.toByte()) {
                val tagSize = ((header[6].toInt() and 0x7F) shl 21) or
                    ((header[7].toInt() and 0x7F) shl 14) or
                    ((header[8].toInt() and 0x7F) shl 7) or
                    (header[9].toInt() and 0x7F)
                return (10L + tagSize).coerceAtMost(raf.length())
            }
            return 0L
        }
    }

    private fun intToBigEndianBytes(value: Int): ByteArray = byteArrayOf(
        ((value shr 24) and 0xFF).toByte(),
        ((value shr 16) and 0xFF).toByte(),
        ((value shr 8) and 0xFF).toByte(),
        (value and 0xFF).toByte()
    )

    private fun encodeSyncSafeInt(value: Int): ByteArray = byteArrayOf(
        ((value shr 21) and 0x7F).toByte(),
        ((value shr 14) and 0x7F).toByte(),
        ((value shr 7) and 0x7F).toByte(),
        (value and 0x7F).toByte()
    )
}
