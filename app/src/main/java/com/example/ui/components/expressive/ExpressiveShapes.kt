package com.example.ui.components.expressive

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.graphics.shapes.RoundedPolygon

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