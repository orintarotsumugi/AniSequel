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
 * Custom vector graphics designed specifically for AniSequel.
 * Provides crisp SVG vectors without emoji dependencies or external assets.
 */
object AppCustomVectors {

    /**
     * Anime sparkle / four-pointed star for ratings, top scores, and highlights.
     */
    val AnimeSparkle: ImageVector by lazy {
        ImageVector.Builder(
            name = "AnimeSparkle",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(12f, 2f)
                quadTo(12f, 10f, 4f, 12f)
                quadTo(12f, 14f, 12f, 22f)
                quadTo(12f, 14f, 20f, 12f)
                quadTo(12f, 10f, 12f, 2f)
                close()
            }
        }.build()
    }

    /**
     * Studio / Production building vector icon.
     */
    val StudioBuilding: ImageVector by lazy {
        ImageVector.Builder(
            name = "StudioBuilding",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(4f, 21f)
                lineTo(4f, 3f)
                curveTo(4f, 2.45f, 4.45f, 2f, 5f, 2f)
                lineTo(13f, 2f)
                curveTo(13.55f, 2f, 14f, 2.45f, 14f, 3f)
                lineTo(14f, 7f)
                lineTo(19f, 7f)
                curveTo(19.55f, 7f, 20f, 7.45f, 20f, 8f)
                lineTo(20f, 21f)
                lineTo(22f, 21f)
                lineTo(22f, 23f)
                lineTo(2f, 23f)
                lineTo(2f, 21f)
                lineTo(4f, 21f)
                close()

                // Windows in main tower
                moveTo(6f, 5f)
                lineTo(8f, 5f)
                lineTo(8f, 7f)
                lineTo(6f, 7f)
                close()

                moveTo(10f, 5f)
                lineTo(12f, 5f)
                lineTo(12f, 7f)
                lineTo(10f, 7f)
                close()

                moveTo(6f, 9f)
                lineTo(8f, 9f)
                lineTo(8f, 11f)
                lineTo(6f, 11f)
                close()

                moveTo(10f, 9f)
                lineTo(12f, 9f)
                lineTo(12f, 11f)
                lineTo(10f, 11f)
                close()

                moveTo(6f, 13f)
                lineTo(8f, 13f)
                lineTo(8f, 15f)
                lineTo(6f, 15f)
                close()

                moveTo(10f, 13f)
                lineTo(12f, 13f)
                lineTo(12f, 15f)
                lineTo(10f, 15f)
                close()

                // Side annex windows
                moveTo(16f, 10f)
                lineTo(18f, 10f)
                lineTo(18f, 12f)
                lineTo(16f, 12f)
                close()

                moveTo(16f, 14f)
                lineTo(18f, 14f)
                lineTo(18f, 16f)
                lineTo(16f, 16f)
                close()
            }
        }.build()
    }

    /**
     * Trophy / Ranking medal vector icon.
     */
    val TrophyRank: ImageVector by lazy {
        ImageVector.Builder(
            name = "TrophyRank",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(19f, 5f)
                horizontalLineTo(17f)
                verticalLineTo(3f)
                horizontalLineTo(7f)
                verticalLineTo(5f)
                horizontalLineTo(5f)
                curveTo(3.9f, 5f, 3f, 5.9f, 3f, 7f)
                verticalLineTo(8f)
                curveTo(3f, 10.55f, 4.92f, 12.63f, 7.39f, 12.93f)
                curveTo(8.14f, 14.36f, 9.48f, 15.41f, 11f, 15.82f)
                verticalLineTo(19f)
                horizontalLineTo(8f)
                verticalLineTo(21f)
                horizontalLineTo(16f)
                verticalLineTo(19f)
                horizontalLineTo(13f)
                verticalLineTo(15.82f)
                curveTo(14.52f, 15.41f, 15.86f, 14.36f, 16.61f, 12.93f)
                curveTo(19.08f, 12.63f, 21f, 10.55f, 21f, 8f)
                verticalLineTo(7f)
                curveTo(21f, 5.9f, 20.1f, 5f, 19f, 5f)
                close()

                moveTo(5f, 8f)
                verticalLineTo(7f)
                horizontalLineTo(7f)
                verticalLineTo(10.82f)
                curveTo(5.84f, 10.4f, 5f, 9.3f, 5f, 8f)
                close()

                moveTo(19f, 8f)
                curveTo(19f, 9.3f, 18.16f, 10.4f, 17f, 10.82f)
                verticalLineTo(7f)
                horizontalLineTo(19f)
                verticalLineTo(8f)
                close()
            }
        }.build()
    }

    /**
     * Trailer Play badge vector icon.
     */
    val TrailerPlay: ImageVector by lazy {
        ImageVector.Builder(
            name = "TrailerPlay",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(21.58f, 7.19f)
                curveTo(21.35f, 6.33f, 20.67f, 5.65f, 19.81f, 5.42f)
                curveTo(18.25f, 5f, 12f, 5f, 12f, 5f)
                curveTo(12f, 5f, 5.75f, 5f, 4.19f, 5.42f)
                curveTo(3.33f, 5.65f, 2.65f, 6.33f, 2.42f, 7.19f)
                curveTo(2f, 8.75f, 2f, 12f, 2f, 12f)
                curveTo(2f, 12f, 2f, 15.25f, 2.42f, 16.81f)
                curveTo(2.65f, 17.67f, 3.33f, 18.35f, 4.19f, 18.58f)
                curveTo(5.75f, 19f, 12f, 19f, 12f, 19f)
                curveTo(12f, 19f, 18.25f, 19f, 19.81f, 18.58f)
                curveTo(20.67f, 18.35f, 21.35f, 17.67f, 21.58f, 16.81f)
                curveTo(22f, 15.25f, 22f, 12f, 22f, 12f)
                curveTo(22f, 12f, 22f, 8.75f, 21.58f, 7.19f)
                close()

                moveTo(10f, 15.5f)
                verticalLineTo(8.5f)
                lineTo(16f, 12f)
                lineTo(10f, 15.5f)
                close()
            }
        }.build()
    }

    /**
     * Franchise Sequel Leap / Fast Forward Chevron vector icon.
     */
    val SequelJump: ImageVector by lazy {
        ImageVector.Builder(
            name = "SequelJump",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 2.5f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(5f, 6f)
                lineTo(11f, 12f)
                lineTo(5f, 18f)

                moveTo(13f, 6f)
                lineTo(19f, 12f)
                lineTo(13f, 18f)
            }
        }.build()
    }

    /**
     * Tag / Genre label vector icon.
     */
    val TagLabel: ImageVector by lazy {
        ImageVector.Builder(
            name = "TagLabel",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(21.41f, 11.58f)
                lineTo(12.41f, 2.58f)
                curveTo(12.05f, 2.22f, 11.55f, 2f, 11f, 2f)
                horizontalLineTo(4f)
                curveTo(2.9f, 2f, 2f, 2.9f, 2f, 4f)
                verticalLineTo(11f)
                curveTo(2f, 11.55f, 2.22f, 12.05f, 2.59f, 12.42f)
                lineTo(11.59f, 21.42f)
                curveTo(11.95f, 21.78f, 12.45f, 22f, 13f, 22f)
                curveTo(13.55f, 22f, 14.05f, 21.78f, 14.41f, 21.41f)
                lineTo(21.41f, 14.41f)
                curveTo(21.78f, 14.05f, 22f, 13.55f, 22f, 13f)
                curveTo(22f, 12.45f, 21.77f, 11.94f, 21.41f, 11.58f)
                close()

                moveTo(6.5f, 8f)
                curveTo(5.67f, 8f, 5f, 7.33f, 5f, 6.5f)
                curveTo(5f, 5.67f, 5.67f, 5f, 6.5f, 5f)
                curveTo(7.33f, 5f, 8f, 5.67f, 8f, 6.5f)
                curveTo(8f, 7.33f, 7.33f, 8f, 6.5f, 8f)
                close()
            }
        }.build()
    }

    /**
     * Expand to fullscreen vector icon.
     */
    val FullscreenExpand: ImageVector by lazy {
        ImageVector.Builder(
            name = "FullscreenExpand",
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
                moveTo(3f, 9f)
                lineTo(3f, 3f)
                lineTo(9f, 3f)

                moveTo(15f, 3f)
                lineTo(21f, 3f)
                lineTo(21f, 9f)

                moveTo(21f, 15f)
                lineTo(21f, 21f)
                lineTo(15f, 21f)

                moveTo(9f, 21f)
                lineTo(3f, 21f)
                lineTo(3f, 15f)
            }
        }.build()
    }
}
