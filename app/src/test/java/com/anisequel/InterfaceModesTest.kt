package com.anisequel

import com.anisequel.domain.card.CardTitlePlacement
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.InfoPageStyle
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.domain.ui.NavBarLift
import com.anisequel.domain.ui.ProgressBarStyle
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

    /**
     * The title-page switch is its own key, and an install that has never seen it
     * must keep the page it shipped with.
     *
     * Both halves matter: a fresh value on the wrong key would make the switch
     * look live while writing nothing the app reads, and the default has to be
     * the page every install opens in when no choice is stored. Nova is that page
     * now; Classic still wins whenever it was explicitly chosen.
     */
    @Test
    fun `the title page style is stable and defaults to Nova`() {
        assertEquals(InfoPageStyle.CLASSIC, InfoPageStyle.fromStorage("classic"))
        assertEquals(InfoPageStyle.NOVA, InfoPageStyle.fromStorage("nova"))
        assertEquals(InfoPageStyle.NOVA, InfoPageStyle.DEFAULT)
        assertEquals(
            "a value written by a newer build must fall back to Nova",
            InfoPageStyle.NOVA,
            InfoPageStyle.fromStorage("hologram")
        )
    }

    /**
     * A stored bar style must round-trip, and an unknown one must fall back to the
     * shipped wave rather than crash on a value a newer build wrote.
     *
     * This is the usual storage-key contract for the interface enums; it is
     * written here because the picker now writes this value from a long hold on
     * the Info page rather than from a Settings row.
     */
    @Test
    fun `the progress bar style is stable and defaults to the wave`() {
        assertEquals(ProgressBarStyle.WAVE, ProgressBarStyle.fromStorage("wave"))
        assertEquals(ProgressBarStyle.BAR, ProgressBarStyle.fromStorage("bar"))
        assertEquals(ProgressBarStyle.ROCKET, ProgressBarStyle.fromStorage("rocket"))
        assertEquals(ProgressBarStyle.WAVE, ProgressBarStyle.DEFAULT)
        assertEquals(
            "a style written by a newer build must fall back to the shipped wave",
            ProgressBarStyle.WAVE,
            ProgressBarStyle.fromStorage("constellation")
        )
    }

    @Test
    fun `the bar lift only offers values the slider can draw`() {
        assertEquals(0, NavBarLift.MIN_DP)
        assertEquals(96, NavBarLift.MAX_DP)
        assertEquals(4, NavBarLift.STEP_DP)
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
        assertEquals(96f, NavBarLift.clampDp(126.2f), 0f)
        assertEquals(11.5f, NavBarLift.clampDp(11.5f), 0f)
    }

    /**
     * The per-frame guard has a hard floor and a forgiving ceiling.
     *
     * Below zero is the shipped crash: `Modifier.padding` refuses a negative dp,
     * so the spring cannot be allowed to spend frames under the screen edge.
     * Above the stored maximum is not a crash and not a lie - it is the spring's
     * own overshoot - so it is allowed a little room rather than stopping the bar
     * dead at the top of its range.
     */
    @Test
    fun `the animated guard floors at zero and lets the spring overshoot the ceiling`() {
        assertEquals(0f, NavBarLift.clampAnimatedDp(-4f), 0f)
        assertEquals(11.5f, NavBarLift.clampAnimatedDp(11.5f), 0f)
        assertTrue(
            "the spring must be able to pass the stored ceiling",
            NavBarLift.clampAnimatedDp(100f) > NavBarLift.MAX_DP
        )
        assertEquals(
            "but not travel without bound",
            (NavBarLift.MAX_DP + NavBarLift.OVERSHOOT_DP).toFloat(),
            NavBarLift.clampAnimatedDp(10_000f),
            0f
        )
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
