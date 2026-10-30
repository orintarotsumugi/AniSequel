package com.anisequel.ui.viewmodel

import com.anisequel.data.model.FavouriteCharacter
import com.anisequel.data.model.FavouriteStaff
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.StudioNode
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.domain.usecase.ActivityCalendar
import com.anisequel.domain.usecase.ListEntryInsights
import com.anisequel.domain.usecase.ListInsights

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
         * Free on the viewer's own profile: the dashboard that led here has already
         * downloaded and cached the whole thing, and the Stats tab forces the merge
         * anyway, so this screen was throwing all of it away the moment
         * [entryInsights] had been tallied. The Library page is built on it, which is
         * why adding a tab that lists hundreds of entries cost no extra request.
         *
         * On somebody *else's* profile it starts empty and arrives when a tab asks for
         * it - see `UserOverviewViewModel.loadAnimeLibrary` and
         * `fetchProfileListEagerly` - because a stranger's list is up to forty
         * sequential requests that a reader looking at their bio never wanted.
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
         * on its way - or has not been asked for yet.
         *
         * True between the two publishes in `UserOverviewViewModel.load`: the
         * profile arrives in one request and is shown immediately, and the list -
         * one request per 500 entries, chunk-merged - follows. The distinction
         * exists so the two tabs that read it can say "counting" instead of
         * rendering zeroes, because "0 completed" is a wrong answer rather than a
         * loading one, and it is what a reader would have seen.
         *
         * On another person's profile it also covers the state before anybody asked:
         * the same flag is the honest one, because "we have not counted this" and "we
         * are counting it" both forbid the same wrong answer. The tab that shows it
         * is the one that starts the download.
         */
        val isLibraryLoading: Boolean = false,
        /**
         * Why the media list could not be fetched, or null when it was.
         *
         * A failed list fetch was flattened to "no list", so the flag above went
         * back to false and the Library page asserted the thing it had least
         * evidence for: "Nothing in this list yet", over an account with nine
         * hundred entries, with every chip reading (0) and no retry anywhere.
         * Private lists, a 429 and a dropped connection all produced the same
         * confident wrong answer.
         *
         * Null is the real answer - an empty list and a list that could not be
         * read are different, and only one of them is a thing about the person.
         */
        val libraryError: String? = null,
        /**
         * Whether this page is the device's saved copy rather than a live answer.
         *
         * The profile and Library used to have two outcomes: live data, or a
         * failure page. There is a third - the answer is on disk, the network is
         * not answering - and presenting that as a live page is a lie the screen
         * does not need to tell. The list is still worth reading; it is just not
         * current, and the header says so.
         */
        val isCached: Boolean = false,
        val isDemoMode: Boolean = false
    ) : UserOverviewUiState

    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val isAuthError: Boolean = false,
        /**
         * The classified failure, when there was one.
         *
         * Null for a failure that arrived without an [AniListException] - in
         * which case the screen falls back to its plain title and message.
         * Optional so the demo mode and any older construction path keep
         * compiling without inventing a classification they do not have.
         */
        val kind: AniListErrorKind? = null
    ) : UserOverviewUiState
}