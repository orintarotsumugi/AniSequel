package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.domain.profile.ProfileLayout
import com.anisequel.domain.profile.ProfileSection
import com.anisequel.ui.components.AppVectorIcons

/**
 * The profile's layout editor: order, visibility and the two-column characters.
 *
 * A sheet rather than a settings page, because the thing being edited is the page
 * behind it - the reader can dismiss it and see the result immediately, and the
 * header's palette button is the only entry that cannot be hidden by the layout it
 * edits. Changes are written on each tap rather than on a Save button: the page
 * behind the sheet is live, and a Save/Cancel pair would mean a preview that lies
 * until confirmed.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileLayoutSheet(
    layout: ProfileLayout,
    onMove: (ProfileSection, Int) -> Unit,
    onToggle: (ProfileSection) -> Unit,
    onCharactersTwoColumn: (Boolean) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 20.dp)
                .testTag("profile_layout_sheet")
        ) {
            Text(
                text = "Profile layout",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Reorder, hide or show the blocks on your own profile. " +
                    "The header stays - it is the page, not a block.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            val order = layout.resolvedOrder
            order.forEachIndexed { index, section ->
                val visible = section !in layout.hidden
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .testTag("profile_layout_row_${section.storageValue}"),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = section.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (visible) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { onMove(section, -1) },
                        enabled = index > 0
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.ExpandLess,
                            contentDescription = "Move ${section.displayName} up",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { onMove(section, 1) },
                        enabled = index < order.lastIndex
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.ExpandMore,
                            contentDescription = "Move ${section.displayName} down",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = visible,
                        onCheckedChange = { onToggle(section) },
                        modifier = Modifier.testTag(
                            "profile_layout_visible_${section.storageValue}"
                        )
                    )
                }
            }

            if (ProfileSection.FAVOURITE_CHARACTERS !in layout.hidden) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Two-column characters",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Show the character favourites as a 2-wide grid " +
                                "instead of the single strip.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = layout.charactersTwoColumn,
                        onCheckedChange = onCharactersTwoColumn,
                        modifier = Modifier.testTag(
                            "profile_layout_characters_two_column"
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            TextButton(
                onClick = onReset,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile_layout_reset")
            ) {
                Text("Reset layout")
            }
        }
    }
}
