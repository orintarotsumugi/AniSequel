package com.anisequel.domain.profile

/**
 * The blocks a profile's Home tab can draw, and can let the reader arrange.
 *
 * The header is not a member: it is the page's identity, not a block about it, and
 * a profile that could hide its own name and face would be a settings screen. Every
 * entry here is a *card* or a labelled row underneath it.
 *
 * [storageValue] is what the preferences file keeps - stable across a rename of the
 * display label, which is the whole reason labels are not the keys.
 */
enum class ProfileSection(val storageValue: String, val displayName: String) {
    ABOUT("about", "About"),
    FAVOURITE_ANIME("favourite_anime", "Favourite anime"),
    FAVOURITE_MANGA("favourite_manga", "Favourite manga"),
    FAVOURITE_CHARACTERS("favourite_characters", "Favourite characters"),
    FAVOURITE_STAFF("favourite_staff", "Favourite staff"),
    FAVOURITE_STUDIOS("favourite_studios", "Favourite studios");

    companion object {
        fun fromStorage(values: Set<String>): Set<ProfileSection> =
            entries.filter { it.storageValue in values }.toSet()

        fun fromStorageList(values: List<String>): List<ProfileSection> =
            values.mapNotNull { value -> entries.firstOrNull { it.storageValue == value } }
    }
}

/**
 * How the reader wants their own profile arranged.
 *
 * A stored order rather than a set of position integers: moving a row is a swap in
 * one list, and a section removed from a later build simply falls out of the order
 * instead of leaving a hole. Sections the stored order has never heard of are
 * appended in their declared order, so a build that adds a block shows it rather
 * than hiding it by omission - the failure mode that makes a new feature invisible
 * on every upgraded device.
 *
 * [hidden] is the visibility switch, and it deliberately allows hiding everything:
 * a reader who wants a header and nothing else is asking for a page with no blocks,
 * not for a bug, and the header still carries the way back into this editor.
 */
data class ProfileLayout(
    val order: List<ProfileSection> = ProfileSection.entries.toList(),
    val hidden: Set<ProfileSection> = emptySet(),
    /**
     * Draw the character favourites as a two-column grid instead of the diagonal
     * strip. The strip favours looking at one portrait closely; the grid favours
     * seeing all of them at once.
     */
    val charactersTwoColumn: Boolean = false
) {
    /** Stored order, de-duplicated, unknown entries dropped, missing ones appended. */
    val resolvedOrder: List<ProfileSection>
        get() {
            val known = order.distinct().filter { it in ProfileSection.entries }
            val missing = ProfileSection.entries.filterNot { it in known }
            return known + missing
        }

    /** The sections the page draws, in the reader's order. */
    val visibleSections: List<ProfileSection>
        get() = resolvedOrder.filterNot { it in hidden }

    /**
     * Moves [section] one place earlier ([direction] < 0) or later (> 0).
     *
     * Works on the *resolved* order, so a section that was appended by an upgrade
     * can be moved on the first press rather than needing a save first. The result
     * stores the full resolved order, which also normalises anything the file held.
     */
    fun move(section: ProfileSection, direction: Int): ProfileLayout {
        val current = resolvedOrder.toMutableList()
        val from = current.indexOf(section)
        if (from < 0) return this
        val to = (from + direction).coerceIn(0, current.lastIndex)
        if (to == from) return this
        val moved = current.removeAt(from)
        current.add(to, moved)
        return copy(order = current)
    }

    /** Shows or hides [section]; the order is kept so unhiding restores the place. */
    fun toggle(section: ProfileSection): ProfileLayout =
        copy(hidden = if (section in hidden) hidden - section else hidden + section)

    companion object {
        val DEFAULT = ProfileLayout()
    }
}
