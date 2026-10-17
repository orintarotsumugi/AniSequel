package com.anisequel.ui.viewmodel

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.PersonProfileState
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.model.toLoadedState
import com.anisequel.data.repository.AniListRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The person whose profile sheet is open, and the fetching behind it.
 *
 * ## Why this is not a ViewModel
 *
 * It is per-destination UI state with nothing to persist, and it is owned by whatever
 * drew the sheet. A `ViewModel` would need a factory and a lifecycle owner for a piece
 * of state that is `Closed` whenever nobody is looking at it - which is most of the
 * time - so it holds a plain [CoroutineScope] instead and is cancelled with the
 * composition. The scope is a [SupervisorJob] so one failed fetch cannot cancel the
 * others queued behind it.
 *
 * ## The id in the state is the guard
 *
 * A response is only published if it still answers the question that is open. A reader
 * who taps a character, dismisses, and taps a staff member while the first is in
 * flight gets the first answer back last - and without the guard it would replace the
 * staff member's face with the character's. Every fetch in this app has the same race
 * and the same answer; see `UserActivityViewModel.openComments`.
 *
 * ## Why it lives here and not on each of the three ViewModels
 *
 * The sheet is reachable from a cast card on a title opened by the dashboard, by a
 * profile, or by a studio page - three different ViewModels, one sheet. Three copies of
 * this state would be three things to keep in step, and the bug that produces is always
 * the same: an id fetched for one screen surfacing on another, because the sheet
 * outlived the screen that opened it.
 */
class PersonProfileStore(
    private val repository: AniListRepository,
    private val isDemo: Boolean = false
) {
    private val scope = CoroutineScope(SupervisorJob())

    private val _state = MutableStateFlow<PersonProfileState>(PersonProfileState.Closed)
    val state: StateFlow<PersonProfileState> = _state.asStateFlow()

    /**
     * The request that is still live, as kind and id.
     *
     * Held beside the state rather than read out of it, because by the time a response
     * arrives the state may have moved to [PersonProfileState.Closed] and the response
     * would then have nothing to compare itself against.
     */
    private var pending: Pair<PersonKind, Int>? = null

    /** Opens a character's profile. A no-op while that same character is loading. */
    fun openCharacter(id: Int, name: String? = null) = open(PersonKind.CHARACTER, id, name)

    /** Opens a staff member's profile, on the same terms as [openCharacter]. */
    fun openStaff(id: Int, name: String? = null) = open(PersonKind.STAFF, id, name)

    private fun open(kind: PersonKind, id: Int, name: String?) {
        if (pending == (kind to id)) return
        pending = kind to id
        _state.value = PersonProfileState.Loading(kind, name)

        scope.launch {
            val result = if (isDemo) {
                Result.failure<Any>(
                    IllegalStateException("There is no ${kind.label()} to look up in the demo.")
                )
            } else if (kind == PersonKind.CHARACTER) {
                repository.getCharacterProfile(id)
            } else {
                repository.getStaffProfile(id)
            }

            // The guard. A response for a sheet the reader has already replaced is
            // dropped rather than painted over whatever they are looking at now.
            if (pending != (kind to id)) return@launch
            pending = null

            _state.value = result.fold(
                onSuccess = { it.toLoadedState() },
                onFailure = { error ->
                    PersonProfileState.Failed(
                        kind = kind,
                        // Carried so `retry()` has something to re-run. A retry that
                        // cannot name its own subject is a button that does nothing,
                        // which is the state this whole sheet exists to end.
                        id = id,
                        name = name,
                        message = error.message ?: "Couldn't load that profile."
                    )
                }
            )
        }
    }

    fun close() {
        pending = null
        _state.value = PersonProfileState.Closed
    }

    /**
     * Re-runs the request that failed, if there is one.
     *
     * The id has to survive the failure for this to work, so it is carried on
     * [PersonProfileState.Failed] rather than read back out of the message. Without it
     * a retry button has nothing to retry and the sheet's own affordance is a no-op.
     */
    fun retry() {
        val failed = _state.value as? PersonProfileState.Failed ?: return
        open(failed.kind, failed.id, failed.name)
    }

    /** Cancels anything in flight. Called when the composition that owns this goes. */
    fun dispose() {
        close()
        scope.cancel()
    }

    /**
     * One reader for the two payloads, so the sheet does not branch on which one
     * arrived. `CharacterProfile` and `StaffProfile` both define `toLoadedState()`.
     */
    private fun Any.toLoadedState(): PersonProfileState.Loaded = when (this) {
        is CharacterProfile -> toLoadedState()
        is StaffProfile -> toLoadedState()
        else -> error("PersonProfileStore got ${this::class.simpleName}, which is neither payload")
    }

    private fun PersonKind.label(): String =
        if (this == PersonKind.CHARACTER) "character" else "person"
}

/**
 * Remembers a [PersonProfileStore] for the life of this composition.
 *
 * Cancelled on dispose, so a sheet that goes away mid-fetch stops caring about the
 * answer - which is also the answer to the guard above, because the response has
 * nowhere to publish to by then.
 */
@Composable
fun rememberPersonProfileStore(
    repository: AniListRepository,
    isDemo: Boolean = false
): PersonProfileStore {
    val store = remember(repository, isDemo) { PersonProfileStore(repository, isDemo) }
    DisposableEffect(store) {
        onDispose { store.dispose() }
    }
    return store
}

/**
 * The store's state, as Compose reads it.
 *
 * A wrapper so the screens that own a sheet do not each repeat the same
 * `collectAsState` line and its import.
 */
@Composable
fun PersonProfileStore.currentState(): PersonProfileState {
    val value by this.state.collectAsState()
    return value
}

/**
 * A [ProfileAppearance] as the media node the title detail sheet opens on.
 *
 * The profile row already carries a summary - a title, a format, a status, a score and
 * a cover - so the tap routes to the sheet's own detail load instead of fetching a
 * title the reader is already looking at. `type` travels with every appearance, because
 * an id alone resolves against whichever namespace AniList prefers and a character can
 * appear in a manga; see `MediaCoverKey` for that collision.
 */
fun ProfileAppearance.toMediaNode(): MediaNode = MediaNode(
    id = id,
    type = type,
    title = title,
    format = format,
    status = status,
    averageScore = averageScore,
    siteUrl = siteUrl,
    coverImage = coverImage?.let { MediaCoverImage(extraLarge = it.large, large = it.large) }
)