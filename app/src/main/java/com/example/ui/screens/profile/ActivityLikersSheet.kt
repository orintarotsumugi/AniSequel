package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.FollowUser
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.viewmodel.UserActivityViewModel

/**
 * The people who liked one activity.
 *
 * Read-only, unlike [ActivityCommentsSheet]: liking is a tap on the card, not an
 * action taken in the sheet, so there is no composer and nothing here writes.
 * The visibility of the whole sheet is derived from [UserActivityViewModel]'s
 * nullable likers state, so a loading or empty sheet is never up with nothing
 * under it.
 *
 * One [LazyColumn] of header plus rows, the same structure and spacing as the
 * comments sheet, so the two engagement sheets read as one family. The counts
 * are not paginated by AniList - `likes` is a plain list - so there is no paging
 * footer to get wrong.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLikersSheet(
    state: UserActivityViewModel.LikersState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .testTag("activity_likers_sheet"),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item(key = "header") {
                LikersHeader(count = state.likers.size, isLoading = state.isLoading)
            }

            when {
                state.isLoading -> item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 28.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        ExpressiveLoadingIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                state.loadError != null -> item(key = "load_error") {
                    Text(
                        text = state.loadError,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }

                state.likers.isEmpty() -> item(key = "empty") {
                    Text(
                        text = "No likes yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }

                else -> items(
                    items = state.likers,
                    key = { it.id },
                    contentType = { "liker" }
                ) { liker ->
                    LikerRow(liker = liker)
                }
            }
        }
    }
}

@Composable
private fun LikersHeader(count: Int, isLoading: Boolean) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.ActivityLiked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                // "Likes" while the list is still coming rather than "Liked by 0":
                // a zero here would be a claim about the activity the app has not
                // been told yet, and the comments header omits its count for the
                // same reason.
                text = if (isLoading) "Likes" else "Liked by $count",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun LikerRow(liker: FollowUser) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LikerAvatar(
            url = liker.avatar?.large ?: liker.avatar?.medium,
            name = liker.name
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = liker.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1
        )
    }
}

/**
 * A 34dp circle, the same one a comment author gets, so the same person reads as
 * the same shape in either sheet. Falls back to an initial when AniList serves
 * no avatar, which is the case for some older accounts.
 */
@Composable
private fun LikerAvatar(url: String?, name: String) {
    val shape = CircleShape
    val base = Modifier
        .size(34.dp)
        .clip(shape)
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            modifier = base.background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = base.background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.trim().take(1).uppercase(),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}
