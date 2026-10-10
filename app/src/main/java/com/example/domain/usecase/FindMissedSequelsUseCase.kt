package com.example.domain.usecase

import com.example.data.model.FilterCriteria
import com.example.data.model.MediaListCollection
import com.example.data.model.MissedSequel
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

        val completedMediaIds = mutableSetOf<Int>()
        val watchingMediaIds = mutableSetOf<Int>()
        val plannedMediaIds = mutableSetOf<Int>()
        val otherListMediaIds = mutableSetOf<Int>()

        for (entry in allUserEntries) {
            val mediaId = entry.media.id
            val entryStatus = entry.status ?: entry.media.mediaListEntry?.status
            when {
                entryStatus.equals("PLANNING", ignoreCase = true) -> plannedMediaIds.add(mediaId)
                entryStatus.equals("CURRENT", ignoreCase = true) || entryStatus.equals("WATCHING", ignoreCase = true) -> watchingMediaIds.add(mediaId)
                isWatched(entryStatus, entry.media.episodes, entry.progress) -> completedMediaIds.add(mediaId)
                else -> otherListMediaIds.add(mediaId)
            }
        }

        val includedRelations = filterCriteria.includedRelations
        val missedSequels = mutableListOf<MissedSequel>()
        // Performance optimization: prevent duplicate candidate allocations during discovery
        val seenSequelIds = mutableSetOf<Int>()

        for (entry in allUserEntries) {
            val entryStatus = entry.status ?: entry.media.mediaListEntry?.status
            val isParentWatched = isWatched(entryStatus, entry.media.episodes, entry.progress)
            val isParentWatching = entryStatus.equals("CURRENT", ignoreCase = true) || entryStatus.equals("WATCHING", ignoreCase = true)
            val isParentOther = !isParentWatched && !isParentWatching && !entryStatus.equals("PLANNING", ignoreCase = true)

            // Can this entry serve as a parent from which to explore franchise edges?
            val canExploreFromParent = isParentWatched ||
                (filterCriteria.includeCurrentlyWatching && isParentWatching) ||
                (filterCriteria.includeInList && (isParentWatching || isParentOther))

            if (!canExploreFromParent) continue

            val parentMedia = entry.media
            val parentTitle = parentMedia.title?.displayTitle ?: "Anime #${parentMedia.id}"
            val parentCompletedYear = entry.completedAt?.year

            for (edge in parentMedia.relations?.edges ?: emptyList()) {
                val relationType = edge.relationType ?: continue

                // Matched case-insensitively against the handful of included
                // kinds rather than by uppercasing the value first.
                val relationKind = includedRelations.firstOrNull {
                    it.apiValue.equals(relationType, ignoreCase = true)
                } ?: continue

                val sequelNode = edge.node
                val sequelId = sequelNode.id

                val isWatching = watchingMediaIds.contains(sequelId) ||
                    sequelNode.mediaListEntry?.status.equals("CURRENT", ignoreCase = true) ||
                    sequelNode.mediaListEntry?.status.equals("WATCHING", ignoreCase = true)

                val isCompleted = completedMediaIds.contains(sequelId) ||
                    sequelNode.mediaListEntry?.status.equals("COMPLETED", ignoreCase = true)

                val isOtherList = otherListMediaIds.contains(sequelId)

                val isPlanned = plannedMediaIds.contains(sequelId) ||
                    sequelNode.mediaListEntry?.status.equals("PLANNING", ignoreCase = true)

                // Skip rules based on toggles:
                // 1. If currently watching: only keep if includeCurrentlyWatching or includeInList is on
                if (isWatching && !filterCriteria.includeCurrentlyWatching && !filterCriteria.includeInList) {
                    continue
                }
                // 2. If already completed or in other list (paused/dropped): only keep if includeInList is on
                if ((isCompleted || isOtherList) && !filterCriteria.includeInList) {
                    continue
                }
                // 3. If in planning: respect hideAlreadyPlanned (unless includeInList is on)
                if (isPlanned && filterCriteria.hideAlreadyPlanned && !filterCriteria.includeInList) {
                    continue
                }

                // The user's own "do not remind me about this one again".
                if (!includeHidden && filterCriteria.hiddenMediaIds.contains(sequelId)) continue

                // Dedup on the fly: the same sequel reachable from two parents is one gap
                if (!seenSequelIds.add(sequelId)) continue

                missedSequels.add(
                    MissedSequel(
                        parentId = parentMedia.id,
                        parentTitle = parentTitle,
                        // Both taken from the entry the edge was walked from, so
                        // showing them costs no extra AniList request.
                        parentCoverUrl = parentMedia.coverImage?.bestUrl,
                        parentCoverColor = parentMedia.coverImage?.color,
                        parentCompletedYear = parentCompletedYear,
                        sequelMedia = sequelNode,
                        relationType = relationKind.apiValue,
                        isAddedToPlanning = isPlanned,
                        isAddedToWatching = isWatching
                    )
                )
            }
        }

        return missedSequels
    }

    /**
     * Filters and orders candidates. Cheap enough to re-run on every keystroke,
     * which is exactly why it is separate from [discover].
     *
     * [currentYear] is a parameter rather than read inside the filter because
     * "this year" is a real input to the answer: tests pass the year they mean
     * instead of inheriting whatever year the machine running them happens to
     * be in.
     */
    fun applyFilters(
        candidates: List<MissedSequel>,
        filterCriteria: FilterCriteria,
        currentYear: Int = java.time.LocalDate.now().year
    ): List<MissedSequel> {
        val query = filterCriteria.searchQuery.trim().lowercase(java.util.Locale.ROOT)

        val filtered = candidates.filter { sequel ->
            // One pre-lowercased string per candidate rather than six
            // `lowercase()` calls per candidate per keystroke. See
            // [MissedSequel.searchableText] for why the fields are joined the
            // way they are.
            val matchesQuery = query.isEmpty() || sequel.searchableText.contains(query)

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

            // Both year axes are off by default, so the common path is two
            // boolean checks. An undiscovered date (null year) never equals
            // the current year, which is the honest answer for both: a show
            // with no announced date is not releasing this year, and a parent
            // the viewer never marked completed was not completed this year.
            val matchesYear =
                (!filterCriteria.sequelReleasedThisYear ||
                    sequel.sequelMedia.startDate?.year == currentYear) &&
                    (!filterCriteria.parentCompletedThisYear ||
                        sequel.parentCompletedYear == currentYear)

            matchesQuery && matchesRelease && matchesStatus && matchesFormat && matchesYear
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
            SequelSortOption.TITLE_ASC -> filtered.sortedBy { it.sequelTitle.lowercase(java.util.Locale.ROOT) }
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
        hidden.sortBy { it.sequelTitle.lowercase(java.util.Locale.ROOT) }
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
