package com.example.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
) = base.copy(
    fontFamily = FontFamily.Default,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    letterSpacing = tracking.sp,
    lineHeightStyle = lineHeightStyle,
)

val AniSequelTypography = Typography(
    displaySmall = style(Default.displaySmall, FontWeight.Bold, 34, 42, (-0.5)),
    headlineLarge = style(Default.headlineLarge, FontWeight.Bold, 30, 38, (-0.4)),
    headlineMedium = style(Default.headlineMedium, FontWeight.Bold, 26, 34, (-0.3)),
    headlineSmall = style(Default.headlineSmall, FontWeight.SemiBold, 22, 30),
    titleLarge = style(Default.titleLarge, FontWeight.SemiBold, 20, 28),
    titleMedium = style(Default.titleMedium, FontWeight.SemiBold, 16, 24, 0.1),
    titleSmall = style(Default.titleSmall, FontWeight.SemiBold, 14, 20, 0.1),
    bodyLarge = style(Default.bodyLarge, FontWeight.Normal, 16, 24, 0.15),
    bodyMedium = style(Default.bodyMedium, FontWeight.Normal, 14, 21, 0.2),
    bodySmall = style(Default.bodySmall, FontWeight.Normal, 12, 17, 0.3),
    labelLarge = style(Default.labelLarge, FontWeight.SemiBold, 14, 20, 0.1),
    labelMedium = style(Default.labelMedium, FontWeight.Medium, 12, 16, 0.4),
    labelSmall = style(Default.labelSmall, FontWeight.Medium, 11, 15, 0.4),
)

/** Backwards-compatible alias for the theme's typography. */
val Typography: Typography = AniSequelTypography

/**
 * Alias for the app's one shape scale, which lives in `ExpressiveTheme.kt`.
 *
 * This file used to declare a *second* `Shapes` with different values - medium
 * at 16dp here against 18dp there, extraLarge 28dp against 34dp. Two scales
 * meant "the app's roundness" was whichever one a file happened to import, and
 * tuning the theme changed some surfaces and not others. `ExpressiveShapes` is
 * the one the theme installs, so this points at it rather than restating it.
 */
val AniSequelShapes: Shapes
    get() = ExpressiveShapes