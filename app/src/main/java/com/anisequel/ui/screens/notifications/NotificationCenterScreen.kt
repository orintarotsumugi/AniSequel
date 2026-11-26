package com.anisequel.ui.screens.notifications

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.NotificationFilter
import com.anisequel.domain.notifications.NotificationGroup
import com.anisequel.domain.notifications.NotificationSection
import com.anisequel.domain.notifications.NotificationSectionSlice
import com.anisequel.domain.notifications.buildNotificationSections
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.viewmodel.NotificationUiState
import com.anisequel.ui.viewmodel.NotificationViewModel
import java.time.Instant
import java.util.Locale
import kotlinx.coroutines.delay

/**
 * The notification inbox.
 *
 * The page is a pure drawing of [NotificationUiState]: filters, folding, unread
 * state and page fences all live in the view model. The one thing computed here
 * is the section tree (NEW/EARLIER, then a date bucket), because it depends on
 * the wall clock and on nothing that survives a rotation.
 *
 * Tapping a row marks the whole folded group read before handing the
 * representative to the navigation layer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterScreen(
    viewModel: NotificationViewModel,
    onNavigateBack: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenNotification: (InboxNotification) -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val snackbar = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    // One clock for the whole visible page. Calling Instant.now() inside every
    // timestamp is the same value 40 times and a recomposition hazard besides.
    // Keying on the list and the refresh flag, not its size, also refreshes
    // the clock when a pull returns the same page it already showed.
    val now = remember(state.items, state.refreshing) { Instant.now() }
    val slices = remember(state.groups, now) {
        buildNotificationSections(state.groups, now)
    }

    BackHandler(onBack = onNavigateBack)
    LaunchedEffect(Unit) { viewModel.start() }
    LaunchedEffect(state.filter) {
        // A chip can leave two rows where eighty were. Without this the list keeps
        // the old offset and opens on blank space below the end of the new slice.
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }
    LaunchedEffect(state.notice) {
        val notice = state.notice ?: return@LaunchedEffect
        snackbar.showSnackbar(notice)
        viewModel.dismissNotice()
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            InboxTopBar(
                // Before the first page resolves there is no honest count to
                // animate: drawing "All read" over a skeleton claims an answer
                // the request has not given yet.
                unreadCount = state.unreadCount.takeIf { !state.loading && state.error == null },
                trackingEnabled = state.preferences.trackingEnabled,
                busy = state.loading || state.refreshing,
                scrollBehavior = scrollBehavior,
                onNavigateBack = onNavigateBack,
                onRefresh = viewModel::refresh,
                onOpenSettings = onOpenSettings
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            if (state.filters.size > 1) {
                FilterRow(
                    filters = state.filters,
                    selected = state.filter,
                    onSelect = viewModel::selectFilter
                )
            }
            InboxContent(
                state = state,
                slices = slices,
                listState = listState,
                now = now,
                onRefresh = viewModel::refresh,
                onRetry = viewModel::retry,
                onLoadMore = viewModel::loadMore,
                onMarkAllRead = viewModel::markAllRead,
                onOpenGroup = { group ->
                    viewModel.onGroupOpened(group)
                    onOpenNotification(group.representative)
                },
                onResetFilter = { viewModel.selectFilter(NotificationFilter.ALL) },
                onOpenSettings = onOpenSettings,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

/**
 * The header: a title, an animated unread pill, refresh and settings.
 *
 * A small app bar, not a Medium one. The previous header carried a second line
 * ("Your AniList activity, in one place") that said nothing the screen did not
 * already show, and it cost a fifth of a phone screen above the first row.
 * Everything that must be one tap away still is.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxTopBar(
    unreadCount: Int?,
    trackingEnabled: Boolean,
    busy: Boolean,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior,
    onNavigateBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit
) {
    TopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Notifications",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (trackingEnabled && unreadCount != null) {
                    Spacer(Modifier.width(10.dp))
                    UnreadPill(unreadCount)
                }
            }
        },
        navigationIcon = {
            IconButton(
                onClick = onNavigateBack,
                modifier = Modifier.bouncyPress(pressedScale = 0.9f)
            ) {
                Icon(
                    imageVector = AppVectorIcons.ChevronRight,
                    contentDescription = "Back",
                    modifier = Modifier.graphicsLayer { rotationZ = 180f }
                )
            }
        },
        actions = {
            IconButton(
                onClick = onRefresh,
                enabled = !busy,
                modifier = Modifier.bouncyPress(pressedScale = 0.9f)
            ) {
                Icon(
                    imageVector = AppVectorIcons.Refresh,
                    contentDescription = "Refresh inbox"
                )
            }
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.bouncyPress(pressedScale = 0.9f)
            ) {
                Icon(
                    imageVector = AppVectorIcons.Settings,
                    contentDescription = "Notification settings"
                )
            }
        },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.background,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    )
}

@Composable
private fun UnreadPill(unreadCount: Int) {
    val description = when {
        unreadCount <= 0 -> "All notifications read"
        unreadCount == 1 -> "1 unread notification"
        else -> "$unreadCount unread notifications"
    }
    Surface(
        shape = CircleShape,
        color = if (unreadCount > 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = description }
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
    ) {
        AnimatedContent(
            targetState = unreadCount,
            transitionSpec = {
                (fadeIn(ExpressiveMotion.FastEffects) togetherWith fadeOut(ExpressiveMotion.FastEffects))
                    .using(null)
            },
            label = "unread_pill_count"
        ) { count ->
            Text(
                text = when {
                    count <= 0 -> "All read"
                    count > 99 -> "99+ new"
                    else -> "$count new"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (count > 0) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
private fun FilterRow(
    filters: List<NotificationFilter>,
    selected: NotificationFilter,
    onSelect: (NotificationFilter) -> Unit
) {
    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
        // Same measure as the cards below, so a tablet does not get a tight
        // cluster of chips at the far left of an otherwise centred page.
        val sidePadding = ((maxWidth - NotificationCardMaxWidth) / 2).coerceAtLeast(20.dp)
        LazyRow(
            contentPadding = PaddingValues(start = sidePadding, top = 6.dp, end = sidePadding, bottom = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(filters.size, key = { filters[it].name }) { index ->
                val filter = filters[index]
                ExpressiveStateChip(
                    label = filter.label,
                    selected = selected == filter,
                    onClick = { onSelect(filter) },
                    minTouchTargetHeight = 46.dp,
                    role = androidx.compose.ui.semantics.Role.RadioButton,
                    onClickLabel = "Show ${filter.label.lowercase(Locale.ROOT)}"
                )
            }
        }
    }
}

@Composable
private fun InboxContent(
    state: NotificationUiState,
    slices: List<NotificationSectionSlice>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    now: Instant,
    onRefresh: () -> Unit,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    onMarkAllRead: () -> Unit,
    onOpenGroup: (NotificationGroup) -> Unit,
    onResetFilter: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val newSlices = remember(slices) { slices.filter { it.section == NotificationSection.NEW } }
    val earlierSlices = remember(slices) { slices.filter { it.section == NotificationSection.EARLIER } }
    // Entrance stagger is keyed on the unfiltered list position, so a chip
    // switch that keeps a row does not replay a different delay for it.
    val entranceIndex = remember(state.groups) {
        state.groups.withIndex().associate { (index, group) -> group.key to index }
    }
    val latestState = rememberUpdatedState(state)
    val shouldLoadMore by remember(listState) {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            val count = latestState.value
            // One page is requested at a time. A failed page also stops the
            // trigger; without that it went false on failure and true again on
            // the next frame, retrying a rate-limited inbox in a loop.
            //
            // An empty filtered view loads nothing automatically. Otherwise a
            // chip with no match on page one walked the entire notification
            // history while the reader watched a spinner. The empty state
            // offers one explicit "Load older" instead.
            count.groups.isNotEmpty() && count.hasMore &&
                !count.loading && !count.refreshing && !count.loadingMore &&
                count.loadMoreError == null && last >= info.totalItemsCount - 4
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    // A refresh can return fewer rows than were on screen. Keep the list from
    // being parked below its new end when that happens.
    LaunchedEffect(state.groups.size) {
        val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        if (last > state.groups.size + 3) listState.scrollToItem(0)
    }

    // One clock for every skeleton on the page, and only while the page is a
    // skeleton. A driver per card meant five infinite transitions for one
    // waiting state; a driver at the wrong scope would keep ticking after load.
    val skeletonPhase = if (state.loading && state.items.isEmpty()) {
        rememberShimmerPhase()
    } else {
        null
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        // Cards stop at a readable measure on tablets. Padding the list rather
        // than each card keeps the section headings and the pull indicator on
        // the same edge as the rows they belong to.
        val sidePadding = ((maxWidth - NotificationCardMaxWidth) / 2).coerceAtLeast(20.dp)
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = sidePadding,
                    end = sidePadding,
                    top = 2.dp,
                    bottom = 28.dp
                ),
                verticalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                when {
                    state.loading && state.items.isEmpty() -> {
                        item(key = "skeleton_header") { SkeletonSectionHeader(skeletonPhase!!) }
                        items(5, key = { "skeleton_$it" }) { index ->
                            // The branch above guarantees the phase was created.
                            SkeletonCard(media = index % 3 == 0, phase = skeletonPhase!!)
                        }
                    }

                    state.error != null && state.items.isEmpty() -> {
                        item(key = "error") {
                            ErrorState(
                                message = state.error,
                                onRetry = onRetry,
                                modifier = Modifier.fillParentMaxSize()
                            )
                        }
                    }

                    state.groups.isEmpty() -> {
                        item(key = "empty") {
                            EmptyState(
                                filtered = state.filter != NotificationFilter.ALL,
                                allCategoriesOff = state.preferences.allCategoriesOff,
                                hasMore = state.hasMore,
                                loadingMore = state.loadingMore,
                                loadMoreError = state.loadMoreError,
                                onResetFilter = onResetFilter,
                                onLoadMore = onLoadMore,
                                onOpenSettings = onOpenSettings,
                                modifier = Modifier.fillParentMaxSize()
                            )
                        }
                    }

                    else -> {
                        if (newSlices.isNotEmpty()) {
                            item(key = "new_header") {
                                SectionHeader(
                                    title = "New",
                                    count = newSlices.sumOf { it.unreadCount },
                                    onMarkAllRead = onMarkAllRead,
                                    showMarkAll = state.preferences.trackingEnabled
                                )
                            }
                            newSlices.forEach { slice ->
                                item(key = "bucket_new_${slice.bucket.name}") {
                                    BucketHeader(slice)
                                }
                                itemsIndexed(slice.groups, key = { _, group -> group.key }) { _, group ->
                                    NotificationEntrance(
                                        group = group,
                                        index = entranceIndex[group.key] ?: 0,
                                        now = now,
                                        onOpen = { onOpenGroup(group) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                        if (earlierSlices.isNotEmpty()) {
                            item(key = "earlier_header") {
                                SectionHeader(
                                    title = "Earlier",
                                    count = earlierSlices.sumOf { it.memberCount },
                                    onMarkAllRead = onMarkAllRead,
                                    // Bulk read belongs to NEW. Without a New
                                    // section every row is already read, so the
                                    // action would do nothing.
                                    showMarkAll = false
                                )
                            }
                            earlierSlices.forEach { slice ->
                                item(key = "bucket_earlier_${slice.bucket.name}") {
                                    BucketHeader(slice)
                                }
                                itemsIndexed(slice.groups, key = { _, group -> group.key }) { _, group ->
                                    NotificationEntrance(
                                        group = group,
                                        index = entranceIndex[group.key] ?: 0,
                                        now = now,
                                        onOpen = { onOpenGroup(group) },
                                        modifier = Modifier.animateItem()
                                    )
                                }
                            }
                        }
                        if (state.loadingMore) {
                            item(key = "loading_more") {
                                Box(Modifier.fillMaxWidth().padding(18.dp), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                }
                            }
                        }
                        if (state.loadMoreError != null) {
                            // A failed page does not replace the rows already read; it
                            // gets one line under them with its own retry.
                            item(key = "load_more_error") {
                                LoadMoreError(state.loadMoreError, onRetry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationEntrance(
    group: NotificationGroup,
    index: Int,
    now: Instant,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    var shown by remember(group.key) { mutableStateOf(false) }
    LaunchedEffect(group.key) {
        if (index < 10) delay(index * 26L)
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "inbox_card_fade"
    )
    val lift by animateFloatAsState(
        targetValue = if (shown) 0f else 12f,
        animationSpec = ExpressiveMotion.DefaultSpatial,
        label = "inbox_card_lift"
    )
    NotificationCard(
        group = group,
        onClick = onOpen,
        now = now,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = lift
        }
    )
}

/**
 * The NEW / EARLIER heading, with the one bulk action that belongs to it.
 *
 * Counts are member counts, not row counts: a folded "three people liked this"
 * row contributes three to the heading and three to the unread pill, so the two
 * numbers cannot disagree.
 */
