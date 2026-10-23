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
 * for the one who wants the page.
 *
 * A pinned character or staff member opens the same in-app profile sheet a title's
 * cast and crew rows open. They used to be the two rows that left the app: the tap
 * went straight to `openExternalUrl`, so a face that opened a sheet from a title
 * opened a browser tab from a profile. The browser is still the fallback when this
 * host has no repository to fetch a profile with, which is the one case where a
 * sheet could only report a failure.
 */
@Composable
fun ProfileHomeTab(
    state: UserOverviewUiState.Success,
    contentPadding: PaddingValues,
    onOpenDetail: (MediaNode) -> Unit,
    /**
     * Opens a studio's page from a pinned studio chip.
     *
     * The same `(Int, String?)` pair the Info page's own studio credits hand to the
     * same route, so "open this studio" has one definition on this screen.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    /**
     * Opens a pinned character's in-app profile sheet, or null when this host has
     * no repository to fetch one with.
     *
     * Nullable rather than a no-op for the reason `PersonProfileHost` states: a
     * portrait that looks tappable and does nothing is worse than one that opens
     * the AniList page, which is what null falls back to.
     */
    onOpenCharacter: ((Int) -> Unit)? = null,
    /** The same, for the pinned staff row. */
    onOpenStaff: ((Int) -> Unit)? = null,
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

        // Characters and staff are two different bonds, so they get two headings.
        //
        // They were merged into one "Favourite People" section for density - a
        // profile with twelve characters and no staff spent one heading and one row
        // instead of two of each. That read AniList's two connections as one list of
        // people, and they are not: a character is a figure the account likes, a
        // staff member is a professional whose work they follow, and the caption
        // under each portrait already tells the two apart. The app keeps them as two
        // lists, so the split costs one heading on the profiles that pin both.
        if (state.favouriteCharacters.isNotEmpty()) {
            item(key = "favourite_characters") {
                ProfileSectionHeader(
                    title = "Favourite Characters",
                    icon = AppVectorIcons.FavouriteCharacters,
                    trailing = "${state.favouriteCharacters.size}",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            item(key = "favourite_characters_row") {
                FavouriteCharacterRow(
                    characters = state.favouriteCharacters,
                    onOpen = { character ->
                        val open = onOpenCharacter
                        if (open != null) open(character.id)
                        else character.siteUrl?.let { openExternalUrl(context, it) }
                    }
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
                FavouriteStaffRow(
                    staff = state.favouriteStaff,
                    onOpen = { member ->
                        val open = onOpenStaff
                        if (open != null) open(member.id)
                        else member.siteUrl?.let { openExternalUrl(context, it) }
                    }
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
                FavouriteStudioRow(
                    studios = state.favouriteStudios,
                    onOpenStudio = onOpenStudio
                )
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
