package com.anisequel.domain.tier

/**
 * Which half of an AniList account a tier list ranks.
 *
 * ## Why this is a property of the list, not of the screen
 *
 * AniList numbers anime and manga independently, so media id 1 is *both* "Cowboy
 * Bebop" and "Monster". A board that mixed the two pools would key two different
 * titles under one number: one of them would silently inherit the other's tier,
 * and an export would draw a poster with the wrong name on it. The type belongs
 * to the document for the same reason the tiers do - it is what the ranking is
 * *of*, and a board must never be able to change that by being opened from the
 * wrong shelf.
 *
 * The sections are separate by default: the library opens on [ANIME], a manga
 * board is only reachable from the manga section, and a pool is always narrowed
 * by its own type before anything is drawn. See `TierFilter.mediaType` and
 * `List<TierCandidate>.ofMediaType`.
 *
 * ## Why the labels live here
 *
 * "Watching" and "Reading" are the same AniList status (`CURRENT`), and so are
 * "Rewatching" and "Rereading" (`REPEATING`). The chip wording is a property of
 * what is being tracked, not of the status token, and keeping the two lists
 * beside the type is what stops a manga filter from asking whether you are
 * watching a book.
 */
enum class TierMediaType(
    /** The section label: "Anime" or "Manga". */
    val label: String,
    /**
     * How copy names the half of the account this ranks: "your library" or
     * "your manga list".
     *
     * Used where a sentence would otherwise claim the anime list is the only
     * list there is - "Your library has not loaded" is true and unhelpful on a
     * manga board whose manga list is still arriving.
     */
    val libraryLabel: String
) {
    ANIME("Anime", "library"),
    MANGA("Manga", "manga list");

    /**
     * The "on your list" chips, in the wording this media type is tracked in.
     *
     * The values are AniList's own uppercase tokens. Only the labels differ
     * between the two types; the status vocabulary is shared.
     */
    val listStatusOptions: List<TierStatusOption>
        get() = when (this) {
            ANIME -> ANIME_LIST_STATUSES
            MANGA -> MANGA_LIST_STATUSES
        }

    /**
     * The release-status chips: airing for anime, publishing for manga.
     *
     * `RELEASING` is one token on AniList and two words in English, which is why
     * this is per type rather than a single shared list with a swapped heading.
     */
    val airingStatusOptions: List<TierStatusOption>
        get() = when (this) {
            ANIME -> ANIME_AIRING_STATUSES
            MANGA -> MANGA_AIRING_STATUSES
        }

    /** The heading over [airingStatusOptions]: a manga is published, not aired. */
    val releaseSectionTitle: String
        get() = if (this == MANGA) "Publishing" else "Airing"
}

private val ANIME_LIST_STATUSES: List<TierStatusOption> = listOf(
    TierStatusOption("CURRENT", "Watching"),
    TierStatusOption("REPEATING", "Rewatching"),
    TierStatusOption("COMPLETED", "Completed"),
    TierStatusOption("PAUSED", "Paused"),
    TierStatusOption("DROPPED", "Dropped"),
    TierStatusOption("PLANNING", "Planning")
)

private val MANGA_LIST_STATUSES: List<TierStatusOption> = listOf(
    TierStatusOption("CURRENT", "Reading"),
    TierStatusOption("REPEATING", "Rereading"),
    TierStatusOption("COMPLETED", "Completed"),
    TierStatusOption("PAUSED", "Paused"),
    TierStatusOption("DROPPED", "Dropped"),
    TierStatusOption("PLANNING", "Planning")
)

private val ANIME_AIRING_STATUSES: List<TierStatusOption> = listOf(
    TierStatusOption("RELEASING", "Releasing"),
    TierStatusOption("FINISHED", "Finished"),
    TierStatusOption("NOT_YET_RELEASED", "Not yet aired"),
    TierStatusOption("HIATUS", "Hiatus"),
    TierStatusOption("CANCELLED", "Cancelled")
)

private val MANGA_AIRING_STATUSES: List<TierStatusOption> = listOf(
    TierStatusOption("RELEASING", "Publishing"),
    TierStatusOption("FINISHED", "Finished"),
    TierStatusOption("NOT_YET_RELEASED", "Not yet published"),
    TierStatusOption("HIATUS", "Hiatus"),
    TierStatusOption("CANCELLED", "Cancelled")
)

/**
 * The candidates that belong to [mediaType].
 *
 * The one place the two pools are separated. AniList's ids collide across
 * namespaces - anime 1 and manga 1 are different titles - so this is not a
 * cosmetic filter: a pool that was not narrowed here would let a board key both
 * titles under one media id, and the assignment map would keep whichever
 * placement was written last.
 */
fun List<TierCandidate>.ofMediaType(mediaType: TierMediaType): List<TierCandidate> =
    filter { it.mediaType == mediaType }