@Composable
private fun SectionHeader(
    title: String,
    count: Int,
    onMarkAllRead: () -> Unit,
    showMarkAll: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 10.dp, bottom = 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            // The heading belongs to this line, not to the row: merging the row
            // also swallowed the Mark all read button, leaving its action
            // unreachable for a screen reader.
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.width(8.dp))
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Text(
                text = "$count",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
            )
        }
        Spacer(Modifier.weight(1f))
        if (showMarkAll) {
            TextButton(onClick = onMarkAllRead, contentPadding = PaddingValues(horizontal = 10.dp)) {
                Text(
                    text = "Mark all read",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

/**
 * A date heading under NEW or EARLIER.
 *
 * Smaller than the section heading and separated by a rule, so the hierarchy is
 * readable at a glance: NEW is the promise, "Today" is where to look. Only
 * buckets that actually contain rows reach this composable - the section
 * builder never emits an empty one.
 */
@Composable
private fun BucketHeader(slice: NotificationSectionSlice) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 2.dp, bottom = 1.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = slice.bucket.label.uppercase(Locale.ROOT),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { heading() }
        )
        Spacer(Modifier.width(8.dp))
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        )
    }
}

@Composable
private fun EmptyState(
    filtered: Boolean,
    allCategoriesOff: Boolean,
    hasMore: Boolean,
    loadingMore: Boolean,
    loadMoreError: String?,
    onResetFilter: () -> Unit,
    onLoadMore: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    // A filtered page can be empty while older pages still contain a match. Do
    // not walk those pages automatically; offer one deliberate step instead.
    val canLoadOlder = filtered && !allCategoriesOff && hasMore
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveEmptyOrb(
            icon = emptyStateIcon(allCategoriesOff, filtered),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            iconTint = MaterialTheme.colorScheme.onPrimaryContainer
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = emptyStateTitle(allCategoriesOff, filtered, canLoadOlder),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = emptyStateBody(allCategoriesOff, filtered, canLoadOlder),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        ExpressivePrimaryButton(
            text = emptyStateActionLabel(allCategoriesOff, filtered),
            onClick = if (filtered && !allCategoriesOff) onResetFilter else onOpenSettings
        )
        if (canLoadOlder) {
            EmptyStateOlder(
                loadMoreError = loadMoreError,
                loadingMore = loadingMore,
                onLoadMore = onLoadMore
            )
        }
    }
}

