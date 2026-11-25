package com.example.player

import android.content.Context
import android.media.MediaScannerConnection
import android.os.Environment
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import com.example.domain.model.Song
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume

/**
 * #16 On-Device Snippet Cutter:
 * Exports a trimmed audio clip / ringtone using Media3 Transformer + ClippingConfiguration.
 */
object SnippetCutter {

    @OptIn(UnstableApi::class)
    suspend fun exportClip(
        context: Context,
        song: Song,
        startMs: Long,
        endMs: Long
    ): Result<File> = suspendCancellableCoroutine { cont ->
        val safeStart = startMs.coerceAtLeast(0L)
        val safeEnd = endMs.coerceAtLeast(safeStart + 1000L)

        val outDir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MUSIC),
            "SynFix_Clips"
        ).apply { mkdirs() }
        val sanitizedTitle = song.title.replace(Regex("[^a-zA-Z0-9_\\- ]"), "_").take(32)
        val outFile = File(outDir, "${sanitizedTitle}_${safeStart / 1000}s_${safeEnd / 1000}s.m4a")

        val clippingConfig = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(safeStart)
            .setEndPositionMs(safeEnd)
            .build()

        val mediaItem = MediaItem.Builder()
            .setUri(song.contentUri)
            .setClippingConfiguration(clippingConfig)
            .build()

        val editedMediaItem = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    MediaScannerConnection.scanFile(context, arrayOf(outFile.absolutePath), null, null)
                    if (cont.isActive) cont.resume(Result.success(outFile))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException
                ) {
                    if (cont.isActive) cont.resume(Result.failure(exportException))
                }
            })
            .build()

        cont.invokeOnCancellation {
            runCatching { transformer.cancel() }
        }

        runCatching {
            transformer.start(editedMediaItem, outFile.absolutePath)
        }.onFailure { err ->
            if (cont.isActive) cont.resume(Result.failure(err))
        }
    }
}
