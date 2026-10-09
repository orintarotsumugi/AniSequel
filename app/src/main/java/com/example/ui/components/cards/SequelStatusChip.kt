package com.example.ui.components.cards

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.theme.AniSequelTheme

/**
 * One status, as the four things the chip has to draw.
 *
 * A class rather than a `Triple` because there are four now, and the pair of
 * four-value tuples that would otherwise be needed at every branch is how one
 * status ends up with another status's label.
 */
private data class StatusVisual(
    val container: Color,
    val content: Color,
    val label: String,
    val icon: ImageVector
)

/**
 * The airing state of a franchise entry.
 *
 * Each state carries its **own icon**, not just its own colour.
 *
 * The chip used to render a bare dot for every status and encode the difference
 * entirely in hue - so "Airing" and "Upcoming" were the same shape in two
 * greens, which is not a distinction a colour-blind reader can make and not one
 * that survives a grayscale screenshot. The icons are existing semantic vectors
 * rather than new artwork: a radio-button for the state that is live right now,
 * a clock for one that has not started, a pause for Hiatus, a check for
 * Finished, an X for Cancelled.
 */
@Composable
fun SequelStatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors
    val scheme = MaterialTheme.colorScheme

    val (bgColor, textColor, label, icon) = when (status.uppercase(java.util.Locale.ROOT)) {
        "RELEASING" -> StatusVisual(
            statusColors.successContainer,
            statusColors.onSuccessContainer,
            "Airing",
            AppVectorIcons.StatStatus
        )
        "NOT_YET_RELEASED" -> StatusVisual(
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            "Upcoming",
            AppVectorIcons.Schedule
        )
        "HIATUS" -> StatusVisual(
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            "Hiatus",
            AppVectorIcons.Pause
        )
        "FINISHED" -> StatusVisual(
            scheme.surfaceContainerHighest,
            scheme.onSurfaceVariant,
            "Finished",
            AppVectorIcons.Done
        )
        "CANCELLED" -> StatusVisual(
            scheme.errorContainer,
            scheme.onErrorContainer,
            "Cancelled",
            AppVectorIcons.Close
        )
        else -> StatusVisual(
            scheme.surfaceContainerHighest,
            scheme.onSurfaceVariant,
            status.replace('_', ' ').lowercase(java.util.Locale.ROOT).replaceFirstChar { it.uppercase(java.util.Locale.ROOT) },
            AppVectorIcons.Info
        )
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = textColor,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SequelInfoChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    // No `animateColorAsState` here, deliberately. The target was
    // `onSurfaceVariant` - a value that never changes - so the animation could
    // never run: it only allocated an animation state, subscribed to the frame
    // clock and recomposed on every frame change, forever, to draw a constant
    // colour. And this chip is inside `SequelCard`, which recomposes for every
    // visible row on every scroll frame, so that cost was paid a few hundred
    // times a second to do nothing.
    val tint = MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
