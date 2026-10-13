package com.example.domain.tier

/**
 * The domain model behind the profile's Tier List.
 *
 * Everything here is pure: no Android, no Compose, no coroutines. That is not
 * tidiness for its own sake. The same layout produced by [layoutTierList] is
 * drawn two ways - on screen by Compose and into an SVG by [tierListToSvg] - and
 * two independent arrangements of the same list is exactly the situation where
 * one of them quietly disagrees with the other. One geometry, two renderers.
 */

/**
 * One tier: a row of the list, with the label and colour it is drawn with.
 *
 * [id] rather than [label] as the identity, because the standard template's
 * labels are editable - renaming "A" to "Excellent" must not orphan every entry
 * filed under it. The id is generated once and never shown.
 */
data class TierDefinition(
    val id: String,
    val label: String,
    /**
     * The row's fill, as an ARGB `Long` in the `0xAARRGGBB` layout.
     *
     * A packed long rather than a Compose `Color` on purpose: this is the layer
     * the SVG writer reads, and an `Int` that has to be masked differently by each
     * renderer is a bug waiting for the one that forgets.
     */
    val colorArgb: Long
)

/**
 * The conventional S/A/B/C/D/F template.
 *
 * The template every "anime tier list" screenshot uses, so a list built here is
 * legible to somebody who has seen a hundred of them without being told what the
 * letters mean. F rather than E, for the same reason.
 *
 * These are the *defaults*, not a fixed set - [tierForScore] reads whatever list
 * it is handed, and the tiers are reorderable, addable and removable. What is
 * fixed is only the score bands, so that "what does S mean" keeps one answer.
 */
val StandardTiers: List<TierDefinition> = listOf(
    TierDefinition("s", "S", 0xFFE5484D),
    TierDefinition("a", "A", 0xFFF76B15),
    TierDefinition("b", "B", 0xFFFFC53D),
    TierDefinition("c", "C", 0xFF30A46C),
    TierDefinition("d", "D", 0xFF0091FF),
    TierDefinition("f", "F", 0xFF8E8E93)
)

/**
 * One list entry as the tier list needs it: an id to key on, a title to draw, and
 * the score the seeding rule reads.
 *
 * A projection rather than the whole `MediaListEntryItem` so this layer does not
 * depend on the data models, and so the exporters can be tested with three-field
 * literals instead of a nested AniList object graph.
 */
data class TierCandidate(
    val mediaId: Int,
    val title: String,
    val coverUrl: String? = null,
    /** AniList's 0-100 average, or null/0 when nobody has scored it. */
    val averageScore: Int? = null
)

/**
 * Which tier each media id sits in.
 *
 * A map from media id to tier id, deliberately not a list of pairs: the screen
 * re-reads one entry per frame while a card is being dragged, and a map answers
 * that without scanning. An entry absent from the map is unfiled, which is a
 * legitimate state - it is what an empty tier list is.
 */
data class TierAssignment(
    val tierIdByMediaId: Map<Int, String> = emptyMap()
) {
    fun tierOf(mediaId: Int): String? = tierIdByMediaId[mediaId]

    /** The ids filed under [tierId], in insertion order so a drag cannot reshuffle a row. */
    fun membersOf(tierId: String): List<Int> =
        tierIdByMediaId.entries.filter { it.value == tierId }.map { it.key }

    val isEmpty: Boolean get() = tierIdByMediaId.isEmpty()
}

/**
 * The AniList score at which a title earns each standard tier.
 *
 * Keyed by tier id rather than being a positional list, because the two are not
 * the same thing once the tiers are customisable. A positional list paired with
 * whatever tiers it was handed silently gave a person's brand-new tier the "S"
 * band's meaning, which is not what adding a tier means. Keyed by id, a tier that
 * is not in this map simply has no band, and a custom tier starts empty.
 */
private val DEFAULT_SCORE_FLOORS = mapOf(
    "s" to 85,
    "a" to 75,
    "b" to 65,
    "c" to 55,
    "d" to 45,
    "f" to 0
)

/**
 * Files candidates into tiers by their AniList score.
 *
 * Only candidates that have actually been scored are filed. AniList answers 0
 * for a title nobody has rated and null for one it has no score for, and seeding
 * those would drop every unrated planning-list entry into the bottom tier - which
 * is not a judgement anybody made, presented in the same typography as the
 * judgements they did make. They are returned unfiled instead, and the UI can say
 * how many were skipped.
 *
 * Tiers with no floor are never assigned to: a tier the person has added but not
 * given a meaning to should stay empty rather than collect strays.
 */
fun seedFromScores(
    candidates: List<TierCandidate>,
    tiers: List<TierDefinition> = StandardTiers,
    floors: Map<String, Int> = DEFAULT_SCORE_FLOORS
): TierAssignment {
    if (tiers.isEmpty()) return TierAssignment()

    // Highest floor first, because the first floor a score clears is the tier it
    // belongs to. Sorting by floor rather than by position in [tiers] means the
    // person's tier *order* is presentation - they can put "Favourite" at the top
    // without it inheriting somebody else's band.
    val bands = tiers
        .mapNotNull { tier -> floors[tier.id]?.let { tier to it } }
        .sortedByDescending { it.second }

    val assignment = LinkedHashMap<Int, String>()
    candidates.forEach { candidate ->
        val score = candidate.averageScore ?: 0
        if (score <= 0) return@forEach
        val match = bands.firstOrNull { score >= it.second } ?: return@forEach
        assignment[candidate.mediaId] = match.first.id
    }
    return TierAssignment(assignment)
}

