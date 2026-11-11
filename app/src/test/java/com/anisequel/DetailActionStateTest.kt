package com.anisequel

import com.anisequel.domain.list.ListStatuses
import com.anisequel.ui.components.detail.DetailActionState
import com.anisequel.ui.viewmodel.QuickAddStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DetailActionStateTest {
    @Test
    fun `unknown list membership waits for detail before offering a write`() {
        val loading = state(loading = true)
        assertFalse(loading.showMediaAction)
        assertFalse(loading.sharesRow)
        assertFalse(loading.showEntryEdit)
        assertTrue(state().showMediaAction)
    }

    @Test
    fun `completed paused and dropped entries offer editing not quick add`() {
        for (status in listOf(ListStatuses.COMPLETED, ListStatuses.PAUSED, ListStatuses.DROPPED)) {
            val result = state(status = status, loading = true)
            assertFalse(result.showMediaAction)
            assertFalse(result.sharesRow)
            assertTrue(result.showEntryEdit)
            assertEquals(status, result.editableStatus)
        }
    }

    @Test
    fun `known progress and in-flight writes remain visible while detail loads`() {
        val planning = state(loading = true, quickAdd = QuickAddStatus(isAddedToPlanning = true))
        assertTrue(planning.showMediaAction)
        assertTrue(planning.isPlanned)
        assertTrue(planning.showEntryEdit)
        assertEquals(ListStatuses.PLANNING, planning.editableStatus)
        val watching = state(loading = true, quickAdd = QuickAddStatus(isAddedToWatching = true))
        assertTrue(watching.showMediaAction)
        assertTrue(watching.isWatching)
        assertEquals(ListStatuses.CURRENT, watching.editableStatus)
        val busy = state(loading = true, quickAdd = QuickAddStatus(isAddingToPlanning = true))
        assertTrue(busy.showMediaAction)
        assertFalse(busy.showEntryEdit)
    }

    @Test
    fun `repeating entries keep their status and watching confirmation`() {
        val result = state(status = ListStatuses.REPEATING, loading = true)
        assertTrue(result.isWatching)
        assertTrue(result.showMediaAction)
        assertTrue(result.showEntryEdit)
        assertEquals(ListStatuses.REPEATING, result.editableStatus)
    }

    @Test
    fun `recommendations retain the dashboard action instead of the media action`() {
        val result = state(sequel = true)
        assertTrue(result.sharesRow)
        assertFalse(result.showMediaAction)
        assertFalse(result.showEntryEdit)
    }

    private fun state(
        status: String? = null,
        loading: Boolean = false,
        quickAdd: QuickAddStatus = QuickAddStatus(),
        sequel: Boolean = false
    ) = DetailActionState(status, quickAdd, loading, sequel)
}
