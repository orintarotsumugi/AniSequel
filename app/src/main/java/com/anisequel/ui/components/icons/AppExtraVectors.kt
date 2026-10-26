package com.anisequel.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Additional specialized SVG vector graphics for AniSequel.
 * Provides custom vector assets without external image files or emoji dependencies.
 */
object AppExtraVectors {

    /**
     * The GitHub mark, for the link to this app's own source.
     *
     * The one glyph in this file that is not drawn for AniSequel: the Octocat is
     * GitHub's own mark, and a link to GitHub that is not the Octocat is a link
     * nobody recognises. Drawn as an `ImageVector` like everything else - the
     * alternative was a PNG, which is the one thing the app does not ship for UI.
     *
     * The path is the published 24x24 mark, converted from its SVG path data to
     * the `PathBuilder` DSL by expanding the implicit coordinate repeats. It is
     * filled as one shape with `NonZero`, which is what makes the body solid and
     * the arms and tail read as silhouette rather than as a dozen separate blobs.
     */
    val GitHub: ImageVector by lazy {
        ImageVector.Builder(
            name = "GitHub",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 0.297f)
                curveToRelative(-6.63f, 0f, -12f, 5.373f, -12f, 12f)
                curveToRelative(0f, 5.303f, 3.438f, 9.8f, 8.205f, 11.385f)
                curveToRelative(0.6f, 0.113f, 0.82f, -0.258f, 0.82f, -0.577f)
                curveToRelative(0f, -0.285f, -0.01f, -1.04f, -0.015f, -2.04f)
                curveToRelative(-3.338f, 0.724f, -4.042f, -1.61f, -4.042f, -1.61f)
                curveTo(4.422f, 18.07f, 3.633f, 17.7f, 3.633f, 17.7f)
                curveToRelative(-1.087f, -0.744f, 0.084f, -0.729f, 0.084f, -0.729f)
                curveToRelative(1.205f, 0.084f, 1.838f, 1.236f, 1.838f, 1.236f)
                curveToRelative(1.07f, 1.835f, 2.809f, 1.305f, 3.495f, 0.998f)
                curveToRelative(0.108f, -0.776f, 0.417f, -1.305f, 0.76f, -1.605f)
                curveToRelative(-2.665f, -0.3f, -5.466f, -1.332f, -5.466f, -5.93f)
                curveToRelative(0f, -1.31f, 0.465f, -2.38f, 1.235f, -3.22f)
                curveToRelative(-0.135f, -0.303f, -0.54f, -1.523f, 0.105f, -3.176f)
                curveToRelative(0f, 0f, 1.005f, -0.322f, 3.3f, 1.23f)
                curveToRelative(0.96f, -0.267f, 1.98f, -0.399f, 3f, -0.405f)
                curveToRelative(1.02f, 0.006f, 2.04f, 0.138f, 3f, 0.405f)
                curveToRelative(2.28f, -1.552f, 3.285f, -1.23f, 3.285f, -1.23f)
                curveToRelative(0.645f, 1.653f, 0.24f, 2.873f, 0.12f, 3.176f)
                curveToRelative(0.765f, 0.84f, 1.23f, 1.91f, 1.23f, 3.22f)
                curveToRelative(0f, 4.61f, -2.805f, 5.625f, -5.475f, 5.92f)
                curveToRelative(0.42f, 0.36f, 0.81f, 1.096f, 0.81f, 2.22f)
                curveToRelative(0f, 1.606f, -0.015f, 2.896f, -0.015f, 3.286f)
                curveToRelative(0f, 0.315f, 0.21f, 0.69f, 0.825f, 0.57f)
                curveTo(20.565f, 22.092f, 24f, 17.592f, 24f, 12.297f)
                curveToRelative(0f, -6.627f, -5.373f, -12f, -12f, -12f)
            }
        }.build()
    }

    /**
     * Franchise relationship branch graph: one parent anime splitting into two
     * sequel/prequel nodes.
     *
     * The old glyph was a bare trunk with a single stub curve and two dots, which
     * read as a squiggle rather than a graph. This draws the relationship the
     * detail screen actually shows - a parent node entering a horizontal bus that
     * fans out to two children - so "watched -> has a sequel" is legible at a
     * glance. The connector is stroked while the nodes are filled, because the
     * round node markers must stay solid dots and cannot be faked with a line.
     */
    val FranchiseBranch: ImageVector by lazy {
        ImageVector.Builder(
            name = "FranchiseBranch",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // Connector: parent down to a bus, then out and down to both children.
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(12f, 5.8f)
                verticalLineTo(11f)
                moveTo(5f, 11f)
                horizontalLineTo(19f)
                moveTo(5f, 11f)
                verticalLineTo(16.7f)
                moveTo(19f, 11f)
                verticalLineTo(16.7f)
            }
            // Nodes: one parent over two children. Each is two half-arcs so the
            // dots are true circles rather than the superellipses the old quadTo
            // approximation produced.
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                // Parent node.
                moveTo(10.2f, 4f)
                arcTo(1.8f, 1.8f, 0f, false, true, 13.8f, 4f)
                arcTo(1.8f, 1.8f, 0f, false, true, 10.2f, 4f)
                close()

                // Left child node.
                moveTo(3.2f, 18.5f)
                arcTo(1.8f, 1.8f, 0f, false, true, 6.8f, 18.5f)
                arcTo(1.8f, 1.8f, 0f, false, true, 3.2f, 18.5f)
                close()

                // Right child node.
                moveTo(17.2f, 18.5f)
                arcTo(1.8f, 1.8f, 0f, false, true, 20.8f, 18.5f)
                arcTo(1.8f, 1.8f, 0f, false, true, 17.2f, 18.5f)
                close()
            }
        }.build()
    }

    /**
     * Double checkmark badge for completed series & synced status.
     *
     * Two separate ticks, the second shifted right so it overlaps the tail of the
     * first - the "both stages done" reading. The old glyph was one wide tick with
     * a second stroke buried inside it, which merged into a single blob at the
     * 16-20dp the badges render at. Keeping the ticks parallel and offset leaves a
     * visible notch between them at any size.
     *
     * Both ticks moved right 1 unit and up 1.5. The pair had been drawn around
     * x = 1.5..20.5, y = 9.5..17, which put the ink's centre of mass at
     * (11.1, 13.7) - the badge hung low and left in its 24-unit frame, and its
     * round caps came within 0.6 units of the left edge while 2.4 sat empty on
     * the right. It now sits at (12.1, 12.2), with an even 1.5 either side.
     */
    val CheckDouble: ImageVector by lazy {
        ImageVector.Builder(
            name = "CheckDouble",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Leading tick.
                moveTo(2.5f, 11f)
                lineTo(7f, 15.5f)
                lineTo(14.5f, 8f)

                // Trailing tick, offset right so the two read as a pair.
                moveTo(9.5f, 11f)
                lineTo(14f, 15.5f)
                lineTo(21.5f, 8f)
            }
        }.build()
    }

    /**
     * Calendar clock for next airing episode countdown.
     *
     * Calendar body with two binding rings and a clock face set into it. The
     * face and both hands are what make it a countdown rather than a shape.
     *
     * There is deliberately no header rule. The old glyph drew one *and* a
     * clock below it, and the two could not both fit inside a 24-unit frame:
     * the ring's stroke overlapped the rule by 0.1 units and the frame's bottom
     * edge by 0.5, and the hands were 3.4 and 3.3 units long inside a 3.6-unit
     * bore. At the 13dp this icon renders at, the lower half was three touching
     * strokes and one blob. Dropping the rule leaves the interior to the clock
     * alone: the face is now 11.4 units across with 1.4 units of clear air above
     * and below it, and the hands stop half a unit short of the ring instead of
     * merging into it. The two binding rings still carry the calendar.
     */
    val CalendarClock: ImageVector by lazy {
        ImageVector.Builder(
            name = "CalendarClock",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Calendar body with rounded corners.
                moveTo(5f, 4f)
                horizontalLineTo(19f)
                curveTo(20.1f, 4f, 21f, 4.9f, 21f, 6f)
                verticalLineTo(18f)
                curveTo(21f, 19.1f, 20.1f, 20f, 19f, 20f)
                horizontalLineTo(5f)
                curveTo(3.9f, 20f, 3f, 19.1f, 3f, 18f)
                verticalLineTo(6f)
                curveTo(3f, 4.9f, 3.9f, 4f, 5f, 4f)
                close()

                // Binding rings above the top edge.
                moveTo(7.5f, 1.5f)
                verticalLineTo(5.5f)
                moveTo(16.5f, 1.5f)
                verticalLineTo(5.5f)

                // Clock face centred in what is left of the body: two half-arcs
                // make a circle. 4.8 is the largest radius that still leaves an
                // even gap between the ring's outer edge and both the top and
                // the bottom of the frame.
                moveTo(7.2f, 12f)
                arcTo(4.8f, 4.8f, 0f, false, true, 16.8f, 12f)
                arcTo(4.8f, 4.8f, 0f, false, true, 7.2f, 12f)

                // Minute hand up to twelve, hour hand out to four. Both stay
                // inside the ring's 3.9-unit bore with half a unit to spare.
                moveTo(12f, 12f)
                lineTo(12f, 8.6f)
                moveTo(12f, 12f)
                lineTo(14.8f, 13.7f)
            }
        }.build()
    }

    /**
     * Three blades of grass over a ground line, for the "go touch grass"
     * recovery action.
     *
     * The ground stops short of both edges deliberately: a full-width bar at
     * icon size reads as an underline beneath the blades, which is the one thing
     * a lawn must not look like. The middle blade is straight and the outer two
     * lean away from it, so the tuft still reads at 16dp where individual curves
     * start to blur.
     */
    val GrassTuft: ImageVector by lazy {
        ImageVector.Builder(
            name = "GrassTuft",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round
            ) {
                // Ground.
                moveTo(3.5f, 20f)
                lineTo(20.5f, 20f)

                // Left blade, leaning out.
                moveTo(8f, 20f)
                quadTo(6.5f, 14.5f, 9.5f, 10.5f)

                // Centre blade, upright.
                moveTo(12f, 20f)
                quadTo(12.5f, 13f, 12f, 8.5f)

                // Right blade, leaning out.
                moveTo(16f, 20f)
                quadTo(17.5f, 14.5f, 14.5f, 10.5f)
            }
        }.build()
    }

    /**
     * Three books on a shelf, for the Library destination.
     *
     * The tab used to borrow the Stats tab's stopwatch (`Icons.Default.Timer`),
     * which read as "time spent" rather than "the list I keep" and made Library and
     * the Stats chart share a glyph for two unrelated destinations. Spines are
     * filled and the shelf is stroked, which is what keeps them legible at 22dp:
     * three outlines at that size merge into a smudge.
     *
     * The middle book is taller and the third leans, so the silhouette is not three
     * identical bars - the shape that reads as a bar chart of three values. Gaps are
     * real space between subpaths rather than cut-outs, so no winding trick is
     * needed here.
     */
    val LibraryShelf: ImageVector by lazy {
        ImageVector.Builder(
            name = "LibraryShelf",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                // Book one, upright.
                moveTo(4f, 4.5f)
                lineTo(8.6f, 4.5f)
                lineTo(8.6f, 20f)
                lineTo(4f, 20f)
                close()

                // Book two, taller.
                moveTo(9.8f, 2.5f)
                lineTo(14.4f, 2.5f)
                lineTo(14.4f, 20f)
                lineTo(9.8f, 20f)
                close()

                // Book three, leaning against the others.
                moveTo(15.8f, 5.4f)
                lineTo(20f, 6.6f)
                lineTo(18.5f, 20f)
                lineTo(15.2f, 20f)
                close()
            }

            // The shelf, inset at both ends so it reads as a surface the books
            // stand on rather than as a rule under the icon.
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round
            ) {
                moveTo(2.5f, 21f)
                lineTo(21.5f, 21f)
            }
        }.build()
    }
}
