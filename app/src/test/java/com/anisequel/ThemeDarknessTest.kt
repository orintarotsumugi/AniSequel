package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The cover accent has to be chosen by the same darkness flag as the scheme.
 *
 * `DynamicThemeBuilder.accentColor` picks tone 40 in light and tone 80 in dark, so
 * it is only the accent the buttons are using if the caller asks about the same
 * mode the theme resolved. Two surfaces asked the *phone* instead
 * (`isSystemInDarkTheme()`): the title page's backdrop and the Settings swatch. A
 * reader who forces Dark on a light phone got the light tone over a dark sheet -
 * the exact mismatch the accent pipeline exists to avoid, and invisible in a
 * screenshot taken on the default setting.
 *
 * A JVM test cannot compose the theme, but this failure is structural: the two
 * call sites read a different question. So the source is what is pinned.
 */
class ThemeDarknessTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    private val hero = "app/src/main/java/com/anisequel/ui/components/detail/info/InfoHero.kt"
    private val section = "app/src/main/java/com/anisequel/ui/screens/settings/AnimeColourSection.kt"

    @Test
    fun `the theme provides its resolved darkness to the pages`() {
        val theme = source("app/src/main/java/com/anisequel/ui/theme/Theme.kt")

        assertTrue(
            "AniSequelTheme must provide the resolved mode; without the provider " +
                "every reader of LocalAppDarkTheme silently gets the default",
            theme.contains("LocalAppDarkTheme provides darkTheme")
        )
    }

    @Test
    fun `the cover accent reads the app's mode rather than the phone's`() {
        listOf(hero, section).forEach { path ->
            val code = source(path)

            assertTrue(
                "$path must read LocalAppDarkTheme for the accent tone",
                code.contains("LocalAppDarkTheme.current")
            )
            assertFalse(
                "$path must not pick the tone from the system theme: a reader who " +
                    "forced Dark in Settings would still be shown the light accent",
                code.contains("isSystemInDarkTheme")
            )
        }
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
