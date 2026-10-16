package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRanking
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.alternativeTitleRows
import com.anisequel.data.model.displayName
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.hasRankings
import com.anisequel.data.model.progressCountLabel
import com.anisequel.data.model.watchFraction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
 * show still left a hole in the page. Each of the four sections below returns early
 * for entirely ordinary titles - a show nobody has started, a show with no staff
 * listing, a show with no alternative titles, a show with no ranking.
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
        // It is also what the Library tab already does: `libraryProgressLabel`
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

    // ---- helpers --------------------------------------------------------------------

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
}
