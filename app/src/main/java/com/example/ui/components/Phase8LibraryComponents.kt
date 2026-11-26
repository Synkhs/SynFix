package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BubbleChart
import androidx.compose.material.icons.filled.Hearing
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.model.Song
import com.example.ui.theme.ExpressiveTypography
import kotlinx.collections.immutable.ImmutableList
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * #75 Mosaic Playlist Cover:
 * Clips up to 4 album artworks into an M3 Expressive geometric mosaic grid (zero disk files).
 */
@Composable
fun MosaicPlaylistCover(
    songs: List<Song>,
    fallbackUri: android.net.Uri?,
    contentDescription: String,
    modifier: Modifier = Modifier
) {
    val distinctArts = remember(songs, fallbackUri) {
        val list = songs.map { it.artworkUri }.distinct().take(4)
        if (list.isEmpty() && fallbackUri != null) listOf(fallbackUri) else list
    }

    if (distinctArts.size <= 1) {
        ArtworkImage(
            artworkUri = distinctArts.firstOrNull(),
            contentDescription = contentDescription,
            targetSizeDp = 56.dp,
            shape = RoundedCornerShape(16.dp),
            modifier = modifier
        )
        return
    }

    val p0 = distinctArts.getOrNull(0)
    val p1 = distinctArts.getOrNull(1) ?: p0
    val p2 = distinctArts.getOrNull(2) ?: p1
    val p3 = distinctArts.getOrNull(3) ?: p0

    Surface(
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 2.dp,
        modifier = modifier.clip(RoundedCornerShape(16.dp))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ArtworkImage(
                    artworkUri = p0,
                    contentDescription = null,
                    targetSizeDp = 32.dp,
                    shape = RoundedCornerShape(topStart = 14.dp, bottomEnd = 6.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)
                )
                ArtworkImage(
                    artworkUri = p1,
                    contentDescription = null,
                    targetSizeDp = 32.dp,
                    shape = RoundedCornerShape(topEnd = 14.dp, bottomStart = 6.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)
                )
            }
            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                ArtworkImage(
                    artworkUri = p2,
                    contentDescription = null,
                    targetSizeDp = 32.dp,
                    shape = RoundedCornerShape(bottomStart = 14.dp, topEnd = 6.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)
                )
                ArtworkImage(
                    artworkUri = p3,
                    contentDescription = null,
                    targetSizeDp = 32.dp,
                    shape = RoundedCornerShape(bottomEnd = 14.dp, topStart = 6.dp),
                    modifier = Modifier.weight(1f).fillMaxHeight().padding(0.5.dp)
                )
            }
        }
    }
}

/**
 * #51 Hold-to-Peek Scrubber Pill:
 * Appears while user holds and drags horizontally on any track row to audition any section.
 */
@Composable
fun HoldToPeekOverlayBar(
    song: Song,
    fraction: Float,
    modifier: Modifier = Modifier
) {
    val previewMs = (song.durationMs * fraction.coerceIn(0f, 1f)).toLong()
    ElevatedCard(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.inverseSurface,
            contentColor = MaterialTheme.colorScheme.inverseOnSurface
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 10.dp),
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Hearing, contentDescription = null, tint = MaterialTheme.colorScheme.inversePrimary)
                    Text(
                        text = "Peek: ${song.title}",
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${formatDuration(previewMs)} / ${formatDuration(song.durationMs)}",
                    style = ExpressiveTypography.MonoTimecode,
                    color = MaterialTheme.colorScheme.inversePrimary
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)),
                color = MaterialTheme.colorScheme.inversePrimary,
                trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.25f)
            )
        }
    }
}

private data class TagBubbleBody(
    val label: String,
    val count: Int,
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    val radius: Float,
    val colorIndex: Int
)

