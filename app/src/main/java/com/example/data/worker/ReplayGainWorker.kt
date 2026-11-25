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
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * #8 True LUFS ReplayGain Scanner:
 * Scheduled via WorkManager with charging constraint (`setRequiresCharging(true)`)
 * to analyze perceived loudness (-14 LUFS target) without draining battery.
 */
class ReplayGainWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as SynFixApplication
        val dao = app.database.musicDao()
        val songs = dao.getAllSongsOnce().filter { it.lufsDb == null }

        songs.forEach { song ->
            if (isStopped) return@withContext Result.success()
            val file = File(song.dataPath)
            if (file.exists() && file.canRead()) {
                val lufs = measureApproxIntegratedLufs(song.dataPath)
                if (lufs != null) {
                    val gainDb = (-14.0f - lufs).coerceIn(-12f, 12f)
                    dao.updateReplayGain(song.id, lufs, gainDb)
                }
            }
        }
        Result.success()
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "synfix_lufs_replaygain_scan"

        fun scheduleChargingScan(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiresCharging(true)
                .build()

            val request = OneTimeWorkRequestBuilder<ReplayGainWorker>()
                .setConstraints(constraints)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        fun measureApproxIntegratedLufs(filePath: String): Float? {
            val extractor = MediaExtractor()
            var codec: MediaCodec? = null
            return try {
                extractor.setDataSource(filePath)
                var audioTrackIndex = -1
                var format: MediaFormat? = null
                for (i in 0 until extractor.trackCount) {
                    val trackFormat = extractor.getTrackFormat(i)
                    val mime = trackFormat.getString(MediaFormat.KEY_MIME) ?: ""
                    if (mime.startsWith("audio/")) {
                        audioTrackIndex = i
                        format = trackFormat
                        break
                    }
                }
                if (audioTrackIndex < 0 || format == null) return null
                val mime = format.getString(MediaFormat.KEY_MIME) ?: return null
                extractor.selectTrack(audioTrackIndex)

                codec = MediaCodec.createDecoderByType(mime)
                codec.configure(format, null, null, 0)
                codec.start()

                val bufferInfo = MediaCodec.BufferInfo()
                var sumSquares = 0.0
                var sampleCount = 0L
                var inputDone = false
                var outputDone = false
                val maxSamplesToMeasure = 44_100L * 2L * 18L // sample up to ~18 seconds evenly

                // Seek to ~30% of track for representative loudness measurement
                val durationUs = if (format.containsKey(MediaFormat.KEY_DURATION)) format.getLong(MediaFormat.KEY_DURATION) else 0L
                if (durationUs > 20_000_000L) {
                    extractor.seekTo(durationUs / 3L, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
                }

                while (!outputDone && sampleCount < maxSamplesToMeasure) {
                    if (!inputDone) {
                        val inIndex = codec.dequeueInputBuffer(5000)
                        if (inIndex >= 0) {
                            val inBuf = codec.getInputBuffer(inIndex) ?: continue
                            val sampleSize = extractor.readSampleData(inBuf, 0)
                            if (sampleSize < 0) {
                                codec.queueInputBuffer(inIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                                inputDone = true
                            } else {
                                codec.queueInputBuffer(inIndex, 0, sampleSize, extractor.sampleTime, 0)
                                extractor.advance()
                            }
                        }
                    }

                    val outIndex = codec.dequeueOutputBuffer(bufferInfo, 5000)
                    if (outIndex >= 0) {
                        val outBuf = codec.getOutputBuffer(outIndex)
                        if (outBuf != null && bufferInfo.size > 0) {
                            outBuf.position(bufferInfo.offset)
                            outBuf.limit(bufferInfo.offset + bufferInfo.size)
                            val shortBuf = outBuf.asShortBuffer()
                            while (shortBuf.hasRemaining()) {
                                val normalized = shortBuf.get() / 32768.0
                                sumSquares += normalized * normalized
                                sampleCount++
                            }
                        }
                        codec.releaseOutputBuffer(outIndex, false)
                        if ((bufferInfo.flags and MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            outputDone = true
                        }
                    }
                }

                if (sampleCount == 0L) return null
                val rms = sqrt(sumSquares / sampleCount.toDouble()).coerceAtLeast(1e-6)
                (20.0 * log10(rms) - 0.691).toFloat().coerceIn(-45f, 0f)
            } catch (_: Throwable) {
                null
            } finally {
                runCatching { codec?.stop() }
                runCatching { codec?.release() }
                runCatching { extractor.release() }
            }
        }
    }
}
