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
        assertEquals(LibraryViewMode.TABLE, LibraryViewMode.fromStorage("table"))
        assertEquals(LibraryViewMode.COVERS, LibraryViewMode.fromStorage("covers"))
        assertEquals(
            "an unknown mode written by a newer build must fall back to the shipped grid",
            LibraryViewMode.GRID,
            LibraryViewMode.fromStorage("hologram")
        )
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

    /**
     * The animated value is the one that crashed 1.9.7.
     *
     * `animateIntAsState` on the spatial spring overshoots its target; travelling
     * from the top of the range to zero it produces negative frames. Those frames
     * reached `Modifier.padding` and the LazyColumn content padding, and Compose
     * rejects a negative dp rather than treating it as a smaller lift. The float
     * clamp is the per-frame guard, so it must hold at both edges and keep the
     * in-range values it is given.
     */
    @Test
    fun `an animated bar lift cannot leave its range`() {
        assertEquals(0f, NavBarLift.clampDp(-3.4f), 0f)
        assertEquals(24f, NavBarLift.clampDp(26.2f), 0f)
        assertEquals(11.5f, NavBarLift.clampDp(11.5f), 0f)
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
