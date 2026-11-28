package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Source-level guards for the animation fixes that do not need a device to prove.
 *
 * The Compose UI test source set is not available in this development environment,
 * but these failures are still structural: an infinite clock must be gated by the
 * window and lifecycle, and its animated value must be consumed by a draw modifier,
 * not read while composing the whole screen.
 */
class AnimationWiringTest {

    private val root = findProjectRoot()

    private fun source(path: String): String = File(root, path).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

    @Test
    fun `empty orb stops when it is outside the window`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/components/expressive/ExpressiveVisuals.kt"
        )

        assertTrue("the orb must observe its window bounds", code.contains("onGloballyPositioned"))
        assertTrue("visibility must use window coordinates", code.contains("boundsInWindow"))
        assertTrue(
            "the morph branch must be disabled off-screen",
            code.contains("!visibleInWindow")
        )
    }

    @Test
    fun `backdrop drift is lifecycle gated and consumed in the draw phase`() {
        val code = source("app/src/main/java/com/anisequel/ui/components/PannableBackdrop.kt")

        assertTrue("drift must stop behind a paused Activity", code.contains("rememberIsResumed()"))
        assertTrue(
            "drift must stop when the banner has scrolled out of the window",
            code.contains("boundsInWindow") && code.contains("visibleInWindow")
        )
        val drawPhase = code.substringAfter(".graphicsLayer {")
        assertTrue(
            "the infinite phase must be read inside graphicsLayer",
            drawPhase.contains("driftPhase?.value")
        )
        assertFalse(
            "the old composition-time transform must not survive",
            code.substringBefore(".graphicsLayer {").contains("drift = driftPhase")
        )
    }

    @Test
    fun `the profile banner is still`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileHeaderCard.kt"
        )

        assertFalse(
            "the automatic sweep restarted from the middle after a visible jump; a " +
                "profile banner must not move on its own",
            code.contains("drift = true")
        )
        assertTrue("the banner is still drawn through PannableBackdrop", code.contains("PannableBackdrop("))
    }

    @Test
    fun `profile chart bars read their animated width while drawing`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileChartComponents.kt"
        )

        assertTrue("the chart bar must keep the animation State", code.contains("val width = animateFloatAsState"))
        assertTrue("the chart bar must read width in drawBehind", code.contains("width.value"))
        assertFalse("a chart bar must not delegate its draw-only animation in composition", code.contains("val width by"))
    }

    /**
     * A press has to scale the thing the finger is on, not the text inside it.
     *
     * `bouncyPress` is a `graphicsLayer`, and a layer transforms only what is
     * drawn inside it. Installed after `.clip(...).background(...)` - which a score
     * of call sites had done - the press layer contains the label while the pill's
     * own surface is drawn outside it, so a tap shrank the label inside a shape
     * that never moved. This walks each call site's modifier chain and fails on a
     * press installed behind a fill; the shared rule is documented on
     * [com.anisequel.ui.components.expressive.bouncyPress].
     */
    @Test
    fun `press springs wrap the surface they press`() {
        val offenders = mutableListOf<String>()
        File(root, "app/src/main/java").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val lines = source(file.relativeTo(root).path).lines()
                lines.forEachIndexed { index, line ->
                    if (!line.contains("bouncyPress(")) return@forEachIndexed
                    var cursor = index - 1
                    var behindFill = false
                    while (cursor >= 0) {
                        val trimmed = lines[cursor].trim()
                        if (!trimmed.startsWith(".")) break
                        if (trimmed.startsWith(".background(")) behindFill = true
                        cursor--
                    }
                    if (behindFill) offenders += "${file.name}:${index + 1}"
                }
            }

        assertTrue(
            "bouncyPress is installed after .background(...) at $offenders. A " +
                "graphicsLayer transforms only what is drawn inside it, so the press " +
                "scales the label while the pill's own surface stays put. Move the " +
                "press before the clip and the fill.",
            offenders.isEmpty()
        )
    }

    /**
     * A count may not tick past the number it is counting to.
     *
     * A spring on an `Int` interpolates the number itself, so a spatial spring's
     * overshoot is a visibly wrong value - 41 likes becoming 43 and back. The
     * counter and the like button therefore take [ExpressiveMotion.FastEffectsInt],
     * which is `NoBouncy` on every motion style. `FastSpatialInt` still exists for
     * positions; this test is about counts.
     */
    @Test
    fun `counts land on the number they show`() {
        val motion = source(
            "app/src/main/java/com/anisequel/ui/components/expressive/ExpressiveMotion.kt"
        )
        val effectsInt = motion
            .substringAfter("val FastEffectsInt", missingDelimiterValue = "")
            .substringBefore("val FastSpatialDp")
        assertTrue("no FastEffectsInt token in ExpressiveMotion", effectsInt.isNotEmpty())
        assertTrue(
            "FastEffectsInt must be NoBouncy on every style, or a count overshoots",
            effectsInt.contains("DampingRatioNoBouncy") &&
                !effectsInt.contains("DampingRatioMediumBouncy") &&
                !effectsInt.contains("DampingRatioLowBouncy")
        )

        val counter = source(
            "app/src/main/java/com/anisequel/ui/components/expressive/AnimatedCounter.kt"
        )
        assertTrue(
            "the counter must animate on FastEffectsInt",
            counter.contains("animationSpec = ExpressiveMotion.FastEffectsInt")
        )
        val likes = source("app/src/main/java/com/anisequel/ui/components/EngagementAction.kt")
        assertTrue(
            "a like count must animate on FastEffectsInt",
            likes.contains("animationSpec = ExpressiveMotion.FastEffectsInt")
        )
    }

    /**
     * A one-line crossfade must not animate its own box.
     *
     * These three swap content whose size was already known - tabular digits, one
     * 24dp icon, one 44dp rail - and the default `SizeTransform` measured both
     * copies on every frame to animate a size that never changed. `using null` is
     * the same fix the count badge carries.
     */
    @Test
    fun `fixed-size crossfades drop the box animation`() {
        listOf(
            "app/src/main/java/com/anisequel/ui/components/expressive/AnimatedCounter.kt",
            "app/src/main/java/com/anisequel/ui/components/AniTopAppBar.kt",
            "app/src/main/java/com/anisequel/ui/screens/login/IconCarousel.kt"
        ).forEach { path ->
            assertTrue(
                "$path still animates the box of a fixed-size swap; add `using null`",
                source(path).contains("using null")
            )
        }
    }

    /**
     * The artwork viewer's disclosure is not exempt from the motion setting.
     *
     * It was the one `AnimatedVisibility` left on `fadeIn()`/`expandVertically()`
     * defaults, which are Compose's own tween and spring: the reader's Motion style
     * never reached it and `INSTANT` still animated it.
     */
    @Test
    fun `the cover viewer disclosure follows the motion tokens`() {
        val details = source(
            "app/src/main/java/com/anisequel/ui/components/detail/CoverViewerDetails.kt"
        )
        assertTrue(
            "the disclosure must fade on the effects token",
            details.contains("fadeIn(ExpressiveMotion.FastEffects)")
        )
        assertTrue(
            "the disclosure must expand on the size token",
            details.contains("expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize)")
        )
        assertFalse(
            "the default enter/exit pair ignored the motion style",
            details.contains("enter = fadeIn() + expandVertically()")
        )
    }

    /**
     * A cancelled swipe must not settle outside its own button.
     *
     * The drag offset was returned on `PressSpatial`, a bouncy spring, so it
     * travelled below zero at the end of every cancelled drag and drew the fill a
     * few dp left of its clip. The settle is non-overshooting now, and the drawing
     * side clamps at zero as well.
     */
    @Test
    fun `the swipe settle cannot overshoot`() {
        val gestures = source(
            "app/src/main/java/com/anisequel/ui/components/cards/QuickAddGestures.kt"
        )
        assertTrue(
            "the drag must settle on a non-overshooting spring",
            gestures.contains("dragOffset.animateTo(0f, ExpressiveMotion.FastEffects)")
        )
        assertFalse(
            "a bouncy settle takes the fill below its own container",
            gestures.contains("dragOffset.animateTo(0f, ExpressiveMotion.PressSpatial)")
        )
        val button = source(
            "app/src/main/java/com/anisequel/ui/components/cards/HoldAndSwipePlanningButton.kt"
        )
        assertTrue(
            "the drawn offset must be clamped at the button's edge",
            button.contains("dragOffset.value.coerceAtLeast(0f)")
        )
    }

    @Test
    fun `bound slider syncs after composition`() {
        val code = source("app/src/main/java/com/anisequel/ui/components/BoundSliderState.kt")

        assertTrue("external slider writes need a SideEffect", code.contains("SideEffect"))
        assertTrue("the sync must avoid redundant state writes", code.contains("state.value != value"))
    }

    /**
     * An `AnimatedContent` content lambda may emit exactly one layout child.
     *
     * Its measure policy places *every* measurable at `contentAlignment` inside the
     * largest size - it is a Box with one position, not a Column - so a lambda that
     * emits several composables as siblings draws them all at the same origin.
     *
     * 2.5.4 put the Settings shelf inside one: the account card, three section
     * labels and three groups were siblings of the `else` branch, so every card
     * landed on top of every other. The shelf showed one group - "Device Manager",
     * "Updates", "About & support" - because the last one drawn was on top, and the
     * six cards above it in the list were under it and untappable. 2.5.3 was fine
     * because the swap was a plain `if`; the crossfade was the regression.
     *
     * So this pins both halves: no animated container around the search/shelf swap,
     * and the shelf still drawn from the column it belongs to - otherwise the first
     * assertion passes trivially on a screen with the shelf deleted.
     */
    @Test
    fun `the settings shelf is not the content of an animated container`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/settings/SettingsScreen.kt"
        )

        assertFalse(
            "the search/shelf swap must stay a plain `if`: `AnimatedContent` places the " +
                "content lambda's direct children on top of each other, which is what " +
                "collapsed the whole shelf onto its last group",
            code.contains("targetState = searching")
        )
        assertTrue(
            "the swap must still be a conditional in the scroll column",
            code.contains("if (searching) {")
        )
        assertTrue(
            "the shelf must still draw its cards",
            code.contains("SettingsAccountCard(") &&
                code.contains("SettingsSectionLabel(\"Device & app\")")
        )
    }

    /**
     * A video WebView must not be faded through a Compose compositing layer.
     *
     * The native view draws above Compose content and its video plane can be lost when
     * alpha or scale compositing is applied around it. The viewer therefore keeps the
     * view fully opaque and switches visibility only after the player is ready; the
     * loading ring remains visible until that hand-off (or the watchdog fallback).
     */
    @Test
    fun `the trailer video surface is revealed without alpha compositing`() {
        // The two files split by responsibility: the view and its layer rules live
        // in the embed, and everything the dialog draws around it lives in the
        // viewer. Reading one file for both was the assertion, not the requirement -
        // and it failed the build the moment the WebView moved.
        val player = source(
            "app/src/main/java/com/anisequel/ui/components/detail/info/NovaTrailerEmbed.kt"
        )
        val viewer = source(
            "app/src/main/java/com/anisequel/ui/components/detail/info/NovaTrailerViewer.kt"
        )

        assertTrue(
            "the WebView must stay opaque instead of fading through a compositing layer",
            player.contains("alpha = 1f")
        )
        assertTrue(
            "the video surface stays hidden until the player can be revealed",
            player.contains("visibility = View.INVISIBLE")
        )
        assertTrue(
            "the readiness callback reveals the real video surface discretely",
            player.contains("webView.visibility = View.VISIBLE")
        )
        assertTrue(
            "the WebView video surface requires its own hardware layer",
            player.contains("setLayerType(View.LAYER_TYPE_HARDWARE, null)")
        )
        assertFalse(
            "alpha animation on the native video surface can bring back the black-picture bug",
            viewer.contains("player.alpha =") ||
                (viewer.contains("snapshotFlow {") && viewer.contains("presence()"))
        )
        assertFalse(
            "a platform animator is a second clock the dialog cannot cancel or read",
            player.contains(".animate().alpha(") || viewer.contains(".animate().alpha(")
        )
        assertTrue(
            "the dialog entrance must still settle exactly at 1",
            viewer.contains("entrance.snapTo(1f)")
        )
        assertTrue(
            "the loading ring stays mounted only while the WebView is hidden",
            viewer.contains("if (!session.revealed)") && viewer.contains("NovaWavyRing(")
        )
        assertTrue(
            "the platform's own dim is the hard cut this dialog must not draw",
            viewer.contains("setDimAmount(0f)")
        )
    }

    /**
     * An arriving inbox row must not be faded twice.
     *
     * `NotificationEntrance` fades each card in; `animateItem()`'s default does too,
     * and the two multiplied - a new row arrived at the square of the curve its
     * neighbours were on. The bare call also used inline springs, so a reader who had
     * asked for Instant motion still got rows sliding.
     */
    @Test
    fun `an inbox row is faded by its entrance and not by the list`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/notifications/NotificationCenterScreen.kt"
        )
        val row = source(
            "app/src/main/java/com/anisequel/ui/screens/notifications/NotificationRow.kt"
        )

        assertTrue("both halves of the list must use it", code.contains("notificationRowPlacement()"))
        assertTrue(
            "the list must not add a second fade in",
            row.contains("fadeInSpec = null")
        )
        assertTrue(
            "the exit belongs to the list, because the entrance is gone by then",
            row.contains("fadeOutSpec = ExpressiveMotion.ListItemFadeOut")
        )
        assertFalse(
            "the bare default fades in and ignores the motion style",
            code.contains("Modifier.animateItem()") || row.contains("Modifier.animateItem()")
        )
    }

    private fun findProjectRoot(): File {
        var candidate = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(candidate, "gradle.properties").isFile) {
            candidate = candidate.parentFile ?: error("could not locate project root")
        }
        return candidate
    }
}
