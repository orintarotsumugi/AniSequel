package com.anisequel.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.bouncyPress

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
    modifier: Modifier = Modifier
) {
    val tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    val appearance = if (enabled) tint else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = appearance
        )
    }
}