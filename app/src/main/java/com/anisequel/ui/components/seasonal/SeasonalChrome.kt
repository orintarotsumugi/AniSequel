package com.anisequel.ui.components.seasonal

/**
 * The one measurement the sky needs from the chrome: how much of the bottom edge
 * the floating bar is occupying.
 *
 * ## Why this exists at all
 *
 * The bar is a sibling of the seasonal layer, composed after it, so the layer
 * cannot measure it and the bar cannot be asked for its height at composition
 * time. Without the number, a drift drawn from the bottom of the layer sits
 * entirely *behind* the bar and the reader never sees the snow they asked to
 * accumulate.
 *
 * The bar publishes its measured height here from layout; the layer reads it in
 * its draw phase (where it already redraws every frame) and lifts the drift's
 * baseline to the bar's top edge. When the bar is hidden - Settings, a pushed
 * page - the height goes back to zero and the drift settles on the screen's own
 * bottom edge, which is exactly where the reader last saw it.
 *
 * This is deliberately a plain object with volatile fields rather than snapshot
 * state: a height change is a layout event, and the only reader is a draw lambda
 * that runs sixty times a second anyway. Publishing it as state would invalidate
 * the app's root every time the bar animated in or out.
 */
object SeasonalChrome {

    /** The bar's measured height in pixels, including its navigation-bar inset. */
    @Volatile
    internal var barHeightPx: Int = 0
        private set

    /** Published from the bar's layout; also marks it on screen. */
    internal fun publishBarHeight(heightPx: Int) {
        barHeightPx = heightPx.coerceAtLeast(0)
    }

    /** The bar left or is leaving; the drift returns to the screen's bottom edge. */
    internal fun clearBar() {
        barHeightPx = 0
    }
}
