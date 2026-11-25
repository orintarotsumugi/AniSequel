package com.anisequel.ui.components.detail.info

import androidx.compose.runtime.Immutable
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.viewmodel.QuickAddStatus

/**
 * Everything the two Info page styles receive that is not a modifier or a
 * hoisted scroll state.
 *
 * The classic and Nova pages take the same information and the same callbacks, but
 * used to spell out twenty parameters each. That was not only a duplicate-code
 * finding: adding a callback meant editing two lists that had to stay in lockstep,
 * and the host that draws whichever style the reader picked passed the same twenty
 * arguments twice. One value keeps the styles substitutable.
 */
@Immutable
data class InfoPageArgs(
    val subject: AnimeDetailSubject,
    val isDetailLoading: Boolean,
    val isFriendsLoading: Boolean = false,
    val canWriteToAniList: Boolean,
    val holdDurationSeconds: Int,
    val swipeEnabled: Boolean,
    val onAddToPlanning: (MissedSequel) -> Unit,
    val onAddToWatching: (MissedSequel) -> Unit,
    val quickAddStatus: QuickAddStatus = QuickAddStatus(),
    val onAddMediaToPlanning: (MediaNode) -> Unit = {},
    val onAddMediaToWatching: (MediaNode) -> Unit = {},
    val friends: List<MediaFriend> = emptyList(),
    val onClose: () -> Unit,
    val onOpenMedia: (MediaNode) -> Unit = {},
    val onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    val onOpenCharacter: ((Int) -> Unit)? = null,
    val onOpenStaff: ((Int) -> Unit)? = null,
    val onEditEntry: (() -> Unit)? = null
)
