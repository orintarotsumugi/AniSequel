package com.anisequel

import com.anisequel.ui.components.novamarkdown.NovaSvgAsset
import com.anisequel.ui.components.novamarkdown.novaSvgCanBeCaptured
import com.anisequel.ui.components.novamarkdown.novaSvgIsAnimated
import com.anisequel.ui.components.novamarkdown.sanitizeNovaSvg
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * An animated card must not be replaced by one of its frames.
 *
 * ## What this caught
 *
 * A dynamic card - AniList stats, signature strips, "currently watching" banners -
 * is drawn in a Chromium `WebView` and then captured to a bitmap so a bio can
 * scroll (see [com.anisequel.ui.components.novamarkdown.NovaDynamicSvg]). The
 * capture runs a fraction of a second after the page loads, and a great many of
 * these cards animate themselves in: bars growing from zero, panels fading up,
 * text that scrolls. The captured frame was therefore whatever the animation was
 * doing at that instant - routinely an empty or half-drawn card, which the reader
 * sees as a broken image, permanently, on a card that would have been fine.
 *
 * ## What is asserted
 *
 * That the two ways an SVG can animate without scripting are both recognised, that
 * an ordinary card is not mistaken for one, and - the actual rule - that a
 * recognised animation is only captured when the device has stopped animating
 * things anyway. Nothing here inspects a `WebView`: the decision is a pure function
 * of the document and one system setting, which is why it is testable at all.
 */
class NovaSvgAnimationTest {

    private fun animated(svg: String): Boolean = novaSvgIsAnimated(svg)

    private fun asset(animated: Boolean) =
        NovaSvgAsset(markup = "<svg/>", aspectRatio = 2f, animated = animated)

    @Test
    fun `smil animation elements mark a card as animated`() {
        listOf(
            """<animate attributeName="width" from="0" to="120" dur="1s"/>""",
            """<animateTransform attributeName="transform" type="rotate" dur="4s"/>""",
            """<animateMotion dur="3s" path="M0,0 L10,10"/>""",
            """<animateColor attributeName="fill" from="#fff" to="#000" dur="2s"/>""",
            // A single change rather than a loop: it fires on its own `begin`, so a
            // frame taken before that time is the state the card is not in yet.
            """<set attributeName="opacity" to="1" begin="0.2s"/>"""
        ).forEach { tag ->
            assertTrue(
                "a card that animates through SMIL was treated as a still: $tag",
                animated("""<svg xmlns="http://www.w3.org/2000/svg">$tag</svg>""")
            )
        }
    }

    @Test
    fun `css animation marks a card as animated`() {
        listOf(
            "@keyframes grow { from { width: 0 } to { width: 120px } }",
            "@-webkit-keyframes grow { from { width: 0 } }",
            ".bar { animation: grow 1.2s ease-out forwards; }",
            ".bar { animation-name: grow; animation-duration: 1s; }",
            // Older exporters still emit the prefixed form, and Chromium still
            // honours it, so missing it would leave those cards frozen.
            ".bar { -webkit-animation: grow 1.2s linear; }"
        ).forEach { css ->
            assertTrue(
                "a card that animates through CSS was treated as a still: $css",
                animated("""<svg xmlns="http://www.w3.org/2000/svg"><style>$css</style></svg>""")
            )
        }
    }

    @Test
    fun `an animation declared on the element counts too`() {
        // The other place a CSS declaration can live; cards that animate one shape
        // usually inline it rather than carrying a stylesheet.
        assertTrue(
            animated(
                """<svg><rect style="animation: pulse 2s infinite" width="10" height="10"/></svg>"""
            )
        )
    }

    @Test
    fun `an ordinary card is not animated`() {
        listOf(
            // The shape most stat cards actually have: a flat document plus an
            // embedded font and a nested raster image.
            """<svg viewBox="0 0 1200 630"><style>@font-face{font-family:x;src:url(data:font/woff2;base64,AA)}</style>""" +
                """<defs><linearGradient id="g"><stop offset="0" stop-color="#111"/></linearGradient></defs>""" +
                """<rect width="1200" height="630" fill="url(#g)"/><text x="40" y="80">Watching</text>""" +
                """<image href="data:image/png;base64,AA" width="10" height="10"/></svg>""",
            // CSS transitions only run on hover/focus, which a disabled WebView in a
            // scrolling list never receives - they must not buy a live renderer.
            """<svg><style>rect{transition:fill .3s}</style><rect width="10" height="10"/></svg>""",
            // Words that merely look like the markers, in the places a declaration
            // cannot be: a card whose caption mentions animation is still one picture.
            """<svg><text>animation: the art of moving things</text></svg>""",
            """<svg><desc>@keyframes are not used in this card</desc></svg>""",
            """<svg><g data-note="no-keyframes-here" class="animations"/></svg>""",
            """<svg><section><rect width="4" height="4"/></section></svg>"""
        ).forEach { svg ->
            assertFalse(
                "a static card was treated as animated, which puts a renderer back " +
                    "in the scrolling list for a picture that cannot move: $svg",
                animated(svg)
            )
        }
    }

    @Test
    fun `sanitising keeps the animation and drops the script`() {
        val sanitized = sanitizeNovaSvg(
            """
            <svg xmlns="http://www.w3.org/2000/svg" onload="steal()">
              <script>document.querySelector('rect').style.width='9px'</script>
              <rect width="10" height="10"><animate attributeName="width" to="40" dur="2s"/></rect>
            </svg>
            """.trimIndent()
        )
        assertNotNull("the document must survive sanitising", sanitized)
        val markup = sanitized!!
        // The detector reads the sanitised document, so a strip list that grew to
        // include the SMIL elements would silently freeze every animated card.
        assertTrue("sanitising removed the SMIL animation", animated(markup))
        assertFalse("sanitising left a <script> in place", markup.contains("<script", true))
        assertFalse("sanitising left an event handler in place", markup.contains("onload", true))
    }

    @Test
    fun `an animated card is only captured when nothing is animating`() {
        // The bug: captured while the device animates, which freezes the card on a
        // frame of its own intro.
        assertFalse(
            "an animated card must keep a real renderer",
            novaSvgCanBeCaptured(asset(animated = true), animatorsEnabled = true)
        )
        // With animations switched off system-wide nothing moves, so a still is the
        // whole picture and the cheap path is the correct one.
        assertTrue(
            "an animated card may be captured once the reader has switched motion off",
            novaSvgCanBeCaptured(asset(animated = true), animatorsEnabled = false)
        )
        assertTrue(novaSvgCanBeCaptured(asset(animated = false), animatorsEnabled = true))
        assertTrue(novaSvgCanBeCaptured(asset(animated = false), animatorsEnabled = false))
    }
}
