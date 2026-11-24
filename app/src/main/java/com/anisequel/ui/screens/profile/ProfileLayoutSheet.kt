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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.anisequel.domain.profile.ProfileBannerAnchor
import com.anisequel.domain.profile.ProfileBannerShape
import com.anisequel.domain.profile.ProfileLayout
import com.anisequel.domain.profile.ProfileSection
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.SegmentedOption

/**
 * The profile's layout editor: the banner's shape, crop and drift, the section
 * order and visibility, and the two-column characters.
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
    onBannerShape: (ProfileBannerShape) -> Unit,
    onBannerAnchor: (ProfileBannerAnchor) -> Unit,
    onBannerDrift: (Boolean) -> Unit,
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
            Spacer(modifier = Modifier.height(14.dp))

            // The header's shape, in the editor for the page it heads rather than
            // in Settings: the sheet sits over the page, so a tap here is a change
            // the reader can see behind it - the same reason the order is live.
            Text(
                text = "Banner shape",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            ExpressivePolygonSegmentedBar(
                options = ProfileBannerShape.entries.map {
                    SegmentedOption(label = it.displayName)
                },
                selectedIndex = ProfileBannerShape.entries.indexOf(layout.bannerShape)
                    .coerceAtLeast(0),
                onSelect = { index ->
                    ProfileBannerShape.entries.getOrNull(index)?.let(onBannerShape)
                },
                modifier = Modifier.testTag("profile_layout_banner_shape")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Adapt makes the banner as tall as its own picture needs. " +
                    "The other three give every profile the same shape.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // The other half of "the banner is the reader's artwork": the shape
            // decides how much of the header the picture gets, the position decides
            // what survives the crop that a wide banner cannot avoid. Both live
            // here, over the page they change, rather than in Settings.
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Banner position",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            ExpressivePolygonSegmentedBar(
                options = ProfileBannerAnchor.entries.map {
                    SegmentedOption(label = it.displayName)
                },
                selectedIndex = ProfileBannerAnchor.entries.indexOf(layout.bannerAnchor),
                onSelect = { index ->
                    ProfileBannerAnchor.entries.getOrNull(index)?.let(onBannerAnchor)
                },
                // Dimmed *and* disabled while drifting: a segment that still answers
                // a tap would look like it worked and change nothing visible,
                // because the sweep is drawing over the side it selected.
                enabled = !layout.bannerDrift,
                modifier = Modifier
                    .alpha(if (layout.bannerDrift) 0.4f else 1f)
                    .testTag("profile_layout_banner_anchor")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (layout.bannerDrift) {
                    "Drift is on, so the whole banner is swept. Turn it off to hold " +
                        "one side."
                } else {
                    "Which part of a banner wider than the card the header keeps. " +
                        "The picture slides to the side you pick."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Drift",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Sweep the banner slowly sideways while the page sits " +
                            "still, like a moving still.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = layout.bannerDrift,
                    onCheckedChange = onBannerDrift,
                    modifier = Modifier
                        .testTag("profile_layout_banner_drift")
                        .semantics {
                            contentDescription = "Drift"
                        }
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
                        modifier = Modifier
                            .testTag("profile_layout_visible_${section.storageValue}")
                            // A bare Switch announces only "On"/"Off"; the row's
                            // visible text is not part of its merged semantics, so
                            // TalkBack could not say which section it toggled.
                            .semantics {
                                contentDescription = section.displayName
                            }
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
                        modifier = Modifier
                            .testTag("profile_layout_characters_two_column")
                            .semantics {
                                contentDescription = "Two-column characters"
                            }
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
