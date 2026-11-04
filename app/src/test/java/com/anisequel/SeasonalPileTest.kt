package com.anisequel

import com.anisequel.domain.seasonal.SEASONAL_PILE_COLUMNS
import com.anisequel.domain.seasonal.SeasonalPile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The drift: the only part of the seasonal sky that remembers anything.
 *
 * A pile is easy to get subtly wrong in ways a screenshot cannot show. It can
 * start at zero and look broken for a minute; it can deposit at a column and
 * leave the two beside it at exactly the old height, which draws a comb; it can
 * grow past its own ceiling and become a wall. These pin the three.
 */
class SeasonalPileTest {

    @Test
    fun `a new drift already has a thin dusting rather than a flat zero`() {
        val pile = SeasonalPile(seed = 4)
        for (column in 0 until pile.columns) {
            assertTrue(
                "column $column started empty; the first half-minute looks like a bug",
                pile.column(column) > 0f
            )
            assertTrue(
                "the initial dusting is already a wall",
                pile.column(column) < 0.2f
            )
        }
    }

    @Test
    fun `a landing raises its column and its neighbours but not the whole field`() {
        val pile = SeasonalPile(seed = 4)
        val center = SeasonalPile(seed = 4)
        val before = pile.column(24)

        center.deposit(xFraction = 24.5f / SEASONAL_PILE_COLUMNS, mass = 1f)

        assertTrue("the column under the landing did not grow", center.column(24) > before)
        assertTrue(
            "the neighbours did not share the landing, so the drift would grow as spikes",
            center.column(23) > pile.column(23)
        )
        assertTrue(
            "the neighbours did not share the landing, so the drift would grow as spikes",
            center.column(25) > pile.column(25)
        )
        assertEquals(
            "a landing at the middle of the screen must not raise the far edge",
            pile.column(0),
            center.column(0),
            0.0001f
        )
    }

    @Test
    fun `the drift has a ceiling no amount of landing can pass`() {
        val pile = SeasonalPile(seed = 9)
        repeat(4_000) { pile.deposit(0.5f, mass = 1.6f) }
        for (column in 0 until pile.columns) {
            assertTrue(
                "column $column grew past the drift ceiling and would hide the page",
                pile.column(column) <= 0.98f
            )
        }
        pile.fill()
        assertEquals(0.98f, pile.column(12), 0f)
    }

    @Test
    fun `heightAt interpolates between columns rather than stepping`() {
        val pile = SeasonalPile(seed = 3)
        pile.deposit(0.25f, mass = 1.6f)
        val quarter = 0.25f * (SEASONAL_PILE_COLUMNS - 1)
        val low = quarter.toInt()
        val high = low + 1
        val blend = quarter - low
        val expected = pile.column(low) + (pile.column(high) - pile.column(low)) * blend
        assertEquals(expected, pile.heightAt(0.25f), 0.0001f)
    }

    @Test
    fun `an out-of-range fraction is clamped to an edge`() {
        val pile = SeasonalPile(seed = 3)
        assertEquals(pile.column(0), pile.heightAt(-2f), 0.0001f)
        assertEquals(pile.column(pile.columns - 1), pile.heightAt(2f), 0.0001f)
    }

    @Test
    fun `the same seed draws the same dusting`() {
        val first = SeasonalPile(seed = 17)
        val second = SeasonalPile(seed = 17)
        for (column in 0 until first.columns) {
            assertEquals(first.column(column), second.column(column), 0f)
        }
    }

    @Test
    fun `smoothing spreads a mound sideways instead of leaving a spike`() {
        // Six landings in one place, so the mound is far taller than the starting dusting
        // and the assertion does not depend on which grains the seed happened to draw.
        val pile = SeasonalPile(seed = 4)
        repeat(6) { pile.deposit(xFraction = 0.5f, mass = 1.6f, spread = 1) }
        val peak = pile.column(24)
        val beside = pile.column(23)

        pile.relax()

        assertTrue(
            "the peak survived a smoothing pass, so the drift grows combs, not banks",
            pile.column(24) < peak
        )
        assertTrue(
            "nothing moved sideways into the column beside the mound",
            pile.column(23) > beside
        )
    }

    @Test
    fun `smoothing cannot push the drift past its own ceiling`() {
        val pile = SeasonalPile(seed = 4)
        pile.fill()
        repeat(40) {
            pile.deposit(xFraction = 0.5f, mass = 1.6f, spread = 4)
            pile.relax()
        }
        for (column in 0 until pile.columns) {
            assertTrue(
                "column $column passed the ceiling while it was being smoothed",
                pile.column(column) <= 0.98f
            )
        }
    }
}
