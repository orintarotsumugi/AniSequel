package com.example.domain.usecase

import com.example.data.model.ArrivingEntry
import com.example.data.model.MediaListCollection

/**
 * Works out which of the viewer's own entries are airing now or have not
 * started yet.
 *
 * Separate from [FindMissedSequelsUseCase] because the two answer different
 * questions about the same collection: that one walks relation edges to find
 * what the list is *missing*, this one reads the list itself for what is
 * *coming*. It is a single pass over the entries and does not touch relations
 * at all, so it is cheap enough to run alongside the discovery walk.
 */
class FindArrivingEntriesUseCase {

    fun execute(collection: MediaListCollection): List<ArrivingEntry> {
        val entries = collection.lists?.flatMap { it.entries.orEmpty() } ?: return emptyList()

        val arriving = entries.mapNotNull { entry ->
            val media = entry.media
            val mediaStatus = media.status ?: return@mapNotNull null

            // Only what is happening now or is announced to happen. A finished
            // or cancelled show has already arrived, whatever the viewer's own
            // list says about it.
            val isArriving = mediaStatus.equals("RELEASING", ignoreCase = true) ||
                mediaStatus.equals("NOT_YET_RELEASED", ignoreCase = true)
            if (!isArriving) return@mapNotNull null

            // ...and only while it is still *theirs*. A show the viewer marked
            // Completed or Dropped is not arriving for them - the first is
            // finished as far as they are concerned, and the second was a
            // decision. Paused stays in: they mean to come back to it.
            val ownStatus = entry.status ?: media.mediaListEntry?.status
            if (ownStatus.equals("COMPLETED", ignoreCase = true) ||
                ownStatus.equals("DROPPED", ignoreCase = true)
            ) return@mapNotNull null

            ArrivingEntry(
                mediaId = media.id,
                title = media.title?.displayTitle ?: "Anime #${media.id}",
                coverUrl = media.coverImage?.bestUrl,
                coverColor = media.coverImage?.color,
                status = mediaStatus,
                startDate = media.startDate,
                nextAiringAt = media.nextAiringEpisode?.airingAt,
                nextEpisodeNumber = media.nextAiringEpisode?.episode,
                episodes = media.episodes,
                progress = entry.progress
            )
        }

        // One row per show even if it somehow sits in two of the viewer's
        // lists.
        return arriving
            .distinctBy { it.mediaId }
            .sortedWith(
                // Airing first - what is on now beats what is announced - then
                // soonest first inside each half.
                compareByDescending<ArrivingEntry> { it.isAiring }
                    .thenComparator { a, b -> soonestFirst(a, b) }
            )
    }

    /**
     * Inside a group, the entry with the nearer date wins; an unknown date
     * sorts last rather than first, because "date unknown" is the least useful
     * row to put at the front.
     *
     * Only ever called for two entries in the same half, so an airing entry is
     * always compared on its next episode and an upcoming one on its start.
     */
    private fun soonestFirst(a: ArrivingEntry, b: ArrivingEntry): Int {
        if (a.isAiring != b.isAiring) return 0
        return if (a.isAiring) {
            (a.nextAiringAt ?: Long.MAX_VALUE).compareTo(b.nextAiringAt ?: Long.MAX_VALUE)
        } else {
            startKey(a).compareTo(startKey(b))
        }
    }

    /** The announced start as one sortable number, with unknowns pushed to the end. */
    private fun startKey(entry: ArrivingEntry): Int {
        val year = entry.startDate?.year ?: return Int.MAX_VALUE
        val month = entry.startDate.month ?: 99
        val day = entry.startDate.day ?: 99
        return year * 10_000 + month * 100 + day
    }
}
