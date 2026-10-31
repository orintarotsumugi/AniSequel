package com.anisequel.ui.components.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.anisequel.data.model.MissedSequel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveContainedLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.theme.AniSequelTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Interactive Material 3 Expressive quick-action button:
 *  - Tap: Adds anime to Planning list
 *  - Hold: Fills with progress; transitions to adding directly to Currently Watching
 *  - Swipe (if enabled): Horizontal slide gesture immediately confirms Currently Watching
 *
 * The dashboard's card draws this from a [MissedSequel]; every other title page
 * draws the same control from a media id. Both go through
 * [QuickAddPlanningButton] so the two cannot drift into different gestures.
 */
@Composable
fun HoldAndSwipePlanningButton(
    sequel: MissedSequel,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    QuickAddPlanningButton(
        mediaId = sequel.sequelId,
        isAddedToPlanning = sequel.isAddedToPlanning,
        isAddedToWatching = sequel.isAddedToWatching,
        isBusy = sequel.isSavingToList,
        onAddToPlanning = onAddToPlanning,
        onAddToWatching = onAddToWatching,
        modifier = modifier,
        holdDurationSeconds = holdDurationSeconds,
        swipeEnabled = swipeEnabled
    )
}

/**
 * The quick-add control itself, addressed by media id.
 *
 * Takes an id and its flags instead of a [MissedSequel] because a title page is not
 * always showing a sequel: a Library row, an activity entry and a studio credit
 * are all titles somebody may want to plan, and none of them has a parent to
 * hang the dashboard's flags off. The flags live in
 * [com.anisequel.ui.viewmodel.QuickAddStatus] for those hosts.
 *
 * `isAddedToWatching` is checked before `isAddedToPlanning` so a title that has
 * been moved on from Planning shows the state it is in now rather than the one
 * it used to be in.
 */
@Composable
fun QuickAddPlanningButton(
    mediaId: Int,
    isAddedToPlanning: Boolean,
    isAddedToWatching: Boolean,
    isBusy: Boolean,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit,
    modifier: Modifier = Modifier,
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    val statusColors = AniSequelTheme.statusColors
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Hold progress state (0f to 1f). `holdJob` and the "a hold already
    // committed" flag live inside the gesture modifier below: both are per-press
    // facts that no layout reads, and keeping them in composition only made the
    // button recompose for state nothing drew.
    var isHolding by remember { mutableStateOf(false) }
    val holdProgressAnim = remember { Animatable(0f) }

    // Swipe drag state
    val dragOffset = remember { Animatable(0f) }
    val swipeThresholdPx = with(density) { 90.dp.toPx() }

    // Handle already completed states
    if (isAddedToWatching) {
        WatchingCompletedBadge(
            mediaId = mediaId,
            modifier = modifier
        )
        return
    }

    if (isAddedToPlanning) {
        PlanningCompletedBadge(
            mediaId = mediaId,
            modifier = modifier
        )
        return
    }

    val holdDurationMs = (if (holdDurationSeconds == 5) 5000L else 3000L)

    val progressFraction = holdProgressAnim.value

    // The hold's colour, computed from the hold's own fraction.
    //
    // This used to be an `animateColorAsState` whose *target* was this lerp. The
    // fraction is itself animated (that is what the hold is), so the colour spring was
    // re-aimed at a new target every frame of a three-second gesture and could never
    // settle: the fill lagged its own progress, and the whole button recomposed per
    // frame to keep feeding it. AGENTS.md §3 names this exact shape - animate toward a
    // settled value - and the old `> 0.05f` branch made it worse by jumping the target
    // from primary to the lerp's value one frame in.
    val buttonColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.primary,
        statusColors.info,
        progressFraction
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isHolding) 0.97f else 1f,
        // The In/Out pair every other control uses (`ExpressiveControls.kt`), rather
        // than the symmetric spring: press-in and release as different gestures is the
        // house feel, and a main-screen button that presses differently from a
        // Settings segment reads as a different kind of control.
        animationSpec = if (isHolding) {
            ExpressiveMotion.PressSpatialIn
        } else {
            ExpressiveMotion.PressSpatialOut
        },
        label = "btn_press_scale"
    )

    // The hold is a shape as well as a colour.
    //
    // At rest this is the theme's small rounded rectangle; as the hold fills it
    // rounds into a capsule, and it springs back out on release. The fill said how
    // far the hold had got, but the control kept the same outline throughout, so
    // the one gesture that means something different from a tap (hold = Watching,
    // tap = Planning) looked identical to it until the colour had already changed.
    // `PillCorner` is more than half the 44dp height below, so `RoundedCornerShape`
    // clamps it to a true capsule rather than an ellipse.
    val shape = RoundedCornerShape(
        lerp(ExpressiveShapes.SmallCorner, ExpressiveShapes.PillCorner, progressFraction)
    )

    val canSwipe = swipeEnabled && !isBusy

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .graphicsLayer {
                scaleX = buttonScale
                scaleY = buttonScale
            }
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .testTag("hold_swipe_button_$mediaId")
    ) {
        if (canSwipe) {
            SwipeToWatchReveal(
                revealColor = statusColors.info,
                containerColor = statusColors.infoContainer
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .clip(shape)
                .background(buttonColor)
                .quickAddGestures(
                    holdEnabled = !isBusy,
                    swipeEnabled = canSwipe,
                    holdDurationMs = holdDurationMs,
                    swipeThresholdPx = swipeThresholdPx,
                    holdProgress = holdProgressAnim,
                    dragOffset = dragOffset,
                    haptic = haptic,
                    coroutineScope = coroutineScope,
                    onHoldingChange = { isHolding = it },
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching
                ),
            contentAlignment = Alignment.Center
        ) {
            HoldProgressFill(progressFraction = progressFraction)
            QuickAddButtonFace(isBusy = isBusy, isHolding = isHolding)
        }
    }
}

