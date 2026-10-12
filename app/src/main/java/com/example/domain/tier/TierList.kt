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