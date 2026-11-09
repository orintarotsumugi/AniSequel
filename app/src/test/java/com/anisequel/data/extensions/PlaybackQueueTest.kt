package com.anisequel.data.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Pins the process-scoped playback queue, including the lazy-server write-back
 * the sandboxed runtime needs. The next-at-end and resume-at-server-switch cases
 * are the Phase 1.5 regressions; updateEpisode is the Phase 2 addition.
 */
class PlaybackQueueTest {

    @Before
    fun reset() {
        PlaybackQueue.clear()
    }

    private fun episode(id: String, vararg servers: SourceServer) = SourceEpisode(
        id = id,
        name = id,
        number = id.removePrefix("ep").toIntOrNull() ?: 0,
        thumbnailUrl = null,
        servers = servers.toList()
    )

    private fun server(name: String, quality: String? = null) =
        SourceServer(id = name, name = name, url = "https://x/$name", quality = quality)

    private fun prepare(vararg episodes: SourceEpisode, startMs: Long = 0L) {
        PlaybackQueue.prepare(
            PlaybackContext(
                mediaId = 1,
                mediaTitle = "Show",
                coverUrl = null,
                sourceId = "src",
                animeId = "anime",
                episodes = episodes.toList()
            ),
            index = 0,
            server = episodes.firstOrNull()?.servers?.firstOrNull(),
            startPositionMs = startMs
        )
    }

    @Test
    fun `next advances and refuses past the end`() {
        prepare(episode("ep1"), episode("ep2"))
        assertTrue(PlaybackQueue.hasNext())
        assertEquals("ep2", PlaybackQueue.moveNext(null)?.id)
        assertFalse(PlaybackQueue.hasNext())
        assertNull(PlaybackQueue.moveNext(null))
        assertEquals("ep2", PlaybackQueue.episode()?.id)
    }

    @Test
    fun `previous refuses before the start`() {
        prepare(episode("ep1"), episode("ep2"))
        assertFalse(PlaybackQueue.hasPrevious())
        assertNull(PlaybackQueue.movePrevious(null))
        PlaybackQueue.moveNext(null)
        assertEquals("ep1", PlaybackQueue.movePrevious(null)?.id)
    }

    @Test
    fun `select jumps within the queue and ignores out-of-range`() {
        prepare(episode("ep1"), episode("ep2"), episode("ep3"))
        assertEquals("ep3", PlaybackQueue.select(2, null)?.id)
        assertNull(PlaybackQueue.select(9, null))
        assertEquals("ep3", PlaybackQueue.episode()?.id)
    }

    @Test
    fun `advancing clears the resume point but a server switch keeps it`() {
        prepare(episode("ep1", server("a")), episode("ep2", server("b")), startMs = 5_000L)
        assertEquals(5_000L, PlaybackQueue.startPositionMs)
        PlaybackQueue.switchServer(server("c"))
        assertEquals("c", PlaybackQueue.server?.name)
        assertEquals(5_000L, PlaybackQueue.startPositionMs)
        PlaybackQueue.resumeAt(9_000L)
        assertEquals(9_000L, PlaybackQueue.startPositionMs)
        PlaybackQueue.moveNext(null)
        assertEquals(0L, PlaybackQueue.startPositionMs)
    }

    @Test
    fun `updateEpisode writes resolved servers back into the queue`() {
        prepare(episode("ep1"), episode("ep2"))
        PlaybackQueue.updateEpisode(episode("ep2", server("1080p", "1080p")))
        assertEquals(listOf("1080p"), PlaybackQueue.context?.episodes?.get(1)?.servers?.map { it.name })
        assertEquals(0, PlaybackQueue.context?.episodes?.get(0)?.servers?.size)
    }

    @Test
    fun `updateEpisode ignores an unknown id`() {
        prepare(episode("ep1"))
        PlaybackQueue.updateEpisode(episode("ghost", server("x")))
        assertEquals(1, PlaybackQueue.context?.episodes?.size)
        assertEquals("ep1", PlaybackQueue.context?.episodes?.first()?.id)
    }

    @Test
    fun `a remembered server wins over quality`() {
        val episode = episode("ep1", server("720p", "720p"), server("1080p", "1080p"))
        assertEquals("720p", chooseServer(episode, "720p", "1080p")?.name)
    }

    @Test
    fun `preferred quality picks the matching file`() {
        val episode = episode("ep1", server("low", "480p"), server("high", "1080p"))
        assertEquals("high", chooseServer(episode, null, "1080p")?.name)
    }

    @Test
    fun `the first file is the fallback`() {
        val episode = episode("ep1", server("low", "480p"), server("high", "1080p"))
        assertEquals("low", chooseServer(episode, null, "4K")?.name)
        assertEquals("low", chooseServer(episode, "missing", null)?.name)
        assertNull(chooseServer(episode("ep1"), null, null))
    }
}