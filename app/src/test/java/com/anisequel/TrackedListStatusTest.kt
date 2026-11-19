package com.anisequel

import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.detailFor
import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.components.detail.DetailActionState
import com.anisequel.ui.viewmodel.QuickAddStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The Info page's action row on a title the viewer already tracks.
 *
 * ## The bug this pins
 *
 * A completed show opened from the Library - or from search, the tier board, a
 * studio page, anybody's profile - drew "Add to Planning" instead of its status.
 * The status was in the detail answer the whole time: `MEDIA_DETAIL_FIELDS` asks
 * for `mediaListEntry { status progress }`. It was [MediaNode.withDetail] that
 * dropped it. `MediaListCollection` carries the status on the *list entry* and the
 * list query does not select `mediaListEntry` on the entry's own media, so the
 * summary the tap came from had no entry, `withDetail` copied no entry either, and
 * the row saw a title "on no list".
 *
 * That is not only a wrong label. AniList's add mutation overwrites the entry, so
 * the button it offered would have moved a finished show back to Planning.
 */
class TrackedListStatusTest {

    @Test
    fun `a title opened from a library row keeps the detail's list status`() {
        // The Library's own query: the status is on the entry, not on the media.
        val summary = MediaNode(id = 1, type = "ANIME", episodes = 12)
        val detail = MediaNode(
            id = 1,
            type = "ANIME",
            episodes = 12,
            description = "A finished show.",
            mediaListEntry = SimpleMediaListEntry(
                status = ListStatuses.COMPLETED,
                progress = 12
            )
        )

        val merged = mapOf(MediaCoverKey("ANIME", 1) to detail).detailFor(summary)!!

        assertEquals(
            "the detail query's own mediaListEntry has to survive the merge, or a " +
                "completed show has no status to draw",
            ListStatuses.COMPLETED,
            merged.mediaListEntry?.status
        )
        assertEquals(12, merged.mediaListEntry?.progress)

        val state = state(listStatus = merged.mediaListEntry?.status)
        assertEquals(ListStatuses.COMPLETED, state.trackedStatus)
        assertFalse("a completed show must never offer the quick add", state.showMediaAction)
        assertTrue(state.showEntryEdit)
    }

    @Test
    fun `the summary's own status and progress win over the detail's`() {
        // An arriving row knows the status and progress from the viewer's list;
        // the detail may be a cached answer from before the last write.
        val summary = MediaNode(
            id = 2,
            type = "ANIME",
            mediaListEntry = SimpleMediaListEntry(
                status = ListStatuses.CURRENT,
                progress = 4
            )
        )
        val detail = MediaNode(
            id = 2,
            type = "ANIME",
            mediaListEntry = SimpleMediaListEntry(
                status = ListStatuses.COMPLETED,
                progress = 12
            )
        )

        val merged = mapOf(MediaCoverKey("ANIME", 2) to detail).detailFor(summary)!!

        assertEquals(ListStatuses.CURRENT, merged.mediaListEntry?.status)
        assertEquals(4, merged.mediaListEntry?.progress)
    }

    @Test
    fun `a summary that only knows progress takes the status from the detail`() {
        val summary = MediaNode(
            id = 3,
            type = "ANIME",
            mediaListEntry = SimpleMediaListEntry(progress = 7)
        )
        val detail = MediaNode(
            id = 3,
            type = "ANIME",
            mediaListEntry = SimpleMediaListEntry(status = ListStatuses.PAUSED)
        )

        val merged = mapOf(MediaCoverKey("ANIME", 3) to detail).detailFor(summary)!!

        assertEquals(ListStatuses.PAUSED, merged.mediaListEntry?.status)
        assertEquals("progress the summary knew must not be erased", 7, merged.mediaListEntry?.progress)
    }

    @Test
    fun `completed paused dropped and rewatching each keep their own status`() {
        for (status in listOf(
            ListStatuses.COMPLETED,
            ListStatuses.PAUSED,
            ListStatuses.DROPPED,
            ListStatuses.REPEATING
        )) {
            val result = state(listStatus = status)

            assertEquals("$status was not kept as the tracked status", status, result.trackedStatus)
            assertFalse("$status must not offer the quick add", result.showMediaAction)
            assertFalse("$status must not be treated as plain watching", result.isWatching)
            assertEquals("$status must be what the edit control names", status, result.editableStatus)
            assertTrue("$status still needs its edit control", result.showEntryEdit)
        }
    }

    @Test
    fun `watching keeps the quick add's own badge`() {
        val state = state(listStatus = ListStatuses.CURRENT)

        assertNull(state.trackedStatus)
        assertTrue(state.isWatching)
        assertTrue(state.showMediaAction)
    }

    @Test
    fun `a title in no list offers the quick add`() {
        val state = state(listStatus = null)

        assertNull(state.trackedStatus)
        assertFalse(state.isWatching)
        assertTrue(state.showMediaAction)
    }

    private fun state(
        listStatus: String?,
        quickAdd: QuickAddStatus = QuickAddStatus(),
        loading: Boolean = false
    ) = DetailActionState(
        listStatus = listStatus,
        quickAddStatus = quickAdd,
        isDetailLoading = loading,
        hasSequel = false
    )
}
