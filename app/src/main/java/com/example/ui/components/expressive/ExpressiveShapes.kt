package com.example.ui.components.expressive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.max

/**
 * Shape tokens and utilities for Material 3 Expressive surfaces.
 *
 * Two families live here, and which one a shape comes from matters:
 *
 *  - The [RoundedPolygon] token below is *decorative*. It is authored on a
 *    square perimeter and is only used on an element that is itself square
 *    (`ExpressiveEmptyOrb`, a fixed `size(80.dp)`).
 *  - The capsule tokens are *structural* - they outline tabs and segmented
 *    controls, which are wide and short and change size with their content.
 *
 * Those two must not be mixed. See [pill].
 *
 * ## Why there is only one polygon token
 *
 * There used to be five - `orb`, `burst`, `diamond`, `cookie4` and `flower` -
 * and four of them had no call sites anywhere in the app. Every one is a `get()`
 * property that calls straight into `MaterialShapes`, so each one is a live
 * reference the shrinker has to consider reachable, and each one is a shape
 * somebody could reach for by name without ever learning the square-perimeter
 * caveat above. The token set is now the smallest set the UI actually draws
 * with; adding one back means adding a call site, not just a property.
 */
object ExpressiveShapes {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val orb: RoundedPolygon get() = MaterialShapes.Cookie9Sided

    /**
     * A true capsule: straight sides, semicircular ends, at any size.
     *
     * This used to be `MaterialShapes.Pill` converted through
     * `RoundedPolygon.toShape()`, which is what produced the eggs and blobs this
     * replaced. `RoundedPolygon` is authored on a square perimeter, and
     * `toShape()` maps that square onto whatever `Size` the layout happens to
     * hand it - scaling x and y independently. On a tab measured 120x44 the
     * "pill" came out as a 120x44 *ellipse*, so the selected tab was an oval
     * rather than a capsule.
     *
     * `pillSoft` was worse still: it pointed at `Cookie7Sided`, a seven-sided
     * cookie rather than a pill at all, which is the blob the selected segment
     * of the theme picker was drawn as.
     *
     * `RoundedCornerShape(percent = 50)` recomputes the corner radius as
     * `min(width, height) / 2` on every draw, so it is a correct capsule at any
     * size and any aspect ratio - which matters because these elements are
     * measured from their label and change width with it and with font scale.
     *
     * Percent rather than `RoundedCornerShape(50.dp)`: a fixed radius only
     * reads as a capsule while the box is exactly 100dp tall, and quietly stops
     * being one the moment the height or the text size changes.
     */
    val pill: Shape = RoundedCornerShape(percent = 50)

    /**
     * The same capsule as [pill].
     *
     * Kept as a separate name because the call sites mean different things by
     * it - the outer track of a segmented bar versus its selected segment - and
     * the two are free to diverge later. They are identical today because both
     * want a capsule, and the bug was having them be anything else.
     */
    val pillSoft: Shape = RoundedCornerShape(percent = 50)
}

/**
 * Converts a decorative [RoundedPolygon] to a Compose [Shape].
 *
 * Only for square, fixed-size artwork. The result is stretched to fit whatever
 * bounds it is drawn into, so it must not be used to outline a control whose
 * aspect ratio varies - use [ExpressiveShapes.pill] for those.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveShape(polygon: RoundedPolygon) = polygon.toShape()

/**
 * A [Shape] that interpolates between two [MaterialShapes] polygons over [progress].
 *
 * Morphing is what makes the empty-state artwork "breathe" between two of
 * Material's 35 iconic shapes - two polygons whose outlines map vertex to
 * vertex, so the intermediate silhouettes stay simple. Doing this with a plain
 * alpha crossfade produces two translucent shapes overlapping; a morph produces
 * one shape that is between.
 *
 * [progress] is sampled once per [createOutline] call, so driving it from an
 * animated state re-evaluates the clip each frame. The polygon that describes
 * the outline is a [Morph]; its bounds scale/translate to whatever size the
 * caller hands it, which is why, like [expressiveShape], this is only
 * appropriate for a fixed square - a Morph is a unit polygon, and stretching it
 * across a wide/short box would produce the same generic-outline surprise the
 * pill token replaced.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
class MorphShape(
    private val morph: Morph,
    private val progress: Float
) : Shape {
    private var path = Path()
    private val matrix = Matrix()

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        if (size.width <= 0f || size.height <= 0f) {
            return Outline.Rectangle(androidx.compose.ui.geometry.Rect.Zero)
        }
        path.rewind()
        morph.toPath(progress, path)
        val b = morph.calculateBounds()
        val boundsLeft = b[0]
        val boundsTop = b[1]
        val boundsWidth = (b[2] - b[0]).coerceAtLeast(0.0001f)
        val boundsHeight = (b[3] - b[1]).coerceAtLeast(0.0001f)
        val maxDimension = max(boundsWidth, boundsHeight)
        val scale = kotlin.math.min(size.width, size.height) / maxDimension
        matrix.reset()
        matrix.translate(size.width / 2f, size.height / 2f)
        matrix.scale(scale, scale)
        matrix.translate(-(boundsLeft + boundsWidth / 2f), -(boundsTop + boundsHeight / 2f))
        path.transform(matrix)
        return Outline.Generic(path)
    }
}