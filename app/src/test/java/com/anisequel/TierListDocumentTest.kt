package com.anisequel

import com.anisequel.domain.tier.TIER_LIST_BASE_NAME
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierUndoEntry
import com.anisequel.domain.tier.TierUndoStack
import com.anisequel.domain.tier.defaultTierListName
import com.anisequel.domain.tier.mostRecentTierList
import com.anisequel.domain.tier.newTierListId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the rules around a saved list *as a thing*: what it is called, what it is
 * keyed by, and which one the app offers to carry on with.
 *
 * These are the decisions that used to not exist. There was one list, in two fixed
 * DataStore keys, so nothing had to be named, distinguished, or chosen between -
 * and the two rules that go wrong silently are the ones here: a name that collides
 * makes the library a screen of identical cards, and an id that collides makes one
 * list's contents appear under another's name.
 */
class TierListDocumentTest {

    private fun document(id: String, name: String, updatedAt: Long = 0L) =
        TierListDocument(id = id, name = name, updatedAt = updatedAt)

    // ---- Names -------------------------------------------------------------

    @Test
    fun `the first list is called what the feature is called`() {
        assertEquals(TIER_LIST_BASE_NAME, defaultTierListName(emptyList()))
    }

    @Test
    fun `a second list is numbered`() {
        // Three lists all called "Tier list" is a library nobody can navigate,
        // because the name is the only thing distinguishing them.
        assertEquals("$TIER_LIST_BASE_NAME 2", defaultTierListName(listOf(TIER_LIST_BASE_NAME)))
        assertEquals(
            "$TIER_LIST_BASE_NAME 3",
            defaultTierListName(listOf(TIER_LIST_BASE_NAME, "$TIER_LIST_BASE_NAME 2"))
        )
    }

    @Test
    fun `a name that was deleted is used again`() {
        // Numbered by the smallest free number rather than by how many exist, so
        // the third list is not called "Tier list 4" because one was deleted.
        assertEquals(
            "$TIER_LIST_BASE_NAME 2",
            defaultTierListName(listOf(TIER_LIST_BASE_NAME, "$TIER_LIST_BASE_NAME 3"))
        )
    }

    @Test
    fun `a name is matched however it is capitalised`() {
        // Two lists that differ only in case read as the same name to everybody
        // except this function, which is exactly the failure it is here to stop.
        assertEquals(
            "$TIER_LIST_BASE_NAME 2",
            defaultTierListName(listOf("tier LIST"))
        )
    }

    // ---- Ids ---------------------------------------------------------------

    @Test
    fun `ids are unique against the lists that exist`() {
        assertEquals("list1", newTierListId(emptyList()))
        assertEquals("list2", newTierListId(listOf("list1")))
        assertEquals("list2", newTierListId(listOf("list1", "list3")))
        assertEquals("list1", newTierListId(listOf("list2")))
    }

    @Test
    fun `an id is deterministic for the same library`() {
        // Deterministic rather than random or time-based: the id is the storage
        // key, so a storage test that had to tolerate any id could only assert
        // that *something* was written.
        assertEquals(newTierListId(listOf("list1")), newTierListId(listOf("list1")))
    }

    // ---- Which one to carry on with ----------------------------------------

    @Test
    fun `the list that was last opened is the one offered`() {
        // Not the most recently *edited*: a seeding pass somebody barely watched
        // is an edit, and opening a list is what a person means by where they were.
        val lists = listOf(
            document("list1", "One", updatedAt = 900),
            document("list2", "Two", updatedAt = 100)
        )

        // "list2" though "list1" is the newer write: if this ever returns
        // "list1" the rule has silently become "newest edit wins".
        assertEquals("list2", mostRecentTierList(lists, lastOpenedId = "list2")?.id)
    }

    @Test
    fun `with nothing opened the newest list is offered`() {
        val lists = listOf(
            document("list1", "One", updatedAt = 100),
            document("list2", "Two", updatedAt = 900)
        )

        assertEquals("list2", mostRecentTierList(lists, lastOpenedId = null)?.id)
    }

    @Test
    fun `an id that no longer exists does not offer a list that is gone`() {
        val lists = listOf(document("list1", "One", updatedAt = 100))

        assertEquals("list1", mostRecentTierList(lists, lastOpenedId = "list9")?.id)
    }

    @Test
    fun `an empty library offers nothing to carry on with`() {
        assertNull(mostRecentTierList(emptyList(), lastOpenedId = "list1"))
    }

    // ---- Counts ------------------------------------------------------------

    @Test
    fun `a list counts what is ranked in it`() {
        val list = TierListDocument(
            id = "list1",
            name = "One",
            assignment = TierAssignment(linkedMapOf(1 to "s", 2 to "a"))
        )

        assertEquals(2, list.rankedCount)
        assertFalse(list.isEmpty)
        assertTrue(TierListDocument(id = "list2", name = "Two").isEmpty)
    }

    // ---- Undo --------------------------------------------------------------

    private fun entry(label: String) = TierUndoEntry(document("list1", label), label)

    @Test
    fun `undo takes back the most recent edit first`() {
        val stack = TierUndoStack()

        stack.push(entry("Filed under A"))
        stack.push(entry("Filed under S"))

        assertEquals("Filed under S", stack.label)
        assertEquals("Filed under S", stack.pop()?.label)
        assertEquals("Filed under A", stack.pop()?.label)
        assertNull(stack.pop())
        assertFalse(stack.canUndo)
    }

    @Test
    fun `the stack is bounded, and forgets the oldest first`() {
        // Bounded because it lives in memory for as long as the screen does and a
        // drag is cheap to repeat. An unbounded stack of a few hundred placements
        // each, over an afternoon of arranging, is a leak that looks like a
        // feature.
        val stack = TierUndoStack(capacity = 3)

        (1..5).forEach { stack.push(entry("edit $it")) }

        assertEquals(3, stack.size)
        assertEquals(listOf("edit 5", "edit 4", "edit 3"), (1..3).map { stack.pop()?.label })
        assertNull(stack.pop())
    }

    @Test
    fun `clearing the stack forgets everything`() {
        // For the changes a snapshot cannot undo: switching lists, or deleting the
        // one that was open.
        val stack = TierUndoStack()
        stack.push(entry("Filed under S"))

        stack.clear()

        assertFalse(stack.canUndo)
        assertNull(stack.label)
        assertNull(stack.pop())
    }
}
