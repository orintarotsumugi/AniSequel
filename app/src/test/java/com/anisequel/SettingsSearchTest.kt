package com.anisequel

import com.anisequel.ui.screens.settings.InterfaceSection
import com.anisequel.ui.screens.settings.SettingsPage
import com.anisequel.ui.screens.settings.ThemeSection
import com.anisequel.ui.screens.settings.SettingsSearch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Settings search, tested without a Compose harness.
 *
 * The index deliberately contains more than the ten shelf rows, so the tests pin
 * the two properties a result has to have to be useful: it must be the thing the
 * reader named, and it must carry a page (and, where one exists, a sub-section)
 * that the screen can actually open. A result that only knows a label would pass a
 * text test and dead-end in the UI.
 */
class SettingsSearchTest {

    @Test
    fun `an exact setting name ranks first and carries its destination`() {
        val result = SettingsSearch.search("Info page").first()

        assertEquals("Info page", result.title)
        assertEquals(SettingsPage.UI, result.page)
        assertEquals(InterfaceSection.LAYOUT, result.interfaceSection)
    }

    @Test
    fun `library layout search opens the interface library view`() {
        val result = SettingsSearch.search("library layout").first()

        assertEquals("Library layout", result.title)
        assertEquals(SettingsPage.UI, result.page)
        assertEquals(InterfaceSection.LIBRARY, result.interfaceSection)
    }

    @Test
    fun `multi-word queries use the visible copy and aliases`() {
        val result = SettingsSearch.search("dark mode").first()

        assertEquals("Theme mode", result.title)
        assertEquals(SettingsPage.APPEARANCE, result.page)
    }

    @Test
    fun `appearance colour and backdrop questions reach their exact sections`() {
        val colour = SettingsSearch.search("retain anime colour").first()
        assertEquals("Retain anime colour", colour.title)
        assertEquals(SettingsPage.APPEARANCE, colour.page)
        assertEquals(ThemeSection.ARTWORK, colour.themeSection)

        val backdrop = SettingsSearch.search("info page backdrop").first()
        assertEquals("Info page backdrop", backdrop.title)
        assertEquals(SettingsPage.APPEARANCE, backdrop.page)
        assertEquals(ThemeSection.PAGES, backdrop.themeSection)
    }

    @Test
    fun `aliases find list refresh and joke placement`() {
        assertTrue(
            "interval should find List refresh through its keywords",
            SettingsSearch.search("interval").any { it.title == "List refresh" }
        )
        assertTrue(
            "everywhere should find the joke scope through its keywords",
            SettingsSearch.search("everywhere").any { it.title == "Where she appears" }
        )
        assertTrue(
            "dpi should find the dedicated page",
            SettingsSearch.search("dpi").any { it.title == "Display size" }
        )
    }

    @Test
    fun `hyphenated and compact spellings find the same setting`() {
        assertTrue(
            "fullscreen should find Full-screen mode through the compact pass",
            SettingsSearch.search("fullscreen").any { it.title == "Full-screen mode" }
        )
        val display = SettingsSearch.search("fullscreen").first { it.title == "Full-screen mode" }
        assertEquals(SettingsPage.DEVICE, display.page)
        assertEquals(null, display.interfaceSection)
        assertTrue(
            "applock should find App lock through the compact pass",
            SettingsSearch.search("applock").any { it.title == "App lock" }
        )
    }

    @Test
    fun `a typo falls back to the closest setting and says it is fuzzy`() {
        val results = SettingsSearch.search("apearance")

        assertTrue("a one-letter typo must still find Appearance", results.any {
            it.title == "Appearance" && it.fuzzy
        })
    }

    @Test
    fun `a query with no plausible match returns nothing`() {
        assertTrue(SettingsSearch.search("zzzzzzzz").isEmpty())
        assertTrue("short garbage must not trigger fuzzy matching", SettingsSearch.search("zz").isEmpty())
    }

    @Test
    fun `suggestions are the curated recommendations and all resolve`() {
        val suggestions = SettingsSearch.suggestions()

        assertEquals(
            listOf("Info page", "Theme mode", "App lock", "Motion", "What's new"),
            suggestions.map { it.title }
        )
        assertTrue(suggestions.all { it.id.isNotBlank() })
    }

    @Test
    fun `highlight ranges are case-insensitive and merged`() {
        assertEquals(
            listOf(0..3, 14..17),
            SettingsSearch.highlightRanges("Dark mode and dark corners", "dark")
        )
        assertEquals(
            listOf(0..3, 5..8),
            SettingsSearch.highlightRanges("Dark mode", "dark mode")
        )
    }

    @Test
    fun `a compact query highlights the letters across a hyphen`() {
        assertEquals(
            listOf(0..10),
            SettingsSearch.highlightRanges("Full-screen mode", "fullscreen")
        )
    }

    @Test
    fun `an empty query has no highlights`() {
        assertTrue(SettingsSearch.highlightRanges("Anything", "  ").isEmpty())
    }
}
