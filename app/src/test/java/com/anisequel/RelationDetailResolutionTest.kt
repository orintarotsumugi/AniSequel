package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRelations
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.withSummaryProgress
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A relation tapped inside a title opens the *same* page as that title tapped in
 * the list.
 *
 * ## What was broken
 *
 * The `navigatedRelation` branch of the dashboard's `selectedSubject` built its
 * subject straight from the node the tap carried:
 *
 * ```kotlin
 * navigatedRelation != null -> navigatedRelation?.let { AnimeDetailSubject.Media(it) }
 * ```
 *
 * That node is the relation row's own copy, filled from `RELATED_NODE_FIELDS`:
 * id, title, format, status, episodes, cover, and the viewer's entry - and **no
 * synopsis, cast, staff, studios or relations of its own**. The sheet still fired
 * its detail query and `MediaDetailStore` still published the full node; this
 * branch simply never read it. So a followed relation drew the thin copy - "no
 * synopsis available", no credits, and the franchise row gone after a single hop -
 * while the very same title opened from the list drew all of it.
 *
 * ## What it does now
 *
 * The relation resolves through `detailNodes` exactly the way an arriving row
 * already did - the two behaviours were in the same `when` and only one of them
 * was right:
 *
 * ```kotlin
 * detailNodes[summary.id]?.withSummaryProgress(summary) ?: summary
 * ```
 *
 * ## Why this is a source-level test
 *
 * There is no Compose test harness in the unit-test source set -
 * `androidx.compose.ui.test.junit4` is an `androidTestImplementation` only - and
 * the regression isn't in the drawing, it is in *which node the drawing is handed*.
 * The same approach as `InfoFloatingBarKeyingTest`. The merge the resolution
 * depends on is pinned separately, by its own contract, in the second test.
 */
class RelationDetailResolutionTest {

    private val dashboardScreen = File(
        findProjectRoot(),
        "app/src/main/java/com/anisequel/ui/screens/dashboard/DashboardScreen.kt"
    )

    private fun findProjectRoot(): File {
        val start = File(System.getProperty("user.dir") ?: ".")
        var candidate: File? = start
        while (candidate != null) {
            if (File(candidate, "gradle.properties").isFile) return candidate
            candidate = candidate.parentFile
        }
        error("could not locate gradle.properties from $start")
    }

    @Test
    fun `a tapped relation opens through the fetched detail, not the relation node`() {
        assertTrue(
            "could not read ${dashboardScreen.path}; this guard silently passing on a " +
                "missing file is the same failure it exists to prevent",
            dashboardScreen.isFile
        )
        val source = dashboardScreen.readText()

        val branch = source
            .substringAfter("navigatedRelation != null ->", missingDelimiterValue = "")
            .substringBefore("selectedSequelId != null", missingDelimiterValue = "")
        assertTrue(
            "could not find the relation branch in ${dashboardScreen.path}",
            branch.isNotBlank()
        )

        assertTrue(
            "a tapped relation has to resolve through the fetched detail, exactly " +
                "like an arriving row. Without it the sheet fires the detail query " +
                "and throws the answer away, so the page drawn is the relation node " +
                "the card carried - no synopsis, no credits, and no relations of its " +
                "own to follow:\n$branch",
            branch.contains("detailNodes[summary.id]?.withSummaryProgress(summary) ?: summary")
        )

        val paintsTheTappedNode = Regex("""AnimeDetailSubject\.Media\(\s*(?:it|summary)\s*\)""")
        assertFalse(
            "the relation branch must not paint the tapped node directly again. " +
                "`AnimeDetailSubject.Media(it)` is the shipped bug this guard exists " +
                "for - the subject was the thin relation node, so the page was " +
                "visibly emptier than the same title opened from the list:\n$branch",
            paintsTheTappedNode.containsMatchIn(branch)
        )
    }

    @Test
    fun `the fetched detail is the display record and the relation's progress survives`() {
        val relationNode = MediaNode(
            id = 1,
            title = MediaTitle(romaji = "Sequel"),
            format = "TV",
            episodes = 12,
            mediaListEntry = SimpleMediaListEntry(progress = 4)
        )
        val fetched = MediaNode(
            id = 1,
            title = MediaTitle(romaji = "Sequel"),
            description = "The synopsis the relation node never carried",
            relations = MediaRelations(edges = emptyList())
        )

        val subject = fetched.withSummaryProgress(relationNode)

        assertEquals(
            "the fetched detail has to win: it is the record the relation node is " +
                "only a summary of, and losing its synopsis is the bug above",
            fetched.description,
            subject.description
        )
        assertEquals(fetched.relations, subject.relations)
        assertEquals(
            "the relation node's progress has to survive the merge - the detail " +
                "query is not viewer-scoped, and this is the one field the summary " +
                "knows better than the record",
            relationNode.mediaListEntry,
            subject.mediaListEntry
        )
    }
}
