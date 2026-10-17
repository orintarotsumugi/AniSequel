package com.anisequel

import com.anisequel.data.model.MediaEntryEdit
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.ui.components.detail.EntryEditState
import com.anisequel.ui.components.detail.EntryEditor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The entry editor's state holder, and the request it is allowed to make.
 *
 * ## The failure this exists for
 *
 * The draft used to be `remember`ed *inside* the edit tab. The tabs live in an
 * `AnimatedContent`, which disposes the tab it animates away from, so every
 * switch to another tab and back tore the draft down and rebuilt it: the spinner
 * came back, the fields went blank, and `editor.load()` fired a second request
 * for an entry AniList had already answered.
 *
 * That cost a visible flash on every tab switch and a wasted request each time,
 * against an API this app is otherwise careful about - it coalesces duplicate
 * queries and rate-limits on purpose. So the holder now refuses to fetch an
 * entry it already holds, and that refusal is the thing pinned here: it is the
 * only thing standing between a re-composition and a second round trip, and it
 * cannot be seen from the UI.
 */
class EntryEditStateTest {

    /** Counts loads, so a second fetch is a failure rather than an invisible cost. */
    private class FakeEditor(
        private val result: Result<SimpleMediaListEntry>
    ) : EntryEditor {
        var loadCalls = 0
            private set

        override suspend fun load(mediaId: Int): Result<SimpleMediaListEntry> {
            loadCalls++
            return result
        }

        override suspend fun save(edit: MediaEntryEdit): Result<SimpleMediaListEntry> =
            result

        override suspend fun delete(entryId: Int): Result<Unit> = Result.success(Unit)
    }

    private val entry = SimpleMediaListEntry(
        id = 9001,
        status = "CURRENT",
        mediaId = 42,
        progress = 7,
        progressVolumes = 3,
        score = 8.5,
        isPrivate = false
    )

    @Test
    fun `a successful load fills the draft`() = runTest {
        val state = EntryEditState()
        val editor = FakeEditor(Result.success(entry))

        state.load(editor, mediaId = 42)

        assertEquals(1, editor.loadCalls)
        assertNull(state.loadError)
        val draft = state.draft
        assertNotNull("the draft must be populated after a successful load", draft)
        assertEquals(9001, draft!!.entryId)
        assertEquals("CURRENT", draft.status)
        assertEquals(7, draft.progress)
        assertEquals(8.5, draft.score, 0.0001)
    }

    @Test
    fun `an entry already held is never fetched again`() = runTest {
        // The regression. Rebuilding this tab used to mean a second `load` for a
        // list AniList had already sent, so the guard is the fix.
        val state = EntryEditState()
        val editor = FakeEditor(Result.success(entry))

        state.load(editor, mediaId = 42)
        state.load(editor, mediaId = 42)
        state.load(editor, mediaId = 42)

        assertEquals(
            "three visits to the tab must still cost exactly one request",
            1,
            editor.loadCalls
        )
    }

    @Test
    fun `a failed load reports the error and holds no draft`() = runTest {
        val state = EntryEditState()
        val editor = FakeEditor(Result.failure(IllegalStateException("AniList said no")))

        state.load(editor, mediaId = 42)

        assertNotNull("a failure must surface rather than render an empty form", state.loadError)
        assertTrue(state.loadError!!.contains("AniList said no"))
        assertNull(state.draft)
    }

    @Test
    fun `a failed load can be retried`() = runTest {
        // The guard keys off a held draft, not off "has been asked". A failure
        // holds nothing, so coming back to the tab is allowed to try again -
        // otherwise a single dropped request would need a full app restart.
        //
        // Asserted as `1`, not `2`: the retry is a *second editor*, so counting
        // its calls proves the retry was not blocked. An earlier version of this
        // test asserted 2 and failed, because it was counting calls on a fresh
        // object as though it were the same one that had already been called.
        val state = EntryEditState()
        val failing = FakeEditor(Result.failure(IllegalStateException("offline")))
        state.load(failing, mediaId = 42)
        assertEquals(1, failing.loadCalls)

        val recovered = FakeEditor(Result.success(entry))
        state.load(recovered, mediaId = 42)

        assertEquals(
            "the retry must actually reach the editor, not be blocked by the guard",
            1,
            recovered.loadCalls
        )
        assertNull("a successful retry clears the earlier failure", state.loadError)
        assertNotNull(state.draft)
    }

    @Test
    fun `clearing an entry resets the form to something that can be created`() = runTest {
        // What "Delete" leaves behind. It has to be a real blank form rather than
        // null, or the tab would fall through to the loading state and look like
        // it was fetching an entry the viewer had just deleted.
        val state = EntryEditState()
        state.load(FakeEditor(Result.success(entry)), mediaId = 42)

        state.cleared()

        val draft = state.draft
        assertNotNull(draft)
        assertNull("a cleared entry has no id to delete or update", draft!!.entryId)
        assertNull(draft.status)
        assertEquals(0, draft.progress)
        assertNull(draft.progressVolumes)
        assertEquals(0.0, draft.score, 0.0001)
        assertEquals(10, draft.scoreMax)
        assertNull(draft.startedAt)
        assertNull(draft.completedAt)
        assertFalse(draft.isPrivate)
    }

    @Test
    fun `save is only offered while the draft differs from what AniList confirmed`() = runTest {
        // The pinned action row's whole reason for the `confirmed` baseline: a
        // freshly loaded form must not offer to write a snapshot that changes
        // nothing, and a save must leave nothing behind for the next one.
        val state = EntryEditState()
        state.load(FakeEditor(Result.success(entry)), mediaId = 42)

        assertFalse("a freshly loaded entry has nothing to send", state.hasUnsavedChanges)

        state.draft = state.draft!!.copy(score = 9.0)
        assertTrue("a changed field is something to send", state.hasUnsavedChanges)

        state.markSaved(state.draft!!)
        assertFalse("the saved draft becomes the new baseline", state.hasUnsavedChanges)
    }

    @Test
    fun `the next edit clears the message the last save left`() = runTest {
        // "Saved to your AniList list" used to survive the next edit, so the
        // form's one line of feedback described an entry that was no longer on
        // screen. `markSaved` must not clear it - the save path sets it after -
        // but the edit that follows must.
        val state = EntryEditState()
        state.load(FakeEditor(Result.success(entry)), mediaId = 42)

        val saved = state.draft!!.copy(score = 9.0)
        state.markSaved(saved)
        state.statusMessage = "Saved to your AniList list"

        state.draft = saved.copy(score = 6.0)

        assertNull("the message described the entry as it was", state.statusMessage)
    }

    @Test
    fun `a hundred-point account keeps its scale and its volume count`() = runTest {
        // Not about the holder, but this is the state it now carries, and the two
        // fields are the ones a refactor of this shape quietly drops: a manga's
        // volumes, and a score that is not out of ten. Both are part of a
        // whole-entry snapshot, so losing either clears it on the server.
        val state = EntryEditState()
        val hundred = entry.copy(score = 85.0, progressVolumes = 12)

        state.load(FakeEditor(Result.success(hundred)), mediaId = 42)

        val draft = state.draft!!
        assertEquals(100, draft.scoreMax)
        assertEquals(85.0, draft.score, 0.0001)
        assertEquals(12, draft.progressVolumes)
    }
}
