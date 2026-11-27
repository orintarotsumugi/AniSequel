package com.anisequel.ui.screens.profile

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * The ring that answers "which post was the alert about?".
 *
 * Tapping an activity alert opens the profile's Activity tab already scrolled to
 * the activity it names. On a feed of near-identical cards that is not enough: the
 * scroll lands the post under the reader's eye, but nothing there is different from
 * the row above it, so the arrival reads as "the app opened at some activity" rather
 * than as "this one". The glow is the difference - a ring that arrives at full
 * strength and settles out over a couple of seconds, drawn over the card rather than
 * replacing any of its own colour.
 *
 * It is deliberately not a `Modifier.blur`, which is the obvious way to make a glow
 * and the expensive one: a blurred layer on a long feed is a render pass per row, and
 * this app has already paid for that (TO-DO 5.1). Two stroked rounds of the card's
 * own outline - a wide soft one outside and a crisp one on the edge - read as light
 * without a mask, and they are drawn in the draw phase, so the fade costs no
 * recomposition and no layout.
 *
 * The value starts at rest for a row that is not the target, which is every row but
 * one, so the common path allocates an [Animatable] that never runs.
 */
@Composable
internal fun rememberNotificationGlow(highlighted: Boolean): Animatable<Float, AnimationVector1D> {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(highlighted) {
        if (!highlighted) {
            glow.snapTo(0f)
            return@LaunchedEffect
        }
        glow.snapTo(1f)
        // A settle, not a bounce: light fading out does not overshoot, and this
        // token is already the app's "arrives and stays" spring.
        glow.animateTo(0f, ExpressiveMotion.SettleSpatial)
    }
    return glow
}

/**
 * Draws [glow] over the card's own shape.
 *
 * [glow] is read inside the draw lambda rather than in composition for the reason
 * [rememberNotificationGlow] exists at all: a value read by composition would
 * recompose this card on every frame of a fade, and a feed can hold dozens of them.
 */
internal fun Modifier.notificationGlow(
    glow: Animatable<Float, AnimationVector1D>,
    shape: Shape,
    color: Color
): Modifier = drawWithContent {
    drawContent()
    val strength = glow.value
    if (strength <= 0.01f) return@drawWithContent

    // 4dp out, an 8dp stroke: the stroke is centred on the inflated outline, so
    // its inner edge sits on the card's own edge - no gap of background between
    // the card and its light - and half of it falls outside the card where the
    // press layer does not clip it.
    val spread = 4.dp.toPx()
    val halo = shape.createOutline(
        Size(size.width + spread * 2f, size.height + spread * 2f),
        layoutDirection,
        this
    )
    translate(left = -spread, top = -spread) {
        drawOutline(
            outline = halo,
            color = color,
            alpha = 0.30f * strength,
            style = Stroke(width = 8.dp.toPx())
        )
    }
    // The crisp edge stays on the card's own outline rather than the inflated
    // one: that is what makes the light read as attached to this row.
    drawOutline(
        outline = shape.createOutline(size, layoutDirection, this@drawWithContent),
        color = color,
        alpha = 0.85f * strength,
        style = Stroke(width = 1.5.dp.toPx())
    )
}
