package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaNode
import com.anisequel.ui.viewmodel.UserOverviewUiState

/**
 * The Home tab: who this is, what they wrote in their bio, and what they have pinned.
 *
 * Places the expressive markdown About bio right below the header card for high visibility,
 * followed by rich carousels of pinned favourites.
 *
 * A pinned anime or manga opens the same in-app detail sheet the activity feed uses,
 * rather than handing the tap to the browser. The favourites are the shows a person
 * chose to show off, so tapping one should answer with the synopsis, scores and specs
 * the app already knows how to render - and the sheet still carries the AniList link
 * for the one who wants the page. Characters, staff and studios have no media sheet,
 * so they keep the external link.
 */
@Composable
fun ProfileHomeTab(
    state: UserOverviewUiState.Success,
    contentPadding: PaddingValues,
    onOpenDetail: (MediaNode) -> Unit,
    modifier: Modifier = Modifier,
    /** Media key to cover, for the AniList cards inside the bio. */
    mediaCovers: Map<MediaCoverKey, MediaCover> = emptyMap(),
    /**
     * Hoisted by the screen so this tab's scroll position outlives a tab switch.
     *
     * `AnimatedContent` disposes the tab it is animating away from, so a state
     * created here would be garbage collected with it and the reader would come
     * back to the top of their own favourites.
     */
    listState: LazyListState = rememberLazyListState()
) {
    val context = LocalContext.current

    val hasAnyFavourites = state.favouriteAnime.isNotEmpty() ||
        state.favouriteManga.isNotEmpty() ||
        state.favouriteCharacters.isNotEmpty() ||
        state.favouriteStaff.isNotEmpty() ||
        state.favouriteStudios.isNotEmpty()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_home_tab"),
        contentPadding = profileListPadding(contentPadding),
        // 16 rather than 14: the header ends with a summary strip and the bio is
        // a card, so the two dense blocks at the top of the page need a hair more
        // air between them than the favourites rows do further down.
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = listState
    ) {
        item(key = "header") {
            ProfileHeaderCard(
                state = state,
                modifier = Modifier.testTag("profile_header_card")
            )
        }

        item(key = "about") {
            if (state.about != null) {
                AboutCard(about = state.about, mediaCovers = mediaCovers)
            } else {
                NoBioNote()
            }
        }

        if (state.favouriteAnime.isNotEmpty()) {
            item(key = "favourite_anime") {
                ProfileSectionHeader(
                    title = "Favourite Anime",
                    icon = AppVectorIcons.FavouriteAnime,
                    trailing = "${state.favouriteAnime.size}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "favourite_anime_row") {
                FavouriteMediaRow(
                    items = state.favouriteAnime,
                    onOpen = onOpenDetail
                )
            }
        }

        if (state.favouriteManga.isNotEmpty()) {
            item(key = "favourite_manga") {
                ProfileSectionHeader(
                    title = "Favourite Manga",
                    icon = AppVectorIcons.FavouriteManga,
                    trailing = "${state.favouriteManga.size}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "favourite_manga_row") {
                FavouriteMediaRow(
                    items = state.favouriteManga,
                    onOpen = onOpenDetail
                )
            }
        }

        // People, and studios, in that order, under one heading.
        //
        // These were two separate sections with two separate headings and two
        // identical rows - "Favourite Characters" and "Favourite Staff" - which read
        // as two kinds of thing rather than as one list of people a person has
        // pinned. AniList returns them as two connections and the app keeps them
        // as two lists, which is right for the model and wrong for the reader: an
        // actor and an animator are both people this account chose, and the only
        // thing that tells them apart is the caption already under each portrait.
        //
        // One heading, one row, and the existing `hasAnyFavourites` check covering
        // it - so a profile with twelve characters and no staff now spends one
        // heading and one row instead of two of each.
        if (state.favouriteCharacters.isNotEmpty() || state.favouriteStaff.isNotEmpty()) {
            item(key = "favourite_people") {
                ProfileSectionHeader(
                    title = "Favourite People",
                    icon = AppVectorIcons.FavouriteCharacters,
                    trailing = "${state.favouriteCharacters.size + state.favouriteStaff.size}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "favourite_people_row") {
                FavouritePeopleRow(
                    characters = state.favouriteCharacters,
                    staff = state.favouriteStaff,
                    onOpen = { url -> url?.let { openExternalUrl(context, it) } }
                )
            }
        }

        if (state.favouriteStudios.isNotEmpty()) {
            item(key = "favourite_studios") {
                ProfileSectionHeader(
                    title = "Favourite Studios",
                    icon = AppVectorIcons.FavouriteStudios,
                    trailing = "${state.favouriteStudios.size}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "favourite_studio_row") {
                FavouriteStudioRow(studios = state.favouriteStudios)
            }
        }

        if (!hasAnyFavourites) {
            item(key = "empty_favourites") {
                EmptyFavourites(
                    message = "No favourites pinned yet",
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }
        }
    }
}
