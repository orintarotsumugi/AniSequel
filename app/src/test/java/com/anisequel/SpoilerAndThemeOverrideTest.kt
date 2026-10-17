package com.anisequel

import com.anisequel.ui.theme.DynamicThemeBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

/**
 * Two bugs found after v1.3.2 shipped: a spoiler that would not open, and an anime
 * theme that left most of the Material palette behind.
 *
 * ## The spoiler did nothing
 *
 * The chip chained `.bouncyPress(...).clickable(onClick = onToggle)`. `bouncyPress`
 * takes an `interactionSource` for exactly this case, and passing none of them sends
 * it down its documented fallback: it builds its **own** `MutableInteractionSource`
 * and installs a **second** `pointerInput` loop - `awaitFirstDown`, then a loop over
 * `PointerEventPass.Main` - to watch for a press that turns into a scroll.
 *
 * Two pointer handlers on one element compete for the same gesture, and the tap did
 * not reach the `clickable`. The reveal simply did nothing. Every other press control
 * in this app does it correctly - `EngagementAction` builds one `MutableInteractionSource`
 * and hands it to both - which is why the heart works and the chip did not.
 *
 * The chip also used a `FlowRow` for its content, and a `Surface` inside a
 * `fillMaxWidth` Column hands a `FlowRow` the full width, so the chip stretched the
 * length of the card instead of wrapping to its label.
 *
 * ## The anime theme only overrode 21 of 36 roles
 *
 * `createDynamicSchemes` called `lightColorScheme(...)` and `darkColorScheme(...)`,
 * and those builders start from **Material's baseline scheme**, which is derived from
 * a purple seed. Every role the call did not name stayed purple: `onSurfaceVariant`
 * (every secondary label in the app), `outline` and `outlineVariant` (every card
 * border), `tertiary`, `error`, `surfaceDim`, `surfaceBright`, `scrim`. The result was
 * a red accent on an app with purple borders - which is what "the anime dynamic colour
 * does not override the M3 colours" looks like, and it is not a subtle tint problem:
 * the roles were never being overridden at all.
 */
@RunWith(RobolectricTestRunner::class)
class SpoilerAndThemeOverrideTest {

