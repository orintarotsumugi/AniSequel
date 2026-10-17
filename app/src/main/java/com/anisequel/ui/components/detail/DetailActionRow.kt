package com.anisequel.ui.components.detail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.pageUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.HoldAndSwipePlanningButton
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The action row on the title page, under the hero.
 *
 * The external AniList link is always offered. The interactive Hold-and-Swipe
 * quick add is only for a [AnimeDetailSubject.Sequel]: an arriving or activity
 * row is already on somebody's list, so there is nothing to add and the button
 * would be offering an action that cannot mean anything.
 *
 * It sits in the page's own scroll rather than pinned to the bottom of the sheet,
 * because the sheet's bottom edge belongs to the capsule that switches between the
 * page and the entry editor - two floating bars over one page is one too many.
 *
 * The row is one action or two, and which one it is depends on the **subject** rather
 * than on the reader's session: a recommendation from the dashboard has a sequel to
 * offer, while an arriving row or an activity entry is a show that is already on
 * somebody's list, so there is nothing to add. When there is nothing to add the row
 * does not stretch its one button to fill the space - see the note on the button.
 */
@Composable
fun DetailActionRow(
    subject: AnimeDetailSubject,
    canWriteToAniList: Boolean,
    onAddToPlanning: (MissedSequel) -> Unit,
    context: Context,
    modifier: Modifier = Modifier,
    onAddToWatching: (MissedSequel) -> Unit = {},
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    val sequel = subject.sequel

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(subject.media.pageUrl)))
                }
            },
            modifier = Modifier
                // **Weighted only when it shares the row.** It used to hold
                // `weight(1f)` unconditionally, so a title with no quick-add drew a
                // single bordered button across the full width of the page - which
                // reads as a primary call to action for what is a link out to
                // AniList, and as a row that failed to lay out rather than as a row
                // with one control in it. The case is not rare: opening a title from
                // an Activity row has no sequel to add, because the show is already
                // on that person's list.
                .then(if (sequel != null) Modifier.weight(1f) else Modifier)
                .height(44.dp)
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

        if (sequel != null && !canWriteToAniList && !sequel.isSavedToList) {
            OutlinedButton(
                onClick = { onAddToPlanning(sequel) },
                modifier = Modifier
                    .weight(1.3f)
                    .height(44.dp)
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
        } else if (sequel != null) {
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
}
