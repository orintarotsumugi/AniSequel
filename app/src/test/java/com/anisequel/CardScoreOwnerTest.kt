package com.anisequel

import com.anisequel.domain.card.CardScoreOwner
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.card.posterScoreOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Which surface draws a card's rating, as one answer every card reads.
 *
 * ## Why this is worth pinning on a JVM
 *
 * A rating is one number and there are three drawings of it: the badge on the artwork,
 * the band under the title, and the chip in the body's row of facts. Each of those
 * used to decide for itself, with different rules - the dashboard's poster took the
 * score only when it was at least 75, while its chip row stepped aside for every style
 * that had a badge. A sequel rated 74 was therefore drawn by neither, so the app hid a
 * number it had. Nothing about that shows up in a screenshot of a well-rated list,
 * which is why it lasted: the bug only appears on the cards nobody checked.
 *
 * The invariant is "exactly once, whatever the score", and it is a property of the
 * table rather than of any one screen, so it is checked here.
 */
class CardScoreOwnerTest {

    /** A rating the old threshold would have dropped, on purpose. */
    private val lowScore = 74

    @Test
    fun `a rated title is drawn exactly once in every style`() {
        MediaCardStyle.entries.forEach { style ->
            val onPoster = posterScoreOf(style, lowScore)
            if (style.scoreOwner == CardScoreOwner.POSTER) {
                assertEquals(
                    "${style.displayName} owns the rating on its poster but handed " +
                        "back $onPoster instead of the card's score",
                    lowScore,
                    onPoster
                )
            } else {
                assertNull(
                    "${style.displayName} keeps the rating in the card's body but the " +
                        "poster drew it too, so the card shows the same number twice",
                    onPoster
                )
            }
        }
    }

    @Test
    fun `an unrated title draws no rating anywhere`() {
        MediaCardStyle.entries.forEach { style ->
            // AniList writes 0 for "not rated yet", which is not a score of zero.
            assertNull(
                "${style.displayName} drew a badge for a title AniList has not rated",
                posterScoreOf(style, 0)
            )
            assertNull(
                "${style.displayName} drew a badge for a title with no score at all",
                posterScoreOf(style, null)
            )
        }
    }

    @Test
    fun `the body draws the ratings the artwork does not take`() {
        // Both of the card body's drawings stay reachable, so "not on the poster"
        // never means "nowhere": a band style paints the band and the rest paint a
        // chip. These four are the answer the dashboard's details column branches on.
        assertEquals(CardScoreOwner.BANNER, MediaCardStyle.EXOTIC.scoreOwner)
        assertEquals(CardScoreOwner.BANNER, MediaCardStyle.RIBBON.scoreOwner)
        assertEquals(CardScoreOwner.CHIP, MediaCardStyle.DEFAULT.scoreOwner)
        assertEquals(CardScoreOwner.CHIP, MediaCardStyle.CINEMA.scoreOwner)
    }
}
