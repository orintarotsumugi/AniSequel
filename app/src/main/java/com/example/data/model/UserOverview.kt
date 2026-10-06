package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The payload of the profile query: everything AniList holds about *who* a
 * person is, as opposed to [ViewerProfile], which is the handful of fields the
 * dashboard needs to render a title bar.
 *
 * Split into its own file for the same reason the dashboard's state types were:
 * [ViewerProfile] is what every screen already depends on and is fetched on
 * every load, while this is fetched only when someone opens the profile screen.
 * Merging them would make the common payload carry the favourites tree whether
 * or not anything was going to draw it.
 */
@JsonClass(generateAdapter = true)
data class UserOverviewData(
    @Json(name = "User") val user: UserOverview?
)

@JsonClass(generateAdapter = true)
data class UserOverview(
    val id: Int,
    val name: String,
    /**
     * The bio, as Markdown - the query asks for `about(asHtml: false)`.
     *
     * Rendered as plain text with the syntax stripped rather than parsed.
     * AniList bios are written in Markdown and there is no Markdown renderer in
     * the app; `ParsedMarkdown` exists on the API but returns a GFM AST rather
     * than anything renderable. A stripped-to-text bio that reads cleanly is
     * worth more here than a half-broken rich-text renderer, and the raw text is
     * kept rather than dropped so nothing the person wrote disappears.
     */
    val about: String? = null,
    val siteUrl: String? = null,
    val avatar: UserAvatar? = null,
    val bannerImage: String? = null,
    /** Unix seconds. Absent for accounts created before 2020 - AniList does not backfill it. */
    val createdAt: Int? = null,
    val updatedAt: Int? = null,
    val statistics: UserStatistics? = null,
    val favourites: Favourites? = null,
    /**
     * AniList's deprecated aggregate statistics. See [AniListUserStats] for why
     * this is here at all when `statistics` is the field the API tells you to
     * use.
     */
    val stats: AniListUserStats? = null
)

/**
 * `User.stats` - deprecated by AniList in favour of `User.statistics`, and the
 * only one of the two that carries data.
 *
 * This is the awkward part of the profile screen and it is worth writing down in
 * full, because the schema actively misleads here:
 *
 * `User.statistics` (`UserStatisticTypes`) is the documented, non-deprecated
 * replacement, and it answers every per-status / per-genre / per-format question
 * with `[]`. Checked against AniList for accounts holding 142 and 258 scored
 * anime, and for the site's own account: `statuses: []`, `genres: []`, and zeros
 * everywhere else, while `count`, `meanScore`, `episodesWatched` and
 * `minutesWatched` come back populated. The sub-selections are simply not served.
 *
 * `User.stats` is marked "Replaced with statistics field" and is what actually
 * answers. Every list below is served from it, for one request and about 6 KB.
 *
 * The consequences are handled rather than hidden:
 *
 *  - The two halves that *do* work on `statistics` are read from there, so the
 *    query does not pay twice for the same number.
 *  - [ActivityCalendar] is built from [activityHistory], which is this type's
 *    own per-day history and carries AniList's own intensity [level]. The
 *    alternative - deriving days from list entries - has a gap the reference
 *    implementation does not: bumping progress on a show you are watching is
 *    activity and produces no `completedAt` anywhere.
 *  - The distributions the profile screen shows are marked
 *    `UserOverviewQueryTest` as coming from a deprecated field, so that whoever
 *    finds this after AniList removes `stats` knows exactly what to move.
 */
@JsonClass(generateAdapter = true)
data class AniListUserStats(
    /** Minutes. The unit is not documented anywhere and is verified below. */
    val watchedTime: Int? = null,
    val chaptersRead: Int? = null,
    val activityHistory: List<ActivityHistoryDay>? = null,
    val animeStatusDistribution: List<StatusAmount>? = null,
    val mangaStatusDistribution: List<StatusAmount>? = null,
    val animeScoreDistribution: List<ScoreAmount>? = null,
    val animeListScores: ListScoreStats? = null,
    val mangaListScores: ListScoreStats? = null,
    val favouredFormats: List<FormatAmount>? = null,
    val favouredYears: List<YearAmount>? = null,
    val favouredGenres: List<GenreAmount>? = null,
    val favouredTags: List<TagAmount>? = null
)

