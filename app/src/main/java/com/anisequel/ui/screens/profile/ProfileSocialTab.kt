package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.FollowUser
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.viewmodel.SocialList
import com.anisequel.ui.viewmodel.UserSocialViewModel

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.theme.AniSequelTheme
import java.util.Locale

/**
 * The Social tab: the people who follow this account, and the people it follows.
 *
 * ## Tapping somebody opens *their* AniList
 *
 * [onOpenUser] carries the tapped [FollowUser] out, and the destination builds
 * the next profile from that person - never from the signed-in viewer. This is
 * the whole point of the tab, and the way it goes wrong is silent: if the
 * navigation ever fell back to "whoever is signed in", all fifty faces in this
 * grid would open the same profile and nothing on screen would look broken. The
 * id is passed rather than the name alone so the next profile can be fetched by
 * id, which is the same path the signed-in profile takes.
 *
 * ## Three columns, not two
 *
 * A follower grid is a face-recognising task: the user is looking for *someone
 * they know*, and the number of faces they can hold at once is what decides
 * whether they find that person. Two columns of huge portraits on a phone shows
 * nine people per screen; three shows fifteen. Fixed rather than adaptive so the
 * grid does not reflow when the window is resized mid-scroll.
 *
 * ## The A-Z switch is a choice the reader makes, not the default
 *
 * AniList returns followers in its own order, which is roughly "most recently
 * followed first" - the order someone's own list of people was built in. Sorting it
 * alphabetically by default would throw that away to answer a question nobody
 * asked. So the switch is off until it is turned on, and turning it on is the only
 * thing that reorders the grid: the search field narrows what is shown, it never
 * reorders it.
 */
