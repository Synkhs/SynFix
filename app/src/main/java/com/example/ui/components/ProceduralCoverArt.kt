package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.core.graphics.ColorUtils
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath
import kotlin.math.abs

/**
 * #18 Procedural Cover Art:
 * Deterministic geometric Compose Canvas cover art with RoundedPolygon shapes
 * and harmonious hash-derived palettes (0 MB on disk/RAM) for tracks lacking embedded artwork.
 */
@Composable
fun ProceduralCoverArt(
    seedKey: String,
    modifier: Modifier = Modifier
) {
    val spec = remember(seedKey) { buildProceduralArtSpec(seedKey) }
    val matrix = remember { Matrix() }

    Canvas(modifier = modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val minDim = minOf(w, h)

        // Rich multi-stop backdrop gradient
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(spec.bgStart, spec.bgEnd),
                start = Offset.Zero,
                end = Offset(w, h)
            )
        )

        // Radial luminous glow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(spec.accent.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(w * spec.glowX, h * spec.glowY),
                radius = minDim * 0.78f
            ),
            radius = minDim * 0.78f,
            center = Offset(w * spec.glowX, h * spec.glowY)
        )

        // Outer organic polygon
        matrix.reset()
        matrix.scale(w * 0.36f, h * 0.36f)
        matrix.translate(w / (w * 0.36f * 2f), h / (h * 0.36f * 2f))
        matrix.rotateZ(spec.rotationDeg)
        val outerPath = spec.outerPolygon.toPath().asComposePath().apply { transform(matrix) }
        drawPath(
            path = outerPath,
            color = spec.accent.copy(alpha = 0.28f)
        )
        drawPath(
            path = outerPath,
            color = spec.highlight.copy(alpha = 0.72f),
            style = Stroke(width = (minDim * 0.025f).coerceAtLeast(1.5f))
        )

        // Inner contrasting polygon
        matrix.reset()
        matrix.scale(w * 0.20f, h * 0.20f)
        matrix.translate(w / (w * 0.20f * 2f), h / (h * 0.20f * 2f))
        matrix.rotateZ(-spec.rotationDeg * 0.7f)
        val innerPath = spec.innerPolygon.toPath().asComposePath().apply { transform(matrix) }
        drawPath(
            path = innerPath,
            color = spec.highlight.copy(alpha = 0.88f)
        )

        // Subtle center vinyl dot
        drawCircle(
            color = spec.bgStart,
            radius = minDim * 0.055f,
            center = Offset(w / 2f, h / 2f)
        )
    }
}

private data class ProceduralArtSpec(
    val bgStart: Color,
    val bgEnd: Color,
    val accent: Color,
    val highlight: Color,
    val glowX: Float,
    val glowY: Float,
    val rotationDeg: Float,
    val outerPolygon: RoundedPolygon,
    val innerPolygon: RoundedPolygon
)

private fun buildProceduralArtSpec(seedKey: String): ProceduralArtSpec {
    val h = abs(seedKey.hashCode())
    val hue = (h % 360).toFloat()
    val hue2 = (hue + 48f + (h % 55)) % 360f
    val hue3 = (hue + 150f) % 360f

    fun hsl(deg: Float, s: Float, l: Float): Color =
        Color(ColorUtils.HSLToColor(floatArrayOf(((deg % 360f) + 360f) % 360f, s, l)))

    val bgStart = hsl(hue, 0.52f, 0.16f)
    val bgEnd = hsl(hue2, 0.58f, 0.26f)
    val accent = hsl(hue2, 0.82f, 0.62f)
    val highlight = hsl(hue3, 0.86f, 0.76f)

    val outerVertices = 5 + (h % 5) // 5..9
    val outer = if (h % 2 == 0) {
        RoundedPolygon.star(
            numVerticesPerRadius = outerVertices,
            innerRadius = 0.72f + ((h % 15) / 100f),
            rounding = CornerRounding(0.28f)
        )
    } else {
        RoundedPolygon(
            numVertices = outerVertices,
            rounding = CornerRounding(0.32f)
        )
    }

    val inner = RoundedPolygon(
        numVertices = 3 + (h % 4),
        rounding = CornerRounding(0.36f)
    )

    return ProceduralArtSpec(
        bgStart = bgStart,
        bgEnd = bgEnd,
        accent = accent,
        highlight = highlight,
        glowX = 0.25f + ((h % 50) / 100f),
        glowY = 0.25f + (((h / 7) % 50) / 100f),
        rotationDeg = (h % 360).toFloat(),
        outerPolygon = outer,
        innerPolygon = inner
    )
}
