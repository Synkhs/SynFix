package com.example.ui.screens.pro

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.model.Song
import com.example.ui.components.formatDuration
import com.example.ui.theme.ExpressiveTypography
import kotlin.math.abs
import kotlin.math.sin

/**
 * #16 On-Device Snippet Cutter:
 * Interactive waveform range trimmer exporting audio clips using Media3 Transformer.
 */
@Composable
fun SnippetCutterDialog(
    song: Song,
    isExporting: Boolean,
    lastExportedPath: String?,
    onExportClip: (startMs: Long, endMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    val totalMs = song.durationMs.coerceAtLeast(10_000L)
    var range by remember(song.id) {
        val defaultEnd = (minOf(totalMs, 45_000L).toFloat() / totalMs.toFloat()).coerceIn(0.2f, 1f)
        mutableStateOf(0.05f..defaultEnd)
    }

    val startMs = (range.start * totalMs).toLong()
    val endMs = (range.endInclusive * totalMs).toLong().coerceAtLeast(startMs + 2000L)

    val primary = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.outlineVariant

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.ContentCut, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Text("Recortador On-Device")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(74.dp)
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        val bars = 42
                        val step = size.width / bars
                        val centerY = size.height / 2f
                        val seed = song.id.toInt()
                        for (i in 0 until bars) {
                            val frac = i / bars.toFloat()
                            val inSelection = frac in range.start..range.endInclusive
                            val amp = (0.25f + 0.72f * abs(sin((i + seed) * 0.55f))).coerceIn(0.2f, 1f)
                            val halfH = (size.height * 0.45f) * amp
                            val x = i * step + step / 2f
                            drawLine(
                                color = if (inSelection) primary else muted,
                                start = Offset(x, centerY - halfH),
                                end = Offset(x, centerY + halfH),
                                strokeWidth = 3.dp.toPx(),
                                cap = StrokeCap.Round
                            )
                        }
                    }
                }

                RangeSlider(
                    value = range,
                    onValueChange = { updated ->
                        if (updated.endInclusive - updated.start >= 0.05f) {
                            range = updated
                        }
                    },
                    valueRange = 0f..1f
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Inicio: ${formatDuration(startMs)}", style = ExpressiveTypography.MonoTimecode)
                    Text("Fin: ${formatDuration(endMs)}", style = ExpressiveTypography.MonoTimecode)
                }

                if (lastExportedPath != null) {
                    Text(
                        text = "Guardado en: $lastExportedPath",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isExporting,
                onClick = { onExportClip(startMs, endMs) }
            ) {
                if (isExporting) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(if (isExporting) "Exportando..." else "Exportar M4A")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
