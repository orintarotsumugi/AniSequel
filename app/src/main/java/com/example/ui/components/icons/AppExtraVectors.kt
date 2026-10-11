package com.example.ui.components.icons

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
                moveTo(1.5f, 12.5f)
                lineTo(6f, 17f)
                lineTo(13.5f, 9.5f)

                // Trailing tick, offset right so the two read as a pair.
                moveTo(8.5f, 12.5f)
                lineTo(13f, 17f)
                lineTo(20.5f, 9.5f)
            }
        }.build()
    }

    /**
     * Calendar clock for next airing episode countdown.
     *
     * Calendar body with two binding rings, a header rule, and a clock face inset
     * below it. The old glyph drew an open-bottomed calendar with only a dangling
     * minute hand and no face at all, so it read as a broken square. The face and
     * both hands are what make it a countdown rather than a shape.
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

                // Binding rings above the header.
                moveTo(7.5f, 1.5f)
                verticalLineTo(5.5f)
                moveTo(16.5f, 1.5f)
                verticalLineTo(5.5f)

                // Header rule.
                moveTo(3f, 8f)
                horizontalLineTo(21f)

                // Clock face inset below the header: two half-arcs make a circle.
                moveTo(7.5f, 14.2f)
                arcTo(4.5f, 4.5f, 0f, false, true, 16.5f, 14.2f)
                arcTo(4.5f, 4.5f, 0f, false, true, 7.5f, 14.2f)

                // Minute hand up to twelve, hour hand out to four.
                moveTo(12f, 14.2f)
                lineTo(12f, 10.8f)
                moveTo(12f, 14.2f)
                lineTo(15f, 15.6f)
            }
        }.build()
    }
}
