package com.anisequel

import com.anisequel.ui.components.nova.nextGreeting
import com.anisequel.ui.components.nova.nextRandomGreeting
import com.anisequel.ui.screens.nova.isCurrentSearchGeneration
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovaGreetingTest {
    @Test
    fun `greeting advances to the next line rather than repeating`() {
        assertEquals(5, nextGreeting(currentIndex = 4, count = 8))
    }

    @Test
    fun `greeting wraps after the last line`() {
        assertEquals(0, nextGreeting(currentIndex = 7, count = 8))
    }

    @Test
    fun `a random greeting never repeats the line on screen`() {
        repeat(200) { seed ->
            val next = nextRandomGreeting(
                currentIndex = 7,
                count = 90,
                random = Random(seed)
            )
            assertTrue("index $next is outside the pool", next in 0 until 90)
            assertTrue("a new visit repeated greeting 7", next != 7)
        }
    }

    @Test
    fun `a one line pool can only return that line`() {
        assertEquals(0, nextRandomGreeting(currentIndex = 0, count = 1))
    }

    @Test
    fun `stale search response cannot replace the current query results`() {
        org.junit.Assert.assertFalse(isCurrentSearchGeneration(response = 2, current = 3))
        org.junit.Assert.assertTrue(isCurrentSearchGeneration(response = 3, current = 3))
    }
}
