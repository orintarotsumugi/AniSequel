package com.anisequel.ui.components.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.pageUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.HoldAndSwipePlanningButton
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.viewmodel.QuickAddStatus

/**
 * The action row on the title page, under the hero.
 *
 * The external AniList link is always offered. The interactive Hold-and-Swipe
 * quick add is offered for **any title that is not already on one of the
 * viewer's lists**:
 *
 *  - A [AnimeDetailSubject.Sequel] from the dashboard keeps the dashboard's own
 *    write, because that write also recomputes the recommendation list the card
 *    came from.
 *  - Any other title ([AnimeDetailSubject.Media]) goes through the screen's
 *    [com.anisequel.ui.viewmodel.PlanningAddStore], so a title opened from the
 *    Library, a studio page, a profile's favourites or the activity feed can be
 *    planned without leaving the sheet.
 *
 * A title that is already tracked shows a badge instead of an add button. That
 * is not decoration: AniList's mutations *overwrite* the entry, so offering
 * "Add to Planning" on a completed show would silently move it back to Planning
 * and lose the status the reader chose. The status is read from the viewer's
 * own `mediaListEntry` (the detail query is viewer-scoped), and the hosts that
 * open titles straight off a list fold the list entry's status into the summary
 * they hand the sheet so the badge is right on the first frame.
 *
 * It sits in the page's own scroll rather than pinned to the bottom of the sheet,
 * because the sheet's bottom edge belongs to the capsule that switches between the
 * page and the entry editor - two floating bars over one page is one too many.
 *
 * The row is one action or two. When there is nothing to add it does not stretch
 * its one button to fill the space - see the note on the button.
 */
@Composable
fun DetailActionRow(
    subject: AnimeDetailSubject,
    canWriteToAniList: Boolean,
    context: Context,
    modifier: Modifier = Modifier,
    /**
     * This title's quick-add state, from the screen's
     * [com.anisequel.ui.viewmodel.PlanningAddHost].
     *
     * Defaulted so a host that never wires the store still draws the row; a
     * title that is already listed then gets its badge from `mediaListEntry`
     * alone, and one that is not gets a button that only reports the failure.
     */
    quickAddStatus: QuickAddStatus = QuickAddStatus(),
    onAddMediaToPlanning: (MediaNode) -> Unit = {},
    onAddMediaToWatching: (MediaNode) -> Unit = {},
    onAddToPlanning: (MissedSequel) -> Unit = {},
    onAddToWatching: (MissedSequel) -> Unit = {},
    /**
     * Whether the detail query for this title is still in flight.
     *
     * The quick add is held back until it answers: the detail is what says
     * whether the viewer already tracks this show (`mediaListEntry`), and a
     * button that offers to plan a completed title - then flips to its badge a
     * frame later - is a control that can silently rewrite a status the reader
     * chose. AniList's mutation overwrites the entry. A summary that already
     * knows the status (a Library row, an arriving entry) draws its badge
     * immediately either way.
     */
    isDetailLoading: Boolean = false,
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    val sequel = subject.sequel
    val media = subject.media
    val listStatus = media.mediaListEntry?.status

    val isPlanned = quickAddStatus.isAddedToPlanning ||
        listStatus.equals("PLANNING", ignoreCase = true)
    val isWatching = quickAddStatus.isAddedToWatching ||
        listStatus.equals("CURRENT", ignoreCase = true) ||
        listStatus.equals("REPEATING", ignoreCase = true)
    // Completed, paused or dropped: on a list of some kind, so there is nothing
    // to add without rewriting it. Those titles get the AniList link alone.
    val isOnAnotherList = listStatus != null && !isPlanned && !isWatching

    // A sequel's flags come from the recommendation itself; a plain title's from
    // the quick-add store. Kept apart so the dashboard's write - which also
    // recomputes the card behind the sheet - is the one still used there.
    val showSequelAction = sequel != null
    // A quick-add state the reader has already reached (or an in-flight write)
    // is drawn even while the detail loads: the store knows more than the
    // fetch does at that point.
    val showMediaAction = sequel == null && !isOnAnotherList &&
        (!isDetailLoading || isPlanned || isWatching || quickAddStatus.isBusy)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(subject.media.pageUrl))
                        )
                    }
                },
                modifier = Modifier
                    // **Weighted only when it shares the row.** It used to hold
                    // `weight(1f)` unconditionally, so a title with no quick-add drew a
                    // single bordered button across the full width of the page - which
                    // reads as a primary call to action for what is a link out to
                    // AniList, and as a row that failed to lay out rather than as a row
                    // with one control in it.
                    .then(
                        if (showSequelAction || showMediaAction) Modifier.weight(1f) else Modifier
                    )
                    // 48dp, the platform minimum. 44 was the Material 2 button
                    // height, and on the one row a reader reaches for at the bottom
                    // of the sheet the shortfall was felt rather than merely linted.
                    .height(48.dp)
                    .bouncyPress(pressedScale = 0.96f),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = AppVectorIcons.OpenInBrowser,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("AniList", maxLines = 1)
            }

            if (sequel != null) {
                SequelQuickAdd(
                    sequel = sequel,
                    canWriteToAniList = canWriteToAniList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled
                )
            } else if (showMediaAction) {
                MediaQuickAdd(
                    media = media,
                    isPlanned = isPlanned,
                    isWatching = isWatching,
                    canWriteToAniList = canWriteToAniList,
                    status = quickAddStatus,
                    onAddToPlanning = onAddMediaToPlanning,
                    onAddToWatching = onAddMediaToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled
                )
            }
        }

        // The sheet is opened from four screens and only the dashboard owns a
        // snackbar, so a failed write says so here, under the control that made
        // it. Was silently swallowed when the hosts passed empty lambdas.
        quickAddStatus.error?.let { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .testTag("sheet_quick_add_error")
            )
        }
    }
}

