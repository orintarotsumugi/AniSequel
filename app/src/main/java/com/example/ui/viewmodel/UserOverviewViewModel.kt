package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.FavouriteCharacter
import com.example.data.model.FavouriteStaff
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.StudioNode
import com.example.data.model.UserOverview
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.repository.AniListRepository
import com.example.domain.usecase.BuildActivityCalendarUseCase
import com.example.domain.usecase.BuildListEntryInsightsUseCase
import com.example.domain.usecase.BuildListInsightsUseCase
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for the AniList profile screen's Home and Stats tabs.
 *
 * ## Two requests, and why the second one is nearly free
 *
 * The profile query answers the bio, the favourites, the totals and every
 * distribution. Three of the Stats tab's charts are *not* in it, because AniList
 * exposes no aggregate for them anywhere: country of origin, the year something
 * was watched rather than released, and episode-count bands. Those are tallied
 * from the media list collection, which the dashboard that led here has already
 * downloaded and cached.
 *
 * That is why [userId] is threaded through rather than looked up: the repository
 * keys the list under `user:<id>` for a signed-in viewer and `userName:<name>` for
 * a scanned profile. Fetching the signed-in viewer's list by name instead would
 * use a different key, and this screen would re-download the single most expensive
 * query in the app - multi-megabyte, and one of AniList's ~30 requests a minute -
 * to recount what was already in memory.
 */
