package com.anisequel.domain.usecase

/**
 * Where the newest comments of a thread live, and how to walk back from them.
 *
 * ## Why the window is calculated instead of asked for
 *
 * AniList returns a thread's replies oldest-first and paginates them by that
 * order, so there is no "give me the last 25" argument on the API - the newest
 * replies are simply on the last page, and the last page number is
 * `ceil(replyCount / perPage)`. That makes the thread's own count a load-bearing
 * number rather than a label: the sheet cannot ask for anything until it knows
 * how many replies there are.
 *
 * It usually knows before it asks. The feed row that opened the sheet already
 * carries `replyCount`, so the first request is aimed with that guess and the
 * answer's count is checked against it with [needsRetarget]. A stale count is the
 * normal case, not the error case - the feed was fetched minutes ago and other
 * people have been posting since - so a mismatch means one more request at the
 * real last page, exactly as a reader would expect a chat to open at the bottom.
 *
 * ## What this does not do
 *
 * Nothing here fetches, caches, or knows about Android. It is arithmetic on
 * counts and page numbers, which is why it is testable without a socket and why
 * [com.anisequel.data.repository.AniListRepository] can stay a transport.
 */
object CommentWindow {

    /**
     * Replies per request.
     *
     * AniList's own ceiling for a `Page` is 50; half of it is used because a
     * comment can be a wall of markdown, and a page that is fetched to be read a
     * screen at a time should also parse in one frame.
     */
    const val PAGE_SIZE = 25

    /**
     * How many replies the sheet shows before the reader asks for Older.
     *
     * Equal to [PAGE_SIZE] so the first screen is exactly one request; a smaller
     * number would leave fetched rows invisible, which is the one thing a
     * "load only the newest" view is not supposed to do.
     */
    const val INITIAL_ROWS = PAGE_SIZE

    /** The page holding the newest [replyCount] replies; page 1 when there are none. */
    fun lastPage(replyCount: Int, perPage: Int = PAGE_SIZE): Int {
        if (replyCount <= 0 || perPage <= 0) return 1
        return (replyCount - 1) / perPage + 1
    }

    /**
     * Whether the aimed request has to be repeated at the real last page.
     *
     * True only when the two counts land on *different* pages: a feed that says
     * 30 and a server that says 28 both put the newest replies on page 2, so the
     * rows already fetched are the right rows and re-fetching would be a second
     * request for the same screen.
     */
    fun needsRetarget(
        guessedReplyCount: Int,
        realReplyCount: Int,
        perPage: Int = PAGE_SIZE
    ): Boolean = lastPage(guessedReplyCount, perPage) != lastPage(realReplyCount, perPage)

    /** The page just older than [page], or null when page 1 is already loaded. */
    fun olderPage(page: Int): Int? = if (page <= 1) null else page - 1

    /**
     * Whether Older can fetch anything more.
     *
     * A thread whose server count disagrees with the rows in hand is trusted to
     * the rows: an empty last page at a stale high count is what a thread looks
     * like after its newest comments were deleted, and offering Older there would
     * fetch a page that has nothing on it.
     */
    fun hasOlder(page: Int, replyCount: Int, loadedCount: Int, perPage: Int = PAGE_SIZE): Boolean =
        olderPage(page) != null && loadedCount < replyCount

    /** How many replies exist that the sheet has not fetched, for the Older label. */
    fun hiddenCount(replyCount: Int, loadedCount: Int): Int =
        (replyCount - loadedCount).coerceAtLeast(0)

    /**
     * How many of the fetched rows the thread draws.
     *
     * The window is a number of rows taken from the newest end, so the rows on
     * screen are the last [shownRows] of [repliesLoaded] - unless everything is
     * revealed, or chat view is off and [shownRows] is 0 for the whole list.
     *
     * It is asked here rather than at each call site because two places answer it
     * and they have to agree: the list slices its rows with it, and the scroll that
     * follows a comment this session just posted counts the Older row with it,
     * because that row sits above the list and moves every index below it. Asked
     * once from `replies.size` and once from the slice, the two disagreed on a
     * thread the reader had scrolled up in, and the scroll stopped on the comment
     * above the one they had written.
     */
    fun visibleRowCount(repliesLoaded: Int, shownRows: Int): Int =
        if (shownRows in 1 until repliesLoaded) shownRows else repliesLoaded

    /**
     * How many rows a refresh may push out of sight.
     *
     * New replies are appended, so a reader parked halfway up a thread loses the
     * row they were reading to the bottom of the list. The sheet therefore grows
     * its own window by exactly the number of rows a refresh added - and only for
     * a reader who had already scrolled up: one who never scrolled is at the
     * newest end, where new rows belong.
     */
    fun grownRowCount(shownRows: Int, addedRows: Int): Int = shownRows + addedRows.coerceAtLeast(0)

    /**
     * How many fetched rows are *not* drawn, and so sit above the top of the list.
     *
     * [visibleRowCount] answers how many rows the sheet draws; this is the other half
     * of the same division, and the half a scroll has to know: a reply's position in
     * the drawn list is its position in the fetched list minus this prefix. Counting
     * from the fetched list instead was the bug that made the scroll after a post
     * land on the newest row rather than on the reader's own - on a thread they had
     * scrolled up in, the two lists are not the same length, and the index simply
     * pointed past the end, where `animateScrollToItem` quietly clamps it.
     */
    fun hiddenPrefixRows(repliesLoaded: Int, shownRows: Int): Int =
        repliesLoaded - visibleRowCount(repliesLoaded, shownRows)

    /**
     * How many rows landed **above** a list that grew, or 0 when it did not grow that way.
     *
     * Two things grow the thread upwards: an Older page, and a window that reveals
     * rows it had already fetched. Either way the rows the reader was looking at are
     * still there, now further down, and a `LazyColumn` that is not reversed keeps
     * `firstVisibleItemIndex` where it was - so the conversation slides backwards
     * under the reader's eyes and the comment they were reading leaves the screen.
     * That is what this count is for: add it to the anchor's new index and the place
     * is kept.
     *
     * Returns 0 for anything that is not a clean prepend - the first fill of an empty
     * list (the open-at-newest anchor owns that one), a refresh that replaced rows at
     * the newest end, a delete, or a different thread entirely. Only a list that
     * *ends* with the old one is the old list with rows above it, and guessing
     * otherwise is how a scroll fights a refresh.
     */
    fun prependedRows(previousIds: List<Int>, currentIds: List<Int>): Int {
        if (previousIds.isEmpty()) return 0
        val added = currentIds.size - previousIds.size
        if (added <= 0) return 0
        return if (currentIds.takeLast(previousIds.size) == previousIds) added else 0
    }

    /**
     * The row a reply occupies in the drawn thread.
     *
     * The list is not just replies: a message thread draws the message above them, a
     * failed request draws a banner, and chat view draws the Older row - each of them
     * pushes every reply down by one, and a scroll has to count them the way the list
     * does. -1 when the reply is not drawn at all: a row the window has not revealed
     * has no position to scroll to, and saying so is what stops the caller from
     * landing on whatever the clamped index pointed at.
     */
    fun drawnRowIndex(replyId: Int, drawnIds: List<Int>, rowsAboveReplies: Int): Int {
        val index = drawnIds.indexOf(replyId)
        return if (index < 0) -1 else index + rowsAboveReplies
    }
}
