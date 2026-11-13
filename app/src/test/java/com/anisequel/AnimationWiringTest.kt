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
    fun `profile chart bars read their animated width while drawing`() {
        val code = source(
            "app/src/main/java/com/anisequel/ui/screens/profile/ProfileChartComponents.kt"
        )

        assertTrue("the chart bar must keep the animation State", code.contains("val width = animateFloatAsState"))
        assertTrue("the chart bar must read width in drawBehind", code.contains("width.value"))
        assertFalse("a chart bar must not delegate its draw-only animation in composition", code.contains("val width by"))
    }

    @Test
    fun `bound slider syncs after composition`() {
        val code = source("app/src/main/java/com/anisequel/ui/components/BoundSliderState.kt")

        assertTrue("external slider writes need a SideEffect", code.contains("SideEffect"))
        assertTrue("the sync must avoid redundant state writes", code.contains("state.value != value"))
    }

    private fun findProjectRoot(): File {
        var candidate = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (!File(candidate, "gradle.properties").isFile) {
            candidate = candidate.parentFile ?: error("could not locate project root")
        }
        return candidate
    }
}
