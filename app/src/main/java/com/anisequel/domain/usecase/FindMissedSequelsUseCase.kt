package com.anisequel.domain.usecase

import com.anisequel.data.model.FilterCriteria
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.SequelSortOption
import com.anisequel.data.model.StatusFilter

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
            addListBucket(
                mediaId = entry.media.id,
                status = entry.status ?: entry.media.mediaListEntry?.status,
                episodes = entry.media.episodes,
                progress = entry.progress,
                completed = completedMediaIds,
                watching = watchingMediaIds,
                planned = plannedMediaIds,
                other = otherListMediaIds
            )
        }

        val missedSequels = mutableListOf<MissedSequel>()
        // Performance optimization: prevent duplicate candidate allocations during discovery
        val seenSequelIds = mutableSetOf<Int>()

        for (entry in allUserEntries) {
            val entryStatus = entry.status ?: entry.media.mediaListEntry?.status
            // Can this entry serve as a parent from which to explore franchise edges?
            if (!canExploreFromParent(entryStatus, entry.media.episodes, entry.progress, filterCriteria)) continue

            val parentMedia = entry.media
            val parentTitle = parentMedia.title?.displayTitle ?: "Anime #${parentMedia.id}"
            val parentCompletedYear = entry.completedAt?.year

            for (edge in parentMedia.relations?.edges ?: emptyList()) {
                val relationType = edge.relationType ?: continue

                // Matched case-insensitively against the handful of included
                // kinds rather than by uppercasing the value first.
                val relationKind = includedRelationApiValue(relationType, filterCriteria) ?: continue

                val sequelNode = edge.node
                val sequelId = sequelNode.id

                val flags = listFlagsFor(
                    sequelId = sequelId,
                    listStatus = sequelNode.mediaListEntry?.status,
                    completed = completedMediaIds,
                    watching = watchingMediaIds,
                    planned = plannedMediaIds,
                    other = otherListMediaIds
                )
                if (shouldSkipSequel(flags, sequelId, filterCriteria, includeHidden)) continue

                // Dedup on the fly: the same sequel reachable from two parents is one gap
                if (!seenSequelIds.add(sequelId)) continue

                missedSequels.add(
                    MissedSequel(
                        parentId = parentMedia.id,
                        parentType = parentMedia.type,
                        parentTitle = parentTitle,
                        // Both taken from the entry the edge was walked from, so
                        // showing them costs no extra AniList request.
                        parentCoverUrl = parentMedia.coverImage?.smallSlotUrl,
                        parentCoverLargeUrl = parentMedia.coverImage?.bestUrl,
                        parentCoverColor = parentMedia.coverImage?.color,
                        parentCompletedYear = parentCompletedYear,
                        sequelMedia = sequelNode,
                        relationType = relationKind,
                        isAddedToPlanning = flags.planned,
                        isAddedToWatching = flags.watching
                    )
                )
            }
        }

        return missedSequels
    }


private data class SequelListFlags(
    val watching: Boolean,
    val completed: Boolean,
    val other: Boolean,
    val planned: Boolean
)

/** Which bucket an entry belongs to; the same answer [discover] uses later. */
private fun addListBucket(
    mediaId: Int,
    status: String?,
    episodes: Int?,
    progress: Int?,
    completed: MutableSet<Int>,
    watching: MutableSet<Int>,
    planned: MutableSet<Int>,
    other: MutableSet<Int>
) {
    when {
        status.equals("PLANNING", ignoreCase = true) -> planned.add(mediaId)
        status.equals("CURRENT", ignoreCase = true) ||
            status.equals("WATCHING", ignoreCase = true) -> watching.add(mediaId)
        isWatched(status, episodes, progress) -> completed.add(mediaId)
        else -> other.add(mediaId)
    }
}

private fun canExploreFromParent(
    status: String?,
    episodes: Int?,
    progress: Int?,
    criteria: FilterCriteria
): Boolean {
    val watched = isWatched(status, episodes, progress)
    val watching = status.equals("CURRENT", ignoreCase = true) ||
        status.equals("WATCHING", ignoreCase = true)
    val other = !watched && !watching && !status.equals("PLANNING", ignoreCase = true)
    return watched ||
        (criteria.includeCurrentlyWatching && watching) ||
        (criteria.includeInList && (watching || other))
}

