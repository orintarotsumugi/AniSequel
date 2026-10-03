package com.example.domain.usecase

import com.example.data.model.FilterCriteria
import com.example.data.model.MediaListCollection
import com.example.data.model.MissedSequel
import com.example.data.model.RelationKind
import com.example.data.model.SequelSortOption
import com.example.data.model.StatusFilter

/**
 * Works out which franchise entries are missing from a user's AniList list.
 *
 * Split into [discover] and [applyFilters] on purpose. [discover] walks every
 * entry and every relation edge in the list - for a large account that is
 * hundreds of entries and well over a thousand edges - while [applyFilters] only
 * touches the candidates it is given. They used to be one function, so typing
 * in the search box re-walked the entire list on every keystroke.
 */
class FindMissedSequelsUseCase {

    /**
     * Everything the user's list is missing, before any filter or sort.
     *
     * Depends only on the list itself and on [FilterCriteria.hideAlreadyPlanned],
     * so its result is worth caching between filter changes.
     */
    fun discover(
        collection: MediaListCollection,
        filterCriteria: FilterCriteria = FilterCriteria(),
        /**
         * Build the entries the user has hidden as well as the ones they have not.
         *
         * Off by default, and that default is what every caller wants: hiding an
         * entry is meant to stop it costing anything in the sort that runs on
         * every keystroke, so the ordinary path never builds one.
         *
         * It exists for the opposite question - "what did I hide?" - which
         * [DashboardViewModel] answers by discovering with this on and keeping
         * the hidden half. That answer needs a title, a poster and a parent, and
         * all three only exist on a built [MissedSequel]; the stored preference
         * is bare AniList ids. Without this the hidden list could only ever have
         * been rendered as a row of numbers.
         */
        includeHidden: Boolean = false
    ): List<MissedSequel> {
        val lists = collection.lists ?: return emptyList()
        val allUserEntries = lists.flatMap { it.entries ?: emptyList() }

        // Every media the user already tracks in an active or completed list
        // (Completed, Watching, Paused, Dropped). These can never be missed.
        val activeOrCompletedMediaIds = mutableSetOf<Int>()
        // ...and the subset sitting in Planning, which the switch controls.
        val plannedMediaIds = mutableSetOf<Int>()

        for (entry in allUserEntries) {
            val mediaId = entry.media.id
            val entryStatus = entry.status ?: entry.media.mediaListEntry?.status
            if (entryStatus.equals("PLANNING", ignoreCase = true)) {
                plannedMediaIds.add(mediaId)
            } else {
                activeOrCompletedMediaIds.add(mediaId)
            }
        }

        val includedRelations = filterCriteria.includedRelations
            .map { it.apiValue }
            .toSet()

        val missedSequels = mutableListOf<MissedSequel>()

        for (entry in allUserEntries) {
            if (!isWatched(entry.status, entry.media.episodes, entry.progress)) continue

            val parentMedia = entry.media
            val parentTitle = parentMedia.title?.displayTitle ?: "Anime #${parentMedia.id}"

            for (edge in parentMedia.relations?.edges ?: emptyList()) {
                val relationType = edge.relationType
                if (relationType == null || !includedRelations.contains(relationType.uppercase())) continue

                val sequelNode = edge.node
                val sequelId = sequelNode.id

                // If the user already watched or is actively tracking this franchise entry,
                // it is never a "missed" entry.
                if (activeOrCompletedMediaIds.contains(sequelId)) continue

                // The user's own "do not remind me about this one again". Checked
                // here rather than in [applyFilters] so a hidden entry is never
                // built in the first place, and so it stops costing anything in
                // the sort that runs on every keystroke.
                if (!includeHidden && filterCriteria.hiddenMediaIds.contains(sequelId)) continue

                val isPlanned = plannedMediaIds.contains(sequelId) ||
                        sequelNode.mediaListEntry?.status.equals("PLANNING", ignoreCase = true)

                // If it is on the planning list and the user chose to hide planned entries, skip.
                if (isPlanned && filterCriteria.hideAlreadyPlanned) continue

                missedSequels.add(
                    MissedSequel(
                        parentId = parentMedia.id,
                        parentTitle = parentTitle,
                        // Both taken from the entry the edge was walked from, so
                        // showing them costs no extra AniList request.
                        parentCoverUrl = parentMedia.coverImage?.bestUrl,
                        parentCoverColor = parentMedia.coverImage?.color,
                        sequelMedia = sequelNode,
                        relationType = RelationKind.fromApi(relationType).apiValue,
                        isAddedToPlanning = isPlanned
                    )
                )
            }
        }

        // The same sequel reachable from two parents is one gap, not two.
        return missedSequels.distinctBy { it.sequelId }
    }

