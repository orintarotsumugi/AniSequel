package com.anisequel.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle

/**
 * The motion style glyphs, in one family, and the only place a style becomes a mark.
 *
 * ## Why this file rather than `AppCustomVectors.kt`
 *
 * Custom vectors live in [AppCustomVectors] and [AppExtraVectors]; both were already
 * past the 700-line ceiling when this family was drawn, and these four glyphs share
 * one authoring rule (a 2.2dp stroke, a 3.2dp filled dot, one idea each) that the two
 * catch-all files do not. Splitting by subject rather than by size is the point.
 *
 * ## Why every style needs its own glyph
 *
 * The picker drew five styles with three icons: Snappy reused Smooth's waves and
 * Floaty reused Chill's bolt. Two of five choices therefore wore another choice's
 * face, and the face is the only part of the picker that is not a word - a reader
 * deciding between "Snappy" and "Chill" was shown the same picture for both halves
 * of the question. The set below is one per style; [motionGlyph] is the mapping the
 * picker, the sample and the test all read, so a sixth style cannot be added without
 * being given a mark.
 *
 * Each mark is the style's *behaviour*, not a mood:
 *  - Instant lands against a wall and does not ease;
 *  - Snappy is the existing bolt, the one glyph in the set already about speed;
 *  - Chill is a spring: it passes the rest line twice before settling;
 *  - Floaty is a drift - the same dot, repeated, getting further away.
 */
object MotionVectors {

    /**
     * Instant: a straight travel line, and a dot already stopped against a wall.
     *
     * The old Instant glyph was Material's `RestartAlt`, an arrow that means "undo" on
     * a screen whose subject is motion. A line with no curve is the absence of easing,
     * and the wall is where the movement ends without a settle - which is exactly what
     * `snap()` does to every token on this style.
     */
    val MotionSnap: ImageVector by lazy {
        ImageVector.Builder(
            name = "MotionSnap",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // A perfectly straight line: no easing got it here.
                moveTo(2.5f, 12f)
                lineTo(11.5f, 12f)
                // The wall it stopped against.
                moveTo(21.2f, 5.5f)
                lineTo(21.2f, 18.5f)
            }
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                circle(cx = 16.2f, cy = 12f, radius = 3.2f)
            }
        }.build()
    }

    /**
     * Chill: a damped spring settling on its rest line.
     *
     * Three arcs, not one: the first passes far above the rest line, the second dips
     * below it, and the third is a small hop that lands. That is what a spring with a
     * little damping does, and it is the one shape that tells Chill apart from Snappy's
     * bolt at 18dp - both are "energetic", but only one overshoots.
     */
    val MotionSpring: ImageVector by lazy {
        ImageVector.Builder(
            name = "MotionSpring",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.5f, 13.5f)
                // Overshoot above rest, then below it, then the settle.
                curveTo(4.2f, 3.2f, 7.8f, 3.2f, 9.4f, 13.5f)
                curveTo(10.6f, 20.2f, 12.9f, 20.2f, 13.9f, 13.5f)
                curveTo(14.6f, 10f, 15.9f, 10f, 16.5f, 13.5f)
            }
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                circle(cx = 19.7f, cy = 13.5f, radius = 2.4f)
            }
        }.build()
    }

    /**
     * Floaty: the same dot, three times, further apart.
     *
     * The other four styles have a line because they have a path. Floaty is the one
     * that drifts, so it is drawn as the trail itself: a dot and the two it left
     * behind, shrinking as they fall away. Nothing in it is angular - there is no
     * curve because the movement is too slow to see a shape in.
     */
    val MotionDrift: ImageVector by lazy {
        ImageVector.Builder(
            name = "MotionDrift",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                circle(cx = 17.2f, cy = 6.8f, radius = 2.9f)
                circle(cx = 12.2f, cy = 11.9f, radius = 2f)
                circle(cx = 8.2f, cy = 16.8f, radius = 1.25f)
            }
        }.build()
    }

    /**
     * The Motion section's own header mark: two travel lines and a dot.
     *
     * Deliberately not one of the five styles. The section used to borrow Smooth's
     * waves, so the header of "how much the app animates" was a picture of one of its
     * five answers - the same trap Snappy and Floaty were in.
     */
    val MotionTrail: ImageVector by lazy {
        ImageVector.Builder(
            name = "MotionTrail",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2.5f, 8f)
                lineTo(12.5f, 8f)
                moveTo(2.5f, 16f)
                lineTo(16.5f, 16f)
            }
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                circle(cx = 19f, cy = 12f, radius = 2.7f)
            }
        }.build()
    }
}

/**
 * The mark for [style], defined once.
 *
 * The picker draws it, the preview puts it on the press capsule, and
 * `VectorIconsTest` asserts the five are distinct - so a style cannot be added
 * without a glyph, and two styles cannot quietly share one again.
 */
fun motionGlyph(style: MotionStyle): ImageVector = when (style) {
    MotionStyle.INSTANT -> MotionVectors.MotionSnap
    MotionStyle.SMOOTH -> AppCustomVectors.MotionWaves
    MotionStyle.SNAPPY -> AppCustomVectors.MotionBolt
    MotionStyle.CHILL -> MotionVectors.MotionSpring
    MotionStyle.FLOATY -> MotionVectors.MotionDrift
}

/**
 * One filled circle, as two cubics.
 *
 * `PathBuilder` has no primitive for a circle, and this family draws five of them at
 * two radii; spelling out six `curveTo` coordinates per dot was how the first draft
 * ended up with two dots at slightly different sizes. The handle length is the usual
 * 1.12 x radius, which is close enough to a true circle that no edge shows at 18dp.
 */
private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    val handle = radius * 1.12f
    moveTo(cx, cy - radius)
    curveTo(cx + handle, cy - radius, cx + handle, cy + radius, cx, cy + radius)
    curveTo(cx - handle, cy + radius, cx - handle, cy - radius, cx, cy - radius)
    close()
}
