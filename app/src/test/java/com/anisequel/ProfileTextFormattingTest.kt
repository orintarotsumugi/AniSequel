package com.anisequel

import com.anisequel.ui.screens.profile.formatJoinYear
import com.anisequel.ui.screens.profile.formatMeanScore
import com.anisequel.ui.screens.profile.formatRelativeSeconds
import com.anisequel.ui.screens.profile.formatWatchTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

// This file also held `StripMarkdownTest`, twelve cases against a
// `stripMarkdown` that nothing in the app called - the bio card renders the
// parsed AST, so a tested-but-uncalled formatter read as "some screen uses this"
// to whoever opened the file next. The function and those cases are deleted
// together; see the note left where it used to live in `ProfileAboutCard.kt`.
//
// The property they were pinning is worth keeping in mind if a single-line bio
// preview is ever wanted: over-stripping is the real risk. A bio that loses its
// asterisks and also loses its apostrophes is a person's words edited by their
// app, and the trailing-lone-dash case below was deliberately left alone for the
// same reason - a hyphen at the end of a bio is a typo, and guessing at it would
// be worse than leaving it.

/**
 * The two numbers the header card quotes.
 *
 * Both exist because the wrong rendering is a plausible-looking number: minutes
 * printed raw ("47160") rather than as hours, and a mean score rounded to the
 * wrong side ("8.2/10" for 82.4).
 */
class ProfileNumberFormattingTest {

    @Test
    fun `minutes read as minutes below an hour`() {
        assertEquals("42m", formatWatchTime(42))
    }

    @Test
    fun `minutes read as hours below a day`() {
        assertEquals("3h", formatWatchTime(180))
        assertEquals("23h", formatWatchTime(23 * 60))
    }

    @Test
    fun `long lists read as days`() {
        assertEquals("1d", formatWatchTime(24 * 60))
        assertEquals("3d", formatWatchTime(72 * 60))
    }

    @Test
    fun `a whole day and some hours keeps the hours`() {
        assertEquals("1d 5h", formatWatchTime(29 * 60))
    }

    @Test
    fun `nothing watched reads as zero rather than as a blank`() {
        assertEquals("0h", formatWatchTime(0))
        assertEquals("0h", formatWatchTime(-5))
    }

    @Test
    fun `an AniList mean score is quoted out of ten`() {
        assertEquals("8/10", formatMeanScore(80.0))
        assertEquals("8.2/10", formatMeanScore(81.91))
        assertEquals("10/10", formatMeanScore(100.0))
    }

    @Test
    fun `a whole mean score does not grow a decimal point`() {
        assertEquals("9/10", formatMeanScore(90.0))
    }

    @Test
    fun `the decimal separator does not follow the device locale`() {
        // Rendered under a comma-decimal locale this used to produce "8,2/10",
        // which is not a score.
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            assertEquals("8.2/10", formatMeanScore(81.91))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun `a mean score of zero is not quoted as a score`() {
        // The caller filters it, but a zero here would read as "0.0/10" next to a
        // list of 258 entries.
        assertTrue(formatMeanScore(0.0) == "0/10")
    }
}
/**
 * The two dates in the profile header.
 *
 * Both are AniList's Unix timestamps, and both are nullable: `User.createdAt`
 * does not exist for accounts opened before 2020. The chip is hidden rather than
 * rendered as "Joined unknown", which is why these are only about formatting.
 */
class ProfileDateFormattingTest {

    private val now: Instant = Instant.parse("2026-06-10T12:00:00Z")

    @Test
    fun `a join date is a year`() {
        // 2014-04-07T00:00:00Z
        assertEquals("2014", formatJoinYear(1_397_079_561))
    }

    @Test
    fun `a recent timestamp reads in minutes`() {
        assertEquals("5m ago", formatRelativeSeconds((now.epochSecond - 300).toInt(), now))
    }

    @Test
    fun `a few hours old reads in hours`() {
        assertEquals("5h ago", formatRelativeSeconds((now.epochSecond - 5 * 3600).toInt(), now))
    }

    @Test
    fun `a few days old reads in days`() {
        assertEquals("5d ago", formatRelativeSeconds((now.epochSecond - 5 * 86_400).toInt(), now))
    }

    @Test
    fun `a year or more old reads in years`() {
        assertEquals("2y ago", formatRelativeSeconds((now.epochSecond - 730 * 86_400L).toInt(), now))
    }

    @Test
    fun `a timestamp in the future reads as now, not as a negative age`() {
        // A device with a wrong clock must not render "in -3h ago".
        assertEquals("just now", formatRelativeSeconds((now.epochSecond + 3_600).toInt(), now))
    }

    @Test
    fun `the format does not follow the device locale`() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            // Relative time sits next to a username; a chip written in a different
            // script or numbering from the text around it reads as a bug.
            assertEquals("5h ago", formatRelativeSeconds((now.epochSecond - 5 * 3600).toInt(), now))
            assertEquals("2014", formatJoinYear(1_397_079_561))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
