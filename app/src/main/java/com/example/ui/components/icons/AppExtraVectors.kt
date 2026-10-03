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
     * Franchise relationship branch graph icon (connecting parent anime to sequel node).
     */
    val FranchiseBranch: ImageVector by lazy {
        ImageVector.Builder(
            name = "FranchiseBranch",
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
                moveTo(6f, 5f)
                verticalLineTo(19f)
                moveTo(6f, 12f)
                curveTo(10f, 12f, 14f, 8f, 18f, 8f)
            }
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                // Circle at start
                moveTo(6f, 3.5f)
                quadTo(7.5f, 3.5f, 7.5f, 5f)
                quadTo(7.5f, 6.5f, 6f, 6.5f)
                quadTo(4.5f, 6.5f, 4.5f, 5f)
                quadTo(4.5f, 3.5f, 6f, 3.5f)
                close()

                // Circle at branch end
                moveTo(18f, 6.5f)
                quadTo(19.5f, 6.5f, 19.5f, 8f)
                quadTo(19.5f, 9.5f, 18f, 9.5f)
                quadTo(16.5f, 9.5f, 16.5f, 8f)
                quadTo(16.5f, 6.5f, 18f, 6.5f)
                close()
            }
        }.build()
    }

    /**
     * Double checkmark badge for completed series & synced status.
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
                strokeLineWidth = 2.2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(2f, 12f)
                lineTo(7f, 17f)
                lineTo(17f, 7f)

                moveTo(10f, 12f)
                lineTo(14f, 16f)
                lineTo(22f, 8f)
            }
        }.build()
    }

    /**
     * Calendar clock for next airing episode countdown.
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
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(19f, 4f)
                horizontalLineTo(5f)
                curveTo(3.89f, 4f, 3f, 4.89f, 3f, 6f)
                verticalLineTo(20f)
                curveTo(3f, 21.1f, 3.89f, 22f, 5f, 22f)
                horizontalLineTo(12f)

                moveTo(16f, 2f)
                verticalLineTo(6f)
                moveTo(8f, 2f)
                verticalLineTo(6f)
                moveTo(3f, 10f)
                horizontalLineTo(19f)

                // Clock circle & hands
                moveTo(17f, 14f)
                verticalLineTo(18f)
                lineTo(20f, 20f)
            }
        }.build()
    }
}
