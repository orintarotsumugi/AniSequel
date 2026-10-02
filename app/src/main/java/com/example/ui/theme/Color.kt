package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// AniList brand seed. Everything else is derived by hand into full Material 3
// role sets below, because the brand blue alone is not enough to build an
// accessible UI: the status colours used for "Airing", "Upcoming" and
// "Missed" had to be picked separately for light and dark to clear the 4.5:1
// contrast requirement, which the old hardcoded 0xFF10B981 on a light surface
// did not.

// --- Brand ------------------------------------------------------------------
val AniBlueLight = Color(0xFF4FA8FF)
val AniBlue = Color(0xFF1673D6)
val AniBlueDark = Color(0xFF0B4F9E)
val AniBlueContainerLight = Color(0xFFD6E7FF)
val AniBlueContainerDark = Color(0xFF0A3766)
val AniCyan = Color(0xFF00B8C4)

// --- Semantic status colours ------------------------------------------------
// Light theme: darker, so text/icon on a tinted chip stays legible.
val StatusSuccessLight = Color(0xFF0F7A4A)
val StatusSuccessContainerLight = Color(0xFFC8EFDC)
val StatusWarningLight = Color(0xFF9A5B00)
val StatusWarningContainerLight = Color(0xFFFFE6C2)
val StatusInfoLight = Color(0xFF1667B3)
val StatusInfoContainerLight = Color(0xFFD5E8FF)

// Dark theme: lighter, for the same reason on dark surfaces.
val StatusSuccessDark = Color(0xFF5FD9A3)
val StatusSuccessContainerDark = Color(0xFF0B3D2A)
val StatusWarningDark = Color(0xFFFFC46B)
val StatusWarningContainerDark = Color(0xFF4A2F00)
val StatusInfoDark = Color(0xFF8FC2FF)
val StatusInfoContainerDark = Color(0xFF0B3A63)

// --- Surfaces ---------------------------------------------------------------
val DarkBgMain = Color(0xFF0A1017)
val DarkSurfaceLow = Color(0xFF101A24)
val DarkSurface = Color(0xFF141F2B)
val DarkSurfaceContainer = Color(0xFF1A2734)
val DarkSurfaceContainerHigh = Color(0xFF21303F)
val DarkSurfaceContainerHighest = Color(0xFF2A3B4C)
val DarkBorder = Color(0xFF33465A)
val DarkTextPrimary = Color(0xFFE8EFF6)
val DarkTextSecondary = Color(0xFFA3B4C6)

val LightBgMain = Color(0xFFF3F6FB)
val LightSurfaceLow = Color(0xFFFAFCFF)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFEDF2F9)
val LightSurfaceContainerHigh = Color(0xFFE4EBF5)
val LightSurfaceContainerHighest = Color(0xFFDCE5F2)
val LightBorder = Color(0xFFC3D0E0)
val LightTextPrimary = Color(0xFF0D1520)
val LightTextSecondary = Color(0xFF51637A)