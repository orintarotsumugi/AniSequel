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
import com.example.ui.viewmodel.UserOverviewUiState

/**
 * The Home tab: who this is, what they have pinned, and what they wrote.
 *
 * Everything that is about *the person* rather than about a number. The counts
 * live on the Stats tab, because a person opening their own profile is usually
 * looking for the thing they pinned, and a wall of statistics above the favourites
 * is what AniList's own profile does and why people scroll past it.
 *
 * The bio sits under the favourites rather than above them, for the same reason.
 */
@Composable
fun ProfileHomeTab(
    state: UserOverviewUiState.Success,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_home_tab"),
        contentPadding = profileListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "header") {
            ProfileHeaderCard(
                state = state,
                modifier = Modifier.testTag("profile_header_card")
            )
        }

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
                onOpen = { media -> media.siteUrl?.let { openExternalUrl(context, it) } }
            )
        }

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
                onOpen = { media -> media.siteUrl?.let { openExternalUrl(context, it) } }
            )
        }

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

        item(key = "about") {
            if (state.about != null) {
                AboutCard(about = state.about)
            } else {
                NoBioNote()
            }
        }
    }
}