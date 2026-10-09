package com.example.ui.components.detail

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
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.cards.HoldAndSwipePlanningButton
import com.example.ui.components.expressive.bouncyPress

/**
 * Action bar at the bottom of the anime detail sheet.
 * Features an external AniList link button and the interactive Hold-and-Swipe quick action button.
 */
@Composable
fun DetailActionRow(
    sequel: MissedSequel,
    canWriteToAniList: Boolean,
    onAddToPlanning: (MissedSequel) -> Unit,
    context: Context,
    modifier: Modifier = Modifier,
    onAddToWatching: (MissedSequel) -> Unit = {},
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = {
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(sequel.siteUrl)))
                }
            },
            modifier = Modifier
                .weight(1f)
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

        if (!canWriteToAniList && !sequel.isSavedToList) {
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
}
