package com.anisequel.ui.components.cards

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.lerp
import com.anisequel.data.model.MissedSequel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveContainedLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.theme.LocalConfirmPlanningAdd
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
    swipeEnabled: Boolean = true,
    /**
     * Whether the planning add asks for a tick first.
     *
     * Read from the app's own switch by default - provided once at the root from
     * `QuickAddPreferences` - so every host (dashboard cards, the new Home's rows,
     * the detail sheet's action row) hears the same answer without each one being
     * handed the setting. A caller may still pass a value explicitly, which is what
     * a preview or a screenshot fixture wants.
     */
    confirmBeforeAdding: Boolean = LocalConfirmPlanningAdd.current
) {
    // The two terminal states are answered before the active control is
    // composed, so the gesture and confirmation state is never allocated for a
    // title that already has a badge. Splitting them out also keeps the active
    // control small enough to read: its branches are the gesture, not the
    // historical states of the list.
    if (isAddedToWatching) {
        WatchingCompletedBadge(mediaId = mediaId, modifier = modifier)
        return
    }
    if (isAddedToPlanning) {
        PlanningCompletedBadge(mediaId = mediaId, modifier = modifier)
        return
    }

    ActiveQuickAddPlanningButton(
        mediaId = mediaId,
        isBusy = isBusy,
        onAddToPlanning = onAddToPlanning,
        onAddToWatching = onAddToWatching,
        onConfirmBeforeAdding = confirmBeforeAdding,
        modifier = modifier,
        holdDurationSeconds = holdDurationSeconds,
        swipeEnabled = swipeEnabled
    )
}

/**
 * The button when the title is on neither list yet: the hold, the swipe and the
 * tick/cross question.
 */
