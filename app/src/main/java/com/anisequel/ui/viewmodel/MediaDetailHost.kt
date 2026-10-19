package com.anisequel.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.anisequel.data.model.MediaNode
import com.anisequel.data.repository.AniListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.StateFlow

/**
 * The whole of [MediaDetailStore] that a screen without a ViewModel needs.
 *
 * ## Why a fourth owner exists
 *
 * The store is the one implementation of the detail sheet's on-demand fetch, with
 * three owners today: the dashboard, the activity feed and the studio page, each
 * holding one through its ViewModel. The Library page is not ViewModel-backed, so
 * somebody has to hold the fourth store - this, the two flows and the load
 * callback, with nothing of its own.
 *
 * ## Why the scope is owned here
 *
 * The store takes a [CoroutineScope] because a fetch must outlive the tap that
 * started it, and each ViewModel passes its own `viewModelScope`. This owner has
 * to build one instead, created - and cancelled - by [rememberMediaDetailHost]
 * rather than handed in by its caller: a scope belongs to whoever made it (the
 * rule `PersonProfileStore` records), and one a caller disposed could cancel work
 * that was never theirs.
 *
 * A `SupervisorJob`, so the failures stay per-fetch: the store already swallows a
 * failed detail load, but the scope under it must not take the other requests in
 * flight down with it. `Dispatchers.Main.immediate` is the dispatcher the three
 * `viewModelScope`s run on, so a host-owned store publishes at the same point in
 * the frame as a ViewModel-owned one - the sheet cannot tell the two apart.
 *
 * ## Why [isDemo] is carried
 *
 * Demo data is already complete, and there is no session to fetch a title with.
 * The store answers both with one rule - `isDemo` means "do not fetch" - but
 * somebody has to say it, and the screen cannot: whether there is a session is a
 * property of the route, not of the sheet.
 */
class MediaDetailHost internal constructor(
    private val store: MediaDetailStore
) {
    /** The nodes fetched so far, keyed by media id. See [MediaDetailStore.details]. */
    val details: StateFlow<Map<Int, MediaNode>> get() = store.details

    /** Which media ids are still on the wire, for the sheet's spinner. */
    val loading: StateFlow<Set<Int>> get() = store.loading

    /** What `SequelDetailSheet` calls when the reader opens a title. */
    val onLoadDetail: (Int, String?) -> Unit = store::load
}

/**
 * Remembers a [MediaDetailHost] for the life of this composition, with a scope
 * of its own that is cancelled with it.
 *
 * Mirrors `rememberPersonProfileHost`: built once per screen and disposed when
 * that screen leaves, so nothing is shared between two callers and one page's
 * fetch cannot appear in another page's spinner.
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
