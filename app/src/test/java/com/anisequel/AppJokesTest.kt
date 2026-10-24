package com.anisequel

import com.anisequel.ui.components.AppJokes
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The joke toast's pool.
 *
 * The pool is the whole feature: a toast with four lines becomes a catchphrase
 * on the second day, and a pool with a duplicate makes the "different every time"
 * promise a lie in exactly the case a reader notices. These are cheap to pin and
 * invisible in review, which is why they are pinned here.
 */
class AppJokesTest {

    @Test
    fun `the pool is large enough to survive a long session`() {
        assertTrue(
            "the joke pool has shrunk to ${AppJokes.lines.size} lines; " +
                "a random toast needs enough of them not to repeat within a day",
            // 1.5.8 more than doubled the pool. The old floor of 20 was the
            // minimum that could survive an afternoon; this one keeps a session
            // from cycling back to the same line in the same day.
            AppJokes.lines.size >= 150
        )
    }

    @Test
    fun `no line is blank or listed twice`() {
        AppJokes.lines.forEach { line ->
            assertTrue("the joke pool has a blank line", line.isNotBlank())
        }
        assertEquals(
            "the joke pool repeats a line; a repeat is the one thing a random " +
                "toast must not do",
            AppJokes.lines.size,
            AppJokes.lines.distinct().size
        )
    }

    @Test
    fun `pick only ever returns a line from the pool`() {
        val pool = AppJokes.lines.toSet()
        repeat(200) { seed ->
            val line = AppJokes.pick(Random(seed))
            assertTrue("pick returned '$line', which is not in the pool", line in pool)
        }
    }

    @Test
    fun `consecutive picks never repeat the same joke`() {
        var previous: String? = null
        repeat(200) {
            val next = AppJokes.pickDifferent(previous = previous)
            assertNotEquals("the toast repeated its previous line", previous, next)
            previous = next
        }
    }

    @Test
    fun `the shuffle bag uses every line once before repeating any`() {
        // Avoiding only the previous line let the toast tell the same joke again
        // an hour later. A reader experiences the pool across a session, not as
        // adjacent draws, so the whole pool has to cycle.
        val seen = mutableSetOf<String>()
        val drawn = mutableListOf<String>()

        repeat(AppJokes.lines.size) {
            val next = AppJokes.pickUnseen(seen)
            assertFalse("the toast repeated a line before the pool was spent", next in seen)
            seen += next
            drawn += next
        }

        assertEquals(AppJokes.lines.toSet(), drawn.toSet())
    }

    @Test
    fun `a spent pool still answers with a real line`() {
        val next = AppJokes.pickUnseen(AppJokes.lines.toSet())

        assertTrue("the bag returned a line that is not in the pool", next in AppJokes.lines)
    }
}