    private fun markdownSource() = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/components/markdown/AniListMarkdownBlocks.kt"
    ).readText()

    /**
     * The source of the one top-level declaration that starts with [marker].
     *
     * Cut at the first `\n}`. The closing brace of a top-level declaration is the
     * only one at column 0, because everything nested inside it is indented, and
     * that single observation replaces every fancier rule tried before it.
     *
     * Rejected on the way here, each wrong in a way worth recording:
     *
     * - `substringAfter(marker)` alone runs to end-of-file, so a
     *   `wrapContentWidth()` in some unrelated composable further down satisfies an
     *   assertion about the chip. A test that passes while the bug is still present
     *   is worse than no test at all.
     * - Cutting at a blank line: a Kotlin body routinely contains one.
     * - Cutting at the next line at column 0: the `) {` closing a multi-line
     *   signature is also at column 0, and stopping there left sixteen lines - the
     *   parameter list - so every assertion failed on correct code.
     * - Cutting at the next line that opens a declaration, first with a large
     *   `Regex` and then with a predicate function. The regex had to spell a
     *   comment opener and a closer using backslashes inside a Kotlin string, in a
     *   file that already holds raw strings containing comment delimiters further
     *   down: two spellings of one hazardous construct in one file, and the
     *   compiler disagreed about where the class ended.
     */
    private fun declarationStartingAt(source: String, marker: String): String =
        source.substringAfter(marker).substringBefore("\n}")

    // ---- the spoiler ------------------------------------------------------------

    /**
     * The one that matters: one press signal, not two.
     *
     * A `bouncyPress` with no source is not "the default, it's fine" - it is a second
     * gesture handler, and that is what stopped the tap. This is a source assertion
     * because the failure only shows up on a device under a real pointer stream.
     */
    @Test
    fun `the spoiler chip shares one interaction source with its press modifier`() {
        val source = markdownSource()
        val chip = declarationStartingAt(source, "private fun SpoilerChip(")

        assertTrue(
            "the chip must build one MutableInteractionSource: a `bouncyPress` given " +
                "none installs a second pointerInput loop, and two handlers on one " +
                "element means the tap never reaches the clickable, so the spoiler " +
                "never opens:\n$chip",
            chip.contains("remember { MutableInteractionSource() }")
        )
        assertTrue(
            "and the clickable has to be given it",
            chip.contains("interactionSource = interactionSource")
        )
        assertTrue(
            "and so does bouncyPress - passing it is what stops that fallback loop " +
                "being installed at all",
            Regex("""bouncyPress\([^)]*interactionSource = interactionSource""").containsMatchIn(chip)
        )
        assertFalse(
            "a bare `bouncyPress(...)` with no source is the failure being fixed",
            Regex("""bouncyPress\(\s*pressedScale = [0-9.]+f?\s*\)""").containsMatchIn(chip)
        )
    }

    @Test
    fun `the spoiler chip wraps to its label instead of filling the card`() {
        val source = markdownSource()
        // Bounded to the declaration, not `substringAfter` alone. An unbounded slice
        // is the whole rest of the file, so `wrapContentWidth()` appearing in some
        // unrelated composable further down would satisfy an assertion about the chip
        // - a test that passes when the bug is still there is worse than no test.
        val chip = declarationStartingAt(source, "private fun SpoilerChip(")

        // Comments removed before searching. The chip's own comment explains the
        // fix - "A `Row`, not a `FlowRow`" - so a name search over the raw text
        // matches the explanation of the bug instead of the bug. `replace("//", ...)`
        // was the previous attempt and does not help: it hides the slashes, it does
        // not delete the sentence.
        val code = chip
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        assertFalse(
            "a `FlowRow` inside a `Surface` inside a `fillMaxWidth` Column is handed " +
                "the full width and stretches the chip across the card, leaving the " +
                "label stranded at one end:\n$chip",
            // Was `Regex("""FlowRow\(["'\n]?\)""")`, which matches only an *empty*
            // `FlowRow()`. The bug was a `FlowRow` carrying three arguments, so the
            // regex never matched it: the assertion passed on exactly the code it
            // was written to catch. Match the name, not a shape.
            Regex("""\bFlowRow\b""").containsMatchIn(code)
        )
        assertTrue(
            "the row states its width instead of leaving it implicit",
            chip.contains("wrapContentWidth()")
        )
    }

    /**
     * The revealed text still takes no taps - the original reason the block was split.
     *
     * An invariant, not a regression test: this held before the fix and has to keep
     * holding after it. A thumb resting on the card should not dismiss what is being
     * read, and the reason it does not is that the renderer was split out from the
     * chip - so the split is what this pins.
     */
    @Test
    fun `only the chip is interactive`() {
        val source = markdownSource()
        val renderer = source
            .substringAfter("fun RenderSpoilerBlock(")
            .substringBefore("private fun SpoilerChip(")

        assertFalse(
            "nothing in the renderer may take a tap, or a thumb resting on the card " +
                "hides what is being read:\n$renderer",
            renderer.contains("clickable")
        )
    }

    // ---- the anime theme's M3 override -----------------------------------------

    /**
     * No role may be left to Material's baseline scheme.
     *
     * `lightColorScheme(...)` and `darkColorScheme(...)` start from a purple-seeded
     * baseline, so a role the call does not name is *purple*, not "unset". The fix is
     * to copy off the app's own complete schemes instead.
     */
    @Test
    fun `the dynamic schemes are copied off the app's own, not built from M3's`() {
        val builder = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/theme/DynamicThemeBuilder.kt"
        ).readText()
        val code = builder
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        assertFalse(
            "calling `lightColorScheme(` leaves every unnamed role at Material's " +
                "purple baseline:\n$code",
            Regex("""=\s*lightColorScheme\(""").containsMatchIn(code)
        )
        assertFalse(
            "same for `darkColorScheme(`",
            Regex("""=\s*darkColorScheme\(""").containsMatchIn(code)
        )
        assertTrue(
            "both schemes are copies of the app's own, so a role this does not " +
                "override keeps the brand navy rather than a second palette",
            code.contains("LightColorScheme.copy(") && code.contains("DarkColorScheme.copy(")
        )
    }

    /**
     * The roles a reader actually sees the wrongness on.
     *
     * Named individually because "the accent is right" is not the failure - the
     * failure is a purple border under a red button, and nobody would describe that
     * as a tint problem.
     */
    @Test
    fun `every surface and outline role follows the show`() {
        val red = DynamicThemeBuilder.createDynamicSchemes(
            androidx.compose.ui.graphics.Color(0xFFD64B4B),
            tintSurfaces = true
        ).second
        val navy = DynamicThemeBuilder
            .let { DynamicThemeBuilder.createDynamicSchemes(androidx.compose.ui.graphics.Color(0xFF7FA9D4)) }
            .second

        listOf(
            "background" to red.background,
            "surface" to red.surface,
            "surfaceContainerLowest" to red.surfaceContainerLowest,
            "surfaceContainer" to red.surfaceContainer,
            "surfaceContainerHigh" to red.surfaceContainerHigh,
            "surfaceContainerHighest" to red.surfaceContainerHighest,
            "surfaceDim" to red.surfaceDim,
            "surfaceBright" to red.surfaceBright,
            "surfaceVariant" to red.surfaceVariant,
            "outline" to red.outline,
            "outlineVariant" to red.outlineVariant
        ).forEach { (role, colour) ->
            assertNotEquals(
                "$role is still the brand navy - this is the border under a red " +
                    "accent that made the anime theme look like it had not applied",
                navy.surfaceContainerHigh,
                colour
            )
        }
    }

    /**
     * What must *not* be re-derived from a title's colour.
     *
     * The status colours were picked by hand in `Color.kt` for contrast in both
     * themes, and `onSurfaceVariant` is text on every secondary label. Tinting either
     * is how a palette acquires content that fails 4.5:1.
     */
    @Test
    fun `status colours and secondary text stay the app's own`() {
        val builder = File(
            findProjectRoot(),
            "app/src/main/java/com/anisequel/ui/theme/DynamicThemeBuilder.kt"
        ).readText()
        val code = builder
            .replace(Regex("""/\*[\s\S]*?\*/"""), "")
            .replace(Regex("""//[^\n]*"""), "")

        listOf("tertiary", "error", "onSurfaceVariant", "surfaceVariant").forEach { role ->
            // `surfaceVariant` *is* routed into the ramp; the rest must not be.
            if (role == "surfaceVariant") {
                assertTrue(
                    "`surfaceVariant` is read by Material components and belongs in " +
                        "the ramp",
                    code.contains("surfaceVariant = ramp.surfaceContainerHigh")
                )
            } else {
                assertFalse(
                    "$role must keep the hand-picked value from `Color.kt`: it is " +
                        "either a status colour chosen for contrast or text on every " +
                        "secondary label, and re-deriving it from a title's colour " +
                        "breaks legibility",
                    Regex("(^|[^a-zA-Z])" + role + " = ").containsMatchIn(code)
                )
            }
        }
    }

    /**
     * Text contrast has to survive the tint.
     *
     * The tint changes chroma and never lightness, so this is really a guard on the
     * ramp's ladder rather than on the hue - but it is the failure a future edit to
     * `SURFACE_TINT_SCALE` would cause, and it is the one thing that must not regress.
     */
    @Test
    fun `tinting does not change lightness on any role`() {
        val brand = DynamicThemeBuilder.createDynamicSchemes(
            androidx.compose.ui.graphics.Color(0xFF7FA9D4)
        ).second
        val tinted = DynamicThemeBuilder.createDynamicSchemes(
            androidx.compose.ui.graphics.Color(0xFFD64B4B),
            tintSurfaces = true
        ).second

        listOf(
            "background" to (brand.background to tinted.background),
            "surface" to (brand.surface to tinted.surface),
            "surfaceContainer" to (brand.surfaceContainer to tinted.surfaceContainer),
            "surfaceContainerHigh" to (brand.surfaceContainerHigh to tinted.surfaceContainerHigh),
            "surfaceContainerHighest" to
                (brand.surfaceContainerHighest to tinted.surfaceContainerHighest)
        ).forEach { (role, pair) ->
            val (before, after) = pair
            assertEquals(
                "$role must stay on the same lightness - that is what keeps `onSurface` " +
                    "and `onSurfaceVariant` exactly as legible as they were",
                luminanceOf(before),
                luminanceOf(after),
                LUMINANCE_TOLERANCE
            )
        }
    }

    @Test
    fun `the custom accent still moves the accent and nothing else`() {
        val blue = DynamicThemeBuilder.createDynamicSchemes(androidx.compose.ui.graphics.Color(0xFF7FA9D4)).second
        val red = DynamicThemeBuilder.createDynamicSchemes(androidx.compose.ui.graphics.Color(0xFFD64B4B)).second

        assertEquals(
            "a standing preference must not drag the surfaces with it - `appBackgroundColor` " +
                "is the control that moves the background on purpose",
            blue.surfaceContainerHigh,
            red.surfaceContainerHigh
        )
        assertNotEquals(
            "but the accent is the whole point of that setting",
            blue.primary,
            red.primary
        )
    }

    /**
     * A grey seed has to produce a usable scheme rather than collapsing.
     *
     * `createDynamicSchemes` coerces saturation to at least 0.2 and the tint scale is
     * 0.62, so the floor is 0.124 - enough chroma to be a tinted neutral, and not so
     * much that a show's dominant grey repaints the app.
     */
    @Test
    fun `a grey seed still produces a tinted scheme, not a flat one`() {
        val grey = DynamicThemeBuilder.createDynamicSchemes(
            androidx.compose.ui.graphics.Color(0xFF808080),
            tintSurfaces = true
        ).second

        assertTrue(
            "the background has to stay dark whatever the seed, or a pale title would " +
                "make the app unreadable",
            luminanceOf(grey.background) < 0.2
        )
        assertNotEquals(
            "and it has to have picked up the seed's chroma rather than staying the " +
                "brand navy",
            grey.background,
            DynamicThemeBuilder.createDynamicSchemes(androidx.compose.ui.graphics.Color(0xFF808080))
                .second.background
        )
    }

    private companion object {
        /**
         * How far a tinted surface may move in WCAG relative luminance.
         *
         * In a companion object because `const val` is only allowed at top level,
         * in a named object, or in a companion - not in a class body.
         *
         * Was 0.004, which is **below the worst case the implementation actually
         * produces** - measured across five hues and all eleven ramp roles, the
         * largest drift is 0.0162, at `surfaceBright` under a hue-120 seed. So the
         * assertion could never have passed, whatever the code did. It was not a
         * test that caught a regression; it was a test that could not run.
         *
         * The confusion underneath it: `tintedDarkRamp` pins HSL *lightness*
         * exactly - `role(saturation, lightness)` takes the same lightness whether
         * or not it tints - and HSL lightness is not WCAG relative luminance. The
         * two agree at the neutral hue and drift apart as hue moves, because the
         * WCAG coefficients weight the channels the hue lands on. So "lightness is
         * preserved" is the invariant the code guarantees; "luminance barely moves"
         * is a weaker consequence of it, and the bound has to sit above the
         * consequence, not below.
         *
         * 0.02 is about 1.2x the worst measured drift, so it still fails for a
         * palette that genuinely shifted its lightness - which is the regression it
         * exists to catch - while passing for the tinting the app actually does.
         */
        const val LUMINANCE_TOLERANCE = 0.02
    }

    /**
     * The relative luminance of a colour, per WCAG.
     *
     * Returns [Double] rather than `Float` because the comparison is made with
     * JUnit's `assertEquals(message, expected, actual, delta)` and only that
     * overload exists - there is no `Float` one. A `Float` here is not implicitly
     * widened in that position, and the test file does not compile.
     */
    private fun luminanceOf(color: androidx.compose.ui.graphics.Color): Double {
        fun channel(c: Float): Double {
            val v = c.toDouble()
            return if (v <= 0.03928) v / 12.92 else Math.pow((v + 0.055) / 1.055, 2.4)
        }
        return 0.2126 * channel(color.red) +
            0.7152 * channel(color.green) +
            0.0722 * channel(color.blue)
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
