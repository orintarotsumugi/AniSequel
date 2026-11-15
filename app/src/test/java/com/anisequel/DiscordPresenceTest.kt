package com.anisequel

import com.anisequel.data.discord.DiscordPresenceJson
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.domain.discord.DISCORD_TEXT_LIMIT
import com.anisequel.domain.discord.DiscordActivity
import com.anisequel.domain.discord.DiscordActivityKind
import com.anisequel.domain.discord.DiscordActivityType
import com.anisequel.domain.discord.DiscordJokeInterval
import com.anisequel.domain.discord.DiscordLogin
import com.anisequel.domain.discord.DiscordOnlineStatus
import com.anisequel.domain.discord.DiscordPhrasing
import com.anisequel.domain.discord.DiscordPresenceComposer
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.discord.DiscordStats
import com.anisequel.domain.jokes.JokeTone
import com.anisequel.domain.usecase.SummarizeDiscordStats
import com.anisequel.ui.components.discord.DiscordJokes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscordPresenceTest {

    private fun settings(
        enabled: Boolean = true,
        phrasing: Map<DiscordActivityKind, DiscordPhrasing> = emptyMap()
    ) = DiscordSettings(
        enabled = enabled,
        token = "mfa.test-token-value-that-is-long-enough",
        linked = true,
        username = "Reader",
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
        val presence = DiscordPresenceComposer.compose(settings(), activity(), DiscordStats.Empty)
        assertEquals("Viewing Frieren", presence.details)
        assertNull(presence.state)
    }

    @Test
    fun `hiding titles removes the name, the cover and the AniList button`() {
        val visible = DiscordPresenceComposer.compose(settings(), activity(), stats)
        val presence = DiscordPresenceComposer.compose(
            settings().copy(hideTitles = true),
            activity(),
            stats
        )

        assertEquals("Viewing Frieren", visible.details)
        assertTrue(visible.buttons.any { it.label == "View on AniList" })
        assertEquals("Viewing an anime", presence.details)
        assertTrue(presence.buttons.none { it.label == "View on AniList" })
        assertNull(presence.largeImage)
        assertNull(presence.largeText)
    }

    @Test
    fun `turning cover art off keeps the title link`() {
        val presence = DiscordPresenceComposer.compose(settings().copy(showCover = false), activity(), stats)
        assertNull(presence.largeImage)
        assertTrue(presence.buttons.any { it.label == "View on AniList" })
    }

    @Test
    fun `elapsed time can be hidden without changing the text`() {
        val presence = DiscordPresenceComposer.compose(settings().copy(showElapsedTime = false), activity(), stats)
        assertNull(presence.startTimestampSeconds)
        assertEquals("Viewing Frieren", presence.details)
    }

    @Test
    fun `custom activity name and buttons are applied`() {
        val presence = DiscordPresenceComposer.compose(
            settings().copy(
                activityName = "AniSequel RPC",
                primaryButtonLabel = "Open title",
                secondaryButtonLabel = "Project",
                secondaryButtonUrl = "https://example.com/project"
            ),
            activity(),
            stats
        )
        assertEquals("AniSequel RPC", presence.activityName)
        assertEquals("Open title", presence.buttons.first().label)
        assertEquals("Project", presence.buttons.last().label)
        assertEquals("https://example.com/project", presence.buttons.last().url)
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
        val presence = DiscordPresenceComposer.compose(jokes, activity(), DiscordStats.Empty, joke = "Sleep is a side quest.")
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
        val presence = DiscordPresenceComposer.compose(settings(), activity(title = "A".repeat(300)), DiscordStats.Empty)
        assertEquals(DISCORD_TEXT_LIMIT, presence.details.length)
        assertTrue(presence.details.endsWith("…"))
    }

    @Test
    fun `turning activity off still leaves a valid presence`() {
        val presence = DiscordPresenceComposer.compose(settings().copy(showActivity = false), activity(), stats)
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
    fun `token login validation normalizes and masks plausible tokens`() {
        val raw = "  mfa.abcdefghijklmnopqrstuvwxyz1234567890  "
        val normalized = DiscordLogin.normalizeToken(raw)
        assertTrue(DiscordLogin.looksLikeToken(normalized))
        assertEquals("mfa.••••••••7890", DiscordLogin.maskedToken(normalized))
        assertFalse(DiscordLogin.looksLikeToken("short"))
    }

    @Test
    fun `gateway presence payload includes activity type status and timestamps`() {
        val presence = DiscordPresenceComposer.compose(settings(), activity(), DiscordStats.Empty)
        val json = DiscordPresenceJson.gatewayPresenceUpdate(
            presence = presence,
            status = DiscordOnlineStatus.IDLE,
            activityType = DiscordActivityType.WATCHING
        )
        assertTrue(json.contains("\"op\":3"))
        assertTrue(json.contains("\"status\":\"idle\""))
        assertTrue(json.contains("\"type\":3"))
        assertTrue(json.contains("\"details\":\"Viewing Frieren\""))
        assertTrue(json.contains("\"start\":1700000000000"))
    }

    @Test
    fun `invisible status publishes no activity`() {
        val presence = DiscordPresenceComposer.compose(settings(), activity(), stats)
        val json = DiscordPresenceJson.gatewayPresenceUpdate(
            presence = presence,
            status = DiscordOnlineStatus.INVISIBLE,
            activityType = DiscordActivityType.PLAYING
        )

        assertTrue(json.contains("\"activities\":[]"))
        assertTrue(json.contains("\"status\":\"invisible\""))
    }

    @Test
    fun `a link without a token is not a connection`() {
        // A relay-era install carries the old "linked" flag but no token, because
        // the relay held the credentials.
        val relayEra = DiscordSettings(enabled = true, linked = true, token = "", username = "Old")
        assertFalse(relayEra.canPublish)
        assertEquals("Preview only - no Discord account linked", relayEra.connectionLabel)

        val direct = relayEra.copy(token = "mfa.test-token-value-that-is-long-enough")
        assertTrue(direct.canPublish)
    }

    @Test
    fun `a secondary button with a non-http url is dropped`() {
        val presence = DiscordPresenceComposer.compose(
            settings().copy(secondaryButtonUrl = "javascript:alert(1)"),
            activity(),
            DiscordStats.Empty
        )
        assertTrue(presence.buttons.none { it.label == "AniSequel on GitHub" })
    }

    @Test
    fun `a rotation skips the lines it just quoted`() {
        val pool = DiscordJokes.pool(settings())
        assertTrue(pool.size > 2)
        val recent = pool.dropLast(1)

        repeat(8) {
            assertEquals(pool.last(), DiscordJokes.next(settings(), recent))
        }
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
