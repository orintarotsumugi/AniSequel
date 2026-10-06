package com.example

import com.example.data.model.FilterCriteria
import com.example.data.model.RelationKind
import com.example.data.model.StatusFilter
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the single `isNarrowing` definition used by the filter badge, the
 * "Filtered" row and the empty state. Three copies of this expression had
 * drifted apart; every criterion that actually narrows the result list has to
 * flip the flag, or the badge lies about why results are missing.
 */
class FilterCriteriaNarrowingTest {

    @Test
    fun `default criteria are not narrowing`() {
        assertFalse(FilterCriteria().isNarrowing)
    }

    @Test
    fun `every narrowing criterion trips the flag`() {
        assertTrue("search narrows", FilterCriteria(searchQuery = "frieren").isNarrowing)
        assertTrue("status filter narrows", FilterCriteria(statusFilter = StatusFilter.RELEASING).isNarrowing)
        assertTrue("hiding unreleased narrows", FilterCriteria(includeUnreleased = false).isNarrowing)
        assertTrue("format narrows", FilterCriteria(selectedFormat = "MOVIE").isNarrowing)
        assertTrue("revealing planned entries narrows", FilterCriteria(hideAlreadyPlanned = false).isNarrowing)
        assertTrue("wider relation kinds narrow", FilterCriteria(includedRelations = setOf(RelationKind.SEQUEL, RelationKind.SIDE_STORY)).isNarrowing)
        assertTrue("sequel released this year narrows", FilterCriteria(sequelReleasedThisYear = true).isNarrowing)
        assertTrue("parent completed this year narrows", FilterCriteria(parentCompletedThisYear = true).isNarrowing)
    }

    @Test
    fun `hiding an entry does not trip the flag`() {
        // Hiding one anime is a standing decision about that anime, not a view
        // of the list - it must never light a badge that cannot be switched off.
        assertFalse(FilterCriteria(hiddenMediaIds = setOf(42)).isNarrowing)
    }

    @Test
    fun `sorting alone does not narrow`() {
        assertFalse(FilterCriteria().copy(sortOption = com.example.data.model.SequelSortOption.POPULARITY).isNarrowing)
    }
}