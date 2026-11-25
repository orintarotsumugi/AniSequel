package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The banner's viewport, and the places the feed could paint outside what it owns.
 *
 * ## Why most of these are source scans
 *
 * Every failure here is a drawing or a wiring decision rather than arithmetic: a
 * `ContentScale` that disagrees with the transform's units, a missing clip, a
 * parameter that is declared and never passed. None of them can fail a type
 * check, and all of them look finished until the banner is a portrait or the
 * reader drags it to an edge, or the title is long, or the font size is large.
 *
 * The pure half - the progress line - lives in [ActivityDetailFormattingTest],
 * because it can be checked without a device. This file pins the half that cannot.
 */
class BackdropViewportTest {

    private fun source(path: String): String =
        Regex("""/[*][\s\S]*?[*]/""")
            .replace(File(findProjectRoot(), path).readText()) { "" }
            .lines()
            .filterNot { it.trimStart().startsWith("//") }
            .joinToString("\n")

    /**
     * The cover fit is measured in the picture's own pixels; the painter has to be
     * drawn in them too.
     *
     * `FillBounds` stretches the bitmap to the box, so the layer's cover scale was
     * applied to an already-fitted picture: every banner was drawn at a fraction of
     * its size, centred, with the caller's fallback showing around it. The anchor
     * and the drift translate by a fraction of the *intrinsic* overflow, so on the
     * shrunken picture those translations pushed the artwork out of the banner
     * entirely - the "goes outside and drifts outside" report. `None` draws the
     * bitmap at its own size, which is the space `backdropTransform` has always
     * assumed, and the clip keeps every pixel inside the banner.
     */
    @Test
    fun `the backdrop draws the picture in the space its transform measures`() {
        val backdrop = source("app/src/main/java/com/anisequel/ui/components/PannableBackdrop.kt")

        assertTrue(
            "the backdrop must draw the picture at its intrinsic size: `FillBounds` " +
                "stretches it to the box, and the cover transform is computed in the " +
                "picture's own pixels - the two then disagree, and the anchor and the " +
                "drift translate by an overflow the drawn picture does not have",
            backdrop.contains("ContentScale.None")
        )
        assertFalse(
            "`ContentScale.FillBounds` is back in the backdrop; see the note above",
            backdrop.contains("ContentScale.FillBounds")
        )
        assertTrue(
            "the picture can paint outside the banner's own box",
            backdrop.contains(".clipToBounds()")
        )
    }

    /**
     * The marquee measures its line at full width and draws it with visible
     * overflow, so the row has to clip it.
     *
     * Without the clip a title longer than the card was drawn past the card's
     * rounded edge and into the page margin, which reads as a card wider than the
     * screen rather than as a title that scrolls.
     */
    @Test
    fun `a scrolling title cannot paint outside its card`() {
        val marquee = source("app/src/main/java/com/anisequel/ui/components/MarqueeText.kt")

        assertTrue(
            "the marquee measures its line at full width and draws it with visible " +
                "overflow; without a clip the title paints past the card that owns it",
            marquee.contains(".clipToBounds()")
        )
    }

    /**
     * The number beside a heart is the discoverable way to the names.
     *
     * The control was one target - tap to like, hold to see who did - and nothing
     * on screen announced the hold. Each of the three rows that draws a heart has
     * to pass the split target, or the parameter is dead weight.
     */
    @Test
    fun `the number beside every heart opens the likers list`() {
        val action = source("app/src/main/java/com/anisequel/ui/components/EngagementAction.kt")
        assertTrue(
            "the split target was removed, so the likers list is reachable only by a " +
                "long press nothing announces",
            action.contains("onCountClick: (() -> Unit)? = null")
        )

        listOf(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileActivityCard.kt",
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileTextActivityCard.kt",
            "app/src/main/java/com/anisequel/ui/screens/profile/CommentActionRow.kt"
        ).forEach { path ->
            assertTrue(
                "$path draws a like control but never passes onCountClick, so its " +
                    "number is not a way to the people who liked it",
                source(path).contains("onCountClick =")
            )
        }
    }

    @Test
    fun `the likers tray opens a profile and closes itself first`() {
        val sheet = source("app/src/main/java/com/anisequel/ui/screens/profile/ActivityLikersSheet.kt")
        assertTrue(
            "the tray's rows are inert again, so the one thing a reader wants from a " +
                "face is the one thing the tray cannot do",
            sheet.contains("onOpenUser: ((FollowUser) -> Unit)? = null") &&
                sheet.contains("onClickLabel = \"Open ")
        )

        val host = source("app/src/main/java/com/anisequel/ui/screens/profile/ProfileTabsContent.kt")
        assertTrue(
            "the host does not dismiss the tray before walking to a profile, so the " +
                "tray stays drawn over the page that was opened",
            host.contains("activityViewModel.dismissLikers()")
        )
    }

    /**
     * The calendar is a place the reader navigates; a rebuild of the feed is not.
     *
     * It used to scroll to the newest week whenever the calendar object changed,
     * which the Activity tab does on every list write - so a reader who had walked
     * back through the year was pulled to the right-hand edge by a refresh. The
     * gutter is not a target, and the selected day has to be clearable.
     */
    @Test
    fun `the heatmap opens once at the newest week and ignores the gutters`() {
        val calendar = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ActivityCalendarCard.kt"
        )

        assertTrue(
            "the newest-week scroll is keyed on the calendar again, so a refresh " +
                "yanks a reader who scrolled back",
            calendar.contains("var openedAtNewest by rememberSaveable")
        )
        assertTrue(
            "the gutter between two cells is a target again, so a tap in the gap " +
                "answers about the neighbouring day",
            calendar.contains("offset.x - column * pitchPx > cellPx")
        )
        assertTrue(
            "the selected day can no longer be cleared",
            calendar.contains("day.epochDay.takeIf { it != selectedEpochDay }")
        )
    }

    @Test
    fun `the reply chip and the highlighted comment are cleared together`() {
        val sheet = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ActivityCommentsSheet.kt"
        )

        assertTrue(
            "clearing the reply chip leaves the comment highlight behind, so the " +
                "editor points at a row it is not answering",
            sheet.contains("onClearReplyTo = {") &&
                sheet.contains("replyTargetId = null")
        )
    }

    /**
     * Four flows feed this page, and the inbox's is on a timer.
     *
     * Without the `remember`s a messages poll rebuilt all three feed sources, and
     * the whole activity feed - heatmap, headings and list - recomposed because an
     * unrelated segment had news.
     */
    @Test
    fun `the feed sources survive an unrelated state publish`() {
        val page = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileActivityPage.kt"
        )

        assertTrue(
            "the feed sources are rebuilt on every recomposition again, so the " +
                "inbox's poll recomposes the activity feed",
            page.contains("val ownFeed = remember(") &&
                page.contains("val globalFeed = remember(") &&
                page.contains("val friendsFeed = remember(")
        )
    }

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }
}
