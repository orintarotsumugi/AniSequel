package com.anisequel

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import com.anisequel.data.repository.MAX_FONT_SCALE
import com.anisequel.ui.theme.anisequelTypography
import com.anisequel.ui.theme.asHeroTitle
import com.anisequel.ui.theme.withLeadingFactor
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Leading has to grow with the reader's text size.
 *
 * ## What this caught
 *
 * Settings > Type & reading scales every style in `anisequelTypography`, which means
 * each style's `lineHeight` grows with it - and five call sites then replaced that
 * line height with a literal:
 *
 * ```kotlin
 * style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 22.sp)
 * ```
 *
 * The literal is right for the default 14sp body text. At the slider's 175% the
 * glyphs are 24.5sp and the lines are still 22sp apart, so consecutive lines
 * overlapped: the synopsis, every comment body, the tier tiles' captions, the
 * segmented bars' labels and both hero titles were unreadable at the largest size
 * the app offers, and each of them looked like a rendering fault rather than like a
 * setting the reader chose.
 *
 * The hero title was the worst of the five: it also pinned `letterSpacing =
 * (-0.4).sp`, so the tracking stayed at its 100% value while every measurement
 * around it grew.
 *
 * ## What is asserted
 *
 * Three things, and none of them is a preference:
 *
 * - the *scaled* type scale never has leading shorter than its own glyphs;
 * - the two helpers that express a style modification - [withLeadingFactor] and
 *   [asHeroTitle] - keep that property at every stop the slider offers;
 * - no source file pins a `lineHeight` to a literal on a style whose font size is
 *   still the theme's, which is the exact shape of the bug. The miniature preview
 *   styles in `LayoutPreviews.kt` pin *both* size and leading, so they are a
 *   different thing and are allowed by the last rule.
 */
class TextScaleLeadingTest {

    private val root: File = findProjectRoot()

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    /** Every style in the scale, so a new one cannot skip the check. */
    private fun styles(typography: Typography): List<Pair<String, TextStyle>> = listOf(
        "displayLarge" to typography.displayLarge,
        "displayMedium" to typography.displayMedium,
        "displaySmall" to typography.displaySmall,
        "headlineLarge" to typography.headlineLarge,
        "headlineMedium" to typography.headlineMedium,
        "headlineSmall" to typography.headlineSmall,
        "titleLarge" to typography.titleLarge,
        "titleMedium" to typography.titleMedium,
        "titleSmall" to typography.titleSmall,
        "bodyLarge" to typography.bodyLarge,
        "bodyMedium" to typography.bodyMedium,
        "bodySmall" to typography.bodySmall,
        "labelLarge" to typography.labelLarge,
        "labelMedium" to typography.labelMedium,
        "labelSmall" to typography.labelSmall
    )

    @Test
    fun `no style at the largest reading size has leading shorter than its glyphs`() {
        val largest = styles(anisequelTypography(MAX_FONT_SCALE))
        val offenders = largest.filter { (_, style) ->
            style.lineHeight.value < style.fontSize.value
        }.map { (name, style) -> "$name: ${style.fontSize.value}sp text on ${style.lineHeight.value}sp lines" }
        assertTrue(
            "a line height below the font size draws consecutive lines on top of " +
                "each other:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    @Test
    fun `a leading factor keeps the style's proportion at every size`() {
        val factor = 22f / 21f
        listOf(1f, 1.5f, MAX_FONT_SCALE).forEach { scale ->
            val base = anisequelTypography(scale).bodyMedium
            val loose = base.withLeadingFactor(factor)
            assertEquals(
                "the factor has to multiply the scaled leading, not replace it (at $scale)",
                base.lineHeight.value * factor,
                loose.lineHeight.value,
                0.001f
            )
            assertEquals(
                "a leading override must not touch the glyph size (at $scale)",
                base.fontSize.value,
                loose.fontSize.value,
                0.001f
            )
        }
    }

    /**
     * The hero title keeps its design at 100% and its *proportions* everywhere else.
     *
     * 30sp leading and -0.4sp tracking against `headlineSmall`'s 22sp base is what
     * the two heroes drew before; the test pins the 100% numbers so the design cannot
     * drift, and the ratio so a later scale cannot reintroduce the overlap.
     */
    @Test
    fun `the hero title scales its leading and tracking together`() {
        val atDefault = anisequelTypography(1f).headlineSmall.asHeroTitle()
        assertEquals(30f, atDefault.lineHeight.value, 0.001f)
        assertEquals(-0.4f, atDefault.letterSpacing.value, 0.001f)

        listOf(1f, 1.25f, MAX_FONT_SCALE).forEach { scale ->
            val style = anisequelTypography(scale).headlineSmall.asHeroTitle()
            val ratio = style.lineHeight.value / style.fontSize.value
            assertEquals(
                "leading must stay a fixed share of the font size at $scale",
                30f / 22f,
                ratio,
                0.001f
            )
            assertTrue(
                "the hero title must keep its own leading above its glyphs at $scale",
                style.lineHeight.value > style.fontSize.value
            )
            // Tracking is negative here, so the test is that it *grew* with the text
            // rather than staying at the 100% value it used to be pinned to.
            assertEquals(
                "tracking must stay a fixed share of the font size at $scale",
                -0.4f / 22f,
                style.letterSpacing.value / style.fontSize.value,
                0.001f
            )
        }
    }

    /**
     * A `lineHeight = <literal>.sp` override on a themed style is the bug's shape.
     *
     * The regex only reads a `copy(...)` whose arguments contain no nested
     * parentheses, so a factor expression (`lineHeight = style.lineHeight * 0.96f`) is
     * never matched - it has no numeric literal - and neither is a call with a lambda.
     * What it does catch is the exact form all five sites had.
     */
    @Test
    fun `no source pins a line height to a literal on a themed style`() {
        val pattern = Regex("""copy\([^)]*lineHeight\s*=\s*[0-9.]+\.sp""")
        val overridesFontSize = Regex("""fontSize\s*=\s*[0-9.]+\.sp""")
        val ui = File(root, "app/src/main/java/com/anisequel/ui")
        assertTrue("the ui source tree is missing", ui.isDirectory)

        val offenders = ui.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .flatMap { file ->
                // Comments are stripped first: `withLeadingFactor`'s own KDoc quotes
                // the call it replaced, and a guard that cannot tell an explanation
                // from a call fails on the comment documenting the bug it prevents.
                // Removing text can only hide a call here, never invent one.
                val text = file.readText()
                    .replace(Regex("/\\*[\\s\\S]*?\\*/"), "")
                    .replace(Regex("//[^\\n]*"), "")
                pattern.findAll(text).mapNotNull { match ->
                    if (overridesFontSize.containsMatchIn(match.value)) {
                        // A miniature that pins its own font size too: the leading is
                        // part of the same hand-built proportion, not an override of a
                        // scaled one.
                        null
                    } else {
                        "${file.relativeTo(root)}: ${match.value.replace('\n', ' ')}"
                    }
                }
            }
            .toList()

        assertTrue(
            "a literal line height is right at 100% and overlaps its own glyphs at " +
                "the largest reading size. Use withLeadingFactor(...) or " +
                "asHeroTitle() instead:\n" + offenders.joinToString("\n"),
            offenders.isEmpty()
        )
    }

    /** Guards the helper's unit handling: a factor must stay in sp. */
    @Test
    fun `the leading factor stays in sp`() {
        val scaled = TextStyle(fontSize = 14.sp, lineHeight = 21.sp)
            .withLeadingFactor(1.05f)
        assertEquals(22.05f, scaled.lineHeight.value, 0.001f)
    }
}
