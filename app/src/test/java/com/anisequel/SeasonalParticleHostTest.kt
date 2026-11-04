package com.anisequel

import org.junit.Assert.assertEquals
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
 *    at the settings screen, not at the decoration. Since 1.8.1 the sky *does*
 *    react to taps, so the test also pins where that observer lives: on the app's
 *    root, in the Initial pass, and without a single `consume(`.
 *  - **It must stop.** A frame loop without the resume gate keeps drawing behind a
 *    paused Activity and over a page nobody is looking at; a loop without the
 *    Instant check ignores the one setting that says "stop animating".
 *  - **It must sit between the page and the chrome.** Over the `NavHost`, under
 *    the bar: over the page, under the controls the reader needs. The drift gathers
 *    on the screen's bottom edge; the bar's own cap is a second, non-interactive
 *    Canvas drawn over the bar, and the pill's real bounds are the only geometry it
 *    is allowed to use.
 *  - **The settings page has doors for all of it.** A shape grid nobody reaches,
 *    a settle switch with no test tag, or a wind control that never got wired to
 *    the layer is a feature that exists only in this repository.
 */
class SeasonalParticleHostTest {

    private val root: File = findProjectRoot()

    private fun source(path: String): String =
        withoutComments(File(root, path).readText())

    private val layer: String
        get() = source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalParticleLayer.kt")

    private val host: String
        get() = source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalParticleHost.kt")

    private val ambient: String
        get() = source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalAmbient.kt")

    private val appNavigation: String
        get() = source("app/src/main/java/com/anisequel/ui/navigation/AppNavigation.kt")

    private val barTrail: String
        get() = source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalBarTrail.kt")

