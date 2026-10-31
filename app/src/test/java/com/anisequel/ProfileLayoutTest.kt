package com.anisequel

import com.anisequel.domain.profile.ProfileLayout
import com.anisequel.domain.profile.ProfileSection
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The profile layout contract.
 *
 * The failure this protects is an upgrade, not a drag: a stored order written
 * before a section existed must still draw that section, and hiding must be
 * reversible without losing the place it had. Both are pure functions of the
 * layout, so they are pinned here rather than left to a phone.
 */
class ProfileLayoutTest {

    @Test
    fun `an old stored order gains the sections it predates`() {
        val legacy = ProfileLayout(
            order = listOf(ProfileSection.ABOUT, ProfileSection.FAVOURITE_ANIME)
        )

        assertEquals(
            listOf(
                ProfileSection.ABOUT,
                ProfileSection.FAVOURITE_ANIME,
                ProfileSection.FAVOURITE_MANGA,
                ProfileSection.FAVOURITE_CHARACTERS,
                ProfileSection.FAVOURITE_STAFF,
                ProfileSection.FAVOURITE_STUDIOS
            ),
            legacy.resolvedOrder
        )
    }

    @Test
    fun `moving swaps a section with its neighbour and stops at the ends`() {
        val layout = ProfileLayout()
        val movedUp = layout.move(ProfileSection.ABOUT, -1)
        assertEquals(layout.resolvedOrder, movedUp.resolvedOrder)

        val movedDown = layout.move(ProfileSection.ABOUT, 1)
        assertEquals(ProfileSection.FAVOURITE_ANIME, movedDown.resolvedOrder.first())
        assertEquals(ProfileSection.ABOUT, movedDown.resolvedOrder[1])

        val last = layout.resolvedOrder.last()
        val beyond = layout.move(last, 1)
        assertEquals(layout.resolvedOrder, beyond.resolvedOrder)
    }

    @Test
    fun `hiding keeps the stored place and unhiding restores it`() {
        val layout = ProfileLayout()
        val hidden = layout.toggle(ProfileSection.FAVOURITE_MANGA)

        assertTrue(ProfileSection.FAVOURITE_MANGA !in hidden.visibleSections)
        assertEquals(layout.order, hidden.order)
        assertEquals(layout.resolvedOrder, hidden.toggle(ProfileSection.FAVOURITE_MANGA).visibleSections)
    }
}
