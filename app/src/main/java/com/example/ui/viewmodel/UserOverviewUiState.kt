package com.example.ui.viewmodel

import com.example.data.model.FavouriteCharacter
import com.example.data.model.FavouriteStaff
import com.example.data.model.MediaListEntryItem
import com.example.data.model.MediaNode
import com.example.data.model.StudioNode
import com.example.domain.usecase.ActivityCalendar
import com.example.domain.usecase.ListEntryInsights
import com.example.domain.usecase.ListInsights

/**
 * The profile screen's state contract for the Home and Stats tabs.
 *
 * Split out of `UserOverviewViewModel` for the reason `DashboardUiState` is split
 * out of `DashboardViewModel`: these are the types the *screen* depends on, and
 * none of them has a repository, a coroutine or a behaviour in it. The screen
 * switches over every branch here, so being able to read what it needs without
 * scrolling through the load pipeline is the point.
 *
 * The favourites are flattened out of the response's nested
 * `favourites.anime.nodes` shape into five lists. AniList omits any branch a
 * person has never pinned anything to, and a screen that has to null-check five
 * different connection objects at five different call sites is how one of them
 * ends up rendering a header over nothing.
 */
sealed interface UserOverviewUiState {
    data class Loading(val message: String = "Loading your AniList profile...") : UserOverviewUiState

    data class Success(
        val userId: Int,
        val username: String,
        val about: String?,
        val siteUrl: String?,
        val avatarUrl: String?,
        val bannerUrl: String?,
        val createdAt: Int?,
        val updatedAt: Int?,
        val favouriteAnime: List<MediaNode>,
        val favouriteManga: List<MediaNode>,
        val favouriteCharacters: List<FavouriteCharacter>,
        val favouriteStaff: List<FavouriteStaff>,
        val favouriteStudios: List<StudioNode>,
        /** AniList's own aggregate distributions - status, format, score, genre, tag, year. */
        val insights: ListInsights,
        /** The three AniList does not aggregate: country, release year, watch year, episodes. */
        val entryInsights: ListEntryInsights,
        val activity: ActivityCalendar,
        /**
         * Every anime on this person's list, flattened and de-duplicated.
         *
         * Free: the Stats tab already forces the whole anime list to be fetched
         * and chunk-merged, and this screen was throwing all of it away the
         * moment [entryInsights] had been tallied. The Library tab is built on it,
         * which is why adding a tab that lists hundreds of entries cost no extra
         * request.
         *
         * Entries rather than bare media because the list status and progress are
         * the reason to list things - "Completed, 24/24" beside a cover is what
         * a row in this tab has to say, and it is not on the media node.
         */
        val libraryAnime: List<MediaListEntryItem>,
        val animeCount: Int,
        val mangaCount: Int,
        val episodesWatched: Int,
        val minutesWatched: Long,
        val meanScore: Double?,
        val standardDeviation: Int?,
        val isRefreshing: Boolean = false,
        /**
         * Whether the media list this profile's tallies are built from is still
         * on its way.
         *
         * True between the two publishes in `UserOverviewViewModel.load`: the
         * profile arrives in one request and is shown immediately, and the list -
         * one request per 500 entries, chunk-merged - follows. The distinction
         * exists so the two tabs that read it can say "counting" instead of
         * rendering zeroes, because "0 completed" is a wrong answer rather than a
         * loading one, and it is what a reader would have seen.
         */
        val isLibraryLoading: Boolean = false,
        val isDemoMode: Boolean = false
    ) : UserOverviewUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val isAuthError: Boolean = false
    ) : UserOverviewUiState
}