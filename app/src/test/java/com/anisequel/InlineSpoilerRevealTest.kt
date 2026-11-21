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
 * `~!...!~` on a line of its own becomes a [NovaBlock.Spoiler] with a chip. The
 * same token *inside* a sentence - the plot point in a review, the twist in a
 * recommendation - becomes a `NovaInline.Spoiler`, and the previous renderer
 * printed the hidden run and stopped there: placeholder characters with no
 * annotation, no chip and no state. It could not be opened at all.
 *
 * NovaMarkdown keeps the revealed run inside the same `LinkAnnotation.Clickable`
 * so the tap toggles: opening appends the real text, and a second tap covers it
 * again. The list of revealed indices is `rememberSaveable`, because a plain
 * `remember` is discarded with a LazyColumn item and a revealed spoiler would
 * re-hide itself on the way back up.
 *
 * Source assertions, not behaviour tests, for the reason the chip's are: this is
 * view code, the failure is a missing annotation rather than a logic error, and
 * the JVM test suite has no Compose UI harness to tap a span in.
 */
@RunWith(RobolectricTestRunner::class)
class InlineSpoilerRevealTest {

    private fun inlinesSource() = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/novamarkdown/NovaMarkdownInline.kt"
    ).readText()

    /**
     * The body of the inline spoiler branch, and nothing after it.
     *
     * Bounded by the next top-level declaration. The file has other
     * `LinkAnnotation.Clickable` code paths nearby once the branch ends, so an
     * unbounded slice would satisfy these assertions with the spoiler branch
     * removed.
     */
    private fun spoilerBranch(source: String): String = source
        .substringAfter("private fun AnnotatedString.Builder.appendNovaSpoiler(")
        .substringBefore("private fun novaLink(")

    @Test
    fun `an inline spoiler carries the tap that reveals it`() {
        val branch = spoilerBranch(inlinesSource())

        assertTrue(
            "the covered run has to be a `LinkAnnotation.Clickable`, or there is no " +
                "gesture that can ever reach the text it is hiding:\n$branch",
            branch.contains("LinkAnnotation.Clickable(")
        )
        assertTrue(
            "and the annotation needs the listener that flips the reveal:\n$branch",
            branch.contains("spoilers.onToggle(index)")
        )
        assertTrue(
            "the listener must be reached through the spoiler state, not captured " +
                "from a stale composition:\n$branch",
            branch.contains("linkInteractionListener = { spoilers.onToggle(index) }")
        )
    }

    @Test
    fun `the tap opens the text, and a second tap closes it again`() {
        val branch = spoilerBranch(inlinesSource())

        assertTrue(
            "the gate is the per-paragraph index, so two spoilers in one sentence " +
                "open independently:\n$branch",
            branch.contains("spoilers.revealed.contains(index)")
        )
        assertTrue(
            "revealing has to append the real tokens, not another placeholder run:\n$branch",
            branch.contains("appendNovaTokens(")
        )
        assertTrue(
            "and closing has to put the placeholder run back:\n$branch",
            branch.contains("append(SPOILER_PLACEHOLDER.repeat(")
        )
        assertTrue(
            "the revealed run keeps a style that marks it as still tappable, which " +
                "is where the closing tap goes:\n$branch",
            branch.contains("if (revealed) spoilers.revealedStyles else spoilers.styles")
        )
        assertTrue(
            "both appends sit inside the same `withLink`, so one annotation owns the " +
                "toggle:\n$branch",
            branch.substringAfter("withLink(").contains("append(SPOILER_PLACEHOLDER.repeat(") &&
                branch.substringAfter("withLink(").contains("appendNovaTokens(")
        )
    }

    /**
     * The reveal has to survive the paragraph leaving the viewport.
     *
     * `NovaSpoiler` has the same requirement in its own file - a plain `remember`
     * is discarded with the LazyColumn item, so a revealed spoiler re-hid itself on
     * the way back up - and this is the inline half of the same shape.
     */
    @Test
    fun `the inline reveal is saved, not merely remembered`() {
        val source = inlinesSource()

        assertTrue(
            "a `remember` would be discarded with the item, so the text the reader " +
                "had already chosen to read covers itself again:\n$source",
            source.contains("revealed by rememberSaveable { mutableStateOf(IntArray(0)) }")
        )
    }

    /**
     * Nested spoilers spend their indices even while the parent is closed.
     *
     * Otherwise opening a sibling after a parent is revealed renumbers everything
     * below it and a previously opened run closes on its own.
     */
    @Test
    fun `nested spoiler indices are reserved while the parent is closed`() {
        val branch = spoilerBranch(inlinesSource())

        assertTrue(
            "the children's indices have to be skipped while they are not drawn:\n$branch",
            branch.contains("spoilers.index += countNovaSpoilers(token.children)")
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
