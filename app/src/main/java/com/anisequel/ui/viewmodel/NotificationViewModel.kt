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
    val isEmpty: Boolean get() = !loading && groups.isEmpty()
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
 * Stale work is fenced with [loadGeneration]. A refresh, a filter change and an
 * in-flight page can all finish out of order; only the generation that owns the
 * current state may write it. The filter itself never reaches the network: pages
 * are fetched unfiltered so every chip is a local slice.
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
    // False while the first page has neither anchored nor failed on this account.
    // It distinguishes the anchor write racing the DataStore flow from a reset
    // the reader performs later, which is allowed to clear the local mark.
    private var baselineResolved = false
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
     * Every entry asks AniList. A ViewModel can be recreated by popping and
     * pushing this route long after the app-scoped page cache was written, so
     * choosing "no force" for an empty state would show that cached page without
     * a request - the stale inbox this screen exists to avoid. A start while the
     * previous request is still running is ignored rather than queued.
     */
    fun start() {
        val snapshot = _state.value
        if (snapshot.loading || snapshot.refreshing) return
        load(forceRefresh = true)
    }

    fun selectFilter(filter: NotificationFilter) {
        if (!_state.value.preferences.isFilterEnabled(filter)) return
        if (_state.value.filter == filter) return
        _state.update { it.copy(filter = filter, loadMoreError = null) }
        rebuild()
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now < rateLimitedUntilMillis) {
            val seconds = ((rateLimitedUntilMillis - now) / 1000L).coerceAtLeast(1L)
            _state.update { it.copy(notice = "AniList asked for a pause. Try again in ${seconds}s.") }
            return
        }
        load(forceRefresh = true)
    }

    fun dismissNotice() {
        _state.update { it.copy(notice = null) }
    }

    fun retry() {
        if (_state.value.items.isEmpty()) {
            load(forceRefresh = true)
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
        _state.update { it.copy() }
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
        _state.update { it.copy() }
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

            aniListRepository.getNotifications(page = 1, forceRefresh = forceRefresh)
                .onSuccess { page ->
                    if (generation != loadGeneration) return@onSuccess
                    val current = _state.value.items
                    val merged = merge(page.notifications, current)
                    if (!readState.anchored) {
                        val anchored = NotificationReadState.anchorFor(
                            ids = page.notifications.map { it.id },
                            serverUnreadCount = page.serverUnreadCount
                        )
                        readState = anchored
                        readStore.anchor(viewer.id, anchored)
                    }
                    baselineResolved = true
                    nextPage = 2
                    _state.update {
                        it.copy(
                            loading = false,
                            refreshing = false,
                            items = merged,
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
        baselineResolved = true
        _state.update {
            it.copy(
                loading = false,
                refreshing = false,
                loadingMore = false,
                error = if (it.items.isEmpty()) message else null,
                notice = if (it.items.isEmpty()) null else message
            )
        }
    }

    /** Fetches the next page. The guard runs before any state is written. */
    fun loadMore() {
        val snapshot = _state.value
        if (!snapshot.hasMore || snapshot.loading || snapshot.loadingMore || snapshot.refreshing) return
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
        baselineResolved = readState.anchored
        filterCache.clear()
        _state.update { it.copy(items = emptyList(), groups = emptyList(), unreadCount = 0) }
        invalidate()
        accountCollector = viewModelScope.launch {
            readStore.state(id).collect { stored ->
                if (accountId != id) return@collect
                // The anchor write and this Flow both run around the first page.
                // A stored "not anchored" emission must not undo an anchor the
                // current request just established; the write will follow.
                if (!baselineResolved && readState.anchored && !stored.anchored) return@collect
                if (readState != stored) {
                    readState = stored
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
