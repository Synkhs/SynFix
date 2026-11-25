package com.example.ui.screens.pro

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.example.domain.model.Song
import com.example.ui.components.ArtworkImage
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import com.example.ui.theme.PolygonShape
import com.example.ui.theme.SynFixPolygons
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.math.sin

/**
 * #21 M3 Expressive Session Cards:
 * Renders an organic M3 Expressive session card with artwork, waveform signature, and metadata,
 * captures it offscreen via rememberGraphicsLayer().toImageBitmap(), and shares it via FileProvider.
 */
@Composable
fun SessionCardExportDialog(
    song: Song,
    positionMs: Long,
    onDismiss: () -> Unit,
    onExportedMessage: (String) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val graphicsLayer = rememberGraphicsLayer()
    var isSharing by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val tertiaryColor = MaterialTheme.colorScheme.tertiary
    val containerStart = MaterialTheme.colorScheme.primaryContainer
    val containerEnd = MaterialTheme.colorScheme.surfaceContainerHighest

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Tarjeta de Sesión Expressive") },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .drawWithContent {
                            graphicsLayer.record {
                                this@drawWithContent.drawContent()
                            }
                            drawLayer(graphicsLayer)
                        }
                ) {
                    Surface(
                        shape = RoundedCornerShape(28.dp),
                        color = containerEnd,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(containerStart, containerEnd)
                                    )
                                )
                                .padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "SYNFIX HI-RES",
                                    style = ExpressiveTypography.MonoTimecode,
                                    color = primaryColor
                                )
                                Text(
                                    text = "${song.bpm} BPM",
                                    style = ExpressiveTypography.MonoTimecode,
                                    color = tertiaryColor
                                )
                            }

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp)
                            ) {
                                ArtworkImage(
                                    artworkUri = song.artworkUri,
                                    contentDescription = song.album,
                                    targetSizeDp = 88.dp,
                                    shape = PolygonShape(SynFixPolygons.softOctagon()),
                                    proceduralSeedKey = "${song.album}|${song.artist}",
                                    modifier = Modifier.size(88.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = song.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.artist,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = song.album,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            // Signature waveform bar
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(34.dp)
                            ) {
                                val bars = 36
                                val step = size.width / bars
                                val centerY = size.height / 2f
                                val seed = song.id.toInt()
                                for (i in 0 until bars) {
                                    val mag = (0.25f + 0.70f * kotlin.math.abs(sin((i + seed) * 0.65f))).coerceIn(0.2f, 1f)
                                    val halfH = (size.height * 0.44f) * mag
                                    val x = i * step + step / 2f
                                    val frac = i / bars.toFloat()
                                    val playedFrac = (positionMs.toFloat() / song.durationMs.coerceAtLeast(1L)).coerceIn(0f, 1f)
                                    drawLine(
                                        color = if (frac <= playedFrac) primaryColor else primaryColor.copy(alpha = 0.3f),
                                        start = Offset(x, centerY - halfH),
                                        end = Offset(x, centerY + halfH),
                                        strokeWidth = 3.dp.toPx(),
                                        cap = StrokeCap.Round
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = formatDuration(positionMs),
                                    style = ExpressiveTypography.MonoTimecode,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatDuration(song.durationMs),
                                    style = ExpressiveTypography.MonoTimecode,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isSharing,
                onClick = {
                    isSharing = true
                    scope.launch {
                        runCatching {
                            val imageBitmap = graphicsLayer.toImageBitmap()
                            val androidBmp = imageBitmap.asAndroidBitmap()
                            val uri = saveAndGetShareUri(context, androidBmp, song.id)
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "image/png"
                                putExtra(Intent.EXTRA_STREAM, uri)
                                putExtra(Intent.EXTRA_TEXT, "Escuchando ${song.title} — ${song.artist} en SynFix")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Compartir tarjeta de sesión"))
                        }.onSuccess {
                            onExportedMessage("Tarjeta de sesión lista para compartir")
                            onDismiss()
                        }.onFailure { err ->
                            onExportedMessage("Error al exportar tarjeta: ${err.message}")
                        }
                        isSharing = false
                    }
                }
            ) {
                Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isSharing) "Exportando..." else "Compartir PNG")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

private suspend fun saveAndGetShareUri(
    context: Context,
    bitmap: Bitmap,
    songId: Long
) = withContext(Dispatchers.IO) {
    val dir = File(context.cacheDir, "shared_cards").apply { mkdirs() }
    val outFile = File(dir, "synfix_card_${songId}_${System.currentTimeMillis()}.png")
    FileOutputStream(outFile).use { fos ->
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
    }
    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", outFile)
}
