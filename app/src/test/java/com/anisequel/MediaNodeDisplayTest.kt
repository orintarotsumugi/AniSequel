package com.anisequel

import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaExternalLink
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRanking
import com.anisequel.data.model.MediaRelationEdge
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.alternativeTitleRows
import com.anisequel.data.model.detailFor
import com.anisequel.data.model.displayName
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.hasRankings
import com.anisequel.data.model.metaLabel
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.streamingLinks
import com.anisequel.data.model.watchFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Info page's display derivations that decide whether a section is drawn at all.
 *
 * These two exist because of one shared mistake, so they are tested together.
 *
 * A `LazyColumn` item whose content returns early is not free: the list's
 * `Arrangement.spacedBy` gives every item its trailing gap whether or not the item
 * measured to anything, so a section that decided for itself that it had nothing to
 * show still left a hole in the page. Each of the five sections below returns early
 * for entirely ordinary titles - a show nobody has started, a show with no streaming
 * links, a show with no staff listing, a show with no alternative titles, a show with
 * no ranking.
 *
 * The fix was to have the page ask before it adds the item, which only works if the
 * predicate agrees with what the section would have drawn. That agreement is the
 * thing worth pinning: a predicate that drifts from its section reintroduces the gap
 * silently, and nothing about either one looks wrong in a diff.
 */
class MediaNodeDisplayTest {

    // ---- hasRankings -----------------------------------------------------------------

    /**
     * A ranking with no rank is not a ranking: there is no badge to draw and no
     * number to print, so the section renders nothing.
     */
    @Test
    fun `a ranking without a rank does not count as a ranking`() {
        val media = node(rankings = listOf(MediaRanking(context = "Trending", rank = null)))

        assertFalse(media.hasRankings)
    }

    @Test
    fun `a ranking with a rank counts`() {
        val media = node(rankings = listOf(MediaRanking(context = "Trending", rank = 3)))

        assertTrue(media.hasRankings)
    }

    @Test
    fun `no rankings at all does not count`() {
        assertFalse(node().hasRankings)
    }

    /**
     * The predicate has to match what the section draws, and the section draws as long
     * as *any* ranking has a rank - not only the first, and not only the best.
     */
    @Test
    fun `one ranked entry among unranked ones is enough`() {
        val media = node(
            rankings = listOf(
                MediaRanking(context = "A", rank = null),
                MediaRanking(context = "B", rank = 91)
            )
        )

        assertTrue(media.hasRankings)
    }

    // ---- hasProgressCard ------------------------------------------------------------

    @Test
    fun `a title with no entry has no progress card`() {
        assertFalse(node(episodes = 26).hasProgressCard)
    }

    @Test
    fun `a tracked title with no progress yet still has a progress card`() {
        // AniList records progress 0 the moment a show is added to Planning, before
        // anything is watched - and that is not the same as "not on the list". The
        // distinction is the whole point of this predicate: a title with no
        // `mediaListEntry` has no `watchFraction` at all, so no card and no 0% wave
        // (a bar there would claim progress for a thing nobody is tracking), while a
        // title that is tracked at zero legitimately says "0 / 26".
        //
        // It is also what the Library grid already does: `libraryProgressLabel`
        // returns "Ep 0/26" for the same entry, so the two surfaces agree.
        val media = node(episodes = 26, watched = 0)

        assertEquals("0 / 26", media.progressCountLabel)
        assertEquals("the fraction is defined, at zero", 0f, media.watchFraction()!!, 0f)
        assertTrue(media.hasProgressCard)
    }

    @Test
    fun `a partly watched title with a known total has a progress card`() {
        val media = node(episodes = 26, watched = 6)

        assertTrue(media.hasProgressCard)
    }

    /**
     * A total of zero is not progress: it is the API saying it does not know how many
     * episodes there are, and dividing by it would be a number rather than a fact.
     */
    @Test
    fun `a zero total has no progress card even with a count`() {
        val media = node(episodes = 0, watched = 3)

        assertFalse(media.hasProgressCard)
    }

