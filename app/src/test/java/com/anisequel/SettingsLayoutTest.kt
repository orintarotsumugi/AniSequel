package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings redesign, read off the source.
 *
 * ## What this is for
 *
 * Three of the changes here are structural, and a structural change that is half
 * done looks exactly like a finished one on a screenshot of the wrong screen:
 *
 *  - **The shelf groups its rows.** Rows inside one surface with a rule between
 *    them is what makes a section label structural. Drawn as separate cards the
 *    grouping is carried by the label alone, and the gap between two rows in a group
 *    was the same size as the gap between two groups.
 *  - **The card preview draws the real card.** A preview written separately is an
 *    advert: it can be tuned, it can be left behind, and nothing fails when it does,
 *    because nobody taps it. This pins that the preview, the Library grid and the
 *    dashboard's poster all call the one cell.
 *  - **A moved control is not drawn twice.** The Interface page took motion off the
 *    Theme page. A copy left behind means two switches over one preference, and the
 *    reader turns the one they can see while the other keeps the value.
 *
 * There is no Compose test harness in the unit-test source set, so these are read
 * from the source - the same approach as `RelationDetailResolutionTest`.
 */
class SettingsLayoutTest {

    private fun source(path: String): String {
        val file = File(findProjectRoot(), path)
        assertTrue(
            "could not read ${file.path}; this guard silently passing on a missing " +
                "file is the same failure it exists to prevent",
            file.isFile
        )
        return withoutComments(file.readText())
    }

    private val settings = "app/src/main/java/com/anisequel/ui/screens/settings/"

    private fun withoutComments(src: String): String = Regex("""/[*][\s\S]*?[*]/""")
        .replace(src) { "" }
        .lines()
        .filterNot { it.trimStart().startsWith("//") }
        .joinToString("\n")

    @Test
    fun `the shelf draws one grouped surface per section`() {
        val hub = source(settings + "SettingsHub.kt")
        assertTrue(
            "the hub has no grouped surface, so every row is still its own card",
            hub.contains("fun SettingsHubGroup(")
        )
        assertTrue(
            "a group with no divider between its rows is the old separated cards " +
                "wearing a new name",
            hub.contains("HorizontalDivider(")
        )
        assertFalse(
            "SettingsHubRow is back: one surface per row undoes the grouping",
            hub.contains("fun SettingsHubRow(")
        )

        val screen = source(settings + "SettingsScreen.kt")
        listOf(
            "SettingsSectionLabel(\"Personalisation\")",
            "SettingsSectionLabel(\"AniList\")",
            "SettingsSectionLabel(\"About\")"
        ).forEach { label ->
            assertTrue("the shelf lost its $label heading", screen.contains(label))
        }
        assertTrue(
            "the rows are no longer built from the page enum, so a row can drift " +
                "from the page it opens",
            screen.contains("SettingsPage.APPEARANCE.entry(")
        )
    }

    @Test
    fun `the interface page exists and the theme page is only about colour`() {
        val screen = source(settings + "SettingsScreen.kt")
        assertTrue("the enum has no interface page", screen.contains("UI(\"Interface\""))
        assertTrue(
            "the hub has no door to the interface page",
            screen.contains("settings_row_ui")
        )
        assertTrue(
            "the page is not dispatched",
            screen.contains("SettingsPage.UI -> InterfaceTab(")
        )

        val theme = source(settings + "ThemeTab.kt")
        assertFalse(
            "motion is drawn on the Theme page again, so two pages write one " +
                "preference",
            theme.contains("MotionSection(")
        )
        assertTrue(
            "the appearance mode is a word rather than a picture of the scheme",
            theme.contains("ThemeModeTile(")
        )
        assertTrue("the OLED switch lost its name", theme.contains("title = \"OLED mode\""))

        val iface = source(settings + "InterfaceTab.kt")
        assertTrue("the interface page does not draw motion", iface.contains("MotionSection("))
        assertTrue("the interface page has no style chips", iface.contains("card_style_"))
    }

    /**
     * The preview, the Library grid and the dashboard's poster all call one cell.
     *
     * This is the assertion that makes the preview worth drawing at all.
     */
    @Test
    fun `every card surface draws the same cell`() {
        val surfaces = listOf(
            settings + "InterfaceTab.kt" to "the picker's preview",
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileLibraryTab.kt" to
                "the Library grid",
            "app/src/main/java/com/anisequel/ui/components/cards/SequelPoster.kt" to
                "the dashboard's poster"
        )
        surfaces.forEach { (path, who) ->
            assertTrue(
                "$who does not go through MediaPosterCell, so it is a second " +
                    "implementation of the card style",
                source(path).contains("MediaPosterCell(")
            )
        }

        val details = source(
            "app/src/main/java/com/anisequel/ui/components/cards/SequelCardDetails.kt"
        )
        assertTrue(
            "the details column reads the style, so it cannot keep drawing a score " +
                "the poster already shows",
            details.contains("style.scoreOnPoster")
        )
    }

    @Test
    fun `the style is provided once at the app root`() {
        val nav = source("app/src/main/java/com/anisequel/ui/navigation/AppNavigation.kt")
        assertTrue(
            "nothing provides the card style, so every reader falls back to the " +
                "default and the picker changes nothing",
            nav.contains("LocalMediaCardStyle provides cardStyle")
        )
        assertTrue(
            "the Interface page writes a different store than the root reads",
            nav.contains("interfacePreferences = interfacePreferences")
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
