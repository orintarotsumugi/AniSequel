package com.anisequel.domain.usecase

/**
 * When the Social tab has to ask AniList for a list, and when it does not.
 *
 * Both rules exist because the tab's trigger runs on *every* state change, and the
 * requests it guards are the app's most expensive per-tap pair. The failure these
 * encode is the same one from both directions:
 *
 *  - A one-shot `LaunchedEffect(Unit)` that only fired from `Idle` left the tab on a
 *    spinner forever after a cancelled job or an unresolved profile id - nothing was
 *    ever going to ask again, because the one frame allowed to ask had already
 *    happened.
 *  - Widening that to "ask whenever the tab is composed" makes it one follower *and*
 *    one following request per recomposition, against an API AniList currently holds
 *    at 30 requests a minute - which also rate-limits every other screen in the app.
 *
 * So the entry trigger asks only when nothing is producing an answer and nothing is
 * on screen, and a chip tap spends a request only when the list it opens has nothing
 * behind it.
 */

/** What a tap on the Followers/Following chip should do. */
enum class SocialChipAction {
    /** Rows are in memory: change what is displayed and spend nothing. */
    Show,

    /**
     * A fetch is already in flight that will answer this list: move the spinner.
     *
     * The tab fetches followers *and* following in one job, so restarting the job
     * for the other chip cancels both answers already on the wire and re-issues
     * both requests to show a list the reader is looking at either way.
     */
    Retarget,

    /** Nothing on screen and nothing coming: ask. */
    Fetch
}

fun socialChipAction(
    hasRows: Boolean,
    fetchInFlight: Boolean,
    isFailedList: Boolean
): SocialChipAction = when {
    // A list that failed has no rows to switch to even when the state still holds
    // some: those are the rows from before it failed, and the tap is a retry.
    isFailedList -> SocialChipAction.Fetch
    hasRows -> SocialChipAction.Show
    fetchInFlight -> SocialChipAction.Retarget
    else -> SocialChipAction.Fetch
}

/**
 * Whether entering the tab should start the fetch it came for.
 *
 * A failure is deliberately *not* an auto-retry: the tab's error state carries its
 * own button, and asking again on entry would spend a request per visit to say the
 * same sentence - and for a dead session, say it forever.
 */
fun shouldStartSocialFetch(
    hasSomeoneToFetch: Boolean,
    hasRowsOnScreen: Boolean,
    isFetchInFlight: Boolean,
    isShowingFailure: Boolean
): Boolean = hasSomeoneToFetch &&
    !hasRowsOnScreen &&
    !isFetchInFlight &&
    !isShowingFailure
