package com.example.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.AsyncImagePainter
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import com.example.R
import com.example.domain.model.ArtworkShapeStyle
import com.example.ui.theme.PolygonShape
import com.example.ui.theme.SynFixPolygons

@Composable
fun rememberArtworkShape(style: ArtworkShapeStyle): Shape {
    return remember(style) {
        when (style) {
            ArtworkShapeStyle.SOFT_SQUARE -> RoundedCornerShape(8.dp)
            ArtworkShapeStyle.EXPRESSIVE_ROUNDED -> RoundedCornerShape(20.dp)
            ArtworkShapeStyle.ORGANIC -> PolygonShape(SynFixPolygons.softOctagon())
        }
    }
}

/**
 * Coil 3 Artwork loader with:
 * - Exact pixel downsampling to avoid OOM on 4K embedded covers
 * - #18 Procedural Cover Art fallback (0 MB disk/RAM) when embedded cover is missing
 * - #1 Optional Beat-Synced graphicsLayer scaling driven by 20-100 Hz sub-bass energy
 */
@Composable
fun ArtworkImage(
    artworkUri: Uri?,
    contentDescription: String?,
    targetSizeDp: Dp,
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    circular: Boolean = false,
    proceduralSeedKey: String? = null,
    useProceduralFallback: Boolean = true,
    beatEnergyProvider: (() -> Float)? = null
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val targetPx = remember(targetSizeDp, density) {
        with(density) { targetSizeDp.roundToPx().coerceIn(64, 1024) }
    }

    val appliedShape = if (circular) CircleShape else shape
    val placeholder = painterResource(id = R.drawable.img_default_cover_1795610069342)
    var imageFailed by remember(artworkUri) { mutableStateOf(artworkUri == null) }

    val request = remember(artworkUri, targetPx) {
        val isLocalUri = artworkUri == null ||
            artworkUri.scheme == null ||
            artworkUri.scheme == "content" ||
            artworkUri.scheme == "file" ||
            artworkUri.scheme == "android.resource"
        ImageRequest.Builder(context)
            .data(artworkUri)
            .size(Size(targetPx, targetPx))
            .memoryCachePolicy(CachePolicy.ENABLED)
            .diskCachePolicy(if (isLocalUri) CachePolicy.DISABLED else CachePolicy.ENABLED)
            .crossfade(220)
            .build()
    }

    Box(
        modifier = modifier
            .then(
                if (beatEnergyProvider != null) {
                    Modifier.graphicsLayer {
                        val energy = beatEnergyProvider().coerceIn(0f, 1f)
                        val pulse = 1f + energy * 0.055f
                        scaleX = pulse
                        scaleY = pulse
                    }
                } else {
                    Modifier
                }
            )
            .clip(appliedShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        if (imageFailed && useProceduralFallback) {
            ProceduralCoverArt(
                seedKey = proceduralSeedKey ?: artworkUri?.toString() ?: contentDescription ?: "SynFix",
                modifier = Modifier.fillMaxSize()
            )
        } else {
            AsyncImage(
                model = request,
                contentDescription = contentDescription,
                placeholder = if (useProceduralFallback) null else placeholder,
                error = if (useProceduralFallback) null else placeholder,
                fallback = if (useProceduralFallback) null else placeholder,
                onSuccess = { imageFailed = false },
                onError = { imageFailed = true },
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}
