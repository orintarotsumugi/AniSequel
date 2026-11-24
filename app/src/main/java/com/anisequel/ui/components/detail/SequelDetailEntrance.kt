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
 */
@Composable
internal fun rememberSheetClose(
    entrance: Animatable<Float, AnimationVector1D>,
    onDismissed: () -> Unit
): () -> Unit {
    var closing by remember { mutableStateOf(false) }
    LaunchedEffect(closing) {
        if (!closing) return@LaunchedEffect
        entrance.animateTo(0f, ExpressiveMotion.SettleSpatial)
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
            .background(Color.Black)
            .graphicsLayer { alpha = SCRIM_ALPHA * entrance.value }
    )
}

/**
 * The page's own transform, read in the draw phase so a full tree never recomposes.
 *
 * Same numbers the Surface applied inline before this moved out: a hair of scale
 * and a short rise, so the page settles in rather than appearing.
 */
internal fun Modifier.sheetEntrance(
    entrance: Animatable<Float, AnimationVector1D>
): Modifier = graphicsLayer {
    val progress = entrance.value
    alpha = progress
    val scale = 0.985f + (0.015f * progress)
    scaleX = scale
    scaleY = scale
    translationY = (1f - progress) * 22.dp.toPx()
}
