package com.anisequel.ui.components

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import com.anisequel.data.model.PersonKind
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Where the reader last was on the pages that can be opened more than once.
 *
 * ## Why this is not `rememberSaveable`
 *
 * `rememberSaveable` belongs to the composition that saved it. A studio page is a
 * navigation destination: popping it drops the destination's saved state, so the
 * next studio - or the same one twenty minutes later - opened at the top of its
 * filmography. A character sheet is worse: it is drawn by whichever screen owns it,
 * and the body is rebuilt under a key when the person changes, so the scroll was
 * recreated with the new person as well.
 *
 * The reader's mental model is not "a new page" - it is "the same page I was on".
 * A studio they went back to is the one they were reading, and a voice actor they
 * tapped away from and returned to is still open at their filmography. This object
 * is the small piece of process memory that makes that true without turning either
 * page into a preference or a database.
 *
 * ## Bounded, and deliberately not persistent
 *
 * This is a session convenience, not saved work: a cold start opening at the top of
 * a studio is not a lost bookmark, it is the start of a visit. The maps are bounded
 * because a cast walk can open dozens of people, and a wholesale clear past the cap
 * is cheaper and more honest than reasoning about which entries are stale.
 */
internal object ReadingPositions {

    /** A studio's remembered place: where it was scrolled, and which layout it was in. */
    data class StudioPosition(
        val firstVisibleItemIndex: Int,
        val firstVisibleItemScrollOffset: Int
    )

    private const val MAX_STUDIOS = 24
    private const val MAX_PEOPLE = 48

    private val studioPositions = mutableMapOf<Int, StudioPosition>()
    private val studioLayouts = mutableMapOf<Int, Boolean>()
    private val personOffsets = mutableMapOf<Pair<PersonKind, Int>, Int>()

    fun studioPosition(studioId: Int): StudioPosition? = studioPositions[studioId]

    fun saveStudioPosition(
        studioId: Int,
        firstVisibleItemIndex: Int,
        firstVisibleItemScrollOffset: Int
    ) {
        if (studioPositions.size >= MAX_STUDIOS && studioId !in studioPositions) {
            studioPositions.clear()
        }
        studioPositions[studioId] = StudioPosition(
            firstVisibleItemIndex = firstVisibleItemIndex,
            firstVisibleItemScrollOffset = firstVisibleItemScrollOffset
        )
    }

    /** Whether this studio was last read as the packed wall; shelves are the default. */
    fun studioWallLayout(studioId: Int): Boolean = studioLayouts[studioId] ?: false

    fun saveStudioWallLayout(studioId: Int, wall: Boolean) {
        if (studioLayouts.size >= MAX_STUDIOS && studioId !in studioLayouts) {
            studioLayouts.clear()
        }
        studioLayouts[studioId] = wall
    }

    fun personOffset(kind: PersonKind, id: Int): Int = personOffsets[kind to id] ?: 0

    fun savePersonOffset(kind: PersonKind, id: Int, offset: Int) {
        val key = kind to id
        if (personOffsets.size >= MAX_PEOPLE && key !in personOffsets) {
            personOffsets.clear()
        }
        personOffsets[key] = offset.coerceAtLeast(0)
    }
}

/**
 * The studio page's list state, resumed where this studio was left.
 *
 * The snapshot flow is the save path rather than a `DisposableEffect`: a pop can
 * happen while a fling is still settling, and reading the position on dispose can
 * catch a frame before the last settled offset. `distinctUntilChanged` keeps the
 * writes to one per changed pixel pair rather than one per frame of a fling.
 */
@Composable
internal fun rememberStudioReadingState(
    studioId: Int,
    /**
     * Whether the filmography has arrived.
     *
     * The page's first composition is a loading row, not the list the remembered
     * offset belongs to. A `LazyListState` whose index is past a two-item list is
     * measured down to the last row and *keeps* that clamped index, so collecting
     * from the first frame wrote the placeholder's position over the reader's real
     * one before the films had a chance to be there. Saving starts with the data,
     * and the screen restores from a value it captured before any save could run.
     */
    enabled: Boolean = true
): LazyListState {
    val remembered = remember(studioId) { ReadingPositions.studioPosition(studioId) }
    val state = remember(studioId) {
        LazyListState(
            firstVisibleItemIndex = remembered?.firstVisibleItemIndex ?: 0,
            firstVisibleItemScrollOffset = remembered?.firstVisibleItemScrollOffset ?: 0
        )
    }
    LaunchedEffect(state, studioId, enabled) {
        if (!enabled) return@LaunchedEffect
        snapshotFlow {
            state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset
        }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                ReadingPositions.saveStudioPosition(studioId, index, offset)
            }
    }
    return state
}

/**
 * A person sheet's body scroll, resumed per character or staff member.
 *
 * Keyed on both halves of the identity for the same reason the sheet's own body is:
 * `Character(1)` and `Staff(1)` are different people sharing a number, and a shared
 * offset would drop one of them halfway down the other's biography.
 */
@Composable
internal fun rememberPersonReadingScroll(kind: PersonKind, id: Int): ScrollState {
    val state = remember(kind, id) {
        ScrollState(ReadingPositions.personOffset(kind, id))
    }
    LaunchedEffect(state, kind, id) {
        snapshotFlow { state.value }
            .distinctUntilChanged()
            .collect { offset -> ReadingPositions.savePersonOffset(kind, id, offset) }
    }
    return state
}
