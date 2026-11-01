package com.anisequel.ui.viewmodel

import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.ActivityReplyWindow
import com.anisequel.data.repository.AniListRepository
import com.anisequel.domain.usecase.CommentWindow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which part of a comment thread is on screen, and how the rest is reached.
 *
 * Split out of [CommentThreadStore] because the two halves have different reasons
 * to change. Everything that *writes* - posting, liking, editing, deleting, the
 * manage dialog - changes when AniList's mutations change. Everything here changes
 * when the *read* changes: how a page is aimed, what a refresh merges, when Older
 * is offered. They share one thing, the thread's state, so it is handed in rather
 * than duplicated; both halves write it through the same flow and neither gets to
 * keep a private copy that could disagree with the other.
 *
 * ## The window, in one paragraph
 *
 * Chat view asks AniList for one page of replies at a time instead of the whole
 * thread - the same trick every browser chat view of this conversation uses, and
 * for the same reason: `Activity(id:){replies}` has no arguments, so a thread with
 * nine hundred comments arrives as nine hundred comments. The newest rows live on
 * the last page, which is computed from the thread's own reply count; Older walks
 * backwards from there; and a background check re-reads the last page only.
 *
 * [CommentWindow] holds the arithmetic, which is why none of this needs a device to
 * test.
 */