@Composable
fun ProfileSocialTab(
    socialState: UserSocialViewModel.SocialState,
    onSelectList: (SocialList) -> Unit,
    onLoad: (SocialList) -> Unit,
    onOpenUser: (FollowUser) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    /**
     * Hoisted by the screen so the grid's scroll position outlives a tab switch.
     *
     * This is the tab where that matters most: it is the only one that is read
     * by scrolling *and* commonly left and returned to, and a fifty-person grid
     * that resets to the top on every visit makes the Followers/Following switch
     * feel like it costs more than it does.
     */
    gridState: LazyGridState = rememberLazyGridState()
) {
    // Triggers initial load when the Social tab is opened. Without this,
    // the tab stays indefinitely in Idle state and spins forever.
    LaunchedEffect(Unit) {
        if (socialState is UserSocialViewModel.SocialState.Idle) {
            onLoad(SocialList.FOLLOWERS)
        }
    }

    var searchQuery by rememberSaveable { mutableStateOf("") }
    // Sort state survives a rotation for the same reason the query does: a reader
    // who switched to A-Z and rotated the phone would otherwise be handed the
    // AniList order back, with the grid silently reshuffled under them.
    var sortByName by rememberSaveable { mutableStateOf(false) }

    /**
     * Faces, or names.
     *
     * The grid is the default because recognising somebody is what this tab is
     * for, and a portrait is faster to pick out of fifteen than a name is to read
     * out of fifteen - and it is the one thing the search field cannot help with,
     * since it needs the name to search by.
     *
     * The list is for the other half of the job: "who is this handle, and am I
     * actually following them". A portrait beside a full name reads a long
     * account at a glance, where the grid truncates every name to a single
     * ellipsised line - `PotatoWithThoug...` is in the screenshots this was built
     * from, and it tells you nothing.
     */
    var asGrid by rememberSaveable { mutableStateOf(true) }

    val successState = socialState as? UserSocialViewModel.SocialState.Success
    val currentSelected = successState?.selected ?: SocialList.FOLLOWERS
    val rawPeople = successState?.peopleFor(currentSelected).orEmpty()
    val filteredPeople = remember(rawPeople, searchQuery, sortByName) {
        val matches = if (searchQuery.isBlank()) {
            rawPeople
        } else {
            rawPeople.filter { it.name.contains(searchQuery.trim(), ignoreCase = true) }
        }
        // `Locale.ROOT`, not the default: the name is somebody else's identifier,
        // not a word being read aloud, and Turkish would otherwise sort every
        // name containing an `i` to the end of the grid.
        if (sortByName) matches.sortedBy { it.name.lowercase(Locale.ROOT) } else matches
    }

    LazyVerticalGrid(
        // `Fixed` rather than `Adaptive`: the grid must not reflow when the
        // window changes, because the user's scroll position would no longer be
        // the person they were looking at.
        columns = GridCells.Fixed(3),
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_social_tab"),
        state = gridState,
        contentPadding = profileListPadding(contentPadding),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            SocialSelector(
                selected = currentSelected,
                followersCount = successState?.followers?.size,
                followingCount = successState?.following?.size,
                onSelect = { list ->
                    searchQuery = ""
                    onSelectList(list)
                },
                modifier = Modifier
                    .widthIn(max = ProfileMaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        if (rawPeople.size > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier
                        .widthIn(max = ProfileMaxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The search field only earns its row once the list is longer
                    // than a screen, but the sort switch is useful at any size -
                    // so it holds the row on its own for short lists rather than
                    // appearing and disappearing with the count.
                    if (rawPeople.size > 5) {
                        SocialSearchBar(
                            query = searchQuery,
                            onQueryChange = { searchQuery = it },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    ExpressiveStateChip(
                        label = "A-Z",
                        selected = sortByName,
                        onClick = { sortByName = !sortByName },
                        // This switch reorders every face in the grid, so a missed
                        // tap is not a lost press - it is a list the user believes
                        // is in AniList order and is not.
                        minTouchTargetHeight = 48.dp,
                        modifier = Modifier.testTag("social_sort_chip")
                    )
                    SocialViewToggle(
                        asGrid = asGrid,
                        onToggle = { asGrid = !asGrid }
                    )
                }
            }
        }

        if (searchQuery.isNotBlank() && filteredPeople.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = "Showing ${filteredPeople.size} of ${rawPeople.size}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .widthIn(max = ProfileMaxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .testTag("social_search_count")
                )
            }
        }

        when (socialState) {
            is UserSocialViewModel.SocialState.Loading,
            UserSocialViewModel.SocialState.Idle -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SocialLoading()
                }
            }

            is UserSocialViewModel.SocialState.Error -> {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    SocialError(
                        message = socialState.message,
                        onRetry = { onLoad(socialState.list) }
                    )
                }
            }

            is UserSocialViewModel.SocialState.Success -> {
                if (rawPeople.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        SocialEmpty(selected = socialState.selected)
                    }
                } else if (filteredPeople.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(
                            text = "No matches for \"$searchQuery\"",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 32.dp)
                        )
                    }
                } else if (asGrid) {
                    items(
                        items = filteredPeople,
                        // Scoped by list, because the grid is showing one list at a
                        // time but both are in the same `LazyVerticalGrid`. Following
                        // somebody who also follows you puts their id in *both* lists,
                        // and a bare `id` key then matches the same slot twice on the
                        // switch - so the grid tries to animate one person into a
                        // different person instead of replacing the list.
                        //
                        // The view mode is part of the key for the same reason: a
                        // face card and a name row are not two states of one item,
                        // they are different items, and sharing a key would make the
                        // Lazy layout reuse one slot's state for the other.
                        key = { "${currentSelected}_g_${it.id}" },
                        contentType = { "follower_grid" }
                    ) { person ->
                        FollowerCard(
                            person = person,
                            onClick = { onOpenUser(person) }
                        )
                    }
                } else {
                    items(
                        items = filteredPeople,
                        key = { "${currentSelected}_l_${it.id}" },
                        // One per line, which is what makes the name readable
                        // rather than truncated: the row is the grid's own
                        // full-span slot, so both modes stay in one `LazyVerticalGrid`
                        // and the scroll position and the search field above them
                        // are not rebuilt when the mode changes.
                        span = { GridItemSpan(maxLineSpan) },
                        contentType = { "follower_row" }
                    ) { person ->
                        FollowerRow(
                            person = person,
                            onClick = { onOpenUser(person) }
                        )
                    }
                }
            }
        }
    }
}

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
private fun SocialViewToggle(
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
private fun SocialSearchBar(
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
private fun SocialSelector(
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
private fun FollowerCard(
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
private fun FollowerRow(
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
private fun SocialLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp)
            .testTag("social_loading"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun SocialError(message: String, onRetry: () -> Unit) {
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
private fun SocialEmpty(selected: SocialList) {
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
