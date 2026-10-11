package com.example.ui.components.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveContainedLoadingIndicator
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.theme.AniSequelTheme
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Interactive Material 3 Expressive quick-action button:
 *  - Tap: Adds anime to Planning list
 *  - Hold: Fills with progress; transitions to adding directly to Currently Watching
 *  - Swipe (if enabled): Horizontal slide gesture immediately confirms Currently Watching
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
    val statusColors = AniSequelTheme.statusColors
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // Hold progress state (0f to 1f)
    var isHolding by remember { mutableStateOf(false) }
    val holdProgressAnim = remember { Animatable(0f) }
    var holdJob by remember { mutableStateOf<Job?>(null) }
    // True from the moment a hold reaches 100% until the finger lifts. The
    // release branch decided "quick tap -> Add to Planning" from the elapsed
    // fraction, and the hold handler snaps the animator back to 0f the instant
    // it completes - so releasing after a completed hold read as a quick tap
    // and wrote the same title to Planning right after Writing it to Watching.
    var holdCommitted by remember { mutableStateOf(false) }

    // Swipe drag state
    val dragOffset = remember { Animatable(0f) }
    val swipeThresholdPx = with(density) { 90.dp.toPx() }

    // Handle already completed states
    if (sequel.isAddedToWatching) {
        WatchingCompletedBadge(
            sequelId = sequel.sequelId,
            modifier = modifier
        )
        return
    }

    if (sequel.isAddedToPlanning) {
        PlanningCompletedBadge(
            sequelId = sequel.sequelId,
            modifier = modifier
        )
        return
    }

    val isBusy = sequel.isAddingToPlanning || sequel.isAddingToWatching
    val holdDurationMs = (if (holdDurationSeconds == 5) 5000L else 3000L)

    // Animated container background transitioning from primary to info/watching color
    val progressFraction = holdProgressAnim.value
    val targetContainerColor = when {
        progressFraction > 0.05f -> androidx.compose.ui.graphics.lerp(
            MaterialTheme.colorScheme.primary,
            statusColors.info,
            progressFraction
        )
        else -> MaterialTheme.colorScheme.primary
    }

    val buttonColor by animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "btn_bg_color"
    )

    val buttonScale by animateFloatAsState(
        targetValue = if (isHolding) 0.97f else 1f,
        animationSpec = ExpressiveMotion.PressSpatial,
        label = "btn_press_scale"
    )

    val shape = RoundedCornerShape(12.dp)

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
            .testTag("hold_swipe_button_${sequel.sequelId}")
    ) {
        // Background reveal for horizontal swipe to watch
        if (swipeEnabled && !isBusy) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(statusColors.infoContainer)
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppVectorIcons.Play,
                        contentDescription = "Swipe to Watch",
                        tint = statusColors.info,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Swipe to Watch",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = statusColors.info
                    )
                }
            }
        }

        // Main button surface that slides on swipe and fills on hold
        Box(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(dragOffset.value.roundToInt(), 0) }
                .clip(shape)
                .background(buttonColor)
                .pointerInput(isBusy, swipeEnabled, holdDurationMs) {
                    if (isBusy) return@pointerInput

                    // Gestures: Tap & Hold
                    detectTapGestures(
                        onPress = {
                            isHolding = true
                            holdCommitted = false
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)

                            holdJob?.cancel()
                            holdJob = coroutineScope.launch {
                                holdProgressAnim.snapTo(0f)
                                holdProgressAnim.animateTo(
                                    targetValue = 1f,
                                    animationSpec = tween(
                                        durationMillis = holdDurationMs.toInt(),
                                        easing = LinearEasing
                                    )
                                )
                                // Hold reached 100% -> Trigger watching!
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isHolding = false
                                holdCommitted = true
                                onAddToWatching()
                                holdProgressAnim.snapTo(0f)
                            }

                            val released = tryAwaitRelease()
                            isHolding = false

                            if (released) {
                                // User released before hold finished
                                val elapsedFraction = holdProgressAnim.value
                                holdJob?.cancel()
                                coroutineScope.launch {
                                    holdProgressAnim.animateTo(0f, ExpressiveMotion.FastEffects)
                                }

                                // Quick tap -> Add to Planning. Only when the hold
                                // did not already commit: see `holdCommitted`.
                                if (elapsedFraction < 0.25f && !holdCommitted) {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    onAddToPlanning()
                                }
                            } else {
                                holdJob?.cancel()
                                coroutineScope.launch { holdProgressAnim.snapTo(0f) }
                            }
                        }
                    )
                }
                .then(
                    if (swipeEnabled && !isBusy) {
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
                                    val newOffset = (dragOffset.value + dragAmount.x).coerceIn(0f, swipeThresholdPx * 1.5f)
                                    coroutineScope.launch { dragOffset.snapTo(newOffset) }
                                }
                            )
                        }
                    } else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            // Fill progress bar indicator while holding
            if (progressFraction > 0.01f) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progressFraction)
                        .align(Alignment.CenterStart)
                        .background(Color.White.copy(alpha = 0.28f))
                )
            }

            // Dynamic button content with icon & label
            if (isBusy) {
                ExpressiveContainedLoadingIndicator(
                    modifier = Modifier.size(20.dp),
                    containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                    indicatorColor = MaterialTheme.colorScheme.onPrimary
                )
            } else {
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
        }
    }
}

@Composable
private fun WatchingCompletedBadge(
    sequelId: Int,
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
            .testTag("watching_button_$sequelId"),
        shape = RoundedCornerShape(12.dp),
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
    sequelId: Int,
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
            .testTag("planned_button_$sequelId"),
        shape = RoundedCornerShape(12.dp),
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
