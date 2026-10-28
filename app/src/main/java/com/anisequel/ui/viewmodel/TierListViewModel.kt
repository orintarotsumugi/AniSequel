package com.anisequel.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.StoredTierLibrary
import com.anisequel.data.repository.TierListPreferences
import com.anisequel.domain.tier.TIER_LIST_NAME_MAX_LENGTH
import com.anisequel.domain.tier.TierAssignment
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierColorPalette
import com.anisequel.domain.tier.TierDefinition
import com.anisequel.domain.tier.TierDisplay
import com.anisequel.domain.tier.TierFilter
import com.anisequel.domain.tier.TierListDocument
import com.anisequel.domain.tier.TierMediaType
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

    /**
     * The stack of edits that have been undone, so they can be put back.
     *
     * Separate from the undo stack and cleared by every *new* edit: once the person
     * changes something else, the branch they undid away from is no longer the
     * future of this list. Keeping it would offer a redo that silently drops the
     * work done since the undo.
     */
    private val redoStack = TierUndoStack()

    private val _redoLabel = MutableStateFlow<String?>(null)

    /** What "Redo" would put back, or null when nothing has been undone. */
    val redoLabel: StateFlow<String?> = _redoLabel.asStateFlow()

    /**
     * What the board and the tray are currently showing.
     *
     * Session state, like [_openedId]: it describes this visit, not the list. It is
     * *not* saved per list because a filter is a question you ask for a moment
     * ("what am I still watching?") rather than a property of the ranking. The
     * default hides planning, which is the one piece of that question that is
     * always true for a fresh visit.
     */
    private val _filter = MutableStateFlow(TierFilter.Default)
    val filter: StateFlow<TierFilter> = _filter.asStateFlow()

    /**
     * The two pools of rankable titles, and the flags describing their arrival.
     *
     * A separate type because the ViewModel's own job is the saved lists, not where
     * their titles come from; see [TierLibraryPools].
     */
    private val pools = TierLibraryPools(aniListRepository, viewModelScope, userId, isDemo)

    val animeCandidates: StateFlow<List<TierCandidate>?> = pools.anime

    val mangaCandidates: StateFlow<List<TierCandidate>?> = pools.manga

    /**
     * The pool for whichever list is open, or the anime pool while the library is
     * showing.
     *
     * The two pools are never concatenated. AniList numbers anime and manga
     * independently, so mixing them would key anime 1 and manga 1 under one media
     * id and one of the two titles would inherit the other's placement. This is
     * the one place the screen's pool is chosen, and `TierFilter` refuses the
     * other type as a second line of defence.
     */
    val candidates: StateFlow<List<TierCandidate>> =
        combine(pools.anime, pools.manga, opened) { anime, manga, document ->
            when (document?.mediaType ?: TierMediaType.ANIME) {
                TierMediaType.ANIME -> anime.orEmpty()
                TierMediaType.MANGA -> manga.orEmpty()
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private val _isSeeding = MutableStateFlow(false)
    val isSeeding: StateFlow<Boolean> = _isSeeding.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    /** Whether this account has a manga list to rank at all. See [TierLibraryPools]. */
    val canLoadManga: Boolean get() = pools.canLoadManga

    val isLoadingManga: StateFlow<Boolean> = pools.isLoadingManga

    val mangaLoadFailed: StateFlow<Boolean> = pools.mangaLoadFailed

    /**
     * Migrates the pre-library list, then reads the viewer's anime list.
     *
     * The migration runs first so the screen never shows an empty library over the
     * top of somebody's ranking. The read itself is a **cache hit** for anyone who
     * has already opened their profile this session, and manga is deliberately not
     * fetched here - see [TierLibraryPools.loadManga].
     */
    fun loadLibrary() {
        viewModelScope.launch { preferences.migrateLegacyList() }
        pools.loadAnime()
    }

    /** Reads the viewer's manga list once, the first time its shelf is opened. */
    fun loadMangaLibrary() = pools.loadManga()

    // -----------------------------------------------------------------------
    // The library
    // -----------------------------------------------------------------------

    /** Opens [listId] on the board. Does nothing when there is no such list. */
    fun openList(listId: String) {
        val document = library.value.document(listId) ?: return
        _openedId.value = listId
        // The filter asks about a pool, and the pool changed. Only the media type
        // moves: the statuses, genres, search and the studio selection are the
        // reader's question and stay where they were, so opening a manga list does
        // not silently clear the anime studio filter they set a minute ago.
        _filter.value = _filter.value.forMediaType(document.mediaType)
        // The stack describes edits to the list that *was* open. Carrying it across
        // a switch would offer to undo somebody else's list under this one's name.
        undoStack.clear()
        _undoLabel.value = null
        redoStack.clear()
        _redoLabel.value = null
        viewModelScope.launch { preferences.setLastOpened(listId) }
    }

    /** Leaves the board for the library. Nothing is written; edits are already saved. */
    fun closeList() {
        _openedId.value = null
        undoStack.clear()
        _undoLabel.value = null
        redoStack.clear()
        _redoLabel.value = null
    }

    /**
     * Makes a new empty list of [mediaType] and opens it.
     *
     * Named from the lists that already exist, so the third list is "Tier list 3"
     * rather than three lists all called "Tier list" - the name is the only thing
     * distinguishing them in the library, and a screen of identical cards is a
     * screen you cannot navigate.
     *
     * The type is the section the person pressed "New tier list" in. A list cannot
     * change its type afterwards: the pool it ranks is what the ranking is of, and
     * an anime board relabelled as manga would keep placements for ids that mean
     * different titles in the other namespace.
     */
    fun createList(mediaType: TierMediaType = TierMediaType.ANIME) {
        viewModelScope.launch {
            val name = defaultTierListName(library.value.documents.map { it.name })
            val id = preferences.createEmpty(name, mediaType = mediaType)
            _openedId.value = id
            _filter.value = _filter.value.forMediaType(mediaType)
            undoStack.clear()
            _undoLabel.value = null
            redoStack.clear()
            _redoLabel.value = null
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
                redoStack.clear()
                _redoLabel.value = null
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
            // A new edit is a new branch: whatever was undone is no longer the
            // future of this list, and redoing it would silently discard this.
            redoStack.clear()
            _redoLabel.value = null
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
        val current = library.value.document(listId) ?: return
        // Seed what the board is showing, not everything the viewer has ever
        // listed. With planning hidden by default this is the difference between
        // ranking the titles they have an opinion about and sweeping in a hundred
        // unwatched ones. The pool is narrowed to the list's own media type first:
        // an anime score must never be filed into a manga ranking, and the two
        // namespaces share ids.
        val pool = pools.poolFor(current.mediaType)
            .filter(_filter.value.forMediaType(current.mediaType)::allows)
        if (pool.isEmpty()) return
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
                    redoStack.clear()
                    _redoLabel.value = null
                }

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

    /**
     * Adds a tier below the last one, with an id derived from its position.
     *
     * The colour walks [TierColorPalette] by position rather than being the same
     * grey every time: a list with four custom tiers used to be four identical
     * grey rows, which is the one thing a colour-coded board must not be. It is
     * still just a starting point - the manage sheet recolours it in one tap.
     */
    fun addTier() {
        edit("Added a tier") { document ->
            val current = document.tiers
            val id = "custom_${current.size}_" +
                current.joinToString("") { it.id }.hashCode().toUInt().toString(16)
            val color = TierColorPalette[current.size % TierColorPalette.size]
            document.copy(tiers = current + TierDefinition(id, "New tier", color))
        }
    }

    /** Recolours one tier. The id is untouched, so nothing filed under it moves. */
    fun setTierColor(tierId: String, colorArgb: Long) {
        edit("Changed a tier colour") { document ->
            document.copy(
                tiers = document.tiers.map {
                    if (it.id == tierId) it.copy(colorArgb = colorArgb) else it
                }
            )
        }
    }

    /**
     * Sets the score band a tier accepts, or clears it with null.
     *
     * Clamped to AniList's 0-100 range here rather than in the sheet, so a value
     * from any future caller cannot produce a band that no title can clear. A
     * cleared band is not the same as a band of 0: 0 accepts every scored title,
     * null accepts none, and the manage sheet offers both.
     */
    fun setTierScoreFloor(tierId: String, floor: Int?) {
        val clamped = floor?.coerceIn(0, 100)
        edit(if (clamped == null) "Removed a rating band" else "Changed a rating band") { document ->
            document.copy(
                tiers = document.tiers.map {
                    if (it.id == tierId) it.copy(scoreFloor = clamped) else it
                }
            )
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
        val current = opened.value
        _undoLabel.value = undoStack.label
        // The *current* document becomes the redo snapshot, and the edit's own
        // label describes it: "Redo: Filed under S" is the edit being put back,
        // which is the same label undo showed a moment ago.
        if (current != null && current.id == entry.document.id) {
            redoStack.push(TierUndoEntry(current, entry.label))
        }
        _redoLabel.value = redoStack.label
        // A snapshot carries the display options that were current when the edit
        // was made. Undo is about the ranking, not the view: restoring the stored
        // zoom would snap the board back to whatever it was at the time of the
        // drag, even though a display change is deliberately not an undo entry.
        val restored = if (current != null && current.id == entry.document.id) {
            entry.document.copy(display = current.display)
        } else {
            entry.document
        }
        viewModelScope.launch { preferences.restore(restored) }
    }

    /**
     * Puts back the last edit [undo] took away.
     *
     * The mirror of undo, and it has to be, or the two stacks drift: the state
     * being replaced is pushed onto undo, so undoing a redo returns to exactly the
     * state the redo started from. Both stacks are cleared together whenever the
     * board changes lists.
     */
    fun redo() {
        val entry = redoStack.pop() ?: return
        val current = opened.value
        _redoLabel.value = redoStack.label
        if (current != null && current.id == entry.document.id) {
            undoStack.push(TierUndoEntry(current, entry.label))
        }
        _undoLabel.value = undoStack.label
        val restored = if (current != null && current.id == entry.document.id) {
            entry.document.copy(display = current.display)
        } else {
            entry.document
        }
        viewModelScope.launch { preferences.restore(restored) }
    }

    /** Replaces the session filter; the board and tray both read it. */
    fun setFilter(filter: TierFilter) {
        _filter.value = filter
    }

    /**
     * Changes how the open list is drawn.
     *
     * Deliberately outside [edit]: display options are not work to undo, and a
     * zoom step pushed onto the undo stack would mean the next "Undo" after
     * zooming in undid the zoom instead of the drag the person actually regrets.
     * [TierListPreferences.mutate] still skips the write when nothing changed, so
     * a debounced zoom that settles on the value already stored costs no write.
     */
    fun setDisplay(display: TierDisplay) {
        writeDisplay { display }
    }

    /**
     * Stores the board's zoom after a pinch or a button press.
     *
     * A separate entry point rather than a whole [TierDisplay] from the caller:
     * the zoom settles on a debounce while the display sheet may have changed the
     * title options in between, and writing the board's stale copy of the other
     * fields would undo that.
     */
    fun setZoom(zoom: Float) {
        writeDisplay { it.copy(zoom = zoom) }
    }

    private fun writeDisplay(change: (TierDisplay) -> TierDisplay) {
        val listId = _openedId.value ?: return
        viewModelScope.launch {
            preferences.mutate(listId) { document ->
                document.copy(display = change(document.display))
            }
        }
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
    }
}
