package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.FollowUser
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.viewmodel.UserActivityViewModel

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
 *
 * The sheet skips the partially-expanded state. A tray that opens halfway up a
 * list of faces it was opened to show is a second gesture the reader did not ask
 * for, and the comments sheet has opened fully expanded since it was written.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActivityLikersSheet(
    state: UserActivityViewModel.LikersState,
    onDismiss: () -> Unit,
    /**
     * Retries the list after a failed load.
     *
     * The error used to be a bare sentence with no way forward, unlike its
     * sibling sheet, whose failure offers the same request one tap away. Who
     * liked something is a read that fails for the ordinary reasons - a dropped
     * connection, a rate limit - and the retry that would succeed is one tap.
     */
    onRetry: () -> Unit = {}
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Nothing de-duplicates a likes list on the way here: `getActivityLikes` maps
    // `activity.likes` straight through, and AniList's favourites connection has
    // already been captured returning six copies of one entry. A repeated id is a
    // fatal duplicate key in the `LazyColumn` below, so the list is collapsed here
    // - this is the boundary that actually lays rows out, and a viewer listed twice
    // is the wrong answer regardless.
    val likers = remember(state.likers) { state.likers.distinctBy { it.id } }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                // No `navigationBarsPadding`: the sheet's default
                // `contentWindowInsets` already pads its content for the system
                // bars, and applying both drew the last row a nav bar's height
                // above the edge for no reason.
                .nestedScroll(rememberSheetFlingGuard(sheetState))
                .testTag("activity_likers_sheet"),
            contentPadding = PaddingValues(bottom = 20.dp)
        ) {
            item(key = "header") {
                LikersHeader(count = likers.size, isLoading = state.isLoading)
            }

            when {
                state.isLoading -> item(key = "loading") { LikersLoading() }

                state.loadError != null -> item(key = "load_error") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = state.loadError,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(
                            onClick = onRetry,
                            modifier = Modifier.testTag("activity_likers_retry_button")
                        ) {
                            Text("Try again")
                        }
                    }
                }

                likers.isEmpty() -> item(key = "empty") {
                    Text(
                        text = "No likes yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp)
                    )
                }

                else -> items(
                    items = likers,
                    key = { it.id },
                    contentType = { "liker" }
                ) { liker ->
                    LikerRow(liker = liker)
                }
            }
        }
    }
}

/**
 * The waiting rows, and the shimmer clock that draws them.
 *
 * The clock used to be remembered at the top of the sheet, so its infinite
 * transition was composed for the whole time the tray was open - including after
 * the list had loaded, driving a skeleton that was no longer drawn. Scoped to the
 * loading item it exists only while there is a skeleton to sweep, which is the
 * same rule the thread's skeleton follows.
 */
@Composable
private fun LikersLoading() {
    PersonRowsSkeleton(
        phase = rememberShimmerPhase(),
        avatarSize = 34.dp,
        avatarShape = CircleShape,
        horizontalPadding = 20.dp
    )
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
