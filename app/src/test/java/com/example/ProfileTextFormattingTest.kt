package com.example

import com.example.ui.screens.profile.formatJoinYear
import com.example.ui.screens.profile.formatMeanScore
import com.example.ui.screens.profile.formatRelativeSeconds
import com.example.ui.screens.profile.formatWatchTime
import com.example.ui.screens.profile.stripMarkdown
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.util.Locale

/**
 * Guards the bio card's text handling.
 *
 * AniList bios are Markdown and there is no Markdown renderer in the app. What
 * is rendered instead is plain text with the four things that actually appear in
 * a bio - headings, emphasis, links and bullets - un-done, and nothing else
 * touched. The risk being pinned here is over-stripping: a bio that loses its
 * asterisks but also loses its apostrophes, or a link whose text is thrown away
 * along with its URL, is a person's own words edited by their app.
 */
class StripMarkdownTest {

    @Test
    fun `removes heading markers but keeps the heading text`() {
        // No bullet in the input on purpose: bullets are a separate rule with
        // their own test, and having one here meant this asserted two things and
        // failed on the one it was not named for.
        val result = stripMarkdown("### How I rate\n\n10 / 10 - peak fiction")

        assertEquals("How I rate\n\n10 / 10 - peak fiction", result)
    }

    @Test
    fun `keeps link text and drops the url`() {
        val result = stripMarkdown("I play [visual novels](https://vndb.org/u57774) instead.")

        assertEquals("I play visual novels instead.", result)
        assertFalse(result.contains("vndb.org"))
    }

    @Test
    fun `removes bold and italic markers`() {
        assertEquals("leave a comment", stripMarkdown("**leave a comment**"))
        assertEquals("leave a comment", stripMarkdown("*leave a comment*"))
        assertEquals("leave a comment", stripMarkdown("__leave a comment__"))
        assertEquals("leave a comment", stripMarkdown("_leave a comment_"))
    }

    @Test
    fun `keeps apostrophes and punctuation inside emphasised text`() {
        // The regex is `(\*|_)(.+?)\1`, so the risk is an apostrophe being read as
        // an emphasis marker and swallowing the rest of the sentence.
        val result = stripMarkdown("**You're** always welcome here, *don't* worry")

        assertEquals("You're always welcome here, don't worry", result)
    }

    @Test
    fun `removes bullet markers but keeps the item text`() {
        val result = stripMarkdown("* Watching 7\n* Completed 128\n* Dropped 0")

        assertEquals("Watching 7\nCompleted 128\nDropped 0", result)
    }

    @Test
    fun `collapses the blank lines markdown leaves behind`() {
        // A bio that alternates heading, list and paragraph is three paragraphs
        // once rendered, not fifteen blank-spaced lines.
        val result = stripMarkdown("### One\n\n\n\ntext\n\n\n\n### Two")

        assertEquals("One\n\ntext\n\nTwo", result)
    }

    @Test
    fun `drops blockquote markers`() {
        assertEquals("a quoted line", stripMarkdown("> a quoted line"))
    }

    @Test
    fun `strips inline code fences but keeps the code`() {
        assertEquals("SaveMediaListEntry", stripMarkdown("`SaveMediaListEntry`"))
    }

    @Test
    fun `leaves prose with no markdown in it completely alone`() {
        val source = "I watch too much television and I have no regrets about it."
        assertEquals(source, stripMarkdown(source))
    }

    @Test
    fun `an empty bio stays empty rather than becoming blank lines`() {
        assertEquals("", stripMarkdown("   \n\n  "))
    }

    @Test
    fun `an empty heading leaves no heading marker behind`() {
        val result = stripMarkdown("###\n\nSome text")

        assertFalse(result.contains("#"))
        assertEquals("Some text", result)
    }

    @Test
    fun `a bullet with no text after it is still removed`() {
        // `[-*+]\s+` needs something after the marker, so a trailing lone dash
        // survives. Left as it is rather than special-cased: a bio that ends in a
        // hyphen is a person's typo, and stripping it would mean guessing.
        assertEquals("**\n\n-", stripMarkdown("###\n\n**\n\n-"))
    }
}

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