internal class CommentThreadWindow(
    private val repository: AniListRepository,
    private val scope: CoroutineScope,
    private val isDemo: Boolean,
    private val state: MutableStateFlow<CommentThreadStore.CommentsState?>
) {

    /**
     * Where the thread most recently closed sat, and its rows.
     *
     * One entry, deliberately: this is the "I dismissed the sheet by accident"
     * case, not an offline cache. Keeping every thread would grow for as long as
     * the app is open and would need an eviction rule that nothing else here
     * wants; keeping the last one costs one list and makes the reopen instant -
     * and the reopen has to know which page it was on, or a reader who walked
     * back three pages is jumped to the newest message and told to scroll again.
     */
    private var cachedActivityId: Int? = null
    private var cachedReplies: List<ActivityReply> = emptyList()
    private var cachedWindow = ThreadWindow()

    /** The window position of a thread: what the server counted, and what was fetched. */
    private data class ThreadWindow(
        val replyCount: Int = 0,
        val oldestPage: Int = 1,
        val shownRows: Int = CommentWindow.INITIAL_ROWS
    )

    /**
     * The fetch for the currently open thread.
     *
     * Cancelled rather than merely ignored on the next open. The activity-id
     * guard below discards a response for a *different* thread, but opening the
     * same thread twice - close and reopen in quick succession, which is what a
     * dismiss-by-accident looks like - left two requests racing, and the older
     * one could land last and overwrite the newer thread's replies (including a
     * comment posted in between).
     */
    private var openJob: Job? = null

    /**
     * Opens the comment thread for [activityId] and loads it.
     *
     * If this is the thread that was just closed, its comments are drawn
     * immediately and the fetch becomes a refresh:
     * [CommentThreadStore.CommentsState.isLoading] stays false,
     * [CommentThreadStore.CommentsState.isRefreshing] is true, and a failure
     * leaves the rows where they are with `loadError` set.
     *
     * ## The two modes, and why both exist
     *
     * [chatView] picks between the window and the whole thread. In chat view the
     * sheet asks for one page - the last one, aimed with [knownReplyCount], the
     * feed card's own reply count - and offers Older from there. Without it the
     * sheet asks `Activity(id:){replies}`, which is AniList's entire comment list
     * in one response. The window is the default because it is strictly less work
     * on the thread the app was slow on; the whole-thread read stays because a
     * reader who turns chat view off in Settings means it, and because it is the
     * only shape that answers "show me this conversation at once".
     *
     * Aim is a guess and is treated as one: if the count that comes back sits on a
     * different page than the one asked for, the request is repeated at the real
     * page. A feed fetched five minutes ago is normally a few comments short of the
     * thread as it now stands, and opening a chat at the second-newest message with
     * the newest one hidden behind a tap would be a bug the reader would blame on
     * the app rather than on their own stale feed.
     */
    fun open(activityId: Int, knownReplyCount: Int, chatView: Boolean) {
        openJob?.cancel()
        val isCached = cachedActivityId == activityId
        val seedReplies = if (isCached) cachedReplies else emptyList()
        val seedWindow = if (isCached) {
            cachedWindow
        } else {
            ThreadWindow(
                replyCount = knownReplyCount,
                oldestPage = CommentWindow.lastPage(knownReplyCount)
            )
        }
        val aimPage = if (isCached) {
            seedWindow.oldestPage
        } else {
            CommentWindow.lastPage(knownReplyCount)
        }
        state.value = CommentThreadStore.CommentsState(
            activityId = activityId,
            isLoading = seedReplies.isEmpty(),
            isRefreshing = seedReplies.isNotEmpty(),
            replies = seedReplies,
            isChatView = chatView,
            replyCount = seedWindow.replyCount,
            oldestPage = seedWindow.oldestPage,
            shownRows = if (chatView) seedWindow.shownRows else 0
        )
        openJob = scope.launch {
            if (isDemo) {
                // The demo has no session and nobody commented on a fixture, so the
                // answer is an empty thread either way - and the shape it arrives in
                // must not decide which branch draws.
                state.update { current ->
                    current?.takeIf { it.activityId == activityId }?.copy(
                        isLoading = false,
                        isRefreshing = false,
                        shownRows = if (chatView) CommentWindow.INITIAL_ROWS else 0
                    )
                }
                return@launch
            }
            if (chatView) loadWindow(activityId, aimPage, guessCount = knownReplyCount)
            else loadWholeThread(activityId)
        }
    }

    /** The whole thread in one response - the read [CommentWindow] exists to avoid. */
    private suspend fun loadWholeThread(activityId: Int) {
        val result = repositoryResult { repository.getActivityReplies(activityId) }
        val current = state.value?.takeIf { it.activityId == activityId } ?: return
        state.value = result.fold(
            onSuccess = { replies ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    replies = replies.distinctBy { it.id },
                    replyCount = replies.size,
                    shownRows = 0,
                    loadError = null
                )
            },
            onFailure = { error ->
                current.copy(
                    isLoading = false,
                    isRefreshing = false,
                    loadError = error.message ?: "Couldn't load comments."
                )
            }
        )
    }

    /**
     * Fetches one page of the thread and lands it, re-aiming once if the count
     * that came back disagrees with the page that was asked for.
     */
    private suspend fun loadWindow(
        activityId: Int,
        page: Int,
        guessCount: Int,
        asRefresh: Boolean = false
    ) {
        val result = repositoryResult {
            repository.getActivityReplyWindow(activityId, page, CommentWindow.PAGE_SIZE)
        }
        val current = state.value?.takeIf { it.activityId == activityId } ?: return
        result.fold(
            onSuccess = { window ->
                if (!asRefresh && CommentWindow.needsRetarget(guessCount, window.replyCount)) {
                    // The count says the newest replies are on another page. Fetch
                    // that one instead, and only that one: a second re-aim would be
                    // a loop around a thread that is being posted to while it is
                    // open, which is normal on a live thread.
                    loadWindow(
                        activityId = activityId,
                        page = CommentWindow.lastPage(window.replyCount),
                        guessCount = window.replyCount
                    )
                    return@fold
                }
                state.value = current.mergeWindow(window, isRefresh = asRefresh)
            },
            onFailure = { error ->
                val message = error.message ?: "Couldn't load comments."
                state.value = if (asRefresh) {
                    current.copy(
                        isRefreshing = false,
                        windowError = message,
                        lastCheckedAtMillis = System.currentTimeMillis()
                    )
                } else {
                    current.copy(
                        isLoading = false,
                        isRefreshing = false,
                        loadError = message
                    )
                }
            }
        )
    }

    /**
     * Merges a fetched page into the list.
     *
     * Ordering is the server's: replies arrive oldest-first, and the sheet draws
     * them that way with the newest at the bottom, like every other chat. A window
     * that overlaps what is already on screen replaces rather than appends, keyed
     * on the reply id - which is also what keeps the state's unique-id promise,
     * the one a `LazyColumn` key will throw on.
     */
    private fun CommentThreadStore.CommentsState.mergeWindow(
        window: ActivityReplyWindow,
        isRefresh: Boolean
    ): CommentThreadStore.CommentsState {
        val incoming = window.replies
        val oldestFetched = incoming.minOfOrNull { it.id }

        // Three different merges, because three different things arrived.
        //
        // A refresh of the newest page replaces everything from the oldest reply it
        // carried onward: that is what drops a deleted comment, picks up an edit,
        // and notices a like changed - all of which the userscript this imitates
        // discovered by re-reading the tail rather than trusting its own copy.
        //
        // An Older page is *prepended*. It can never replace: the rows below it are
        // the ones the reader is looking at, and a page of comments from an hour
        // earlier arriving on its own would leave the thread with a hole where the
        // present used to be.
        //
        // Anything else is the re-aim: the page that was aimed at with a stale count
        // is discarded, because it was never the newest page.
        val merged = when {
            incoming.isEmpty() && isRefresh -> replies
            isRefresh && oldestFetched != null ->
                replies.filter { it.id < oldestFetched } + incoming
            window.page < oldestPage -> incoming + replies
            else -> incoming
        }
        val unique = merged.distinctBy { it.id }
        val unseen = if (isRefresh) {
            unique.count { reply -> replies.none { it.id == reply.id } }
        } else {
            0
        }
        val grown = if (isRefresh && unseen > 0 && shownRows in 1 until replies.size) {
            // The reader had scrolled up into older rows. Taking in new comments
            // silently is the same as moving their place, so the window grows to hold
            // both what was on screen and what arrived.
            CommentWindow.grownRowCount(shownRows, unseen)
        } else if (!isRefresh && window.page < oldestPage) {
            shownRows + incoming.size
        } else {
            shownRows
        }
        return copy(
            isLoading = false,
            isRefreshing = false,
            replies = unique,
            replyCount = window.replyCount,
            oldestPage = when {
                window.page < oldestPage -> window.page
                isRefresh -> oldestPage
                else -> window.page
            },
            shownRows = grown,
            unseenNewComments = if (isRefresh) unseen else unseenNewComments,
            lastCheckedAtMillis = System.currentTimeMillis(),
            windowError = null,
            loadError = if (isRefresh) loadError else null
        )
    }

    /**
     * Shows the page of comments older than the oldest one on screen.
     *
     * Rows already fetched but not drawn are revealed first, without a request:
     * the reader pressing Older twice should not pay twice for the same page.
     */
    fun loadOlder() {
        val current = state.value ?: return
        if (current.isLoadingOlder || !current.isChatView) return
        if (current.shownRows in 1 until current.replies.size) {
            state.value = current.copy(
                shownRows = (current.shownRows + CommentWindow.PAGE_SIZE)
                    .coerceAtMost(current.replies.size)
            )
            return
        }
        val page = CommentWindow.olderPage(current.oldestPage) ?: return
        if (!CommentWindow.hasOlder(page, current.replyCount, current.replies.size)) return
        state.value = current.copy(isLoadingOlder = true, windowError = null)
        scope.launch {
            val result = repositoryResult {
                repository.getActivityReplyWindow(current.activityId, page, CommentWindow.PAGE_SIZE)
            }
            val latest = state.value?.takeIf { it.activityId == current.activityId } ?: return@launch
            result.fold(
                onSuccess = { window ->
                    // The merge already grew the window by what it prepended, so
                    // this only clears the busy flag: adding the count again here is
                    // how a second Older press showed rows the fetch had never made.
                    val merged = latest.mergeWindow(window, isRefresh = false)
                    state.value = merged.copy(isLoadingOlder = false)
                },
                onFailure = { error ->
                    state.value = latest.copy(
                        isLoadingOlder = false,
                        windowError = error.message ?: "Couldn't load older comments."
                    )
                }
            )
        }
    }

    /**
     * Checks the newest page again for comments that arrived since it was drawn.
     *
     * Called on a timer by the sheet while it is visible, and once when the app
     * comes back to the foreground. [atBottom] decides what happens to what is
     * found: a reader parked at the newest message is shown the new rows as they
     * land, and one who is reading further up gets
     * [CommentThreadStore.CommentsState.unseenNewComments] instead of having their
     * place taken.
     */
    fun checkForNewComments(atBottom: Boolean) {
        val current = state.value ?: return
        if (isDemo || !current.isChatView || current.isLoading || current.isRefreshing) return
        scope.launch {
            loadWindow(
                activityId = current.activityId,
                page = CommentWindow.lastPage(current.replyCount),
                guessCount = current.replyCount,
                asRefresh = true
            )
            if (atBottom) {
                state.update { latest ->
                    latest?.takeIf { it.activityId == current.activityId }
                        ?.copy(unseenNewComments = 0)
                }
            }
        }
    }

    /** Brings the comments that arrived into view and clears the pending count. */
    fun revealNewComments() {
        val current = state.value ?: return
        if (current.unseenNewComments == 0) return
        state.value = current.copy(unseenNewComments = 0)
    }

    /** Records what the open thread was showing, called as the sheet closes. */
    fun capture(closed: CommentThreadStore.CommentsState) {
        cachedActivityId = closed.activityId
        cachedReplies = closed.replies
        cachedWindow = ThreadWindow(closed.replyCount, closed.oldestPage, closed.shownRows)
    }

    companion object {
        /**
         * How often an open, visible sheet asks whether the thread has new
         * comments.
         *
         * Fifteen seconds is the interval the browser chat views settled on, and it
         * is roughly the right compromise here: long enough that a reader who leaves
         * a thread open for ten minutes spends forty seconds of AniList's
         * ~30-requests-a-minute budget on it rather than three hundred, short
         * enough that a reply to the reader's own comment shows up while the
         * conversation is still on screen.
         */
        const val POLL_INTERVAL_MILLIS = 15_000L
    }
}