/**
 * One day of list activity.
 *
 * [date] is a Unix timestamp at AniList's own day boundary, and [level] is
 * AniList's precomputed intensity from 1 to 7 - seven steps, not five, and not
 * normalised. Both are taken as sent; the calendar divides [level] by seven.
 */
@JsonClass(generateAdapter = true)
data class ActivityHistoryDay(
    val date: Int? = null,
    val amount: Int? = null,
    val level: Int? = null
)

@JsonClass(generateAdapter = true)
data class StatusAmount(
    val status: String? = null,
    val amount: Int? = null
)

@JsonClass(generateAdapter = true)
data class ScoreAmount(
    val score: Int? = null,
    val amount: Int? = null
)

@JsonClass(generateAdapter = true)
data class ListScoreStats(
    val meanScore: Int? = null,
    val standardDeviation: Int? = null
)

@JsonClass(generateAdapter = true)
data class FormatAmount(
    val format: String? = null,
    val amount: Int? = null
)

@JsonClass(generateAdapter = true)
data class YearAmount(
    val year: Int? = null,
    val amount: Int? = null,
    val meanScore: Int? = null
)

@JsonClass(generateAdapter = true)
data class GenreAmount(
    val genre: String? = null,
    val amount: Int? = null,
    val meanScore: Int? = null,
    val timeWatched: Int? = null
)

@JsonClass(generateAdapter = true)
data class TagAmount(
    val tag: MediaTag? = null,
    val amount: Int? = null,
    val meanScore: Int? = null
)

/**
 * `User.favourites`: the five lists a person has pinned by hand.
 *
 * Every branch is separately nullable because AniList omits - or answers null
 * for - any of them a user has never added to, and a person with no favourites
 * at all gets `{}`. A missing branch and an empty one both mean the same thing
 * to the UI, which is "no section to draw".
 */
@JsonClass(generateAdapter = true)
data class Favourites(
    val anime: FavouriteMediaConnection? = null,
    val manga: FavouriteMediaConnection? = null,
    val characters: FavouriteCharacterConnection? = null,
    val staff: FavouriteStaffConnection? = null,
    val studios: StudioConnection? = null
)

/**
 * A page of favourited media.
 *
 * Reuses [MediaNode] rather than declaring a favourite-specific media type. The
 * fields this screen draws - cover, title, format, status, score, link - are all
 * already on it, and a second type would need merging with it the moment
 * anything wanted both.
 */
@JsonClass(generateAdapter = true)
data class FavouriteMediaConnection(
    val nodes: List<MediaNode>? = null
)

@JsonClass(generateAdapter = true)
data class FavouriteCharacterConnection(
    val nodes: List<FavouriteCharacter>? = null
)

@JsonClass(generateAdapter = true)
data class FavouriteStaffConnection(
    val nodes: List<FavouriteStaff>? = null
)

