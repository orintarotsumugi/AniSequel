package com.anisequel

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a markdown picture waits, in a spoiler or anywhere else.
 *
 * The failure: a block image with no declared height - which is what a spoiler
 * usually hides - reserved its space from its *placeholder*, and the placeholder
 * was a 28dp spinner. So revealing a spoiler showed a dot, then a full-width
 * picture, and the article under it jumped by the picture's height the instant
 * the bytes landed. A card that took a second to load looked like a page that had
 * not drawn.
 *
 * The fix has two halves that have to hold together, which is why they are
 * asserted together: the block's placeholder is the app's own skeleton at a
 * landscape shape rather than a dot, and the box settles from that shape to the
 * artwork's on the layout spring instead of snapping. An inline picture keeps the
 * small spinner - a shimmering plate behind a 50dp icon that is about to become
 * a transparent logo would draw a box the picture will never have.
 *
 * Source-level because there is no Compose test runtime in this source set; the
 * same approach as [NovaArtworkLoadingTest] and [ShimmerGeometryTest].
 */
class MarkdownImagePlaceholderTest {

    private val media = source("ui/components/novamarkdown/NovaMarkdownMedia.kt")
    private val svg = source("ui/components/novamarkdown/NovaDynamicSvg.kt")

    @Test
    fun `a block image waits on a picture-shaped plate`() {
        assertTrue(
            "the loading slot must know whether the picture fills its block",
            media.contains("NovaImagePending(fillsBox = fillsBox)")
        )
        assertTrue(
            "a block image's placeholder must be the shared skeleton, not a dot",
            media.contains("ShimmerBox(")
        )
        assertTrue(
            "the plate must be landscape, or it would shrink the page back up as " +
                "the artwork loads",
            media.contains("aspectRatio(NOVA_IMAGE_PENDING_ASPECT)")
        )
    }

    @Test
    fun `an inline picture keeps its small spinner`() {
        val pending = media.substringAfter("private fun NovaImagePending(")
            .substringBefore("\nprivate const")

        assertTrue(
            "the inline case must still be the small indicator",
            pending.contains("CircularProgressIndicator(")
        )
        assertTrue(
            "and it must be the branch the block case does not take",
            pending.contains("if (!fillsBox)")
        )
    }

    @Test
    fun `the wait settles on the layout spring instead of snapping`() {
        assertTrue(
            "the picture's box must animate from the plate's height to the " +
                "artwork's, or a slow image moves everything under it in one frame",
            media.contains("animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)")
        )
    }

    /**
     * A live AniList card is the other thing a spoiler hides, and it waits in a
     * platform view rather than in Coil, so it used to be the one image in the app
     * that waited on a bare spinner while every neighbour shimmered.
     */
    @Test
    fun `a live card waits on the same plate`() {
        val loading = svg.substringAfter("NovaSvgState.Loading ->")

        assertTrue(
            "the card's waiting state must be the shared skeleton",
            loading.contains("ShimmerBox(")
        )
        assertFalse(
            "the bare spinner must not come back",
            loading.substringBefore("is NovaSvgState.Ready").contains("CircularProgressIndicator(")
        )
    }

    private fun source(path: String): String = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/$path"
    ).readText()
        .replace(Regex("""/\*[\s\S]*?\*/"""), "")
        .replace(Regex("""//[^\n]*"""), "")

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
