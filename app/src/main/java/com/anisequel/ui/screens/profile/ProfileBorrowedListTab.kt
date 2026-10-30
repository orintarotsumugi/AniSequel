package com.anisequel.ui.screens.profile

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.MediaNode
import com.anisequel.domain.usecase.fetchProfileListEagerly
import com.anisequel.ui.viewmodel.UserOverviewUiState
import com.anisequel.ui.viewmodel.UserOverviewViewModel

/**
 * Somebody else's list, on their profile, in the grid the reader's own list uses.
 *
 * ## Why this is a fifth tab and not a pushed page
 *
 * It hangs off the profile it belongs to: the reader got here by tapping a face in
 * the Social grid, and the thing they want is *this person's* titles, next to their
 * bio and their stats, behind the same Back button. A separate full-screen route
 * would need its own top bar, its own sheet wiring and its own identity plumbing to
 * draw the same grid - and would leave the profile's tab count disagreeing with the
 * one page that shows what the profile is about. So it is a tab, [ProfileLibraryTab]
 * is reused rather than rewritten, and the only new thing here is whose view the
 * chips write to.
 *
 * ## Whose preferences the chips are wired to
 *
 * Not the reader's. [borrowedLibraryView] hands the grid a store that forgets, and
 * [ProfileLibraryTab]'s own KDoc is the record of what the alternative does: pointing
 * a borrowed grid at the viewer's [com.anisequel.data.repository.LibraryPreferences]
 * writes a stranger's sort and type into the store the reader's *own* Library page
 * opens from, so leaving a follower's profile would re-arrange the reader's list.
 * Reading the same store back is the other half of the same bug - the reader's "show
 * me manga, by score" landing on a stranger's grid they expected to be plain.
 *
 * ## Why the list is not here when this opens
 *
 * The anime half is fetched by whoever asks - see [fetchProfileListEagerly] - because
 * a stranger's list is up to forty sequential chunk requests, and reading their bio
 * should not pay for it. Until it lands this draws on [UserOverviewUiState.Success]'s
 * own `isLibraryLoading` flag, which the grid shows as "still fetching" rather than
 * as an empty library: the same lie, in the same words, that a failed fetch used to
 * be told as.
 */
@Composable
fun ProfileBorrowedListTab(
    state: UserOverviewUiState.Success,
    /**
     * Held for its three manga flows and its retry, not for the anime half.
     *
     * The list this tab renders arrives with the profile or is asked for by the
     * screen's own tab trigger; the manga half is a separate, on-demand download, and
     * the collector has to outlive the grid it is drawn into, which is disposed by
     * every tab switch.
     */
    overviewViewModel: UserOverviewViewModel,
    onOpenDetail: (MediaNode) -> Unit,
    onRefresh: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    gridState: LazyGridState = rememberLazyGridState()
) {
    val mangaLibrary by overviewViewModel.mangaLibrary.collectAsStateWithLifecycle()
    val isLoadingManga by overviewViewModel.isLoadingManga.collectAsStateWithLifecycle()
    val mangaError by overviewViewModel.mangaError.collectAsStateWithLifecycle()

    // The same rule the Library page uses: favourites are their own connection in
    // AniList rather than a list status, so the Favourites chip tests membership of
    // these ids. Taken from the overview this tab is already rendering, so the grid
    // and the hearted rows on the Home tab cannot disagree.
    val favouriteIds = remember(state.favouriteAnime, state.favouriteManga) {
        (state.favouriteAnime + state.favouriteManga).mapNotNullTo(mutableSetOf()) { it.id }
    }
    val remembered = remember { borrowedLibraryView() }

    ProfileLibraryTab(
        animeEntries = state.libraryAnime,
        mangaEntries = mangaLibrary,
        favouriteIds = favouriteIds,
        isLoadingManga = isLoadingManga,
        // "Not asked for yet" and "on its way" are the same flag here, and both forbid
        // the grid's empty state, which would be a statement about this person's
        // library that nothing has evidence for.
        isAnimeLoading = state.isLibraryLoading,
        libraryError = state.libraryError,
        mangaError = mangaError,
        onLoadManga = { overviewViewModel.loadMangaLibrary() },
        contentPadding = profileListPadding(contentPadding),
        onOpenDetail = onOpenDetail,
        modifier = modifier,
        remembered = remembered,
        gridState = gridState,
        isRefreshing = state.isRefreshing,
        onRefresh = onRefresh
    )
}
