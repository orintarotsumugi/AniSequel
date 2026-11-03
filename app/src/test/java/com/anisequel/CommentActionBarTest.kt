package com.anisequel

import com.anisequel.ui.screens.profile.CommentActionBarForm
import com.anisequel.ui.screens.profile.commentActionBarForm
import com.anisequel.ui.screens.profile.commentActionBarWidthDp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The bar under a comment, and where its controls land.
 *
 * ## The failure this pins
 *
 * The bar was a `FlowRow`, which is a reasonable answer to "five controls under a
 * body that can be any width" and a bad answer to "where is the button that edits
 * my comment". On a wide top-level row the manage dot sat beside Quote; on a
 * threaded one the line ran out and the dot fell to a second line under the
 * timestamp. Same app, same comment, two places - and the row that had the dot was
 * the row the reader most needed to find it on.
 *
 * The bar is one line now, with the time and the dot pinned to the trailing edge,
 * and what gives way when the line runs out is the two *words* "Reply" and "Quote",
 * not the position of anything. So there are two things to hold: the arithmetic that
 * decides it, and the order the controls are emitted in.
 */
class CommentActionBarTest {

    /** A 360dp phone: 20dp of margin each side, and the avatar column's 46dp. */
    private val topLevelRowDp = 360f - 20f - 20f - 46f

    @Test
    fun `a top-level row has room for the words`() {
        assertEquals(
            CommentActionBarForm.Full,
            commentActionBarForm(
                availableDp = topLevelRowDp,
                showLikes = true,
                canReply = true,
                canQuote = true,
                showTimestamp = false,
                canManage = true
            )
        )
    }

    /**
     * The order things give way in, which is the whole design.
     *
     * A control is never the thing that goes: the words go, then a continuation's
     * timestamp - and the timestamp is second precisely because the header above it
     * already said roughly when.
     */
    @Test
    fun `a threaded row drops the words instead of dropping a line`() {
        // Two levels of 18dp of nesting in, on the same phone.
        val threadedDp = topLevelRowDp - 18f * 2
        assertEquals(
            "the labelled bar cannot fit a threaded comment; the glyphs can",
            CommentActionBarForm.Compact,
            commentActionBarForm(
                availableDp = threadedDp,
                showLikes = true,
                canReply = true,
                canQuote = true,
                showTimestamp = false,
                canManage = true
            )
        )
        // The worst row there is: nested, somebody's own comment (so the dot), and a
        // continuation (so the time). Even the glyphs plus both do not fit, and what
        // goes is the time - never the dot.
        assertEquals(
            CommentActionBarForm.Minimal,
            commentActionBarForm(
                availableDp = threadedDp,
                showLikes = true,
                canReply = true,
                canQuote = true,
                showTimestamp = true,
                canManage = true
            )
        )
        // The same worst bar on a top-level row fits as Compact: the timestamp costs
        // a line only when there is no room for it.
        assertEquals(
            CommentActionBarForm.Compact,
            commentActionBarForm(
                availableDp = topLevelRowDp,
                showLikes = true,
                canReply = true,
                canQuote = true,
                showTimestamp = true,
                canManage = true
            )
        )
    }

    @Test
    fun `the width adds only the controls the row actually draws`() {
        val full = commentActionBarWidthDp(
            showLikes = true, canReply = true, canQuote = true,
            showTimestamp = true, canManage = true, labelled = true
        )
        // A signed-out reader: no heart they can press, nothing to manage.
        val noManage = commentActionBarWidthDp(
            showLikes = true, canReply = true, canQuote = true,
            showTimestamp = true, canManage = false, labelled = true
        )
        // A message thread's head row: no heart at all.
        val noLikes = commentActionBarWidthDp(
            showLikes = false, canReply = true, canQuote = true,
            showTimestamp = false, canManage = false, labelled = true
        )
        assertTrue("the dot costs a target", full > noManage)
        assertTrue("the heart costs a target", noManage > noLikes)
        assertEquals(
            "a control that is not drawn is not measured",
            noLikes,
            commentActionBarWidthDp(
                showLikes = false, canReply = true, canQuote = true,
                showTimestamp = false, canManage = false, labelled = true
            ),
            0.0
        )
    }

    /**
     * The order the controls are emitted in, read off the source.
     *
     * A UI assertion needs a device, and there is none here; what can be pinned on a
     * JVM is that nothing in the bar wraps any more, and that the trailing group is
     * the time and then the dot. Both are properties of the emitted order, and the
     * order is exactly what the reader sees in a different place on every row when it
     * is wrong.
     */
    @Test
    fun `the dot is the last thing on the line and nothing wraps`() {
        val code = withoutComments(
            File(
                findProjectRoot(),
                "app/src/main/java/com/anisequel/ui/screens/profile/CommentActionRow.kt"
            ).readText()
        )
        assertFalse("the bar is one line: no FlowRow", code.contains("FlowRow"))
        val spacer = code.indexOf("Spacer(modifier = Modifier.weight(1f))")
        val timestamp = code.indexOf("CommentTimestamp(reply.createdAt, nowEpochSecond)")
        val manage = code.indexOf("CommentManageButton(onClick = onManage)")
        assertTrue("the leftover space is taken before the trailing group", spacer in 0 until timestamp)
        assertTrue("the manage dot is emitted after the time, so it is the last row control", timestamp < manage)
    }

    private fun withoutComments(text: String): String {
        val out = StringBuilder()
        var i = 0
        while (i < text.length) {
            when {
                text.startsWith("//", i) -> {
                    val stop = text.indexOf('\n', i).let { if (it < 0) text.length else it }
                    i = stop
                }
                text.startsWith("/*", i) -> {
                    val stop = text.indexOf("*/", i + 2).let { if (it < 0) text.length else it + 2 }
                    i = stop
                }
                else -> {
                    out.append(text[i])
                    i++
                }
            }
        }
        return out.toString()
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
