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
     * How many rows a refresh may push out of sight.
     *
     * New replies are appended, so a reader parked halfway up a thread loses the
     * row they were reading to the bottom of the list. The sheet therefore grows
     * its own window by exactly the number of rows a refresh added - and only for
     * a reader who had already scrolled up: one who never scrolled is at the
     * newest end, where new rows belong.
     */
    fun grownRowCount(shownRows: Int, addedRows: Int): Int = shownRows + addedRows.coerceAtLeast(0)
}
