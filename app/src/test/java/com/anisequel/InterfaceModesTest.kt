package com.anisequel

import com.anisequel.domain.card.CardTitlePlacement
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.domain.ui.NavBarLift
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class InterfaceModesTest {

    @Test
    fun `new interface modes keep stable storage keys`() {
        assertEquals(LibraryViewMode.CARDS, LibraryViewMode.fromStorage("cards"))
        assertEquals(LibraryViewMode.SHELF, LibraryViewMode.fromStorage("shelf"))
        assertEquals(LibraryViewMode.GROUPED, LibraryViewMode.fromStorage("grouped"))
        assertEquals(HomeSequelStyle.BANNER, HomeSequelStyle.fromStorage("banner"))
        assertEquals(HomeSequelStyle.SHELF, HomeSequelStyle.fromStorage("shelf"))
        assertEquals(MediaCardStyle.COVER_ONLY, MediaCardStyle.fromStorage("cover_only"))
    }

    @Test
    fun `the bar lift only offers values the slider can draw`() {
        assertEquals(0, NavBarLift.MIN_DP)
        assertEquals(24, NavBarLift.MAX_DP)
        assertEquals(2, NavBarLift.STEP_DP)
        assertEquals(NavBarLift.MAX_DP, NavBarLift.clamp(NavBarLift.MAX_DP + 1))
        assertEquals(NavBarLift.MIN_DP, NavBarLift.clamp(-1))
        assertEquals(0, (NavBarLift.MAX_DP - NavBarLift.MIN_DP) % NavBarLift.STEP_DP)
    }

    @Test
    fun `cover only card intentionally hides the repeated title`() {
        assertEquals(
            CardTitlePlacement.HIDDEN,
            MediaCardStyle.COVER_ONLY.titlePlacement
        )
        assertTrue(MediaCardStyle.COVER_ONLY.scoreOnPoster)
    }
}
