package com.anisequel

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Info page's scroll-derived state must not outlive the title it was derived for.
 *
 * ## What was broken
 *
 * `AnimeInfoPage` derived two things from the `LazyListState`:
 *
 * ```kotlin
 * val collapse = remember(listState, collapseDistance) { derivedStateOf { ... } }
 * val showFloatingBar by remember { derivedStateOf { collapse.value > DOCKED_BAR_AT } }
 * ```
 *
 * The first is keyed; the second is not. `SequelDetailSheet` hands the page a
 * **different `LazyListState` for every title** - it keys the state on
 * `subject.mediaId` so that following a relation starts the new title at the top
 * instead of inheriting the old one's scroll offset - so opening a relation rebuilds
 * `collapse` against the new state while an unkeyed `remember` keeps the *first*
 * `derivedStateOf` it was ever handed. That one's lambda closes over the old list
 * state, which is detached by then and keeps its last value.
 *
 * ## What that looked like
 *
 * The reader is always *past* the hero when they open a relation - the relation row
 * is a dozen sections down - so the old state read `1f`, the threshold stayed
 * exceeded, and the new title opened with its hero at the top **and** the docked
 * bar already drawn over it.
 *
 * `InfoHero` and `InfoFloatingBar` each carry their own "Open on AniList" and
 * "Close" controls, deliberately - one belongs to the hero and one to the bar, and
 * they hand over as the page scrolls (see `DOCKED_BAR_AT` and `LOCKUP_FADE_FROM`
 * in `InfoMotion.kt`). Drawn together they are two of each in the same corner of a
 * page whose entire answer to "how do I get out of here" is one of them.
 *
 * ## Why this is a test and not a note
 *
 * The failing edit is a removed argument from a `remember` call. It compiles, it
 * reads as an ordinary "no keys needed" memo, and nothing about the diff looks like
 * it could draw a second copy of a button. The invariant is also a Compose rule
 * rather than an app rule - a `derivedStateOf` built in an unkeyed `remember` reads
 * whatever value was current when the group was created, forever - so the same
 * shape anywhere in this page would fail the same way with a different symptom.
 *
 * ## What it checks
 *
 * Every `remember` in the Info page that builds a `derivedStateOf` has to declare at
 * least one key. `derivedStateOf` is a read, and a read is only re-evaluated when
 * what it reads is; without a key there is nothing that can tell the memo the value
 * it holds is no longer the one it wants.
 *
 * This is a source-level assertion because there is no JVM Compose harness here -
 * `androidx.compose.ui.test.junit4` is an `androidTestImplementation` only, and
 * adding a Compose test runtime to the unit-test source set for one assertion would
 * cost far more than the guard is worth. Same approach as
 * `MinimumSdkReachabilityTest` and `VectorIconsTest`.
 */
class InfoFloatingBarKeyingTest {

    private val infoPage = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/detail/info/InfoPage.kt"
    )

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    /**
     * `remember { derivedStateOf { ... } }` with nothing between the braces of the
     * `remember` call, over whatever follows it on the line or lines after.
     *
     * Deliberately narrow: it matches the unkeyed form only, so a correctly keyed
     * `remember(collapse) { derivedStateOf { ... } }` is not a match and does not
     * have to be allowed for.
     */
    private val unkeyedDerivedState = Regex(
        """remember\s*\{\s*(?:\n\s*)?derivedStateOf"""
    )

    @Test
    fun `no derived scroll state in the Info page is remembered without a key`() {
        assertTrue(
            "could not read ${infoPage.path}; this guard silently passing on a " +
                "missing file is the same failure it exists to prevent",
            infoPage.isFile
        )
        val source = infoPage.readText()

        val unkeyed = unkeyedDerivedState.findAll(source).map { match ->
            // A short window from the match, enough to name the declaration in a
            // failure without dumping the whole page.
            source.substring(
                match.range.first,
                (match.range.first + 120).coerceAtMost(source.length)
            ).substringBeforeLast('\n')
        }.toList()

        assertTrue(
            "every `derivedStateOf` built in a `remember` on the Info page has to " +
                "declare the value it reads as a key. An unkeyed one keeps the " +
                "state it was created with, so opening a relation - which gives " +
                "the page a new LazyListState - left the docked bar drawn over " +
                "the new title's hero, putting a second \"Open on AniList\" and a " +
                "second \"Close\" in the same corner of the screen:\n" +
                unkeyed.joinToString("\n\n") { "  $it" },
            unkeyed.isEmpty()
        )
    }

    /**
     * Names the fix, so a regression is a changed line rather than a broad
     * "something is unkeyed" failure.
     */
    @Test
    fun `the floating bar's threshold is keyed on the collapse it reads`() {
        val source = infoPage.readText()

        assertTrue(
            "`showFloatingBar` must be remembered with `collapse` as a key - that " +
                "is what rebuilds the threshold when the page is given another " +
                "title's scroll state:\n$source",
            source.contains("remember(collapse) {")
        )
    }
}