/**
 * Tap to plan, hold to watch, swipe sideways for watching too.
 *
 * Split out of [QuickAddPlanningButton] because the gesture branches were most
 * of that composable's complexity and none of its layout. `holdJob` and the
 * "a hold already committed" flag live here, in the pointer-input coroutine,
 * rather than in composition: no frame reads either one. The flag is not
 * cosmetic - the hold handler snaps the fill back to 0f when it completes, so
 * without it the release after a completed hold looks like a quick tap and the
 * same title gets written to Planning immediately after Watching.
 */
private fun Modifier.quickAddGestures(
    holdEnabled: Boolean,
    swipeEnabled: Boolean,
    holdDurationMs: Long,
    swipeThresholdPx: Float,
    holdProgress: Animatable<Float, AnimationVector1D>,
    dragOffset: Animatable<Float, AnimationVector1D>,
    haptic: HapticFeedback,
    coroutineScope: CoroutineScope,
    onHoldingChange: (Boolean) -> Unit,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit
): Modifier {
    if (!holdEnabled) return this

    val tapAndHold = pointerInput(holdDurationMs) {
        var holdJob: Job? = null
        var holdCommitted = false

        detectTapGestures(
            onPress = {
                onHoldingChange(true)
                holdCommitted = false
                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                holdJob?.cancel()
                holdJob = coroutineScope.launch {
                    holdProgress.snapTo(0f)
                    holdProgress.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(
                            durationMillis = holdDurationMs.toInt(),
                            easing = LinearEasing
                        )
                    )
                    // Hold reached 100% -> Trigger watching!
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onHoldingChange(false)
                    holdCommitted = true
                    onAddToWatching()
                    holdProgress.snapTo(0f)
                }

                val released = tryAwaitRelease()
                onHoldingChange(false)

                if (released) {
                    // User released before hold finished
                    val elapsedFraction = holdProgress.value
                    holdJob?.cancel()
                    coroutineScope.launch {
                        holdProgress.animateTo(0f, ExpressiveMotion.FastEffects)
                    }

                    // Quick tap -> Add to Planning. Only when the hold did not
                    // already commit: see `holdCommitted` above.
                    if (elapsedFraction < 0.25f && !holdCommitted) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onAddToPlanning()
                    }
                } else {
                    holdJob?.cancel()
                    coroutineScope.launch { holdProgress.snapTo(0f) }
                }
            }
        )
    }

    val swipe = if (swipeEnabled) {
        Modifier.pointerInput(Unit) {
            detectDragGestures(
                onDragEnd = {
                    coroutineScope.launch {
                        if (dragOffset.value >= swipeThresholdPx) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onAddToWatching()
                        }
                        dragOffset.animateTo(0f, ExpressiveMotion.PressSpatial)
                    }
                },
                onDragCancel = {
                    coroutineScope.launch {
                        dragOffset.animateTo(0f, ExpressiveMotion.PressSpatial)
                    }
                },
                onDrag = { change, dragAmount ->
                    change.consume()
                    val newOffset = (dragOffset.value + dragAmount.x)
                        .coerceIn(0f, swipeThresholdPx * 1.5f)
                    coroutineScope.launch { dragOffset.snapTo(newOffset) }
                }
            )
        }
    } else {
        Modifier
    }

    return this.then(tapAndHold).then(swipe)
}

