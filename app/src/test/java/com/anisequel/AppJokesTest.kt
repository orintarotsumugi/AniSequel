package com.anisequel

import com.anisequel.domain.jokes.JokeTone
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
 *
 * The tone filter adds a second way to break the pool quietly: a filter that
 * draws from the wrong list, or a tone with too few lines to cycle, looks fine in
 * the Settings screen and only shows up as a repetitive toast. Both are pinned
 * here because they are pure functions of the object.
 */
class AppJokesTest {

    @Test
    fun `the pool is large enough to survive a long session`() {
        assertTrue(
            "the joke pool has shrunk to ${AppJokes.lines.size} lines; " +
                "a random toast needs enough of them not to repeat within a day",
            // 1.5.9 crossed 300. The floor is checked against the *whole* pool,
            // not per tone: a reader with one tone on gets a smaller bag by
            // choice, and `every tone has enough lines to stand alone` is the
            // floor that keeps that choice from being a loop.
            AppJokes.lines.size >= 300
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
    fun `every tone has enough lines to stand alone`() {
        JokeTone.entries.forEach { tone ->
            val count = AppJokes.pool.count { it.tone == tone }
            assertTrue(
                "$tone has only $count lines; a reader who switches the other " +
                    "tones off would see the same handful of jokes in a session",
                count >= 40
            )
        }
    }

    @Test
    fun `every tone in the domain is used by the pool`() {
        assertEquals(
            "a tone the reader can switch on but no line uses would be a filter " +
                "that silently removes every joke",
            JokeTone.entries.toSet(),
            AppJokes.pool.map { it.tone }.toSet()
        )
    }

    @Test
    fun `a tone filter only draws from that tone`() {
        repeat(200) { seed ->
            val line = AppJokes.pick(setOf(JokeTone.CRAFT), Random(seed))
            val joke = AppJokes.pool.first { it.text == line }

            assertEquals("pick returned a line outside the CRAFT filter", JokeTone.CRAFT, joke.tone)
        }
    }

    @Test
    fun `an empty filter means the whole pool`() {
        // "Nothing selected" is what a cleared preference looks like. It has to
        // mean everything, not an empty draw that makes the toast disappear.
        assertEquals(AppJokes.pool, AppJokes.poolFor(emptySet()))
        assertEquals(AppJokes.pool.size, AppJokes.countFor(emptySet()))
    }

    @Test
    fun `the shuffle bag uses every filtered line once before repeating any`() {
        // Avoiding only the previous line let the toast tell the same joke again
        // an hour later. A reader experiences the pool across a session, not as
        // adjacent draws, so the whole filtered pool has to cycle.
        val tones = setOf(JokeTone.META)
        val expected = AppJokes.poolFor(tones).map { it.text }.toSet()
        val seen = mutableSetOf<String>()
        val drawn = mutableListOf<String>()

        repeat(expected.size) {
            val next = AppJokes.pickUnseen(seen, tones)
            assertFalse("the toast repeated a line before the filter was spent", next in seen)
            seen += next
            drawn += next
        }

        assertEquals(expected, drawn.toSet())
    }

    @Test
    fun `a spent pool still answers with a real line`() {
        val next = AppJokes.pickUnseen(AppJokes.lines.toSet())

        assertTrue("the bag returned a line that is not in the pool", next in AppJokes.lines)
    }

    @Test
    fun `the bag never repeats the line at the seam`() {
        // The rule that used to live in the host as a comment. A bag that clears
        // *including* the line currently on screen passes every other test in this
        // file and still tells the same joke twice in a row at the seam - which is
        // the one repeat a shuffle bag exists to prevent.
        val tones = setOf(JokeTone.META)
        val lines = AppJokes.poolFor(tones).map { it.text }
        val seen = mutableSetOf<String>()
        var last = ""
        val drawn = mutableListOf<String>()

        repeat(lines.size * 3) { index ->
            last = AppJokes.draw(seen, last, tones, Random(index))
            drawn += last
        }

        drawn.zipWithNext().forEach { (previous, next) ->
            assertNotEquals("the toast repeated a line across the seam", previous, next)
        }
        assertEquals(
            "the first cycle is not the whole filter, so the bag is not a bag",
            lines.toSet(),
            drawn.take(lines.size).toSet()
        )
        assertEquals(
            "a cycle drew a line twice, which means the bag kept it after showing it",
            lines.size,
            drawn.take(lines.size).distinct().size
        )
    }

    @Test
    fun `an empty last line does not occupy a place in the bag`() {
        // The first draw of a session passes "", and treating it as a line would
        // shrink the first cycle by one and make the last line arrive twice.
        val tones = setOf(JokeTone.DARK)
        val lines = AppJokes.poolFor(tones).map { it.text }
        val seen = mutableSetOf<String>()
        var last = ""

        repeat(lines.size) { index ->
            last = AppJokes.draw(seen, last, tones, Random(index))
        }

        assertEquals(lines.toSet(), seen)
    }

    @Test
    fun `saved lines follow pool order and keep lines the pool no longer has`() {
        val known = AppJokes.lines.take(3).toSet()
        val unknown = "a line from a build that no longer exists"
        val ordered = AppJokes.orderedFavourites(known + unknown)

        assertEquals(AppJokes.lines.take(3), ordered.dropLast(1))
        assertEquals(
            "a saved line the current pool does not know about was dropped; the " +
                "reader's line outlives a pool edit",
            unknown,
            ordered.last()
        )
    }
}
