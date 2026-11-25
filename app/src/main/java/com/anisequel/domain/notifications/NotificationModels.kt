package com.anisequel.domain.notifications

/**
 * One inbox notification, as this app understands it.
 *
 * The AniList notification union has twenty-odd members and only some of them
 * carry enough information to draw a useful row. Mapping the wire payload into
 * these types is the repository job; nothing in the UI branches on a GraphQL
 * typename. A member added to the union after this build becomes
 * [UnknownNotification] rather than a blank card, so the inbox never quietly
 * loses something the server says happened.
 */
sealed interface InboxNotification {
    val id: Int
    val kind: NotificationKind

    /** Unix seconds, or null when AniList did not send one. */
    val createdAt: Int?

    /** Stable key for the only notifications this app folds together. */
    val groupKey: String? get() = null

    val actor: NotificationActor? get() = null

    val target: NotificationTarget get() = NotificationTarget.None
}

/** Every notification type this build knows how to name. */
enum class NotificationKind {
    AIRING,
    ACTIVITY_LIKE,
    ACTIVITY_REPLY,
    ACTIVITY_REPLY_LIKE,
    ACTIVITY_REPLY_SUBSCRIBED,
    ACTIVITY_MENTION,
    ACTIVITY_MESSAGE,
    THREAD_COMMENT_REPLY,
    THREAD_COMMENT_SUBSCRIBED,
    THREAD_COMMENT_MENTION,
    THREAD_LIKE,
    THREAD_COMMENT_LIKE,
    FOLLOWING,
    RELATED_MEDIA_ADDITION,
    MEDIA_DATA_CHANGE,
    MEDIA_MERGE,
    MEDIA_DELETION,
    MEDIA_SUBMISSION_UPDATE,
    UNKNOWN
}

/** The chips across the top of the inbox. */
enum class NotificationFilter(val label: String) {
    ALL("All"),
    AIRING("Airing"),
    ACTIVITY("Activity"),
    MESSAGES("Messages"),
    REPLIES("Replies"),
    MENTIONS("Mentions"),
    LIKES("Likes"),
    FOLLOWS("Follows"),
    MEDIA_UPDATES("Media updates"),
    SUBMISSIONS("Submissions");

    fun matches(kind: NotificationKind): Boolean = when (this) {
        ALL -> true
        AIRING -> kind == NotificationKind.AIRING
        ACTIVITY -> kind in ACTIVITY_KINDS
        MESSAGES -> kind == NotificationKind.ACTIVITY_MESSAGE
        REPLIES -> kind in REPLY_KINDS
        MENTIONS -> kind in MENTION_KINDS
        LIKES -> kind in LIKE_KINDS
        FOLLOWS -> kind == NotificationKind.FOLLOWING
        MEDIA_UPDATES -> kind in MEDIA_UPDATE_KINDS
        SUBMISSIONS -> kind == NotificationKind.MEDIA_SUBMISSION_UPDATE
    }

    private companion object {
        val LIKE_KINDS = setOf(
            NotificationKind.ACTIVITY_LIKE,
            NotificationKind.ACTIVITY_REPLY_LIKE,
            NotificationKind.THREAD_LIKE,
            NotificationKind.THREAD_COMMENT_LIKE
        )
        val REPLY_KINDS = setOf(
            NotificationKind.ACTIVITY_REPLY,
            NotificationKind.ACTIVITY_REPLY_SUBSCRIBED,
            NotificationKind.THREAD_COMMENT_REPLY,
            NotificationKind.THREAD_COMMENT_SUBSCRIBED
        )
        val MENTION_KINDS = setOf(
            NotificationKind.ACTIVITY_MENTION,
            NotificationKind.THREAD_COMMENT_MENTION
        )
        val ACTIVITY_KINDS = LIKE_KINDS + REPLY_KINDS + MENTION_KINDS + setOf(
            NotificationKind.FOLLOWING,
            NotificationKind.ACTIVITY_REPLY_LIKE,
            NotificationKind.THREAD_COMMENT_LIKE
        )
        val MEDIA_UPDATE_KINDS = setOf(
            NotificationKind.RELATED_MEDIA_ADDITION,
            NotificationKind.MEDIA_DATA_CHANGE,
            NotificationKind.MEDIA_MERGE,
            NotificationKind.MEDIA_DELETION
        )
    }
}

/** Who did it. */
data class NotificationActor(
    val id: Int,
    val name: String,
    val avatarUrl: String?
)

/** The title a notification is about, reduced to what a row draws. */
data class NotificationMedia(
    val id: Int,
    val title: String?,
    val coverUrl: String?,
    /** ANIME or MANGA, needed to open the right detail namespace. */
    val type: String?
)

data class NotificationThread(
    val id: Int,
    val title: String?,
    /** The API's canonical page, including the site's own comment anchor. */
    val url: String? = null
)