class UserOverviewViewModel(
    private val aniListRepository: AniListRepository,
    /**
     * The signed-in viewer's AniList id, when there is one.
     *
     * Null for a scanned public profile and for the demo. That is the only thing
     * that distinguishes the two list-fetch paths.
     */
    private val userId: Int? = null,
    private val username: String? = null,
    private val isDemo: Boolean = false,
    private val buildActivityCalendar: BuildActivityCalendarUseCase = BuildActivityCalendarUseCase(),
    private val buildListInsights: BuildListInsightsUseCase = BuildListInsightsUseCase(),
    private val buildListEntryInsights: BuildListEntryInsightsUseCase = BuildListEntryInsightsUseCase()
) : ViewModel() {

    private val _uiState = MutableStateFlow<UserOverviewUiState>(UserOverviewUiState.Loading())
    val uiState: StateFlow<UserOverviewUiState> = _uiState.asStateFlow()

    /**
     * The in-flight load, cancelled before another starts.
     *
     * Same reason as the dashboard's: two overlapping loads both run to
     * completion and whichever finishes *last* wins, so a rotate during a slow
     * profile fetch could leave the screen showing the older result.
     */
    private var loadJob: Job? = null

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val name = username?.takeUnless { it.isBlank() }

            // Neither half is worth showing alone: the header and the favourites
            // come from one, three of the Stats charts from the other, and a page
            // with a name and a "7 Watching" is more useful than either a spinner
            // or a half-populated screen.
            val overviewDeferred = async {
                if (isDemo) {
                    Result.success(aniListRepository.getDemoUserOverview())
                } else {
                    aniListRepository.getUserOverview(userId, name, forceRefresh)
                }
            }
            val listDeferred = async { fetchList(forceRefresh) }

            val overview = overviewDeferred.await().getOrElse { error ->
                // The list fetch is a child of this coroutine, so returning
                // without cancelling it would still block this coroutine on it
                // and then throw its result away. Cancel it instead: the profile
                // cannot be shown either way, and the list query is the
                // expensive half of the pair.
                listDeferred.cancel()
                _uiState.value = errorState(error, "Failed to load the AniList profile")
                return@launch
            }

            // If fetching the anime list fails (for instance on accounts with private lists
            // or temporary rate limits), gracefully build the profile with null list data
            // rather than failing the entire profile screen when the overview succeeded.
            val list = listDeferred.await().getOrNull()

            _uiState.value = buildState(overview, list, isRefreshing = false)
        }
    }

    private suspend fun fetchList(forceRefresh: Boolean): Result<MediaListCollection> = when {
        isDemo -> Result.success(aniListRepository.getDemoAnimeList())
        // Keyed on whichever identifier this screen was opened with, so the
        // dashboard's cached entry is the one that gets reused.
        userId != null -> aniListRepository.getUserAnimeList(userId, forceRefresh)
        else -> aniListRepository.getUserAnimeListByUsername(username.orEmpty(), forceRefresh)
    }

    fun refresh() {
        // Progress shown in place, as on the dashboard: blanking a profile back to
        // a spinner to show five more favourites would throw away the page someone
        // is reading to watch a loading indicator.
        (_uiState.value as? UserOverviewUiState.Success)?.let { current ->
            _uiState.value = current.copy(isRefreshing = true)
        }
        load(forceRefresh = true)
    }

    private fun buildState(
        overview: UserOverview,
        list: MediaListCollection?,
        isRefreshing: Boolean
    ): UserOverviewUiState.Success {
        val favourites = overview.favourites
        val animeStats = overview.statistics?.anime
        val mangaStats = overview.statistics?.manga
        // `stats` is the deprecated aggregate block. Everything below reads it
        // defensively: AniList omits a distribution entirely for an account that
        // has none, and a chart of nothing is better than a crash.
        val aggregate = overview.stats
        // Once. It walks every list entry, and this screen already holds the
        // dashboard's list in memory - calling it three times because three
        // charts need different slices of the answer would triple the walk for
        // no reason.
        val entries = buildListEntryInsights(list)

        return UserOverviewUiState.Success(
            userId = overview.id,
            username = overview.name,
            about = overview.about?.takeIf { it.isNotBlank() },
            siteUrl = overview.siteUrl,
            avatarUrl = overview.avatar?.large ?: overview.avatar?.medium,
            bannerUrl = overview.bannerImage,
            createdAt = overview.createdAt,
            updatedAt = overview.updatedAt,
            favouriteAnime = favourites?.anime?.nodes.orEmpty().distinctById(),
            favouriteManga = favourites?.manga?.nodes.orEmpty().distinctById(),
            favouriteCharacters = favourites?.characters?.nodes.orEmpty().distinctById(),
            favouriteStaff = favourites?.staff?.nodes.orEmpty().distinctById(),
            favouriteStudios = favourites?.studios?.nodes.orEmpty().distinctById(),
            insights = buildListInsights(
                animeStatusDistribution = aggregate?.animeStatusDistribution,
                animeScoreDistribution = aggregate?.animeScoreDistribution,
                favouredFormats = aggregate?.favouredFormats,
                favouredYears = aggregate?.favouredYears,
                favouredGenres = aggregate?.favouredGenres,
                favouredTags = aggregate?.favouredTags,
                countries = entries.countries,
                releaseYears = entries.releaseYears
            ),
            entryInsights = entries,
            activity = buildActivityCalendar(aggregate?.activityHistory),
            animeCount = animeStats?.count ?: 0,
            mangaCount = mangaStats?.count ?: 0,
            episodesWatched = animeStats?.episodesWatched ?: 0,
            minutesWatched = animeStats?.minutesWatched
                ?: aggregate?.watchedTime?.toLong()
                ?: 0L,
            meanScore = animeStats?.meanScore
                ?: aggregate?.animeListScores?.meanScore?.toDouble(),
            standardDeviation = aggregate?.animeListScores?.standardDeviation,
            isRefreshing = isRefreshing,
            isDemoMode = isDemo
        )
    }

    /**
     * A dead token is not a network blip: retrying the same request can never
     * succeed, so the screen needs a way back to the login screen rather than a
     * Retry button that fails identically every time.
     */
    private fun errorState(error: Throwable, fallback: String) = UserOverviewUiState.Error(
        message = error.message ?: fallback,
        isAuthError = (error as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION
    )

    class Factory(
        private val aniListRepository: AniListRepository,
        private val userId: Int? = null,
        private val username: String? = null,
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserOverviewViewModel(
            aniListRepository = aniListRepository,
            userId = userId,
            username = username,
            isDemo = isDemo
        ) as T
    }
}

/**
 * Drops repeated entries from a favourites connection.
 *
 * Not defensive coding against an impossible response - AniList returns them. A
 * real captured response for a user with six favourited anime contained the same
 * media six times, and another returned the same twelve twice. The `favourites`
 * connection is evidently not de-duplicated server-side.
 *
 * That is not a cosmetic problem. The favourites rows are `LazyRow`s keyed on the
 * media id, and a `LazyRow` handed the same key twice throws
 * `IllegalArgumentException: Key "79" was already used` while it is being laid
 * out - so one person's favourites would crash the screen, on a device, with no way
 * to see what went wrong. De-duplicating here also makes the rows honest: twelve
 * covers of six shows is not what anybody pinned.
 *
 * First occurrence wins, so the order AniList chose is preserved.
 *
 * `internal` rather than private so `FavouritesDeduplicationTest` can exercise the
 * real function. A test that re-implements the rule next to it proves the test,
 * not the screen.
 */
internal fun <T> List<T>.distinctById(): List<T> {
    val seen = HashSet<Int>()
    return filter { item ->
        val id = when (item) {
            is MediaNode -> item.id
            is FavouriteCharacter -> item.id
            is FavouriteStaff -> item.id
            is StudioNode -> item.id
            else -> null
        }
        id == null || seen.add(id)
    }
}