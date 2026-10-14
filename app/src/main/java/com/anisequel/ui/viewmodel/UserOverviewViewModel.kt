package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.FavouriteCharacter
import com.anisequel.data.model.FavouriteStaff
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.UserOverview
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.BuildActivityCalendarUseCase
import com.anisequel.domain.usecase.BuildListEntryInsightsUseCase
import com.anisequel.domain.usecase.BuildListInsightsUseCase
import com.anisequel.ui.components.markdown.mediaCoverKeysIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Every list entry on a collection, flattened and de-duplicated by media id.
 *
 * AniList returns a list per *status group*, and a custom list can overlap
 * the standard ones, so the same anime can arrive more than once - across
 * *different* groups too. The Library tab is a `LazyColumn` keyed on the media
 * id, and a repeated key throws `IllegalArgumentException` while it lays out,
 * so this is a crash guard rather than tidying.
 */
private fun flattenLibrary(collection: MediaListCollection?): List<MediaListEntryItem> =
    collection?.lists
        ?.flatMap { it.entries.orEmpty() }
        ?.distinctBy { it.media.id }
        .orEmpty()

/**
 * State holder for the AniList profile screen's Home, Stats and Library tabs.
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
 *
 * ## Why the manga half of the Library tab is not fetched here
 *
 * The anime list is forced on every profile open for the Stats tallies above. The
 * manga list is needed by none of that, and AniList is currently degraded to 30
 * requests per minute, so it is fetched on demand - the first time somebody
 * filters the Library tab to manga - and then cached by the repository like every
 * other list.
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

    /**
     * Cover art for the AniList preview cards in this person's bio.
     *
     * Media id to URL, fetched as **one** batch after the profile loads rather
     * than per card. A "hall of fame" bio carries a dozen AniList links, and a
     * request each would spend a dozen of AniList's 30-a-minute budget before the
     * reader had scrolled - then the next screen they opened would be handed a
     * 429.
     *
     * Failure is silent by design: a missing cover leaves the card as it was
     * before covers existed, and that is a far better outcome than an error
     * banner over somebody's bio.
     */
    private val _mediaCovers = MutableStateFlow<Map<MediaCoverKey, MediaCover>>(emptyMap())
    val mediaCovers: StateFlow<Map<MediaCoverKey, MediaCover>> = _mediaCovers.asStateFlow()

    /**
     * Asks for the cover of every media this bio links to, in one request.
     *
     * Called as soon as the *overview* has been published, and deliberately not
     * awaited by it: the reader should see the bio immediately and have posters
     * arrive into the cards afterwards.
     *
     * ## Why it cannot wait for the list
     *
     * It used to be called after `listDeferred.await()`, which is the app's single
     * most expensive query - chunked, one request per 500 entries, sequentially.
     * Every card in a "hall of fame" bio therefore showed its placeholder icon for
     * the whole duration of that download and then popped a poster in, on a large
     * account after ten sequential requests of blanks. The comment above the call
     * said the opposite of what the code did, which is how it survived.
     *
     * ## Why the state is cleared even when there is nothing to fetch
     *
     * Both early returns below happen *before* the previous `coverJob` was
     * cancelled, and nothing else reset `_mediaCovers`. So a refresh after the
     * author removed their AniList links left the old map published, and an
     * in-flight request could still land and overwrite it. `coverJob` is a child
     * of `viewModelScope` rather than of `loadJob`, so cancelling a reload does
     * not stop it either. Clearing first makes the state honest: whatever is
     * published belongs to the bio currently on screen.
     */
    private fun loadMediaCovers(about: String?) {
        coverJob?.cancel()
        _mediaCovers.value = emptyMap()

        val bio = about?.takeIf { it.isNotBlank() } ?: return
        val keys = mediaCoverKeysIn(bio)
        if (keys.isEmpty()) return

        coverJob = viewModelScope.launch {
            aniListRepository.getMediaCovers(keys).onSuccess { covers ->
                _mediaCovers.value = covers
            }
        }
    }

    private var coverJob: Job? = null

    /**
     * The manga half of the Library tab, loaded only on demand.
     *
     * Null until [loadMangaLibrary] has been called and it has not failed; an
     * empty list afterwards is a real answer ("this person tracks no manga") and
     * is deliberately different from null, so the tab can tell "not asked yet"
     * from "asked, and there is none".
     */
    private val _mangaLibrary = MutableStateFlow<List<MediaListEntryItem>?>(null)
    val mangaLibrary: StateFlow<List<MediaListEntryItem>?> = _mangaLibrary.asStateFlow()

    private val _isLoadingManga = MutableStateFlow(false)
    val isLoadingManga: StateFlow<Boolean> = _isLoadingManga.asStateFlow()

    private var mangaJob: Job? = null

    /**
     * Fetches the manga list, once.
     *
     * Guarded rather than idempotent because the Library tab calls it from a
     * filter chip that a tapper will hit repeatedly: a re-entry that refetched
     * would spend a request per tap, against an API AniList currently holds at
     * 30 requests a minute, and the repository's cache would have prevented the
     * network call one layer too late to matter.
     */
    fun loadMangaLibrary() {
        if (_mangaLibrary.value != null || _isLoadingManga.value) return
        if (isDemo || userId == null) return

        _isLoadingManga.value = true
        mangaJob = viewModelScope.launch {
            val result = aniListRepository.getUserMangaList(userId, false)
            result.fold(
                onSuccess = { collection ->
                    _mangaLibrary.value = flattenLibrary(collection)
                },
                // Left null on failure so the chip is still a retry: a person who
                // hit a rate limit needs the tap to be able to ask again, and a
                // failure is not the same as an empty list.
                onFailure = { }
            )
            _isLoadingManga.value = false
        }
    }

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val name = username?.takeUnless { it.isBlank() }

            // One request for the page, and the expensive half beside it.
            //
            // The two used to be awaited together, on the argument that a page
            // with a name and a "7 Watching" is worse than a spinner. That
            // reasoning cost the reader the whole chunk-merged list download
            // before seeing anything: the overview is one request, the list is one
            // per 500 entries, and only two of the profile's five tabs read it.
            // The overview is published on its own below and the list is merged in
            // when it lands, so the bio and the favourites are on screen while the
            // tallies are still being counted. `isLibraryLoading` is what keeps the
            // two tabs that need the list from showing zeroes as if they were
            // answers.
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

            // Published before the list is awaited, and deliberately so: see the
            // note above. `isLibraryLoading` is the flag that stops the Stats and
            // Library tabs from rendering "0 completed" for the length of the
            // download - an empty answer and an unasked question are different, and
            // the screen has to be able to tell them apart.
            //
            // Only when there is nothing to replace, though. On a *refresh* this
            // page is already painted, and publishing the list-less state over it
            // would empty the Library grid and blank every entry-derived chart for
            // the length of the download - trading a slow first paint for a slow
            // every refresh. The previous tallies are seconds old and the refresh
            // bar is already saying the page is being replaced.
            val alreadyShowing = (_uiState.value as? UserOverviewUiState.Success)?.userId == overview.id
            if (!alreadyShowing) {
                _uiState.value = buildState(
                    overview = overview,
                    list = null,
                    isRefreshing = false,
                    isLibraryLoading = true
                )
            }

            // Here rather than after the list is awaited, so the bio's posters
            // are fetched in parallel with the chunked list download instead of
            // queued behind the length of it. See [loadMediaCovers]: a card that
            // shows its placeholder for the whole download and then fills in is
            // the bug this position avoids.
            loadMediaCovers(overview.about)

            // If fetching the anime list fails (for instance on accounts with private lists
            // or temporary rate limits), gracefully build the profile with null list data
            // rather than failing the entire profile screen when the overview succeeded.
            //
            // The *reason* is carried through rather than discarded. `getOrNull()`
            // threw away the only evidence that "no list" and "could not read the
            // list" were different answers, and the Library tab then rendered
            // "Nothing in this list yet" over an account with nine hundred entries,
            // with every chip reading (0) and no way to try again.
            val listResult = listDeferred.await()
            val list = listResult.getOrNull()

            _uiState.value = buildState(
                overview = overview,
                list = list,
                isRefreshing = false,
                isLibraryLoading = false,
                libraryError = if (list == null) {
                    listResult.exceptionOrNull()?.message
                        ?: "AniList didn't return this list."
                } else {
                    null
                }
            )
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
        isRefreshing: Boolean,
        isLibraryLoading: Boolean = false,
        /** Null on the way in; see [UserOverviewUiState.Success.libraryError]. */
        libraryError: String? = null
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