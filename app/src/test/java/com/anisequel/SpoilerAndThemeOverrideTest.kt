package com.anisequel

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import com.anisequel.domain.color.Hct
import com.anisequel.domain.color.MaterialColorSchemes
import com.anisequel.domain.color.MaterialScheme
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
     * baseline, so a role the call does not name is *purple*, not "unset". The
     * builder now generates a [com.anisequel.domain.color.MaterialScheme] and copies
     * every one of its roles onto the Compose scheme. This asserts the copy is
     * complete by comparing each role's value, not by trusting the list.
     */
    @Test
    fun `every Material role is generated and copied onto the scheme`() {
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

        assertEquals(
            "the scheme and the test have to agree on how many roles Material 3 has",
            36,
            MaterialScheme.ROLE_NAMES.size
        )

        val seed = Color(0xFFD64B4B)
        val seedInt = seed.toArgb()
        val (light, dark) = DynamicThemeBuilder.createDynamicSchemes(seed, tintSurfaces = true)
        listOf(
            Triple(
                false,
                light,
                MaterialColorSchemes.tonalSpot(seedInt, dark = false, tintSurfaces = true)
            ),
            Triple(
                true,
                dark,
                MaterialColorSchemes.tonalSpot(seedInt, dark = true, tintSurfaces = true)
            )
        ).forEach { (isDark, actual, expected) ->
            MaterialScheme.ROLE_NAMES.forEach { role ->
                assertEquals(
                    "${if (isDark) "dark" else "light"}.$role is not the generated " +
                        "value - a role the builder forgot keeps whichever scheme it " +
                        "was copied from, which is the purple-border bug",
                    expected.role(role),
                    actual.role(role)
                )
            }
        }
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
            .createDynamicSchemes(androidx.compose.ui.graphics.Color(0xFF7FA9D4))
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
                navy.role(role),
                colour.toArgb()
            )
        }
    }

    /**
     * The error palette is a semantic colour, not part of the show's identity.
     *
     * A red cover must not make an error state indistinguishable from the accent,
     * and a green one must not make it green. Material 3 fixes the error palette
     * (hue ~25); the builder keeps it fixed and only samples the tone ladder.
     */
    @Test
    fun `the error palette stays red whatever the seed`() {
        val seeds = listOf(
            0xFFD64B4B.toInt(), 0xFF4BA86B.toInt(), 0xFF7FA9D4.toInt(), 0xFF9B5BD6.toInt()
        )
        seeds.forEach { seed ->
            listOf(false, true).forEach { dark ->
                val scheme = MaterialColorSchemes.tonalSpot(seed, dark = dark, tintSurfaces = true)
                val hue = Hct.fromInt(scheme.error).hue
                val delta = angularDistance(hue, 25.0)
                assertTrue(
                    "the error role sits $delta degrees from Material's red, so a " +
                        "title's accent has replaced the one colour that has to mean " +
                        "the same thing in every theme",
                    delta < 15.0
                )
                assertTrue(
                    "onError has to be readable on error",
                    ColorUtils.calculateContrast(scheme.onError, scheme.error) >= 4.5
                )
            }
        }
    }

    /**
     * Secondary text keeps AA contrast in every scheme.
     *
     * This was the requirement behind the old hand-picked `onSurfaceVariant`, and
     * generating it is only acceptable because the tone ladder guarantees the same
     * pair. Asserted on the generated values rather than the source, because the
     * source can name the role and still pair it with the wrong tone.
     */
    @Test
    fun `secondary text keeps AA contrast in every scheme`() {
        val seeds = listOf(
            0xFFD64B4B.toInt(), 0xFF4BA86B.toInt(), 0xFF7FA9D4.toInt(), 0xFFFFD54F.toInt()
        )
        seeds.forEach { seed ->
            listOf(false, true).forEach { dark ->
                val scheme = MaterialColorSchemes.tonalSpot(seed, dark = dark, tintSurfaces = true)
                assertTrue(
                    "${if (dark) "dark" else "light"}: onSurface on surface",
                    ColorUtils.calculateContrast(scheme.onSurface, scheme.surface) >= 4.5
                )
                assertTrue(
                    "${if (dark) "dark" else "light"}: onSurfaceVariant on " +
                        "surfaceVariant",
                    ColorUtils.calculateContrast(
                        scheme.onSurfaceVariant,
                        scheme.surfaceVariant
                    ) >= 4.5
                )
                assertTrue(
                    "${if (dark) "dark" else "light"}: onSurfaceVariant on surface - " +
                        "the pair secondary labels are actually drawn on",
                    ColorUtils.calculateContrast(
                        scheme.onSurfaceVariant,
                        scheme.surface
                    ) >= 4.5
                )
                assertTrue(
                    "${if (dark) "dark" else "light"}: onPrimary on primary",
                    ColorUtils.calculateContrast(scheme.onPrimary, scheme.primary) >= 4.5
                )
            }
        }
    }

    /**
     * Text contrast has to survive the tint.
     *
     * Both palettes are sampled at the same HCT tones, so the tinted and brand
     * surfaces share a luminance ladder by construction. This is the guard that a
     * future edit to the neutral palette's chroma cannot quietly move a surface to
     * a different tone, which is what would break `onSurface` and
     * `onSurfaceVariant`.
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
     * A grey cover has no hue worth trusting, so the builder falls back to the
     * brand hue and the primary-chroma floor. The surfaces then come from the same
     * (slightly chromatic) neutral palette as the tinted path, which keeps the
     * page dark and gives it a blue-grey cast instead of the old flat navy.
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
            "and it has to have picked up the neutral palette rather than staying the " +
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
         * This used to be 0.02, and the comment explained at length why "HSL
         * lightness is preserved but luminance drifts". That reasoning belonged to
         * the old HSL ramp and is now the wrong model: the builder samples both
         * palettes at the same HCT tone, tone is CIELAB L*, and L* determines Y.
         * The two must therefore have the same luminance up to the rounding of an
         * 8-bit channel - which is what 0.005 allows. A larger drift means the
         * tint left the tone ladder, which is the regression this guards.
         */
        const val LUMINANCE_TOLERANCE = 0.005
    }

    /**
     * The relative luminance of a colour, per WCAG.
     *
     * Returns [Double] so the comparison is made with JUnit's
     * `assertEquals(message, expected, actual, delta)` overload in [Double].
     * The channels are `Float`; widening once here keeps the arithmetic and the
     * 0.005 tolerance in the same type instead of introducing a `Float` delta
     * that rounds differently at the boundary.
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

    /**
     * The Compose scheme's value for a role, by the name the domain scheme uses.
     *
     * This is the test's own mapping on purpose: comparing through it is what catches
     * the builder wiring, say, `onSurfaceVariant` to `surfaceVariant`. A helper that
     * read the builder's own map would have the same mistake as the builder.
     */
    private fun ColorScheme.role(name: String): Int = when (name) {
        "primary" -> primary.toArgb()
        "onPrimary" -> onPrimary.toArgb()
        "primaryContainer" -> primaryContainer.toArgb()
        "onPrimaryContainer" -> onPrimaryContainer.toArgb()
        "inversePrimary" -> inversePrimary.toArgb()
        "secondary" -> secondary.toArgb()
        "onSecondary" -> onSecondary.toArgb()
        "secondaryContainer" -> secondaryContainer.toArgb()
        "onSecondaryContainer" -> onSecondaryContainer.toArgb()
        "tertiary" -> tertiary.toArgb()
        "onTertiary" -> onTertiary.toArgb()
        "tertiaryContainer" -> tertiaryContainer.toArgb()
        "onTertiaryContainer" -> onTertiaryContainer.toArgb()
        "error" -> error.toArgb()
        "onError" -> onError.toArgb()
        "errorContainer" -> errorContainer.toArgb()
        "onErrorContainer" -> onErrorContainer.toArgb()
        "background" -> background.toArgb()
        "onBackground" -> onBackground.toArgb()
        "surface" -> surface.toArgb()
        "onSurface" -> onSurface.toArgb()
        "surfaceVariant" -> surfaceVariant.toArgb()
        "onSurfaceVariant" -> onSurfaceVariant.toArgb()
        "surfaceTint" -> surfaceTint.toArgb()
        "inverseSurface" -> inverseSurface.toArgb()
        "inverseOnSurface" -> inverseOnSurface.toArgb()
        "outline" -> outline.toArgb()
        "outlineVariant" -> outlineVariant.toArgb()
        "scrim" -> scrim.toArgb()
        "surfaceBright" -> surfaceBright.toArgb()
        "surfaceContainer" -> surfaceContainer.toArgb()
        "surfaceContainerHigh" -> surfaceContainerHigh.toArgb()
        "surfaceContainerHighest" -> surfaceContainerHighest.toArgb()
        "surfaceContainerLow" -> surfaceContainerLow.toArgb()
        "surfaceContainerLowest" -> surfaceContainerLowest.toArgb()
        "surfaceDim" -> surfaceDim.toArgb()
        else -> throw IllegalArgumentException("unknown role '$name'")
    }

    private fun angularDistance(a: Double, b: Double): Double {
        val raw = kotlin.math.abs(a - b) % 360.0
        return if (raw > 180.0) 360.0 - raw else raw
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
