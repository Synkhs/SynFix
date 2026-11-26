package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.CornerRounding
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import androidx.graphics.shapes.star
import androidx.graphics.shapes.toPath

val SynFixShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

/**
 * M3 Expressive organic polygonal shapes powered by androidx.graphics.shapes.
 */
object SynFixPolygons {
    fun squircle(): RoundedPolygon = RoundedPolygon(
        numVertices = 4,
        rounding = CornerRounding(radius = 0.35f)
    )

    fun softOctagon(): RoundedPolygon = RoundedPolygon(
        numVertices = 8,
        rounding = CornerRounding(radius = 0.28f)
    )

    fun clover(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 6,
        innerRadius = 0.80f,
        rounding = CornerRounding(radius = 0.35f)
    )

    fun sunburst(): RoundedPolygon = RoundedPolygon.star(
        numVerticesPerRadius = 10,
        innerRadius = 0.86f,
        rounding = CornerRounding(radius = 0.25f)
    )
}

class MorphPolygonShape(
    private val morph: Morph,
    private val progress: Float,
    private val rotationDegrees: Float = 0f
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        if (rotationDegrees != 0f) {
            matrix.rotateZ(rotationDegrees)
        }
        val path = morph.toPath(progress = progress.coerceIn(0f, 1f)).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

class StateMorphPolygonShape(
    private val morph: Morph,
    private val progressProvider: () -> Float
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        val path = morph.toPath(progress = progressProvider().coerceIn(0f, 1f)).asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}

class PolygonShape(
    private val polygon: RoundedPolygon,
    private val rotationDegrees: Float = 0f
) : Shape {
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        matrix.reset()
        matrix.scale(size.width / 2f, size.height / 2f)
        matrix.translate(1f, 1f)
        if (rotationDegrees != 0f) {
            matrix.rotateZ(rotationDegrees)
        }
        val path = polygon.toPath().asComposePath()
        path.transform(matrix)
        return Outline.Generic(path)
    }
}
