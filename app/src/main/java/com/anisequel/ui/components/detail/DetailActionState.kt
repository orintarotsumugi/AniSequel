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
    val isPlanned = quickAddStatus.isAddedToPlanning || canonicalStatus == ListStatuses.PLANNING
    val isWatching = quickAddStatus.isAddedToWatching ||
        canonicalStatus == ListStatuses.CURRENT || canonicalStatus == ListStatuses.REPEATING

    // AniList's add mutation overwrites existing entries, including completed titles.
    private val isOnAnotherList = listStatus != null && !isPlanned && !isWatching

    // A confirmed or in-flight write knows more than a detail fetch still in flight.
    private val hasKnownAddState = isPlanned || isWatching || quickAddStatus.isBusy
    val showMediaAction = !hasSequel && !isOnAnotherList && (!isDetailLoading || hasKnownAddState)

    // Recommendations retain the dashboard's write, which also recomputes their card.
    val sharesRow = hasSequel || showMediaAction
    val showEntryEdit = !hasSequel && (listStatus != null || isPlanned || isWatching)
    val editableStatus = listStatus ?: if (isPlanned) ListStatuses.PLANNING else ListStatuses.CURRENT
}