    @Test
    fun `a title with no episode total has no progress card`() {
        val media = node(episodes = null, watched = 3)

        assertFalse(media.hasProgressCard)
    }

    // ---- alternativeTitleRows --------------------------------------------------------

    /**
     * The bug this replaces, verbatim.
     *
     * `displayName` prefers English, and the card used to open with a "Main title"
     * row and then list English underneath it - so on any title AniList has an
     * English name for, which is nearly all of them, the card showed the same string
     * on two consecutive rows. The English row is now dropped because it is the one
     * the hero is already showing at full size.
     */
    @Test
    fun `the row for the preferred title is not repeated`() {
        val media = node(
            title = MediaTitle(
                english = "Cowboy Bebop",
                romaji = "Cowboy Bebop",
                native = "カウボーイビバップ"
            )
        )

        assertEquals("Cowboy Bebop", media.displayName)
        assertEquals(
            listOf("Native" to "カウボーイビバップ"),
            media.alternativeTitleRows
        )
    }

    /**
     * Same failure from the other side: with no English title, `displayName` falls
     * back to Romaji, so the Romaji row was the one that repeated.
     */
    @Test
    fun `the row for a romaji fallback is not repeated either`() {
        val media = node(
            title = MediaTitle(romaji = "Shingeki no Kyojin", native = "進撃の巨人")
        )

        assertEquals("Shingeki no Kyojin", media.displayName)
        assertEquals(listOf("Native" to "進撃の巨人"), media.alternativeTitleRows)
    }

    /**
     * When all three names genuinely differ, only the English one drops - because
     * `displayName` prefers English, and the hero already shows it at full size. The
     * two that add something stay.
     */
    @Test
    fun `names that differ from the preferred title are all kept`() {
        val media = node(
            title = MediaTitle(
                english = "Cowboy Bebop",
                romaji = "カウボーイビバップ・ビバップ",
                native = "カウボーイビバップ"
            )
        )

        assertEquals(
            listOf(
                "Romaji" to "カウボーイビバップ・ビバップ",
                "Native" to "カウボーイビバップ"
            ),
            media.alternativeTitleRows
        )
    }

    /**
     * The card is for looking a title *up*, so a name the reader would search AniList
     * with has to survive even when another name is already on screen. The row that
     * is dropped is always the one the hero is showing, never a search term.
     */
    @Test
    fun `a searchable name is kept even alongside the displayed one`() {
        val media = node(
            title = MediaTitle(english = "Bebop", romaji = "Cowboy Bebop")
        )

        assertEquals(
            listOf("Romaji" to "Cowboy Bebop"),
            media.alternativeTitleRows
        )
    }

    @Test
    fun `synonyms are listed last`() {
        val media = node(
            title = MediaTitle(english = "Steins;Gate"),
            synonyms = listOf("Gates of Steiner", "S:G")
        )

        assertEquals(
            listOf("Synonyms" to "Gates of Steiner, S:G"),
            media.alternativeTitleRows
        )
    }

    /**
     * A title with one name has nothing to list. The page asks for the rows before it
     * adds the card as a list item, so this has to come back genuinely empty rather
     * than as one row repeating the hero.
     */
    @Test
    fun `a title with only one name has no rows`() {
        val media = node(title = MediaTitle(english = "Frieren"))

        assertTrue(media.alternativeTitleRows.isEmpty())
    }

    @Test
    fun `a title with no titles at all has no rows`() {
        assertTrue(node().alternativeTitleRows.isEmpty())
    }

    /**
     * Blank names are not names. AniList sends empty strings for titles it has not
     * filled in, and an `English | ` row with nothing after the bar is worse than
     * the row being absent.
     */
    @Test
    fun `blank names are ignored`() {
        val media = node(
            title = MediaTitle(english = "Bebop", romaji = "  ", native = "")
        )

        assertTrue(media.alternativeTitleRows.isEmpty())
    }

    // ---- metaLabel -------------------------------------------------------------------

