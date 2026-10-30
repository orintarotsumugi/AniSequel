package com.anisequel.data.model

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
     * Kept as raw text, and parsed by `AniListMarkdownParser` at render time.
     *
     * This comment used to say the opposite - that the bio was "rendered as plain
     * text with the syntax stripped", because there was no Markdown renderer in
     * the app. That stopped being true when `ui/components/markdown/` was added,
     * and `AboutCard` has rendered these nodes rich ever since, so the sentence was
     * describing a renderer that no longer exists while the one that does went
     * unmentioned. (`stripMarkdown` was the leftover from that approach. Nothing
     * ever rendered it — only its own test suite did — and it has since been
     * deleted along with those tests.)
     * The API's `ParsedMarkdown` is still not the path: it answers with a GFM AST
     * rather than anything renderable.
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
 * `User.stats` - deprecated by AniList in favour of `User.statistics`, and still
 * the only source for two things.
 *
 * ## What is still read from here, and why
 *
 *  - [activityHistory] is this type's per-day history and carries AniList's own
 *    intensity [level]. No replacement exists anywhere in the schema, and
 *    deriving days from list entries has a gap: bumping progress on a show you
 *    are watching is activity and produces no `completedAt` anywhere.
 *  - The status, score and list-score distributions still answer here and have no
 *    equivalent that the Stats tab can use without a second interpretation step.
 *
 * ## What was removed, and it is not cosmetic
 *
 * This type used to carry `favouredFormats`, `favouredYears`, `favouredGenres`
 * and `favouredTags`. On 2026-10-22 any one of those four makes AniList answer
 * **HTTP 500** for the entire profile document (`data.User = null`, four generic
 * "Internal Server Error" entries), verified against the live API. Since the
 * profile screen was the only reader, every Profile and Library open became the
 * "Ani-chan is having a nap" page while the dashboard kept working.
 *
 * They are not modelled here any more, so a future edit cannot ask for them by
 * accident. The same distributions are read from
 * `User.statistics.anime` - see [UserFormatStatistic] and siblings - which is
 * populated and is the field AniList actually maintains.
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
    val mangaListScores: ListScoreStats? = null
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
 * union, so one flat type reads both members this feed shows: a **list update**
 * ([media] plus [status], "watched 3 episodes") and a **text post** ([text],
 * "what are you watching this season?"). The query asks for `type_in:
 * [ANIME_LIST, MANGA_LIST, TEXT]`; `MessageActivity` - site messages between
 * two users - stays out, because a private message rendered on a public profile
 * tab is not a feed.
 *
 * The parser drops anything [isUnrecognised], which is every remaining union
 * member: a forum post arriving through some future document, or a member that
 * slipped past the server-side filter, would otherwise render as a blank card.
 * See `UserActivityJsonTest`.
 *
 * [status] is **lowercase** here - `"completed"`, not `"COMPLETED"` - unlike
 * every other status in the API. Compare case-insensitively; the JSON test pins
 * it.
 */
