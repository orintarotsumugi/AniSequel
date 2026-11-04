package com.anisequel

import com.anisequel.data.repository.MAX_PAGED_CACHE_ENTRIES
import com.anisequel.data.repository.expiredThenOldestKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The paged caches have to stop growing.
 *
 * Every page cache in the repository expires on the reader's refresh interval, but
 * expiry is only consulted when the same key is looked up again - so the twenty
 * activity pages and a dozen profiles a reader opened on the way *in* were never
 * released, because nobody went back to them. The maps grew for the whole session
 * and the cost showed up as a hitch while flinging a list. These pin the eviction
 * rule that bounds them: what gets dropped, and in what order.
 */
class PagedCacheEvictionTest {

    private val ttl = 60_000L
    private val now = 1_000_000L

    @Test
    fun `a cache under its ceiling keeps everything`() {
        val stamps = (1..MAX_PAGED_CACHE_ENTRIES).associate { "page:$it" to now - it }
        assertTrue(expiredThenOldestKeys(stamps, now, ttl, MAX_PAGED_CACHE_ENTRIES).isEmpty())
    }

    @Test
    fun `the oldest page goes first when nothing has expired`() {
        val stamps = (1..41).associate { index -> "p$index" to now - index * 1_000L }
        val dropped = expiredThenOldestKeys(stamps, now, ttl, ceiling = 40)
        // `p41` carries the smallest stamp, so it is the page the reader left first.
        assertEquals(listOf("p41"), dropped)
    }

    @Test
    fun `expired entries are dropped before live ones are considered`() {
        val stamps = buildMap {
            // Thirty live pages, ten long-expired, and one over the ceiling.
            (1..30).forEach { put("live$it", now - 1_000L) }
            (1..10).forEach { put("dead$it", now - 10 * ttl) }
            put("extra", now - 500L)
        }
        val dropped = expiredThenOldestKeys(stamps, now, ttl, 40)
        assertEquals(10, dropped.count { it.startsWith("dead") })
        // Dropping the ten expired pages alone gets the map back under the ceiling,
        // so no live page is thrown away to satisfy it.
        assertEquals(0, dropped.count { !it.startsWith("dead") })
    }

    @Test
    fun `a live page is dropped only when the expired ones were not enough`() {
        val stamps = buildMap {
            (1..39).forEach { index -> put("live$index", now - index * 1_000L) }
            put("dead", now - 10 * ttl)
            put("newest", now - 100L)
        }
        // Forty-one entries against a ceiling of thirty-nine: two must go, and the
        // one expired page is not enough, so the oldest live write follows it out.
        // (Thirty-nine live plus the expired page plus the newest - the count is the
        // whole test, and it was one short when this first ran.)
        val dropped = expiredThenOldestKeys(stamps, now, ttl, ceiling = 39)
        assertEquals(2, dropped.size)
        assertTrue("the expired page was not dropped", dropped.contains("dead"))
        assertTrue("live39 was the oldest live write", dropped.contains("live39"))
    }

    @Test
    fun `every expired entry goes, not just enough to clear the ceiling`() {
        val stamps = (1..41).associate { index -> "p$index" to now - 10 * ttl }
        // All of them are outside the TTL, so all of them are wrong answers being
        // served as cached ones; the ceiling is the reason to look, not the reason
        // to keep a stale page.
        assertEquals(41, expiredThenOldestKeys(stamps, now, ttl, ceiling = 40).size)
    }
}
