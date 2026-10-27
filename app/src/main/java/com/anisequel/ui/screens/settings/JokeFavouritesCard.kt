package com.anisequel.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The lines the reader kept.
 *
 * Saving a line from the toast is only useful if there is somewhere to find it
 * again, and "somewhere" has to be a place the line can be replayed. Each row is
 * therefore both a reminder and a button: tapping it asks the real host to show
 * that exact line at the chosen edge, the same way the section's Preview button
 * asks for a random one.
 *
 * The list is deliberately not paginated or truncated. The pool is hundreds of
 * lines and the card sits in a scrolling column, so the worst case is a long page
 * the reader built themselves - and hiding their own saved lines behind "show
 * more" would be the one place the app second-guesses them.
 */
@Composable
internal fun JokeFavouritesCard(
    lines: List<String>,
    onPreview: (String) -> Unit,
    onRemove: (String) -> Unit,
    onClear: () -> Unit
) {
    SectionCard(
        title = "Saved lines",
        icon = AppVectorIcons.BookmarkDone,
        subtitle = "Jokes you kept, stored on this device"
    ) {
        if (lines.isEmpty()) {
            Text(
                text = "Tap the heart on a joke to keep it here. Saved lines survive " +
                    "\"Reset joke settings\", because they are yours rather than a setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Text(
                text = "${lines.size} saved. Tap a line to see it again at the edge above.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            lines.forEachIndexed { index, line ->
                JokeSavedLineRow(
                    line = line,
                    index = index,
                    onPreview = { onPreview(line) },
                    onRemove = { onRemove(line) }
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            SettingsButton(
                text = "Clear saved lines",
                onClick = onClear,
                icon = AppVectorIcons.Close,
                variant = SettingsButtonVariant.Outlined,
                fillWidth = true,
                testTag = "joke_clear_favourites_button"
            )
        }
    }
}

/**
 * One saved line, with the two things that can be done to it.
 *
 * The text is the row's label and the buttons are trailing, rather than the
 * whole row being a play button with a hidden delete: replaying is safe and
 * obvious, deleting is neither, and a row that does both on one tap makes the
 * safe action and the destructive one the same gesture.
 */
@Composable
private fun JokeSavedLineRow(
    line: String,
    index: Int,
    onPreview: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier.padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
        JokeSavedLineAction(
            icon = AppVectorIcons.Play,
            label = "Show this line",
            onClick = onPreview,
            testTag = "joke_saved_preview_$index"
        )
        JokeSavedLineAction(
            icon = AppVectorIcons.Close,
            label = "Remove from saved lines",
            onClick = onRemove,
            testTag = "joke_saved_remove_$index"
        )
    }
}

/**
 * A 36dp icon action.
 *
 * `RoundedCornerShape(percent = 50)` rather than a Material shape for the same
 * reason as every other capsule in this page: a polygon stretched to a 36dp
 * square is not a circle.
 */
@Composable
private fun JokeSavedLineAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    testTag: String
) {
    val haptics = LocalHapticFeedback.current
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(percent = 50))
            .bouncyPress(pressedScale = 0.86f)
            .clickable(onClickLabel = label, role = Role.Button) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .semantics { contentDescription = label }
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
