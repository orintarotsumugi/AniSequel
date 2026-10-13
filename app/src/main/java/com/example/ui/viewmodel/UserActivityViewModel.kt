package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.ActivityReply
import com.example.data.model.FollowUser
import com.example.data.model.ListActivity
import com.example.data.model.MediaNode
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.repository.AniListRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * State holder for the Activity tab: somebody's list history, newest first.
 *
 * ## Why it does not load until it is asked
 *
 * The feed is a second and third request that a person who opened their profile
 * to look at their favourites does not need, and it is the only thing on this
 * screen that can be *long* - twenty-five entries per page, page after page. It
 * is fetched when the Activity tab is selected rather than in `init`, which is
 * the same rule the repository's caches exist to support: a profile the user
 * glanced at must not have spent AniList's request budget on a scrollback.
 *
 * ## Pagination
 *
 * [loadNextPage] exists and the screen calls it as the list nears its end. It is
 * one page per call rather than a loop, because the alternative is a screen that
 * silently pulls twenty pages while nobody is watching.
 */
class UserActivityViewModel(
    private val aniListRepository: AniListRepository,
    /**
     * Whose activity this is.
     *
     * Required, and nullable-safe: a feed is only addressable by id - AniList has
     * no "activity by username" - so a profile opened without one can show the
     * favourites but cannot show a feed, and says so rather than loading the
     * wrong person's.
     */
    private var userId: Int?,
    private val isDemo: Boolean = false,
    private val pageSize: Int = 25
) : ViewModel() {

    sealed interface ActivityState {
        /** Not fetched yet - the tab has not been opened, or there is no id. */
        data object Idle : ActivityState
        data class Loading(val message: String = "Loading recent activity...") : ActivityState
        data class Success(
            val activities: List<ListActivity>,
            val isLoadingMore: Boolean = false,
            val hasMore: Boolean = false,
            /**
             * Activity ids whose like request is still unanswered.
             *
             * In the state rather than in a mutable set beside it so the card can
             * disable its own button on the frame the tap lands: two taps before
             * the first response would toggle twice, and AniList would be right to
             * end up back where it started while the UI showed one change.
             */
            val pendingLikes: Set<Int> = emptySet(),
            /**
             * Why the *next page* failed, or null when it has not.
             *
             * This exists because a failed page used to leave no trace at all. The
             * append branch kept the rows it had, cleared the footer spinner, and
             * discarded the error - and pagination is driven by
             * `LaunchedEffect(shouldPage)`, where `shouldPage` is a `derivedStateOf`
             * over scroll geometry. A reader parked at the bottom of the feed leaves
             * it `true`, so the effect never re-fired: one offline blip or one 429
             * ended the feed for the rest of the session, with `hasMore` still true
             * and nothing on screen saying why. Recovering needed scrolling seven
             * rows away and back.
             *
             * The tab turns this into a retry row, so the way back is a tap rather
             * than a scroll nobody would think to make.
             */
            val loadMoreError: String? = null
        ) : ActivityState
        data class Error(val message: String, val isAuthError: Boolean = false) : ActivityState
    }

    /**
     * The comments sheet's state.
     *
     * Null when the sheet is closed, and non-null when it is open - the sheet's
     * visibility is derived from this rather than held beside it, so there is no
     * frame where a sheet is up with no thread under it.
     *
     * [activityId] is part of the state so a slow response for a thread the user
     * already closed and replaced with another one is discarded rather than
     * painted over the new thread.
     */
    data class CommentsState(
        val activityId: Int,
        val isLoading: Boolean = false,
        val replies: List<ActivityReply> = emptyList(),
        val loadError: String? = null,
        val isPosting: Boolean = false,
        val postError: String? = null
    )

    /**
     * The likers sheet's state.
     *
     * The same shape as [CommentsState] and nullable for the same reason: the
     * sheet's visibility is derived from the state, so there is no frame where it
     * is up with nothing under it. [activityId] is part of the state so a slow
     * response for a sheet the user already closed and replaced with another
     * activity's is discarded rather than painted over the new one.
     *
     * There is no posting side here - a like is toggled from the card - so unlike
     * [CommentsState] there is no in-flight or error-writing field.
     */
    data class LikersState(
        val activityId: Int,
        val isLoading: Boolean = false,
        val likers: List<FollowUser> = emptyList(),
        val loadError: String? = null
    )

    private val _state = MutableStateFlow<ActivityState>(ActivityState.Idle)
    val state: StateFlow<ActivityState> = _state.asStateFlow()

    private val _comments = MutableStateFlow<CommentsState?>(null)
    val comments: StateFlow<CommentsState?> = _comments.asStateFlow()

    private val _likers = MutableStateFlow<LikersState?>(null)
    val likers: StateFlow<LikersState?> = _likers.asStateFlow()

    /**
     * Detail fetched on demand for the entry an activity row opens.
     *
     * Shares [MediaDetailStore] with the dashboard: an activity row's own media
     * node is a thin summary - title, cover, format, episodes - and the sheet's
     * query is what fills in the synopsis, ratings and tags.
     */
    private val detailStore = MediaDetailStore(
        repository = aniListRepository,
        scope = viewModelScope,
        isDemo = isDemo
    )

    val detailNodes: StateFlow<Map<Int, MediaNode>> get() = detailStore.details
    val loadingDetailIds: StateFlow<Set<Int>> get() = detailStore.loading

    /** Fetches the synopsis, banner and studio for one entry. */
    fun loadDetail(mediaId: Int) {
        detailStore.load(mediaId)
    }

    private var loadJob: Job? = null
    private var nextPage = 1
    private var started = false

    /**
     * Updates the user id once resolved from a profile overview scan.
     *
     * ## Why this does not fetch
     *
     * It used to treat `Idle` as "go and get it", and `Idle` is this flow's
     * *initial* state - so the id arriving from the profile overview started a
     * fetch immediately, as soon as the header was on screen and before the reader
     * had touched the Activity tab. On the one route where the id is scanned rather
     * than known up front - a profile opened by name - that spent a request per
     * profile the reader merely glanced at.
     *
     * The KDoc on [loadFirstPage] says the feed is fetched when the tab is selected
     * precisely so that "a profile the user glanced at must not have spent AniList's
     * request budget on a scrollback". The tab calls `loadFirstPage` itself, so
     * dropping the fetch here costs the reader nothing and the API one request
     * they did not ask for.
     *
     * `Error` and `Loading` are still reloaded: those are states where a fetch was
     * genuinely wanted - a retry, or one that started before the id was known.
     */
    fun updateUserId(newUserId: Int) {
        if (this.userId == newUserId) return
        this.userId = newUserId
        val current = _state.value
        if (current is ActivityState.Error || current is ActivityState.Loading) {
            started = false
            loadFirstPage(forceRefresh = true)
        }
    }

    /**
     * Fetch the first page, once.
     *
     * Guarded rather than idempotent because the Activity tab is re-entered every
     * time the user taps it, and a re-entry that refetched would spend a request
     * to show the identical list - which is also what the repository's cache would
     * prevent, one layer too late to matter for the UI.
     */
    fun loadFirstPage(forceRefresh: Boolean = false) {
        if (started && !forceRefresh) return
        if (userId == null && !isDemo) {
            _state.value = ActivityState.Loading("Loading recent activity...")
            return
        }
        started = true
        nextPage = 1
        fetch(forceRefresh)
    }

    fun loadNextPage() {
        val current = _state.value as? ActivityState.Success ?: return
        if (!current.hasMore || current.isLoadingMore) return
        fetch(forceRefresh = false, appending = true)
    }

    fun retry() {
        started = false
        nextPage = 1
        fetch(forceRefresh = true)
    }

    private fun fetch(forceRefresh: Boolean, appending: Boolean = false) {
        val currentId = userId
        if (currentId == null && !isDemo) {
            _state.value = ActivityState.Error(
                "AniList does not serve activity by name, and this profile was opened without an id."
            )
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            if (appending) {
                (_state.value as? ActivityState.Success)?.let {
                    _state.value = it.copy(isLoadingMore = true)
                }
            } else {
                _state.value = ActivityState.Loading()
            }

            val page = nextPage
            val result = if (isDemo) {
                Result.success(aniListRepository.getDemoUserActivity())
            } else {
                aniListRepository.getUserActivity(currentId!!, page, forceRefresh)
            }

            result.fold(
                onSuccess = { activities ->
                    nextPage = page + 1
                    val existing = (_state.value as? ActivityState.Success)?.activities.orEmpty()
                    // De-duplicated on the id because pages overlap whenever a new
                    // update lands between two requests - the list is ordered by
                    // update time, so a row fetched on both pages appears twice and
                    // a `LazyColumn` keyed on it would throw.
                    val combined = if (appending) existing + activities else activities
                    _state.value = ActivityState.Success(
                        activities = combined.distinctBy { it.id },
                        isLoadingMore = false,
                        // Cleared on success, so a retry that works does not leave
                        // the failure row sitting under a feed that is growing.
                        loadMoreError = null,
                        // A short page means the end. One short page is the only
                        // signal AniList gives here - `Page.pageInfo` is not asked
                        // for, because for a union-typed field the parse cost is
                        // not worth a boolean this derives more reliably.
                        hasMore = activities.size >= pageSize,
                        // Carried over so a page landing mid-like does not re-enable
                        // a button whose request is still in flight.
                        pendingLikes = (_state.value as? ActivityState.Success)?.pendingLikes.orEmpty()
                    )
                },
                onFailure = { error ->
                    if (appending) {
                        // A failed *extra* page must not wipe the pages already
                        // read. It used to replace the whole state with an error
                        // screen, so one transient failure while scrolling threw
                        // away twenty-five loaded rows and showed a retry button
                        // where the feed had been. Keep the feed and clear the
                        // footer spinner - but *record* the failure, because
                        // discarding it left pagination with no way back: see
                        // [ActivityState.Success.loadMoreError].
                        (_state.value as? ActivityState.Success)?.let { current ->
                            _state.value = current.copy(
                                isLoadingMore = false,
                                loadMoreError = error.message
                                    ?: "Couldn't load more activity."
                            )
                        }
                    } else {
                        _state.value = ActivityState.Error(
                            message = error.message ?: "Couldn't load recent activity.",
                            isAuthError = (error as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION
                        )
                    }
                }
            )
        }
    }

    /**
     * Likes an activity if it is not liked, un-likes it if it is.
     *
     * Optimistic: the heart and the count move on the tap, and the server's
     * answer - which is the truth about the new state, not just an ack - settles
     * them. A failure puts the original row back, because the optimistic count was
     * a guess and the server just contradicted it.
     *
     * Refuses when `isLiked` is null (AniList did not answer it, which is what
     * happens with no session) and while a request for the same activity is still
     * in flight, so a double tap cannot toggle twice.
     */
    fun toggleLike(activityId: Int) {
        if (isDemo) return
        val current = _state.value as? ActivityState.Success ?: return
        if (activityId in current.pendingLikes) return
        val activity = current.activities.firstOrNull { it.id == activityId } ?: return
        val wasLiked = activity.isLiked ?: return

        val optimistic = activity.copy(
            isLiked = !wasLiked,
            likeCount = ((activity.likeCount ?: 0) + if (wasLiked) -1 else 1).coerceAtLeast(0)
        )
        replaceActivity(optimistic, addPending = activityId)

        viewModelScope.launch {
            aniListRepository.toggleActivityLike(activityId).fold(
                onSuccess = { result ->
                    replaceActivity(
                        optimistic.copy(isLiked = result.isLiked, likeCount = result.likeCount),
                        removePending = activityId
                    )
                },
                onFailure = {
                    replaceActivity(activity, removePending = activityId)
                }
            )
        }
    }

    /** Opens the comment thread for [activityId] and loads it. */
    fun openComments(activityId: Int) {
        _comments.value = CommentsState(activityId = activityId, isLoading = true)
        viewModelScope.launch {
            val result = if (isDemo) {
                Result.success(emptyList())
            } else {
                aniListRepository.getActivityReplies(activityId)
            }
            // Discard a response for a thread the user has already closed or
            // replaced: the state is only written if it is still this thread.
            val current = _comments.value?.takeIf { it.activityId == activityId } ?: return@launch
            result.fold(
                onSuccess = { replies ->
                    _comments.value = current.copy(isLoading = false, replies = replies, loadError = null)
                },
                onFailure = { error ->
                    _comments.value = current.copy(
                        isLoading = false,
                        loadError = error.message ?: "Couldn't load comments."
                    )
                }
            )
        }
    }

    fun closeComments() {
        _comments.value = null
    }

    /**
     * Opens the likers sheet for [activityId] and loads who liked it.
     *
     * A failure is swallowed into the sheet's error line rather than the feed's
     * error screen: reading who liked something is a read, and a read that does
     * not arrive must not throw away the twenty-five cards already on screen.
     * The demo feed answers with an empty list, the same way [openComments] does,
     * because it has no session and nobody actually liked the fixture.
     */
    fun openLikers(activityId: Int) {
        _likers.value = LikersState(activityId = activityId, isLoading = true)
        viewModelScope.launch {
            val result = if (isDemo) {
                Result.success(emptyList())
            } else {
                aniListRepository.getActivityLikes(activityId)
            }
            // Discard a response for a sheet the user has already closed or
            // replaced: the state is only written if it is still this activity.
            val current = _likers.value?.takeIf { it.activityId == activityId } ?: return@launch
            result.fold(
                onSuccess = { likers ->
                    _likers.value = current.copy(isLoading = false, likers = likers, loadError = null)
                },
                onFailure = { error ->
                    _likers.value = current.copy(
                        isLoading = false,
                        loadError = error.message ?: "Couldn't load likes."
                    )
                }
            )
        }
    }

    fun dismissLikers() {
        _likers.value = null
    }

    /** Posts [text] as a comment on the open thread. */
    fun postComment(text: String) {
        if (isDemo) return
        val current = _comments.value ?: return
        val trimmed = text.trim()
        if (trimmed.isEmpty() || current.isPosting) return

        val activityId = current.activityId
        _comments.value = current.copy(isPosting = true, postError = null)

        viewModelScope.launch {
            aniListRepository.postActivityReply(activityId, trimmed).fold(
                onSuccess = { reply ->
                    val latest = _comments.value?.takeIf { it.activityId == activityId } ?: return@fold
                    _comments.value = latest.copy(
                        isPosting = false,
                        postError = null,
                        replies = latest.replies + reply
                    )
                    bumpReplyCount(activityId)
                },
                onFailure = { error ->
                    _comments.value = _comments.value
                        ?.takeIf { it.activityId == activityId }
                        ?.copy(isPosting = false, postError = error.message ?: "Couldn't post that comment.")
                }
            )
        }
    }

    /** Rewrites the one activity in the loaded page, and its in-flight marker. */
    private fun replaceActivity(
        activity: ListActivity,
        addPending: Int? = null,
        removePending: Int? = null
    ) {
        val current = _state.value as? ActivityState.Success ?: return
        _state.value = current.copy(
            activities = current.activities.map { if (it.id == activity.id) activity else it },
            pendingLikes = current.pendingLikes
                .let { if (addPending != null) it + addPending else it }
                .let { if (removePending != null) it - removePending else it }
        )
    }

    /** Keeps the feed's reply count honest after a comment is posted. */
    private fun bumpReplyCount(activityId: Int) {
        val current = _state.value as? ActivityState.Success ?: return
        _state.value = current.copy(
            activities = current.activities.map {
                if (it.id == activityId) it.copy(replyCount = (it.replyCount ?: 0) + 1) else it
            }
        )
    }

    class Factory(
        private val aniListRepository: AniListRepository,
        private val userId: Int? = null,
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserActivityViewModel(
            aniListRepository = aniListRepository,
            userId = userId,
            isDemo = isDemo
        ) as T
    }
}