private fun emptyStateIcon(allCategoriesOff: Boolean, filtered: Boolean) = when {
    allCategoriesOff -> AppVectorIcons.Tune
    filtered -> AppVectorIcons.Filter
    else -> AppVectorIcons.CheckCircle
}

private fun emptyStateTitle(
    allCategoriesOff: Boolean,
    filtered: Boolean,
    canLoadOlder: Boolean
): String = when {
    allCategoriesOff -> "Your categories are off."
    filtered && canLoadOlder -> "No matches in these pages."
    filtered -> "No matches in this inbox."
    else -> "You're all caught up."
}

private fun emptyStateBody(
    allCategoriesOff: Boolean,
    filtered: Boolean,
    canLoadOlder: Boolean
): String = when {
    allCategoriesOff ->
        "Every category is switched off in Notification settings, so there is nothing to show here. Turn one back on to see your inbox."
    canLoadOlder ->
        "No loaded notification matches this filter. Older notifications may still have one, or you can switch back to everything."
    filtered ->
        "No notifications match this filter. Switch back to everything to see the rest of your inbox."
    else ->
        "Likes, replies, follows, messages and title updates will land here when they need you. Turn on background alerts in Notification settings if you want Android to tell you about them."
}

private fun emptyStateActionLabel(allCategoriesOff: Boolean, filtered: Boolean): String = when {
    allCategoriesOff -> "Open notification settings"
    filtered -> "Show all"
    else -> "Notification settings"
}

