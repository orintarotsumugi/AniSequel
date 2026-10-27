package com.anisequel.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaNode
import com.anisequel.data.repository.AniListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

/**
 * The whole of [MediaDetailStore] that a screen needs, held once for the app.
 *
 * ## Why one owner now
 *
 * The store is the one implementation of the detail sheet's on-demand fetch. It
 * used to have four owners: the dashboard, the activity feed and the studio page
 * each held one through a ViewModel, and the Library held this one because it is
 * not ViewModel-backed. Four caches meant the same title opened from two tabs was
 * two requests, two loading flags and two merge answers - the "it opens a
 * different thing in the profile" report - and a title the Library had already
 * loaded still arrived thin somewhere else.
 *
 * The Library, the Profile and the studio page now share this host, created once in
 * `AppNavigation`, so opening a title is the same page wherever the tap came from
 * and leaving a tab no longer throws away a synopsis. The dashboard keeps its own
 * store for one reason: that store also folds each answer into the recommendation
 * cards behind the sheet ([MediaDetailStore]'s `onLoaded`), which is a
 * dashboard-only write. It is an addition, not a different rule.
 *
 * ## Why the scope is owned here
 *
 * The store takes a [CoroutineScope] because a fetch must outlive the tap that
 * started it, and a ViewModel passes its own `viewModelScope`. This owner has
 * to build one instead, created - and cancelled - by [rememberMediaDetailHost]
 * rather than handed in by its caller: a scope belongs to whoever made it (the
 * rule `PersonProfileStore` records), and one a caller disposed could cancel work
 * that was never theirs.
 *
 * A `SupervisorJob`, so the failures stay per-fetch: the store already swallows a
 * failed detail load, but the scope under it must not take the other requests in
 * flight down with it. `Dispatchers.Main.immediate` is the dispatcher the
 * `viewModelScope`s run on, so a host-owned store publishes at the same point in
 * the frame as a ViewModel-owned one - the sheet cannot tell the two apart.
 *
 * ## Why [isDemo] is still a parameter
 *
 * Demo data is already complete, so the store's rule is "`isDemo` means do not
 * fetch". Whether there is a session used to be a property of the route, which is
 * why the flag lived on the host. The shared host passes false: it also serves the
 * studio page, whose titles are live even in the demo, so a blanket "do not fetch"
 * would leave a real studio title's sheet half empty. The demo's own summaries are
 * complete already, so the fetch they trigger is redundant rather than wrong. The
 * parameter stays for a caller that only ever holds complete summaries.
 */
class MediaDetailHost internal constructor(
    private val store: MediaDetailStore
) {
    /** The nodes fetched so far, keyed by media id. See [MediaDetailStore.details]. */
    val details: StateFlow<Map<Int, MediaNode>> get() = store.details

    /** Followed users' entries per title, keyed by media id. See [MediaDetailStore.friends]. */
    val friends: StateFlow<Map<Int, List<MediaFriend>>> get() = store.friends

    /** Which media ids are still on the wire, for the sheet's spinner. */
    val loading: StateFlow<Set<Int>> get() = store.loading

    /** What `SequelDetailSheet` calls when the reader opens a title. */
    val onLoadDetail: (Int, String?) -> Unit = store::load

    /** What the same sheet calls for the friends card, separately from the detail. */
    val onLoadFriends: (Int, String?) -> Unit = store::loadFriends
}

/**
 * Remembers a [MediaDetailHost] for the life of this composition, with a scope
 * of its own that is cancelled with it.
 *
 * Called once, at the app's navigation root, so every tab that draws the sheet
 * shares one store. That is the point: a fetch started from the Library is still
 * there when the reader opens the same title from a profile, and one page's
 * spinner is the spinner on every page that can see that title. Call it per
 * *route* and the sharing - and the bug it fixes - comes back.
 */
@Composable
fun rememberMediaDetailHost(
    repository: AniListRepository,
    isDemo: Boolean = false
): MediaDetailHost {
    val scope = remember(repository, isDemo) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    val host = remember(scope, repository, isDemo) {
        MediaDetailHost(
            MediaDetailStore(repository = repository, scope = scope, isDemo = isDemo)
        )
    }
    // Keyed on the scope rather than on the host: exactly the scope this
    // composition created is what gets cancelled, so rebuilding the host for a
    // new repository cannot leave the previous scope's requests running.
    DisposableEffect(scope) {
        onDispose { scope.cancel() }
    }
    return host
}
