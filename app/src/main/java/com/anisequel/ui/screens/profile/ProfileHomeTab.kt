package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
    listState: LazyListState = rememberLazyListState(),
    /**
     * The reader's arrangement of this page.
     *
     * Defaulted so every existing caller keeps the shipped order, and passed in by
     * the screen once the profile-layout preference is wired: the tab draws what it
     * is given rather than reading a preference of its own, which is what keeps the
     * editor's preview and the page from disagreeing.
     */
    layout: com.anisequel.domain.profile.ProfileLayout =
        com.anisequel.domain.profile.ProfileLayout(),
    /**
     * Opens the layout editor, or null when this page is not the viewer's own.
     *
     * The control lives on the header because that is the only element the reader
     * cannot reorder away - a palette button inside a hideable section would vanish
     * with the section it was meant to bring back.
     */
    onEditLayout: (() -> Unit)? = null
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
            Box {
                ProfileHeaderCard(
                    state = state,
                    modifier = Modifier.testTag("profile_header_card")
                )
                if (onEditLayout != null) {
                    IconButton(
                        onClick = onEditLayout,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 6.dp, end = 6.dp)
                            .testTag("profile_layout_edit")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Tune,
                            contentDescription = "Edit profile layout",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // The stored order, minus hidden sections. The header is not in it: the
        // page's identity is not a block the reader can reorder away.
        //
        // Each block lives in its own `LazyListScope` helper: the dispatch below is
        // one line per section, so adding a block cannot push this composable over
        // the complexity budget (CodeFactor counts the branches in one function).
        layout.visibleSections.forEach { section ->
            when (section) {
                com.anisequel.domain.profile.ProfileSection.ABOUT ->
                    aboutItem(state = state, mediaCovers = mediaCovers)

                com.anisequel.domain.profile.ProfileSection.FAVOURITE_ANIME ->
                    favouriteMediaItems(
                        headerKey = "favourite_anime",
                        entryKey = "favourite_anime_row",
                        title = "Favourite Anime",
                        icon = AppVectorIcons.FavouriteAnime,
                        items = state.favouriteAnime,
                        onOpen = onOpenDetail
                    )

                com.anisequel.domain.profile.ProfileSection.FAVOURITE_MANGA ->
                    favouriteMediaItems(
                        headerKey = "favourite_manga",
                        entryKey = "favourite_manga_row",
                        title = "Favourite Manga",
                        icon = AppVectorIcons.FavouriteManga,
                        items = state.favouriteManga,
                        onOpen = onOpenDetail
                    )

                com.anisequel.domain.profile.ProfileSection.FAVOURITE_CHARACTERS ->
                    favouriteCharacterItems(
                        characters = state.favouriteCharacters,
                        twoColumns = layout.charactersTwoColumn,
                        context = context,
                        onOpenCharacter = onOpenCharacter
                    )

                com.anisequel.domain.profile.ProfileSection.FAVOURITE_STAFF ->
                    favouriteStaffItems(
                        staff = state.favouriteStaff,
                        context = context,
                        onOpenStaff = onOpenStaff
                    )

                com.anisequel.domain.profile.ProfileSection.FAVOURITE_STUDIOS ->
                    favouriteStudioItems(
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

/** The About card - or the note that says there is no bio - as one lazy item. */
private fun LazyListScope.aboutItem(
    state: UserOverviewUiState.Success,
    mediaCovers: Map<MediaCoverKey, MediaCover>
) {
    item(key = "about") {
        if (state.about != null) {
            AboutCard(about = state.about, mediaCovers = mediaCovers)
        } else {
            NoBioNote()
        }
    }
}

/**
 * One favourite-media block: its heading and its strip.
 *
 * Anime and manga share this because the two rows differ by nothing but their
 * contents - the "two different bonds" argument below is about characters and
 * staff, not about the media halves.
 */
private fun LazyListScope.favouriteMediaItems(
    headerKey: String,
    entryKey: String,
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    items: List<MediaNode>,
    onOpen: (MediaNode) -> Unit
) {
    if (items.isEmpty()) return
    item(key = headerKey) {
        ProfileSectionHeader(
            title = title,
            icon = icon,
            trailing = "${items.size}",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = entryKey) {
        FavouriteMediaRow(items = items, onOpen = onOpen)
    }
}

/**
 * Characters and staff are two different bonds, so they get two headings: a
 * character is a figure the account likes, a staff member is a professional whose
 * work they follow.
 */
private fun LazyListScope.favouriteCharacterItems(
    characters: List<com.anisequel.data.model.FavouriteCharacter>,
    twoColumns: Boolean,
    context: android.content.Context,
    onOpenCharacter: ((Int) -> Unit)?
) {
    if (characters.isEmpty()) return
    item(key = "favourite_characters") {
        ProfileSectionHeader(
            title = "Favourite Characters",
            icon = AppVectorIcons.FavouriteCharacters,
            trailing = "${characters.size}",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = "favourite_characters_row") {
        FavouriteCharacterRow(
            characters = characters,
            columns = if (twoColumns) 2 else 1,
            onOpen = { character ->
                val open = onOpenCharacter
                if (open != null) open(character.id)
                else character.siteUrl?.let { openExternalUrl(context, it) }
            }
        )
    }
}

private fun LazyListScope.favouriteStaffItems(
    staff: List<com.anisequel.data.model.FavouriteStaff>,
    context: android.content.Context,
    onOpenStaff: ((Int) -> Unit)?
) {
    if (staff.isEmpty()) return
    item(key = "favourite_staff") {
        ProfileSectionHeader(
            title = "Favourite Staff",
            icon = AppVectorIcons.FavouriteStaff,
            trailing = "${staff.size}",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = "favourite_staff_row") {
        FavouriteStaffRow(
            staff = staff,
            onOpen = { member ->
                val open = onOpenStaff
                if (open != null) open(member.id)
                else member.siteUrl?.let { openExternalUrl(context, it) }
            }
        )
    }
}

private fun LazyListScope.favouriteStudioItems(
    studios: List<com.anisequel.data.model.StudioNode>,
    onOpenStudio: (Int, String?) -> Unit
) {
    if (studios.isEmpty()) return
    item(key = "favourite_studios") {
        ProfileSectionHeader(
            title = "Favourite Studios",
            icon = AppVectorIcons.FavouriteStudios,
            trailing = "${studios.size}",
            modifier = Modifier.padding(horizontal = 16.dp)
        )
    }
    item(key = "favourite_studio_row") {
        FavouriteStudioRow(studios = studios, onOpenStudio = onOpenStudio)
    }
}
