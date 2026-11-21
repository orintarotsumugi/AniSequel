package com.anisequel

import android.graphics.Color
import android.view.View
import android.view.View.MeasureSpec
import androidx.test.core.app.ApplicationProvider
import com.anisequel.ui.components.novamarkdown.captureNovaSvg
import com.anisequel.ui.components.novamarkdown.novaSvgBitmapHasInk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * What a card's WebView has to prove before it is allowed to become a bitmap.
 *
 * This is the one decision in the SVG path that can *lose* content: a capture that
 * came back blank and was shown anyway deletes the card the bio was written around,
 * and nothing on screen says which of the two it is. So the rule the renderer obeys
 * is asserted here rather than trusted - a painted view is captured at its slot
 * size, an unpainted one is refused (and keeps its WebView), and a transparent strip
 * inside an otherwise painted card does not make it look blank.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class NovaSvgCaptureTest {

    @Test
    fun `a painted card is captured at the size it was laid out at`() {
        val view = paintedView(Color.RED, width = 300, height = 150)

        val bitmap = captureNovaSvg(view)

        assertNotNull("a painted view must be captured", bitmap)
        assertEquals(300, bitmap!!.width)
        assertEquals(150, bitmap.height)
        assertTrue("the painted colour must have survived", novaSvgBitmapHasInk(bitmap))
    }

    @Test
    fun `a card that painted nothing is refused, not shown empty`() {
        // The failure this guards: Chromium draws into its own surface when the view
        // is hardware accelerated, so a capture can return a transparent bitmap.
        // Showing that bitmap replaces the card with nothing.
        val view = paintedView(Color.TRANSPARENT, width = 300, height = 150)

        assertNull("a blank capture must fall back to the WebView", captureNovaSvg(view))
    }

    @Test
    fun `a card smaller than its content still counts as painted`() {
        val view = View(ApplicationProvider.getApplicationContext()).apply {
            setBackgroundColor(Color.WHITE)
        }
        view.measure(
            MeasureSpec.makeMeasureSpec(40, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(12, MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, 40, 12)

        assertNotNull(captureNovaSvg(view))
    }

    @Test
    fun `a view with no size is not captured`() {
        assertNull(captureNovaSvg(View(ApplicationProvider.getApplicationContext())))
    }

    @Test
    fun `ink is read from alpha, and a mostly empty bitmap is still empty`() {
        val bitmap = android.graphics.Bitmap.createBitmap(64, 64, android.graphics.Bitmap.Config.ARGB_8888)
        assertFalse(novaSvgBitmapHasInk(bitmap))

        // Six samples in the top-left corner: the grid is coarser than that, so a
        // card this sparse is not evidence of a painting page.
        for (x in 0 until 6) bitmap.setPixel(x, 0, Color.WHITE)
        assertFalse("a handful of pixels is not a card", novaSvgBitmapHasInk(bitmap))

        for (x in 0 until 64) bitmap.setPixel(x, 32, Color.WHITE)
        assertTrue("a painted line across the card is", novaSvgBitmapHasInk(bitmap))
    }

    private fun paintedView(color: Int, width: Int, height: Int): View =
        View(ApplicationProvider.getApplicationContext()).apply {
            setBackgroundColor(color)
            measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(height, MeasureSpec.EXACTLY)
            )
            layout(0, 0, width, height)
        }
}