/**
 * The "Swipe to Watch" hint uncovered as the button slides right.
 *
 * Takes its two colours rather than reading the theme itself so the caller owns
 * the only composition-local read.
 */
@Composable
private fun SwipeToWatchReveal(revealColor: Color, containerColor: Color) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(containerColor)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppVectorIcons.Play,
                contentDescription = "Swipe to Watch",
                tint = revealColor,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Swipe to Watch",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = revealColor
            )
        }
    }
}

/** The white fill that grows across the button while a hold is in progress. */
@Composable
private fun BoxScope.HoldProgressFill(progressFraction: Float) {
    if (progressFraction <= 0.01f) return
    Box(
        modifier = Modifier
            .fillMaxSize()
            .align(Alignment.CenterStart)
            .graphicsLayer {
                // A scale from the left edge, not `fillMaxWidth(fraction)`: the width
                // form re-runs layout on every frame of the hold, and §3 asks for
                // transforms in the draw phase. Same bar, no measuring.
                transformOrigin = TransformOrigin(0f, 0.5f)
                scaleX = progressFraction
            }
            .background(Color.White.copy(alpha = 0.28f))
    )
}

/**
 * What the button says: a spinner while saving, otherwise the icon and label
 * that swaps from "Add to Planning" to "Hold for Watching..." as the hold starts.
 */
@Composable
private fun QuickAddButtonFace(isBusy: Boolean, isHolding: Boolean) {
    if (isBusy) {
        ExpressiveContainedLoadingIndicator(
            modifier = Modifier.size(20.dp),
            containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
            indicatorColor = MaterialTheme.colorScheme.onPrimary
        )
        return
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(horizontal = 12.dp)
    ) {
        val icon = if (isHolding) AppVectorIcons.Play else AppVectorIcons.BookmarkAdd
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))

        AnimatedContent(
            targetState = isHolding,
            transitionSpec = {
                fadeIn(ExpressiveMotion.FastEffects) togetherWith fadeOut(ExpressiveMotion.FastEffects)
            },
            label = "btn_label_anim"
        ) { holding ->
            Text(
                text = if (holding) "Hold for Watching..." else "Add to Planning",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun WatchingCompletedBadge(
    mediaId: Int,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clearAndSetSemantics { contentDescription = "Currently Watching" }
            .testTag("watching_button_$mediaId"),
        shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContentColor = statusColors.info,
            disabledContainerColor = statusColors.infoContainer
        )
    ) {
        Icon(
            imageVector = AppVectorIcons.Play,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Currently Watching",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun PlanningCompletedBadge(
    mediaId: Int,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors
    OutlinedButton(
        onClick = {},
        enabled = false,
        modifier = modifier
            .fillMaxWidth()
            .height(44.dp)
            .clearAndSetSemantics { contentDescription = "On Planning list" }
            .testTag("planned_button_$mediaId"),
        shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
        colors = ButtonDefaults.outlinedButtonColors(
            disabledContentColor = statusColors.success,
            disabledContainerColor = statusColors.successContainer
        )
    ) {
        Icon(
            imageVector = AppVectorIcons.BookmarkDone,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "On Planning List",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
