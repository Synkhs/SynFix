package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.domain.model.SynFixBackupSnapshot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

/**
 * #81 Export/Import Backup .synfix:
 * Compresses playlists, EQ presets, timestamped track notes, custom vibe tags,
 * BT MAC profiles, and stats into a portable GZIP JSON file via SAF.
 */
object SynFixBackupManager {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encodeSnapshotToGzipBytes(snapshot: SynFixBackupSnapshot): ByteArray {
        val rawJson = json.encodeToString(snapshot)
        val baos = ByteArrayOutputStream()
        GZIPOutputStream(baos).use { gzip ->
            gzip.write(rawJson.toByteArray(Charsets.UTF_8))
        }
        return baos.toByteArray()
    }

    fun decodeSnapshotFromGzipBytes(bytes: ByteArray): SynFixBackupSnapshot {
        val rawJson = GZIPInputStream(ByteArrayInputStream(bytes)).bufferedReader(Charsets.UTF_8).use {
            it.readText()
        }
        return json.decodeFromString<SynFixBackupSnapshot>(rawJson)
    }

    suspend fun exportToUri(context: Context, uri: Uri, snapshot: SynFixBackupSnapshot): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val compressed = encodeSnapshotToGzipBytes(snapshot)
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(compressed)
                    out.flush()
                } ?: throw IllegalStateException("No se pudo abrir el archivo de destino")
                compressed.size
            }
        }

    suspend fun importFromUri(context: Context, uri: Uri): Result<SynFixBackupSnapshot> =
        withContext(Dispatchers.IO) {
            runCatching {
                val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                    input.readBytes()
                } ?: throw IllegalStateException("No se pudo leer el archivo .synfix")
                decodeSnapshotFromGzipBytes(bytes)
            }
        }
}
