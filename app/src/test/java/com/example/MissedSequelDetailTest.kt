package com.example

import com.example.data.model.MediaCoverImage
import com.example.data.model.MediaNode
import com.example.data.model.MediaTitle
import com.example.data.model.MissedSequel
import com.example.data.model.NextAiringEpisode
import com.example.data.model.RelationKind
import com.example.data.model.SimpleMediaListEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the card now tells you about an entry.
 *
 * The airing countdown and the watch-progress bar are both derived values on the
 * hot path - recomputed on every card during a scroll - and both were wrong in
 * ways a screenshot would not reveal: a countdown computed in the wrong unit is
 * off by a factor of a thousand, and a progress fraction that divides by a null
 * total crashes the card.
 */
class MissedSequelDetailTest {

    private fun sequel(
        episodes: Int? = 12,
        progress: Int? = null,
        airingAt: Long? = null,
        status: String = "RELEASING",
        description: String? = null,
        season: Int? = null,
        score: Int? = null
    ) = MissedSequel(
        parentId = 1,
        parentTitle = "Season 1",
        relationType = RelationKind.SEQUEL.apiValue,
        sequelMedia = MediaNode(
            id = 2,
            title = MediaTitle(english = "Season 2"),
            episodes = episodes,
            season = season,
            status = status,
            averageScore = score,
            description = description,
            nextAiringEpisode = airingAt?.let {
                NextAiringEpisode(episode = 5, airingAt = it)
            },
            mediaListEntry = SimpleMediaListEntry(status = "CURRENT", progress = progress)
        )
    )

    private val now = 1_700_000_000_000L

    @Test
    fun `the countdown is computed in seconds to milliseconds`() {
        // AniList reports `airingAt` in SECONDS. Treating that as milliseconds
        // made every countdown land in 1970 and render as "now" forever.
        val threeDaysOut = (now / 1000) + (3 * 24 * 60 * 60)
        assertEquals("in 3d", sequel(airingAt = threeDaysOut).nextAiringCountdown(now))
    }

    @Test
    fun `the countdown covers hours minutes and the immediate case`() {
        val seconds = now / 1000
        assertEquals("in 5h", sequel(airingAt = seconds + 5 * 3600).nextAiringCountdown(now))
        assertEquals("in 30m", sequel(airingAt = seconds + 30 * 60).nextAiringCountdown(now))
        assertEquals("now", sequel(airingAt = seconds + 5).nextAiringCountdown(now))
    }

    @Test
    fun `an entry that has already aired has no countdown`() {
        val past = (now / 1000) - 3600
        // Returning "in -1h" or "now" for something that finished an hour ago
        // is worse than saying nothing.
        assertNull(sequel(airingAt = past).nextAiringCountdown(now))
        assertNull(sequel(airingAt = null).nextAiringCountdown(now))
    }

    @Test
    fun `watch progress is a clamped fraction of the episode count`() {
        assertEquals(0.5f, sequel(episodes = 12, progress = 6).watchProgress()!!, 0.001f)
        assertEquals(1f, sequel(episodes = 12, progress = 12).watchProgress()!!, 0.001f)
    }

    @Test
    fun `progress beyond the episode count is clamped rather than overflowing`() {
        // Someone who has watched more than the listed total - a recap movie
        // counted twice, a stale total - otherwise drew a bar wider than its
        // track.
        assertEquals(1f, sequel(episodes = 12, progress = 40).watchProgress()!!, 0.001f)
    }

    @Test
    fun `progress is absent rather than invented when there is nothing to show`() {
        // A zero-length bar is worse than no bar: it reads as "0% watched".
        assertNull(sequel(episodes = null, progress = 3).watchProgress())
        assertNull(sequel(episodes = 0, progress = 3).watchProgress())
        assertNull(sequel(episodes = 12, progress = null).watchProgress())
        assertNull(sequel(episodes = 12, progress = 0).watchProgressLabel())
    }

    @Test
    fun `the progress label reads as a fraction of the total`() {
        assertEquals("6 / 12 eps", sequel(episodes = 12, progress = 6).watchProgressLabel())
        assertNull(sequel(episodes = 12, progress = null).watchProgressLabel())
    }

    @Test
    fun `the synopsis has html and collapsed whitespace removed`() {
        val entry = sequel(
            description = "<p>Attack on Titan</p>\n\n   <em> humanity</em> fights back."
        )

        val text = entry.description!!
        assertTrue("html tags survived: $text", !text.contains("<"))
        assertTrue("a newline survived: $text", !text.contains("\n"))
        assertEquals("Attack on Titan humanity fights back.", text)
    }

    @Test
    fun `an entry with no synopsis yields null rather than an empty string`() {
        // An empty string is not the same as absent: the sheet checks for blank
        // to decide whether to show the "Read more" affordance.
        assertNull(sequel(description = null).description)
    }

    @Test
    fun `the cover colour is surfaced for the placeholder`() {
        val entry = MissedSequel(
            parentId = 1,
            parentTitle = "P",
            sequelMedia = MediaNode(
                id = 2,
                coverImage = MediaCoverImage(large = "https://x/y.jpg", color = "#f1a143")
            )
        )
        assertEquals("#f1a143", entry.coverColor)
        assertNull(sequel().coverColor)
    }
}