    /**
     * The relation row's second line.
     *
     * This is the only fact the title page's relation cards gained, and it is the end
     * of a chain - the query asks for the fields, `MediaNode` holds them, and this line
     * is what a reader actually sees. The mistake it exists to prevent is the one it
     * was written for: a card that says a related title's *name* and nothing that would
     * help anybody decide whether to open it.
     */
    @Test
    fun `a relation's meta line carries its format, length and year`() {
        assertEquals(
            "TV · 12 eps · 2024",
            edge(episodes = 12, seasonYear = 2024).metaLabel
        )
    }

    /**
     * A manga has chapters, not episodes.
     *
     * The relation query asks for both counts, so a manga card can say how long the
     * thing is without pretending it aired. Before this the line read "Manga · 2020"
     * beside an anime card's "TV · 12 eps · 2020", which is a metadata line written
     * for one namespace and reused for the other.
     */
    @Test
    fun `a manga relation reports chapters rather than episodes`() {
        assertEquals(
            "Manga · 120 ch · 2020",
            edge(
                format = "MANGA",
                type = "MANGA",
                chapters = 120,
                episodes = 24,
                seasonYear = 2020
            ).metaLabel
        )
    }

    /**
     * Volumes are the fallback when AniList does not know the chapter count.
     *
     * Plenty of manga entries carry volumes and a null chapter count, and the card
     * should still say something about length rather than nothing.
     */
    @Test
    fun `volumes stand in when chapters are unknown`() {
        assertEquals(
            "Manga · 12 vols · 2020",
            edge(format = "MANGA", type = "MANGA", volumes = 12, seasonYear = 2020).metaLabel
        )
    }

    /**
     * "1 eps" is the kind of detail that makes a whole row look machine-generated, and
     * a film is the common single-episode relation.
     */
    @Test
    fun `a single episode is not pluralised`() {
        assertEquals(
            "Movie · 1 ep · 1995",
            edge(format = "MOVIE", episodes = 1, seasonYear = 1995).metaLabel
        )
    }

    /**
     * A zero episode count is AniList saying it does not know, not that the title has no
     * episodes - so it is dropped rather than printed. Same rule as the progress card.
     */
    @Test
    fun `a zero episode count is dropped rather than printed`() {
        assertEquals("TV · 2024", edge(episodes = 0, seasonYear = 2024).metaLabel)
    }

    /**
     * An unannounced title has no season and often no count, and the line still has to
     * say something useful rather than collapsing to an empty string - which is what an
     * unguarded `joinToString` over three nullables produces.
     */
    @Test
    fun `a relation with nothing but a format still has a line`() {
        assertEquals("ONA", edge(format = "ONA", status = null).metaLabel)
    }

    /**
     * The year prefers `seasonYear` and falls back to the start date.
     *
     * AniList sets the season on anything it has scheduled, and an older title often
     * arrives with a full start date and no season - a card that showed no year for
     * those would look broken next to one that did.
     */
    @Test
    fun `the year falls back to the start date when the season is missing`() {
        assertEquals("TV · 1998", edge(seasonYear = null, startYear = 1998).metaLabel)
    }

    /**
     * Status is news or it is noise: a sequel that has not started is the exact case
     * this row exists for, while a finished one is described by its year already.
     */
    @Test
    fun `an upcoming relation says so and a finished one does not`() {
        assertEquals(
            "TV · 12 eps · 2025 · Upcoming",
            edge(status = "NOT_YET_RELEASED", episodes = 12, seasonYear = 2025).metaLabel
        )
        assertEquals(
            "TV · 12 eps · 2024",
            edge(status = "FINISHED", episodes = 12, seasonYear = 2024).metaLabel
        )
    }

    /**
     * The format and the status are spelled by the shared derivations, so the row can
     * never print the raw AniList enum next to a cleaned-up hero chip.
     */
    @Test
    fun `the line is spelled for a reader and not in API vocabulary`() {
        val label = edge(format = "TV_SHORT", status = "RELEASING", episodes = 12).metaLabel

        assertTrue(label, label.startsWith("TV Short"))
        assertTrue(label, label.contains("Airing"))
        assertFalse(label, label.contains("TV_SHORT"))
        assertFalse(label, label.contains("RELEASING"))
    }

