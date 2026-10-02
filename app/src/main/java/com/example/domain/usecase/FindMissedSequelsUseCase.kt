package com.example.domain.usecase

import com.example.data.model.FilterCriteria
import com.example.data.model.MediaListCollection
import com.example.data.model.MissedSequel
import com.example.data.model.SequelSortOption
import com.example.data.model.StatusFilter

class FindMissedSequelsUseCase {

    fun execute(
        collection: MediaListCollection,
        filterCriteria: FilterCriteria = FilterCriteria()
    ): List<MissedSequel> {
        val lists = collection.lists ?: return emptyList()
        val allUserEntries = lists.flatMap { it.entries ?: emptyList() }
        
        // Collect all media IDs that the user already has in any of their lists
        val allUserListMediaIds = mutableSetOf<Int>()
        val plannedMediaIds = mutableSetOf<Int>()
        
        for (entry in allUserEntries) {
            val mediaId = entry.media.id
            allUserListMediaIds.add(mediaId)
            
            val entryStatus = entry.status ?: entry.media.mediaListEntry?.status
            if (entryStatus.equals("PLANNING", ignoreCase = true)) {
                plannedMediaIds.add(mediaId)
            }
        }

        // Completed or watched anime entries
        val watchedEntries = allUserEntries.filter { entry ->
            val status = entry.status ?: entry.media.mediaListEntry?.status
            status.equals("COMPLETED", ignoreCase = true) ||
                    (entry.progress != null && entry.media.episodes != null && entry.progress >= entry.media.episodes && entry.media.episodes > 0)
        }

        val missedSequels = mutableListOf<MissedSequel>()

        for (entry in watchedEntries) {
            val parentMedia = entry.media
            val parentTitle = parentMedia.title?.displayTitle ?: "Anime #${parentMedia.id}"
            val parentCover = parentMedia.coverImage?.bestUrl
            val parentStatus = parentMedia.status

            val relationEdges = parentMedia.relations?.edges ?: emptyList()
            for (edge in relationEdges) {
                if (edge.relationType.equals("SEQUEL", ignoreCase = true)) {
                    val sequelNode = edge.node
                    val sequelId = sequelNode.id
                    
                    // Check if the user already has this sequel on ANY list
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
                                parentCoverUrl = parentCover,
                                parentStatus = parentStatus,
                                sequelMedia = sequelNode,
                                isAddedToPlanning = isPlanned
                            )
                        )
                    }
                }
            }
        }

        // Distinct by sequel ID so identical sequels across multi-season parents don't duplicate
        val distinctMissed = missedSequels.distinctBy { it.sequelId }

        // Apply filters
        val filtered = distinctMissed.filter { sequel ->
            // Search query filter
            val query = filterCriteria.searchQuery.trim().lowercase()
            val matchesQuery = query.isEmpty() ||
                    sequel.sequelTitle.lowercase().contains(query) ||
                    sequel.parentTitle.lowercase().contains(query)

            // Release filter. Skipped when a specific status was chosen,
            // otherwise picking "Upcoming" while "Include unreleased" was off
            // returned an empty list with nothing on screen to explain why.
            val matchesRelease = filterCriteria.statusFilter != StatusFilter.ALL ||
                    filterCriteria.includeUnreleased ||
                    !sequel.isUnreleased

            // Status filter
            val matchesStatus = when (filterCriteria.statusFilter) {
                StatusFilter.ALL -> true
                StatusFilter.FINISHED -> sequel.status.equals("FINISHED", ignoreCase = true)
                StatusFilter.RELEASING -> sequel.status.equals("RELEASING", ignoreCase = true)
                StatusFilter.NOT_YET_RELEASED -> sequel.isUnreleased
            }

            // Format filter
            val matchesFormat = filterCriteria.selectedFormat == null ||
                    sequel.format.equals(filterCriteria.selectedFormat, ignoreCase = true)

            matchesQuery && matchesRelease && matchesStatus && matchesFormat
        }

        // Apply sorting
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
}
