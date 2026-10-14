package com.anisequel.domain.tier

import java.util.Locale

/**
 * One saved tier list, as a thing with a name and an identity of its own.
 *
 * ## Why this exists at all
 *
 * There used to be one tier list, in one pair of DataStore keys, and opening the
 * trophy went straight into the board. Two consequences followed from that, and
 * both of them are what this type is for:
 *
 *  - **The board was the entry point.** There was no way to see what you had, and
 *    starting over meant destroying the only list there was, so "start a second
 *    list" was not a thing the app could express.
 *  - **Every edit rewrote everything.** The tiers and the placements shared a
 *    fixed pair of keys, so a list was the schema. A document that travels as one
 *    value is what lets the store keep several, write only the one that changed,
 *    and give each one a name the person recognises.
 *
 * ## Pure, like the rest of `domain/tier`
 *
 * No Android, no Compose, no coroutines, no clock. [updatedAt] is a number the
 * caller supplies, which is what makes "which list did I touch last" testable
 * without waiting for a millisecond to pass.
 */
data class TierListDocument(
    /** Stable identity, and the key the store writes it under. Never shown. */
    val id: String,
    val name: String,
    val tiers: List<TierDefinition> = StandardTiers,
    val assignment: TierAssignment = TierAssignment(),
    /**
     * Epoch millis of the last write, or 0 for a list that has never been saved.
     *
     * The library sorts on this and says "updated 2 days ago" from it. It is
     * deliberately *not* used to decide what opens: that is [lastOpenedId], and
     * mixing the two is how a list you merely edited jumps in front of the one you
     * were working in.
     */
    val updatedAt: Long = 0L
) {
    /** Titles with a tier. The number the library card counts. */
    val rankedCount: Int get() = assignment.tierIdByMediaId.size

    val isEmpty: Boolean get() = assignment.isEmpty
}

/**
 * The name a new list gets when the person does not choose one.
 *
 * Numbered from the second list onward, and by the smallest free number, so the
 * third list is not called "Tier List 4" because the second was deleted. Matched
 * case-insensitively: two lists differing only in case read as the same name to
 * everybody except this function.
 */
fun defaultTierListName(existingNames: Collection<String>): String {
    // Locale.ROOT so "Tier List" and "TIER LIST" compare equal on every device;
    // a locale that lowercases I differently would let two lists that read as
    // the same name both be created.
    val taken = existingNames.map { it.trim().lowercase(Locale.ROOT) }.toSet()
    if (TIER_LIST_BASE_NAME.lowercase(Locale.ROOT) !in taken) return TIER_LIST_BASE_NAME
    var suffix = 2
    while ("${TIER_LIST_BASE_NAME.lowercase(Locale.ROOT)} $suffix" in taken) suffix++
    return "$TIER_LIST_BASE_NAME $suffix"
}

/**
 * An id for a new list, unique among [existingIds].
 *
 * Sequential rather than random or time-based, and that is on purpose: a document
 * is stored under a key derived from its id, and a deterministic id means the same
 * test and the same store produce the same keys, which is the only way a storage
 * test can assert anything about them without a clock or a seeded RNG. Uniqueness
 * is checked against every live list, so the smallest free index cannot collide -
 * and reuse after a delete is harmless because deleting removes the key too.
 */
fun newTierListId(existingIds: Collection<String>): String {
    val taken = existingIds.toSet()
    var index = 1
    while ("${TIER_LIST_ID_PREFIX}$index" in taken) index++
    return "$TIER_LIST_ID_PREFIX$index"
}

/**
 * The list to offer as "carry on where you left off".
 *
 * The last one *opened* wins when it still exists, because that is what the person
 * was working on, and opening a list is a deliberate act rather than a side effect
 * of scrolling past it. Falling back to the most recently edited keeps the card
 * useful for somebody who has not opened one yet this install.
 *
 * Null only when there is nothing to continue, which is what the library's
 * first-run state is: an empty screen with one button, rather than a card
 * offering to continue a list that does not exist.
 */
fun mostRecentTierList(
    lists: List<TierListDocument>,
    lastOpenedId: String?
): TierListDocument? {
    lastOpenedId?.let { id -> lists.firstOrNull { it.id == id }?.let { return it } }
    return lists.maxByOrNull { it.updatedAt }
}

/** The base name, so the numbering rule and the fallback title cannot drift apart. */
const val TIER_LIST_BASE_NAME = "Tier list"

/**
 * How long a list's name may be.
 *
 * Enforced where the name is set rather than where it is drawn, so a name that is
 * too long cannot get onto disk at all: a card that truncates its own title and a
 * stored name that is a paragraph read as two different lists, and the person sees
 * only the truncated one.
 */
const val TIER_LIST_NAME_MAX_LENGTH = 60

/** The id prefix; ids are never shown, only compared and used as keys. */
private const val TIER_LIST_ID_PREFIX = "list"

/**
 * One undone-able edit, and what to call it in the UI.
 *
 * The *whole previous document* rather than a delta. A tier list is a few hundred
 * ints and a handful of labels, so a snapshot costs less than the bug it prevents:
 * a delta has to be inverted correctly for every kind of edit - a move, a removal
 * that dropped entries filed under the removed row, a seeding - and the one that is
 * inverted wrong silently corrupts the list instead of failing.
 */
data class TierUndoEntry(val document: TierListDocument, val label: String)

/**
 * The last few edits, so a mis-drop can be taken back.
 *
 * Bounded, because this lives in memory for as long as the screen does and a drag
 * is cheap to repeat: an unbounded stack of a few hundred placements each, over an
 * afternoon of arranging, is a memory leak that looks like a feature. Ten is past
 * the point anybody reaches for undo; past that they are scrolling back through
 * their own work, which is what the list itself is for.
 */
class TierUndoStack(private val capacity: Int = DEFAULT_CAPACITY) {

    private val entries = ArrayDeque<TierUndoEntry>()

    val size: Int get() = entries.size

    val canUndo: Boolean get() = entries.isNotEmpty()

    /** The label of the edit [undo] would take back, or null when there is none. */
    val label: String? get() = entries.lastOrNull()?.label

    fun push(entry: TierUndoEntry) {
        entries.addLast(entry)
        while (entries.size > capacity) entries.removeFirst()
    }

    /** The entry to restore, removed from the stack. Null when there is nothing. */
    fun pop(): TierUndoEntry? = entries.removeLastOrNull()

    /**
     * Forgets everything, for a change that cannot be unwound by putting a
     * snapshot back: switching lists, or a list being deleted. Restoring the other
     * list's document over this one would be worse than losing the undo.
     */
    fun clear() = entries.clear()

    private companion object {
        const val DEFAULT_CAPACITY = 10
    }
}
