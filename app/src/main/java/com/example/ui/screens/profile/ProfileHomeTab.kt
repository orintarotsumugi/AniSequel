package com.example.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.openExternalUrl
import com.example.data.model.MediaNode
import com.example.ui.viewmodel.UserOverviewUiState

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
    modifier: Modifier = Modifier
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
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "header") {
            ProfileHeaderCard(
                state = state,
                modifier = Modifier.testTag("profile_header_card")
            )
        }

        item(key = "about") {
            if (state.about != null) {
                AboutCard(about = state.about)
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

        if (state.favouriteCharacters.isNotEmpty()) {
            item(key = "favourite_characters") {
                ProfileSectionHeader(
                    title = "Favourite Characters",
                    icon = AppVectorIcons.FavouriteCharacters,
                    trailing = "${state.favouriteCharacters.size}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "favourite_character_row") {
                FavouritePeopleRow(
                    characters = state.favouriteCharacters,
                    staff = emptyList(),
                    onOpen = { url -> url?.let { openExternalUrl(context, it) } }
                )
            }
        }

        if (state.favouriteStaff.isNotEmpty()) {
            item(key = "favourite_staff") {
                ProfileSectionHeader(
                    title = "Favourite Staff",
                    icon = AppVectorIcons.FavouriteStaff,
                    trailing = "${state.favouriteStaff.size}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "favourite_staff_row") {
                FavouritePeopleRow(
                    characters = emptyList(),
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