@JsonClass(generateAdapter = true)
data class FavouriteCharacter(
    val id: Int,
    val name: PersonName? = null,
    val image: PersonImage? = null,
    /** How many people have favourited this character, not this person's rank. */
    val favourites: Int? = null,
    val siteUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class FavouriteStaff(
    val id: Int,
    val name: PersonName? = null,
    val image: PersonImage? = null,
    val primaryOccupations: List<String>? = null,
    val favourites: Int? = null,
    val siteUrl: String? = null
) {
    /** The person's day job, as AniList words it: "Story & Art", "Director", "Voice Actor". */
    val occupation: String?
        get() = primaryOccupations?.firstOrNull { it.isNotBlank() }
}

/**
 * One name object, shared by characters and staff.
 *
 * AniList has two near-identical types here - `CharacterName` and `StaffName` -
 * with the same three fields. One model reads both correctly, because Moshi
 * matches on the JSON keys and never sees which type the server meant.
 */
@JsonClass(generateAdapter = true)
data class PersonName(
    val full: String? = null,
    val native: String? = null,
    val alternative: List<String>? = null
) {
    val displayName: String
        get() = full?.takeIf { it.isNotBlank() }
            ?: native?.takeIf { it.isNotBlank() }
            ?: alternative?.firstOrNull { it.isNotBlank() }
            ?: "Unknown"
}

/**
 * One portrait object, shared by characters and staff.
 *
 * As with [PersonName], AniList declares `CharacterImage` and `StaffImage`
 * separately and populates them identically. Only `large` is asked for: the rows
 * are 64dp circles, and `medium` would save a few kilobytes of image that the
 * display would resample straight back up.
 */
@JsonClass(generateAdapter = true)
data class PersonImage(
    val large: String? = null,
    val medium: String? = null
) {
    val bestUrl: String?
        get() = large ?: medium
}

/**
 * One entry in someone's activity feed.
 *
 * AniList returns the feed as `[ActivityUnion]`, a union of `ListActivity`,
 * `TextActivity`, `MessageActivity` and others. Moshi cannot discriminate a
 * union, and this type models only the `ListActivity` member.
 *
 * That is not a shortcut with a hidden cost, because the query filters with
 * `type_in: [ANIME_LIST, MANGA_LIST]` and the parser then drops anything
 * without a [media] or a [status] - which is exactly the shape of every other
 * union member. So a forum post or a site message that slipped through the
 * server-side filter is skipped rather than rendered as a blank card. See
 * `UserActivityJsonTest`.
 *
 * [status] is **lowercase** here - `"completed"`, not `"COMPLETED"` - unlike
 * every other status in the API. Compare case-insensitively; the JSON test pins
 * it.
 */
@JsonClass(generateAdapter = true)
data class ListActivity(
    val id: Int,
    val status: String? = null,
    val progress: Int? = null,
    val createdAt: Int? = null,
    val likeCount: Int? = null,
    val replyCount: Int? = null,
    val media: MediaNode? = null
) {
    /** True for the members of the union this app does not model. */
    val isUnrecognised: Boolean
        get() = media == null && status == null

    val displayStatus: String
        get() = when (status?.lowercase()) {
            "current" -> "Watched"
            "planning" -> "Plans to watch"
            "completed" -> "Completed"
            "dropped" -> "Dropped"
            "paused" -> "Paused"
            "repeating" -> "Rewatching"
            else -> status?.replaceFirstChar { it.uppercase() } ?: "Updated"
        }

    /** Anime and manga use different words for the same status. */
    fun displayStatusFor(isManga: Boolean): String =
        if (isManga && status.equals("current", ignoreCase = true)) "Read" else displayStatus
}

/** Payload of the activity-feed query. */
@JsonClass(generateAdapter = true)
data class ActivityFeedData(
    @Json(name = "Page") val page: ActivityFeedPage?
)

@JsonClass(generateAdapter = true)
data class ActivityFeedPage(
    val activities: List<ListActivity>? = null
)

/** Payload of the followers query. */
@JsonClass(generateAdapter = true)
data class FollowersData(
    @Json(name = "Page") val page: FollowersPage?
)

@JsonClass(generateAdapter = true)
data class FollowersPage(
    val followers: List<FollowUser>? = null
)

/** Payload of the following query. */
@JsonClass(generateAdapter = true)
data class FollowingData(
    @Json(name = "Page") val page: FollowingPage?
)

@JsonClass(generateAdapter = true)
data class FollowingPage(
    val following: List<FollowUser>? = null
)

/**
 * Somebody in a followers or following list.
 *
 * The whole point of the Social tab is that tapping one of these shows *their*
 * AniList data, so the id and name are both kept: the id so the profile query
 * can be keyed on it, the name because it is what the grid prints and what the
 * next screen's cache key uses.
 */
@JsonClass(generateAdapter = true)
data class FollowUser(
    val id: Int,
    val name: String,
    val avatar: UserAvatar? = null
)