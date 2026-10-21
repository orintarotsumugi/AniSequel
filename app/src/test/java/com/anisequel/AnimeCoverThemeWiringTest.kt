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
            effect.contains("setAnimeThemeActive(") && effect.contains("active = true")
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
