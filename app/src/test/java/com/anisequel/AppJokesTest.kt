package com.anisequel

import com.anisequel.ui.components.AppJokes
import kotlin.random.Random
import org.junit.Assert.assertEquals
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
            AppJokes.lines.size >= 20
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
}
