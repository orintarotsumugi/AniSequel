package com.anisequel.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.anisequel.data.model.PersonProfileState
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.repository.AniListRepository
import kotlinx.coroutines.CoroutineScope
import com.anisequel.ui.components.detail.PersonProfileSheet

/**
 * Everything a screen needs to offer character and staff profiles, in one object.
 *
 * ## Why this exists
 *
 * Three screens host [PersonProfileSheet] - the dashboard, a user's profile, and a
 * studio page - and each needs the same five things: a store, a read of its state,
 * two callbacks to hand the detail sheet, and the sheet itself drawn as a sibling of
 * that sheet. Written out per screen that is about forty lines of identical code
 * three times over, and the drift is the ordinary kind: one screen gains a retry
 * button and the other two do not.
 *
 * ## Why the callbacks are nullable
 *
 * A host with no repository - the demo dashboard - must draw the cast and crew
 * cards as captions rather than as buttons whose tap opens a sheet that can only
 * report a failure. A `(Int) -> Unit = { }` default cannot express that: the card
 * cannot tell a no-op from a real handler, so it would draw a tappable control that
 * does nothing. That is the dead control this whole feature replaced, so `null` is
 * the honest answer and the card reads it.
 *
 * ## Why [Sheet] is a method rather than something the screen calls
 *
 * The sheet has to be drawn by the screen, not by the sheet it sits beside: a
 * `ModalBottomSheet` is its own window, so one drawn from inside the detail sheet
 * covers it. That relationship is invisible at the call site if the screen has to
 * remember it, and it is exactly the relationship that is easy to get wrong.
 */
class PersonProfileHost internal constructor(
    private val store: PersonProfileStore,
    /**
     * Whether this host can open profiles at all.
     *
     * Carried rather than derived from [store], because the store tolerates a null
     * repository so it can answer with an error instead of throwing - and a host
     * with no repository should never get as far as needing that answer.
     */
    private val enabled: Boolean
) {
    /** Handed to `SequelDetailSheet`. Null draws captions; see the class KDoc. */
    val onOpenCharacter: ((Int) -> Unit)? =
        if (enabled) store::openCharacter else null

    /** The same, for the crew row. */
    val onOpenStaff: ((Int) -> Unit)? =
        if (enabled) store::openStaff else null

    /**
     * Draws the profile sheet, if one is open.
     *
     * [onOpenAppearance] is what the host does with a tap on the titles row. It is
     * a parameter because the answer is host-specific and there is no right one:
     * the dashboard routes it into the sheet's own relation navigation, a profile
     * replaces its selected media, and a studio page does the same. Each is a
     * different screen's state, and the sheet cannot reach any of them.
     */
    @Composable
    fun Sheet(onOpenAppearance: (ProfileAppearance) -> Unit) {
        // Compared against `Closed` rather than null-checked: the state is not
        // nullable, and `Closed` is the answer meaning "nobody is looking at this".
        val state by store.state.collectAsState()
        if (state == PersonProfileState.Closed) return

        PersonProfileSheet(
            state = state,
            onDismiss = { store.close() },
            onRetry = { store.retry() },
            onOpenAppearance = { appearance ->
                // Close first, then hand the title over. The host opens it with its
                // own state, and leaving this sheet drawn over it would cover the
                // very thing the reader just tapped their way to.
                store.close()
                onOpenAppearance(appearance)
            }
        )
    }
}

/**
 * Remembers a [PersonProfileHost] for the life of this composition.
 *
 * Pass a null [repository] on a host that cannot open profiles; see the class KDoc
 * for why that is not the same as passing a repository that will fail.
 */
@Composable
fun rememberPersonProfileHost(
    repository: AniListRepository?,
    isDemo: Boolean = false,
    scope: CoroutineScope? = null
): PersonProfileHost {
    val store = remember(repository, isDemo, scope) {
        PersonProfileStore(repository, isDemo, scope)
    }
    DisposableEffect(store) {
        onDispose { store.dispose() }
    }
    return remember(store, repository != null) {
        PersonProfileHost(store, enabled = repository != null)
    }
}