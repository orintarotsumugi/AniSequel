package com.anisequel

import com.anisequel.data.network.betterPosterUrl
import com.anisequel.data.network.posterSeason
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.ui.PosterOverlays
import com.anisequel.domain.ui.SequelSeason
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two things 2.3.1 shipped wrong about the card, pinned so they cannot ship wrong
 * again.
 *
 * - **The caption switches.** 2.3.1's release notes said the name and score switches
 *   were on by default; the code stored false. Every grid lost its caption on upgrade,
 *   and the switch that would bring it back read as already on. The default is a
 *   requirement now, not a preference.
 * - **The stacked headers.** Interface > Card design > Card shape drew a header at
 *   every level, so a sub-screen opened with three title rows over a sliver of
 *   content. A sub-screen draws exactly one header, and the page that owns a stack
 *   draws it instead of `SettingsScreen` wrapping one around it.
 *
 * `betterPosterUrl` is pinned here too: the overlay switches ask btttr for a different
 * path, and the letters are the service's, not ours to invent.
 */
class CardDisplayDefaultsTest {

    private fun source(path: String): String {
        val file = File(findProjectRoot(), path)
        assertTrue("could not read ${file.path}", file.isFile)
        return Regex("""/[*][\s\S]*?[*]/""")
            .replace(file.readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")
    }

    @Test
    fun `a card prints its name and its score unless asked otherwise`() {
        assertTrue(
            "showTitles defaults to false again: every grid loses its caption and " +
                "the switch in Poster art describes itself as already on",
            CardDesign.DEFAULT.showTitles
        )
        assertTrue(
            "showScores defaults to false again for the same reason",
            CardDesign.DEFAULT.showScores
        )
    }

    @Test
    fun `a fresh install asks for the clean poster`() {
        // The three overlays are btttr's own additions; the clean poster is the
        // artwork and its name, which is what "only the poster" means. Every one
        // of them defaults off, including through the preference layer's absent
        // keys - `poster-n` is the variant with neither score nor genre, and
        // `tag=none` drops the trend strip.
        assertTrue(
            "the score is on by default again, so a new install draws btttr's pill",
            !PosterOverlays.DEFAULT.showRating
        )
        assertTrue(
            "the genre line is on by default again",
            !PosterOverlays.DEFAULT.showGenre
        )
        assertTrue(
            "the trend strip is on by default again",
            !PosterOverlays.DEFAULT.showTrendTags
        )
        assertEquals(
            "https://btttr.cc/poster-n/imdb/poster-default/tt1.jpg?tag=none",
            betterPosterUrl("tt1")
        )
    }

    @Test
    fun `btttr path letters follow the overlay switches`() {
        // The variant is the *first* segment; `poster-default` is the size/style slot
        // and stays where it is. btttr's own Jellyfin integration builds the URL this
        // way, and a letter in the wrong segment is a 404 rather than a poster.
        val base = "https://btttr.cc"
        assertEquals(
            "$base/poster/imdb/poster-default/tt1.jpg",
            betterPosterUrl(
                "tt1",
                PosterOverlays(
                    showRating = true,
                    showGenre = true,
                    showTrendTags = true
                )
            )
        )
        assertEquals(
            "$base/poster-r/imdb/poster-default/tt1.jpg?tag=none",
            betterPosterUrl("tt1", PosterOverlays(showRating = true))
        )
        assertEquals(
            "$base/poster-g/imdb/poster-default/tt1.jpg?tag=none",
            betterPosterUrl("tt1", PosterOverlays(showGenre = true))
        )
        assertEquals(
            "$base/poster-n/imdb/poster-default/tt1.jpg?tag=none",
            betterPosterUrl("tt1", PosterOverlays())
        )
        assertEquals(
            "$base/poster/imdb/poster-default/tt1.jpg?tag=none",
            betterPosterUrl(
                "tt1",
                PosterOverlays(
                    showRating = true,
                    showGenre = true,
                    showTrendTags = false
                )
            )
        )
    }

    @Test
    fun `sequel seasons are detected from the names the titles use`() {
        // btttr addresses one IMDb series, so these are the entries that would
        // otherwise wear season one's picture under a different name.
        assertEquals(2, SequelSeason.numberIn("Frieren: Beyond Journey's End Season 2"))
        assertEquals(3, SequelSeason.numberIn("Mushoku Tensei 3rd Season"))
        assertEquals(2, SequelSeason.numberIn("Attack on Titan Season II"))
        assertEquals(2, SequelSeason.numberIn("Some Show Part 2"))
        assertEquals(SequelSeason.FINAL_SEASON, SequelSeason.numberIn("The Final Season"))
        assertEquals(1, SequelSeason.numberIn("Frieren Season 1"))
        assertNull("a first season is not a sequel", SequelSeason.numberIn("Frieren"))
        assertNull("a year is not a season", SequelSeason.numberIn("Blade Runner 2049"))
    }

    @Test
    fun `ARM's season keeps a sequel off the shared series poster`() {
        // btttr has one picture for tt1480925, the IMDb series that carries the
        // whole Monogatari run. ARM is what says Nisemonogatari is season 2 of it,
        // which is the case the title parser cannot see - the name has no "Season"
        // in it at all.
        assertEquals(
            2,
            posterSeason(
                mapOf(
                    "imdb" to "tt1480925",
                    "themoviedb-season" to 2.0,
                    "thetvdb-season" to 2.0
                )
            )
        )
        assertEquals(2, posterSeason(mapOf("thetvdb-season" to 2)))
        assertEquals(
            "the larger season wins when the providers split a run differently",
            2,
            posterSeason(mapOf("themoviedb-season" to 1, "thetvdb-season" to 2))
        )
        assertEquals(1, posterSeason(mapOf("themoviedb-season" to 1.0)))
        assertNull("a film has no season", posterSeason(mapOf("imdb" to "tt1480925")))
    }

    @Test
    fun `a settings sub-screen draws one header`() {
        val settings = "app/src/main/java/com/anisequel/ui/screens/settings/"
        val header = Regex("""SettingsSubHeader\(""")

        assertTrue(
            "InterfaceTab draws more than one header, so a sub-screen stacks titles",
            header.findAll(source(settings + "InterfaceTab.kt")).count() == 1
        )
        assertTrue(
            "ThemeTab draws more than one header, so a sub-screen stacks titles",
            header.findAll(source(settings + "ThemeTab.kt")).count() == 1
        )
        assertTrue(
            "the sub-page bodies draw their own header again, one level under the " +
                "one their parent already drew",
            header.findAll(source(settings + "InterfaceSections.kt")).count() == 0
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
