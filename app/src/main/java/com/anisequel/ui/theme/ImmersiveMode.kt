package com.anisequel.ui.theme

import android.view.Window
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
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
 * An extension on `Window` rather than a composable so the places that need it -
 * the theme setting, the Activity's focus callback, and every dialog that draws
 * over the page - cannot disagree about the flags. The system re-shows the bars on
 * a transient swipe and on some configuration changes; every caller applies the
 * same function, so "hidden" always means the same window state.
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

/**
 * The reader's full-screen choice, readable from any composition and any dialog.
 *
 * A dialog is its own window, so the Activity hiding its bars says nothing about
 * the dialog's window - a cover viewer or sheet opened on a full-screen page came
 * back with the system bars visible, which made the app look like it had changed
 * presentation just because the reader opened something. Dialogs that own the
 * whole screen read this and apply the same flag to their own window; see
 * [DialogImmersiveEffect].
 */
val LocalImmersiveMode = staticCompositionLocalOf { false }

/**
 * Applies [LocalImmersiveMode] to a dialog's own window.
 *
 * Must be called inside the dialog's content, where `LocalView`'s parent is the
 * dialog window provider. This is deliberately not part of `Dialog` itself: most
 * dialogs here are sheets that should keep the reader's window state untouched,
 * and the ones that draw edge to edge opt in by calling this.
 *
 * Nothing is restored on dispose. The dialog is leaving the screen at that point,
 * and re-showing the bars on its way out would be a flash on the way to the page
 * that already hid them.
 */
@Composable
fun DialogImmersiveEffect(enabled: Boolean = LocalImmersiveMode.current) {
    val view = LocalView.current
    DisposableEffect(view, enabled) {
        (view.parent as? DialogWindowProvider)?.window?.applyImmersiveMode(enabled)
        onDispose { }
    }
}

/**
 * Pads a full-screen surface by the display cutout while the bars are hidden.
 *
 * Hiding the bars also zeroes `statusBarsPadding()`, which is what lets a page
 * own the whole display - but it also removes the only thing keeping content out
 * from under a camera cutout, and it leaves a header flush against the glass.
 * The cutout is unioned with a small comfort inset so the content always has
 * room, and the bars, when the reader swipes them back, draw over it as usual.
 *
 * Shared by the Activity's root and the full-screen cover viewer's dialog: the
 * two windows hide the same bars and need the same breathing room.
 */
fun Modifier.immersiveDisplayInsets(immersive: Boolean): Modifier =
    if (immersive) {
        windowInsetsPadding(
            WindowInsets.displayCutout.union(WindowInsets(top = ImmersiveTopBreathingRoom))
        )
    } else {
        this
    }

/**
 * The least a page's header may sit from the top edge in full screen.
 *
 * 24dp, not the first attempt's 14: a `TopAppBar` is laid out as if the status
 * bar existed, and with the bars hidden its icon row was visibly squeezed against
 * the glass. The value is the status-bar's own height on most devices, so a hidden
 * bar and a visible one put the header in the same place.
 */
private val ImmersiveTopBreathingRoom = 24.dp
