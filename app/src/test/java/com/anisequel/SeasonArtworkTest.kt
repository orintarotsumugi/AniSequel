package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.domain.ui.PosterSource
import com.anisequel.domain.ui.SequelSeason
import com.anisequel.ui.components.detail.CoverImage
import com.anisequel.ui.components.detail.CoverSource
import com.anisequel.ui.components.detail.coverSaveName
import com.anisequel.ui.components.detail.seriesSeasonOf
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Season names, the series render, and what a saved picture is called.
 *
 * ## The two behaviours this pins
 *
 * btttr addresses a poster by **IMDb series**, so every season of a show resolves
 * to one picture - season one's key art, whatever the reader is looking at. The app
 * answers that in two places and they are deliberately different:
 *
 * - **Cards and lists** honour `LocalPosterUniqueSeasons`: a sequel keeps AniList's
 *   season-specific cover, because a grid has no room to explain that one picture
 *   stands for three entries.
 * - **The full-screen viewer** offers btttr's render to every season and says so:
 *   the caption reads "series art" and a note names the season it is not. Hiding it
 *   there as well removed the only place the series art could be seen whole.
 *
 * The naming half is the part that would otherwise lie. Before this, a Save from
 * "Frieren Season 2" produced "Frieren Season 2 clean poster" - a file name that
 * claims the picture is season two's. It now says "- series poster (clean poster)",
 * so the save is recognisable as the series art next to the AniList cover saved from
 * the same title.
 *
 * ## Why the detection cases matter
 *
 * A season this misses wears season one's poster under another name, which is the
 * defect the rule exists to prevent. A season it *invents* hides btttr art from a
 * show that is not a sequel at all, which is why the negatives (`86`,
 * `Blade Runner 2049`, `Steins;Gate 0`, CLAMP's `X`) are asserted beside the
 * positives. Both directions are bugs.
 */
class SeasonArtworkTest {

    private fun source(path: String): String =
        Regex("""/[*][\s\S]*?[*]/""")
            .replace(File(findProjectRoot(), path).readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    private fun media(english: String? = null, romaji: String? = null) = MediaNode(
        id = 1,
        title = MediaTitle(english = english, romaji = romaji)
    )

    private fun betterPosters(images: List<CoverImage> = listOf(CoverImage("btttr"))) =
        CoverSource(
            label = PosterSource.BETTER_POSTERS.displayName,
            images = images,
            fallbackAspect = 0.75f
        )

    private val anilistPoster = CoverSource(
        label = "Poster",
        images = listOf(CoverImage("cover-1"), CoverImage("cover-2")),
        fallbackAspect = 0.75f
    )

    private val anilistBanner = CoverSource(
        label = "Banner",
        images = listOf(CoverImage("banner")),
        fallbackAspect = 16f / 9f
    )

    @Test
    fun `the spellings a sequel actually uses are seasons`() {
        // Every one of these is a real AniList spelling. The first four were missed
        // by the old rule, which only knew "Season N", "Nth Season", roman numerals
        // after "Season", "Part N" and "Final Season".
        assertEquals(2, SequelSeason.numberIn("Mushoku Tensei II: Isekai Ittara Honki Dasu"))
        assertEquals(4, SequelSeason.numberIn("Overlord IV"))
        assertEquals(2, SequelSeason.numberIn("Bocchi the Rock! S2"))
        assertEquals(2, SequelSeason.numberIn("Bocchi the Rock! S02"))
        assertEquals(2, SequelSeason.numberIn("Some Show Season Two"))
        assertEquals(2, SequelSeason.numberIn("Some Show 2nd Cour"))
        assertEquals(3, SequelSeason.numberIn("Mushoku Tensei III"))
        assertEquals(10, SequelSeason.numberIn("Some Show X"))

        // And the spellings that already worked stay working.
        assertEquals(2, SequelSeason.numberIn("Frieren: Beyond Journey's End Season 2"))
        assertEquals(2, SequelSeason.numberIn("Attack on Titan Season II"))
        assertEquals(2, SequelSeason.numberIn("Some Show Part 2"))
        assertEquals(2, SequelSeason.numberIn("Some Show Cour 2"))
        assertEquals(SequelSeason.FINAL_SEASON, SequelSeason.numberIn("The Final Season"))
        assertEquals(1, SequelSeason.numberIn("Frieren Season 1"))
    }

    @Test
    fun `a title that is not a sequel stays one`() {
        // A bare trailing digit names the title, not its season. Each of these read
        // as a season would hide btttr art from a show with no second season.
        assertNull(SequelSeason.numberIn("86"))
        assertNull(SequelSeason.numberIn("86 - Eighty Six"))
        assertNull(SequelSeason.numberIn("Blade Runner 2049"))
        assertNull(SequelSeason.numberIn("Steins;Gate 0"))
        assertNull(SequelSeason.numberIn("Frieren"))
        // CLAMP's X is a title, not season ten. The roman-numeral rule requires a
        // space in front of it for exactly this case.
        assertNull(SequelSeason.numberIn("X"))
        assertNull(SequelSeason.numberIn("Spy x Family"))
        assertNull(SequelSeason.numberIn("Hunter x Hunter"))
    }

    @Test
    fun `the viewer labels btttr's render as the series art, and only for a sequel`() {
        assertEquals(
            2,
            seriesSeasonOf(media(english = "Frieren Season 2"), betterPosters())
        )
        assertEquals(
            "a Final Season has no number, but it is still not this entry's own art",
            SequelSeason.FINAL_SEASON,
            seriesSeasonOf(media(english = "Attack on Titan: The Final Season"), betterPosters())
        )
        assertNull(
            "a season-one title's btttr render is its own art, so there is nothing " +
                "to correct",
            seriesSeasonOf(media(english = "Frieren"), betterPosters())
        )
        assertNull(
            "AniList's cover is season-specific; calling it 'series art' would be wrong",
            seriesSeasonOf(media(english = "Frieren Season 2"), anilistPoster)
        )
        assertNull(seriesSeasonOf(media(english = "Frieren Season 2"), null))
    }

    @Test
    fun `ARM's season catches the sequels whose name hides them`() {
        assertEquals(
            "Nisemonogatari is season two of its series and carries no number; without " +
                "ARM's answer the viewer would call its render its own art",
            2,
            seriesSeasonOf(media(english = "Nisemonogatari"), betterPosters(), armSeason = 2)
        )
        assertEquals(
            "either source naming a later season is enough, and the larger wins",
            3,
            seriesSeasonOf(media(english = "Frieren Season 2"), betterPosters(), armSeason = 3)
        )
        assertNull(
            "ARM's season is not a licence to relabel AniList's season-specific cover",
            seriesSeasonOf(media(english = "Nisemonogatari"), anilistPoster, armSeason = 2)
        )
        assertNull(
            "a first season is not a sequel even when ARM is asked about it",
            seriesSeasonOf(media(english = "Frieren"), betterPosters(), armSeason = 1)
        )
    }

    @Test
    fun `a saved series render says so instead of claiming the season`() {
        val image = CoverImage("btttr", "Clean poster")
        val source = betterPosters(listOf(image))

        assertEquals(
            "Frieren Season 2 - series poster (clean poster)",
            coverSaveName(
                displayName = "Frieren Season 2",
                source = source,
                image = image,
                imageIndex = 0,
                seriesSeason = 2
            )
        )
        assertEquals(
            "a season-one render keeps the plain variant name - there is nothing to " +
                "disambiguate",
            "Frieren clean poster",
            coverSaveName(
                displayName = "Frieren",
                source = source,
                image = image,
                imageIndex = 0,
                seriesSeason = null
            )
        )
        assertEquals(
            "a Final Season save must not be filed as 'Season 99' - the number is " +
                "internal",
            "Attack on Titan: The Final Season - series poster (clean poster)",
            coverSaveName(
                displayName = "Attack on Titan: The Final Season",
                source = source,
                image = image,
                imageIndex = 0,
                seriesSeason = SequelSeason.FINAL_SEASON
            )
        )
    }

    @Test
    fun `AniList's pair and banner keep their own names`() {
        assertEquals(
            "Frieren 2",
            coverSaveName(
                displayName = "Frieren",
                source = anilistPoster,
                image = anilistPoster.images[1],
                imageIndex = 1,
                seriesSeason = null
            )
        )
        assertEquals(
            "Frieren banner",
            coverSaveName(
                displayName = "Frieren",
                source = anilistBanner,
                image = anilistBanner.images[0],
                imageIndex = 0,
                seriesSeason = null
            )
        )
    }

    @Test
    fun `the gallery offers every season and the sheet explains it`() {
        val posterArt = source(
            "app/src/main/java/com/anisequel/ui/components/cards/PosterArt.kt"
        )
        val gallery = posterArt.substringAfter("fun betterPosterGallery(")
            .substringBefore("\n}\n")

        assertTrue(
            "the viewer's gallery was gated on LocalPosterUniqueSeasons again, so a " +
                "sequel has no btttr art to look at anywhere in the app",
            !gallery.contains("UniqueSeasons")
        )
        assertTrue(
            "the gallery asks btttr to skip sequels, which returns no art for the " +
                "season the reader is looking at",
            gallery.contains("skipSequels = false")
        )

        val viewer = source(
            "app/src/main/java/com/anisequel/ui/components/detail/FullScreenCoverViewer.kt"
        )
        val api = source(
            "app/src/main/java/com/anisequel/data/network/BetterPostersApi.kt"
        )
        val details = source(
            "app/src/main/java/com/anisequel/ui/components/detail/CoverViewerDetails.kt"
        )

        assertTrue(
            "the viewer never asks ARM which season a title is, so the sequels whose " +
                "name hides the number are labelled as if btttr's series render were " +
                "their own art",
            viewer.contains("seasonFor(media.id)") && api.contains("suspend fun seasonFor(")
        )
        assertTrue(
            "the caption no longer says the render is the series art, so a Season 2 " +
                "entry shows season one's picture under season two's name",
            details.contains("· series art")
        )
        assertTrue(
            "there is no explanation of what 'series art' means, so the label reads " +
                "as a name the reader has to decode",
            details.contains("cover_series_art_note")
        )
        assertTrue(
            "the note would print the internal final-season sentinel as 'Season 99'",
            details.contains("SequelSeason.FINAL_SEASON")
        )
    }

    /**
     * A season numeral is English, not the phone's language.
     *
     * `uppercase()` with no locale is the device's, and `"IV".lowercase()` on a
     * Turkish phone is `"ıv"` - so `IV` never matched a roman numeral and
     * `numberIn` answered null. A null is this layer's "not a sequel", which silently
     * hands a season the shared IMDb series poster this whole class exists to avoid.
     */
    @Test
    fun `roman numerals and season words are read whatever the phone's language`() {
        val original = java.util.Locale.getDefault()
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("tr-TR"))

            assertEquals(4, SequelSeason.numberIn("Overlord IV"))
            assertEquals(3, SequelSeason.numberIn("Mushoku Tensei III"))
            assertEquals(2, SequelSeason.numberIn("Some Show Season Two"))
        } finally {
            java.util.Locale.setDefault(original)
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
