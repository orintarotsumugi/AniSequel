package com.example

import com.example.data.repository.DemoProfileProvider
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo activity fixture.
 *
 * Demo mode is the only path that renders the profile's activity feed without a
 * session, so it is also the only place a whole-card regression can be seen
 * before release - there is no account to open and no server to ask.
 *
 * Three properties of the fixture are load-bearing for `ActivityCard`, and all
 * three are invisible when they are wrong:
 *
 *  - The feed has to contain **both** media types. The card labels every row
 *    "Anime" or "Manga" from `media.type` and words its progress line as episodes
 *    or chapters from the same field. A fixture made only of anime exercises the
 *    anime branch on every row and the manga branch on none, which is how a manga
 *    regression reaches a release without anybody seeing it.
 *  - A manga row must carry **no episode count**. There is no chapter total in
 *    this payload, so the card deliberately prints "Chapter 42" on its own; a
 *    fixture that gave a manga an `episodes` value would make the anime branch
 *    look correct for it and hide a wrong "Episode N of M".
 *  - Every row must resolve to a media node with a title. The card returns early
 *    for a row with no media at all, so such a row is a silent missing card.
 *
 * Pure JVM, no Robolectric: this is fixture data plus the model, and the point is
 * that it can be asserted without an Android runtime.
 */
class DemoActivityFixtureTest {

    private val feed = DemoProfileProvider.getDemoActivity()

    private fun isManga(status: String?) = status.equals("MANGA", ignoreCase = true)

    @Test
    fun `the demo feed contains both anime and manga`() {
        assertTrue(
            "the demo activity feed has no manga row, so the card's Manga label and its " +
                "\"Chapter N\" wording are never rendered offline",
            feed.any { isManga(it.media?.type) }
        )
        assertTrue(
            "the demo activity feed has no anime row",
            feed.any { it.media?.type.equals("ANIME", ignoreCase = true) }
        )
    }

    @Test
    fun `no demo manga row carries an episode count`() {
        val offenders = feed
            .filter { isManga(it.media?.type) }
            .filter { it.media?.episodes != null }
            .map { it.media?.title?.displayTitle.orEmpty() }

        assertTrue(
            "a manga row in the demo feed has an episode count ($offenders). The card " +
                "reads episodes only for anime, so this would hide a wrong " +
                "\"Episode N of M\" behind a manga row.",
            offenders.isEmpty()
        )
    }

    @Test
    fun `every demo activity resolves to a titled media node`() {
        val blank = feed.filter {
            it.media == null || it.media?.title?.displayTitle.isNullOrBlank()
        }

        assertTrue(
            "the demo feed has ${blank.size} row(s) with no media or no title. The card " +
                "returns early for the first, so the row renders as a gap in the list " +
                "rather than as an error.",
            blank.isEmpty()
        )
    }

    /**
     * The activity ids are what the feed's `LazyColumn` is keyed on.
     *
     * A duplicate key throws at runtime rather than drawing one row, and the
     * fixture builds its ids by hand precisely because deriving them from the
     * media id collides - two rows for one show is the normal case (watched, then
     * completed).
     */
    @Test
    fun `demo activity ids are unique`() {
        val duplicates = feed.groupingBy { it.id }.eachCount().filterValues { it > 1 }.keys

        assertTrue(
            "the demo feed lists the activity id(s) $duplicates more than once, which is a " +
                "duplicate key in the activity LazyColumn",
            duplicates.isEmpty()
        )
    }
}
