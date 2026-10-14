package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.StoredTierLibrary
import com.anisequel.data.repository.TierListPreferences
import com.anisequel.domain.tier.TIER_LIST_NAME_MAX_LENGTH
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierUndoEntry
import com.anisequel.domain.tier.TierUndoStack
import com.anisequel.domain.tier.defaultTierListName
import com.anisequel.domain.tier.orphanedCount
import com.anisequel.domain.tier.seedFromScores
import com.anisequel.domain.tier.unseedableCount
import com.anisequel.domain.tier.withPlacement
import com.anisequel.domain.tier.withoutOrphans
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * The tier lists: which ones exist, which one is open, and what is in it.
 *
 * ## The library is the entry point now
 *
 * Opening the screen used to open the board. There was one list, so there was
 * nothing to choose between, and the two ways into the feature - rank everything
 * from AniList scores, or start empty - were buttons on an empty board. A list is a
 * document with a name and an id, so the screen opens on the lists themselves and
 * the board is a destination you pick: carry on with the last one, open any of
 * them, or make a new empty one. That is the difference between a screen and a
 * thing you keep.
 *
 * ## Every edit is one write, and undo is the previous document
 *
 * Placements, tiers and removals all go through [edit], which hands the change to
 * the store, gets back the document as it was, and pushes that for undo. The
 * snapshot is read inside the store's transaction, so "undo" restores exactly the
 * state the last edit replaced - including the entries a removed tier took with it,
 * which a delta would have to invert correctly to get right and would silently get
 * wrong if it did not.
 *
 * ## Unrated titles are not seeded
 *
 * AniList answers 0 or null for a title nobody has scored, and filing those into
 * the bottom tier asserts something nobody decided - in the same typeface as the
 * judgements the person did make. They are counted and reported instead, so the UI
 * can say "12 titles have no score yet" rather than silently ranking them.
 */
