package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * One site message, as both the sent list and a message notification carry it.
 *
 * AniList calls the author the **messenger** and the other party the
 * **recipient** no matter who wrote first, so neither field alone says whether
 * the viewer sent or received this row - [isSentBy] does that by comparing the
 * messenger against the viewer's id. [counterpartFor] then names the person the
 * row should show: the recipient on something the viewer wrote, the messenger on
 * something they were sent.
 *
 * This is not [ProfileActivity], even though both arrive through
 * `Page.activities`: a message has no media and no status, and its body lives in
 * [message] rather than `text`. Sharing one type would mean two nullable fields
 * whose combination decides which card draws, which is exactly the kind of
 * ambiguity that put a private message on a public profile feed once already.
 */
@JsonClass(generateAdapter = true)
data class MessageNode(
    val id: Int,
    /**
     * The body, AniList Markdown.
     *
     * Null only for a message that was deleted server-side while the notification
     * survived; the repository drops those rather than drawing an empty card.
     * Parsed at render time for the same reason the bio and text posts are.
     */
    val message: String? = null,
    val isPrivate: Boolean? = null,
    val createdAt: Int? = null,
    val likeCount: Int? = null,
    val replyCount: Int? = null,
    val isLiked: Boolean? = null,
    val siteUrl: String? = null,
    val recipientId: Int? = null,
    val messengerId: Int? = null,
    val recipient: FollowUser? = null,
    val messenger: FollowUser? = null
) {
    /**
     * True when [viewerId] wrote this message.
     *
     * A null viewer - signed out, or a response that somehow lost the field -
     * answers false, which renders the row as received. That is the safe wrong
     * answer: showing "you wrote this" on somebody else's message is a worse
     * lie than showing the sender's name on your own.
     */
    fun isSentBy(viewerId: Int?): Boolean = viewerId != null && messengerId == viewerId

    /** The other party: recipient on a sent row, messenger on a received one. */
    fun counterpartFor(viewerId: Int?): FollowUser? =
        if (isSentBy(viewerId)) recipient else messenger

    /** A body the card can actually draw. */
    val hasBody: Boolean
        get() = !message.isNullOrBlank()
}

/** Payload of the sent-messages query. */
@JsonClass(generateAdapter = true)
data class SentMessagesData(
    @Json(name = "Page") val page: SentMessagesPage?
)

@JsonClass(generateAdapter = true)
data class SentMessagesPage(
    val pageInfo: PageInfo? = null,
    val activities: List<MessageNode>? = null
)

/** Payload of the received-messages query. */
@JsonClass(generateAdapter = true)
data class ReceivedMessagesData(
    @Json(name = "Page") val page: ReceivedMessagesPage?
)

@JsonClass(generateAdapter = true)
data class ReceivedMessagesPage(
    val pageInfo: PageInfo? = null,
    val notifications: List<MessageNotificationNode>? = null
)

/**
 * The notification wrapper around one received message.
 *
 * Received messages have no activity listing of their own - `Page.activities`
 * has no recipient filter - so the inbox is read through the notifications
 * AniList raises for them. The notification carries both its own [id] (which is
 * what paging and de-duplication key on) and the whole [message], which is what
 * the row actually draws.
 *
 * [typename] is read for the same defensive reason [ProfileActivity.typename]
 * is: `type_in: [ACTIVITY_MESSAGE]` already filters server-side, and a future
 * notification type arriving anyway must not be parsed into a message card.
 */
@JsonClass(generateAdapter = true)
data class MessageNotificationNode(
    val id: Int,
    val activityId: Int? = null,
    val createdAt: Int? = null,
    @Json(name = "__typename") val typename: String? = null,
    val message: MessageNode? = null
) {
    /** True for a notification type this tab does not draw. */
    val isUnrecognised: Boolean
        get() = !typename.isNullOrBlank() &&
            !typename.equals("ActivityMessageNotification", ignoreCase = true)
}

/**
 * One page of messages from one source, with the pagination answer attached.
 *
 * Resolved at the wire boundary for the same reason [UserActivityPage] is: the
 * repository drops bodiless messages before the caller sees them, so the row
 * count can no longer say whether more pages exist.
 */
data class UserMessagesPage(
    val messages: List<MessageNode>,
    val hasMore: Boolean
)
