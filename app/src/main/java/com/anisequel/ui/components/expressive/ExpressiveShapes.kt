package com.anisequel.ui.components.expressive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.material3.toPath
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon
import kotlin.math.max
import kotlin.math.min

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

    // --- The corner radius scale ------------------------------------------------
    //
    // These are *numbers*, not shapes: a corner radius in dp to hand to
    // `RoundedCornerShape(...)`. They exist because the app had no scale at all -
    // sixty-four inlined literals across fourteen different radii (5, 6, 7, 8, 9,
    // 10, 12, 14, 16, 18, 20, 24, 28, 50). Corners two dp apart read as two
    // unrelated shapes rather than as neighbours in a family, and that is what
    // makes unrelated screens feel like they were drawn by different people.
    //
    // **These are the theme's values, not Material's.** The app already had a
    // deliberate shape scale in `ExpressiveTheme.kt` - 10 / 14 / 20 / 28 / 36,
    // rounder than M3's 4 / 8 / 12 / 16 / 28 on purpose, so cards read as curved
    // panels rather than hard rectangles. The literals simply had not been moved
    // onto it. So these four are the first four steps of *that* scale, named, and
    // [ExpressiveTheme.kt] is the single place the fifth (36) is applied.
    //
    // Naming them here is what stops the drift coming back: a new literal cannot
    // be spelled next to an existing one without the mismatch being visible.
    //
    // `RoundedCornerShape(percent = 50)` is deliberately absent - that is [pill],
    // and a percentage is not a radius.

    /** 10dp — the theme's `extraSmall`. Chips, badges, dense list rows. */
    val ExtraSmallCorner = 10.dp

    /** 14dp — the theme's `small`. Small cards and controls. */
    val SmallCorner = 14.dp

    /** 20dp — the theme's `medium`. The default: cards, sheets, list items. */
    val MediumCorner = 20.dp

    /** 28dp — the theme's `large`. Panels and prominent surfaces. */
    val LargeCorner = 28.dp

    /**
     * 24dp — half of a 48dp button, so a control at this radius is a true
     * capsule by measurement.
     *
     * A number rather than [pill] because it is compared against a `Dp` in
     * `animateDpAsState` and [pill] is a `Shape`, which cannot be added to one.
     */
    val PillCorner = 24.dp
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
 * Writes [morph]'s outline at [progress] into [target], fitted to [size].
 *
 * Morphing is what makes the empty-state artwork "breathe" between two of
 * Material's iconic shapes. Doing this with an alpha crossfade would produce two
 * translucent shapes overlapping; a morph produces one shape that is between.
 *
 * The two endpoints are deliberately from Material's own loading-indicator
 * sequence (a soft burst and the nine-sided cookie) rather than any two shapes
 * with a pleasing silhouette. `Morph` matches curves by feature proximity, and
 * a 9-lobed cookie paired with an 8-petal flower made the intermediate frames
 * fold through themselves - the "torn clover" was not only the frozen frame the
 * old implementation shipped, it was also a pair whose vertices had no stable
 * correspondence. Adjacent shapes from the library's sequence are the pair the
 * Material team already animates between, and their curves map one to one.
 *
 * The result is centred and scaled to the *smaller* dimension, because a
 * `RoundedPolygon` is authored on a square perimeter: stretching it across a
 * wide/short box gives the ellipse surprise [ExpressiveShapes.pill] exists to
 * keep out of the app. That is also why this is only used for the orb's fixed
 * square. [scaleFraction] leaves a margin when the caller wants to draw a halo
 * behind the silhouette; without it the halo would be clipped by the orb's
 * bounds.
 *
 * ## Why this writes into a caller's [Path] instead of being a `Shape`
 *
 * It was a `Shape` (`MorphShape`), applied with `Modifier.clip(...)`, and that
 * is the bug this replaces: the silhouette never actually morphed. `clip` builds
 * a `graphicsLayer`, and a layer's outline is computed when the layer is
 * configured - not per frame. A `Shape` whose only animated read happens inside
 * `createOutline` therefore hands the layer one frozen outline, so the orb sat
 * on an arbitrary shape from the first frame and only jumped when something
 * else invalidated the layer. It looked like a torn, lopsided clover because it
 * *was* one frame of the morph, kept forever.
 *
 * Drawing the path from the draw phase instead makes the read happen where
 * Compose expects an animation to be read: only the draw pass is invalidated,
 * the silhouette is rebuilt every frame, and no shape instance has to guess
 * when it changed. [target] is rewound and rewritten in place, so callers can
 * remember one path and one matrix for the life of the composable - the same
 * allocation-free pattern `RoundedPolygon.toShape()` uses internally.
 *
 * The caller must invoke this from a draw scope (`Modifier.drawBehind`), which
 * is what makes reusing the path safe: the draw commands are recorded before
 * the next call rewinds it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
fun Morph.writeSilhouette(
    progress: Float,
    size: Size,
    target: Path,
    matrix: Matrix,
    bounds: FloatArray = FloatArray(4),
    scaleFraction: Float = 1f
): Path {
    target.rewind()
    if (size.width <= 0f || size.height <= 0f) return target

    toPath(progress, target)

    // The bounds cover both ends of the morph, not this frame, so the silhouette
    // does not breathe in size while it changes shape. The buffer is a parameter
    // rather than a local so a composable drawing this every frame can remember
    // one instead of allocating four floats per frame.
    calculateBounds(bounds)
    val boundsLeft = bounds[0]
    val boundsTop = bounds[1]
    val boundsWidth = (bounds[2] - bounds[0]).coerceAtLeast(0.0001f)
    val boundsHeight = (bounds[3] - bounds[1]).coerceAtLeast(0.0001f)
    val scale = min(size.width, size.height) / max(boundsWidth, boundsHeight) *
        scaleFraction.coerceIn(0f, 1f)

    matrix.reset()
    matrix.translate(size.width / 2f, size.height / 2f)
    matrix.scale(scale, scale)
    matrix.translate(-(boundsLeft + boundsWidth / 2f), -(boundsTop + boundsHeight / 2f))
    target.transform(matrix)
    return target
}