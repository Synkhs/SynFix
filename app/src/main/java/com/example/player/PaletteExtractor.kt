package com.example.player

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.ui.graphics.Color
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * #5 Liquid Color Drag Transition support:
 * Caches exact 64x64 px extracted Palette seed colors in an LRU cache for instant lerp() interpolation.
 */
object PaletteExtractor {
    private val cache = LruCache<String, Color>(128)

    fun getCachedSeedColor(artworkUri: Uri?): Color? {
        if (artworkUri == null) return null
        return cache.get(artworkUri.toString())
    }

    suspend fun extractSeedColor(context: Context, artworkUri: Uri?): Color? {
        if (artworkUri == null) return null
        val key = artworkUri.toString()
        cache.get(key)?.let { return it }

        val bitmap = withContext(Dispatchers.IO) {
            decode64x64Bitmap(context, artworkUri)
        } ?: return null

        val extracted = withContext(Dispatchers.Default) {
            val palette = Palette.from(bitmap).maximumColorCount(16).generate()
            val rgb = palette.vibrantSwatch?.rgb
                ?: palette.lightVibrantSwatch?.rgb
                ?: palette.darkVibrantSwatch?.rgb
                ?: palette.dominantSwatch?.rgb
            bitmap.recycle()
            rgb?.let { Color(it) }
        }

        if (extracted != null) {
            cache.put(key, extracted)
        }
        return extracted
    }

    private fun decode64x64Bitmap(context: Context, uri: Uri): Bitmap? {
        return runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            var sampleSize = 1
            while (options.outWidth / sampleSize > 128 || options.outHeight / sampleSize > 128) {
                sampleSize *= 2
            }
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            val decoded = context.contentResolver.openInputStream(uri)?.use { stream ->
                BitmapFactory.decodeStream(stream, null, decodeOptions)
            } ?: return null
            if (decoded.width > 64 || decoded.height > 64) {
                Bitmap.createScaledBitmap(decoded, 64, 64, true).also {
                    if (it != decoded) decoded.recycle()
                }
            } else {
                decoded
            }
        }.getOrNull()
    }
}
