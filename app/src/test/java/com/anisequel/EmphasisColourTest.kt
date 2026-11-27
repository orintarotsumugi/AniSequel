package com.anisequel

import com.anisequel.domain.color.Contrast
import com.anisequel.domain.color.EmphasisPalette
import com.anisequel.domain.color.MaterialColorSchemes
import com.anisequel.domain.color.MaterialScheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The emphasis roles, measured.
 *
 * ## The bugs this pins
 *
 * The UI used to dim Material's paired roles with an alpha - 0.62 for a disabled
 * chip label, 0.5 for a disabled like button, 0.45 for a progress track and a
 * sheet's grab handle, 0.38 for a disabled format button and a skeleton line, 0.3
 * for a page indicator, 0.26 for a motion rail. An alpha is measured against
 * whatever is behind it, and every one of those was a different - and worse -
 * colour in the light theme, because the light scheme's secondary text is a
 * mid-grey-blue (#51637A):
 *
 * | role | alpha | light ratio |
 * | --- | --- | --- |
 * | disabled chip label | 0.62 | 2.48:1 |
 * | disabled like button | 0.50 | 2.17:1 |
 * | help icon / track | 0.45 | 1.87:1 |
 * | skeleton line | 0.30 | 1.50:1 |
 * | motion rail | 0.26 | 1.44:1 |
 *
 * So the disabled label was the least legible text on a settings page and a
 * skeleton was a sweep of nothing. The same alpha also meant a card border at
 * 0.38 of `outlineVariant` drew at 1.2:1 while the light theme's card fill sits
 * 1.03:1 from the page - a card with no edge at all.
 *
 * These roles are derived instead, and this file holds them to the floors they
 * claim - for the brand scheme in both modes, and for generated schemes across
 * seeds, because the anime theme, a picked canvas and the platform's dynamic
 * palette all hand the UI a different scheme.
 */
class EmphasisColourTest {

    private val brandLight = MaterialScheme(
        primary = 0xFF0B67D1.toInt(),
        onPrimary = 0xFFFFFFFF.toInt(),
        primaryContainer = 0xFFD3E4FF.toInt(),
        onPrimaryContainer = 0xFF001C38.toInt(),
        inversePrimary = 0xFF64B5FF.toInt(),
        secondary = 0xFF00696B.toInt(),
        onSecondary = 0xFFFFFFFF.toInt(),
        secondaryContainer = 0xFF9CF0F4.toInt(),
        onSecondaryContainer = 0xFF002021.toInt(),
        tertiary = 0xFF9A5B00.toInt(),
        onTertiary = 0xFFFFFFFF.toInt(),
        tertiaryContainer = 0xFFFFE6C2.toInt(),
        onTertiaryContainer = 0xFF2C1700.toInt(),
        error = 0xFFBA1A1A.toInt(),
        onError = 0xFFFFFFFF.toInt(),
        errorContainer = 0xFFFFDAD6.toInt(),
        onErrorContainer = 0xFF410002.toInt(),
        background = 0xFFF3F6FB.toInt(),
        onBackground = 0xFF0D1520.toInt(),
        surface = 0xFFFFFFFF.toInt(),
        onSurface = 0xFF0D1520.toInt(),
        surfaceVariant = 0xFFEDF2F9.toInt(),
        onSurfaceVariant = 0xFF51637A.toInt(),
        surfaceTint = 0xFF0B67D1.toInt(),
        inverseSurface = 0xFF1A2734.toInt(),
        inverseOnSurface = 0xFFE8EFF6.toInt(),
        outline = 0xFFC3D0E0.toInt(),
        outlineVariant = 0xFFD8E1EC.toInt(),
        scrim = 0xFF000000.toInt(),
        surfaceBright = 0xFFFFFFFF.toInt(),
        surfaceContainer = 0xFFEDF2F9.toInt(),
        surfaceContainerHigh = 0xFFE4EBF5.toInt(),
        surfaceContainerHighest = 0xFFDCE5F2.toInt(),
        surfaceContainerLow = 0xFFFAFCFF.toInt(),
        surfaceContainerLowest = 0xFFFFFFFF.toInt(),
        surfaceDim = 0xFFDDE4EF.toInt(),
        // The fixed roles are mode-independent in Material, so both hand-built
        // schemes carry the same ones. Nothing here reads them; they exist because
        // `MaterialScheme` is a complete scheme and the emphasis palette is fed
        // real ones.
        primaryFixed = 0xFFD3E4FF.toInt(),
        primaryFixedDim = 0xFFA9C7FF.toInt(),
        onPrimaryFixed = 0xFF001C38.toInt(),
        onPrimaryFixedVariant = 0xFF00497D.toInt(),
        secondaryFixed = 0xFF9CF0F4.toInt(),
        secondaryFixedDim = 0xFF80D4D8.toInt(),
        onSecondaryFixed = 0xFF002021.toInt(),
        onSecondaryFixedVariant = 0xFF004F51.toInt(),
        tertiaryFixed = 0xFFFFE6C2.toInt(),
        tertiaryFixedDim = 0xFFFFD08A.toInt(),
        onTertiaryFixed = 0xFF2C1700.toInt(),
        onTertiaryFixedVariant = 0xFF6A3C00.toInt()
    )

    private val brandDark = MaterialScheme(
        primary = 0xFF64B5FF.toInt(),
        onPrimary = 0xFF00325F.toInt(),
        primaryContainer = 0xFF06437E.toInt(),
        onPrimaryContainer = 0xFFD6E7FF.toInt(),
        inversePrimary = 0xFF0B67D1.toInt(),
        secondary = 0xFF00B8C4.toInt(),
        onSecondary = 0xFF00363A.toInt(),
        secondaryContainer = 0xFF004E52.toInt(),
        onSecondaryContainer = 0xFF9CF0F4.toInt(),
        tertiary = 0xFFFFC46B.toInt(),
        onTertiary = 0xFF452B00.toInt(),
        tertiaryContainer = 0xFF4A2F00.toInt(),
        onTertiaryContainer = 0xFFFFE0B8.toInt(),
        error = 0xFFFFB4AB.toInt(),
        onError = 0xFF690005.toInt(),
        errorContainer = 0xFF93000A.toInt(),
        onErrorContainer = 0xFFFFDAD6.toInt(),
        background = 0xFF0A1017.toInt(),
        onBackground = 0xFFE8EFF6.toInt(),
        surface = 0xFF141F2B.toInt(),
        onSurface = 0xFFE8EFF6.toInt(),
        surfaceVariant = 0xFF21303F.toInt(),
        onSurfaceVariant = 0xFFA3B4C6.toInt(),
        surfaceTint = 0xFF64B5FF.toInt(),
        inverseSurface = 0xFFEDF2F9.toInt(),
        inverseOnSurface = 0xFF0D1520.toInt(),
        outline = 0xFF33465A.toInt(),
        outlineVariant = 0xFF24333F.toInt(),
        scrim = 0xFF000000.toInt(),
        surfaceBright = 0xFF34485C.toInt(),
        surfaceContainer = 0xFF1A2734.toInt(),
        surfaceContainerHigh = 0xFF21303F.toInt(),
        surfaceContainerHighest = 0xFF2A3B4C.toInt(),
        surfaceContainerLow = 0xFF101A24.toInt(),
        surfaceContainerLowest = 0xFF070C12.toInt(),
        surfaceDim = 0xFF0D151E.toInt(),
        // Identical to the light scheme's: fixed roles do not follow the mode.
        primaryFixed = 0xFFD3E4FF.toInt(),
        primaryFixedDim = 0xFFA9C7FF.toInt(),
        onPrimaryFixed = 0xFF001C38.toInt(),
        onPrimaryFixedVariant = 0xFF00497D.toInt(),
        secondaryFixed = 0xFF9CF0F4.toInt(),
        secondaryFixedDim = 0xFF80D4D8.toInt(),
        onSecondaryFixed = 0xFF002021.toInt(),
        onSecondaryFixedVariant = 0xFF004F51.toInt(),
        tertiaryFixed = 0xFFFFE6C2.toInt(),
        tertiaryFixedDim = 0xFFFFD08A.toInt(),
        onTertiaryFixed = 0xFF2C1700.toInt(),
        onTertiaryFixedVariant = 0xFF6A3C00.toInt()
    )

    /**
     * Every role clears the floor it is named for, on every surface it can sit on.
     *
     * Measured against *all* the surface roles, not against
     * `surfaceContainerHighest`: the palette used to assume that was the furthest
     * the ramp moves from `onSurface`, and it is not - the light scheme's
     * `surfaceDim` is darker (tone 87 against 90) and the dark scheme's
     * `surfaceBright` is lighter (24 against 22). Measured against the container
     * the palette used to treat as worst, the dark theme's secondary text on a red
     * seed reads 4.48:1 on `surfaceBright` - under the 4.5:1 it is named for.
     */
    @Test
    fun `every emphasis role clears its own floor`() {
        listOf("brand light" to brandLight, "brand dark" to brandDark).forEach { (name, scheme) ->
            val palette = EmphasisPalette.of(scheme)

            fun check(role: String, colour: Int, floor: Double) {
                surfaces(scheme).forEach { surface ->
                    val ratio = Contrast.ratio(colour, surface)
                    assertTrue(
                        "$name $role measures %.2f:1 on %08X, under its %.2f " +
                            "floor - the value it replaced was an alpha, and an alpha is " +
                            "measured against whatever happens to be behind it"
                                .format(ratio, surface, floor),
                        ratio >= floor
                    )
                }
            }

            check("secondary", palette.secondary, EmphasisPalette.FLOOR_SECONDARY)
            check("muted", palette.muted, EmphasisPalette.FLOOR_MUTED)
            check("disabled", palette.disabled, EmphasisPalette.FLOOR_CONTROL)
            check("track", palette.track, EmphasisPalette.FLOOR_CONTROL)
            check("boundary", palette.boundary, EmphasisPalette.FLOOR_CONTROL)
            check("skeleton", palette.skeleton, EmphasisPalette.FLOOR_SKELETON)
            check("divider", palette.divider, EmphasisPalette.FLOOR_DIVIDER)
            assertEquals("$name has the wrong number of chart series", 4, palette.charts.size)
            palette.charts.forEachIndexed { index, series ->
                check("chart series $index", series, EmphasisPalette.FLOOR_CONTROL)
            }
        }
    }

    /** The same guarantee for a scheme generated from any seed the app can land on. */
    @Test
    fun `a generated scheme keeps every floor`() {
        val seeds = listOf(
            "red" to 0xFFD32F2F.toInt(),
            "yellow" to 0xFFFBC02D.toInt(),
            "blue" to 0xFF1565C0.toInt(),
            "violet" to 0xFF7B1FA2.toInt(),
            "grey" to 0xFF808080.toInt(),
            "night" to 0xFF101820.toInt()
        )
        seeds.forEach { (name, seed) ->
            listOf(false, true).forEach { dark ->
                val scheme = MaterialColorSchemes.tonalSpot(seed, dark = dark, tintSurfaces = true)
                val palette = EmphasisPalette.of(scheme)
                val mode = if (dark) "dark" else "light"

                fun check(role: String, colour: Int, floor: Double) {
                    surfaces(scheme).forEach { surface ->
                        val ratio = Contrast.ratio(colour, surface)
                        assertTrue(
                            "$name $mode $role is %.2f:1 on %08X, under its %.2f floor"
                                .format(ratio, surface, floor),
                            ratio >= floor
                        )
                    }
                }

                check("secondary", palette.secondary, EmphasisPalette.FLOOR_SECONDARY)
                check("muted", palette.muted, EmphasisPalette.FLOOR_MUTED)
                check("disabled", palette.disabled, EmphasisPalette.FLOOR_CONTROL)
                check("divider", palette.divider, EmphasisPalette.FLOOR_DIVIDER)
                check("skeleton", palette.skeleton, EmphasisPalette.FLOOR_SKELETON)
                palette.charts.forEachIndexed { index, series ->
                    check("chart series $index", series, EmphasisPalette.FLOOR_CONTROL)
                }
            }
        }
    }

    /**
     * The ladder is ordered, so "quieter" means quieter.
     *
     * A derived palette can accidentally collapse: two roles that both resolve to
     * the same strengthened grey look like a hierarchy and read as one tone.
     */
    @Test
    fun `the ladder steps down in the order it claims`() {
        listOf("light" to brandLight, "dark" to brandDark).forEach { (name, scheme) ->
            val palette = EmphasisPalette.of(scheme)
            val worst = EmphasisPalette.worstSurface(scheme)
            fun of(colour: Int) = Contrast.ratio(colour, worst)

            assertTrue(
                "$name: secondary (%.2f) must be stronger than muted (%.2f)".format(
                    of(palette.secondary),
                    of(palette.muted)
                ),
                of(palette.secondary) > of(palette.muted)
            )
            assertTrue(
                "$name: muted (%.2f) must be stronger than disabled (%.2f)".format(
                    of(palette.muted),
                    of(palette.disabled)
                ),
                of(palette.muted) > of(palette.disabled)
            )
            assertTrue(
                "$name: disabled (%.2f) must be stronger than skeleton (%.2f)".format(
                    of(palette.disabled),
                    of(palette.skeleton)
                ),
                of(palette.disabled) > of(palette.skeleton)
            )
            assertTrue(
                "$name: skeleton (%.2f) must be stronger than a divider (%.2f)".format(
                    of(palette.skeleton),
                    of(palette.divider)
                ),
                of(palette.skeleton) > of(palette.divider)
            )
        }
    }

    /**
     * The old alpha ladder, as a number, so the fix cannot be silently reverted.
     *
     * This is the *reason* the roles exist: it recomputes what the app used to draw
     * and asserts that the derived role is better on the surface the alpha was
     * measured on. If someone reintroduces `onSurfaceVariant.copy(alpha = 0.45f)`
     * anywhere, this test still passes - `ColourRoleWiringTest` is what fails.
     */
    @Test
    fun `the derived tones beat the alphas they replaced`() {
        val palette = EmphasisPalette.of(brandLight)
        val card = brandLight.surfaceContainerHighest

        // What the UI actually drew: the role's RGB with an alpha byte in front,
        // composited over the card - which is what `Color.copy(alpha)` costs the
        // reader, rather than what it looks like in the source.
        fun old(roleAlpha: Float): Double {
            val alphaByte = (255 * roleAlpha).toInt() and 0xFF
            val faded = (alphaByte shl 24) or (brandLight.onSurfaceVariant and 0x00FFFFFF)
            return Contrast.ratio(Contrast.compositeOver(faded, card), card)
        }

        assertTrue(
            "a 0.62 label was %.2f:1 and the derived disabled tone is %.2f:1".format(
                old(0.62f),
                Contrast.ratio(palette.disabled, card)
            ),
            old(0.62f) < Contrast.ratio(palette.disabled, card)
        )
        assertTrue(
            "a 0.45 icon was %.2f:1 and the derived muted tone is %.2f:1".format(
                old(0.45f),
                Contrast.ratio(palette.muted, card)
            ),
            old(0.45f) < Contrast.ratio(palette.muted, card)
        )
        assertTrue(
            "a 0.3 skeleton was %.2f:1 and the derived skeleton tone is %.2f:1".format(
                old(0.3f),
                Contrast.ratio(palette.skeleton, card)
            ),
            old(0.3f) < Contrast.ratio(palette.skeleton, card)
        )
    }

    /**
     * The UI reads the roles, and the roles are provided once.
     *
     * `AniSequelTheme.emphasis` is only useful if the theme provides it and the
     * controls ask for it; a role that exists and is never read is the same UI as
     * before with more code. The files listed are the ones whose alphas this change
     * replaced, so a revert anywhere shows up here by name.
     */
    @Test
    fun `the theme provides the roles and the controls read them`() {
        val theme = source("app/src/main/java/com/anisequel/ui/theme/Theme.kt")
        assertTrue(
            "the theme does not provide the emphasis roles, so every control falls " +
                "back to the brand light set in a dark anime theme",
            theme.contains("LocalEmphasisColors provides")
        )
        assertTrue(
            "the roles are not derived from the animated scheme, so they sit still " +
                "while the rest of the page cross-fades",
            theme.contains("animatedColorScheme.toEmphasisColors()")
        )

        mapOf(
            "ui/screens/settings/SettingsChoiceChip.kt" to "emphasis.disabled",
            "ui/screens/settings/SettingsHub.kt" to "rememberVerdictColor",
            "ui/screens/settings/SettingsComponents.kt" to "rememberStateColor",
            "ui/screens/settings/LayoutPreviews.kt" to "emphasis.skeleton",
            "ui/screens/settings/MotionSection.kt" to "emphasis.divider",
            "ui/screens/settings/SequelStylePreviews.kt" to "alpha = 0.68f",
            "ui/components/EngagementAction.kt" to "emphasis.disabled",
            "ui/components/expressive/ExpressiveVisuals.kt" to "emphasis.muted",
            "ui/components/detail/info/WavyProgressBar.kt" to "emphasis.track",
            "ui/components/detail/info/ProgressBars.kt" to "emphasis.track",
            "ui/components/detail/info/NovaStatDrawer.kt" to "emphasis.charts",
            "ui/components/detail/SequelDetailPage.kt" to "emphasis.muted",
            "ui/screens/dashboard/ArrivingCard.kt" to "emphasis.muted",
            "ui/screens/profile/CommentFormatBar.kt" to "emphasis.disabled"
        ).forEach { (path, marker) ->
            assertTrue(
                "$path no longer reads its derived role ($marker), so it is back on " +
                    "a hand-picked alpha",
                source("app/src/main/java/com/anisequel/$path").contains(marker)
            )
        }
    }

    /** Every surface role a card can take its background from. */
    private fun surfaces(scheme: MaterialScheme): List<Int> = listOf(
        scheme.surface,
        scheme.surfaceDim,
        scheme.surfaceBright,
        scheme.surfaceContainer,
        scheme.surfaceContainerHigh,
        scheme.surfaceContainerHighest,
        scheme.surfaceContainerLow,
        scheme.surfaceContainerLowest
    )

    private fun source(path: String): String {
        val file = File(findProjectRoot(), path)
        assertTrue(
            "could not read ${file.path}; a guard that silently passes on a missing " +
                "file is the same failure it exists to prevent",
            file.isFile
        )
        return file.readText()
    }

    private fun findProjectRoot(): File {
        var candidate = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(candidate, "gradle.properties").isFile) {
            candidate = candidate.parentFile ?: error("could not locate project root")
        }
        return candidate
    }
}
