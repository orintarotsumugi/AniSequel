package com.anisequel.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp
import com.anisequel.data.repository.MAX_FONT_SCALE
import com.anisequel.data.repository.MIN_FONT_SCALE

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
