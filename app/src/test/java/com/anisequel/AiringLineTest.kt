package com.anisequel

import com.anisequel.data.model.AiringCountdown
import com.anisequel.data.model.AiringSchedule
import com.anisequel.data.model.AiringScheduleConnection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.NextAiringEpisode
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.airingCountdown
import com.anisequel.data.model.airingLineAt
import com.anisequel.data.model.progressCardStatesAiring
import com.anisequel.data.model.upcomingAiring
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The one sentence the app says about a show that has not finished airing.
 *
 * ## What was wrong
 *
 * Three things, all of them visible on a currently airing show:
 *
 *  - The countdown truncated its unit. "in 5h 20m" printed as "in 5h", so the
 *    line was twenty minutes wrong for an hour and then stood still for that
 *    hour. Days were truncated the same way: "in 3d 20h" was "in 3d".
 *  - The sentence needed a `/ total` to exist. AniList reports no `episodes`
 *    for a show whose length is not announced yet, and every surface that printed
 *    the airing line decided whether to print it from the episode count - so a
 *    weekly show with a known slot and an unknown length said nothing at all.
 *  - Nothing fell back to AniList's documented schedule when `nextAiringEpisode`
 *    was null. The docs describe `airingSchedule(notYetAired: true)` for exactly
 *    that, and the API only promises future airing data is complete - which is
 *    what the filter asks for.
 *
 * The denominator is optional; the sentence is not.
 */
class AiringLineTest {

    private val now = 1_700_000_000_000L
    private val seconds = now / 1000

    private fun anime(
        episodes: Int? = 12,
        episode: Int? = 5,
        airingAt: Long? = seconds + 5 * 3600 + 20 * 60,
        progress: Int? = 3,
        schedule: List<Pair<Int, Long>>? = null
    ) = MediaNode(
        id = 1,
        title = MediaTitle(english = "A show"),
        status = "RELEASING",
        episodes = episodes,
        nextAiringEpisode = airingAt?.let { NextAiringEpisode(episode = episode, airingAt = it) },
        airingSchedule = schedule?.let { slots ->
            AiringScheduleConnection(
                nodes = slots.map { (number, at) -> AiringSchedule(episode = number, airingAt = at) }
            )
        },
        mediaListEntry = progress?.let { SimpleMediaListEntry(status = "CURRENT", progress = it) }
    )

    @Test
    fun `the last day of a countdown is counted in hours and minutes`() {
        // The exact cases the truncation got wrong. An hour and twenty minutes
        // were dropped from the two answers a reader is actually waiting on.
        assertEquals("in 5h 20m", AiringCountdown.format(seconds + 5 * 3600 + 20 * 60, now))
        assertEquals("in 2d 4h", AiringCountdown.format(seconds + 2 * 86_400 + 4 * 3600, now))
        assertEquals("in 40m", AiringCountdown.format(seconds + 40 * 60, now))
    }

    @Test
    fun `a whole unit is not padded with a zero`() {
        // "in 5h 0m" reads as a stopped clock; the zero unit is dropped instead.
        assertEquals("in 5h", AiringCountdown.format(seconds + 5 * 3600, now))
        assertEquals("in 3d", AiringCountdown.format(seconds + 3 * 86_400, now))
        assertEquals("now", AiringCountdown.format(seconds + 5, now))
    }

    @Test
    fun `a show with no announced length still names its episode`() {
        // The Dragon Ball Super case: AniList knows the weekly slot and the
        // episode number, and reports no total. This printed nothing anywhere.
        val unknownLength = anime(episodes = null, episode = 12, progress = 130)

        assertEquals("Episode 12 airs in 5h 20m", unknownLength.airingLineAt(now)?.sentence)
        assertEquals("Episode 12", unknownLength.airingLineAt(now)?.episodeDetail)
    }

    @Test
    fun `the known length is named when AniList has one`() {
        val known = anime(episodes = 131, episode = 12)

        assertEquals("Episode 12 of 131 airs in 5h 20m", known.airingLineAt(now)?.sentence)
        // The details grid labels the row itself, so its value repeats neither the
        // noun nor the verb.
        assertEquals("12 of 131 · in 5h 20m", known.airingLineAt(now)?.specValue)
    }

    @Test
    fun `a zero episode count is unknown rather than a length of zero`() {
        // AniList answers 0 for "not announced yet". The unguarded label printed
        // "Episode 12 of 0" on exactly those titles.
        assertEquals("Episode 12", anime(episodes = 0, episode = 12).airingLineAt(now)?.episodeDetail)
    }

    @Test
    fun `the schedule answers when the next airing field is empty`() {
        // `nextAiringEpisode` null, weekly slots known: the documented
        // `airingSchedule(notYetAired: true)` is the source of the answer.
        val weekly = anime(
            episodes = null,
            airingAt = null,
            progress = 130,
            schedule = listOf(12 to seconds + 5 * 3600 + 20 * 60, 13 to seconds + 7 * 86_400)
        )

        assertEquals("Episode 12 airs in 5h 20m", weekly.airingLineAt(now)?.sentence)
        assertEquals(seconds + 5 * 3600 + 20 * 60, weekly.upcomingAiring(now)?.airingAtSeconds)
    }

    @Test
    fun `the schedule moves the line on after the explicit airing has passed`() {
        // A detail cached before the episode aired still carries the old
        // `nextAiringEpisode`. The stale field must not win over a slot the
        // schedule says is still ahead.
        val stale = anime(
            episode = 11,
            airingAt = seconds - 86_400,
            schedule = listOf(11 to seconds - 86_400, 12 to seconds + 6 * 86_400)
        )

        assertEquals(12, stale.upcomingAiring(now)?.episode)
        assertEquals("Episode 12 airs in 6d", stale.airingLineAt(now)?.sentence)
    }

    @Test
    fun `an airing without an episode number still counts down`() {
        val unnamed = anime(episodes = 12, episode = null)

        assertEquals("Next episode airs in 5h 20m", unnamed.airingLineAt(now)?.sentence)
        assertEquals("in 5h 20m", unnamed.airingLineAt(now)?.specValue)
    }

    @Test
    fun `a title with no airing has nothing to say`() {
        val finished = anime(airingAt = null)

        assertNull(finished.upcomingAiring(now))
        assertNull(finished.airingCountdown(now))
        assertNull(finished.airingLineAt(now)?.sentence)
    }

    @Test
    fun `the progress card only claims the sentence when it can print it`() {
        // True only when the wave can be drawn *and* the line has a number and a
        // time - the combination that leaves the details grid silent.
        assertTrue(anime(episodes = 12).progressCardStatesAiring)
        // The bug: a tracked show with an unannounced length has progress and an
        // airing, but no wave - so the grid was told the progress card had it.
        assertFalse(anime(episodes = null).progressCardStatesAiring)
        // No progress recorded, no progress card.
        assertFalse(anime(progress = null).progressCardStatesAiring)
        // No airing at all, nothing for the card to state.
        assertFalse(anime(airingAt = null).progressCardStatesAiring)
    }
}
