package com.anisequel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.anisequel.data.repository.MAX_FONT_SCALE
import com.anisequel.data.repository.MIN_FONT_SCALE
import com.anisequel.data.repository.SystemFont

// Every style in the Material 3 scale is set explicitly.
//
// The previous file overrode only bodyLarge and left the other fifteen at their
// defaults, so screens that reached for titleMedium or labelSmall got whatever
// the platform shipped - and then overrode the size again at the call site with
// `.copy(fontSize = 15.sp)`. The result was a dozen different title sizes that
// nothing could change in one place. One scale, set once, no call-site sizes.
private val Default = Typography()

private val lineHeightStyle = LineHeightStyle(
    alignment = LineHeightStyle.Alignment.Center,
    trim = LineHeightStyle.Trim.None,
)

private fun style(
    base: TextStyle,
    weight: FontWeight,
    size: Int,
    lineHeight: Int,
    tracking: Double = 0.0,
    scale: Float = 1f,
    family: FontFamily = FontFamily.Default,
) = base.copy(
    fontFamily = family,
    fontWeight = weight,
    // Scaled together: a size that grows while its line height does not is text
    // that clips its own descenders, and letter spacing in sp scales with the
    // glyphs it was tuned against.
    fontSize = (size * scale).sp,
    lineHeight = (lineHeight * scale).sp,
    letterSpacing = (tracking * scale).sp,
    lineHeightStyle = lineHeightStyle,
)

/**
 * The same style with its leading multiplied.
 *
 * ## The bug this replaces
 *
 * `lineHeight` is a `TextUnit` in sp, and every style in [anisequelTypography] is
 * built *at the reader's scale* - so a style's own line height already grows with
 * Settings > Type & reading. An override written as an absolute value does not:
 * `bodyMedium.copy(lineHeight = 22.sp)` holds 22sp for the default 14sp body text
 * and then, at the slider's 175%, lays 24.5sp glyphs on a 22sp line. The lines
 * overlap each other, which on a synopsis or a comment reads as a rendering fault
 * rather than as the size the reader asked for. The same shape was in the tier
 * tiles, the segmented bars and both hero titles.
 *
 * A factor of the style's own line height is the same design at 100% and stays
 * correct at every stop, because it inherits the scale rather than replacing it.
 * It is a function here, rather than a multiplication at each call site, so the
 * ratio and its reason live in one place - the three call sites that had grown
 * their own literal were each a slightly different mistake.
 */
internal fun TextStyle.withLeadingFactor(factor: Float): TextStyle =
    copy(lineHeight = lineHeight * factor)

/**
 * The hero's display title: `headlineSmall`, pulled tighter in lead and tracking.
 *
 * Both numbers are ratios of the style's own font size, which is what makes this
 * survive the text-size setting. They used to be absolute - `lineHeight = 30.sp`,
 * `letterSpacing = (-0.4).sp` - so at 175% the title's glyphs grew to 38.5sp while
 * its lines stayed 30sp apart and a three-line title overlapped itself, with the
 * tracking still at its 100% value while every measurement around it had grown.
 *
 * The ratios are the original pair against `headlineSmall`'s 22sp base, so the
 * design is identical at 100%: 30sp leading and -0.4sp tracking.
 *
 * Shared by the classic and Nova heroes because the two draw the same lockup, and
 * two copies of these two numbers is how one of them would keep the old bug.
 */
internal fun TextStyle.asHeroTitle(): TextStyle = copy(
    lineHeight = fontSize * HERO_TITLE_LEADING,
    letterSpacing = fontSize * HERO_TITLE_TRACKING
)

/** `headlineSmall`'s 30sp leading as a share of its 22sp font size. */
private const val HERO_TITLE_LEADING = 30f / 22f

/** `headlineSmall`'s -0.4sp tracking as a share of its 22sp font size. */
private const val HERO_TITLE_TRACKING = -0.4f / 22f

/**
 * The Compose family for one of the platform's five generic faces.
 *
 * Kept out of [SystemFont] itself because that enum lives in `data/repository`,
 * which is not allowed to import Compose: the preference stores a word, and this
 * is the one place that word becomes a drawable face. Every branch is a generic
 * family Android resolves natively, so none of them can fail to load.
 */
internal fun SystemFont.toFontFamily(): FontFamily = when (this) {
    SystemFont.DEFAULT -> FontFamily.Default
    SystemFont.SANS_SERIF -> FontFamily.SansSerif
    SystemFont.SERIF -> FontFamily.Serif
    SystemFont.MONOSPACE -> FontFamily.Monospace
    SystemFont.CURSIVE -> FontFamily.Cursive
}

/**
 * The app's whole type scale, at one reading size and in one font.
 *
 * [fontScale] is the reader's own choice from Settings > Type & reading, clamped to
 * the range that screen offers; [family] is the system face or a font the reader
 * imported. Everything else in the app reaches type through `MaterialTheme`, so
 * changing either here changes every screen - which is the point of the exercise:
 * the previous implementation had per-call-site sizes that no setting could reach.
 *
 * One function rather than two static scales, because a preview elsewhere in
 * Settings has to draw the same text the reader will get, and two copies of the
 * scale is how a preview starts lying.
 */
fun anisequelTypography(
    fontScale: Float = 1f,
    family: FontFamily = FontFamily.Default,
): Typography {
    val scale = fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)
    return Typography(
        displayLarge = style(Default.displayLarge, FontWeight.Bold, 52, 60, -0.5, scale, family),
        displayMedium = style(Default.displayMedium, FontWeight.Bold, 42, 50, -0.5, scale, family),
        displaySmall = style(Default.displaySmall, FontWeight.Bold, 34, 42, -0.5, scale, family),
        headlineLarge = style(Default.headlineLarge, FontWeight.Bold, 30, 38, -0.4, scale, family),
        headlineMedium = style(Default.headlineMedium, FontWeight.Bold, 26, 34, -0.3, scale, family),
        headlineSmall = style(Default.headlineSmall, FontWeight.SemiBold, 22, 30, 0.0, scale, family),
        titleLarge = style(Default.titleLarge, FontWeight.SemiBold, 20, 28, 0.0, scale, family),
        titleMedium = style(Default.titleMedium, FontWeight.SemiBold, 16, 24, 0.1, scale, family),
        titleSmall = style(Default.titleSmall, FontWeight.SemiBold, 14, 20, 0.1, scale, family),
        bodyLarge = style(Default.bodyLarge, FontWeight.Normal, 16, 24, 0.15, scale, family),
        bodyMedium = style(Default.bodyMedium, FontWeight.Normal, 14, 21, 0.2, scale, family),
        bodySmall = style(Default.bodySmall, FontWeight.Normal, 12, 17, 0.3, scale, family),
        labelLarge = style(Default.labelLarge, FontWeight.SemiBold, 14, 20, 0.1, scale, family),
        labelMedium = style(Default.labelMedium, FontWeight.Medium, 12, 16, 0.4, scale, family),
        labelSmall = style(Default.labelSmall, FontWeight.Medium, 11, 15, 0.4, scale, family),
    )
}
