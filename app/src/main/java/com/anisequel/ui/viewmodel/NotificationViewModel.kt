package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.NotificationFilter
import com.anisequel.domain.notifications.NotificationGroup
import com.anisequel.domain.notifications.NotificationPreferences
import com.anisequel.domain.notifications.NotificationReadState
import com.anisequel.domain.notifications.groupNotifications
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Everything the inbox draws, in one snapshot.
 *
 * [items] is the whole unfiltered set fetched so far. It is the pagination
 * cursor and the source of every filter view; [groups] is only the current
 * filter, already folded. Keeping both means a chip switch never re-requests a
 * page, and the unread pill stays about the inbox rather than the chip.
 */
data class NotificationUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val loadingMore: Boolean = false,
    val items: List<InboxNotification> = emptyList(),
    val groups: List<NotificationGroup> = emptyList(),
    val filter: NotificationFilter = NotificationFilter.ALL,
    val preferences: NotificationPreferences = NotificationPreferences(),
    val unreadCount: Int = 0,
    val hasMore: Boolean = false,
    val error: String? = null,
    val loadMoreError: String? = null,
    val notice: String? = null
) {
    val filters: List<NotificationFilter> get() = preferences.visibleFilters()
}

/**
 * The notification inbox state holder.
 *
 * The read mark is local and account-keyed (see [NotificationReadState]):
 * AniList has no per-item read flag, and this screen must not send
 * resetNotificationCount just because it opened. The server unread count is
 * used exactly once, to seed the first baseline; after that the watermark and
 * the individually-read set are the only things that decide a dot.
 *
 * Stale work is fenced with [loadGeneration]. A refresh, an account change and
 * an in-flight page can all finish out of order; only the generation that owns
 * the current state may write it. The filter itself never reaches the network:
 * pages are fetched unfiltered so every chip is a local slice.
 */
