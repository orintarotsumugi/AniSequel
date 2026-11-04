package com.anisequel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * A bio's animated picture still animates, and an SVG banner still draws.
 *
 * ## Why this needs pinning and nothing else does
 *
 * AniList bios are built out of animated files, and the platform decoder is not a
 * general image decoder: it reads PNG, JPEG, GIF, WebP and HEIF and *nothing else*.
 * So an animated WebP in a bio, or a banner an author's own server draws as SVG, is
 * not "slightly lower quality" without these two registrations - it is the file's
 * first frame with no hint that anything is missing, which reads as a broken image
 * rather than a still one. The user-visible promise is "the pictures in a profile
 * move when the site's own profile page shows them moving", and it lives entirely in
 * two lines of one builder.
 *
 * ## Why the source and not the built loader
 *
 * Coil 2 gives no way back from an `ImageLoader` to its registered component
 * factories, and `createImageLoader` needs a `Context` to build at all, so a JVM test
 * cannot ask the running loader what it can decode. Reading the builder is the same
 * instrument [ImageUserAgentTest] and [MinimumSdkReachabilityTest] use for a line
 * whose absence no runtime reports.
 *
 * ## What this is not
 *
 * Not a claim about which file types *decode well*. AndroidSVG is a partial
 * implementation of a large specification, and `ImageDecoder`'s treatment of very
 * large APNG frames is a device matter - both are stated in the builder's own
 * comments. This pins only that the decoders are *reached for*, because that is the
 * half that can be deleted by accident.
 */
class BioAnimationTest {

    private val loaderSource: String by lazy {
        var dir = File(".").absoluteFile
        while (!File(dir, "settings.gradle.kts").exists()) {
            dir = dir.parentFile ?: error("no settings.gradle.kts above the working directory")
        }
        File(dir, "app/src/main/java/com/anisequel/data/network/NetworkClient.kt")
            .readText()
            .substringAfter("fun createImageLoader")
    }

    /** The `.components { }` block, up to the loader's own `.build()`. */
    private val componentBlock: String
        get() = loaderSource.substringAfter(".components {").substringBefore(".build()")

    /**
     * [componentBlock] with its KDoc stripped.
     *
     * Required rather than tidy: the block's own comments record the guard that used to
     * be there (`if (SDK_INT >= P)`, with `GifDecoder` beneath it as the pre-28
     * fallback), so a plain substring test on the raw text fails on the explanation of
     * the fix. Only the code is the contract.
     */
    private val componentCode: String
        get() = componentBlock.lines()
            .filterNot { line ->
                val t = line.trimStart()
                t.startsWith("//") || t.startsWith("*") || t.startsWith("/*")
            }
            .joinToString("\n")

    @Test
    fun `the animated decoder is registered for every format the platform decoder cannot read`() {
        assertTrue(
            "without ImageDecoderDecoder an animated WebP or APNG in a bio draws its " +
                "first frame forever and looks like a still image, not a broken one",
            componentCode.contains("add(ImageDecoderDecoder.Factory())")
        )
        assertTrue(
            "without SvgDecoder a server-drawn SVG banner shows an error placeholder " +
                "over a picture AniList itself renders",
            componentCode.contains("add(SvgDecoder.Factory())")
        )
    }

    @Test
    fun `the decoders are registered unconditionally because minSdk leaves no branch to take`() {
        // minSdk is 31, so the old `SDK_INT >= P` condition is true on every device this
        // app installs on and the `GifDecoder` fallback beneath it could never run -
        // AGENTS.md §2's "a raise is a deletion". A guard put back here would not fail a
        // build; it would decide, on a device that cannot exist, that bios do not
        // animate. [MinimumSdkReachabilityTest] reads comparisons in the same file and
        // cannot see this one because the block it scans would be re-added as a *pair*
        // of lines with a condition the size test has no way to value.
        assertFalse(
            "a version branch inside the component block describes a device this app " +
                "cannot be installed on:\n$componentCode",
            componentCode.contains("SDK_INT") || componentCode.contains("Build.VERSION")
        )
    }
}
