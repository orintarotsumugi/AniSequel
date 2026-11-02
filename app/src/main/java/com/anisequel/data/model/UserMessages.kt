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

/**
 * Whether this message is one leg of the conversation between [viewerId] and
 * [counterpartId], in either direction.
 *
 * AniList's `Page.activities` has no recipient filter, so a conversation cannot be
 * asked for as a unit - it is assembled from the two lists the app can fetch:
 * messages the viewer sent, and messages addressed to them. That makes this check
 * the whole of the filtering, and it is why both ids are compared as a *set*: a
 * message I wrote has me as messenger and them as recipient, and their reply has it
 * the other way round, and the conversation is both.
 *
 * A message missing either id does not qualify. A notification that carried a
 * stripped `MessageActivity` is not evidence of who the other party was, and
 * guessing from the displayed name would put two similar-named people in one thread.
 */
fun MessageNode.isWith(counterpartId: Int?, viewerId: Int?): Boolean {
    if (counterpartId == null || viewerId == null) return false
    val messenger = messengerId ?: return false
    val recipient = recipientId ?: return false
    return (messenger == viewerId && recipient == counterpartId) ||
        (messenger == counterpartId && recipient == viewerId)
}

/**
 * This message as the first row of its own comment thread.
 *
 * A message is a `MessageActivity`: its body is the activity's, and the rows under
 * it are the replies AniList gives any activity. The comment sheet draws replies,
 * so a message opened straight into that sheet would show the answers without the
 * thing they answer - which is why the messages tab used to have a sheet of its
 * own for the body. Mapping it to [ActivityReply] is what lets the one sheet draw
 * both: same avatar, same name, same timestamp, same Reply and Quote.
 *
 * [ActivityReply.isLiked] is deliberately null. A message *is* likeable, but its
 * like is `ToggleLikeV2(activityId)` while a reply's is `ToggleLikeV2(replyId)`,
 * and the thread store only speaks the second one. Handing the row a value would
 * enable a heart that likes an activity id through the reply mutation.
 */
fun MessageNode.asThreadHead(): ActivityReply = ActivityReply(
    id = id,
    text = message,
    likeCount = likeCount,
    isLiked = null,
    createdAt = createdAt,
    user = messenger
)

