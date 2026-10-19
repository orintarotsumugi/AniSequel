package com.anisequel.ui.viewmodel

import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.PersonProfileState
import com.anisequel.data.model.ProfileAppearance
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
 *
 * ## A person is fetched once
 *
 * What the store has already loaded is remembered (see [remembered]), so tapping back
 * to a face the reader has just read draws it in the same frame and sends nothing.
 * AniList's own cache would make the second request cheap, but a *request* is not
 * what the reader sees: without this the sheet re-entered Loading, and the page they
 * were looking at was replaced by a spinner and then by the same page again.
 */
class PersonProfileStore(
    /**
     * Nullable because not every host has one.
     *
     * The demo dashboard runs on a fake repository with no characters in it, and a
     * host with no repository at all should draw captions rather than buttons that
     * open a sheet which can only say "failed". Screens gate their *callbacks* on
     * this being non-null, so a null here is unreachable in practice - but the store
     * still answers rather than throwing, because the alternative is a crash on a
     * path a reader can reach.
     */
    private val repository: AniListRepository?,
    private val isDemo: Boolean = false,
    /**
     * Where the fetches run.
     *
     * Injectable so a test can drive them with a `TestScope` and step the scheduler
     * by hand. Without it the store owns a `Dispatchers.Default` scope that no test
     * can advance, and the two behaviours that matter here - discarding a late
     * response, and re-running a failed one - are both timing, so they would be
     * untestable rather than merely awkward.
     */
    scope: CoroutineScope? = null
) {
    /** Whether [dispose] may cancel [fetchScope] - i.e. whether this store made it. */
    private val ownsScope = scope == null

    private val fetchScope: CoroutineScope = scope ?: CoroutineScope(SupervisorJob())

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

    /**
     * Profiles already fetched, so looking at the same person twice costs one request.
     *
     * A cast row is twenty-five faces and a reader taps through several of them,
     * often going back to one they have already read. Without this, every one of
     * those re-opens re-entered [PersonProfileState.Loading] and asked again - the
     * face the reader is looking at was replaced by a spinner, then by the same face,
     * and the sheet spent a request on a question AniList had already answered. A
     * character's data changes when AniList edits a wiki entry, which is not a thing
     * that happens while someone is reading a cast row.
     *
     * Keyed exactly like [pending], and for the same reason: `Character(1)` and
     * `Staff(1)` are different people.
     *
     * Bounded, and cleared wholesale past the cap: this holds a description and up to
     * twelve covers per person, and past a couple of cast walks the cheapest correct
     * thing is to forget rather than to keep reasoning about what is stale - the same
     * call the repository's own profile cache makes.
     */
    private val remembered = mutableMapOf<Pair<PersonKind, Int>, PersonProfileState.Loaded>()

    /** Opens a character's profile. A no-op while that same character is loading. */
    fun openCharacter(id: Int, name: String? = null) = open(PersonKind.CHARACTER, id, name)

    /** Opens a staff member's profile, on the same terms as [openCharacter]. */
    fun openStaff(id: Int, name: String? = null) = open(PersonKind.STAFF, id, name)

    private fun open(kind: PersonKind, id: Int, name: String?) {
        val key = kind to id
        if (pending == key) return
        pending = key

        // Answered synchronously, before the sheet can draw a spinner: the whole
        // point of remembering is that the reader never sees the page reload.
        remembered[key]?.let { profile ->
            pending = null
            _state.value = profile
            return
        }

        _state.value = PersonProfileState.Loading(kind, name)

        fetchScope.launch {
            /**
             * Mapped to the sheet's state at the call site, while the payload type is
             * still statically known.
             *
             * This used to go through one `Any.toLoadedState()` that `when`-cast and
             * called the matching conversion. It recursed to a stack overflow: with an
             * `Any` receiver in scope and two imported conversions of the same name,
             * `toLoadedState()` inside the branch resolved to the `Any` one - the call
             * it was meant to be dispatching away from. Branches that map on a value of
             * known type cannot hit that, so there is no dispatcher here to get wrong.
             */
            val result: Result<PersonProfileState.Loaded> = when {
                isDemo -> Result.failure(
                    IllegalStateException("There is no ${kind.label()} to look up in the demo.")
                )
                repository == null -> Result.failure(
                    IllegalStateException("This screen has no AniList connection to look one up with.")
                )
                kind == PersonKind.CHARACTER ->
                    repository.getCharacterProfile(id).map { it.toLoadedState() }
                else -> repository.getStaffProfile(id).map { it.toLoadedState() }
            }

            // The guard. A response for a sheet the reader has already replaced is
            // dropped rather than painted over whatever they are looking at now.
            if (pending != key) return@launch
            pending = null

            _state.value = result.fold(
                onSuccess = { profile ->
                    if (remembered.size >= REMEMBERED_PROFILE_LIMIT) remembered.clear()
                    remembered[key] = profile
                    profile
                },
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
        // Only cancels a scope this store created. One handed in belongs to whoever
        // made it - a `TestScope`, or a screen that outlives this store - and
        // cancelling it here would take down work that is not this store's.
        if (ownsScope) fetchScope.cancel()
    }

    private fun PersonKind.label(): String =
        if (this == PersonKind.CHARACTER) "character" else "person"
}

/**
 * How many profiles the store remembers before it forgets all of them.
 *
 * Smaller than the repository's own ceiling on purpose: an entry here is the *sheet's*
 * shape of a person - formatted facts, a parsed biography and the twelve covers the row
 * draws - and the store only lives as long as the screen that owns it. Two cast walks
 * is more than a reader revisits in one sitting; past that, refetching is cheaper than
 * holding a map nobody is reading.
 */
private const val REMEMBERED_PROFILE_LIMIT = 32

// `rememberPersonProfileStore` and a `currentState()` composable wrapper used to
// live here, one caller each. `PersonProfileHost` needs both and three screens do
// not, so they moved there rather than staying as a second way in.

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
    coverImage = coverImage?.let { MediaCoverImage(extraLarge = it.extraLarge, large = it.large) }
)