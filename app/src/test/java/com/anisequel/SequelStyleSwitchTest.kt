package com.anisequel

import com.anisequel.domain.ui.HomeSequelStyle
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The two shipped bugs of 2.1.2 that a device is not needed to catch.
 *
 * ## The style switch restarting Home
 *
 * `LocalHomeSequelStyle` was a `staticCompositionLocalOf`, which recomposes the
 * provider's entire content when the value changes - and the value is provided at
 * the app root. Picking a style therefore rebuilt the Home page, hero backdrop and
 * Continue rail included, so their opening animations replayed and the page
 * visibly jumped before settling on the new rows. Nothing in the drawn result was
 * wrong, which is why only a source pin can hold the line: a dynamic local
 * invalidates only its readers, and the reader has to be inside the list item.
 *
 * ## The mangled Discord badge
 *
 * The official path was fed to Compose's path parser, and the published geometry
 * writes its arc flags without separators (`a19.7913 19.7913 0 00-4.8851-1.5152`).
 * `00` reads as one number, so every coordinate after the first arc was shifted and
 * the badge rendered as a folded blob. The mark is transcribed now, so the parser
 * must not come back for it.
 *
 * Source checks strip comments first, exactly as `DiscordLoginBrowserTest` does: an
 * explanation of what was removed must not read as a use of it.
 */
class SequelStyleSwitchTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `the sequel style local is dynamic, not static`() {
        val code = source("app/src/main/java/com/anisequel/ui/theme/LayoutLocals.kt")

        assertTrue(
            "LocalHomeSequelStyle must be provided with compositionLocalOf: a static " +
                "local recomposes the whole provider on a change, which is what made a " +
                "style switch rebuild Home and replay its animations",
            code.contains("val LocalHomeSequelStyle = compositionLocalOf")
        )
        assertFalse(
            "LocalHomeSequelStyle is static again - picking a style would restart the " +
                "page instead of redrawing the sequels",
            code.contains("val LocalHomeSequelStyle = staticCompositionLocalOf")
        )
    }

    @Test
    fun `the style is read inside the sequel item, not in the page scope`() {
        val code = source("app/src/main/java/com/anisequel/ui/screens/nova/NovaHomeScreen.kt")
        val read = code.indexOf("val sequelStyle = LocalHomeSequelStyle.current")
        val listStart = code.indexOf("items(")

        assertTrue(
            "NovaHomeScreen no longer reads LocalHomeSequelStyle; the dispatch would " +
                "draw one style only",
            read >= 0
        )
        assertTrue(
            "the style is read before `items(`, which is the page's own scope: every " +
                "row change then recomposes the hero and the Continue rail above it",
            listStart >= 0 && read > listStart
        )
    }

    @Test
    fun `every sequel style has a row of its own in the list`() {
        val code = source("app/src/main/java/com/anisequel/ui/screens/nova/NovaHomeScreen.kt")

        HomeSequelStyle.entries.forEach { style ->
            assertTrue(
                "HomeSequelStyle.$style is offered in Settings but the Home list has no " +
                    "branch for it, so choosing it would fall through",
                code.contains("HomeSequelStyle.$style ->")
            )
        }
    }

    @Test
    fun `the Discord badge is a transcribed mark, not a parsed path`() {
        val catalogue = source("app/src/main/java/com/anisequel/ui/components/icons/AppExtraVectors.kt")
        val mark = source("app/src/main/java/com/anisequel/ui/components/icons/DiscordMark.kt")

        assertFalse(
            "AppExtraVectors is handing the official Discord path to the parser again; " +
                "its arc flags have no separators, and the parser desyncs on them",
            catalogue.contains("addPathNodes")
        )
        assertTrue(
            "the badge has to draw the transcribed mark",
            catalogue.contains("DiscordMark")
        )
        assertTrue(
            "the marked-up geometry is what keeps the arcs readable: arcToRelative names " +
                "its own seven values, so a flag cannot be read as a coordinate",
            mark.contains("arcToRelative")
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