/** The dashboard recommendation's own path, unchanged: its write moves the card. */
@Composable
private fun RowScope.SequelQuickAdd(
    sequel: MissedSequel,
    canWriteToAniList: Boolean,
    onAddToPlanning: (MissedSequel) -> Unit,
    onAddToWatching: (MissedSequel) -> Unit,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean
) {
    if (!canWriteToAniList && !sequel.isSavedToList) {
        SignInToAddButton(onClick = { onAddToPlanning(sequel) })
    } else {
        HoldAndSwipePlanningButton(
            sequel = sequel,
            onAddToPlanning = { onAddToPlanning(sequel) },
            onAddToWatching = { onAddToWatching(sequel) },
            holdDurationSeconds = holdDurationSeconds,
            swipeEnabled = swipeEnabled,
            modifier = Modifier.weight(1.3f)
        )
    }
}

/**
 * Any other title's quick add: tap for Planning, hold or swipe for Watching.
 *
 * Recommends the store's write even when it cannot work - the store turns that
 * into the inline "sign in" line rather than the row hiding the control, which
 * is what tells a signed-out reader *why* they cannot do it.
 */
@Composable
private fun RowScope.MediaQuickAdd(
    media: MediaNode,
    isPlanned: Boolean,
    isWatching: Boolean,
    canWriteToAniList: Boolean,
    status: QuickAddStatus,
    onAddToPlanning: (MediaNode) -> Unit,
    onAddToWatching: (MediaNode) -> Unit,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean
) {
    if (!canWriteToAniList && !isPlanned && !isWatching) {
        SignInToAddButton(onClick = { onAddToPlanning(media) })
        return
    }

    QuickAddPlanningButton(
        mediaId = media.id,
        isAddedToPlanning = isPlanned,
        isAddedToWatching = isWatching,
        isBusy = status.isBusy,
        onAddToPlanning = { onAddToPlanning(media) },
        onAddToWatching = { onAddToWatching(media) },
        holdDurationSeconds = holdDurationSeconds,
        swipeEnabled = swipeEnabled,
        modifier = Modifier.weight(1.3f)
    )
}

@Composable
private fun RowScope.SignInToAddButton(
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .weight(1.3f)
            // Matches the AniList button beside it; see the note there.
            .height(48.dp)
            .bouncyPress(pressedScale = 0.96f)
            .testTag("sheet_sign_in_to_add_button"),
        shape = MaterialTheme.shapes.small
    ) {
        Icon(
            imageVector = AppVectorIcons.Login,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text("Sign in to add")
    }
}
