package com.example.ui.screens.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.domain.usecase.ActivityCalendar
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveEmptyOrb
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.theme.AniSequelTheme
import com.example.ui.viewmodel.UserActivityViewModel

/**
 * The Activity tab: the person's activity heatmap and list history, newest first.
 *
 * ## Two complementary views:
 *
 * 1. [ActivityCalendarCard] provides the 26-week contribution calendar summary
 *    (finished anime and list changes), giving an immediate visual overview of
 *    consistency and streaks.
 * 2. The paginated activity feed presents rich, tinted cards for individual episodes
 *    watched, titles completed, or items planned.
 */
@Composable
fun ProfileActivityTab(
    activityState: UserActivityViewModel.ActivityState,
    onLoadFirstPage: () -> Unit,
    onLoadNextPage: () -> Unit,
    onRetry: () -> Unit = onLoadFirstPage,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
    calendar: ActivityCalendar? = null
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
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        state = listState
    ) {
        if (calendar != null && calendar.columns.isNotEmpty()) {
            item(key = "activity_calendar_section") {
                Column(
                    modifier = Modifier
                        .widthIn(max = ProfileMaxContentWidth)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    ProfileSectionHeader(
                        title = "Activity History",
                        icon = AppVectorIcons.Calendar,
                        trailing = "${calendar.totalChanges} changes"
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    ActivityCalendarCard(calendar = calendar)
                }
            }
        }

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
                        onRetry = onRetry
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp)
            .testTag("activity_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.ProfileActivity,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "No list activity to show",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
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
