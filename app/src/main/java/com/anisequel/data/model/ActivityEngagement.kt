package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The reply-and-like side of the activity feed.
 *
 * Kept out of [UserOverview.kt] because it is a different concern with a
 * different lifetime: [ProfileActivity] is parsed once per feed page and thrown
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
 * `Activity(id:)` returns `ActivityUnion`, so the document fragments the two
 * members the feed can open a thread for, `ListActivity` and `TextActivity`. A
 * member the fragments do not match comes back as an object with no selected
 * fields, and an id that no longer exists as null; both read as an empty thread
 * through `replies.orEmpty()`. The feed only contains those two, but a stale id
 * could still resolve to something else.
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
 * Payload of the likes-on-an-activity query.
 *
 * `likes` is a plain `[User]` - AniList does not paginate it - so the whole list
 * of likers arrives in one response and no cursor state is modelled. Each liker
 * is a [FollowUser] rather than a new type: the selection is the identical
 * `{ id name avatar { ... } }` a follower row asks for, and the sheet renders the
 * same circle from the same fields, so a second type would only be the same data
 * under a second name.
 *
 * An absent or null `likes` reads as no likers rather than an error, because the
 * activity a stale id resolves to need not be a `ListActivity` at all.
 */
@JsonClass(generateAdapter = true)
data class ActivityLikesData(
    @Json(name = "Activity") val activity: ActivityLikesNode? = null
)

@JsonClass(generateAdapter = true)
data class ActivityLikesNode(
    val id: Int? = null,
    val likeCount: Int? = null,
    val likes: List<FollowUser>? = null
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
 * Payload of a direct message sent through `SaveActivityMessage`.
 *
 * AniList answers with the created message as an activity-shaped object; the only
 * field this app needs back is its id, because the toast says "Sent" and the sent
 * list refetches rather than being patched from the response.
 */
@JsonClass(generateAdapter = true)
data class SendActivityMessageData(
    @Json(name = "SaveActivityMessage") val message: SentMessageId? = null
)

@JsonClass(generateAdapter = true)
data class SentMessageId(val id: Int? = null)

/**
 * Payload of the reply deletion.
 *
 * `DeleteActivityReply` answers with the shared [Deleted] object - the same
 * shape [DeleteMediaListEntryData] reads - and the reply that was removed is
 * *not* echoed back, so the sheet removes it from its own list by the id it
 * sent.
 */
@JsonClass(generateAdapter = true)
data class DeleteActivityReplyData(
    @Json(name = "DeleteActivityReply") val deleted: Deleted? = null
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
