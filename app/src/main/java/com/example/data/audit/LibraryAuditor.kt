package com.example.data.audit

import android.media.MediaExtractor
import android.media.MediaFormat
import com.example.data.repository.MusicRepository
import com.example.domain.model.AuditFinding
import com.example.domain.model.AuditIssueType
import com.example.domain.model.Song
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * #17 Fake-FLAC & Duplicate Auditor:
 * On-demand scanner on Dispatchers.IO inspecting MediaExtractor stream specs and metadata signatures.
 */
object LibraryAuditor {

    suspend fun auditLibrary(
        songs: List<Song>,
        repository: MusicRepository
    ): ImmutableList<AuditFinding> = withContext(Dispatchers.IO) {
        val findings = mutableListOf<AuditFinding>()
        val seenSignatures = HashMap<String, Song>()

        songs.forEach { song ->
            val file = File(song.dataPath)
            if (!file.exists()) return@forEach

            var realBitrateKbps = song.bitrateKbps
            var realSampleRate = song.sampleRateHz
            var streamMime = song.mimeType

            // Inspect actual container/codec properties via MediaExtractor
            val extractor = MediaExtractor()
            runCatching {
                extractor.setDataSource(song.dataPath)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        streamMime = mime
                        if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) {
                            realSampleRate = format.getInteger(MediaFormat.KEY_SAMPLE_RATE)
                        }
                        if (format.containsKey(MediaFormat.KEY_BIT_RATE)) {
                            realBitrateKbps = format.getInteger(MediaFormat.KEY_BIT_RATE) / 1000
                        }
                        break
                    }
                }
            }
            runCatching { extractor.release() }

            val fileSize = file.length()
            if (realBitrateKbps <= 0 && song.durationMs > 0) {
                realBitrateKbps = ((fileSize * 8L) / song.durationMs).toInt()
            }

            repository.updateAudioTechnicalInfo(song.id, realBitrateKbps, realSampleRate, fileSize)

            // 1. Fake-FLAC / Transcode check
            val isFlacExt = song.dataPath.endsWith(".flac", ignoreCase = true) ||
                streamMime.contains("flac", ignoreCase = true)
            if (isFlacExt && (realBitrateKbps in 1..420 || realSampleRate < 44100)) {
                findings.add(
                    AuditFinding(
                        song = song,
                        issueType = AuditIssueType.FAKE_FLAC,
                        details = "FLAC con bitrate efectivo ${realBitrateKbps} kbps / ${realSampleRate} Hz (posible transcodificación desde MP3/AAC)"
                    )
                )
            } else if (!isFlacExt && realBitrateKbps in 1..112) {
                findings.add(
                    AuditFinding(
                        song = song,
                        issueType = AuditIssueType.LOW_BITRATE,
                        details = "Calidad baja detectada: ${realBitrateKbps} kbps (${realSampleRate} Hz)"
                    )
                )
            }

            // 2. Duplicate check (normalized title + artist + approx duration bucket)
            val signature = "${song.title.trim().lowercase()}|${song.artist.trim().lowercase()}|${song.durationMs / 2000L}"
            val existing = seenSignatures[signature]
            if (existing != null) {
                findings.add(
                    AuditFinding(
                        song = song,
                        issueType = AuditIssueType.DUPLICATE,
                        details = "Duplicado de \"${existing.title}\" (${file.name}, ${realBitrateKbps} kbps)"
                    )
                )
            } else {
                seenSignatures[signature] = song
            }
        }

        findings.toImmutableList()
    }
}
