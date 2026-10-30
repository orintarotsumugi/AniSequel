package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.FollowUser
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.viewmodel.SocialList

/**
 * The pieces the Social tab is built from.
 *
 * Split out of ProfileSocialTab for the same reason the rest of the tab directory
 * is split: the tab's *behaviour* - which half is selected, what a chip tap does to
 * an in-flight fetch, whether rows are shown as a grid - is one reason to change a
 * file, and the sixteen-line composables that paint a face are another.
 */
/**
 * Grid or list, as one control.
 *
 * The glyph is the mode that is *on screen*, not the one the tap will switch to,
 * because the control sits under the grid it describes and a button showing an
 * incoming layout reads as a claim about the current one - the same mistake the
 * Library's sort chips were fixed for, where the chip pressed and the order
 * applied were different controls. The content description carries the action.
 *
 * 48dp rather than the chip row's 44: it is a square icon with no label beside
 * it, so its hit area is the only thing telling the user it is a control at all.
 */
@Composable
internal fun SocialViewToggle(
    asGrid: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    IconButton(
        onClick = onToggle,
        modifier = modifier
            .size(48.dp)
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .bouncyPress(pressedScale = 0.92f)
            .testTag("social_view_toggle")
    ) {
        Icon(
            imageVector = if (asGrid) AppVectorIcons.ViewGrid else AppVectorIcons.List,
            contentDescription = if (asGrid) "Show as list" else "Show as grid",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
internal fun SocialSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier.testTag("social_search_input"),
        placeholder = {
            Text(
                text = "Search users...",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = AppVectorIcons.Search,
                contentDescription = "Search",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Clear search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        singleLine = true,
        shape = ExpressiveShapes.pill,
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            focusedBorderColor = MaterialTheme.colorScheme.primary,
            unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant
        )
    )
}

@Composable
internal fun SocialSelector(
    selected: SocialList,
    followersCount: Int?,
    followingCount: Int?,
    onSelect: (SocialList) -> Unit,
    modifier: Modifier = Modifier
) {
    val followersLabel = if (followersCount != null) "Followers ($followersCount)" else "Followers"
    val followingLabel = if (followingCount != null) "Following ($followingCount)" else "Following"

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveStateChip(
            label = followersLabel,
            selected = selected == SocialList.FOLLOWERS,
            onClick = { onSelect(SocialList.FOLLOWERS) },
            // These two are this tab's only navigation, and at the chip's own
            // ~30dp height they were the smallest targets on the screen.
            minTouchTargetHeight = 48.dp,
            modifier = Modifier
                .weight(1f)
                .testTag("social_tab_followers")
        )
        ExpressiveStateChip(
            label = followingLabel,
            selected = selected == SocialList.FOLLOWING,
            onClick = { onSelect(SocialList.FOLLOWING) },
            minTouchTargetHeight = 48.dp,
            modifier = Modifier
                .weight(1f)
                .testTag("social_tab_following")
        )
    }
}

@Composable
internal fun FollowerCard(
    person: FollowUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = modifier
            .bouncyPress(pressedScale = 0.96f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(bottom = 6.dp)
            .testTag("follower_card"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            AsyncImage(
                model = person.avatar?.medium ?: person.avatar?.large,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    // Square, matching the reference. A 1:1 crop of a 220x220
                    // avatar is also what AniList serves, so nothing is
                    // resampled. `shapes.large` and the hairline border are what
                    // separate one face from the next: two hundred avatars on the
                    // app's surface colour used to bleed into each other into a
                    // single mosaic with no edges.
                    .aspectRatio(1f)
                    .clip(MaterialTheme.shapes.large)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(AppVectorIcons.ProfileSocial),
                error = rememberVectorPainter(AppVectorIcons.ProfileSocial),
                fallback = rememberVectorPainter(AppVectorIcons.ProfileSocial)
            )
        }
        Spacer(modifier = Modifier.height(7.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * One person as a full-width row: portrait, then the whole name.
 *
 * The name is `titleSmall` and unconstrained in width where the grid's card is a
 * single ellipsised `labelLarge` line - which is the entire point of the mode.
 * The portrait is 52dp because it has to survive being seen next to a line of text
 * rather than on its own; below about 48 it stops reading as a face.
 */
@Composable
internal fun FollowerRow(
    person: FollowUser,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("follower_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = person.avatar?.medium ?: person.avatar?.large,
            contentDescription = null,
            modifier = Modifier
                .size(52.dp)
                .clip(MaterialTheme.shapes.medium)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(AppVectorIcons.ProfileSocial),
            error = rememberVectorPainter(AppVectorIcons.ProfileSocial),
            fallback = rememberVectorPainter(AppVectorIcons.ProfileSocial)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Text(
            text = person.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
internal fun SocialLoading() {
    val phase = rememberShimmerPhase()
    PersonRowsSkeleton(
        phase = phase,
        avatarSize = 52.dp,
        // The rows it stands in for clip their avatars to a rounded square, not a
        // circle - see `FollowerRow` - and a skeleton of the wrong shape is a
        // layout the reader will watch change.
        avatarShape = MaterialTheme.shapes.medium,
        horizontalPadding = 12.dp,
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .testTag("social_loading")
    )
}

/**
 * The in-place marker for a re-fetch over rows that are already on screen.
 *
 * Deliberately not [SocialLoading]: the skeletons stand in for a grid that has
 * nothing to show yet, and swapping them under a reader who can see fifty people
 * both hides those people and jumps the scroll position - the grid's item count
 * changes, so where they were is no longer where anything is.
 */
@Composable
internal fun SocialRefreshing() {
    Column(
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .testTag("social_refreshing"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(2.dp))
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
internal fun SocialError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .testTag("social_error"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .bouncyPress()
                .testTag("social_retry_button")
        ) {
            Text("Try again")
        }
    }
}

@Composable
internal fun SocialEmpty(selected: SocialList) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .testTag("social_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.ProfileSocial,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(14.dp))
        Text(
            text = when (selected) {
                SocialList.FOLLOWERS -> "No followers"
                SocialList.FOLLOWING -> "Not following anyone"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
