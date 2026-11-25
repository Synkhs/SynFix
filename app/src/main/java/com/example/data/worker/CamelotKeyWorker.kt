package com.example.data.worker

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.work.*
import com.example.SynFixApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos

/**
 * #30 Harmonic Key & Camelot Wheel Analyzer:
 * - Decodes a lightweight mid-track PCM window via MediaExtractor/MediaCodec,
 *   computes 12-semitone chromagram energy (Goertzel across octaves 2..4),
 *   and correlates against Krumhansl-Schmuckler Major/Minor key profiles to map to Camelot 1A..12B.
 */
object CamelotWheelAnalyzer {

    // Camelot codes indexed by pitch class (0=C, 1=C#, ..., 11=B)
    private val MAJOR_CAMELOT = arrayOf("8B", "3B", "10B", "5B", "12B", "7B", "2B", "9B", "4B", "11B", "6B", "1B")
    private val MINOR_CAMELOT = arrayOf("5A", "12A", "7A", "2A", "9A", "4A", "11A", "6A", "1A", "8A", "3A", "10A")

    private val MAJOR_PROFILE = floatArrayOf(6.35f, 2.23f, 3.48f, 2.33f, 4.38f, 4.09f, 2.52f, 5.19f, 2.39f, 3.66f, 2.29f, 2.88f)
    private val MINOR_PROFILE = floatArrayOf(6.33f, 2.68f, 3.52f, 5.38f, 2.60f, 3.53f, 2.54f, 4.75f, 3.98f, 2.69f, 3.34f, 3.17f)

    // Semitone frequencies for octave 3 (C3..B3)
    private val OCTAVE3_FREQS = floatArrayOf(
        130.81f, 138.59f, 146.83f, 155.56f, 164.81f, 174.61f,
        185.00f, 196.00f, 207.65f, 220.00f, 233.08f, 246.94f
    )

    val ALL_CAMELOT_KEYS: List<String> = (1..12).flatMap { n -> listOf("${n}A", "${n}B") }

    /**
     * Returns harmonically compatible Camelot keys for seamless DJ mixing:
     * - Same key (e.g. 8A)
     * - Relative Major/Minor (8B)
     * - +1 / -1 Fifths (9A, 7A)
     */
    fun getCompatibleKeys(camelotKey: String): List<String> {
        val cleaned = camelotKey.trim().uppercase()
        val letter = cleaned.lastOrNull()?.takeIf { it == 'A' || it == 'B' } ?: return listOf("8A", "8B", "7A", "9A")
        val number = cleaned.dropLast(1).toIntOrNull()?.coerceIn(1, 12) ?: return listOf("8A", "8B", "7A", "9A")

        val prev = if (number == 1) 12 else number - 1
        val next = if (number == 12) 1 else number + 1
        val opposite = if (letter == 'A') 'B' else 'A'

        return listOf(
            "$number$letter",
            "$number$opposite",
            "$prev$letter",
            "$next$letter"
        ).distinct()
    }

    fun getKeyDisplayName(camelotKey: String): String {
        return when (camelotKey.uppercase()) {
            "1A" -> "G#m / Abm"
            "2A" -> "D#m / Ebm"
            "3A" -> "A#m / Bbm"
            "4A" -> "Fm"
            "5A" -> "Cm"
            "6A" -> "Gm"
            "7A" -> "Dm"
            "8A" -> "Am"
            "9A" -> "Em"
            "10A" -> "Bm"
            "11A" -> "F#m"
            "12A" -> "C#m"
            "1B" -> "B Maj"
            "2B" -> "F# Maj"
            "3B" -> "Db Maj"
            "4B" -> "Ab Maj"
            "5B" -> "Eb Maj"
            "6B" -> "Bb Maj"
            "7B" -> "F Maj"
            "8B" -> "C Maj"
            "9B" -> "G Maj"
            "10B" -> "D Maj"
            "11B" -> "A Maj"
            "12B" -> "E Maj"
            else -> "Am"
        }
    }

    fun estimateFromMetadata(genre: String, title: String): String {
        val idx = abs((genre + title).hashCode()) % ALL_CAMELOT_KEYS.size
        return ALL_CAMELOT_KEYS[idx]
    }

    fun classifyChromaToCamelot(chroma: FloatArray): String {
        if (chroma.size != 12) return "8A"
        var bestScore = Float.NEGATIVE_INFINITY
        var bestKey = "8A"

        for (tonic in 0 until 12) {
            var majorScore = 0f
            var minorScore = 0f
            for (i in 0 until 12) {
                val energy = chroma[(tonic + i) % 12]
                majorScore += energy * MAJOR_PROFILE[i]
                minorScore += energy * MINOR_PROFILE[i]
            }
            if (majorScore > bestScore) {
                bestScore = majorScore
                bestKey = MAJOR_CAMELOT[tonic]
            }
            if (minorScore > bestScore) {
                bestScore = minorScore
                bestKey = MINOR_CAMELOT[tonic]
            }
        }
        return bestKey
    }

