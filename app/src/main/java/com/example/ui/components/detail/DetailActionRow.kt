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
import androidx.compose.material3.Button
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
import com.example.ui.components.expressive.ExpressiveContainedLoadingIndicator
import com.example.ui.components.expressive.bouncyPress

@Composable
fun DetailActionRow(
    sequel: MissedSequel,
    canWriteToAniList: Boolean,
    onAddToPlanning: (MissedSequel) -> Unit,
    context: Context,
    modifier: Modifier = Modifier
) {
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

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

        if (!canWriteToAniList && !sequel.isAddedToPlanning) {
            OutlinedButton(
                onClick = { onAddToPlanning(sequel) },
                modifier = Modifier
                    .weight(1.3f)
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
        } else if (sequel.isAddedToPlanning) {
            Button(
                onClick = {},
                enabled = false,
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp),
                shape = MaterialTheme.shapes.small
            ) {
                Icon(
                    imageVector = AppVectorIcons.BookmarkDone,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Planned")
            }
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onAddToPlanning(sequel)
                },
                enabled = !sequel.isAddingToPlanning,
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .bouncyPress(pressedScale = 0.96f)
                    .testTag("sheet_add_planning_button"),
                shape = MaterialTheme.shapes.small
            ) {
                if (sequel.isAddingToPlanning) {
                    ExpressiveContainedLoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.3f),
                        indicatorColor = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = AppVectorIcons.BookmarkAdd,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add to Planning")
                }
            }
        }
    }
}
