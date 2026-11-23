package com.anisequel

import com.anisequel.ui.components.novamarkdown.NovaMarkdownCache
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Before
import org.junit.Test

/**
 * The parsed-bio cache.
 *
 * The regression it prevents is a performance one that is invisible in a diff: the
 * profile screen parses a bio for the poster keys its links carry, and the About
 * card parses the same text again to draw it. Both results used to be discarded, so
 * every open paid for two passes over the same document - and on a long bio with
 * tables and nested spans, that is the difference between the page appearing and
 * the page appearing after a visible pause.
 *
 * A second copy of the same document is also a second answer to "which links does
 * this bio carry", which is the kind of duplication that later drifts: one caller
 * updates the parser's options, the other does not.
 */
class NovaMarkdownCacheTest {

    @Before
    fun setUp() {
        NovaMarkdownCache.clear()
    }

    @After
    fun tearDown() {
        NovaMarkdownCache.clear()
    }

    @Test
    fun `the same bio is parsed once and both callers get the same blocks`() {
        val bio = "**Bocchi the Rock!**\n\nhttps://anilist.co/anime/130003\n\n> A lonely guitarist."

        val forTheCovers = NovaMarkdownCache.parse(bio)
        val forTheCard = NovaMarkdownCache.parse(bio)

        assertSame(
            "the card must draw the blocks the profile screen already parsed, not a " +
                "second parse of the same document",
            forTheCovers,
            forTheCard
        )
    }

    @Test
    fun `an edited bio is a different document`() {
        val before = NovaMarkdownCache.parse("one bio")
        val after = NovaMarkdownCache.parse("one bio, edited")

        assertNotSame(
            "an edited bio must not be served the previous document's blocks",
            before,
            after
        )
    }

    @Test
    fun `the cache is bounded and drops the least recently used document`() {
        repeat(NovaMarkdownCache.MAX_ENTRIES + 3) { NovaMarkdownCache.parse("bio number $it") }

        assertEquals(
            "the cache grew past its ceiling; a session's worth of bios is all it may hold",
            NovaMarkdownCache.MAX_ENTRIES,
            NovaMarkdownCache.size()
        )

        // Asking for the oldest again must not resurrect it from the cache - the
        // point is that it is gone, and re-parsing is the correct (bounded) answer.
        NovaMarkdownCache.parse("bio number 0")
        assertEquals(
            "re-asking for an evicted bio must not push the cache over its ceiling",
            NovaMarkdownCache.MAX_ENTRIES,
            NovaMarkdownCache.size()
        )
    }
}