/**
 * #50 Physics Tag Bubble Cloud:
 * Interactive Canvas cloud capped at max 20 genre/vibe bubbles with smooth circle-circle collision physics.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhysicsTagBubbleCloudSheet(
    songs: ImmutableList<Song>,
    onSelectTag: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val tagCounts = remember(songs) {
        val map = LinkedHashMap<String, Int>()
        songs.forEach { s ->
            val genre = s.genre.trim().takeIf { it.isNotBlank() && !it.equals("Unknown", true) }
            if (genre != null) map[genre] = (map[genre] ?: 0) + 1
            s.vibeTags.split(',').map { it.trim() }.filter { it.isNotBlank() }.forEach { vibe ->
                map[vibe] = (map[vibe] ?: 0) + 1
            }
        }
        map.entries.sortedByDescending { it.value }.take(20).map { it.key to it.value }
    }

    val textMeasurer: TextMeasurer = rememberTextMeasurer()
    val paletteColors = listOf(
        MaterialTheme.colorScheme.primaryContainer,
        MaterialTheme.colorScheme.secondaryContainer,
        MaterialTheme.colorScheme.tertiaryContainer
    )
    val onPaletteColors = listOf(
        MaterialTheme.colorScheme.onPrimaryContainer,
        MaterialTheme.colorScheme.onSecondaryContainer,
        MaterialTheme.colorScheme.onTertiaryContainer
    )

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(Icons.Filled.BubbleChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Column {
                    Text("Nube Física de Géneros & Vibes", style = MaterialTheme.typography.titleLarge)
                    Text(
                        text = "Toca cualquier burbuja para filtrar tu biblioteca",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (tagCounts.isEmpty()) {
                Text(
                    "Añade canciones o etiquetas Vibe para generar la nube de burbujas.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp)
                )
            } else {
                val maxCount = (tagCounts.maxOfOrNull { it.second } ?: 1).coerceAtLeast(1)
                val bodies = remember(tagCounts) {
                    tagCounts.mapIndexed { idx, (label, count) ->
                        val angle = idx * 2.39996f // golden angle spiral
                        val dist = 35f + sqrt(idx.toFloat()) * 42f
                        val ratio = (count.toFloat() / maxCount.toFloat()).coerceIn(0.2f, 1f)
                        TagBubbleBody(
                            label = label,
                            count = count,
                            x = 340f + cos(angle) * dist,
                            y = 260f + sin(angle) * dist,
                            vx = 0f,
                            vy = 0f,
                            radius = 52f + ratio * 42f,
                            colorIndex = idx % 3
                        )
                    }.toMutableList()
                }

                var frameTick by remember { mutableIntStateOf(0) }
                LaunchedEffect(bodies) {
                    while (true) {
                        withFrameNanos {
                            val centerX = 340f
                            val centerY = 260f
                            for (i in bodies.indices) {
                                val a = bodies[i]
                                // Soft gravity pull toward center
                                a.vx += (centerX - a.x) * 0.004f
                                a.vy += (centerY - a.y) * 0.004f
                                // Circle-circle soft repulsion
                                for (j in i + 1 until bodies.size) {
                                    val b = bodies[j]
                                    val dx = b.x - a.x
                                    val dy = b.y - a.y
                                    val dist = hypot(dx, dy).coerceAtLeast(1f)
                                    val minDist = a.radius + b.radius + 10f
                                    if (dist < minDist) {
                                        val push = (minDist - dist) * 0.14f
                                        val nx = dx / dist
                                        val ny = dy / dist
                                        a.vx -= nx * push
                                        a.vy -= ny * push
                                        b.vx += nx * push
                                        b.vy += ny * push
                                    }
                                }
                                a.vx *= 0.84f
                                a.vy *= 0.84f
                                a.x = (a.x + a.vx).coerceIn(a.radius + 8f, 680f - a.radius)
                                a.y = (a.y + a.vy).coerceIn(a.radius + 8f, 520f - a.radius)
                            }
                            frameTick++
                        }
                    }
                }

                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .pointerInput(bodies) {
                            detectTapGestures { tap ->
                                val scaleX = 680f / size.width.toFloat()
                                val scaleY = 520f / size.height.toFloat()
                                val lx = tap.x * scaleX
                                val ly = tap.y * scaleY
                                val hit = bodies.firstOrNull { b ->
                                    hypot(lx - b.x, ly - b.y) <= b.radius
                                }
                                if (hit != null) {
                                    onSelectTag(hit.label)
                                    onDismiss()
                                }
                            }
                        }
                ) {
                    @Suppress("UNUSED_EXPRESSION")
                    frameTick
                    val sx = size.width / 680f
                    val sy = size.height / 520f
                    val scale = minOf(sx, sy)
                    val offsetX = (size.width - 680f * scale) / 2f
                    val offsetY = (size.height - 520f * scale) / 2f

                    bodies.forEach { b ->
                        val cx = offsetX + b.x * scale
                        val cy = offsetY + b.y * scale
                        val r = b.radius * scale
                        drawCircle(
                            color = paletteColors[b.colorIndex],
                            radius = r,
                            center = Offset(cx, cy)
                        )
                        val textLayout = textMeasurer.measure(
                            text = "${b.label}\n(${b.count})",
                            style = TextStyle(
                                fontSize = (11f * (r / 54f).coerceIn(0.85f, 1.25f)).sp,
                                fontWeight = FontWeight.SemiBold,
                                color = onPaletteColors[b.colorIndex]
                            ),
                            maxLines = 2
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = Offset(
                                cx - textLayout.size.width / 2f,
                                cy - textLayout.size.height / 2f
                            )
                        )
                    }
                }
            }
        }
    }
}
