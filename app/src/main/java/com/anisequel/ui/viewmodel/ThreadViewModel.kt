package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.THREAD_COMMENT_PAGE_DEPTH_LIMIT
import com.anisequel.data.model.THREAD_COMMENTS_PER_PAGE
import com.anisequel.data.model.ThreadComment
import com.anisequel.data.model.ThreadNode
import com.anisequel.data.repository.AniListRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * One forum thread, read top to bottom inside the app.
 *
 * ## Why the body and the comments are two reads
 *
 * AniList has no single query that returns a thread and its comments: `Thread`
 * carries the post, `Page.threadComments` carries the replies. The post is read
 * first because its reply count decides where the comment half starts; the two
 * stay separate results so one failure does not erase the other half.
 *
 * ## Why the page opens on the last page
 *
 * Thread comments page in ascending id order, so page one is the *oldest* row.
 * This page is reached from a notification, not from a forum index: opening at
 * the start would leave the comment the reader tapped far below the screen. The
 * first read therefore starts on `ceil(replyCount / perPage)` - the newest rows -
 * draws them newest first, and Older walks backwards from there. The inbox's own
 * copy of the target comment is kept in [UiState.seedComment] and drawn first, so
 * an old notification is still readable even when its page is beyond the API's
 * depth limit; a target that does land in the fetched page is highlighted in place.
 *
 * An empty last page falls back to page one once: a thread whose reply count
 * counts a deleted tail can otherwise open on a page with no rows at all. The
 * page is also clamped to AniList's 5000-entry depth limit, which rejects the
 * true last page of a very old thread instead of returning its comments.
 */
class ThreadViewModel(
    private val repository: AniListRepository,
    private val threadId: Int,
    private val highlightCommentId: Int?,
    seedComment: ThreadComment? = null
) : ViewModel() {

    data class UiState(
        val thread: ThreadNode? = null,
        val comments: List<ThreadComment> = emptyList(),
        /** The inbox row's comment, shown even when the target page is unreachable. */
        val seedComment: ThreadComment? = null,
        val loading: Boolean = true,
        val loadingMore: Boolean = false,
        val hasMore: Boolean = false,
        val page: Int = 0,
        val error: String? = null,
        val threadError: String? = null,
        val commentsError: String? = null,
        val highlightCommentId: Int? = null
    )

    private val _state = MutableStateFlow(
        UiState(
            highlightCommentId = highlightCommentId,
            seedComment = seedComment
        )
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = _state.value.copy(
                loading = true,
                loadingMore = false,
                error = null,
                threadError = null,
                commentsError = null
            )
            val threadResult = repository.getThread(threadId)
            val threadNode = threadResult.getOrNull()
            val firstPage = firstPageFor(threadNode)
            var page = firstPage
            var commentsResult = repository.getThreadComments(threadId, page)
            if (firstPage > 1 &&
                commentsResult.isSuccess &&
                commentsResult.getOrNull()?.comments.isNullOrEmpty()
            ) {
                page = 1
                commentsResult = repository.getThreadComments(threadId, page)
            }

            val commentPage = commentsResult.getOrNull()
            val threadFailure = threadResult.exceptionOrNull()?.message
            val commentsFailure = commentsResult.exceptionOrNull()?.message
            _state.value = _state.value.copy(
                thread = threadNode,
                comments = commentPage?.comments.orEmpty()
                    .distinctBy { it.id }
                    .sortedByDescending { it.id },
                hasMore = page > 1,
                page = if (commentPage != null) page else 0,
                loading = false,
                threadError = threadFailure,
                commentsError = commentsFailure,
                error = if (threadNode == null && commentPage == null) {
                    threadFailure ?: commentsFailure ?: "That thread didn't load."
                } else {
                    null
                }
            )
        }
    }

    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return
        val olderPage = current.page - 1
        if (olderPage < 1) return
        viewModelScope.launch {
            _state.value = current.copy(loadingMore = true, commentsError = null)
            repository.getThreadComments(threadId, olderPage)
                .onSuccess { page ->
                    val merged = (_state.value.comments + page.comments)
                        .distinctBy { it.id }
                        .sortedByDescending { it.id }
                    _state.value = _state.value.copy(
                        comments = merged,
                        // A page with no rows ends the walk even when more page
                        // numbers exist: asking again would repeat the same empty
                        // answer behind a button that looked live.
                        hasMore = olderPage > 1 && page.comments.isNotEmpty(),
                        page = olderPage,
                        loadingMore = false
                    )
                }
                .onFailure { failure ->
                    _state.value = _state.value.copy(
                        loadingMore = false,
                        commentsError = failure.message ?: "Couldn't load more comments."
                    )
                }
        }
    }

    fun retryComments() {
        val current = _state.value
        if (current.comments.isEmpty()) {
            load()
            return
        }
        // Page one has no older page to retry. An error there with rows on
        // screen is already covered by the reload path above.
        if (current.page <= 1) return
        _state.value = current.copy(hasMore = true, commentsError = null)
        loadMore()
    }

    /** The newest page, or page one for a thread too short to page. */
    private fun firstPageFor(thread: ThreadNode?): Int {
        val replies = thread?.replyCount ?: return 1
        if (replies <= 0) return 1
        return ((replies + THREAD_COMMENTS_PER_PAGE - 1) / THREAD_COMMENTS_PER_PAGE)
            .coerceIn(1, THREAD_COMMENT_PAGE_DEPTH_LIMIT)
    }

    class Factory(
        private val repository: AniListRepository,
        private val threadId: Int,
        private val commentId: Int?,
        private val seedComment: ThreadComment? = null
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = ThreadViewModel(
            repository = repository,
            threadId = threadId,
            highlightCommentId = commentId,
            seedComment = seedComment
        ) as T
    }
}