@JsonClass(generateAdapter = true)
data class ProfileActivity(
    val id: Int,
    val status: String? = null,
    val progress: String? = null,
    /**
     * The body of a text post, AniList Markdown.
     *
     * Null for list updates. Parsed at render time rather than here, the same
     * rule the bio and replies follow: a post that scrolled past unread should
     * not have paid for a Markdown parse.
     */
    val text: String? = null,
    /**
     * The union member's name, when the response carried it.
     *
     * `MessageActivity` - a site message between two users - has a `text` field
     * exactly like `TextActivity`, so the body alone cannot tell them apart.
     * `type_in` already excludes `MESSAGE` server-side; this is the second line
     * of defence for a response that ignored the filter, and it is why a
     * message cannot be drawn as a text post.
     */
    @Json(name = "__typename") val typename: String? = null,
    val createdAt: Int? = null,
    val likeCount: Int? = null,
    val replyCount: Int? = null,
    /**
     * Whether the signed-in viewer has liked this activity.
     *
     * Nullable on purpose, and null means **unknown** rather than "not liked":
     * AniList only answers it when a request carries a token, so the demo feed
     * has no value here. The like control is rendered only when this is non-null,
     * so an unknown can never be shown to a user as a definite "not liked" - a
     * heart that is empty when it should be filled is a lie about their account.
     */
    val isLiked: Boolean? = null,
    /** This activity's own page on AniList (`https://anilist.co/activity/<id>`). */
    val siteUrl: String? = null,
    /**
     * The account that posted this activity, when the response carried it.
     *
     * Only the following feed asks for it - every row there is a different
     * person, so the author has to travel with the activity. The profile feed
     * leaves it null on purpose: every card already belongs to the person whose
     * profile is open, and the screen passes their name and avatar down in one
     * place rather than repeating them in twenty-five payloads. A card whose
     * author is unknown still draws; it just starts at the poster.
     */
    val user: FollowUser? = null,
    val media: MediaNode? = null
) {
    /** True for the members of the union this feed does not render. */
    val isUnrecognised: Boolean
        get() = media == null && status == null && !isTextPost

    /**
     * A text post: a body and no media.
     *
     * Not just `text != null`: a list update's status and text can both arrive
     * (AniList lets "completed" statuses carry a comment), and those are still
     * list cards - the media is what decides which card draws. Nor is a
     * `MessageActivity` one, even though it also has a body: a private message
     * rendered on a public profile tab is not a feed.
     */
    val isTextPost: Boolean
        get() = text != null &&
            media == null &&
            !typename.equals("MessageActivity", ignoreCase = true)

    /**
     * The activity's status in words.
     *
     * Past tense on purpose, and *not* the [com.anisequel.domain.list.ListStatuses]
     * labels: a list row says a title *is* "Watching", the feed says the reader
     * "Watched" it, and those are different sentences about different things. The
     * app used to carry a second, manga-blind `displayStatus` property beside this
     * one for the same string; the card only ever called this one, so the other was
     * deleted rather than kept in step by a test.
     */
    fun displayStatusFor(isManga: Boolean): String = when (status?.lowercase()) {
        "current" -> if (isManga) "Read" else "Watched"
        "watched", "watched episode" -> if (isManga) "Read" else "Watched"
        "read", "read chapter" -> "Read"
        "planning" -> if (isManga) "Plans to read" else "Plans to watch"
        "completed" -> "Completed"
        "dropped" -> "Dropped"
        "paused" -> "On-Hold"
        "repeating" -> if (isManga) "Rereading" else "Rewatching"
        else -> status?.replaceFirstChar { it.uppercase() } ?: "Updated"
    }
}

/** Payload of the activity-feed query. */
@JsonClass(generateAdapter = true)
data class ActivityFeedData(
    @Json(name = "Page") val page: ActivityFeedPage?
)

@JsonClass(generateAdapter = true)
data class ActivityFeedPage(
    val pageInfo: PageInfo? = null,
    val activities: List<ProfileActivity>? = null
)

/**
 * AniList's own answer to "is there another page".
 *
 * The feed used to infer the end from the size of the page it got back: under a
 * full page meant the last page. That is wrong here because the repository drops
 * union members and list updates whose media is gone before the caller sees them,
 * so a twenty-five-row response can arrive as twenty-four rows - and one deleted
 * title could end a feed that had hundreds of entries left.
 *
 * AniList's pagination guide says `total` and `lastPage` may be inaccurate and
 * names `hasNextPage` as the signal to follow, so that is what is asked for.
 */
@JsonClass(generateAdapter = true)
data class PageInfo(
    val hasNextPage: Boolean? = null
)

/**
 * One page of somebody's list activity, with the pagination answer attached.
 *
 * The repository resolves [hasMore] at the wire boundary rather than leaving it
 * to be guessed from [activities].size downstream: the filter that makes a page
 * shorter than it really is also lives at that boundary, so this is the last
 * place that can still tell.
 */
data class UserActivityPage(
    val activities: List<ProfileActivity>,
    val hasMore: Boolean
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