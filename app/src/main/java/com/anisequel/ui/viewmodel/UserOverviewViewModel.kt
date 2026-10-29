package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.AnimeStats
import com.anisequel.data.model.FavouriteCharacter
import com.anisequel.data.model.FavouriteStaff
import com.anisequel.data.model.FormatAmount
import com.anisequel.data.model.GenreAmount
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.TagAmount
import com.anisequel.data.model.UserOverview
import com.anisequel.data.model.YearAmount
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.BuildActivityCalendarUseCase
import com.anisequel.domain.usecase.BuildListEntryInsightsUseCase
import com.anisequel.domain.usecase.BuildListInsightsUseCase
import com.anisequel.domain.usecase.fetchProfileListEagerly
import com.anisequel.domain.usecase.mangaLibraryProblem
import com.anisequel.domain.usecase.profileIdentityFromRoute
import com.anisequel.ui.components.markdown.mediaCoverKeysIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.math.roundToInt


/**
 * State holder for the AniList profile screen's Home and Stats tabs - and for the
 * Library page, which used to be the profile's fourth tab and now hangs off the
 * app's main bar.
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
 * ## Why the manga half of the Library page is not fetched here
 *
 * The anime list is forced on every *own-profile* open for the Stats tallies above,
 * and for the main bar's Library page, which renders `libraryAnime` with nothing to
 * ask for it. On somebody else's profile it is now on demand like the manga half:
 * the reader may be there for their bio, and a stranger's list costs up to forty
 * chunked requests. [loadAnimeLibrary] is what the Stats and list tabs call, and
 * [fetchProfileListEagerly] is the rule that decides which profiles skip the wait.
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
    /**
     * Whether this is the signed-in reader's own profile.
     *
     * It decides whether the chunked anime list is fetched with the profile or only
     * when a tab asks for it: see [fetchProfileListEagerly]. Defaulted to `true`
     * because that is the answer every existing caller wants - the viewer's own list
     * is rendered by the main bar's Library page, which has no trigger of its own, so
     * a ViewModel that forgot this would leave that page empty rather than slow.
     */
    private val isOwnProfile: Boolean = true,
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
     * The list half, asked for on its own.
     *
     * Only for somebody else's profile: [load] cancels the whole job, so the list
     * needs its own handle or a tab switch would cancel a download the reader is
     * already waiting on. [loadedOverview] and [loadedList] are what lets the answer
     * be merged into the page that is already published - `buildState` needs both
     * halves, and the state the screen holds has the overview only in its printed
     * form.
     */
    private var listJob: Job? = null
    private var loadedOverview: UserOverview? = null
    private var loadedList: MediaListCollection? = null

    /**
     * The id the profile document answered with, for a profile opened *by name*.
     *
     * Only the manga half needs it: `getUserMangaList` is addressable by id alone,
     * while the list and overview this screen fetches can both go by name (and do, so
     * they reuse the `userName:` cache entry the dashboard filled - see
     * [ProfileDestination]). Without this the borrowed list's Manga filter on a scanned
     * profile had a reason it could never outgrow: the id had arrived with the
     * overview and been thrown away, so the error read "hasn't said who this profile
     * is yet" for a profile that had said it some time ago.
     *
     * Deliberately *not* fed back into [fetchList] or [lastKnownListForScreen]: those
     * key the list by whichever identifier the route arrived with, and switching
     * halfway would re-download the most expensive query in the app under a second
     * key for a page that already has the answer under the first.
     */
    private var scannedUserId: Int? = null

    /** Whether the chunked list rides along with the profile open. See [fetchProfileListEagerly]. */
    private val fetchListEagerly = fetchProfileListEagerly(isOwnProfile = isOwnProfile, isDemo = isDemo)

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
     * The manga half of the Library page, loaded only on demand.
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

    /**
     * Why the manga list could not be read, or null when it was.
     *
     * The anime half has carried `libraryError` in the published state since a
     * failed fetch was flattened into "no list"; this is the same distinction
     * for the half that is fetched on demand. Without it a rate limit or a
     * private list left `mangaLibrary` null and the Library page drew its empty
     * state - "Nothing in this list yet" - over a list nobody had read.
     *
     * Cleared on entry and on success, so a retry that works also clears the
     * message from the previous attempt.
     */
    private val _mangaError = MutableStateFlow<String?>(null)
    val mangaError: StateFlow<String?> = _mangaError.asStateFlow()

    private var mangaJob: Job? = null

    /**
     * Record the id the overview answered with, for a profile opened by name.
     *
     * Called by the screen beside the Activity and Social tabs' identical calls, for
     * the same reason: those flows are addressable by id only, and a scanned profile
     * arrives with a name. So is [loadMangaLibrary]'s list - which is the only thing
     * this feeds. A route that already had an id needs none of it.
     */
    fun updateUserId(newUserId: Int) {
        if (userId != null || newUserId <= 0) return
        scannedUserId = newUserId
    }

    /**
     * Fetches the manga list, once.
     *
     * Guarded rather than idempotent because the Library page calls it from a
     * filter chip that a tapper will hit repeatedly: a re-entry that refetched
     * would spend a request per tap, against an API AniList currently holds at
     * 30 requests a minute, and the repository's cache would have prevented the
     * network call one layer too late to matter.
     */
    fun loadMangaLibrary() {
        if (_mangaLibrary.value != null || _isLoadingManga.value) return
        // Named rather than silent. The `return` this replaces left the Manga filter
        // of the Library page on a list that was never arriving: no entries, no
        // error, no reason - which is the same "nothing here" lie as a flattened
        // failure, only slower to notice, and the reader had no way to tell it apart
        // from an account that tracks no manga.
        val id = userId ?: scannedUserId
        mangaLibraryProblem(isDemo = isDemo, userId = id)?.let { problem ->
            _mangaError.value = problem
            return
        }
        // Unreachable beside the guard above, and written anyway: everything between
        // here and the launch flips the in-flight flag, and returning after that with
        // nothing left to clear it is how a page ends up spinning for an answer that
        // was never asked for.
        if (id == null) return

        _isLoadingManga.value = true
        _mangaError.value = null
        mangaJob = viewModelScope.launch {
            val result = aniListRepository.getUserMangaList(id, false)
            result.fold(
                onSuccess = { collection ->
                    _mangaLibrary.value = flattenLibrary(collection)
                    _mangaError.value = null
                },
                // Left null on failure so the chip is still a retry: a person who
                // hit a rate limit needs the tap to be able to ask again, and a
                // failure is not the same as an empty list. The reason is kept
                // beside the null rather than swallowed, so the page can say what
                // happened instead of drawing an empty grid.
                onFailure = { error ->
                    _mangaError.value = error.message ?: "AniList didn't return this list."
                }
            )
            _isLoadingManga.value = false
        }
    }

    /**
     * Ask for the anime list, on a profile that did not fetch it with the overview.
     *
     * Guarded the same way [loadMangaLibrary] is, for the same reason times forty:
     * this is the app's single most expensive query - one request per 500 entries,
     * chunk-merged and sequential - and the tabs that need it are recomposed on every
     * scroll, tab switch and rotation. A re-entry that refetched would spend a whole
     * download per tap against an API AniList holds at 30 requests a minute.
     */
    fun loadAnimeLibrary(forceRefresh: Boolean = false) {
        // The viewer's own list (and the demo's fixture) arrives with [load], so a
        // call here would race the fetch already in flight and double it. The tabs
        // can therefore ask unconditionally, on whichever profile they are on.
        if (fetchListEagerly) return
        if (listJob?.isActive == true) return
        if (loadedList != null && !forceRefresh) return
        // Without the overview there is nothing to rebuild the published state from:
        // `buildState` takes the profile document, not the printed form the state
        // holds. The tabs only compose this call from a loaded profile, so reaching it
        // means the page is not ready, and asking later is the right answer.
        val overview = loadedOverview ?: return

        listJob = viewModelScope.launch {
            (_uiState.value as? UserOverviewUiState.Success)?.let { current ->
                _uiState.value = current.copy(isLibraryLoading = true, libraryError = null)
            }
            val result = fetchList(forceRefresh)
            val list = result.getOrNull() ?: loadedList
                ?: if (isDemo) null else lastKnownListForScreen()
            loadedList = list
            _uiState.value = buildState(
                overview = overview,
                list = list,
                isRefreshing = false,
                isLibraryLoading = false,
                libraryError = if (list == null) {
                    result.exceptionOrNull()?.message ?: "AniList didn't return this list."
                } else {
                    null
                },
                // Live list, live profile: only a list that had to be taken from
                // memory or disk makes the page a saved copy.
                isCached = result.getOrNull() == null && list != null
            )
        }
    }

    init {
        load()
    }

    fun load(forceRefresh: Boolean = false) {
        loadJob?.cancel()
        // Who this screen is about, decided once from the two route arguments.
        //
        // The by-id route used to read its argument as `getInt("userId") ?: 0`, so an
        // unfilled route asked AniList for *user 0* and painted whichever account owns
        // that id as the person the reader tapped: a real name, a real bio, no
        // connection to the face in the grid. A profile with neither a name nor an id
        // is not a slow load, it is nobody, and it is not retryable - the same request
        // would be made again, and again answer the same way.
        val identity = profileIdentityFromRoute(userId, username)
        if (!isDemo && !identity.isResolvable) {
            _uiState.value = UserOverviewUiState.Error(
                message = identity.problem ?: "Failed to load the AniList profile",
                canRetry = false
            )
            return
        }
        loadJob = viewModelScope.launch {
            val name = username?.takeUnless { it.isBlank() }

            // Paint the saved copy before the first request leaves.
            //
            // This is the profile and Library half of the dashboard's cold-start
            // paint. Without it a cold start went straight to `Loading`, and a
            // failed request went straight to the failure page - even though the
            // bio, the favourites and the whole list were sitting in the snapshot
            // directory. Not on a manual refresh (`forceRefresh`): the reader
            // asked for a new answer, not for the one they are already reading.
            var paintedList: MediaListCollection? = null
            if (!isDemo && !forceRefresh) {
                val saved = aniListRepository.lastKnownOverview(userId, name)
                if (saved != null) {
                    paintedList = lastKnownListForScreen()
                    loadedOverview = saved
                    loadedList = paintedList
                    _uiState.value = buildState(
                        overview = saved,
                        list = paintedList,
                        isRefreshing = false,
                        isLibraryLoading = paintedList == null,
                        isCached = true
                    )
                    loadMediaCovers(saved.about)
                }
            }

            // One request for the page, and the expensive half beside it.
            //
            // The two used to be awaited together, on the argument that a page
            // with a name and a "7 Watching" is worse than a spinner. That
            // reasoning cost the reader the whole chunk-merged list download
            // before seeing anything: the overview is one request, the list is one
            // per 500 entries, and only the Stats tab reads it for the profile - the
            // Library page is the other reader of the same list. The overview is
            // published on its own below and the list is merged in when it lands, so
            // the bio and the favourites are on screen while the tallies are still
            // being counted. `isLibraryLoading` is what keeps the two readers from
            // showing zeroes as if they were answers.
            val overviewDeferred = async {
                if (isDemo) {
                    Result.success(aniListRepository.getDemoUserOverview())
                } else {
                    aniListRepository.getUserOverview(userId, name, forceRefresh)
                }
            }
            // Somebody else's list is only asked for once it is wanted - see
            // [fetchProfileListEagerly] for the failure that made this a question
            // rather than a freebie. `loadedList != null` is the other half: once the
            // reader has looked at this person's list, a refresh has to bring it
            // forward too, or the page would go out to the network for the bio and
            // leave a list behind it that nobody had asked about since.
            val listDeferred =
                if (fetchListEagerly || loadedList != null) async { fetchList(forceRefresh) } else null

            val overview = overviewDeferred.await().getOrElse { error ->
                // The list fetch is a child of this coroutine, so returning
                // without cancelling it would still block this coroutine on it
                // and then throw its result away. Cancel it instead: the profile
                // cannot be shown either way, and the list query is the
                // expensive half of the pair.
                listDeferred?.cancel()
                val cached = _uiState.value as? UserOverviewUiState.Success
                // A dead token is not something a saved copy may hide: the profile
                // would keep claiming a session that AniList has already refused,
                // and the "Sign in again" exit is on the error state.
                val isDeadSession = (error as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION
                if (cached != null && !isDeadSession) {
                    // The saved copy is on screen and belongs to this person. Keep
                    // it, marked as such, instead of replacing a readable profile
                    // with a failure page. The flag is preserved rather than forced:
                    // a refresh of a live page that fails leaves a live page behind,
                    // and calling that "saved" would be wrong in the other
                    // direction.
                    //
                    // The library half is cancelled with the overview, so if the
                    // painted copy had no list the "still loading" flag it was given
                    // has nobody left to clear it. Left set, the Stats tab and the
                    // Library page spin forever over a profile that has finished
                    // loading; the honest state is "no list, and here is why".
                    _uiState.value = cached.copy(
                        isRefreshing = false,
                        // In lazy mode there was no fetch to cancel, so a list that
                        // has not been asked for yet must stay "not asked for yet".
                        // Flipping it to a failure here would report a list that
                        // nobody requested as one AniList refused.
                        isLibraryLoading = cached.isLibraryLoading && listDeferred == null,
                        libraryError = cached.libraryError
                            ?: if (cached.isLibraryLoading && listDeferred != null) {
                                "AniList didn't return this list."
                            } else {
                                null
                            }
                    )
                } else {
                    _uiState.value = errorState(error, "Failed to load the AniList profile")
                }
                return@launch
            }

            // Published before the list is awaited, and deliberately so: see the
            // note above. `isLibraryLoading` is the flag that stops the Stats tab and
            // the Library page from rendering "0 completed" for the length of the
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
            loadedOverview = overview
            if (!alreadyShowing) {
                // In lazy mode this is not a transient: the list is what it is
                // until a tab asks, so the state it publishes with has to be the
                // one that survives (`isLibraryLoading` reading "not asked yet").
                val listSoFar = if (listDeferred == null) loadedList else null
                _uiState.value = buildState(
                    overview = overview,
                    list = listSoFar,
                    isRefreshing = false,
                    // Either on its way, or not asked for yet: both mean the same
                    // thing to the two readers - the tallies they would print are
                    // not final, and `isLibraryLoading` is what stops zeroes being
                    // presented as answers.
                    isLibraryLoading = listSoFar == null
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
            // list" were different answers, and the Library page then rendered
            // "Nothing in this list yet" over an account with nine hundred entries,
            // with every chip reading (0) and no way to try again.
            val listResult = listDeferred?.await()
            val liveList = listResult?.getOrNull()
            // A failed list fetch falls back to the last one this device has, on
            // the same terms as the profile itself. The bio and the favourites are
            // already live by here, so the alternative was a page that is half
            // today and half empty - with every chart reading zero.
            val list = liveList ?: paintedList ?: loadedList
                ?: if (isDemo) null else lastKnownListForScreen()
            val usedSavedList = liveList == null && list != null
            loadedList = list

            _uiState.value = buildState(
                overview = overview,
                list = list,
                isRefreshing = false,
                // Nothing was asked for and nothing is on disk: that is "not asked
                // yet", which the tabs show as *counting* rather than as zeroes -
                // and it is cleared by [loadAnimeLibrary], the call the tab makes.
                isLibraryLoading = list == null && listResult == null,
                libraryError = when {
                    list != null -> null
                    // A fetch that was asked for and failed is the only case with a
                    // reason to print. When nobody asked, `isLibraryLoading` is the
                    // honest flag and inventing a failure here would blame AniList
                    // for a request it never saw.
                    listResult != null ->
                        listResult.exceptionOrNull()?.message ?: "AniList didn't return this list."
                    else -> null
                },
                // The overview itself is live here, so the page is only cached
                // when the list half had to be given up on.
                isCached = usedSavedList
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

    /**
     * The last list this device holds for whoever this screen is showing.
     *
     * Same identifier the fetch would use, so the signed-in profile reads the same
     * `user:<id>` entry the dashboard filled. Null in demo mode and for a profile
     * opened with neither id nor name - there is nothing to look up.
     */
    private suspend fun lastKnownListForScreen(): MediaListCollection? = when {
        isDemo -> null
        userId != null -> aniListRepository.lastKnownAnimeList(userId)
        else -> username?.takeIf { it.isNotBlank() }
            ?.let { aniListRepository.lastKnownAnimeListByUsername(it) }
    }

    private fun buildState(
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

    /**
     * A dead token is not a network blip: retrying the same request can never
     * succeed, so the screen needs a way back to the login screen rather than a
     * Retry button that fails identically every time.
     */
    private fun errorState(error: Throwable, fallback: String) = UserOverviewUiState.Error(
        message = error.message ?: fallback,
        isAuthError = (error as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION,
        kind = (error as? AniListException)?.kind
    )

    class Factory(
        private val aniListRepository: AniListRepository,
        private val userId: Int? = null,
        private val username: String? = null,
        private val isDemo: Boolean = false,
        private val isOwnProfile: Boolean = true
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserOverviewViewModel(
            aniListRepository = aniListRepository,
            userId = userId,
            username = username,
            isDemo = isDemo,
            isOwnProfile = isOwnProfile
        ) as T
    }
}
