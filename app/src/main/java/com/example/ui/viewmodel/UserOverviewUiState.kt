package com.example.ui.viewmodel

import com.example.data.model.FavouriteCharacter
import com.example.data.model.FavouriteStaff
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
        val animeCount: Int,
        val mangaCount: Int,
        val episodesWatched: Int,
        val minutesWatched: Long,
        val meanScore: Double?,
        val standardDeviation: Int?,
        val isRefreshing: Boolean = false,
        val isDemoMode: Boolean = false
    ) : UserOverviewUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val isAuthError: Boolean = false
    ) : UserOverviewUiState
}