private fun includedRelationApiValue(
    relationType: String,
    criteria: FilterCriteria
): String? = criteria.includedRelations.firstOrNull {
    it.apiValue.equals(relationType, ignoreCase = true)
}?.apiValue

private fun listFlagsFor(
    sequelId: Int,
    listStatus: String?,
    completed: Set<Int>,
    watching: Set<Int>,
    planned: Set<Int>,
    other: Set<Int>
): SequelListFlags = SequelListFlags(
    watching = watching.contains(sequelId) ||
        listStatus.equals("CURRENT", ignoreCase = true) ||
        listStatus.equals("WATCHING", ignoreCase = true),
    completed = completed.contains(sequelId) ||
        listStatus.equals("COMPLETED", ignoreCase = true),
    other = other.contains(sequelId),
    planned = planned.contains(sequelId) ||
        listStatus.equals("PLANNING", ignoreCase = true)
)

private fun shouldSkipSequel(
    flags: SequelListFlags,
    sequelId: Int,
    criteria: FilterCriteria,
    includeHidden: Boolean
): Boolean {
    // 1. If currently watching: only keep if includeCurrentlyWatching or includeInList is on
    if (flags.watching && !criteria.includeCurrentlyWatching && !criteria.includeInList) return true
    // 2. If already completed or in other list (paused/dropped): only keep if includeInList is on
    if ((flags.completed || flags.other) && !criteria.includeInList) return true
    // 3. If in planning: respect hideAlreadyPlanned (unless includeInList is on)
    if (flags.planned && criteria.hideAlreadyPlanned && !criteria.includeInList) return true
    // The user's own "do not remind me about this one again".
    return !includeHidden && criteria.hiddenMediaIds.contains(sequelId)
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
                // Through the model's own predicate, not the display label.
                // `sequel.status` is `statusLabel` ("Airing"), so comparing it to
                // the raw enum key "RELEASING" matched nothing at all - choosing
                // "Airing" in the filter showed an empty list.
                StatusFilter.RELEASING -> sequel.isAiring
                // Through the model's own predicate, which the status chip also
                // reads. A second case-insensitive string compare here is how the
                // card and the filter end up disagreeing about one entry.
                StatusFilter.HIATUS -> sequel.isHiatus
                StatusFilter.CANCELLED -> sequel.status.equals("CANCELLED", ignoreCase = true)
                StatusFilter.NOT_YET_RELEASED -> sequel.isUnreleased
            }

            // Compared against the *raw* field, not `sequel.format`.
            //
            // `MissedSequel.format` is `MediaNode.formatLabel`, which now spells the
            // enum for a reader - "TV Short", "Movie". The filter chips hold AniList's
            // own keys ("TV", "MOVIE", "SPECIAL"), so comparing the readable label to
            // a raw key silently matched nothing: choosing "Movies" would have
            // filtered the list to zero entries. Read the raw field, and let
            // `formatLabel` do the spelling for the UI that shows it.
            val matchesFormat = filterCriteria.selectedFormat == null ||
                    sequel.sequelMedia.format.equals(filterCriteria.selectedFormat, ignoreCase = true)

            // OR across the selection, and case-insensitive because the chips
            // carry AniList's spelling while a cached node may not.
            val genres = filterCriteria.selectedGenres
            val matchesGenre = genres.isEmpty() ||
                    sequel.genres.any { genre -> genres.any { it.equals(genre, ignoreCase = true) } }

            // An unrated sequel (null averageScore) has no score to clear the
            // bar, so it drops out while a minimum is set. See
            // [filterScoreOptions] - the alternative, keeping unrated entries,
            // would surface titles the viewer did not ask to see as "meeting"
            // a threshold AniList never measured them against.
            val matchesScore = filterCriteria.minScore == null ||
                    (sequel.sequelMedia.averageScore ?: -1) >= filterCriteria.minScore

            // Same shape, same rule as the score floor: a title AniList has not
            // given a length (or given 0 for "not announced") has no number to
            // clear the bar, so it drops out instead of being claimed as short.
            val announcedEpisodes = sequel.sequelMedia.episodes?.takeIf { it > 0 } ?: -1
            val matchesEpisodes = filterCriteria.minEpisodes == null ||
                    announcedEpisodes >= filterCriteria.minEpisodes

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

            matchesQuery && matchesRelease && matchesStatus && matchesFormat &&
                matchesGenre && matchesScore && matchesEpisodes && matchesYear
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
