package com.anisequel.ui.components.detail

import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.viewmodel.QuickAddStatus

/** Keeps list-write safety independent of the row's Compose layout. */
internal class DetailActionState(
    private val listStatus: String?,
    private val quickAddStatus: QuickAddStatus,
    private val isDetailLoading: Boolean,
    private val hasSequel: Boolean
) {
    private val canonicalStatus = ListStatuses.normalize(listStatus)

    // A confirmed or in-flight write on this page knows more than a detail fetch,
    // and the quick add only ever writes these two statuses.
    val isPlanned = quickAddStatus.isAddedToPlanning || canonicalStatus == ListStatuses.PLANNING
    val isWatching = quickAddStatus.isAddedToWatching || canonicalStatus == ListStatuses.CURRENT

    /**
     * The viewer's status for a title the quick add does not represent:
     * completed, paused, dropped - and **rewatching**.
     *
     * Rewatching used to be folded into [isWatching] because the API calls it a
     * watching state, which made the row draw its generic "Currently Watching"
     * badge over an entry the reader had explicitly put in a second pass. The
     * exact token is kept here so the badge and the edit control both say
     * "Rewatching", which is what AniList calls it.
     *
     * A title in this state never offers the quick add. That is not decoration:
     * AniList's add mutation *overwrites* the entry, so offering "Add to Planning"
     * on a completed show silently moves it back to Planning and loses the status
     * the reader chose.
     */
    val trackedStatus: String? = canonicalStatus
        ?.takeUnless { it == ListStatuses.CURRENT || it == ListStatuses.PLANNING }
        ?.takeUnless { isPlanned || isWatching }

    private val isOnAnotherList = trackedStatus != null

    // A confirmed or in-flight write knows more than a detail fetch still in flight.
    private val hasKnownAddState = isPlanned || isWatching || quickAddStatus.isBusy
    val showMediaAction = !hasSequel && !isOnAnotherList && (!isDetailLoading || hasKnownAddState)

    // Recommendations retain the dashboard's write, which also recomputes their card.
    val sharesRow = hasSequel || showMediaAction
    val showEntryEdit = !hasSequel && (listStatus != null || isPlanned || isWatching)
    val editableStatus = listStatus ?: if (isPlanned) ListStatuses.PLANNING else ListStatuses.CURRENT
}