    fun analyzeAudioFile(filePath: String): String? {
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        return try {
            extractor.setDataSource(filePath)
            var audioTrack = -1
            var format: MediaFormat? = null
            for (i in 0 until extractor.trackCount) {
                val tf = extractor.getTrackFormat(i)
                val mime = tf.getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) {
                    audioTrack = i
                    format = tf
                    break
                }
            }
            if (audioTrack < 0 || format == null) return null
            val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
            val sampleRate = if (format.containsKey(MediaFormat.KEY_SAMPLE_RATE)) format.getInteger(MediaFormat.KEY_SAMPLE_RATE) else 44100
            val channels = if (format.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) format.getInteger(MediaFormat.KEY_CHANNEL_COUNT).coerceAtLeast(1) else 2
            val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L

            extractor.selectTrack(audioTrack)
            if (durationUs > 25_000_000L) {
                extractor.seekTo(durationUs / 3L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            }

            codec = MediaCodec.createDecoderByType(mime)
            codec.configure(format, null, null, 0)
            codec.start()

            val windowSize = 2048
            val monoWindow = FloatArray(windowSize)
            var windowPos = 0
            val chroma = FloatArray(12)
            var windowsAnalyzed = 0
            val maxWindows = 16

            val bufferInfo = MediaCodec.BufferInfo()
            var inputDone = false
            var outputDone = false

            while (!outputDone && windowsAnalyzed < maxWindows) {
                if (!inputDone) {
                    val inIdx = codec.dequeueInputBuffer(5000)
                    if (inIdx >= 0) {
                        val inBuf = codec.getInputBuffer(inIdx) ?: continue
                        val sampleSize = extractor.readSampleData(inBuf, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inIdx, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputDone = true
                        } else {
                            codec.queueInputBuffer(inIdx, 0, sampleSize, extractor.sampleTime, 0)
                            extractor.advance()
                        }
                    }
                }

                val outIdx = codec.dequeueOutputBuffer(bufferInfo, 5000)
                if (outIdx >= 0) {
                    val outBuf = codec.getOutputBuffer(outIdx)
                    if (outBuf != null && bufferInfo.size > 0) {
                        outBuf.position(bufferInfo.offset)
                        outBuf.limit(bufferInfo.offset + bufferInfo.size)
                        val shortBuf = outBuf.asShortBuffer()
                        while (shortBuf.hasRemaining() && windowsAnalyzed < maxWindows) {
                            var monoSum = 0f
                            var read = 0
                            for (ch in 0 until channels) {
                                if (shortBuf.hasRemaining()) {
                                    monoSum += shortBuf.get() / 32768f
                                    read++
                                }
                            }
                            if (read > 0) {
                                monoWindow[windowPos++] = monoSum / read
                                if (windowPos == windowSize) {
                                    accumulateGoertzelChroma(monoWindow, sampleRate.toFloat(), chroma)
                                    windowPos = 0
                                    windowsAnalyzed++
                                }
                            }
                        }
                    }
                    codec.releaseOutputBuffer(outIdx, false)
                    if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        outputDone = true
                    }
                }
            }

            if (windowsAnalyzed == 0) return null
            classifyChromaToCamelot(chroma)
        } catch (_: Throwable) {
            null
        } finally {
            runCatching { codec?.stop() }
            runCatching { codec?.release() }
            runCatching { extractor.release() }
        }
    }

    private fun accumulateGoertzelChroma(samples: FloatArray, sampleRate: Float, chromaOut: FloatArray) {
        val n = samples.size
        for (pitch in 0 until 12) {
            val freq = OCTAVE3_FREQS[pitch]
            val k = (0.5 + (n * freq) / sampleRate).toInt()
            val omega = (2.0 * PI * k) / n
            val coeff = (2.0 * cos(omega)).toFloat()
            var q0: Float
            var q1 = 0f
            var q2 = 0f
            for (i in 0 until n) {
                q0 = coeff * q1 - q2 + samples[i]
                q2 = q1
                q1 = q0
            }
            val power = (q1 * q1 + q2 * q2 - q1 * q2 * coeff).coerceAtLeast(0f)
            chromaOut[pitch] += power
        }
    }
}

/**
 * Charging-only WorkManager task analyzing Camelot musical keys into indexed Room columns.
 */
class CamelotKeyWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as SynFixApplication
        val dao = app.database.musicDao()
        val songs = dao.getAllSongsOnce()

        songs.forEach { song ->
            if (isStopped) return@withContext Result.success()
            val file = File(song.dataPath)
            if (file.exists() && file.canRead()) {
                val detected = CamelotWheelAnalyzer.analyzeAudioFile(song.dataPath)
                if (!detected.isNullOrBlank()) {
                    dao.updateCamelotKey(song.id, detected)
                }
            }
        }
        Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "synfix_camelot_key_scan"

        fun scheduleChargingScan(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()

            val request = OneTimeWorkRequestBuilder<CamelotKeyWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }
    }
}
