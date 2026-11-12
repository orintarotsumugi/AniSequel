package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Which half of AniList a catalogue search is allowed to answer from.
 *
 * A search that spans both types cannot say "the anime I meant" and "the manga I
 * meant" at once, and the two namespaces number their ids independently, so the
 * same title can exist twice with the same number. The Search page asks for one
 * type at a time, and Settings can switch each one off; [apiValue] is the exact
 * `MediaType` enum AniList's GraphQL document expects.
 *
 * Pure Kotlin with no storage parsing: the two switches are separate booleans in
 * `MainTabPreferences`, and this enum is only the question the page asks.
 */
enum class MediaSearchType(val apiValue: String, val displayName: String) {
    ANIME("ANIME", "Anime"),
    MANGA("MANGA", "Manga");

    companion object {
        val DEFAULT = ANIME
    }
}

/**
 * AniList's answer to a catalogue search.
 *
 * The same `Page` envelope the app's cover and preview queries use, wrapped
 * around full [MediaNode]s rather than a reduced shape - the quick-add control
 * reads the viewer's own `mediaListEntry` off each result, and a summary type
 * without it would draw "Add to Planning" on a title already on the list.
 *
 * Nullable throughout, as every response model here is: AniList answers a
 * refused or empty query with `data: null`, and a non-null field would turn a
 * "no results" into a parse failure.
 */
@JsonClass(generateAdapter = true)
data class MediaSearchData(
    @Json(name = "Page") val page: MediaSearchPage? = null
)

@JsonClass(generateAdapter = true)
data class MediaSearchPage(
    val media: List<MediaNode>? = null
)

/**
 * The two list statuses that mean "this is already somewhere of mine".
 *
 * AniList's `CURRENT` is the status the app calls Currently Watching; the
 * comparison is case-insensitive because a GraphQL enum arrives as a string and
 * a re-cased response must not turn the badge back into a button that would
 * overwrite the entry.
 */
fun MediaNode.isOnWatchingList(): Boolean =
    mediaListEntry?.status?.equals("CURRENT", ignoreCase = true) == true

fun MediaNode.isOnPlanningList(): Boolean =
    mediaListEntry?.status?.equals("PLANNING", ignoreCase = true) == true