@Composable
private fun ActiveQuickAddPlanningButton(
    mediaId: Int,
    isBusy: Boolean,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit,
    onConfirmBeforeAdding: Boolean,
    modifier: Modifier = Modifier,
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    val statusColors = AniSequelTheme.statusColors
    val haptic = LocalHapticFeedback.current
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current

    // The small confirmation, held by the button itself rather than by a screen:
    // it is one control's state, and hoisting it would make every card in a long
    // list carry a flag none of its neighbours read.
    var confirming by remember(mediaId) { mutableStateOf(false) }

    // The switch can be turned off while a confirmation is open in Settings' own
    // preview composition. Closing it is the honest answer: the setting now says
    // one tap adds, and leaving a tick on screen would be asking a question the
    // reader has switched off.
    LaunchedEffect(onConfirmBeforeAdding) {
        if (!onConfirmBeforeAdding) confirming = false
    }

    // Hold progress state (0f to 1f). `holdJob` and the "a hold already
    // committed" flag live inside the gesture modifier below: both are per-press
    // facts that no layout reads, and keeping them in composition only made the
    // button recompose for state nothing drew.
    //
    // Keyed on the title, every one of them. They used to be remembered without a
    // key, so a row that a `LazyColumn` reused for a different sequel could start
    // out half-filled (a cancelled hold) or part-dragged (a cancelled swipe) before
    // the reader had touched anything.
    var isHolding by remember(mediaId) { mutableStateOf(false) }
    val holdProgressAnim = remember(mediaId) { Animatable(0f) }

    // Swipe drag state
    val dragOffset = remember(mediaId) { Animatable(0f) }
    val swipeThresholdPx = with(density) { 90.dp.toPx() }

    val holdDurationMs = holdDurationMillis(holdDurationSeconds)

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

    val canSwipe = swipeEnabled && !isBusy && !confirming

    // The gesture block below only restarts for a new title, so anything it reads
    // has to be read through state or the block keeps the value it was first given.
    // The suspect is not hypothetical: this control is previewed live in Settings,
    // where the confirmation switch can be turned off under a composition that is
    // already running - and a `pointerInput` that captured `onConfirmBeforeAdding`
    // would go on asking a question the setting had just retired. The same hazard
    // is recorded on `expressiveHoldGesture` for recycled lazy rows.
    val currentOnAddToPlanning by rememberUpdatedState(onAddToPlanning)
    val currentOnAddToWatching by rememberUpdatedState(onAddToWatching)
    val currentConfirmBeforeAdding by rememberUpdatedState(onConfirmBeforeAdding)

    // A tap that will ask a question still needs the haptic the old tap had: the
    // finger has to feel that the button took the press, or the tick that appears
    // a frame later reads as the button having missed it.
    val askOrAdd: () -> Unit = {
        if (currentConfirmBeforeAdding) {
            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            confirming = true
        } else {
            currentOnAddToPlanning()
        }
    }

    // The question sits *beside* the control, not on top of it.
    //
    // Turning the button into its own question (`PlanningConfirmFace` replaced
    // the whole face) read as the button having been replaced rather than asked:
    // the label grew, the two answers were drawn on the same surface, and the
    // control the reader had reached for was momentarily gone. Here the control
    // stays - smaller, because it now has neighbours - and the two answers are
    // square buttons immediately to its right, in the order the question asks:
    // tick to plan, cross to put it back.
    Row(
        // No `animateContentSize` here: this Row is always the window's width, so
        // there was nothing for it to animate. The change of width is real, but it
        // belongs to the answers that take it - see the AnimatedVisibility below.
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
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
                    // The gestures are detached while the question is open. A tick
                    // is an answer, not another tap to plan - and the old gesture
                    // handler would read a press on the label as one more "add".
                    .then(
                        if (confirming) {
                            Modifier.clickable(
                                onClickLabel = "Cancel adding to Planning",
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    confirming = false
                                }
                            )
                        } else {
                            Modifier.quickAddGestures(
                                mediaId = mediaId,
                                holdEnabled = !isBusy,
                                swipeEnabled = canSwipe,
                                holdDurationMs = holdDurationMs,
                                swipeThresholdPx = swipeThresholdPx,
                                holdProgress = holdProgressAnim,
                                dragOffset = dragOffset,
                                haptic = haptic,
                                coroutineScope = coroutineScope,
                                onHoldingChange = { isHolding = it },
                                onAddToPlanning = askOrAdd,
                                onAddToWatching = { currentOnAddToWatching() }
                            )
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                HoldProgressFill(progressFraction = progressFraction)

                AnimatedContent(
                    targetState = confirming,
                    transitionSpec = {
                        fadeIn(ExpressiveMotion.FastEffects) togetherWith
                            fadeOut(ExpressiveMotion.FastEffects)
                    },
                    modifier = Modifier.fillMaxSize(),
                    label = "planning_confirm_state"
                ) { asking ->
                    if (asking) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Add to Planning?",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            QuickAddButtonFace(isBusy = isBusy, isHolding = isHolding)
                        }
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = confirming,
            // The width is the animation. The add button is this Row's weighted
            // child, so the only way it can get smaller smoothly is for the squares
            // beside it to take their width a pixel at a time. They used to arrive
            // at full width while the button snapped narrower on the same frame -
            // at the exact moment the reader was watching the control they had just
            // pressed - and `animateContentSize` on the Row could not help, because
            // the Row never changes width.
            enter = expandHorizontally(
                animationSpec = ExpressiveMotion.FastSpatialSize,
                expandFrom = Alignment.Start
            ) + fadeIn(ExpressiveMotion.FastEffects) +
                scaleIn(
                    initialScale = 0.86f,
                    animationSpec = ExpressiveMotion.BouncySpatial
                ),
            exit = shrinkHorizontally(
                animationSpec = ExpressiveMotion.FastSpatialSize,
                shrinkTowards = Alignment.Start
            ) + fadeOut(ExpressiveMotion.FastEffects) +
                scaleOut(
                    targetScale = 0.90f,
                    animationSpec = ExpressiveMotion.FastSpatial
                )
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PlanningConfirmSquare(
                    mediaId = mediaId,
                    confirm = true,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        confirming = false
                        onAddToPlanning()
                    }
                )
                PlanningConfirmSquare(
                    mediaId = mediaId,
                    confirm = false,
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        confirming = false
                    }
                )
            }
        }
    }
}

/**
 * One square answer beside the button: the tick that plans, the cross that does
 * not.
 *
 * A square rather than a circle, and the same 44dp height as the control it
 * stands beside: two round bubbles next to a rounded rectangle read as three
 * different kinds of thing, while a matched row of rounded squares reads as one
 * control that unfolded. Both answers keep a full-height touch target without
 * stealing the add button's whole width.
 */
@Composable
private fun PlanningConfirmSquare(
    mediaId: Int,
    confirm: Boolean,
    onClick: () -> Unit
) {
    val statusColors = AniSequelTheme.statusColors
    val container = if (confirm) {
        statusColors.successContainer
    } else {
        MaterialTheme.colorScheme.errorContainer
    }
    val content = if (confirm) statusColors.success else MaterialTheme.colorScheme.error

    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(RoundedCornerShape(ExpressiveShapes.SmallCorner))
            .background(container)
            .clickable(
                onClickLabel = if (confirm) {
                    "Confirm adding to Planning"
                } else {
                    "Cancel adding to Planning"
                },
                onClick = onClick
            )
            .semantics {
                contentDescription = if (confirm) "Confirm" else "Cancel"
            }
            .testTag(
                if (confirm) {
                    "planning_confirm_yes_$mediaId"
                } else {
                    "planning_confirm_no_$mediaId"
                }
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (confirm) AppVectorIcons.Done else AppVectorIcons.Close,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(20.dp)
        )
    }
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

/** The two supported hold lengths, as milliseconds. */
private fun holdDurationMillis(holdDurationSeconds: Int): Long =
    if (holdDurationSeconds == 5) 5000L else 3000L
