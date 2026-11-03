package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The wiring the overlay cannot be allowed to get wrong, read off the source.
 *
 * There is no Android SDK in this development environment, so what can be pinned
 * on a JVM is where the layer is mounted and what it is not allowed to contain.
 * Both are properties of the emitted code rather than of a screenshot:
 *
 *  - **It must not take a tap.** The layer is drawn above every page, so one
 *    `clickable` or `pointerInput` in either file would swallow the first tap
 *    everywhere in the app - and the symptom (settings that need two taps) points
 *    at the settings screen, not at the decoration.
 *  - **It must stop.** A frame loop without the resume gate keeps drawing behind a
 *    paused Activity and over a page nobody is looking at; a loop without the
 *    Instant check ignores the one setting that says "stop animating".
 *  - **It must sit between the page and the chrome.** Over the `NavHost`, under
 *    the bar: over the page, under the controls the reader needs.
 */
class SeasonalParticleHostTest {

    private val root: File = findProjectRoot()

    private fun source(path: String): String =
        withoutComments(File(root, path).readText())

    private val layer: String
        get() = source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalParticleLayer.kt")

    private val host: String
        get() = source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalParticleHost.kt")

    private val appNavigation: String
        get() = source("app/src/main/java/com/anisequel/ui/navigation/AppNavigation.kt")

    @Test
    fun `the overlay never takes a touch`() {
        listOf("SeasonalParticleLayer.kt" to layer, "SeasonalParticleHost.kt" to host)
            .forEach { (name, code) ->
                // Lower-cased, because `combinedClickable` does not contain the
                // substring `clickable` and would have passed this test while
                // eating every first tap in the app.
                val lowered = code.lowercase()
                listOf(
                    "pointerinput", "clickable", "selectable", "toggleable",
                    "draggable", "tapgestures", "draggestures", "pointerinteropfilter"
                ).forEach { forbidden ->
                    assertFalse(
                        "$name contains $forbidden. The layer covers every page, so " +
                            "consuming a pointer here makes the whole app need two taps.",
                        lowered.contains(forbidden)
                    )
                }
            }
    }

    @Test
    fun `the frame loop is gated on resume and on the instant motion style`() {
        assertTrue(
            "without rememberIsResumed the sky keeps ticking behind a stopped Activity",
            layer.contains("rememberIsResumed()")
        )
        assertTrue(
            "the loop must be paced by the display rather than by delay()",
            layer.contains("withFrameNanos")
        )
        assertTrue(
            "Instant is the reader asking the app to stop animating; the overlay must obey",
            layer.contains("MotionStyle.INSTANT")
        )
    }

    @Test
    fun `the clock is throttled rather than advanced once per frame`() {
        assertTrue(
            "a 120Hz display must not draw the sky twice as fast",
            layer.contains("FRAME_SECONDS")
        )
        assertTrue(
            "a long stall must not teleport every particle",
            layer.contains("MAX_STEP_SECONDS")
        )
    }

    @Test
    fun `the host is mounted over every page and under the bar`() {
        val navHost = appNavigation.indexOf("NavHost(")
        val seasonal = appNavigation.indexOf("SeasonalParticleHost(")
        val bar = appNavigation.indexOf("MainBarHost(")

        assertTrue("the NavHost call must be found", navHost >= 0)
        assertTrue("SeasonalParticleHost is never mounted", seasonal >= 0)
        assertTrue("the bar call must be found", bar >= 0)
        assertTrue(
            "the sky is painted after the page it covers",
            navHost < seasonal
        )
        assertTrue(
            "the bar and the toast must stay above the sky",
            seasonal < bar
        )
    }

    @Test
    fun `the settings page exists and is reachable from the hub`() {
        val settings = source("app/src/main/java/com/anisequel/ui/screens/settings/SettingsScreen.kt")
        assertTrue("the enum has no seasonal page", settings.contains("SEASONAL("))
        assertTrue(
            "the hub row is missing, so the page has no door",
            settings.contains("settings_row_seasonal")
        )
        assertTrue(
            "the page is not dispatched",
            settings.contains("SettingsPage.SEASONAL -> SeasonalSettingsTab(")
        )
    }

    @Test
    fun `the shape chips draw a vector glyph rather than an emoji`() {
        val tab = source("app/src/main/java/com/anisequel/ui/screens/settings/SeasonalSettingsTab.kt")
        assertTrue(
            "a chip that does not draw the shared glyph can promise a shape the sky does not draw",
            tab.contains("SeasonalEffectGlyph(")
        )
        assertFalse(
            "no emoji as UI icons: the chips must use the Canvas glyph, never a text character",
            containsEmoji(tab)
        )
    }

    /** True when [code] contains a character from either emoji block. */
    private fun containsEmoji(code: String): Boolean =
        code.codePoints().anyMatch { point ->
            point in 0x1F000..0x1FAFF || point in 0x2600..0x27BF
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
