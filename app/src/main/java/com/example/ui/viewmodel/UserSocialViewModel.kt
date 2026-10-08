package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.model.FollowUser
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.repository.AniListRepository
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
 * screen hands [openUser]'s caller the tapped [FollowUser], navigation pushes it
 * as a route argument, and the next `UserOverviewViewModel` is built with that
 * person's id and name. Nothing in the chain falls back to "the signed-in viewer",
 * which is the bug that would make every card in the grid open the same profile -
 * a grid of fifty faces all leading to your own page.
 */
class UserSocialViewModel(
    private val aniListRepository: AniListRepository,
    private var userId: Int?,
    private val isDemo: Boolean = false
) : ViewModel() {

    sealed interface SocialState {
        data object Idle : SocialState
        data class Loading(val list: SocialList) : SocialState
        data class Success(
            val selected: SocialList,
            val followers: List<FollowUser>,
            val following: List<FollowUser>
        ) : SocialState {
            fun peopleFor(list: SocialList): List<FollowUser> =
                if (list == SocialList.FOLLOWERS) followers else following
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
     */
    fun updateUserId(newUserId: Int) {
        if (this.userId == newUserId) return
        this.userId = newUserId
        val current = _state.value
        if (current is SocialState.Error || current is SocialState.Idle || current is SocialState.Loading) {
            val selected = when (current) {
                is SocialState.Error -> current.list
                is SocialState.Loading -> current.list
                else -> SocialList.FOLLOWERS
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
            _state.value = SocialState.Loading(selected)
            return
        }

        val current = _state.value as? SocialState.Success
        if (current != null && !forceRefresh) {
            if (current.selected != selected) {
                _state.value = current.copy(selected = selected)
            }
            return
        }

        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = SocialState.Loading(selected)

            val followersDeferred = async { fetchFollowers(forceRefresh) }
            val followingDeferred = async { fetchFollowing(forceRefresh) }

            val followers = followersDeferred.await()
            val following = followingDeferred.await()

            val failure = followers.exceptionOrNull() ?: following.exceptionOrNull()
            if (failure != null) {
                _state.value = SocialState.Error(
                    message = failure.message ?: "Couldn't load this social list.",
                    list = selected,
                    isAuthError = (failure as? AniListException)?.kind == AniListErrorKind.INVALID_SESSION
                )
                return@launch
            }

            _state.value = SocialState.Success(
                selected = selected,
                followers = followers.getOrDefault(emptyList()),
                following = following.getOrDefault(emptyList())
            )
        }
    }

    fun select(list: SocialList) {
        val current = _state.value as? SocialState.Success
        if (current != null) {
            if (current.selected != list) {
                _state.value = current.copy(selected = list)
            }
        } else {
            load(list)
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