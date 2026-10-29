package com.anisequel.ui.viewmodel

import com.anisequel.data.model.AnimeStats
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.UserOverview
import com.anisequel.domain.usecase.BuildActivityCalendarUseCase
import com.anisequel.domain.usecase.BuildListEntryInsightsUseCase
import com.anisequel.domain.usecase.BuildListInsightsUseCase

/**
 * Turns the profile answer and the list into the page state.
 *
 * It was a private method on [UserOverviewViewModel], and it is the one part of
 * that class that needs no lifecycle: it reads one profile answer and one list,
 * writes nothing, and returns a value. Keeping it there put seventy lines of
 * field-by-field assignment between the load pipeline and the factory, which is
 * where the loader's own branching stopped being readable.
 *
 * The three chart use cases live here rather than on the ViewModel because the
 * mapping is what they are for - they turn distributions into the charts the
 * tabs draw - and nothing else in the load pipeline calls them.
 */
internal class UserOverviewStateMapper(
    private val isDemo: Boolean,
    private val buildActivityCalendar: BuildActivityCalendarUseCase,
    private val buildListInsights: BuildListInsightsUseCase,
    private val buildListEntryInsights: BuildListEntryInsightsUseCase
) {
    fun buildState(
        overview: UserOverview,
        list: MediaListCollection?,
        isRefreshing: Boolean,
        isLibraryLoading: Boolean = false,
        /** Null on the way in; see [UserOverviewUiState.Success.libraryError]. */
        libraryError: String? = null,
        isCached: Boolean = false
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
                // These four come from `statistics.anime`, not from the deprecated
                // `stats` block: requesting `stats.favouredFormats` (and its three
                // siblings) makes AniList answer HTTP 500 for the entire profile
                // document, which is what the profile and Library pages were
                // showing as "Ani-chan is having a nap". See [AnimeStats].
                favouredFormats = animeStats?.formatAmounts(),
                favouredYears = animeStats?.releaseYearAmounts(),
                favouredGenres = animeStats?.genreAmounts(),
                favouredTags = animeStats?.tagAmounts(),
                countries = entries.countries,
                releaseYears = entries.releaseYears
            ),
            entryInsights = entries,
            activity = buildActivityCalendar(aggregate?.activityHistory),
            libraryAnime = flattenLibrary(list),
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
            isLibraryLoading = isLibraryLoading,
            libraryError = libraryError,
            isCached = isCached,
            isDemoMode = isDemo
        )
    }
}
