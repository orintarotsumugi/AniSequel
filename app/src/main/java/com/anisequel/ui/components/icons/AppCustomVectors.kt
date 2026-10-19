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

                // Windows are cut *out* of the tower, so each one is traced
                // anticlockwise (down, right, up, back) and its winding
                // cancels the body's. They used to be wound the same way round
                // as the building, which under PathFillType.NonZero added to it
                // instead of subtracting from it: every window was white on
                // white and the whole icon rendered as one solid blob. The
                // winding fix is the one to keep rather than an EvenOdd fill
                // type, because it survives someone changing that flag.
                //
                // Floors sit at y = 6, 11 and 16 so the tower and the annex
                // share them; before, the top two thirds of both were windows
                // and the bottom third was a blank wall.
                moveTo(6f, 6f)
                lineTo(6f, 8f)
                lineTo(8f, 8f)
                lineTo(8f, 6f)
                close()

                moveTo(10f, 6f)
                lineTo(10f, 8f)
                lineTo(12f, 8f)
                lineTo(12f, 6f)
                close()

                moveTo(6f, 11f)
                lineTo(6f, 13f)
                lineTo(8f, 13f)
                lineTo(8f, 11f)
                close()

                moveTo(10f, 11f)
                lineTo(10f, 13f)
                lineTo(12f, 13f)
                lineTo(12f, 11f)
                close()

                moveTo(6f, 16f)
                lineTo(6f, 18f)
                lineTo(8f, 18f)
                lineTo(8f, 16f)
                close()

                moveTo(10f, 16f)
                lineTo(10f, 18f)
                lineTo(12f, 18f)
                lineTo(12f, 16f)
                close()

                // Side annex windows, on the tower's two lower floors.
                moveTo(16f, 11f)
                lineTo(16f, 13f)
                lineTo(18f, 13f)
                lineTo(18f, 11f)
                close()

                moveTo(16f, 16f)
                lineTo(16f, 18f)
                lineTo(18f, 18f)
                lineTo(18f, 16f)
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
     * Surface tones: three stacked rounded surfaces, each offset from the one
     * behind it.
     *
     * The Theme tab's Background card changes how dark the app's surfaces are,
     * which is a layered-tones job — hence the offset stack rather than a single
     * split shape. It stays distinct from `Contrast`, the About card's
     * half-circle, at the 20dp a section header renders it at. The old glyph was
     * one square bisected down the middle, which read as a "flip" or "compare"
     * control instead of depth.
     */
    val SurfaceTones: ImageVector by lazy {
        ImageVector.Builder(
            name = "SurfaceTones",
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
                // Back plane.
                moveTo(11f, 3f)
                horizontalLineTo(20f)
                quadTo(22f, 3f, 22f, 5f)
                verticalLineTo(13f)
                quadTo(22f, 15f, 20f, 15f)
                horizontalLineTo(11f)
                quadTo(9f, 15f, 9f, 13f)
                verticalLineTo(5f)
                quadTo(9f, 3f, 11f, 3f)
                close()

                // Middle plane, one step forward and down.
                moveTo(8f, 6f)
                horizontalLineTo(17f)
                quadTo(19f, 6f, 19f, 8f)
                verticalLineTo(16f)
                quadTo(19f, 18f, 17f, 18f)
                horizontalLineTo(8f)
                quadTo(6f, 18f, 6f, 16f)
                verticalLineTo(8f)
                quadTo(6f, 6f, 8f, 6f)
                close()

                // Front plane, nearest the viewer.
                moveTo(5f, 9f)
                horizontalLineTo(14f)
                quadTo(16f, 9f, 16f, 11f)
                verticalLineTo(19f)
                quadTo(16f, 21f, 14f, 21f)
                horizontalLineTo(5f)
                quadTo(3f, 21f, 3f, 19f)
                verticalLineTo(11f)
                quadTo(3f, 9f, 5f, 9f)
                close()
            }
        }.build()
    }

    /**
     * Motion waves: two gentle S-curves, like ripples but with no overshoot.
     *
     * The Theme tab's Motion section and its "Smooth" option. "Instant" has no
     * motion at all and "Chill" is all overshoot, so each of the three styles
     * gets its own glyph rather than three copies of one arrow.
     */
    val MotionWaves: ImageVector by lazy {
        ImageVector.Builder(
            name = "MotionWaves",
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
                moveTo(3f, 7.5f)
                curveTo(6f, 3.5f, 9f, 3.5f, 12f, 7.5f)
                curveTo(15f, 11.5f, 18f, 11.5f, 21f, 7.5f)

                moveTo(3f, 16.5f)
                curveTo(6f, 12.5f, 9f, 12.5f, 12f, 16.5f)
                curveTo(15f, 20.5f, 18f, 20.5f, 21f, 16.5f)
            }
        }.build()
    }

    /**
     * Motion bolt / lightning for the "Chill" motion style.
     *
     * Chill is the full expressive physics — the spring with the most energy — so
     * it gets a bolt with three trailing speed lines, while Smooth gets calm
     * waves. The old bolt was a zigzag whose halves met at a single point and
     * filled in at small sizes, reading as a blob. The bolt stays filled and the
     * speed lines are stroked so they remain hairlines and the bolt keeps its hard
     * edges — the one place a fill and a stroke genuinely both belong.
     */
    val MotionBolt: ImageVector by lazy {
        ImageVector.Builder(
            name = "MotionBolt",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            // The bolt itself.
            path(
                fill = SolidColor(Color.White),
                pathFillType = PathFillType.NonZero
            ) {
                moveTo(13.5f, 2f)
                lineTo(4.5f, 13f)
                horizontalLineTo(10.5f)
                lineTo(9f, 22f)
                lineTo(19.5f, 10.5f)
                horizontalLineTo(13.5f)
                close()
            }
            // Trailing speed lines, kept short so they clear the bolt's left edge.
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.6f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(1.5f, 7f)
                horizontalLineTo(6f)

                moveTo(1f, 12f)
                horizontalLineTo(3.5f)

                moveTo(1.5f, 17f)
                horizontalLineTo(6f)
            }
        }.build()
    }

    /**
     * Expand to fullscreen vector icon.
     *
     * Four diagonal arrows radiating from the centre to the corners, each with its
     * own head. The old glyph was four corner brackets with no shafts - the same
     * corner marks a "collapse" glyph uses, which is how the player's expand
     * button could read as its opposite. The shafts make the direction
     * unambiguous.
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
                strokeLineWidth = 1.8f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Top-left arrow.
                moveTo(4f, 7.5f)
                lineTo(4f, 4f)
                lineTo(7.5f, 4f)
                moveTo(4f, 4f)
                lineTo(10.5f, 10.5f)

                // Top-right arrow.
                moveTo(16.5f, 4f)
                lineTo(20f, 4f)
                lineTo(20f, 7.5f)
                moveTo(20f, 4f)
                lineTo(13.5f, 10.5f)

                // Bottom-right arrow.
                moveTo(20f, 16.5f)
                lineTo(20f, 20f)
                lineTo(16.5f, 20f)
                moveTo(20f, 20f)
                lineTo(13.5f, 13.5f)

                // Bottom-left arrow.
                moveTo(7.5f, 20f)
                lineTo(4f, 20f)
                lineTo(4f, 16.5f)
                moveTo(4f, 20f)
                lineTo(10.5f, 13.5f)
            }
        }.build()
    }

    /**
     * The comment composer's bold control.
     *
     * A drawn `B` rather than the Material `FormatBold` glyph, which lives in
     * `material-icons-extended` and would put a font-scale-dependent icon font
     * behind one button in a toolbar of hand-authored vectors.
     */
    val TextBold: ImageVector by lazy {
        ImageVector.Builder(
            name = "TextBold",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                // Stem.
                moveTo(8f, 5f)
                lineTo(8f, 19f)

                // Upper bowl, open at the stem.
                moveTo(8f, 5f)
                lineTo(12.5f, 5f)
                curveTo(15.8f, 5f, 17.2f, 6.6f, 17.2f, 8.3f)
                curveTo(17.2f, 10f, 15.8f, 11.7f, 12.5f, 11.7f)
                lineTo(8f, 11.7f)

                // Lower bowl, which is the wider half of the letter.
                moveTo(8f, 11.7f)
                lineTo(13.3f, 11.7f)
                curveTo(17.2f, 11.7f, 18.4f, 13.7f, 18.4f, 15.3f)
                curveTo(18.4f, 17.2f, 17.2f, 19f, 13.3f, 19f)
                lineTo(8f, 19f)
            }
        }.build()
    }

    /**
     * The composer's italic control: the slanted `I` the format bar expects,
     * with its serifs drawn as two short bars rather than a font glyph.
     */
    val TextItalic: ImageVector by lazy {
        ImageVector.Builder(
            name = "TextItalic",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(10f, 5f)
                lineTo(17.5f, 5f)
                moveTo(6.5f, 19f)
                lineTo(14f, 19f)
                moveTo(14.5f, 5f)
                lineTo(9.5f, 19f)
            }
        }.build()
    }

    /**
     * The composer's spoiler control.
     *
     * A bordered block with a line struck through it, which is the shape
     * AniList's own editor uses: the button writes `~!text!~`, and the glyph has
     * to say "this text will be hidden" without being a padlock, which the app
     * already uses for the app lock in Settings.
     */
    val TextSpoiler: ImageVector by lazy {
        ImageVector.Builder(
            name = "TextSpoiler",
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f
        ).apply {
            path(
                stroke = SolidColor(Color.White),
                strokeLineWidth = 1.9f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round
            ) {
                moveTo(4.5f, 7f)
                lineTo(19.5f, 7f)
                lineTo(19.5f, 17f)
                lineTo(4.5f, 17f)
                close()

                moveTo(5.5f, 18.5f)
                lineTo(18.5f, 5.5f)
            }
        }.build()
    }

    /**
     * The composer's attach-image control.
     *
     * A picture frame rather than a paperclip: AniList has no upload endpoint,
     * so this button inserts an image URL, and a clip that cannot attach a file
     * would be the wrong promise.
     */
    val AttachImage: ImageVector by lazy {
        ImageVector.Builder(
            name = "AttachImage",
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
                // Frame.
                moveTo(4f, 5f)
                lineTo(20f, 5f)
                lineTo(20f, 19f)
                lineTo(4f, 19f)
                close()

                // Sun, drawn as four arcs so it is stroke-only like the frame.
                moveTo(8.4f, 8.8f)
                curveTo(8.4f, 7.8f, 9.2f, 7f, 10.2f, 7f)
                curveTo(11.2f, 7f, 12f, 7.8f, 12f, 8.8f)
                curveTo(12f, 9.8f, 11.2f, 10.6f, 10.2f, 10.6f)
                curveTo(9.2f, 10.6f, 8.4f, 9.8f, 8.4f, 8.8f)
                close()

                // Two peaks, clipped by the frame's lower edge.
                moveTo(4.5f, 16.8f)
                lineTo(9.6f, 11.7f)
                lineTo(13.2f, 15.3f)
                lineTo(15.8f, 12.7f)
                lineTo(19.5f, 16.4f)
            }
        }.build()
    }
}
