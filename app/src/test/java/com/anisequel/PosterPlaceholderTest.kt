package com.anisequel

import com.anisequel.ui.components.cards.POSTER_PLACEHOLDER_SLOTS
import com.anisequel.ui.components.cards.posterInitials
import com.anisequel.ui.components.cards.posterPlaceholderSlot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The tile a poster draws while its cover is on its way - and the two rules that make
 * it worth drawing at all.
 *
 * Both are pure on purpose: the placeholder is the first frame of a cold start, so it
 * cannot afford a request, and it cannot afford to be wrong either - two cards in one
 * grid with the same initials and the same colour are the identical grey boxes it
 * replaced.
 */
class PosterPlaceholderTest {

    @Test
    fun `two words print one letter each`() {
        assertEquals("FB", posterInitials("Frieren: Beyond Journey's End"))
        assertEquals("OP", posterInitials("one piece"))
    }

    @Test
    fun `separators do not become initials`() {
        // The old rule took the first two characters of the title, which printed the
        // colon and the bracket instead of the words behind them.
        assertEquals("FB", posterInitials("Frieren - Beyond"))
        // Brackets and parentheses are separators, not initials - and the first two
        // *words* are what a reader would abbreviate, not the last two.
        assertEquals("ON", posterInitials("[Oshi no Ko] (Anime)"))
    }

    @Test
    fun `one word prints two characters`() {
        // A single letter cannot tell Bleach from Berserk in a grid.
        assertEquals("BL", posterInitials("Bleach"))
        assertEquals("86", posterInitials("86"))
    }

    @Test
    fun `a title with nothing to print still gets a tile`() {
        assertEquals("?", posterInitials(""))
        assertEquals("?", posterInitials("   "))
        assertEquals("?", posterInitials("--- ..."))
    }

    @Test
    fun `initials never split a character in two`() {
        // A CJK title has no spaces, so it takes the first two code points rather
        // than two UTF-16 units - `take(2)` would print half a surrogate pair for an
        // astral character.
        val initials = posterInitials("葬送のフリーレン")
        assertEquals("葬送", initials)
        assertTrue("a lone high surrogate reached the card", initials.none { it.isHighSurrogate() })
    }

    @Test
    fun `a slot is stable for a title and in range`() {
        val slot = posterPlaceholderSlot("Bleach")
        assertEquals("the same title must keep its colour across launches", slot, posterPlaceholderSlot("Bleach"))
        // Case and stray spacing are the same title, so they are the same colour.
        assertEquals(slot, posterPlaceholderSlot("  bleach "))

        listOf("", "86", "One Piece", "葬送のフリーレン", "a".repeat(400)).forEach { title ->
            val value = posterPlaceholderSlot(title)
            assertTrue("slot $value out of range for \"$title\"", value in 0 until POSTER_PLACEHOLDER_SLOTS)
        }
    }

    @Test
    fun `a one slot palette never divides by a bad modulus`() {
        assertEquals(0, posterPlaceholderSlot("Bleach", slots = 1))
        assertEquals(0, posterPlaceholderSlot("Bleach", slots = 0))
    }
}