class TierListViewModel(
    private val preferences: TierListPreferences,
    private val aniListRepository: AniListRepository,
    /** Whose list this is the tier list of. Null for a signed-out viewer. */
    private val userId: Int? = null,
    private val isDemo: Boolean = false
) : ViewModel() {

    /**
     * Which list the board is showing, for this visit.
     *
     * Session state rather than storage: what is *stored* is [StoredTierLibrary.lastOpenedId],
     * which decides what the library offers to carry on with. Nothing here is
     * written, so leaving the board and coming back does not have to mean anything
     * to the disk - and the library screen is simply this being null, which is why
     * there is exactly one place that knows which screen is up.
     */
    private val _openedId = MutableStateFlow<String?>(null)

    /** Every saved list, and which one the app should offer to carry on with. */
    val library: StateFlow<StoredTierLibrary> = preferences.library
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), StoredTierLibrary())

    /**
     * The open list, or null while the library screen is showing.
     *
     * Combined rather than copied into a second state holder: the board reads the
     * tiers, the placements and the name straight off this value, so there is no
     * window in which the screen has a list and the store has another one.
     */
    val opened: StateFlow<TierListDocument?> = combine(library, _openedId) { stored, id ->
        stored.document(id)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /** What the library's "carry on" card offers, or null when no list exists. */
    val recent: StateFlow<TierListDocument?> = library
        .map { it.recent }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), null)

    /**
     * The open list's placements, with entries in deleted tiers removed.
     *
     * A document can name a tier that no longer exists - one saved before a row was
     * removed, or by an older build. Dropping those ids here, in one place, keeps
     * the invariant the whole feature rests on: a title is either in a tier that
     * exists or in none.
     */
    val liveAssignment: StateFlow<TierAssignment> = opened
        .map { document ->
            document?.let { it.assignment.withoutOrphans(it.tiers) } ?: TierAssignment()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), TierAssignment())

    /** How many entries in the open list are filed under a tier that no longer exists. */
    val orphanedCount: StateFlow<Int> = opened
        .map { document -> document?.let { it.assignment.orphanedCount(it.tiers) } ?: 0 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), 0)

    private val undoStack = TierUndoStack()

    private val _undoLabel = MutableStateFlow<String?>(null)

    /**
     * What "Undo" would take back, or null when there is nothing to undo.
     *
     * A label rather than a boolean because a bare "Undo" asks the person to
     * remember what they just did. The stack is cleared whenever the board changes
     * lists, so the label always describes something that happened to *this* list.
     */
    val undoLabel: StateFlow<String?> = _undoLabel.asStateFlow()

    private val _candidates = MutableStateFlow<List<TierCandidate>>(emptyList())
    val candidates: StateFlow<List<TierCandidate>> = _candidates.asStateFlow()

    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /**
     * Reads the viewer's own list, then hands it to [setLibrary].
     *
     * The repository caches the list under `user:<id>`, so this is a **cache hit**
     * for anyone who has already opened their profile in this session and costs no
     * request at all. Fetching it again anyway would spend one of AniList's
     * 30-a-minute budget to re-read a list the app is already looking at.
     *
     * The candidates are the *library*, not a list: they are shared by every tier
     * list the person makes, so this runs once per visit rather than per list.
     */
    fun loadLibrary() {
        viewModelScope.launch {
            // Before anything reads the library: a list written by a build that
            // predates lists becomes a list here, so the screen never shows an
            // empty library over the top of somebody's ranking.
            preferences.migrateLegacyList()
        }
        val id = userId
        if (id == null && !isDemo) return
        if (_candidates.value.isNotEmpty()) return
        viewModelScope.launch {
            val result = if (isDemo) {
                Result.success(aniListRepository.getDemoAnimeList())
            } else {
                aniListRepository.getUserAnimeList(id!!, forceRefresh = false)
            }
            result.onSuccess { collection ->
                setLibrary(
                    collection.lists.orEmpty().flatMap { it.entries.orEmpty() }
                )
            }
        }
    }

    /**
     * Hands the ViewModel the person's library to rank.
     *
     * Called with the entries the repository already holds rather than reading for
     * them here: re-reading the list would spend one of AniList's 30-a-minute
     * requests to obtain data the app is already looking at.
     */
    fun setLibrary(entries: List<MediaListEntryItem>) {
        _candidates.value = entries.mapNotNull { entry ->
            val media = entry.media
            val title = media.title?.displayTitle?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            TierCandidate(
                mediaId = media.id,
                title = title,
                coverUrl = media.coverImage?.bestUrl,
                averageScore = media.averageScore
            )
        }
    }

    // -----------------------------------------------------------------------
    // The library
    // -----------------------------------------------------------------------

    /** Opens [listId] on the board. Does nothing when there is no such list. */
    fun openList(listId: String) {
        if (library.value.document(listId) == null) return
        _openedId.value = listId
        // The stack describes edits to the list that *was* open. Carrying it across
        // a switch would offer to undo somebody else's list under this one's name.
        undoStack.clear()
        _undoLabel.value = null
        viewModelScope.launch { preferences.setLastOpened(listId) }
    }

    /** Leaves the board for the library. Nothing is written; edits are already saved. */
    fun closeList() {
        _openedId.value = null
        undoStack.clear()
        _undoLabel.value = null
    }

    /**
     * Makes a new empty list and opens it.
     *
     * Named from the lists that already exist, so the third list is "Tier list 3"
     * rather than three lists all called "Tier list" - the name is the only thing
     * distinguishing them in the library, and a screen of identical cards is a
     * screen you cannot navigate.
     */
    fun createList() {
        viewModelScope.launch {
            val name = defaultTierListName(library.value.documents.map { it.name })
            val id = preferences.createEmpty(name)
            _openedId.value = id
            undoStack.clear()
            _undoLabel.value = null
        }
    }

    /**
     * Renames one list.
     *
     * Not undoable on purpose: the old name is on the card the person just typed
     * over, and a rename is one tap to repeat. The undo stack is for the edits that
     * are expensive to redo by hand - dragging thirty posters again.
     */
    fun renameList(listId: String, name: String) {
        val cleaned = name.trim().take(TIER_LIST_NAME_MAX_LENGTH)
        if (cleaned.isEmpty()) return
        viewModelScope.launch { preferences.mutate(listId) { it.copy(name = cleaned) } }
    }

    /** Deletes a list and everything in it. */
    fun deleteList(listId: String) {
        viewModelScope.launch {
            preferences.delete(listId)
            if (_openedId.value == listId) {
                _openedId.value = null
                undoStack.clear()
                _undoLabel.value = null
            }
        }
    }

    // -----------------------------------------------------------------------
    // Editing the open list
    // -----------------------------------------------------------------------

    /**
     * Applies one edit to the open list, and remembers how to take it back.
     *
     * A no-op edit - a poster dropped back where it already was, a removal the
     * rules refused - is not pushed: an "Undo" that restores exactly the state you
     * are already in is worse than no undo, because the next press then undoes the
     * edit before it.
     */
    private fun edit(label: String, change: (TierListDocument) -> TierListDocument) {
        val listId = _openedId.value ?: return
        viewModelScope.launch {
            val mutation = preferences.mutate(listId, change) ?: return@launch
            if (mutation.after == mutation.before) return@launch
            undoStack.push(TierUndoEntry(mutation.before, label))
            _undoLabel.value = label
        }
    }

    /**
     * Files one title into a tier at a position, or removes it when [tierId] is null.
     *
     * [beforeMediaId] is the tile the drop landed on top of, and the entry goes
     * immediately above it - which is what makes dropping a poster between two
     * others mean what it looks like it means. Null means the end of that tier.
     */
    fun moveTo(mediaId: Int, tierId: String?, beforeMediaId: Int? = null) {
        val label = when (tierId) {
            null -> "Taken out of the list"
            else -> {
                val name = opened.value?.tiers?.firstOrNull { it.id == tierId }?.label ?: tierId
                "Filed under $name"
            }
        }
        edit(label) { document ->
            // Refuse a tier that is not in this document.
            //
            // The invariant "a title is either in a tier that exists or in none" is
            // enforced on the way *out* - `withoutOrphans` and `liveAssignment` both
            // exist for it - and was never enforced on the way in, because
            // `withPlacement` writes whatever id it is handed. A tier removed while a
            // board was on screen could still be named by a drop resolving against a
            // stale row band, and the result was the worst state this feature has:
            // the title vanished from the board, the counter dropped, and the notice
            // offered no way out, because the row it had been filed under no longer
            // existed to place it into again.
            if (tierId != null && document.tiers.none { it.id == tierId }) {
                document
            } else {
                document.copy(
                    assignment = document.assignment.withPlacement(mediaId, tierId, beforeMediaId)
                )
            }
        }
    }

    /**
     * Files every scored title into a tier.
     *
     * Refuses when a placement already exists, so this cannot be the thing that
     * overwrites work. The refusal is per *list*, which is the whole point of having
     * more than one: a second list can be seeded without touching the first.
     */
    fun seedFromScores() {
        if (_isSeeding.value) return
        val listId = _openedId.value ?: return
        val pool = _candidates.value
        if (pool.isEmpty()) return
        val current = library.value.document(listId) ?: return
        if (!current.isEmpty) return

        _isSeeding.value = true
        viewModelScope.launch {
            // `try`/`finally` rather than clearing the flag on the happy path.
            //
            // Anything thrown between here and the end - a DataStore corruption on
            // the index, a failure inside `mutate` - used to skip the reset and
            // leave "Rank from AniList scores" spinning forever. Re-tapping it was a
            // no-op, because the guard above is `if (_isSeeding.value) return`, so
            // an empty-state board was stuck until the screen was left and
            // re-entered. The failure is also surfaced, because a seed that did
            // nothing must not look like one that is still working.
            try {
                val assignment = seedFromScores(pool, current.tiers)
                val mutation = preferences.mutate(listId) { it.copy(assignment = assignment) }
                if (mutation != null && mutation.after != mutation.before) {
                    undoStack.push(TierUndoEntry(mutation.before, "Ranked from AniList scores"))
                    _undoLabel.value = "Ranked from AniList scores"
                }
                if (userId != null) preferences.markSeededFor(userId)

                val skipped = unseedableCount(pool)
                val placed = assignment.tierIdByMediaId.size
                _notice.value = when {
                    placed == 0 -> "Nothing to rank yet - none of your titles has a score."
                    skipped > 0 -> "Ranked $placed titles. $skipped " +
                        (if (skipped == 1) "has" else "have") +
                        " no score yet and " +
                        (if (skipped == 1) "was" else "were") +
                        " left for you to place."
                    else -> "Ranked $placed titles."
                }
            } catch (error: Throwable) {
                _notice.value = "Couldn't rank from scores - ${
                    error.message ?: "something went wrong"
                }. Nothing was changed."
            } finally {
                _isSeeding.value = false
            }
        }
    }

    /** Empties every tier, keeping the reader's own tiers and colours. */
    fun clearPlacements() {
        edit("Cleared every tier") { it.copy(assignment = TierAssignment()) }
    }

    /** Renames a tier, keeping its id so nothing filed under it is orphaned. */
    fun renameTier(tierId: String, label: String) {
        val trimmed = label.trim()
        if (trimmed.isEmpty()) return
        edit("Renamed a tier") { document ->
            document.copy(
                tiers = document.tiers.map {
                    if (it.id == tierId) it.copy(label = trimmed) else it
                }
            )
        }
    }

    /** Adds a tier below the last one, with an id derived from its position. */
    fun addTier() {
        edit("Added a tier") { document ->
            val current = document.tiers
            val id = "custom_${current.size}_" +
                current.joinToString("") { it.id }.hashCode().toUInt().toString(16)
            document.copy(tiers = current + TierDefinition(id, "New tier", NEW_TIER_COLOR))
        }
    }

    /**
     * Removes a tier, and every entry filed under it.
     *
     * The entries are dropped rather than promoted to the tier above, and the removal
     * is *one* write rather than two. Promoting would silently claim the person had
     * ranked them there, and an entry nobody placed is a truer state than one placed
     * by a deletion. One write because the two-write version left the list naming a
     * row that no longer existed for as long as it took the second write to land.
     */
    fun removeTier(tierId: String) {
        edit("Removed a tier") { document ->
            // Never to zero: with no rows left there is nothing to drag onto.
            if (document.tiers.size <= 1) return@edit document
            document.copy(
                tiers = document.tiers.filterNot { it.id == tierId },
                assignment = TierAssignment(
                    document.assignment.tierIdByMediaId.filterValues { it != tierId }
                )
            )
        }
    }

    /**
     * Takes back the last edit to the open list.
     *
     * Restoring the snapshot restores the tiers as well as the placements, which is
     * what makes undoing a removed tier bring back the titles the removal dropped -
     * the thing a person actually wants back and the thing a placement-only undo
     * could not do.
     */
    fun undo() {
        val entry = undoStack.pop() ?: return
        _undoLabel.value = undoStack.label
        viewModelScope.launch { preferences.restore(entry.document) }
    }

    fun dismissNotice() {
        _notice.value = null
    }

    class Factory(
        private val preferences: TierListPreferences,
        private val aniListRepository: AniListRepository,
        private val userId: Int? = null,
        private val isDemo: Boolean = false
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            TierListViewModel(preferences, aniListRepository, userId, isDemo) as T
    }

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
        const val NEW_TIER_COLOR = 0xFF9E9A99
    }
}
