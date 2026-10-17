package com.anisequel

import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * The inline half of "the spoiler did nothing" - the block chip's sibling bug.
 *
 * [SpoilerAndThemeOverrideTest] pins the chip: it revealed, but the tap never
 * reached it because a `bouncyPress` with no interaction source installed a second
 * gesture handler. This is the other shape of the same failure.
 *
 * `~!...!~` on a line of its own becomes a [MarkdownNode.SpoilerBlock] with a chip.
 * The same token *inside* a sentence - the plot point in a review, the twist in a
 * recommendation - becomes an `InlineToken.Spoiler`, and that branch rendered the
 * hidden run and stopped there: `append(SPOILER_PLACEHOLDER.repeat(...))`, with no
 * annotation, no chip and no state. So it was not merely hard to open, it could not
 * be opened at all: the text was covered for good, one paragraph away from a block
 * spoiler that revealed fine.
 *
 * The comment on the branch recorded why, and the reasoning had expired: "an
 * `AnnotatedString` cannot be tapped, so it has nowhere to put a reveal". That was
 * never quite true of the platform - `LinkAnnotation.Clickable` is exactly a tap on
 * a span - and it is not true of this file at all, which was already routing
 * `LinkAnnotation.Url` taps through the app's own link handler two functions below.
 *
 * Source assertions, not behaviour tests, for the reason the chip's are: this is
 * view code, the failure is a missing annotation rather than a logic error, and the
 * JVM test suite has no Compose UI harness to tap a span in.
 */
@RunWith(RobolectricTestRunner::class)
class InlineSpoilerRevealTest {

    private fun inlinesSource() = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/markdown/AniListMarkdownInlines.kt"
    ).readText()

    /**
     * The body of the `InlineToken.Spoiler` branch, and nothing after it.
     *
     * Cut at the next top-level declaration rather than at the next `is
     * InlineToken.` branch: `childrenOrText()` at the bottom of the file is a `when`
     * over the same token types, so cutting there would hand the assertions
     * everything from this branch to end-of-file - which contains the real
     * `LinkAnnotation.Url` helper - and they would pass with the spoiler branch
     * unchanged.
     */
    private fun spoilerBranch(source: String): String = source
        .substringAfter("is InlineToken.Spoiler ->")
        .substringBefore("private fun AnnotatedString.Builder.appendLink(")

    @Test
    fun `an inline spoiler carries the tap that reveals it`() {
        val branch = spoilerBranch(inlinesSource())

        assertTrue(
            "the covered run has to be a `LinkAnnotation.Clickable`, or there is no " +
                "gesture that can ever reach the text it is hiding:\n$branch",
            branch.contains("LinkAnnotation.Clickable(")
        )
        assertTrue(
            "and the annotation needs the listener that flips the reveal",
            branch.contains("onReveal(index)")
        )
    }

    @Test
    fun `the tap opens the text, and only the covered run is the target`() {
        val branch = spoilerBranch(inlinesSource())

        assertTrue(
            "revealing has to append the real text, not another placeholder run:\n$branch",
            branch.contains("append(token.text)")
        )
        assertTrue(
            "the gate is the per-paragraph index, so two spoilers in one sentence " +
                "open independently:\n$branch",
            branch.contains("spoilers.revealed.contains(index)")
        )

        // Whitespace is collapsed before the two assertions below, because they are
        // about which call wraps which append and indentation is not part of that.
        //
        // This replaced a `withLink\([\s\S]{0,300}?SPOILER_PLACEHOLDER` window, which
        // failed against the implementation it was written to describe: the
        // annotation literal, its named arguments and the lambda header put ~330
        // characters between the two markers at this nesting depth. A fixed window
        // over formatted source measures the indent, not the code.
        val collapsed = branch.replace(Regex("""\s+"""), " ")
        val tapRegion = collapsed.substringAfter("withLink(").substringBefore("} else ")

        assertTrue(
            "what is tappable is the placeholders: the `withLink` has to wrap the " +
                "`SPOILER_PLACEHOLDER` append, or the covered run is not the target " +
                "of the tap:\n$branch",
            tapRegion.contains("append(SPOILER_PLACEHOLDER.repeat(")
        )
        assertTrue(
            "and the revealed text has to sit outside that `withLink`, or a revealed " +
                "spoiler re-closes itself on the next tap - the failure " +
                "`RenderSpoilerBlock` was split to avoid:\n$branch",
            !tapRegion.contains("append(token.text)")
        )
    }

    /**
     * The reveal has to survive the paragraph leaving the viewport.
     *
     * `RenderSpoilerBlock` had the same bug in an earlier round - a plain `remember`
     * is discarded with the LazyColumn item, so a revealed spoiler re-hid itself on
     * the way back up - and this is the resolved shape of it.
     */
    @Test
    fun `the inline reveal is saved, not merely remembered`() {
        val source = inlinesSource()

        assertTrue(
            "a `remember` would be discarded with the item, so the text the reader " +
                "had already chosen to read covers itself again:\n$source",
            source.contains("revealedSpoilers by rememberSaveable { mutableStateOf(IntArray(0)) }")
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