    @Test
    fun `the overlay, its host and the bar cap never take a touch`() {
        listOf(
            "SeasonalParticleLayer.kt" to layer,
            "SeasonalParticleHost.kt" to host,
            "SeasonalBarTrail.kt" to barTrail
        )
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
                assertFalse(
                    "$name must not consume anything; it is a sibling of every page",
                    lowered.contains("consume(")
                )
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
    fun `the sky settles and reacts, and the layer is wired to both`() {
        assertTrue(
            "the layer never creates a drift, so the settle switch does nothing",
            layer.contains("SeasonalPile(")
        )
        assertTrue(
            "the layer never draws the drift",
            layer.contains("drawSeasonalPile(")
        )
        assertTrue(
            "the layer never decays the wind",
            layer.contains("SeasonalWind.decay(")
        )
        assertTrue(
            "the layer never reads the gust, so scrolling cannot move the sky",
            layer.contains("SeasonalWind.gust")
        )
        assertTrue(
            "the layer never reads the tap pulse, so tapping cannot move the sky",
            layer.contains("SeasonalWind.pulse")
        )
        assertTrue(
            "the host does not pass settle to the layer",
            host.contains("settle = settings.settle")
        )
        assertTrue(
            "the host does not pass wind strength to the layer",
            host.contains("windStrength = settings.windStrength")
        )
    }

    @Test
    fun `the ambient observer is installed at the root and never consumes a gesture`() {
        assertTrue(
            "AppNavigation does not install the scroll/tap observer",
            appNavigation.contains("rememberSeasonalAmbientModifier()")
        )
        assertTrue(
            "the observer must be part of the root Box that owns every page",
            appNavigation.contains(".then(seasonalAmbient)")
        )
        assertTrue(
            "the tap observer must read the Initial pass, before buttons consume the tap",
            ambient.contains("PointerEventPass.Initial")
        )
        assertFalse(
            "the ambient observer must never consume an event; that would break every page",
            ambient.lowercase().contains("consume(")
        )
        assertTrue(
            "the scroll observer must return the delta it was given, not swallow it",
            ambient.contains("return Offset.Zero")
        )
        assertTrue(
            "a tap must be able to reach the wind state",
            ambient.contains("SeasonalWind.tapAt(")
        )
        assertTrue(
            "the scroll connection must actually feed the wind",
            ambient.contains("SeasonalWind.scroll(consumed)")
        )
    }

    @Test
    fun `the drift gathers on the bottom and the bar publishes its own pill`() {
        val mainChrome = source("app/src/main/java/com/anisequel/ui/navigation/MainChrome.kt")
        val mainTabs = source("app/src/main/java/com/anisequel/ui/navigation/MainTabs.kt")

        assertTrue(
            "the bar never publishes its capsule, so the snow cap has no surface to sit on",
            mainTabs.contains("SeasonalChrome::publishBar")
        )
        assertTrue(
            "a hidden bar leaves a stale capsule and its cap floats over the next page",
            mainChrome.contains("SeasonalChrome.clearBar")
        )
        assertTrue(
            "nothing draws the cap over the bar, so the drift is hidden behind it",
            mainChrome.contains("SeasonalBarTrail(")
        )
        assertTrue(
            "the cap is drawn before the bar, where the bar's own surface hides it",
            mainChrome.indexOf("SeasonalBarTrail(") > mainChrome.indexOf("MainBar(")
        )
        assertTrue(
            "the trail does not read the published capsule",
            barTrail.contains("SeasonalChrome.barBounds")
        )
        assertTrue(
            "the trail does not take its thickness from the drift above it",
            barTrail.contains("SeasonalChrome.skyPile")
        )
        assertTrue(
            "the app-wide layer never publishes the sky the cap belongs to",
            layer.contains("SeasonalChrome.publishSky(")
        )
        assertFalse(
            "the Settings preview must not publish, or it would paint its drift on a bar " +
                "that is not under it and clear the real sky on the way out",
            source("app/src/main/java/com/anisequel/ui/screens/settings/SeasonalSettingsTab.kt")
                .contains("publishesBarTrail = true")
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
    fun `the settings page offers the shape groups, settle depth and wind`() {
        val tab = source("app/src/main/java/com/anisequel/ui/screens/settings/SeasonalSettingsTab.kt")
        assertTrue(
            "the shapes are not grouped by season, so the page is a flat wall of chips",
            tab.contains("seasonalEffectsIn(")
        )
        assertTrue(
            "there is no shortcut to the sky the current season calls for",
            tab.contains("seasonal_season_button")
        )
        assertTrue("no settle switch", tab.contains("seasonal_settle_switch"))
        assertTrue("no pile depth slider", tab.contains("seasonal_pile_depth_slider"))
        assertTrue("no wind switch", tab.contains("seasonal_wind_switch"))
        assertTrue("no wind strength slider", tab.contains("seasonal_wind_strength_slider"))
        assertTrue(
            "the preview does not pass settling through",
            tab.contains("settle = settings.settle")
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

    @Test
    fun `the removed shapes stay removed`() {
        val effect = source("app/src/main/java/com/anisequel/domain/seasonal/SeasonalEffect.kt")
        val shapes = source(
            "app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalParticleShapes.kt"
        )
        val leaves = source(
            "app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalLeafShapes.kt"
        )
        listOf("BUBBLE(", "FIREFLY(", "STAR(").forEach { entry ->
            assertFalse(
                "$entry was deleted from the shape set; a leftover enum entry would" +
                    " resurrect a sky with no drawing behind it",
                effect.contains(entry)
            )
        }
        listOf("drawFirefly", "drawStar").forEach { drawing ->
            assertFalse(
                "$drawing still builds a shape that nothing can select; the dispatcher" +
                    " cannot reach it, so it is dead ink in a drawing file",
                shapes.contains("$drawing(") || leaves.contains("DrawScope.$drawing(")
            )
        }
        assertTrue(
            "the new autumn leaves are missing",
            effect.contains("OAK(") && effect.contains("GINKGO(")
        )
    }

    /**
     * The domain's variant count and the palette file's list length have to agree, and
     * neither is in a position to see the other. A list one entry short is a variant that
     * silently borrows another shape's colours rather than an error.
     */
    @Test
    fun `every shape has one palette for each drawing it can produce`() {
        val variants = variantCountsByShape()
        val paletteSizes = paletteCountsByShape()
        assertFalse("parsed nothing from the domain file", variants.isEmpty())
        variants.forEach { (shape, variantCount) ->
            assertEquals(
                "$shape can draw $variantCount variants but has ${paletteSizes[shape]} palettes",
                variantCount,
                paletteSizes[shape]
            )
        }
    }

    private fun variantCountsByShape(): Map<String, Int> {
        val counts = mutableMapOf<String, Int>()
        var shape = ""
        source("app/src/main/java/com/anisequel/domain/seasonal/SeasonalEffect.kt")
            .lineSequence()
            .forEach { line ->
                SHAPE_ENTRY.find(line)?.let { shape = it.groupValues[1] }
                VARIANT_COUNT.find(line)?.let { match ->
                    if (shape.isNotEmpty()) counts[shape] = match.groupValues[1].toInt()
                }
            }
        return counts
    }

    private fun paletteCountsByShape(): Map<String, Int> {
        val sizes = mutableMapOf<String, Int>()
        var shape = ""
        var seen = 0
        source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalParticlePalettes.kt")
            .lineSequence()
            .forEach { line ->
                PALETTE_BLOCK.find(line)?.let { match ->
                    if (shape.isNotEmpty()) sizes[shape] = seen
                    shape = match.groupValues[1]
                    seen = 0
                }
                if (line.contains("ParticlePalette(Color(")) seen++
            }
        if (shape.isNotEmpty()) sizes[shape] = seen
        return sizes
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

    private companion object {
        /** An enum entry header in the domain file, with nothing but its name. */
        val SHAPE_ENTRY = Regex("^    ([A-Z]+)\\($")

        /** The drawing count an entry declares, and nothing else in the file. */
        val VARIANT_COUNT = Regex("variantCount = (\\d+)")

        /** Which shape a palette list belongs to. */
        val PALETTE_BLOCK = Regex("private val ([A-Z]+)_PALETTES")
    }
}
