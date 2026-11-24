package com.anisequel.ui.components.detail

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogWindowProvider
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * The title page's open and close choreography.
 *
 * ## The scrim is part of the animation
 *
 * A dialog's dim is drawn by the window manager: it is at full strength on the
 * frame the window is added and gone on the frame it is removed. The page itself
 * springs in over a few hundred milliseconds, so the list used to cut to black
 * before the page had drawn and stay black after it had left - the entrance read
 * as a page fading in over a still photograph of the list, and the exit as the
 * list being switched back on. `FullScreenCoverViewer` had already hit this and
 * zeroed the window's dim; [SheetScrim] is the same fix, with the 60% black the
 * platform drew moved inside the page's own progress so both halves fade
 * together.
 */
private const val SCRIM_ALPHA = 0.6f

/**
 * One progress value for the page's alpha, scale and offset, in both directions.
 *
 * Deliberately not keyed on the subject. Following a relation swaps what the
 * page shows while the page itself stays on screen; keyed by the title, the
 * state was recreated and the page fell back to alpha 0 for a frame before
 * replaying the whole entrance - the list flashed through the window and the
 * related title arrived twice.
 */
@Composable
internal fun rememberSheetEntrance(): Animatable<Float, AnimationVector1D> {
    val entrance = remember { Animatable(0f) }
    LaunchedEffect(entrance) {
        entrance.animateTo(1f, ExpressiveMotion.SettleSpatial)
    }
    return entrance
}

/**
 * The page's way out: request it, and the page settles before the window goes.
 *
 * Keyed on its own request rather than on the subject, so a tap on a relation
 * while the page is already fading cannot cancel the exit and leave the sheet at
 * whatever alpha the interruption caught. A second tap on Close is a no-op
 * instead of restarting the spring.
 *
 * ## The poster has to go back before the window does
 *
 * [travel] is the flying copy's progress, when there is one. It runs *with* the
 * page rather than after it: the copy returns to the list while the page fades,
 * which is one motion instead of a page leaving and then a poster sliding out of
 * an already empty screen. The window may only be removed once both have
 * settled, so this waits on the slower of the two - and [onTravelStart] is where
 * the page puts the copy back on screen, a frame before it starts moving, so the
 * copy is drawn at 1:1 over the page's own poster instead of appearing mid-flight.
 *
 * ## The page and the dim clear out from under the copy
 *
 * With a copy on its way home the cover is the transition, so the page exits on
 * [ExpressiveMotion.FastSpatial] instead of matching the flight: the dim has to
 * be gone by the time the poster is over the list, or the poster lands on a
 * screen that is still dark and then the list brightens after it has already
 * stopped - the dim becomes the thing the eye follows instead of the cover. The
 * copy keeps the slower spring and the window waits for it.
 */
@Composable
internal fun rememberSheetClose(
    entrance: Animatable<Float, AnimationVector1D>,
    onDismissed: () -> Unit,
    travel: Animatable<Float, AnimationVector1D>? = null,
    onTravelStart: () -> Unit = {}
): () -> Unit {
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        onTravelStart()
        val exit =
            if (travel != null) ExpressiveMotion.FastSpatial else ExpressiveMotion.SettleSpatial
        coroutineScope {
            launch { entrance.animateTo(0f, exit) }
            if (travel != null) {
                launch { travel.animateTo(0f, ExpressiveMotion.SettleSpatial) }
            }
        }
        onDismissed()
    }
    return remember { { closing = true } }
}

/**
 * The 60% black the platform used to draw, now owned by the page.
 *
 * Drawn under the page rather than on the window so it can read [entrance] in
 * the draw phase. The platform's copy is zeroed on the first composition;
 * leaving both would double the dim and bring the cut back.
 */
@Composable
internal fun SheetScrim(entrance: Animatable<Float, AnimationVector1D>) {
    val dialogView = LocalView.current
    LaunchedEffect(dialogView) {
        (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            // The layer has to wrap the ink, and that means it comes *first* in
            // the chain: a draw modifier before a `graphicsLayer` paints outside
            // it. Reversed - which this was - the black rectangle was drawn at
            // full strength and the alpha only ever reached an empty Box, so the
            // dim never followed the page at all: it sat at 60% for the whole
            // open and close and vanished when the window was removed, which is
            // the cut this scrim exists to remove, only moved to the other end.
            .graphicsLayer { alpha = SCRIM_ALPHA * entrance.value }
            .background(Color.Black)
    )
}

/**
 * The page's own transform, read in the draw phase so a full tree never recomposes.
 *
 * Same numbers the Surface applied inline before this moved out: a hair of scale
 * and a short rise, so the page settles in rather than appearing.
 *
 * [travels] is false while a poster is flying in. The flight lands on the page's
 * poster rectangle, which is measured in window coordinates; scaling and lifting
 * the page moves that rectangle while the copy is still travelling, so the copy
 * would arrive a few pixels off its own landing spot and the hand-off would
 * twitch. The page still fades, which is the part that covers the list appearing
 * behind it.
 */
internal fun Modifier.sheetEntrance(
    entrance: Animatable<Float, AnimationVector1D>,
    travels: Boolean = true
): Modifier = graphicsLayer {
    val progress = entrance.value
    alpha = progress
    if (travels) {
        val scale = 0.985f + (0.015f * progress)
        scaleX = scale
        scaleY = scale
        translationY = (1f - progress) * 22.dp.toPx()
    }
}
