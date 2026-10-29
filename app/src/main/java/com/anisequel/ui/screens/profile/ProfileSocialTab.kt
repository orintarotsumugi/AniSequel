package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.FollowUser
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.viewmodel.SocialList
import com.anisequel.ui.viewmodel.UserSocialViewModel

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.anisequel.ui.components.expressive.ExpressiveShapes
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
    /**
     * Re-asks for a list, discarding whatever is on screen.
     *
     * Reached from the failure state's own button, and from the chip that stands
     * for a list whose fetch failed - both are a reader asking again, so the
     * guard in [onEnsureLoaded] does not apply.
     */
    onLoad: (SocialList) -> Unit,
    /**
     * Starts the fetch if nothing is producing one.
     *
     * This, not [onLoad], is what the entry trigger calls: the effect re-runs on
     * every state change, so an unguarded `load` here would be one pair of requests
     * per recomposition. The ViewModel's guard makes it one per visit.
     */
    onEnsureLoaded: (SocialList) -> Unit,
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
    // Which chip is highlighted comes from the state that is actually on screen.
    // Only `Success` used to be consulted, so tapping Following - and then waiting
    // for it, or failing to load it - left Followers lit while the content under
    // the switch was the other list's spinner or error.
    val currentSelected = when (socialState) {
        is UserSocialViewModel.SocialState.Success -> socialState.selected
        is UserSocialViewModel.SocialState.Loading -> socialState.list
        is UserSocialViewModel.SocialState.Error -> socialState.list
        UserSocialViewModel.SocialState.Idle -> SocialList.FOLLOWERS
    }
    // The tab's entry trigger, keyed on what it actually loads rather than on
    // `Unit`.
    //
    // A one-shot effect could only ever ask once per composition, and only from
    // `Idle`: re-entering the tab after the job had been cancelled - or arriving
    // on the state the ViewModel publishes while the profile's id is still
    // unknown - left a spinner that nothing was going to clear, because the only
    // thing that could restart the request was a first frame the tab had already
    // had. Keying on the selection means switching Followers/Following and coming
    // back asks again, and [onEnsureLoaded] is what keeps that to one request per
    // visit rather than one per recomposition.
    LaunchedEffect(currentSelected, socialState) {
        onEnsureLoaded(currentSelected)
    }
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
                // A list that failed has no count to print. Reporting the rows this
                // state still holds for it - which after a failure are the ones from
                // before it, or none - as "Following (0)" states a fact about that
                // person's account that nothing has evidence for, and it contradicts
                // the failure the same state carries. `SocialSelector` omits the
                // number for a null count, which is the honest label.
                followersCount = successState
                    ?.takeIf { it.failedList != SocialList.FOLLOWERS }
                    ?.followers?.size,
                followingCount = successState
                    ?.takeIf { it.failedList != SocialList.FOLLOWING }
                    ?.following?.size,
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

        if (successState?.isRefreshing == true && rawPeople.isNotEmpty()) {
            // A re-fetch of rows that are already on screen says so with a marker
            // *beside* them. It used to replace the grid with skeletons, which read
            // as the data being lost - and for the length of the round trip left the
            // reader's scroll position pointing into a placeholder list.
            item(span = { GridItemSpan(maxLineSpan) }) {
                SocialRefreshing()
            }
        }

        if (rawPeople.isNotEmpty()) {
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
                    // Three different things look identical here - nothing on screen -
                    // and only one of them is a sentence about this person.
                    //
                    // A re-fetch of a list that has no rows yet is still fetching, and
                    // the two outcomes of that are not yet known. The half that
                    // *failed* is not the half that is empty: saying "nobody follows
                    // this account" because one request was refused states a fact about
                    // that person that nothing has evidence for, so it keeps the reason
                    // and the re-ask instead. Only a list that came back with nobody in
                    // it gets the empty message.
                    val shownFailed = socialState.failedList == currentSelected
                    when {
                        socialState.isRefreshing ->
                            item(span = { GridItemSpan(maxLineSpan) }) { SocialLoading() }

                        shownFailed && socialState.failureMessage != null ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SocialError(
                                    message = socialState.failureMessage,
                                    // The deliberate re-ask, which is also what a tap
                                    // on the failed chip does.
                                    onRetry = { onLoad(currentSelected) }
                                )
                            }

                        else ->
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                SocialEmpty(selected = socialState.selected)
                            }
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
