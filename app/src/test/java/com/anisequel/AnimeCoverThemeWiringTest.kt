package com.anisequel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The wiring that makes the anime theme work at all.
 *
 * The colour engine can be perfect and the feature still do nothing if the sheet
 * asks AniList for a colour it already had, or applies the theme before the
 * stored preference has loaded. Both happened:
 *
 *  - the seed was `media.posterColor`, the server's averaged field, so the cover
 *    pixels were never read;
 *  - the same `LaunchedEffect` that loaded the detail read `themeSettings` at its
 *    initial `isLoaded = false`, saw the feature as off, and never ran again when
 *    the real settings arrived. Whether the app recoloured depended on whether
 *    DataStore won a race with the tap.
 *
 * Source assertions, because the failure is "the call is not there" rather than a
 * wrong value a unit test could observe.
 */
class AnimeCoverThemeWiringTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `the detail sheet reads the cover pixels instead of only AniList's field`() {
        val sheet = source(
            "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
        )

        assertTrue(
            "nothing calls CoverColorRepository.extract, so the theme seed is still " +
                "whatever AniList averaged over the file rather than the artwork",
            sheet.contains("CoverColorRepository.extract(")
        )
        assertTrue(
            "the extracted colour has to reach the hero as well as the theme",
            sheet.contains("CoverColors.put(")
        )
        assertTrue(
            "the theme seed must come from the extraction first; AniList's colour " +
                "is a fallback, not the source",
            sheet.contains("val colorHex = seed?.let { ArgbHex.format(it) }")
        )
    }

    @Test
    fun `the anime theme waits for the stored preferences before applying`() {
        val sheet = source(
            "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
        )
        val effect = sheet.substringAfter("LaunchedEffect(")
            .substringBefore("val handleDismiss")

        assertTrue(
            "the effect has to be keyed on isLoaded, or a sheet opened while " +
                "DataStore is still reading sees the feature as off and never " +
                "re-runs:\n$effect",
            effect.contains("themeSettings.isLoaded") &&
                effect.contains("if (!themeSettings.isLoaded) return@LaunchedEffect")
        )
        assertTrue(
            "and turning the feature on has to recolour for this title",
            effect.contains("applyAnimeColorLayer(") &&
                effect.contains("retain = themeSettings.animeThemeRetainColor")
        )
        assertTrue(
            "the gate is the stored switch, not the applied layer: a flag that " +
                "means both is how a temporary colour ended up permanent",
            effect.contains("themeSettings.animeThemeEnabled") &&
                effect.contains("if (!themeSettings.animeThemeEnabled) return@LaunchedEffect")
        )
    }

    /**
     * Closing the sheet has to end the layer, and only the layer.
     *
     * The dismiss path used to leave the immersive layer switched on when the colour
     * was retained, so the show's hue stayed in the surfaces of every screen and of
     * the next launch - and used to schedule the revert from inside the sheet, so the
     * timer died with the composable and a "5 minutes" colour never expired. Both
     * halves are wired here: retention clears the layer and keeps the accent,
     * a temporary colour hands its delay to the repository.
     *
     * Clearing the layer is still right, and it is no longer the same thing as
     * clearing the colour: the retained marker keeps the tinted scheme alive after
     * the sheet (pinned by the test below), so an exit does not repaint the app.
     */
    @Test
    fun `dismissing the sheet ends the immersive layer either way`() {
        val sheet = source(
            "app/src/main/java/com/anisequel/ui/components/detail/SequelDetailSheet.kt"
        )
        val dismiss = sheet.substringAfter("val handleDismiss")
            .substringBefore("ModalBottomSheet(")

        assertTrue(
            "a retained colour has to end the live layer (the retained marker is " +
                "what keeps the tint):\n$dismiss",
            dismiss.contains("clearAnimeColorLayer()")
        )
        assertTrue(
            "a temporary one keeps its delay, which the sheet used to cancel:\n$dismiss",
            dismiss.contains("releaseAnimeColorLayer(themeSettings.animeThemeRevertDuration)")
        )
    }

    /**
     * A retained colour has to outlive the sheet as the *background*, not just as
     * the accent.
     *
     * The reported behaviour was exact: open a title, the app takes the cover's
     * reddish tint; back out, and the background snaps to the brand navy while the
     * buttons keep the hue. The fix is the stored marker the preferences already
     * write - the theme seeds its tinted scheme from it while no sheet is open, and
     * the caller gates that on the feature and the retain switch so a marker left
     * by an older build cannot outrank the reader's own palette.
     */
    @Test
    fun `a retained cover colour keeps tinting after the sheet closes`() {
        val theme = source("app/src/main/java/com/anisequel/ui/theme/Theme.kt")
        val activity = source("app/src/main/java/com/anisequel/MainActivity.kt")

        assertTrue(
            "the theme has to fall back to the retained marker when the live layer " +
                "is gone, or every exit repaints the app:\n$theme",
            theme.contains("animeThemeRetainedColorHex") &&
                theme.contains("?: animeThemeRetainedColorHex")
        )
        assertTrue(
            "and the tinted branch must be selected by the seed, not by the live " +
                "layer's flag",
            theme.contains("animeColor != null ->") &&
                !theme.contains("animeThemeActive && animeColor != null ->")
        )
        assertTrue(
            "the activity has to hand the gated value to the theme, not the raw " +
                "marker:\n$activity",
            activity.contains("animeThemeRetainedColorHex = themeSettings.retainedAnimeColorHex")
        )

        // The gate itself, in the one place that can assert it without a device.
        val preferences = source(
            "app/src/main/java/com/anisequel/data/repository/ThemePreferences.kt"
        )
        assertTrue(
            "the retained marker must only drive the scheme while the feature and " +
                "Retain are both on; otherwise a marker left by a downgrade outranks " +
                "the reader's palette",
            preferences.contains("val retainedAnimeColorHex: String?") &&
                preferences.contains("animeThemeEnabled && animeThemeRetainColor")
        )
    }

    @Test
    fun `the anime layer does not force dark mode`() {
        val theme = source("app/src/main/java/com/anisequel/ui/theme/Theme.kt")

        assertTrue(
            "the anime branch still forces dark mode, which makes the light scheme " +
                "unreachable and gives a light-mode reader a dark sheet",
            !theme.contains("if (animeThemeActive && animeColor != null) true")
        )
        assertTrue(
            "both generated anime schemes have to be reachable",
            theme.contains("schemes.first") && theme.contains("schemes.second")
        )
        assertTrue(
            "and they have to be generated, not read from a single dark-only field",
            theme.contains("createDynamicSchemes(it, tintSurfaces = true)")
        )
    }

    @Test
    fun `the hero prefers the extracted cover colour`() {
        val hero = source(
            "app/src/main/java/com/anisequel/ui/components/detail/info/InfoHero.kt"
        )

        assertTrue(
            "the hero still starts from media.posterColor, so its backdrop can " +
                "disagree with the theme built from the same artwork:\n$hero",
            hero.contains("CoverColors.color(media.posterUrl)") &&
                hero.indexOf("CoverColors.color(media.posterUrl)") <
                hero.indexOf("media.posterColor.toCoverColorOrNull()")
        )
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
