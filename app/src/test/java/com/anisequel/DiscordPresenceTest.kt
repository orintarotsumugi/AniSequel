package com.anisequel

import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.domain.discord.DISCORD_TEXT_LIMIT
import com.anisequel.domain.discord.DiscordActivity
import com.anisequel.domain.discord.DiscordActivityKind
import com.anisequel.domain.discord.DiscordJokeInterval
import com.anisequel.domain.discord.DiscordPhrasing
import com.anisequel.domain.discord.DiscordPresenceComposer
import com.anisequel.domain.discord.DiscordRelay
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.discord.DiscordStats
import com.anisequel.domain.jokes.JokeTone
import com.anisequel.domain.usecase.SummarizeDiscordStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The presence's sentence rules.
 *
 * These are the failures that would not throw and would not be seen by the person
 * who caused them: a hidden title leaking through a button, a truncated line cut
 * mid-word, a dangling separator after turning stats off. The preview card cannot
 * be the test for those, because the reader looking at it is the one person whose
 * list is already on their screen.
 */
class DiscordPresenceTest {

    private fun settings(
        enabled: Boolean = true,
        phrasing: Map<DiscordActivityKind, DiscordPhrasing> = emptyMap()
    ) = DiscordSettings(
        enabled = enabled,
        showActivity = true,
        showCover = true,
        showStats = true,
        showJokes = false,
        showButtons = true,
        hideTitles = false,
        jokeInterval = DiscordJokeInterval.TEN,
        jokeTones = JokeTone.entries.toSet(),
        phrasing = phrasing
    )

    private fun activity(
        kind: DiscordActivityKind = DiscordActivityKind.VIEWING_ANIME_DETAILS,
        title: String? = "Frieren",
        status: String? = "Home",
        startedAtMillis: Long = 1_700_000_000_000L
    ) = DiscordActivity(
        kind = kind,
        title = title,
        coverUrl = "https://img.example/frieren.jpg",
        pageUrl = "https://anilist.co/anime/154587",
        progress = "Episode 5",
        status = status,
        startedAtMillis = startedAtMillis
    )

    private val stats = DiscordStats(
        animeCompleted = 24,
        episodesWatched = 318,
        mangaCompleted = 6,
        chaptersRead = 142,
        sequelsFound = 12
    )

    @Test
    fun `the shipped phrasing names the title and the stats`() {
        val presence = DiscordPresenceComposer.compose(settings(), activity(), stats)

        assertEquals("Viewing Frieren", presence.details)
        assertEquals("24 anime · 318 episodes · 6 manga · 142 chapters · 12 sequels", presence.state)
        assertEquals("https://img.example/frieren.jpg", presence.largeImage)
        assertEquals("Frieren", presence.largeText)
        assertEquals(1_700_000_000L, presence.startTimestampSeconds ?: -1L)
    }

    @Test
    fun `an empty stats line does not leave a dangling separator`() {
        val presence = DiscordPresenceComposer.compose(
            settings(),
            activity(),
            DiscordStats.Empty
        )

        assertEquals("Viewing Frieren", presence.details)
        assertNull(presence.state)
    }

    @Test
    fun `hiding titles removes the name, the cover and the AniList button`() {
        val visible = DiscordPresenceComposer.compose(settings(), activity(), stats)
        val presence = DiscordPresenceComposer.compose(
            DiscordSettings(enabled = true, showJokes = false, hideTitles = true),
            activity(),
            stats
        )

        // The visible one is the control: if the switch changed nothing, the
        // assertions below would pass for the wrong reason.
        assertEquals("Viewing Frieren", visible.details)
        assertTrue(visible.buttons.any { it.label == "View on AniList" })
        assertEquals("Viewing an anime", presence.details)
        assertTrue(presence.buttons.none { it.label == "View on AniList" })
        assertNull(presence.largeImage)
        assertNull(presence.largeText)
    }

    @Test
    fun `turning cover art off keeps the title link`() {
        // Separate switches, separate jobs: the cover is a picture, the AniList
        // button is a route. They shared a condition, so turning off the picture
        // also removed the link.
        val presence = DiscordPresenceComposer.compose(
            settings().copy(showCover = false),
            activity(),
            stats
        )

        assertNull(presence.largeImage)
        assertTrue(presence.buttons.any { it.label == "View on AniList" })
    }

    @Test
    fun `an unknown placeholder is removed rather than printed`() {
        val presence = DiscordPresenceComposer.compose(
            settings(
                phrasing = mapOf(
                    DiscordActivityKind.VIEWING_ANIME_DETAILS to
                        DiscordPhrasing("Viewing \$animeTitle", "\$stats")
                )
            ),
            activity(),
            stats
        )

        assertEquals("Viewing", presence.details)
    }