    // ---- detailFor ------------------------------------------------------------------

    /**
     * A published detail is only good for the namespace it was fetched in.
     *
     * `MediaDetailStore` fetches by type and id, but the map the screens read is keyed
     * by id alone. A screen that walked from an anime to a manga with the same number
     * would otherwise paint the anime's synopsis, cast and cover over the manga until
     * the real detail arrived.
     */
    @Test
    fun `a detail fetched for the other namespace is not shown`() {
        val anime = MediaNode(id = 1, type = "ANIME", description = "An anime")
        val manga = MediaNode(id = 1, type = "MANGA", description = "A manga")
        val published = mapOf(1 to anime)

        assertNull(published.detailFor(manga))
        assertEquals(anime, published.detailFor(anime))
    }

    // ---- streamingLinks --------------------------------------------------------------

    /**
     * `externalLinks` is three kinds in one list, and only one of them is a place
     * to watch the show. Drawing all three would offer a wiki as a streamer.
     */
    @Test
    fun `only streaming links reach the where-to-watch card`() {
        val media = MediaNode(
            id = 1,
            externalLinks = listOf(
                link(site = "Crunchyroll", type = "STREAMING"),
                link(site = "Wikipedia", type = "INFO", url = "https://en.wikipedia.org/wiki/Test"),
                link(site = "Facebook", type = "SOCIAL", url = "https://facebook.com/test")
            )
        )

        assertEquals(listOf("Crunchyroll"), media.streamingLinks.map { it.site })
    }

    @Test
    fun `a streaming link without a site or a url is not a chip`() {
        val media = MediaNode(
            id = 1,
            externalLinks = listOf(
                link(site = null),
                link(url = null),
                link(site = "Netflix", url = "https://netflix.com/title/1")
            )
        )

        assertEquals(listOf("Netflix"), media.streamingLinks.map { it.site })
    }

    @Test
    fun `the same service listed twice for two regions is one chip`() {
        // AniList routinely files one service twice with different regional URLs.
        // Two identical "Crunchyroll" chips would look like a duplicate, not a
        // choice, so the first link wins.
        val media = MediaNode(
            id = 1,
            externalLinks = listOf(
                link(site = "Crunchyroll", url = "https://crunchyroll.com/a"),
                link(site = "crunchyroll", url = "https://crunchyroll.com/b")
            )
        )

        assertEquals(1, media.streamingLinks.size)
        assertEquals("https://crunchyroll.com/a", media.streamingLinks.single().url)
    }

    // ---- helpers --------------------------------------------------------------------

    private fun link(
        site: String? = "Crunchyroll",
        type: String? = "STREAMING",
        url: String? = "https://crunchyroll.com/test"
    ): MediaExternalLink = MediaExternalLink(url = url, site = site, type = type)

    private fun node(
        title: MediaTitle? = null,
        episodes: Int? = null,
        watched: Int? = null,
        rankings: List<MediaRanking> = emptyList(),
        synonyms: List<String> = emptyList()
    ): MediaNode = MediaNode(
        id = 1,
        title = title,
        episodes = episodes,
        mediaListEntry = watched?.let { SimpleMediaListEntry(progress = it) },
        rankings = rankings,
        synonyms = synonyms
    )

    /**
     * A relation edge carrying only the fields `metaLabel` reads, so a test that fails
     * here fails on the derivation rather than on the fixture.
     */
    private fun edge(
        format: String? = "TV",
        status: String? = "FINISHED",
        episodes: Int? = null,
        chapters: Int? = null,
        volumes: Int? = null,
        seasonYear: Int? = null,
        startYear: Int? = null,
        type: String = "ANIME"
    ): MediaRelationEdge = MediaRelationEdge(
        relationType = "SEQUEL",
        node = MediaNode(
            id = 2,
            type = type,
            format = format,
            status = status,
            episodes = episodes,
            chapters = chapters,
            volumes = volumes,
            seasonYear = seasonYear,
            startDate = startYear?.let { FuzzyDate(year = it) }
        )
    )
}
