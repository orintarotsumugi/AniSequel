package com.anisequel

import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.isWinterSnow
import com.anisequel.ui.components.seasonal.seasonalGlyphLegend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
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
 *    on the screen's bottom edge and nothing is painted over the bar - the sky used
 *    to add a snow cap on the pill itself, and the reader asked for the weather to
 *    stop dressing the control they press.
 *  - **The settings page has doors for all of it.** A shape grid nobody reaches,
 *    a settle switch with no test tag, or a wind control that never got wired to
 *    the layer is a feature that exists only in this repository.
 *  - **The ten chips are ten pictures.** The glyph shares the sky's drawing code, and
 *    that stays; what changed is that it is *posed* by `seasonalGlyphLegend`, and a pose
 *    table is numbers a JVM can read. The reported failure was variant zero for all ten -
 *    which, for the four shapes that share the snow dispatcher, is one dendrite four times -
 *    and a radius fraction of `0.32`, which left the shapes' own five-pixel detail gate two
 *    pixels away from an eighteen dp chip.
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

    private val pileCode: String
        get() = source("app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalPiles.kt")

    private val shapes: String
        get() = source(
            "app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalParticleShapes.kt"
        )

    private val snow: String
        get() = source(
            "app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalSnowShapes.kt"
        )

    private val glyph: String
        get() = source(
            "app/src/main/java/com/anisequel/ui/components/seasonal/SeasonalGlyphLegend.kt"
        )

    private val appNavigation: String
        get() = source("app/src/main/java/com/anisequel/ui/navigation/AppNavigation.kt")

    @Test
    fun `the overlay and its host never take a touch`() {
        listOf(
            "SeasonalParticleLayer.kt" to layer,
            "SeasonalParticleHost.kt" to host
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
    fun `the drift gathers on the bottom edge and nothing paints over the bar`() {
        val mainChrome = source("app/src/main/java/com/anisequel/ui/navigation/MainChrome.kt")
        val mainTabs = source("app/src/main/java/com/anisequel/ui/navigation/MainTabs.kt")
        val bar = source(
            "app/src/main/java/com/anisequel/ui/components/expressive/ExpressiveNavigationBar.kt"
        )

        // The drift sits on the screen's own bottom edge, behind the bar, so it
        // reads as snow that gathered on the floor rather than on a shelf.
        assertTrue(
            "the drift is no longer drawn from the bottom of the overlay",
            pileCode.contains("ground = height")
        )
        // And the bar is left alone: no canvas above it, and no layout callback
        // publishing its capsule to chrome that does not exist.
        assertFalse(
            "something still draws over the navigation bar",
            mainChrome.contains("SeasonalBarTrail(")
        )
        assertFalse(
            "the bar still publishes its capsule for chrome to draw on",
            mainTabs.contains("onCapsuleBounds") || bar.contains("onCapsuleBounds")
        )
        // The whole publish/observe channel for the cap is gone with it, so a
        // re-introduction has to be a deliberate change rather than a leftover.
        assertFalse(
            "the sky still publishes state for a bar cap to read",
            layer.contains("SeasonalChrome")
        )
    }

    /**
     * Above sixty hertz is a window policy, not a clock policy. Compose will happily
     * pace the frame loop at 144 and still be handed sixty frames a second by a
     * panel the system has bucketed into its default category, so the layer has to
     * ask for the high category - and put it back when the sky is off, because the
     * battery cost of a high-rate window outlives the decoration that needed it.
     */
    @Test
    fun `a sky above sixty asks the window for the high frame-rate category`() {
        assertTrue(
            "the host never asks the view for a refresh-rate category",
            host.contains("setRequestedFrameRate")
        )
        assertTrue(
            "the high category is never requested",
            host.contains("REQUESTED_FRAME_RATE_CATEGORY_HIGH")
        )
        // Android 14 owns this API. `lintRelease` is advisory here, so nothing but
        // this guard stands between the call and a NoSuchMethodError on Android 12
        // and 13 - the two releases this app still installs on.
        assertTrue(
            "the category call is not guarded by an SDK check",
            host.contains("SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE")
        )
        assertTrue(
            "the category is never released when the sky is off or capped",
            host.contains("REQUESTED_FRAME_RATE_CATEGORY_DEFAULT")
        )
        // The loop's floor used to be sixty seconds of frame time, which ignored a
        // faster choice rather than honouring it: whatever the reader picked, the
        // sky stepped sixty times a second.
        assertTrue(
            "the frame loop still refuses to pace faster than sixty",
            layer.contains("1f / MAX_SEASONAL_FRAME_HZ")
        )
    }

    @Test
    fun `the frame rate is a setting the page can reach`() {
        val tab = source("app/src/main/java/com/anisequel/ui/screens/settings/SeasonalSettingsTab.kt")
        val card = source("app/src/main/java/com/anisequel/ui/screens/settings/DeviceManagementCard.kt")

        assertTrue("the page does not draw the device card", tab.contains("DeviceCard("))
        assertTrue("the card is not dispatched from the page", tab.contains("onRateChange"))
        assertTrue(
            "the page never re-reads the screen, so a rate unlocked in the system " +
                "settings stays hidden until the app restarts",
            tab.contains("LaunchedEffect(resumed)")
        )
        // The choice list comes from the panel, and the pill is the same control the
        // rest of the page uses.
        assertTrue(
            "the card offers rates without asking the screen",
            card.contains("offered") && tab.contains("availableSeasonalFrameRates(")
        )
        assertTrue(
            "the pills are not animated in and out, so a rate appearing is a layout jump",
            card.contains("AnimatedVisibility(")
        )
        assertTrue(
            "the pills are tagged by their storage value, so the row cannot be driven " +
                "from a test or a UI check",
            card.contains("\"seasonal_frame_rate_\${option.storageValue}\"")
        )
        // The cover-cache row is the same control on the same card, and the file is
        // named for the card rather than the frame rate now - a rename that dropped
        // this path would silently stop reading the card at all.
        assertTrue(
            "the cache pills are not tagged, so the ceiling cannot be driven from a test",
            card.contains("\"device_cache_\${option.storageValue}\"")
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

    /**
     * The chip is a legend, not a frame of the sky - and still the sky's drawing.
     *
     * Both halves are requirements, and they pull against each other. A chip that poses
     * nothing shows what the *first* particle of that sky happens to look like: `variant` zero
     * gave Snow its stellar dendrite and Crystal its near-identical dendrite, two chips apart,
     * so the winter row could only be read from its labels; and `0.32` of an eighteen dp chip
     * is a radius two pixels above the point where every shape drops its branches, veins and
     * barbs, so the same saved setting drew a fern on one phone and six bare spokes on another.
     * The pose is the legend's job. The shared call is the chip's promise, and a second
     * illustration system - an `ImageVector`, a hand-built path - breaks it silently, because
     * the chip still looks right on the day it is drawn.
     */
    @Test
    fun `the chip legend poses every shape as a picture of its own`() {
        val legends = SeasonalEffect.entries.associateWith { seasonalGlyphLegend(it) }

        // Ten chips, ten (drawing, posture) pairs. A six-armed flake aliases every sixty
        // degrees and a blossom every seventy-two, so all ten tilts being different is the
        // weakest statement that makes the row readable.
        val poses = legends.values.map { it.variant to it.rotationDegrees }
        assertEquals(
            "two chips resolve to the same drawing in the same posture, and the reader is " +
                "back to choosing a shape from the word beside it",
            SeasonalEffect.entries.size,
            poses.toSet().size
        )
        assertNotEquals(
            "Snow and Crystal are one dendrite again",
            legends.getValue(SeasonalEffect.SNOW).variant,
            legends.getValue(SeasonalEffect.CRYSTAL).variant
        )
        val winter = SeasonalEffect.entries.filter { it.isWinterSnow }
        assertEquals("the snow family is not four shapes", 4, winter.size)
        assertTrue(
            "every winter chip asks for the same variant, which is how the row got smudged",
            winter.map { legends.getValue(it).variant }.toSet().size > 1
        )

        SeasonalEffect.entries.forEach { effect ->
            val legend = legends.getValue(effect)
            assertTrue(
                "$effect asks for variant ${legend.variant}, outside the ${effect.variantCount}" +
                    " drawings it can produce",
                legend.variant in 0 until effect.variantCount
            )
            assertTrue(
                "$effect's chip radius fraction ${legend.radiusFraction} is not bigger than the" +
                    " 0.32 that turned the shapes into smudges",
                legend.radiusFraction > 0.32f
            )
            assertTrue(
                "$effect's legend ink would spill out of the box the chip hands the canvas",
                legend.radiusFraction <= 0.46f
            )
            assertTrue(
                "$effect's tilt is a posture, not the sky's tumble",
                legend.rotationDegrees in -30f..30f
            )
            assertTrue("$effect asks for no hairline at all", legend.strokeFloor > 0f)
            assertTrue(
                "$effect's hairline is thick enough to fill its own shape in",
                legend.strokeFloor <= 1.5f
            )
        }

        // The invariant the legend must not trade away for a prettier picture.
        assertTrue(
            "the glyph no longer calls the shared drawing, so a chip can promise a shape the" +
                " overlay does not draw",
            glyph.contains("drawSeasonalParticle(")
        )
        listOf("ImageVector", "vectorResource", "painterResource", "androidx.compose.ui.res")
            .forEach { foreign ->
                assertFalse(
                    "the particle chips grew a second illustration system ($foreign); the" +
                        " shapes are Canvas ink and stay Canvas ink",
                    glyph.contains(foreign)
                )
            }
        assertFalse(
            "an emoji came back onto the shape chips",
            containsEmoji(glyph)
        )
        // The sky and the drift must still pay nothing for the chip's hairline: the floor is
        // an ask the caller makes, defaulted off, and the two frame-loop callers never make it.
        listOf("SeasonalParticleLayer.kt" to layer, "SeasonalPiles.kt" to pileCode)
            .forEach { (name, code) ->
                assertFalse(
                    "$name asks for a stroke floor, so every particle in the frame loop now" +
                        " draws thicker than the shape was sized for",
                    code.contains("strokeFloor")
                )
            }
    }

    @Test
    fun `the removed shapes stay removed`() {
        val effect = source("app/src/main/java/com/anisequel/domain/seasonal/SeasonalEffect.kt")
        // The snow family moved to its own file when the chip became a legend; the pin has to
        // read every file the ink was split across, or a resurrected `drawFirefly` sitting in
        // the snow file is invisible to it.
        val drawingCode = shapes + snow
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
                drawingCode.contains("$drawing(") || leaves.contains("DrawScope.$drawing(")
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
