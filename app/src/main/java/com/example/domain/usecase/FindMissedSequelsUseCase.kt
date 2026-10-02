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
        filterCriteria: FilterCriteria = FilterCriteria()
    ): List<MissedSequel> {
        val lists = collection.lists ?: return emptyList()
        val allUserEntries = lists.flatMap { it.entries ?: emptyList() }

        // Every media the user already tracks, in any list.
        val allUserListMediaIds = mutableSetOf<Int>()
        // ...and the subset sitting in Planning, which the switch is named after.
        val plannedMediaIds = mutableSetOf<Int>()

        for (entry in allUserEntries) {
            val mediaId = entry.media.id
            allUserListMediaIds.add(mediaId)

            val entryStatus = entry.status ?: entry.media.mediaListEntry?.status
            if (entryStatus.equals("PLANNING", ignoreCase = true)) {
                plannedMediaIds.add(mediaId)
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

                val alreadyInUserList = allUserListMediaIds.contains(sequelId)
                val isPlanned = plannedMediaIds.contains(sequelId) ||
                        sequelNode.mediaListEntry?.status.equals("PLANNING", ignoreCase = true)

                // "Hide already planned" off means *show everything*, including
                // the entries the user has already saved. The old expression,
                // `!alreadyInUserList || isPlanned`, could never do that: an
                // entry the user had on a non-planning list was filtered out by
                // both halves of the disjunction, so turning the switch off
                // only ever added back the planning entries it already showed.
                val isMissed = !filterCriteria.hideAlreadyPlanned ||
                        (!alreadyInUserList && !isPlanned)

                if (isMissed) {
                    missedSequels.add(
                        MissedSequel(
                            parentId = parentMedia.id,
                            parentTitle = parentTitle,
                            sequelMedia = sequelNode,
                            relationType = RelationKind.fromApi(relationType).apiValue,
                            isAddedToPlanning = isPlanned
                        )
                    )
                }
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
                    sequel.parentTitle.lowercase().contains(query)

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
     * An entry counts as watched when the user marked it complete, or when their
     * progress reached the last episode without them ever setting a status.
     */
    private fun isWatched(status: String?, episodes: Int?, progress: Int?): Boolean =
        status.equals("COMPLETED", ignoreCase = true) ||
                (progress != null && episodes != null && episodes > 0 && progress >= episodes)
}