class NotificationViewModel(
    private val aniListRepository: AniListRepository,
    private val preferencesStore: NotificationPreferencesStore,
    private val readStore: NotificationReadStore
) : ViewModel() {

    private val _state = MutableStateFlow(NotificationUiState())
    val state: StateFlow<NotificationUiState> = _state.asStateFlow()

    private var accountCollector: Job? = null
    private var accountId: Int? = null
    private var readState = NotificationReadState()
    private var nextPage = 2
    private var loadGeneration = 0
    private var revision = 0
    private var rateLimitedUntilMillis = 0L
    private val filterCache = mutableMapOf<NotificationFilter, Pair<Int, List<NotificationGroup>>>()

    init {
        viewModelScope.launch {
            preferencesStore.preferences.collect { preferences ->
                val previous = _state.value.preferences
                _state.update {
                    it.copy(
                        preferences = preferences,
                        filter = if (preferences.isFilterEnabled(it.filter)) it.filter else NotificationFilter.ALL,
                        notice = if (previous.trackingEnabled && !preferences.trackingEnabled) {
                            "Unread dots are off in Settings."
                        } else {
                            it.notice
                        }
                    )
                }
                invalidate()
            }
        }
    }

    /**
     * Called when the screen enters composition.
     *
     * This used to force a network read on every entry, which made re-opening
     * the tab feel like a reload even when AniList had answered moments earlier.
     * The page cache is epoch-keyed and short-lived, so an unforced read is still
     * fresh rather than permanently stale; pull-to-refresh remains the explicit
     * way to ask the server now. A start while the previous request is running is
     * ignored rather than queued.
     */
    fun start() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.refreshing) return
        load(forceRefresh = false)
    }

    fun selectFilter(filter: NotificationFilter) {
        if (!_state.value.preferences.isFilterEnabled(filter)) return
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, loadMoreError = null) }
        rebuild()
    }

    fun refresh() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.refreshing) return
        if (pauseForRateLimit()) return
        load(forceRefresh = true)
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    /** One policy for pull, retry and the next page, so none of them bypass a 429. */
    private fun pauseForRateLimit(): Boolean {
        val now = System.currentTimeMillis()
        if (now >= rateLimitedUntilMillis) return false
        val seconds = ((rateLimitedUntilMillis - now) / 1000L).coerceAtLeast(1L)
        _state.update { it.copy(notice = "AniList asked for a pause. Try again in ${seconds}s.") }
        return true
    }

    fun retry() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.refreshing || snapshot.loadingMore) return
        if (snapshot.items.isEmpty()) {
            if (!pauseForRateLimit()) load(forceRefresh = true)
        } else {
            loadMore()
        }
    }

    /** Marks every member of [group] read and leaves the opening to the caller. */
    fun onGroupOpened(group: NotificationGroup) {
        if (!_state.value.preferences.trackingEnabled) return
        if (!_state.value.preferences.markReadOnOpen) return
        val ids = group.ids
        if (ids.isEmpty()) return
        readState = ids.fold(readState) { state, id -> state.markRead(id) }
        invalidate()
        val account = accountId ?: return
        viewModelScope.launch { readStore.markRead(account, ids) }
    }

    /**
     * Marks the whole inbox read, not the visible chip.
     *
     * The watermark is the newest loaded id, so it covers every older row on
     * pages this device has not fetched and every category hidden by a filter.
     * Notifications that arrive after the tap have larger ids and stay unread,
     * which is the correct behaviour for a page that is already on screen.
     */
    fun markAllRead() {
        val throughId = _state.value.items.maxOfOrNull { it.id } ?: return
        readState = readState.markAllRead(throughId)
        invalidate()
        val account = accountId ?: return
        viewModelScope.launch { readStore.markAllRead(account, throughId) }
    }

    /**
     * Fetches page one and, on the first successful page, anchors the baseline.
     *
     * The server count is consumed here and nowhere else. If the account has an
     * anchored read state, a refresh does not re-seed it: rows that were read
     * must not become new because the server count happened to grow.
     */
    private fun load(forceRefresh: Boolean) {
        var generation = ++loadGeneration
        _state.update {
            it.copy(
                loading = it.items.isEmpty(),
                refreshing = it.items.isNotEmpty(),
                // A refresh fences any page request that was in flight. Clearing
                // the flag here is what lets the next page start: the fenced
                // response returns early and would never lower it again.
                loadingMore = false,
                loadMoreError = null,
                error = null,
                notice = null
            )
        }
        viewModelScope.launch {
            val viewer = aniListRepository.getViewer().getOrElse { failure ->
                failInitial(generation, failure)
                return@launch
            }
            if (generation != loadGeneration) return@launch
            if (prepareAccount(viewer.id)) {
                // An account boundary makes every page still in flight belong to
                // somebody else. Moving the generation makes those writes fail their
                // fence even though the refresh that replaced them has the same route.
                generation = ++loadGeneration
            }
            if (generation != loadGeneration) return@launch

            aniListRepository.getNotifications(page = 1, forceRefresh = forceRefresh)
                .onSuccess { page ->
                    if (generation != loadGeneration) return@onSuccess
                    // A reset in Settings can land after this request began. Re-read
                    // the fence before deciding whether to seed, or the page would
                    // treat the old anchored mark as current and leave the reset
                    // half-applied until the next entry. Equal generations union
                    // optimistic reads instead of replacing them.
                    val stored = readStore.load(viewer.id)
                    if (generation != loadGeneration) return@onSuccess
                    readState = readState.reconcile(stored)
                    if (!readState.anchored) {
                        val anchored = NotificationReadState.anchorFor(
                            ids = page.notifications.map { it.id },
                            serverUnreadCount = page.serverUnreadCount
                        ).copy(
                            resetGeneration = readState.resetGeneration,
                            // Keep a row the reader opened after a Settings reset
                            // but before this page re-seeded the baseline.
                            readIds = readState.readIds
                        )
                        readState = anchored
                        readStore.anchor(viewer.id, anchored)
                        if (generation != loadGeneration) return@onSuccess
                    }
                    nextPage = 2
                    // A cached page-one read keeps the older pages already on
                    // screen, or opening the tab would silently collapse a long
                    // inbox back to its first page. A pull-to-refresh is the
                    // deliberate replacement: the repository cleared every page,
                    // so keeping rows from the old snapshot could leave a deleted
                    // row on screen with no cache behind it.
                    val keptOlder = if (forceRefresh) {
                        emptyList()
                    } else {
                        olderRowsThan(page.notifications, _state.value.items)
                    }
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            items = merge(page.notifications, keptOlder),
                            hasMore = page.hasMore,
                            error = null
                        )
                    }
                    invalidate()
                }
                .onFailure { failure -> failInitial(generation, failure) }
        }
    }

    private fun failInitial(generation: Int, failure: Throwable) {
        if (generation != loadGeneration) return
        val message = failure.toInboxMessage()
        val limitedUntil = failure.rateLimitWaitMillis()
        if (limitedUntil != null) rateLimitedUntilMillis = System.currentTimeMillis() + limitedUntil
        _state.update {
            it.copy(
                loading = false,
                refreshing = false,
                loadingMore = false,
                // A page-one failure leaves the cursor ambiguous: a forced
                // refresh has cleared the repository's pages, and even an
                // unforced read can have moved the boundary the old `nextPage`
                // was measured against. Stop paging until a page-one success
                // resets the cursor; pulling again is the only safe way forward.
                hasMore = false,
                error = if (it.items.isEmpty()) message else null,
                notice = if (it.items.isEmpty()) null else message
            )
        }
    }

    /** Fetches the next page. The guard runs before any state is written. */
    fun loadMore() {
        val snapshot = _state.value
        if (!snapshot.hasMore || snapshot.loading || snapshot.loadingMore || snapshot.refreshing) return
        if (pauseForRateLimit()) return
        val generation = loadGeneration
        val page = nextPage
        _state.update { it.copy(loadingMore = true, loadMoreError = null) }
        viewModelScope.launch {
            aniListRepository.getNotifications(page = page)
                .onSuccess { result ->
                    if (generation != loadGeneration) return@onSuccess
                    nextPage = page + 1
                    _state.update {
                        it.copy(
                            loading = false,
                            loadingMore = false,
                            items = merge(it.items, result.notifications),
                            hasMore = result.hasMore,
                            loadMoreError = null
                        )
                    }
                    invalidate()
                }
                .onFailure { failure ->
                    if (generation != loadGeneration) return@onFailure
                    val limited = failure.rateLimitWaitMillis()
                    if (limited != null) rateLimitedUntilMillis = System.currentTimeMillis() + limited
                    _state.update {
                        it.copy(
                            loadingMore = false,
                            loadMoreError = failure.toInboxMessage()
                        )
                    }
                }
        }
    }

    /**
     * Loads the local mark for [id] the first time, and never again for the same
     * account.
     *
     * Reloading on every refresh looked harmless and was not: a mark written a
     * moment earlier is already in memory, while the DataStore write may still be
     * in flight. The reload could read the old state and bring a just-read row back
     * as unread. Returning whether the account actually changed also lets [load]
     * fence page requests that belonged to the previous account.
     */
    private suspend fun prepareAccount(id: Int): Boolean {
        if (accountId == id) return false
        accountCollector?.cancel()
        accountId = id
        readState = readStore.load(id)
        filterCache.clear()
        _state.update {
            it.copy(
                // The old account's rows are gone, so this is an initial load
                // even when the caller started from a pull-to-refresh state.
                loading = true,
                refreshing = false,
                items = emptyList(),
                groups = emptyList(),
                unreadCount = 0,
                hasMore = false,
                loadMoreError = null
            )
        }
        invalidate()
        accountCollector = viewModelScope.launch {
            readStore.state(id).collect { stored ->
                if (accountId != id) return@collect
                // Equal generations are unioned, so a Flow emission queued
                // before an optimistic mark cannot resurrect the row. A newer
                // generation is the Settings reset and is allowed to clear it.
                val reconciled = readState.reconcile(stored)
                if (reconciled != readState) {
                    readState = reconciled
                    invalidate()
                }
            }
        }
        return true
    }

    /**
     * Recomputes the current chip and the pill in one pass.
     *
     * The filter cache is keyed by [revision], which moves whenever the data,
     * preferences or read mark change. Switching back to a chip whose revision
     * is still current is therefore free, and a change can never leave an old
     * dot on a cached row.
     */
    private fun invalidate() {
        revision += 1
        rebuild()
    }

    private fun rebuild() {
        val snapshot = _state.value
        val enabled = snapshot.items.filter { snapshot.preferences.isEnabled(it.kind) }
        val tracking = snapshot.preferences.trackingEnabled
        val isUnread: (InboxNotification) -> Boolean = { tracking && readState.isUnread(it.id) }
        val unread = if (tracking) enabled.count(isUnread) else 0
        val filter = if (snapshot.preferences.isFilterEnabled(snapshot.filter)) {
            snapshot.filter
        } else {
            NotificationFilter.ALL
        }
        val cached = filterCache[filter]
        val groups = if (cached?.first == revision) {
            cached.second
        } else {
            val value = groupNotifications(enabled.filter { filter.matches(it.kind) }, isUnread)
            filterCache[filter] = revision to value
            value
        }
        _state.update { it.copy(filter = filter, groups = groups, unreadCount = unread) }
    }

    private fun merge(
        incoming: List<InboxNotification>,
        existing: List<InboxNotification>
    ): List<InboxNotification> = (incoming + existing)
        .distinctBy { it.id }
        .sortedWith(
            compareByDescending<InboxNotification> { it.createdAt ?: 0 }
                .thenByDescending { it.id }
        )

    /**
     * Rows from [existing] that are older than everything in [page].
     *
     * Page one is not guaranteed to reach the same boundary twice: new rows can
     * push its oldest row down, and a deleted row can pull it up. The oldest
     * row's `(createdAt, id)` is therefore the boundary the older snapshot is
     * measured against, not a timestamp guessed from the page length. An empty
     * page keeps whatever was there rather than blanking the inbox on a strange
     * response.
     */
    private fun olderRowsThan(
        page: List<InboxNotification>,
        existing: List<InboxNotification>
    ): List<InboxNotification> {
        if (page.isEmpty()) return existing
        val pageIds = page.mapTo(HashSet<Int>(page.size)) { it.id }
        val boundary = page.minWith(
            compareBy<InboxNotification> { it.createdAt ?: 0 }.thenBy { it.id }
        )
        val boundaryTime = boundary.createdAt ?: 0
        return existing.filter { row ->
            if (row.id in pageIds) return@filter false
            val time = row.createdAt ?: 0
            time < boundaryTime || (time == boundaryTime && row.id < boundary.id)
        }
    }

    companion object {
        fun factory(
            aniListRepository: AniListRepository,
            preferencesStore: NotificationPreferencesStore,
            readStore: NotificationReadStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NotificationViewModel(aniListRepository, preferencesStore, readStore) as T
        }
    }
}

private fun Throwable.toInboxMessage(): String = when (this) {
    is AniListException -> when (kind) {
        AniListErrorKind.OFFLINE -> "You are offline. The inbox will wait."
        AniListErrorKind.RATE_LIMITED -> "AniList is rate limiting. Give it a moment."
        AniListErrorKind.INVALID_SESSION -> "Your AniList session has expired. Sign in again."
        AniListErrorKind.SERVER -> "AniList is having a bad moment. Try again."
        AniListErrorKind.MALFORMED_RESPONSE -> "This build could not read the inbox response."
        else -> message ?: "Could not load notifications."
    }
    else -> message ?: "Could not load notifications."
}

private fun Throwable.rateLimitWaitMillis(): Long? = (this as? AniListException)
    ?.takeIf { it.kind == AniListErrorKind.RATE_LIMITED }
    ?.let { (it.retryAfterSeconds ?: 30).coerceAtLeast(1) * 1000L }
