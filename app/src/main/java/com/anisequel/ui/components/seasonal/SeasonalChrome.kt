package com.anisequel.ui.components.seasonal

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.anisequel.domain.seasonal.SeasonalEffect
import com.anisequel.domain.seasonal.SeasonalPile

/**
 * The one measurement the sky needs from the chrome: the floating bar's actual
 * capsule, and the drift that is currently falling.
 *
 * ## Why this exists at all
 *
 * The bar is a sibling of the seasonal layer, composed after it, so the layer
 * cannot measure it and the bar cannot be asked for its height at composition
 * time. The two things that need to know about each other are the drift on the
 * floor and the bar floating just above it:
 *
 *  - The drift is drawn from the screen's bottom edge, *behind* the bar, so it
 *    looks like the snow gathered on the floor rather than on a shelf.
 *  - The bar gets its own cap of snow along its top edge, drawn by
 *    [SeasonalBarTrail] after the bar so it is not hidden underneath it.
 *
 * A bar height alone answered the first question for one release, and answered it
 * wrongly: it lifted the drift to the bar's outer top, which is a dozen dp above
 * the capsule because of the bar's own padding, so the snow floated in a gap.
 * The capsule's bounds are the only number that describes where the bar actually
 * is on screen.
 *
 * ## Why these are snapshot state, and why that is safe
 *
 * Each value is read inside a draw lambda that already repaints every frame. The
 * route invalidation that mattered in the old design - an `onSizeChanged` writing
 * a plain field so the app root did not recompose when the bar animated - still
 * holds: state read in the draw phase invalidates the draw, not the composition.
 * It is snapshot state rather than a volatile field because the trail has to be
 * invalidated when the sky is switched off or its shape changes; the layer is
 * disposed then and there is no longer a frame loop to notice a plain field had
 * moved.
 *
 * [seconds] is the sky's clock, published once per frame from the one app-wide
 * layer. The cap's drips breathe with it, so the snow on the bar belongs to the
 * same weather as the snow above it.
 */
object SeasonalChrome {

    /** The bar capsule's bounds in root coordinates, or null when there is no bar. */
    internal var barBounds: Rect? by mutableStateOf(null)
        private set

    /** The shape currently falling, or null while the overlay is off. */
    internal var skyEffect: SeasonalEffect? by mutableStateOf(null)
        private set

    /**
     * The drift the bar's cap takes its thickness from.
     *
     * Null when Settling is off, or for a shape that does not land. The cap still
     * draws a thin dusting in that case: a snow sky that leaves the bar perfectly
     * clean was the thing readers noticed.
     */
    internal var skyPile: SeasonalPile? by mutableStateOf(null)
        private set

    /** The sky's frame clock, for the cap's slow drip and sparkle. */
    internal var seconds: Float by mutableFloatStateOf(0f)
        private set

    /** Published from the bar's own layout; also marks it on screen. */
    internal fun publishBar(bounds: Rect) {
        barBounds = bounds
    }

    /** The bar left or is leaving. Its cap goes with it. */
    internal fun clearBar() {
        barBounds = null
    }

    /** Published by the app-wide layer when its shape or drift changes. */
    internal fun publishSky(effect: SeasonalEffect, pile: SeasonalPile?) {
        skyEffect = effect
        skyPile = pile
    }

    /** The app-wide layer is gone; no sky, no cap. */
    internal fun clearSky() {
        skyEffect = null
        skyPile = null
    }

    internal fun tick(seconds: Float) {
        this.seconds = seconds
    }
}
