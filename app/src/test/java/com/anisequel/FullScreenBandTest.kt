package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Full-screen mode paints the band the status bar used to occupy.
 *
 * ## What was broken
 *
 * `MainActivity` put the immersive insets on the root `Surface` itself:
 *
 * ```kotlin
 * Surface(Modifier.fillMaxSize().then(Modifier.immersiveDisplayInsets(immersive)))
 * ```
 *
 * `windowInsetsPadding` shrinks the thing it is applied to, so that did not just
 * move the content down - it moved the *painted area* down with it. With the bars
 * hidden the insets are otherwise zero and nothing else reaches the top of the
 * window, so the band above the page was the platform window background, which on
 * `Theme.DeviceDefault.NoActionBar` is a flat dark rectangle nobody drew. The
 * reader's words for it were "it's blank, it's nothing, the UI does not cover that
 * place", and they were describing the actual layer: the only surface in the app
 * that was not the app.
 *
 * ## What it does now
 *
 * The `Surface` is full-bleed and the insets move one level in, onto the `Box`
 * that holds the pages. This is the ordering `LoginScreen` already used and
 * commented: paint first, consume the insets second.
 *
 * ## Why this is a source-level test
 *
 * There is no Compose test harness in the unit-test source set and the symptom
 * lives in which modifier carries which inset - a property of the composition
 * tree, not of a value a JVM can call. Same approach as
 * `RelationDetailResolutionTest`.
 */
class FullScreenBandTest {

    private val mainActivity = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/MainActivity.kt"
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

    @Test
    fun `the root surface is full-bleed and the insets sit inside it`() {
        assertTrue(
            "could not read ${mainActivity.path}; this guard silently passing on a " +
                "missing file is the same failure it exists to prevent",
            mainActivity.isFile
        )
        val source = mainActivity.readText()

        // The Surface's own argument list: everything up to the brace that opens
        // its content lambda. Slicing on `AppLockGate(` instead would have swept up
        // the inner Box as well and asserted the opposite of what it means.
        val surfaceArgs = source
            .substringAfter("Surface(", missingDelimiterValue = "")
            .substringBefore(") {", missingDelimiterValue = "")
        assertTrue("could not find the root Surface", surfaceArgs.isNotBlank())

        assertFalse(
            "the immersive insets must not be on the Surface: `windowInsetsPadding` " +
                "shrinks what it is applied to, so the painted area stops short of " +
                "the top of the window and the band above the page is the platform " +
                "window background:\n$surfaceArgs",
            surfaceArgs.contains("immersiveDisplayInsets")
        )
        assertTrue(
            "the Surface still has to fill the window, or there is nothing to " +
                "paint the band with:\n$surfaceArgs",
            surfaceArgs.contains(".fillMaxSize()")
        )

        val insideSurface = source
            .substringAfter("Surface(", missingDelimiterValue = "")
            .substringBefore("AppLockGate(", missingDelimiterValue = "")
        assertTrue(
            "with the bars hidden nothing else keeps a header off the camera " +
                "cutout, so the container inside the Surface has to carry the inset",
            insideSurface.contains("immersiveDisplayInsets(themeSettings.immersive)")
        )
    }
}
