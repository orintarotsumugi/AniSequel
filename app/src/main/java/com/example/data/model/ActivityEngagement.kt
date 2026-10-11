package com.example.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The reply-and-like side of the activity feed.
 *
 * Kept out of [UserOverview.kt] because it is a different concern with a
 * different lifetime: [ListActivity] is parsed once per feed page and thrown
 * away, while these types only exist while a comment sheet is open or a like is
 * in flight. They also have their own query documents, so they change when the
 * engagement features change and for no other reason.
 */

/**
 * One comment on an activity.
 *
 * `text` is AniList Markdown, the same dialect the bio uses, and is parsed by
 * `AniListMarkdownParser` at render time rather than here - a reply that is
 * never expanded should not pay to be parsed.
 *
 * [user] is [FollowUser] rather than a new author type: it is exactly the same
 * `{ id name avatar { ... } }` selection a followers row asks for, and giving it
 * a second name would be two types that have to be kept in step for no gain.
 */
@JsonClass(generateAdapter = true)
data class ActivityReply(
    val id: Int,
    val text: String? = null,
    val likeCount: Int? = null,
    val isLiked: Boolean? = null,
    val createdAt: Int? = null,
    val user: FollowUser? = null
)

/**
 * Payload of the activity-with-replies query.
 *
 * `Activity(id:)` returns `ActivityUnion`, so the document fragments the
 * `ListActivity` member. A member the fragment does not match comes back as an
 * object with no selected fields, and an id that no longer exists as null; both
 * read as an empty thread through `replies.orEmpty()`. The feed can only contain
 * list activities, but a stale id could still resolve to something else.
 */
@JsonClass(generateAdapter = true)
data class ActivityDetailData(
    @Json(name = "Activity") val activity: ActivityDetail? = null
)

@JsonClass(generateAdapter = true)
data class ActivityDetail(
    val id: Int? = null,
    val isLiked: Boolean? = null,
    val likeCount: Int? = null,
    val replyCount: Int? = null,
    val replies: List<ActivityReply>? = null
)

/**
 * Payload of the like toggle.
 *
 * `ToggleLikeV2` returns `LikeableUnion`; the document fragments the two members
 * this app can toggle (an activity and an activity reply), and both expose the
 * same three fields, so one flat type decodes either. The union's `__typename`
 * is not asked for - nothing branches on it.
 */
@JsonClass(generateAdapter = true)
data class ToggleLikeData(
    @Json(name = "ToggleLikeV2") val likeable: ToggleLikeResult? = null
)

@JsonClass(generateAdapter = true)
data class ToggleLikeResult(
    val id: Int? = null,
    val isLiked: Boolean? = null,
    val likeCount: Int? = null
)

/** Payload of the reply mutation. */
@JsonClass(generateAdapter = true)
data class SaveActivityReplyData(
    @Json(name = "SaveActivityReply") val reply: ActivityReply? = null
)

/**
 * The answer to a like toggle, with the union wrapper and the nullability
 * stripped off.
 *
 * [likeCount] is non-null and defaults to 0 because a count is a number to print,
 * not a question to ask - the repository resolves AniList's null to zero once,
 * here, rather than at each of the two places that draw it.
 */
data class ActivityLikeState(
    val isLiked: Boolean,
    val likeCount: Int
)