    /**
     * Filters and orders candidates. Cheap enough to re-run on every keystroke,
     * which is exactly why it is separate from [discover].
     */
    fun applyFilters(
        candidates: List<MissedSequel>,
        filterCriteria: FilterCriteria
    ): List<MissedSequel> {
        val query = filterCriteria.searchQuery.trim().lowercase()

        val filtered = candidates.filter { sequel ->
            val matchesQuery = query.isEmpty() ||
                    sequel.sequelTitle.lowercase().contains(query) ||
                    sequel.parentTitle.lowercase().contains(query) ||
                    sequel.sequelMedia.title?.romaji?.lowercase()?.contains(query) == true ||
                    sequel.sequelMedia.title?.english?.lowercase()?.contains(query) == true ||
                    sequel.sequelMedia.title?.native?.lowercase()?.contains(query) == true ||
                    sequel.synonyms.any { it.lowercase().contains(query) }

            // Release filter. Skipped when a specific status was chosen,
            // otherwise picking "Upcoming" while "Include unreleased" was off
            // returned an empty list with nothing on screen to explain why.
            val matchesRelease = filterCriteria.statusFilter != StatusFilter.ALL ||
                    filterCriteria.includeUnreleased ||
                    !sequel.isUnreleased

            val matchesStatus = when (filterCriteria.statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.FINISHED -> sequel.status.equals("FINISHED", ignoreCase = true)
                StatusFilter.RELEASING -> sequel.status.equals("RELEASING", ignoreCase = true)
                StatusFilter.NOT_YET_RELEASED -> sequel.isUnreleased
            }

            val matchesFormat = filterCriteria.selectedFormat == null ||
                    sequel.format.equals(filterCriteria.selectedFormat, ignoreCase = true)

            matchesQuery && matchesRelease && matchesStatus && matchesFormat
        }

        return when (filterCriteria.sortOption) {
            SequelSortOption.RELEASE_DATE_DESC -> filtered.sortedWith(
                compareByDescending<MissedSequel> { it.sequelMedia.startDate?.year ?: -1 }
                    .thenByDescending { it.sequelMedia.startDate?.month ?: -1 }
                    .thenByDescending { it.sequelMedia.startDate?.day ?: -1 }
            )
            SequelSortOption.RELEASE_DATE_ASC -> filtered.sortedWith(
                compareBy<MissedSequel> { it.sequelMedia.startDate?.year ?: 9999 }
                    .thenBy { it.sequelMedia.startDate?.month ?: 99 }
                    .thenBy { it.sequelMedia.startDate?.day ?: 99 }
            )
            SequelSortOption.TITLE_ASC -> filtered.sortedBy { it.sequelTitle.lowercase() }
            SequelSortOption.POPULARITY -> filtered.sortedByDescending { it.sequelMedia.popularity ?: 0 }
            SequelSortOption.SCORE -> filtered.sortedByDescending { it.sequelMedia.averageScore ?: 0 }
        }
    }

    /** One-shot path, for callers that do not hold on to the candidates. */
    fun execute(
        collection: MediaListCollection,
        filterCriteria: FilterCriteria = FilterCriteria()
    ): List<MissedSequel> = applyFilters(discover(collection, filterCriteria), filterCriteria)

    /**
     * Splits a full candidate list into what is still on offer and what the user hid.
     *
     * Takes candidates discovered with `includeHidden = true`, so both halves
     * carry the titles, posters and parents the UI needs to render them. The
     * hidden half is sorted by title because it is a settings-style list of
     * decisions rather than a ranked feed - "the one I am looking for" is far
     * more likely to be a name someone half-remembers than a date.
     */
    fun splitHidden(
        candidates: List<MissedSequel>,
        hiddenMediaIds: Set<Int>
    ): HiddenSplit {
        if (hiddenMediaIds.isEmpty()) return HiddenSplit(candidates, emptyList())
        val visible = ArrayList<MissedSequel>(candidates.size)
        val hidden = ArrayList<MissedSequel>()
        for (candidate in candidates) {
            if (candidate.sequelId in hiddenMediaIds) hidden.add(candidate) else visible.add(candidate)
        }
        hidden.sortBy { it.sequelTitle.lowercase() }
        return HiddenSplit(visible, hidden)
    }

    /** The two halves of [splitHidden]. */
    data class HiddenSplit(
        val visible: List<MissedSequel>,
        val hidden: List<MissedSequel>
    )

    /**
     * An entry counts as watched when the user marked it complete, or when their
     * progress reached the last episode without them ever setting a status.
     */
    private fun isWatched(status: String?, episodes: Int?, progress: Int?): Boolean =
        status.equals("COMPLETED", ignoreCase = true) ||
                (progress != null && episodes != null && episodes > 0 && progress >= episodes)
}
