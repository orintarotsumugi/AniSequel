package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ListActivity
import com.example.ui.components.AppVectorIcons
import androidx.compose.material3.Icon
import androidx.compose.material3.TextButton
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.viewmodel.UserActivityViewModel

/**
 * The Activity tab: the person's own list history, newest first.
 *
 * ## Why the cards are tinted rather than flat
 *
 * Each card's background is a heavily blurred, heavily scaled copy of its own
 * cover, under the surface colour. It is not decoration: a wall of twenty
 * identical dark cards with a small thumbnail each is a wall nobody can scan,
 * and the cover is already the only thing that identifies the entry. Using the
 * cover as the card's own background makes the row readable at a glance at the
 * cost of one extra image load, which Coil serves from the disk cache the same
 * URL filled a moment earlier.
 *
 * The blur is drawn as a `Brush` scrim rather than a real blur, because Compose
 * has no cheap blur and a `RenderEffect` on twenty cards is not something to put
 * on someone's phone. Scaling the image up to fill before tinting is what makes it
 * read as a blur at all.
 */
@Composable
fun ProfileActivityTab(
    activityState: UserActivityViewModel.ActivityState,
    onLoadFirstPage: () -> Unit,
    onLoadNextPage: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier
) {
    // Fetched on first composition, so opening the tab is what costs the request -
    // not opening the profile.
    LaunchedEffect(Unit) { onLoadFirstPage() }

    val listState = rememberLazyListState()

    // Six rows from the end is far enough that the fetch has usually landed before
    // the user gets there, and near enough that a short list does not spin.
    val shouldPage by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && lastVisible >= total - PAGE_TRIGGER_ROWS
        }
    }
    LaunchedEffect(shouldPage) {
        if (shouldPage) onLoadNextPage()
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_activity_tab"),
        contentPadding = profileListPadding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = listState
    ) {
        item(key = "activity_header") {
            ProfileSectionHeader(
                title = "Recent Activity",
                icon = AppVectorIcons.ProfileActivity,
                modifier = Modifier
                    .widthIn(max = ProfileMaxContentWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            )
        }

        when (activityState) {
            is UserActivityViewModel.ActivityState.Idle,
            is UserActivityViewModel.ActivityState.Loading -> {
                item(key = "activity_loading") {
                    ActivityLoading()
                }
            }

            is UserActivityViewModel.ActivityState.Error -> {
                item(key = "activity_error") {
                    ActivityError(
                        message = activityState.message,
                        onRetry = onLoadFirstPage
                    )
                }
            }

            is UserActivityViewModel.ActivityState.Success -> {
                if (activityState.activities.isEmpty()) {
                    item(key = "activity_empty") {
                        ActivityEmpty()
                    }
                } else {
                    items(
                        items = activityState.activities,
                        // The activity id, not the media id: one show can appear
                        // in the feed more than once - watched, then completed -
                        // and keying on the media would collapse those rows and
                        // then throw on the duplicate key.
                        key = { it.id }
                    ) { activity ->
                        ActivityCard(
                            activity = activity,
                            modifier = Modifier
                                .widthIn(max = ProfileMaxContentWidth)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp)
                        )
                    }

                    if (activityState.hasMore) {
                        item(key = "activity_more") {
                            ActivityPagingIndicator(loading = activityState.isLoadingMore)
                        }
                    }
                }
            }
        }
    }
}

private const val PAGE_TRIGGER_ROWS = 6

@Composable
private fun ActivityCard(
    activity: ListActivity,
    modifier: Modifier = Modifier
) {
    val media = activity.media ?: return
    val coverUrl = media.coverImage?.large
    val isManga = media.type == "MANGA"

    Card(
        modifier = modifier
            .bouncyPress(pressedScale = 0.97f)
            .testTag("activity_card"),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Box {
            // The tinted cover, behind everything. `fillMaxSize` inside a Box whose
            // size comes from the content below, so it is measured last and drawn
            // first.
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.large),
                    contentScale = ContentScale.Crop,
                    alpha = COVER_BACKGROUND_ALPHA
                )
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(MaterialTheme.shapes.large)
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.82f),
                                    MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f)
                                )
                            )
                        )
                )
            }

            Row(modifier = Modifier.padding(14.dp)) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .width(92.dp)
                        .aspectRatio(POSTER_ASPECT)
                        .clip(MaterialTheme.shapes.medium)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop,
                    placeholder = rememberVectorPainter(AppVectorIcons.FavouriteAnime),
                    error = rememberVectorPainter(AppVectorIcons.FavouriteAnime),
                    fallback = rememberVectorPainter(AppVectorIcons.FavouriteAnime)
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    ActivityStatusPill(
                        text = activity.displayStatusFor(isManga),
                        accent = activity.status?.lowercase() == "completed"
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = activityDetail(activity),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = media.title?.displayTitle.orEmpty(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityCount(
                            icon = AppVectorIcons.Star,
                            count = activity.likeCount ?: 0
                        )
                        ActivityCount(
                            icon = AppVectorIcons.ActivityReplies,
                            count = activity.replyCount ?: 0
                        )
                    }
                }
            }
        }
    }
}

/**
 * The sentence under the status pill.
 *
 * "Episode 7 of 24" while watching, the title alone when there is nothing to add.
 * The progress is only rendered when AniList gave a progress *and* the show has an
 * episode count to compare it against - "Episode 7 of 0" is worse than no second
 * line at all.
 */
private fun activityDetail(activity: ListActivity): String {
    val media = activity.media ?: return ""
    val progress = activity.progress ?: return media.format?.lowercase()
        ?.replaceFirstChar { it.uppercase() }
        .orEmpty()
    val episodes = media.episodes ?: return "Episode $progress"
    return "Episode $progress of $episodes"
}

@Composable
private fun ActivityStatusPill(text: String, accent: Boolean) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = if (accent) {
            MaterialTheme.colorScheme.tertiary
        } else {
            MaterialTheme.colorScheme.primary
        },
        maxLines = 1,
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(
                if (accent) {
                    MaterialTheme.colorScheme.tertiaryContainer
                } else {
                    MaterialTheme.colorScheme.primaryContainer
                }
            )
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("activity_status_pill")
    )
}

@Composable
private fun ActivityCount(icon: ImageVector, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(5.dp))
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ActivityLoading() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .testTag("activity_loading"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(36.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun ActivityError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .testTag("activity_error"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .bouncyPress()
                .testTag("activity_retry_button")
        ) {
            Text("Try again")
        }
    }
}

@Composable
private fun ActivityEmpty() {
    Text(
        text = "No list activity to show",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp)
            .testTag("activity_empty")
    )
}

@Composable
private fun ActivityPagingIndicator(loading: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("activity_paging"),
        contentAlignment = Alignment.Center
    ) {
        if (loading) {
            ExpressiveLoadingIndicator(
                modifier = Modifier.size(22.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

/** AniList covers are 2:3. */
private const val POSTER_ASPECT = 2f / 3f

/**
 * How present the cover is behind its own card.
 *
 * Low on purpose: this is a background, and a card whose background competes with
 * the status pill is a card nobody can read the pill on.
 */
private const val COVER_BACKGROUND_ALPHA = 0.18f