    @Test
    fun `a joke goes on the second line and is not duplicated`() {
        val jokes = settings().copy(showJokes = true)
        val presence = DiscordPresenceComposer.compose(
            jokes,
            activity(),
            DiscordStats.Empty,
            joke = "Sleep is a side quest."
        )

        assertEquals("Sleep is a side quest.", presence.state)

        val inTemplate = DiscordPresenceComposer.compose(
            jokes.copy(
                phrasing = mapOf(
                    DiscordActivityKind.VIEWING_ANIME_DETAILS to
                        DiscordPhrasing("Viewing \$anime", "Thinking about \$joke")
                )
            ),
            activity(),
            DiscordStats.Empty,
            joke = "Sleep is a side quest."
        )

        assertEquals("Thinking about Sleep is a side quest.", inTemplate.state)
    }

    @Test
    fun `the joke can move to the details line`() {
        val presence = DiscordPresenceComposer.compose(
            settings().copy(showJokes = true, jokeInDetails = true),
            activity(),
            DiscordStats.Empty,
            joke = "One more episode."
        )

        assertEquals("Viewing Frieren · One more episode.", presence.details)
        assertNull(presence.state)
    }

    @Test
    fun `turning the rotation off also drops the joke`() {
        // The interval's Off is a second way to say no jokes. The switch alone
        // used to decide this, so the last rotated line stayed in the presence
        // after the reader chose Off and the host stopped replacing it.
        val presence = DiscordPresenceComposer.compose(
            settings().copy(showJokes = true, jokeInterval = DiscordJokeInterval.OFF),
            activity(),
            DiscordStats.Empty,
            joke = "One more episode."
        )

        assertEquals("Viewing Frieren", presence.details)
        assertNull(presence.state)
        assertNull(presence.joke)
    }

    @Test
    fun `a long line is cut to Discord's limit with an ellipsis`() {
        val presence = DiscordPresenceComposer.compose(
            settings(),
            activity(title = "A".repeat(300)),
            DiscordStats.Empty
        )

        assertEquals(DISCORD_TEXT_LIMIT, presence.details.length)
        assertTrue(presence.details.endsWith("…"))
    }

    @Test
    fun `turning activity off still leaves a valid presence`() {
        val presence = DiscordPresenceComposer.compose(
            settings().copy(showActivity = false),
            activity(),
            stats
        )

        assertEquals("Browsing AniSequel", presence.details)
        assertEquals("24 anime · 318 episodes · 6 manga · 142 chapters · 12 sequels", presence.state)
    }

    @Test
    fun `the default is private until the reader opts in`() {
        val defaults = DiscordSettings()

        assertFalse(defaults.enabled)
        assertFalse(defaults.canPublish)
        assertEquals("Rich Presence disabled", defaults.connectionLabel)
    }

    @Test
    fun `the relay rejects plain http and keeps https`() {
        assertEquals(
            "https://relay.example",
            DiscordRelay.normalizeBaseUrl(" https://relay.example/ ")
        )
        assertNull(DiscordRelay.normalizeBaseUrl("http://relay.example"))
        assertNull(DiscordRelay.normalizeBaseUrl("relay.example"))
        assertNull(DiscordRelay.normalizeBaseUrl("https://user:pass@relay.example"))
    }

    @Test
    fun `an application id is a snowflake`() {
        assertTrue(DiscordRelay.isValidApplicationId("123456789012345678"))
        assertFalse(DiscordRelay.isValidApplicationId("AniSequel"))
        assertFalse(DiscordRelay.isValidApplicationId("1234"))
    }

    @Test
    fun `stats count completed titles and real progress`() {
        val collection = MediaListCollection(
            lists = listOf(
                MediaListGroup(
                    entries = listOf(
                        entry(id = 1, type = "ANIME", episodes = 12, progress = 12, status = "COMPLETED"),
                        entry(id = 2, type = "ANIME", episodes = 24, progress = 3, status = "CURRENT"),
                        entry(id = 3, type = "MANGA", chapters = 100, progress = 40, status = "CURRENT"),
                        entry(id = 4, type = "MANGA", chapters = 10, progress = 10, status = "COMPLETED")
                    )
                )
            )
        )

        val folded = SummarizeDiscordStats.execute(collection, sequelsFound = 4)

        assertEquals(1, folded.animeCompleted)
        assertEquals(15, folded.episodesWatched)
        assertEquals(1, folded.mangaCompleted)
        assertEquals(50, folded.chaptersRead)
        assertEquals(4, folded.sequelsFound)
        assertFalse(folded.isEmpty)
    }

    private fun entry(
        id: Int,
        type: String,
        episodes: Int? = null,
        chapters: Int? = null,
        progress: Int?,
        status: String
    ) = MediaListEntryItem(
        status = status,
        progress = progress,
        media = MediaNode(
            id = id,
            type = type,
            title = MediaTitle(userPreferred = "Title $id"),
            episodes = episodes,
            chapters = chapters
        )
    )
}
