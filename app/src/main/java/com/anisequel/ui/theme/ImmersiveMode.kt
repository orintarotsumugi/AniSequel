package com.anisequel.ui.theme

import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

/**
 * Full-screen presentation: both system bars hidden, revealed by an edge swipe.
 *
 * `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE` is what keeps this usable rather than
 * trapping the reader: the status and navigation bars come back over the content
 * for a moment on a swipe from the edge, and go away again on their own. The other
 * two behaviours would either leave a permanently empty strip or require a tap on
 * the screen to bring the bars back.
 *
 * An extension on `Window` rather than a composable so the two places that need it
 * - the theme setting and the Activity's focus callback - cannot disagree about
 * the flags. The system re-shows the bars on a transient swipe and on some
 * configuration changes; both callers apply the same function, so "hidden" always
 * means the same window state.
 */
fun Window.applyImmersiveMode(enabled: Boolean) {
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    if (enabled) {
        controller.hide(WindowInsetsCompat.Type.systemBars())
    } else {
        controller.show(WindowInsetsCompat.Type.systemBars())
    }
}