/**
 * How many candidates [seedFromScores] would leave unfiled.
 *
 * Returned alongside rather than derivable from the assignment, because "I could
 * not place these" and "these are in no tier because the list is empty" look
 * identical from an assignment alone, and the UI needs to say which.
 */
fun unseedableCount(candidates: List<TierCandidate>): Int =
    candidates.count { (it.averageScore ?: 0) <= 0 }

/**
 * Drops every placement that names a tier which no longer exists.
 *
 * This is the invariant the whole feature rests on: a title is either in a tier
 * that exists or in none. Storage can hold the other state - a document saved
 * before a row was removed, or one written by an older build - and the fix
 * belongs here rather than in each reader, because there is more than one reader
 * now (the board, the exports, the library's counts) and two of them disagreeing
 * about whether an entry is placed is not a display bug: it is the export and the
 * screen showing different lists. [TierListViewModel.orphanedCount] still reports
 * them, so the person can see that something was dropped instead of wondering
 * where a poster went.
 */
fun TierAssignment.withoutOrphans(tiers: List<TierDefinition>): TierAssignment {
    if (tierIdByMediaId.isEmpty()) return this
    val live = tiers.map { it.id }.toSet()
    val filtered = tierIdByMediaId.filterValues { it in live }
    // The same instance when nothing was dropped, which is not a micro-optimisation
    // here: this runs on every emission of the open list, and a fresh equal value
    // is a new `StateFlow` value - which recomposes the board's whole arrangement
    // for a list that did not change.
    if (filtered.size == tierIdByMediaId.size) return this
    return TierAssignment(filtered)
}

/** How many placements name a tier that no longer exists. */
fun TierAssignment.orphanedCount(tiers: List<TierDefinition>): Int {
    val live = tiers.map { it.id }.toSet()
    return tierIdByMediaId.count { it.value !in live }
}

/**
 * Files [mediaId] into [tierId] at a chosen position, or un-files it.
 *
 * ## Why this is not [moveTo]'s "put it in the tier"
 *
 * Dropping a poster *onto another poster* has to mean "here, above this one".
 * The old move only knew a tier, so the dropped title landed wherever its media id
 * happened to sit in the map - which for an id filed under that tier a year ago is
 * the top of the row, nowhere near the finger. `beforeMediaId` is the tile the drop
 * landed on top of, and the new entry goes immediately before it.
 *
 * ## Order is the storage order, deliberately
 *
 * There is no separate position field. The assignment is one ordered map, written
 * out in iteration order and read back in it, so rebuilding the map *is* the
 * reorder - and there is only one place for the order to live, which is what keeps
 * the screen and an export of it in the same order. The previous code's ordering
 * was a side effect of insertion; that is still true, it is just now deliberate.
 *
 * An anchor that is not in [tierId] (the finger was over the dragged tile itself,
 * or the tile was moved a frame earlier), or no anchor at all, means "the end of
 * that tier" - not the end of the whole map, which would file it below rows it is
 * not in.
 */
fun TierAssignment.withPlacement(
    mediaId: Int,
    tierId: String?,
    beforeMediaId: Int? = null
): TierAssignment {
    // Dropping a tile onto its own slot is a no-op rather than "move it to the end
    // of the row". The board can name the tile itself as the anchor for a frame
    // while the finger is still over it, and reordering under a stationary finger
    // reads as the board rearranging itself.
    if (beforeMediaId == mediaId) return this

    val rest = tierIdByMediaId.filterKeys { it != mediaId }
    if (tierId == null) {
        if (rest.size == tierIdByMediaId.size) return this
        return TierAssignment(rest)
    }

    val rebuilt = LinkedHashMap<Int, String>(rest.size + 1)
    var placed = false
    rest.forEach { (id, existingTier) ->
        if (!placed && id == beforeMediaId && existingTier == tierId) {
            rebuilt[mediaId] = tierId
            placed = true
        }
        rebuilt[id] = existingTier
    }

    if (!placed) {
        // The anchor was absent, so the entry goes after the last one already in
        // this tier - not at the end of the map. Appending would put it below every
        // *other* tier's entries too, which is invisible on the board (each row
        // filters by tier) and visible the moment anything walks the map in order,
        // which the exports and the "add titles" list both do.
        val entries = rest.entries.toList()
        val lastOfTier = entries.indexOfLast { it.value == tierId }
        val ordered = LinkedHashMap<Int, String>(rest.size + 1)
        entries.forEachIndexed { index, entry ->
            ordered[entry.key] = entry.value
            if (index == lastOfTier) ordered[mediaId] = tierId
        }
        // A tier with nothing in it has no "after the last entry", so this is the
        // one case that lands at the end.
        if (lastOfTier < 0) ordered[mediaId] = tierId
        return TierAssignment(ordered)
    }

    return TierAssignment(rebuilt)
}