package com.anisequel

import com.anisequel.ui.components.nova.nextGreeting
import com.anisequel.ui.screens.nova.isCurrentSearchGeneration
import org.junit.Assert.assertEquals
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
    fun `stale search response cannot replace the current query results`() {
        org.junit.Assert.assertFalse(isCurrentSearchGeneration(response = 2, current = 3))
        org.junit.Assert.assertTrue(isCurrentSearchGeneration(response = 3, current = 3))
    }
}
