package com.anisequel.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * The Discord mark, for the presence settings and the preview card.
 *
 * The official 24x24 brand glyph, not a lookalike. The previous drawing was a
 * simplified mascot - a stroked outline with two filled eyes - and the one
 * thing a reader checks is whether the badge on "Log in to Discord" is
 * actually Discord's. A shape that resembles it is the wrong answer on a
 * button whose entire job is to be trusted with an account.
 *
 * The geometry is the published asset, transcribed command for command rather
 * than handed to a path parser. The published path writes its arc flags with
 * no separators (`a19.7913 19.7913 0 00-4.8851-1.5152`), and a parser that
 * reads `00` as one number loses its place in every coordinate after it -
 * which is what the badge on the account card was showing: a mark with its
 * wings folded over itself and one eye where the jaw should be. Spelling the
 * arcs out as `arcToRelative` calls cannot desync: each call names
 * its own seven values.
 *
 * Filled with [PathFillType.EvenOdd]. The mark is one closed body plus two eye
 * subpaths, so the even-odd rule punches the eyes out whatever direction they
 * were drawn in. Relying on the winding instead would make two white blobs out
 * of the eyes if the direction of a single subpath were ever transcribed
 * backwards, and that is the one part of the mark a reader would notice.
 */
internal val DiscordMark: ImageVector by lazy {
    ImageVector.Builder(
        name = "Discord",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply {
        path(
            fill = SolidColor(Color.White),
            pathFillType = PathFillType.EvenOdd
        ) {
            moveTo(20.317f, 4.3698f)
            arcToRelative(19.7913f, 19.7913f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4.8851f, -1.5152f)
            arcToRelative(0.0741f, 0.0741f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.0785f, 0.0371f)
            curveToRelative(-0.211f, 0.3753f, -0.4447f, 0.8648f, -0.6083f, 1.2495f)
            curveToRelative(-1.8447f, -0.2762f, -3.68f, -0.2762f, -5.4868f, 0f)
            curveToRelative(-0.1636f, -0.3933f, -0.4058f, -0.8742f, -0.6177f, -1.2495f)
            arcToRelative(0.077f, 0.077f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.0785f, -0.037f)
            arcToRelative(19.7363f, 19.7363f, 0f, isMoreThanHalf = false, isPositiveArc = false, -4.8852f, 1.515f)
            arcToRelative(0.0699f, 0.0699f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.0321f, 0.0277f)
            curveTo(0.5334f, 9.0458f, -0.319f, 13.5799f, 0.0992f, 18.0578f)
            arcToRelative(0.0824f, 0.0824f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.0312f, 0.0561f)
            curveToRelative(2.0528f, 1.5076f, 4.0413f, 2.4228f, 5.9929f, 3.0294f)
            arcToRelative(0.0777f, 0.0777f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.0842f, -0.0276f)
            curveToRelative(0.4616f, -0.6304f, 0.8731f, -1.2952f, 1.226f, -1.9942f)
            arcToRelative(0.076f, 0.076f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.0416f, -0.1057f)
            curveToRelative(-0.6528f, -0.2476f, -1.2743f, -0.5495f, -1.8722f, -0.8923f)
            arcToRelative(0.077f, 0.077f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.0076f, -0.1277f)
            curveToRelative(0.1258f, -0.0943f, 0.2517f, -0.1923f, 0.3718f, -0.2914f)
            arcToRelative(0.0743f, 0.0743f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.0776f, -0.0105f)
            curveToRelative(3.9278f, 1.7933f, 8.18f, 1.7933f, 12.0614f, 0f)
            arcToRelative(0.0739f, 0.0739f, 0f, isMoreThanHalf = false, isPositiveArc = true, 0.0785f, 0.0095f)
            curveToRelative(0.1202f, 0.099f, 0.246f, 0.1981f, 0.3728f, 0.2924f)
            arcToRelative(0.077f, 0.077f, 0f, isMoreThanHalf = false, isPositiveArc = true, -0.0066f, 0.1276f)
            arcToRelative(12.2986f, 12.2986f, 0f, isMoreThanHalf = false, isPositiveArc = true, -1.873f, 0.8914f)
            arcToRelative(0.0766f, 0.0766f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.0407f, 0.1067f)
            curveToRelative(0.3604f, 0.698f, 0.7719f, 1.3628f, 1.225f, 1.9932f)
            arcToRelative(0.076f, 0.076f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.0842f, 0.0286f)
            curveToRelative(1.961f, -0.6067f, 3.9495f, -1.5219f, 6.0023f, -3.0294f)
            arcToRelative(0.077f, 0.077f, 0f, isMoreThanHalf = false, isPositiveArc = false, 0.0313f, -0.0552f)
            curveToRelative(0.5004f, -5.177f, -0.8382f, -9.6739f, -3.5485f, -13.6604f)
            arcToRelative(0.061f, 0.061f, 0f, isMoreThanHalf = false, isPositiveArc = false, -0.0312f, -0.0286f)
            close()
            moveTo(8.02f, 15.3312f)
            curveToRelative(-1.1825f, 0f, -2.1569f, -1.0857f, -2.1569f, -2.419f)
            curveToRelative(0f, -1.3332f, 0.9555f, -2.4189f, 2.157f, -2.4189f)
            curveToRelative(1.2108f, 0f, 2.1757f, 1.0952f, 2.1568f, 2.419f)
            curveToRelative(0f, 1.3332f, -0.9555f, 2.4189f, -2.1569f, 2.4189f)
            close()
            moveToRelative(7.9748f, 0f)
            curveToRelative(-1.1825f, 0f, -2.1569f, -1.0857f, -2.1569f, -2.419f)
            curveToRelative(0f, -1.3332f, 0.9554f, -2.4189f, 2.1569f, -2.4189f)
            curveToRelative(1.2108f, 0f, 2.1757f, 1.0952f, 2.1568f, 2.419f)
            curveToRelative(0f, 1.3332f, -0.946f, 2.4189f, -2.1568f, 2.4189f)
            close()
        }
    }.build()
}