data class NotificationComment(
    val id: Int,
    val text: String?,
    /** Canonical comment link; preferred over guessing an anchor from ids. */
    val url: String? = null
)

/** Where tapping a row should go. */
sealed interface NotificationTarget {
    data class Media(val mediaId: Int, val type: String?) : NotificationTarget
    data class User(val userId: Int, val name: String?) : NotificationTarget
    data class Message(val activityId: Int?, val peerId: Int?, val peerName: String?) : NotificationTarget
    data class Thread(
        val threadId: Int,
        val commentId: Int?,
        /** The API supplied link; the old synthetic anchor did not match AniList. */
        val siteUrl: String? = null
    ) : NotificationTarget
    data class Activity(val activityId: Int, val mediaId: Int?) : NotificationTarget
    data object None : NotificationTarget
}

data class AiringNotification(
    override val id: Int,
    override val createdAt: Int?,
    val episode: Int?,
    val media: NotificationMedia?,
    val contexts: List<String> = emptyList()
) : InboxNotification {
    override val kind = NotificationKind.AIRING
    override val target = media?.let { NotificationTarget.Media(it.id, it.type) }
        ?: NotificationTarget.None
}

data class ActivityNotification(
    override val id: Int,
    override val kind: NotificationKind,
    override val createdAt: Int?,
    override val actor: NotificationActor?,
    val activityId: Int?,
    val context: String?,
    val activityText: String?,
    val activityUrl: String?,
    val media: NotificationMedia?
) : InboxNotification {
    override val groupKey: String?
        get() = when (kind) {
            NotificationKind.ACTIVITY_LIKE -> activityId?.let { "activity_like:$it" }
            // Reply likes are deliberately absent. The payload carries the parent
            // activity id but no reply id, so folding by that field would merge
            // likes on two different replies under one post into a single row.
            else -> null
        }

    override val target: NotificationTarget
        get() = activityId?.let { NotificationTarget.Activity(it, media?.id) }
            ?: media?.let { NotificationTarget.Media(it.id, it.type) }
            ?: NotificationTarget.None
}

data class ThreadNotification(
    override val id: Int,
    override val kind: NotificationKind,
    override val createdAt: Int?,
    override val actor: NotificationActor?,
    val thread: NotificationThread?,
    val comment: NotificationComment?,
    val context: String?
) : InboxNotification {
    override val groupKey: String?
        get() = when (kind) {
            NotificationKind.THREAD_LIKE -> thread?.let { "thread_like:${it.id}" }
            // A comment-like without a comment id cannot be told apart from a
            // different one under the same thread, so it stays individual. The
            // alternative folded separate conversations into one row.
            NotificationKind.THREAD_COMMENT_LIKE -> thread?.let { thread ->
                comment?.id?.let { commentId -> "thread_comment_like:${thread.id}:$commentId" }
            }
            else -> null
        }

    override val target: NotificationTarget
        get() = thread?.let { thread ->
            NotificationTarget.Thread(
                threadId = thread.id,
                commentId = comment?.id,
                siteUrl = comment?.url ?: thread.url
            )
        } ?: NotificationTarget.None
}

data class MessageNotification(
    override val id: Int,
    override val createdAt: Int?,
    override val actor: NotificationActor?,
    val activityId: Int?,
    val peerId: Int?,
    val peerName: String?,
    val body: String?,
    val context: String?
) : InboxNotification {
    override val kind = NotificationKind.ACTIVITY_MESSAGE
    override val target = NotificationTarget.Message(activityId, peerId, peerName)
}

data class FollowNotification(
    override val id: Int,
    override val createdAt: Int?,
    override val actor: NotificationActor?,
    val context: String?
) : InboxNotification {
    override val kind = NotificationKind.FOLLOWING
    override val target = actor?.let { NotificationTarget.User(it.id, it.name) }
        ?: NotificationTarget.None
}

data class MediaUpdateNotification(
    override val id: Int,
    override val kind: NotificationKind,
    override val createdAt: Int?,
    val media: NotificationMedia?,
    val reason: String?,
    val deletedTitles: List<String> = emptyList(),
    val context: String? = null
) : InboxNotification {
    override val target = media?.let { NotificationTarget.Media(it.id, it.type) }
        ?: NotificationTarget.None
}

data class SubmissionNotification(
    override val id: Int,
    override val createdAt: Int?,
    val media: NotificationMedia?,
    val submittedTitle: String?,
    val status: String?,
    val notes: String?,
    val contexts: List<String> = emptyList()
) : InboxNotification {
    override val kind = NotificationKind.MEDIA_SUBMISSION_UPDATE
    override val target = media?.let { NotificationTarget.Media(it.id, it.type) }
        ?: NotificationTarget.None
}

data class UnknownNotification(
    override val id: Int,
    override val createdAt: Int?,
    val typename: String?,
    val type: String?,
    val context: String?
) : InboxNotification {
    override val kind = NotificationKind.UNKNOWN
}