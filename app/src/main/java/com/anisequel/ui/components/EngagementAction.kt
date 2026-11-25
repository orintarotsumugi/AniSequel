package com.anisequel.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme

/**
 * A like, a comment count, a reply count: an icon and a number, as one control.
 *
 * ## Why this lives in `components/` and not in the card that first needed it
 *
 * It was `private` to `ProfileActivityCard`, which is the right default and the wrong
 * outcome once a second caller appears: a comment on a thread is liked the same way an
 * activity is, with the same icon swap, the same 48dp target, the same press spring and
 * the same disabled state. Re-declaring it inside the comments sheet would be four
 * visual details free to drift apart from each other, and the drift is invisible until
 * two controls that are meant to look the same do not.
 *
 * The card's three uses are the reason it is shaped this way rather than being three
 * separate controls: they sit in one row, share a 48dp minimum each and a 190dp budget
 * on a 360dp phone, and a like is the one that is also long-pressable.
 *
 * ## The long press
 *
 * [onLongClick] is optional and only the activity's heart passes it - it opens the
 * list of people who liked it. The gesture runs through `combinedClickable` so the tap
 * path is unchanged, with `hapticFeedbackEnabled = false` and the haptic played by
 * hand: the modifier performs its own long-press haptic by default, and leaving both on
 * would buzz twice. The hand-played one is the same single
 * [HapticFeedbackType.LongPress] every other hold gesture in the app uses.
 *
 * ## The disabled state is load-bearing
 *
 * [enabled] is false whenever AniList has not said whether the thing is liked - a
 * signed-out reader gets `isLiked == null` rather than `false`. A heart that renders
 * and does nothing is the one state this control must never be in, so the caller
 * passes `isLiked != null` and not `true`.
 */
@Composable
fun EngagementAction(
    icon: ImageVector,
    count: Int,
    active: Boolean,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    /**
     * What tapping the *number* does, when it does something other than [onClick].
     *
     * The heart and its count are two questions on AniList's own site: the glyph
     * likes, the number opens the list of people who did. Here the whole control
     * was one target, so the likers list was reachable only by a long press that
     * nothing on screen announced - a reader who wanted to know who liked
     * something had no way to find out. Null keeps the control a single target,
     * which is right for the comment count and for a heart with no likes to show.
     */
    onCountClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    // Two settled values, not a pulse: the heart's colour and its count both jump
    // on the tap - the icon swaps in the same frame and the optimistic number is
    // already correct - so without this the control that confirms a like is the
    // one thing in the row that does not move. `FastColorEffects` and
    // `FastEffectsInt` are the house tokens for exactly this pair; the count uses
    // the effects spring so a like never ticks past the number and back.
    val appearance by animateColorAsState(
        // `onSurfaceVariant` at 0.5 is 2.2:1 on a light surface, and a like
        // button that is disabled by a network error is precisely the control that
        // still has to be readable. `disabled` is the scheme's 3:1 tone.
        targetValue = if (enabled) tint else AniSequelTheme.emphasis.disabled,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "engagement_tint"
    )
    val shownCount by animateIntAsState(
        targetValue = count,
        animationSpec = ExpressiveMotion.FastEffectsInt,
        label = "engagement_count"
    )
    // One short pop when a like lands, on the same spring family as every press in
    // the app. Only on the transition *to* active: a card that composes with the
    // heart already filled is not an event, and an unlike is not a celebration.
    // Gated on the reader's motion setting, like every other loop and spring here.
    val pop = remember { Animatable(1f) }
    var wasActive by remember { mutableStateOf(active) }
    LaunchedEffect(active) {
        val changed = wasActive != active
        wasActive = active
        if (!changed || !active || ExpressiveMotion.speed == MotionStyle.INSTANT) {
            return@LaunchedEffect
        }
        pop.snapTo(1f)
        pop.animateTo(1.28f, ExpressiveMotion.FastSpatial)
        pop.animateTo(1f, ExpressiveMotion.SuperBouncy)
    }
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .defaultMinSize(minWidth = 48.dp, minHeight = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .combinedClickable(
                enabled = enabled,
                interactionSource = interactionSource,
                onLongClick = onLongClick?.let { open ->
                    {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        open()
                    }
                },
                hapticFeedbackEnabled = false,
                onClick = onClick
            )
            .bouncyPress(pressedScale = 0.9f, interactionSource = interactionSource)
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = appearance,
            modifier = Modifier
                .size(16.dp)
                // Draw-phase, so the pop invalidates the layer instead of
                // recomposing the row on every frame of the spring.
                .graphicsLayer {
                    scaleX = pop.value
                    scaleY = pop.value
                }
        )
        Spacer(modifier = Modifier.width(5.dp))
        if (onCountClick != null) {
            Box(
                modifier = Modifier
                    // A secondary target inside the control's own 48dp box: wide
                    // enough to hit on purpose, without growing the row that the
                    // activity card's 190dp budget cannot afford to grow.
                    .defaultMinSize(minWidth = 24.dp)
                    .clip(MaterialTheme.shapes.small)
                    .clickable(
                        onClickLabel = "See who liked this",
                        onClick = onCountClick
                    )
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = shownCount.toString(),
                    style = MaterialTheme.typography.labelMedium,
                    color = appearance
                )
            }
        } else {
            Text(
                text = shownCount.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = appearance
            )
        }
    }
}