@Composable
private fun EmptyStateOlder(
    loadMoreError: String?,
    loadingMore: Boolean,
    onLoadMore: () -> Unit
) {
    if (loadMoreError != null) {
        Spacer(Modifier.height(10.dp))
        Text(
            text = loadMoreError,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center
        )
    }
    TextButton(onClick = onLoadMore, enabled = !loadingMore) {
        if (loadingMore) {
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(8.dp))
            Text(if (loadMoreError == null) "Loading older notifications" else "Retrying")
        } else {
            Text(if (loadMoreError == null) "Load older notifications" else "Retry older notifications")
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.Warning,
            containerColor = MaterialTheme.colorScheme.errorContainer,
            iconTint = MaterialTheme.colorScheme.onErrorContainer
        )
        Spacer(Modifier.height(18.dp))
        Text(
            text = "The inbox could not load.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(7.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        ExpressivePrimaryButton(text = "Retry", onClick = onRetry)
    }
}

@Composable
private fun LoadMoreError(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun SkeletonSectionHeader(phase: State<Float>) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(Modifier.width(78.dp).height(12.dp), CircleShape, phase)
        Spacer(Modifier.width(8.dp))
        ShimmerBox(Modifier.size(26.dp, 14.dp), CircleShape, phase)
        Spacer(Modifier.weight(1f))
        ShimmerBox(Modifier.width(72.dp).height(12.dp), CircleShape, phase)
    }
}

@Composable
private fun SkeletonCard(media: Boolean, phase: State<Float>) {
    Surface(
        shape = ExpressiveShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            ShimmerBox(
                modifier = Modifier.size(
                    width = if (media) 64.dp else 44.dp,
                    height = if (media) 90.dp else 44.dp
                ),
                shape = if (media) RoundedCornerShape(13.dp) else CircleShape,
                phase = phase
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                ShimmerBox(Modifier.fillMaxWidth(0.30f).height(9.dp), CircleShape, phase)
                ShimmerBox(Modifier.fillMaxWidth(0.92f).height(16.dp), CircleShape, phase)
                ShimmerBox(Modifier.fillMaxWidth(0.68f).height(12.dp), CircleShape, phase)
                if (media) {
                    ShimmerBox(
                        Modifier.fillMaxWidth().height(30.dp),
                        RoundedCornerShape(10.dp),
                        phase
                    )
                }
            }
        }
    }
}
