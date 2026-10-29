package com.anisequel.domain.usecase

import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.displayName
import com.anisequel.data.model.posterUrl

/**
 * What the activity widget's tile says about a viewer's list.
 *
 * [latestCoverUrl] is carried even though the tile currently draws the
 * viewer's avatar: the summary is the list's own answer, and deciding which
 * artwork a tile shows is the widget's job, not this one's.
 */
data class RecentActivitySummary(
    val count: Int = 0,
    val latestTitle: String? = null,
    val latestCoverUrl: String? = null,
    val latestMediaId: Int? = null
) {
    companion object {
        val Empty = RecentActivitySummary()
    }
}

/**
 * Counts the list entries the viewer touched inside a recent window.
 *
 * ## Why `updatedAt`
 *
 * It is the only recency signal an AniList list entry carries - the collection
 * arrives grouped by status and the query has no date sort. It is what the
 * Library's "Recently updated" sort reads, so the widget and the page agree
 * about which entry is newest.
 *
 * ## Why a window rather than "since the last refresh"
 *
 * The dashboard refetches on an interval and on open, but the list itself
 * carries no marker for what arrived with this response. A fixed seven-day
 * window is a fact the summary can state on its own, and it matches the tile's
 * caption - "recent updates" - rather than the app's polling schedule.
 *
 * Entries with no `updatedAt` are ignored rather than treated as epoch: an
 * absent date is not evidence of an old edit, and counting it as one would put
 * every entry without a timestamp into "never".
 */
class SummarizeRecentActivityUseCase {

    fun execute(
        collection: MediaListCollection,
        nowSeconds: Int,
        windowSeconds: Int = DEFAULT_WINDOW_SECONDS
    ): RecentActivitySummary {
        val entries = collection.lists?.flatMap { it.entries.orEmpty() }
            ?: return RecentActivitySummary.Empty

        val cutoff = nowSeconds - windowSeconds
        val recent = entries
            .mapNotNull { entry -> entry.updatedAt?.let { entry to it } }
            .filter { (_, updatedAt) -> updatedAt >= cutoff }
            // One title counts once even if it somehow sits in two lists. The
            // newest touch of that title is the one the tile names.
            .sortedByDescending { (_, updatedAt) -> updatedAt }
            .distinctBy { (entry, _) -> entry.media.id }

        val latest = recent.firstOrNull()?.first ?: return RecentActivitySummary.Empty

        return RecentActivitySummary(
            count = recent.size,
            latestTitle = latest.media.displayName,
            latestCoverUrl = latest.media.posterUrl,
            latestMediaId = latest.media.id
        )
    }

    companion object {
        /** Seven days. The tile's caption is "recent updates", not "today". */
        const val DEFAULT_WINDOW_SECONDS = 7 * 24 * 60 * 60
    }
}
