package com.anisequel.ui.screens.tier

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay

/**
 * The debounce between a settled pinch and the write that stores it.
 *
 * Long enough that dragging a pinch to its final size is one write, short enough
 * that a person who stops and looks has already had it kept.
 */
internal const val TIER_ZOOM_WRITE_DELAY_MS = 600L

/**
 * Keeps the board's live zoom and the stored zoom from writing over each other.
 *
 * Both directions exist at once: a pinch moves [zoom] every frame while the
 * store is written on a debounce, and the display sheet can change the stored
 * value while a write is still in flight. Each rule below records a way that
 * race went wrong:
 *
 *  - An external change reaches the board through [onLocalZoomChanged] only when
 *    the stored value *changes* while this composition is alive. A composition
 *    that starts with the two disagreeing is a screen restored inside the
 *    debounce window - `rememberSaveable` kept the newer local value and the
 *    flush below is about to store it - so the stored value must not win.
 *  - The screen's own echo is not an external change. [lastWrittenZoom] records
 *    what was sent, and the mirror skips that value: a pinch made while the
 *    previous write was in flight used to be snapped back by its own arrival.
 *  - The debounce is keyed on the stored value as well as the local one. When
 *    an echo does arrive while a newer local value is pending, writing that
 *    newer value again is exactly what has to happen, and a key on the local
 *    value alone would leave the effect parked because it never changed.
 *  - Leaving the board inside the debounce window flushes the pending value.
 *    The effect is cancelled with the composition, so without the flush a pinch
 *    followed quickly by Back was lost: the store still held the old zoom and
 *    the next visit started there.
 *
 * The flush is skipped when this composition no longer owns [documentId]. A
 * zoom that belonged to a list the screen has already left must not be written
 * into whichever list replaced it.
 */
@Composable
internal fun TierBoardZoomPersistence(
    documentId: String,
    zoom: Float,
    storedZoom: Float,
    onLocalZoomChanged: (Float) -> Unit,
    onZoomChange: (Float) -> Unit
) {
    var lastWrittenZoom by remember(documentId) { mutableFloatStateOf(Float.NaN) }

    // The stored zoom this composition has already seen. `remember` rather than
    // `rememberSaveable`: a recreated composition re-seeds it from the store,
    // which is exactly the "does not win until it moves" rule above.
    var observedStoredZoom by remember {
        mutableFloatStateOf(clampTierZoom(storedZoom))
    }

    LaunchedEffect(storedZoom) {
        val stored = clampTierZoom(storedZoom)
        if (stored == observedStoredZoom) return@LaunchedEffect
        observedStoredZoom = stored
        if (stored != lastWrittenZoom && stored != zoom) onLocalZoomChanged(stored)
    }

    LaunchedEffect(zoom, storedZoom) {
        if (zoom != clampTierZoom(storedZoom)) {
            delay(TIER_ZOOM_WRITE_DELAY_MS)
            lastWrittenZoom = zoom
            onZoomChange(zoom)
        }
    }

    // The flush reads the current values rather than the ones captured when the
    // effect was installed: a rotation disposes the composition after the last
    // pinch of the session may already have moved on.
    val latestZoom by rememberUpdatedState(zoom)
    val latestStoredZoom by rememberUpdatedState(storedZoom)
    val latestWriter by rememberUpdatedState(onZoomChange)
    val latestDocumentId by rememberUpdatedState(documentId)
    DisposableEffect(documentId) {
        val ownedId = documentId
        onDispose {
            if (latestDocumentId == ownedId &&
                latestZoom != clampTierZoom(latestStoredZoom)
            ) {
                latestWriter(latestZoom)
            }
        }
    }
}
