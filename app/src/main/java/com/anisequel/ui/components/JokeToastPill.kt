package com.anisequel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.domain.jokes.JokeDwell
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The pill itself.
 *
 * Spring tokens rather than a tween, like every other entrance in the app. The
 * direction follows the chosen edge: a bottom toast starts below its resting
 * place and rises, a top toast starts above and drops in, and both leave back
 * the way they came (positive offsets are downward).
 *
 * [dwell] is passed only so the draw's semantics can describe the arm; the timing
 * itself belongs to the schedule that owns the wait.
 */
@Composable
internal fun JokeToastPill(
    joke: String,
    golden: Boolean,
    visible: Boolean,
    fromBottom: Boolean,
    dwell: JokeDwell,
    speakerName: String,
    stamp: String,
    textEffect: com.anisequel.domain.jokes.JokeTextEffect,
    compact: Boolean,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    val darkGold = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val containerColor = if (golden) {
        if (darkGold) Color(0xFF5B4300) else Color(0xFFFFE39A)
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (golden) {
        if (darkGold) Color(0xFFFFE9A3) else Color(0xFF3E2D00)
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val accentColor = if (golden) {
        if (darkGold) Color(0xFFFFC107) else Color(0xFF986500)
    } else {
        MaterialTheme.colorScheme.primary
    }
    AnimatedVisibility(
        visible = visible,
        // The slide's spec is an `IntOffset` one - `slideInVertically` takes
        // offsets, not floats - which is why this uses the offset token rather
        // than `FastSpatialInt`; the two are not interchangeable.
        //
        // `FastSpatialOffset` rather than the calm default: the pill is an aside,
        // and at the push spring it was still travelling when the reader had
        // finished glancing at it. The calm spring belongs to pages the reader
        // chose to open.
        enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            slideInVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { height ->
                if (fromBottom) height / 2 else -height / 2
            },
        exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            slideOutVertically(animationSpec = ExpressiveMotion.FastSpatialOffset) { height ->
                if (fromBottom) height / 2 else -height / 2
            },
        modifier = modifier
    ) {
        // The arrival's scale. `FastSpatial` and not `BouncySpatial`: this spring
        // has to settle *inside* the slide's window, because `AnimatedVisibility`
        // disposes the content when its own exit finishes and a slower scale would
        // be cut off mid-collapse. The origin is the edge the toast is anchored to,
        // so it grows out of that edge rather than out of its own middle.
        //
        // Read inside `graphicsLayer` rather than passed to it, so a frame of the
        // scale invalidates the draw pass and not this composable.
        val scale by transition.animateFloat(
            transitionSpec = { ExpressiveMotion.FastSpatial },
            label = "joke_toast_scale"
        ) { state -> if (state == EnterExitState.Visible) 1f else 0.92f }

        Surface(
            // Tapping dismisses it early. No scrim and no modal: it is a remark,
            // not a decision, and the page underneath must stay usable.
            onClick = onDismiss,
            shape = MaterialTheme.shapes.large,
            color = containerColor,
            contentColor = contentColor,
            // Higher than the 3dp it shipped with. The toast lands over a
            // scrolling list of poster artwork, and a `secondaryContainer` pill
            // with a faint tint on it is not reliably separable from a busy
            // picture underneath.
            tonalElevation = 6.dp,
            // A hairline edge so the pill reads as a surface over artwork rather
            // than a patch of the poster done in a different colour.
            border = BorderStroke(
                width = 1.dp,
                color = if (golden) {
                    accentColor.copy(alpha = 0.78f)
                } else {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                }
            ),
            modifier = Modifier
                .testTag("joke_toast")
                .widthIn(max = 420.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    transformOrigin = TransformOrigin(0.5f, if (fromBottom) 1f else 0f)
                }
                // A surface that appears by itself, with no tap and no focus
                // change to announce it, is invisible to a screen reader. Polite
                // rather than Assertive: a joke is not allowed to interrupt a
                // sentence the reader is already hearing.
                .semantics { liveRegion = LiveRegionMode.Polite }
        ) {
            JokeToastBody(
                joke = joke,
                golden = golden,
                accentColor = accentColor,
                contentColor = contentColor,
                speakerName = speakerName,
                stamp = stamp,
                textEffect = textEffect,
                compact = compact,
                fromBottom = fromBottom,
                isFavourite = isFavourite,
                onToggleFavourite = onToggleFavourite,
                staysUntilDismissed = dwell.staysUntilDismissed
            )
        }
    }
}

/**
 * The named line: Ani-chan's sparkle, her name, the joke and a way to keep it.
 *
 * Named, not anonymous. A line that arrives by itself with no attribution reads
 * like a notification from nobody; Ani-chan is the app's own voice and gives the
 * toast a reason to be there. `AnimeSparkle` and not `NewReleases`: the update
 * glyph means "go and install something", and this line is not that.
 *
 * The heart is the one control in the pill. It sits inside the Surface's tap
 * target, so its own clickable has to consume the tap - which is what stops
 * "save this line" from also meaning "dismiss the pill I am reading".
 */
@Composable
private fun JokeToastBody(
    joke: String,
    golden: Boolean,
    accentColor: Color,
    contentColor: Color,
    speakerName: String,
    stamp: String,
    textEffect: com.anisequel.domain.jokes.JokeTextEffect,
    compact: Boolean,
    fromBottom: Boolean,
    isFavourite: Boolean,
    onToggleFavourite: () -> Unit,
    staysUntilDismissed: Boolean
) {
    Row(
        modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The reader's stamp, in place of the fixed sparkle. A pill rather than a
        // circle because a stamp can be more than one glyph: a 30dp circle clipped
        // anything past the second character, and the field legitimately accepts a
        // multi-part emoji.
        Surface(
            shape = RoundedCornerShape(percent = 50),
            color = accentColor.copy(alpha = if (golden) 0.22f else 0.14f),
            modifier = Modifier.heightIn(min = 30.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (golden) {
                    Icon(
                        imageVector = AppVectorIcons.Star,
                        contentDescription = "Golden joke",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                } else {
                    Text(
                        text = stamp,
                        style = MaterialTheme.typography.titleSmall,
                        color = accentColor,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.size(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            // These two used to be `onSecondaryContainer` at 0.65 and 0.55 alpha.
            // That is not a style choice, it is a contrast bug: blended over the
            // container the name measured ~4.0:1 in the default dark scheme and
            // "Tap to dismiss" ~3.3:1, both under 4.5:1 for 11sp text. The label
            // and the caption are the two pieces of the pill a reader needs in
            // order to know who is talking and how to get rid of her.
            // The speaker line is the first thing a habituated reader can do
            // without; the stamp stays, because it is the pill's face.
            if (!compact) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (golden) "GOLDEN · $speakerName" else speakerName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = contentColor.copy(alpha = 0.92f)
                    )
                }
            }
            // Every new line *starts over*: the text re-enters from the pill's own
            // edge on the same spring the pill itself used, instead of mutating in
            // place under the reader's eyes. A timer that draws a second joke while
            // the first is up used to swap the sentence with no motion at all, which
            // read as the app editing itself mid-sentence.
            AnimatedContent(
                targetState = joke,
                transitionSpec = {
                    val direction = if (fromBottom) 1 else -1
                    slideInVertically(
                        animationSpec = ExpressiveMotion.FastSpatialOffset
                    ) { height -> direction * height / 2 } +
                        fadeIn(animationSpec = ExpressiveMotion.FastEffects) togetherWith
                        fadeOut(animationSpec = ExpressiveMotion.FastEffects)
                },
                label = "joke_line"
            ) { line ->
                // The reader's text effect, resolved here so the toast and its
                // Settings preview share one implementation - and so INSTANT still
                // means instant.
                JokeLineText(
                    line = line,
                    effect = textEffect
                )
            }
            // "Until tapped" is the one arm where the pill will not leave on its
            // own, so it says so. Every other arm has a timer, and a caption
            // explaining that would be noise on every line.
            if (!compact && staysUntilDismissed) {
                Text(
                    text = "Tap to dismiss",
                    style = MaterialTheme.typography.labelSmall,
                    color = contentColor.copy(alpha = 0.82f)
                )
            }
        }
        Spacer(modifier = Modifier.size(6.dp))
        JokeFavouriteButton(isFavourite = isFavourite, onToggle = onToggleFavourite)
    }
}

/**
 * Save or unsave the line currently on screen.
 *
 * Capsule-shaped and drawn from the app's own vectors, like every other icon.
 * The colour rides the effects spring and the press the bouncy one - colour is
 * not geometry and must not overshoot, which is the same division the Settings
 * chips use.
 */
@Composable
private fun JokeFavouriteButton(isFavourite: Boolean, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(
        targetValue = if (isFavourite) {
            MaterialTheme.colorScheme.primary
        } else {
            // 0.6 was under the 3:1 non-text minimum against the container in the
            // darker palettes, which made the unsaved heart look disabled rather
            // than available.
            MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.74f)
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "joke_favourite_tint"
    )
    val label = if (isFavourite) "Remove from favourites" else "Save this joke"

    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .bouncyPress(pressedScale = 0.86f)
            .clickable(onClickLabel = label, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onToggle()
            }
            .semantics { contentDescription = label }
            .testTag("joke_favourite"),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = if (isFavourite) {
                AppVectorIcons.ActivityLiked
            } else {
                AppVectorIcons.ActivityNotLiked
            },
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(18.dp)
        )
    }
}

