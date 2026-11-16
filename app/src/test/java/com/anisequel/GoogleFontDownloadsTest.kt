package com.anisequel

import com.anisequel.data.repository.CustomFontStore
import com.anisequel.data.repository.GoogleFontCatalog
import com.anisequel.data.repository.parseGoogleFontFaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Font tab's free downloads.
 *
 * The network is not exercised here: Google owns those URLs, and a test that
 * depends on them would fail when Google changes a version suffix rather than
 * when this app does. What is pinned is the part this repo owns - the quick
 * picks, the search and its exact-name fallback, the shape of the CSS the parser
 * accepts, and the refusal of the WOFF2 response a browser user agent would get.
 */
class GoogleFontDownloadsTest {

    @Test
    fun `the catalog starts with JetBrains Mono, Inter and Roboto`() {
        assertEquals(
            listOf("jetbrains_mono", "inter", "roboto"),
            GoogleFontCatalog.quickPicks.map { it.id }
        )
        assertEquals(
            listOf("JetBrains Mono", "Inter", "Roboto"),
            GoogleFontCatalog.quickPicks.map { it.displayName }
        )
        assertTrue(
            "every download needs a CSS family name",
            GoogleFontCatalog.quickPicks.all { it.familyName.isNotBlank() }
        )
    }

    @Test
    fun `search finds Google families by name and category`() {
        assertTrue(GoogleFontCatalog.search("lora").any { it.familyName == "Lora" })
        assertTrue(GoogleFontCatalog.search("mono").any { it.familyName == "JetBrains Mono" })
        assertTrue(
            GoogleFontCatalog.search("serif").any { it.familyName == "Merriweather" }
        )
    }

    @Test
    fun `an unknown name is still offered as an exact family`() {
        val result = GoogleFontCatalog.search("My Fancy Font").single()

        assertEquals("My Fancy Font", result.familyName)
        assertEquals("my_fancy_font", result.id)
    }

    @Test
    fun `the parser keeps TrueType faces and their weights`() {
        val css = """
            @font-face {
              font-family: 'Inter';
              font-style: normal;
              font-weight: 400;
              src: url(https://fonts.gstatic.com/s/inter/regular.ttf) format('truetype');
            }
            @font-face {
              font-family: 'Inter';
              font-style: normal;
              font-weight: 700;
              src: url(https://fonts.gstatic.com/s/inter/bold.ttf) format('truetype');
            }
        """.trimIndent()

        val faces = parseGoogleFontFaces(css)

        assertEquals(listOf(400, 700), faces.map { it.weight })
        assertEquals(
            "https://fonts.gstatic.com/s/inter/regular.ttf",
            faces.first().url
        )
    }

    @Test
    fun `a browser only WOFF2 response is refused`() {
        val css = """
            @font-face {
              font-family: 'Inter';
              font-weight: 400;
              src: url(https://fonts.gstatic.com/s/inter/regular.woff2) format('woff2');
            }
        """.trimIndent()

        assertTrue(parseGoogleFontFaces(css).isEmpty())
    }

    @Test
    fun `downloaded paths are distinguishable from imported copies`() {
        assertTrue(
            CustomFontStore.isDownloaded("/data/user/0/app/files/fonts/google_inter_400.ttf")
        )
        assertFalse(
            CustomFontStore.isDownloaded("/data/user/0/app/files/fonts/custom_font_123.ttf")
        )
        assertFalse(CustomFontStore.isDownloaded(null))
    }
}
