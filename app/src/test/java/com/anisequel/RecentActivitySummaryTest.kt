package com.anisequel

import com.anisequel.data.model.MediaCoverImage
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.domain.usecase.RecentActivitySummary
import com.anisequel.domain.usecase.SummarizeRecentActivityUseCase
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The activity widget's count, and the title it names.
 *
 * The tile said "0 recent updates" for every signed-in account because no
 * summary was ever computed. The regression to avoid now is subtler: a count
 * that includes entries with no timestamp or entries older than the window
 * would be just as wrong as zero, and would be harder to notice.
 */
class RecentActivitySummaryTest {

    private val useCase = SummarizeRecentActivityUseCase()
    private val now = 1_800_000_000

    @Test
    fun `counts only entries updated inside the window`() {
        val summary = useCase.execute(
            collection = collection(
                entry(id = 1, updatedAt = now - 60, title = "Yesterday"),
                entry(id = 2, updatedAt = now - 8 * DAY, title = "Old"),
                entry(id = 3, updatedAt = null, title = "No date"),
                entry(id = 4, updatedAt = now - 2 * DAY, title = "Older than yesterday")
            ),
            nowSeconds = now
        )

        assertEquals("only the two in-window entries count", 2, summary.count)
        assertEquals(1, summary.latestMediaId ?: -1)
        assertEquals("Yesterday", summary.latestTitle)
        assertEquals("https://img.anili.st/1.jpg", summary.latestCoverUrl)
    }

    @Test
    fun `an empty collection is the empty summary`() {
        val summary = useCase.execute(MediaListCollection(emptyList()), nowSeconds = now)

        assertEquals(RecentActivitySummary.Empty, summary)
    }

    @Test
    fun `a title touched twice counts once and keeps its latest touch`() {
        val summary = useCase.execute(
            collection = MediaListCollection(
                lists = listOf(
                    MediaListGroup(
                        name = "Watching",
                        status = "CURRENT",
                        entries = listOf(
                            entry(id = 5, updatedAt = now - 100, title = "Duplicated (old)")
                        )
                    ),
                    MediaListGroup(
                        name = "Completed",
                        status = "COMPLETED",
                        entries = listOf(
                            entry(id = 5, updatedAt = now - 10, title = "Duplicated (new)")
                        )
                    )
                )
            ),
            nowSeconds = now
        )

        assertEquals("one title is one update", 1, summary.count)
        assertEquals("the latest touch names the tile", "Duplicated (new)", summary.latestTitle)
        assertEquals("https://img.anili.st/5.jpg", summary.latestCoverUrl)
    }

    private fun collection(vararg entries: MediaListEntryItem) = MediaListCollection(
        lists = listOf(
            MediaListGroup(
                name = "Watching",
                status = "CURRENT",
                entries = entries.toList()
            )
        )
    )

    private fun entry(id: Int, updatedAt: Int?, title: String) = MediaListEntryItem(
        status = "CURRENT",
        updatedAt = updatedAt,
        media = MediaNode(
            id = id,
            title = MediaTitle(english = title),
            coverImage = MediaCoverImage(medium = "https://img.anili.st/$id.jpg")
        )
    )

    private companion object {
        const val DAY = 24 * 60 * 60
    }
}
