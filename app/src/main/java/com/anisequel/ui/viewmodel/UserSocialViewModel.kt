package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.FollowUser
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.SocialChipAction
import com.anisequel.domain.usecase.shouldStartSocialFetch
import com.anisequel.domain.usecase.socialChipAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Which side of the social graph the Social tab is showing. */
enum class SocialList { FOLLOWERS, FOLLOWING }

/**
 * State holder for the Social tab: the people who follow this account, and the
 * people it follows.
 *
 * ## Tapping a person loads *their* AniList, not this one's
 *
 * That is the whole point of the tab, so the identity of whoever was tapped is
 * carried all the way out rather than being re-derived from screen state: the
 * screen's `onOpenUser` callback is handed the tapped [FollowUser], navigation
 * pushes it as a route argument, and the next `UserOverviewViewModel` is built
 * with that person's id and name. Nothing in the chain falls back to "the
 * signed-in viewer", which is the bug that would make every card in the grid open
 * the same profile - a grid of fifty faces all leading to your own page.
 */
class UserSocialViewModel(
    private val aniListRepository: AniListRepository,
    private var userId: Int?,
    private val isDemo: Boolean = false
) : ViewModel() {

    /**
     * ## Why `Success` carries a refresh flag and a *failed* list
     *
     * Both exist because this state holds two lists at once and only ever shows
     * one of them.
     *
     * [isRefreshing] is what lets a re-fetch of rows that are already in memory
     * keep drawing them. Publishing [Loading] over a [Success] blanked a fifty-
     * person grid into skeletons to show the same fifty people a second later, and
     * it read as though the data had been lost.
     *
     * [failedList] is what lets a failure be attributed to the list it happened
     * to. The other list is prefetched whether the user asked for it or not, and
     * when that prefetch failed the whole tab was replaced by an error screen for
     * a list nobody was looking at - the grid they were reading went with it. Now
     * the failure is recorded beside the rows that survived, and [select] uses it
     * to decide when a chip tap has to re-ask rather than switch.
     */
    sealed interface SocialState {
        data object Idle : SocialState
        data class Loading(val list: SocialList) : SocialState
        data class Success(
            val selected: SocialList,
            val followers: List<FollowUser>,
            val following: List<FollowUser>,
            val isRefreshing: Boolean = false,
            /** The list that could not be read while the one on screen could. */
            val failedList: SocialList? = null,
            val failureMessage: String? = null
        ) : SocialState {
            fun peopleFor(list: SocialList): List<FollowUser> =
                if (list == SocialList.FOLLOWERS) followers else following

            /** True when a list has rows on screen, which is what a re-fetch may keep. */
            fun hasRowsFor(list: SocialList): Boolean = peopleFor(list).isNotEmpty()
        }
        data class Error(
            val message: String,
            val list: SocialList,
            val isAuthError: Boolean = false
        ) : SocialState
    }

    private val _state = MutableStateFlow<SocialState>(SocialState.Idle)
    val state: StateFlow<SocialState> = _state.asStateFlow()

    private var loadJob: Job? = null

    /**
     * Updates the user id once resolved from a profile overview scan.
     *
     * ## Why `Idle` does not fetch
     *
     * `Idle` is this flow's initial state, so treating it as "go and get it" meant
     * every scanned profile spent two requests - followers *and* following, because
     * [load] fetches the other list alongside - asking who follows somebody the
     * reader had not decided to look at yet.
     *
     * The KDoc on [load] says this tab is "fetched on demand" and that "opening a
     * profile and looking at your favourites should not have spent two requests
     * asking who follows you". The tab calls [load] itself, so nothing is lost by
     * waiting - and `SocialList.FOLLOWERS` is the tab's own default, which is what
     * this used to fall back to.
     *
     * `Error` and `Loading` still reload, because those are states where a fetch
     * was genuinely wanted.
     */
    fun updateUserId(newUserId: Int) {
        if (this.userId == newUserId) return
        this.userId = newUserId
        val current = _state.value
        if (current is SocialState.Error || current is SocialState.Loading) {
            // No `else` branch: the guard above narrows `current` to those two
            // states, so a fallback to `SocialList.FOLLOWERS` could never run.
            // The compiler reported it on every build ("'when' is exhaustive so
            // 'else' is redundant here"); removing it means the day someone
            // widens the guard, this stops compiling instead of quietly fetching
            // a different list than the one the user is looking at.
            val selected = when (current) {
                is SocialState.Error -> current.list
                is SocialState.Loading -> current.list
            }
            load(selected, forceRefresh = true)
        }
    }

    /**
     * Fetch the list the user picked, once each.
     *
     * Fetched on demand rather than in `init` for the same reason as the activity
     * feed, and for one more: this is the one tab that fans out. Opening a
     * profile and looking at your favourites should not have spent two requests
     * asking who follows you.
     *
     * The *other* list is fetched alongside it. `Page` accepts one data field, so
     * they are two round trips - but the user is one tap from switching between
     * them, and prefetching on that tap turns a spinner into an instant switch at
     * the cost of one request that a tab nobody opens is never charged for.
     */
    fun load(selected: SocialList, forceRefresh: Boolean = false) {
        if (userId == null && !isDemo) {
            // `Loading` here was a spinner with no way out. The only thing that
            // ever clears it is the answer to this very request, and this branch
            // returns before making one, so a slow or failed profile overview left
            // the tab spinning forever: `updateUserId` re-fires only from a state
            // this screen is no longer in by the time the id lands, and the tab's
            // own trigger only spoke for `Idle`. An `Error` is the same sentence
            // the reader can act on - the retry button reaches it, and
            // [updateUserId] reloads from it, which is the recovery path when the
            // overview finally answers with the id.
            _state.value = SocialState.Error(
                message = "This profile hasn't finished loading yet. Try again in a moment.",
                list = selected
            )
            return
        }

        val current = _state.value as? SocialState.Success
        if (current != null && !forceRefresh && current.failedList != selected) {
            if (current.selected != selected) {
                _state.value = current.copy(selected = selected)
            }
            return
        }

        loadJob?.cancel()
        // Rows already in memory are kept on screen behind a refresh marker. Only
        // the state that publishes them is chosen here, before the job starts, so
        // a cancelled job cannot leave a `Loading` that has nothing feeding it -
        // which is the other half of the forever-spinner this method used to have.
        _state.value = if (current != null &&
            (current.hasRowsFor(SocialList.FOLLOWERS) || current.hasRowsFor(SocialList.FOLLOWING))
        ) {
            current.copy(selected = selected, isRefreshing = true)
        } else {
            SocialState.Loading(selected)
        }
        val previous = current

        loadJob = viewModelScope.launch {
            val followersDeferred = async { fetchFollowers(forceRefresh) }
            val followingDeferred = async { fetchFollowing(forceRefresh) }

            val followersResult = followersDeferred.await()
            val followingResult = followingDeferred.await()

            // Whichever list the reader is *now* looking at, which a chip tap during
            // the fetch may have changed from the one this job started for.
            val shown = when (val live = _state.value) {
                is SocialState.Success -> live.selected
                is SocialState.Loading -> live.list
                is SocialState.Error -> live.list
                SocialState.Idle -> selected
            }
            val shownResult = if (shown == SocialList.FOLLOWERS) followersResult else followingResult
            val shownFailure = shownResult.exceptionOrNull()
            if (shownFailure != null) {
                _state.value = SocialState.Error(
                    message = shownFailure.message ?: "Couldn't load this social list.",
                    list = shown,
                    isAuthError =
                    (shownFailure as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION
                )
                return@launch
            }

            val failedList = when {
                followersResult.isFailure -> SocialList.FOLLOWERS
                followingResult.isFailure -> SocialList.FOLLOWING
                else -> null
            }
            val failureMessage = followersResult.exceptionOrNull()?.message
                ?: followingResult.exceptionOrNull()?.message
            _state.value = SocialState.Success(
                selected = shown,
                // A list that failed keeps whatever rows were already in memory
                // rather than reporting them as an empty list, and `failedList`
                // says that the emptiness - or the staleness - is not an answer.
                followers = followersResult.getOrNull() ?: previous?.followers ?: emptyList(),
                following = followingResult.getOrNull() ?: previous?.following ?: emptyList(),
                isRefreshing = false,
                failedList = failedList,
                failureMessage = failureMessage
            )
        }
    }

    /**
     * Start the tab's fetch if nothing is producing one, and otherwise do nothing.
     *
     * This is the guarded form of [load] that the tab's entry trigger calls. The
     * trigger used to be a `LaunchedEffect(Unit)` that only spoke when the state
     * was exactly `Idle`, so a re-entry after the job was cancelled - a tab switch,
     * a process-state restore, or the id-null `Loading` - never asked again. Because
     * the effect now re-runs on every state change, the guard is what keeps this to
     * one request per visit rather than one per recomposition: rows on screen and a
     * live job both answer "nothing to start here", and a failure stays behind its
     * own retry button rather than being re-asked on entry.
     */
    fun ensureLoaded(list: SocialList) {
        val current = _state.value
        val rows = (current as? SocialState.Success)
            ?.let { it.hasRowsFor(SocialList.FOLLOWERS) || it.hasRowsFor(SocialList.FOLLOWING) }
            ?: false
        if (
            shouldStartSocialFetch(
                hasSomeoneToFetch = userId != null || isDemo,
                hasRowsOnScreen = rows,
                isFetchInFlight = loadJob?.isActive == true,
                isShowingFailure = current is SocialState.Error
            )
        ) {
            load(list)
        }
    }

    /**
     * The Followers/Following chip.
     *
     * What a tap costs depends on what the tab already holds, which is what
     * [socialChipAction] decides; see it for why a tap during a fetch must not
     * restart it.
     */
    fun select(list: SocialList) {
        val current = _state.value
        val success = current as? SocialState.Success
        when (
            socialChipAction(
                hasRows = success?.hasRowsFor(list) ?: false,
                fetchInFlight = loadJob?.isActive == true,
                isFailedList = success?.failedList == list
            )
        ) {
            SocialChipAction.Show ->
                if (success != null && success.selected != list) {
                    _state.value = success.copy(selected = list)
                }

            SocialChipAction.Retarget ->
                // The spinner now stands for the list the reader is asking for. The
                // job that is running answers both, and publishes whichever of them
                // this state names when it lands.
                //
                // `Success` is the other half of this branch, and the one that was
                // missing: a refresh of an account that has nothing in one of the two
                // lists is an in-flight job over a state with no rows to switch to,
                // and moving only the `Loading` spinner left that chip answering the
                // tap with nothing at all until the job happened to finish.
                when (current) {
                    is SocialState.Loading ->
                        if (current.list != list) _state.value = current.copy(list = list)

                    is SocialState.Success ->
                        if (current.selected != list) _state.value = current.copy(selected = list)

                    else -> Unit
                }

            SocialChipAction.Fetch -> load(list)
        }
    }

    fun retry() {
        val selected = when (val current = _state.value) {
            is SocialState.Error -> current.list
            is SocialState.Loading -> current.list
            is SocialState.Success -> current.selected
            SocialState.Idle -> SocialList.FOLLOWERS
        }
        load(selected, forceRefresh = true)
    }

    private suspend fun fetchFollowers(forceRefresh: Boolean): Result<List<FollowUser>> =
        if (isDemo) {
            Result.success(aniListRepository.getDemoFollowers())
        } else {
            aniListRepository.getUserFollowers(userId!!, forceRefresh)
        }

    private suspend fun fetchFollowing(forceRefresh: Boolean): Result<List<FollowUser>> =
        if (isDemo) {
            Result.success(aniListRepository.getDemoFollowing())
        } else {
            aniListRepository.getUserFollowing(userId!!, forceRefresh)
        }

    class Factory(
        private val aniListRepository: AniListRepository,
        private val userId: Int? = null,
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = UserSocialViewModel(
            aniListRepository = aniListRepository,
            userId = userId,
            isDemo = isDemo
        ) as T
    }
}