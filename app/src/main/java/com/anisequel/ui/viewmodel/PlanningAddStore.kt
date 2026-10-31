package com.anisequel.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.isManga
import com.anisequel.data.repository.AniListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * What one title's quick-add control is doing, or has just done.
 *
 * Every field the dashboard's `MissedSequel` carries for its own button lives
 * here instead, keyed by media id: a title page can be showing any entry in the
 * app now, including one that has no parent and therefore no `MissedSequel` to
 * hang the flags off.
 *
 * [error] is deliberately part of the state rather than a snackbar event. The
 * sheet is opened from four screens and only the dashboard owns a
 * `SnackbarHostState`, so a write that failed on the Library page used to have
 * nowhere to say so. The action row draws this under its buttons instead, which
 * keeps the failure attached to the control that caused it.
 */
data class QuickAddStatus(
    val isAddingToPlanning: Boolean = false,
    val isAddedToPlanning: Boolean = false,
    val isAddingToWatching: Boolean = false,
    val isAddedToWatching: Boolean = false,
    val error: String? = null
) {
    val isBusy: Boolean get() = isAddingToPlanning || isAddingToWatching
}

/**
 * The store's key: the media *and* its namespace.
 *
 * The sheet walks relations in place, so one host really does hold two subjects in
 * sequence - and a franchise walk crosses the anime/manga boundary routinely, since
 * `source` relations are manga. AniList numbers the two independently, so keying by
 * id alone made `manga/5` inherit the "Added" confirmation and the in-flight flag
 * that `anime/5` earned, on a title nothing was ever written for. That is the same
 * collision [MediaCoverKey] was introduced for; the reasoning that this map was
 * single-subject and therefore safe mistook "one subject at a time" for "one
 * subject per visit".
 *
 * A node whose own `type` is null is counted as anime, which is [isManga]'s rule and
 * keeps a summary node and the detail node for the same show on one key.
 */
fun quickAddKey(media: MediaNode): MediaCoverKey =
    MediaCoverKey(type = if (media.isManga) "MANGA" else "ANIME", id = media.id)

/**
 * One-tap "Add to Planning" for **any** title the detail sheet is showing.
 *
 * ## Why this is not [DashboardViewModel.addToPlanning]
 *
 * That one takes a `MissedSequel`: it updates the card that owns the button,
 * recomputes the dashboard's list and emits a snackbar the dashboard can draw.
 * All three are dashboard facts, and none of them exist on the Library, a studio
 * page or somebody's activity feed - which is why those hosts used to pass an
 * empty lambda and the title page drew no add button at all.
 *
 * A title page is now the same page everywhere, so the capability belongs to the
 * sheet rather than to the list that opened it. This store is the whole of that
 * capability: the write, the in-flight flags and the confirmation, keyed per title
 * so walking a franchise keeps each one's own state - see [quickAddKey] for why the
 * key is a namespace and an id rather than an id alone.
 */
class PlanningAddStore(
    /**
     * Null when the host has no connection (the demo route). Nothing can be
     * written then - [canWrite] is false with it - so this is the "there is
     * nobody to ask" half of the same rule, not a second one.
     */
    private val repository: AniListRepository?,
    private val scope: CoroutineScope,
    private val canWrite: Boolean
) {
    private val _statuses = MutableStateFlow<Map<MediaCoverKey, QuickAddStatus>>(emptyMap())

    /** Per-media add state, for the action row to draw its button from. */
    val statuses: StateFlow<Map<MediaCoverKey, QuickAddStatus>> = _statuses.asStateFlow()

    fun addToPlanning(media: MediaNode) = submit(media, watching = false)

    fun addToWatching(media: MediaNode) = submit(media, watching = true)

    private fun submit(media: MediaNode, watching: Boolean) {
        val key = quickAddKey(media)
        val current = _statuses.value[key] ?: QuickAddStatus()
        // A second tap while the first is on the wire would send the same write
        // twice. AniList would accept both, and the second would win - which is
        // the same state with two requests spent.
        if (current.isBusy) return

        val repository = repository
        if (!canWrite || repository == null) {
            update(key) { copy(error = SIGN_IN_MESSAGE) }
            return
        }

        update(key) {
            copy(
                isAddingToPlanning = !watching,
                isAddingToWatching = watching,
                error = null
            )
        }

        scope.launch {
            val result = repositoryResult {
                if (watching) {
                    repository.addToWatching(media.id)
                } else {
                    repository.addToPlanning(media.id)
                }
            }
            result.fold(
                onSuccess = {
                    update(key) {
                        copy(
                            isAddingToPlanning = false,
                            isAddingToWatching = false,
                            isAddedToPlanning = !watching,
                            isAddedToWatching = watching,
                            error = null
                        )
                    }
                },
                onFailure = { failure ->
                    update(key) {
                        copy(
                            isAddingToPlanning = false,
                            isAddingToWatching = false,
                            error = failure.message ?: if (watching) {
                                "Could not add this title to your Watching list."
                            } else {
                                "Could not add this title to your Planning list."
                            }
                        )
                    }
                }
            )
        }
    }

    private fun update(
        key: MediaCoverKey,
        transform: QuickAddStatus.() -> QuickAddStatus
    ) {
        val current = _statuses.value[key] ?: QuickAddStatus()
        _statuses.value = _statuses.value + (key to current.transform())
    }

    private companion object {
        const val SIGN_IN_MESSAGE =
            "Connect your own AniList account to add entries to your Planning list."
    }
}

/**
 * The whole of [PlanningAddStore] a screen needs, plus the two callbacks.
 *
 * Mirrors [MediaDetailHost]: one owner per screen, with the sheet given flows and
 * lambdas rather than the store itself, so the components package does not have
 * to know how a write is routed.
 */
class PlanningAddHost internal constructor(
    private val store: PlanningAddStore
) {
    val statuses: StateFlow<Map<MediaCoverKey, QuickAddStatus>> get() = store.statuses

    val onAddToPlanning: (MediaNode) -> Unit = store::addToPlanning

    val onAddToWatching: (MediaNode) -> Unit = store::addToWatching
}

/**
 * Remembers a [PlanningAddHost] for the life of this composition, with its own
 * scope.
 *
 * The scope is owned here for the reason [rememberMediaDetailHost] gives: a write
 * must outlive the sheet that started it, and a scope belongs to whoever created
 * it. Cancelling on dispose stops a request for a screen the reader has left.
 *
 * [canWrite] is part of both `remember` keys because it changes when the session
 * does, and a host built for a signed-out reader must not keep serving "added"
 * flags to the account that signs in behind it.
 */
@Composable
fun rememberPlanningAddHost(
    repository: AniListRepository?,
    canWrite: Boolean
): PlanningAddHost {
    val scope = remember(repository, canWrite) {
        CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }
    val host = remember(scope, repository, canWrite) {
        PlanningAddHost(
            PlanningAddStore(repository = repository, scope = scope, canWrite = canWrite)
        )
    }
    DisposableEffect(scope) {
        onDispose { scope.cancel() }
    }
    return host
}
