package com.anisequel.ui.screens.notifications

import com.anisequel.domain.notifications.ActivityNotification
import com.anisequel.domain.notifications.AiringNotification
import com.anisequel.domain.notifications.FollowNotification
import com.anisequel.domain.notifications.InboxNotification
import com.anisequel.domain.notifications.MediaUpdateNotification
import com.anisequel.domain.notifications.MessageNotification
import com.anisequel.domain.notifications.NotificationGroup
import com.anisequel.domain.notifications.NotificationKind
import com.anisequel.domain.notifications.SubmissionNotification
import com.anisequel.domain.notifications.ThreadNotification
import com.anisequel.domain.notifications.UnknownNotification
import com.anisequel.ui.screens.profile.formatRelativeSeconds
import java.time.Instant

/** The small label above a headline: what kind of event this is. */
internal fun NotificationKind.categoryLabel(): String = when (this) {
    NotificationKind.AIRING -> "Airing"
    NotificationKind.ACTIVITY_LIKE,
    NotificationKind.ACTIVITY_REPLY_LIKE,
    NotificationKind.THREAD_LIKE,
    NotificationKind.THREAD_COMMENT_LIKE -> "Like"
    NotificationKind.ACTIVITY_REPLY,
    NotificationKind.ACTIVITY_REPLY_SUBSCRIBED,
    NotificationKind.THREAD_COMMENT_REPLY,
    NotificationKind.THREAD_COMMENT_SUBSCRIBED -> "Reply"
    NotificationKind.ACTIVITY_MENTION,
    NotificationKind.THREAD_COMMENT_MENTION -> "Mention"
    NotificationKind.ACTIVITY_MESSAGE -> "Message"
    NotificationKind.FOLLOWING -> "New follower"
    NotificationKind.RELATED_MEDIA_ADDITION,
    NotificationKind.MEDIA_DATA_CHANGE,
    NotificationKind.MEDIA_MERGE,
    NotificationKind.MEDIA_DELETION -> "Media update"
    NotificationKind.MEDIA_SUBMISSION_UPDATE -> "Submission"
    NotificationKind.UNKNOWN -> "Update"
}

/** True when the row is about a title rather than a person or a conversation. */
internal fun InboxNotification.isMediaFocused(): Boolean = when (this) {
    is AiringNotification,
    is MediaUpdateNotification,
    is SubmissionNotification -> true
    is ActivityNotification -> media != null && activityId == null
    else -> false
}

/** The line that names what happened, with the actor when there is one. */
internal fun NotificationGroup.headline(): String {
    val newest = representative
    val who = newest.actor?.name?.takeIf(String::isNotBlank)
        ?: actors.firstOrNull()?.name?.takeIf(String::isNotBlank)
    val extra = (actorCount - 1).coerceAtLeast(0)
    val subject = when {
        who == null -> "Someone"
        extra == 0 -> who
        else -> "$who and $extra ${if (extra == 1) "other" else "others"}"
    }
    return when (newest) {
        is AiringNotification -> {
            val title = newest.media?.title?.takeIf(String::isNotBlank) ?: "A title you follow"
            val episode = newest.episode?.takeIf { it > 0 }
            if (episode != null) "Episode $episode of $title is out" else "$title has a new episode"
        }

        is ActivityNotification -> when (newest.kind) {
            NotificationKind.ACTIVITY_LIKE -> "$subject liked your activity"
            NotificationKind.ACTIVITY_REPLY_LIKE -> "$subject liked your reply"
            NotificationKind.ACTIVITY_REPLY -> "$subject replied to your activity"
            NotificationKind.ACTIVITY_REPLY_SUBSCRIBED -> "$subject replied to a conversation you follow"
            NotificationKind.ACTIVITY_MENTION -> "$subject mentioned you"
            else -> "$subject interacted with your activity"
        }

        is ThreadNotification -> when (newest.kind) {
            NotificationKind.THREAD_LIKE -> "$subject liked your thread"
            NotificationKind.THREAD_COMMENT_LIKE -> "$subject liked your comment"
            NotificationKind.THREAD_COMMENT_REPLY -> "$subject replied to your thread"
            NotificationKind.THREAD_COMMENT_SUBSCRIBED -> "$subject replied to a thread you follow"
            NotificationKind.THREAD_COMMENT_MENTION -> "$subject mentioned you in a thread"
            else -> "$subject replied in a thread"
        }

        is MessageNotification -> "$subject sent you a message"

        is FollowNotification -> "$subject followed you"

        is MediaUpdateNotification -> when (newest.kind) {
            NotificationKind.RELATED_MEDIA_ADDITION -> "A related title was added"
            NotificationKind.MEDIA_DATA_CHANGE -> "A title you follow was updated"
            NotificationKind.MEDIA_MERGE -> "Two titles were merged"
            NotificationKind.MEDIA_DELETION -> "A title was deleted"
            else -> "A title changed"
        }

        is SubmissionNotification -> {
            val status = newest.status?.replace('_', ' ')?.lowercase()?.replaceFirstChar { it.uppercase() }
            if (status.isNullOrBlank()) "Your submission was updated" else "Your submission is $status"
        }

        is UnknownNotification -> "Something new happened"
    }
}

/** The supporting sentence: context, thread title, reason or a message preview. */
internal fun NotificationGroup.contextLine(): String? {
    val newest = representative
    return when (newest) {
        is AiringNotification -> newest.contexts.firstOrNull()?.takeIf(String::isNotBlank)
        is ActivityNotification -> (newest.context ?: newest.activityText)?.takeIf(String::isNotBlank)
        is ThreadNotification -> (newest.thread?.title ?: newest.context)?.takeIf(String::isNotBlank)
        is MessageNotification -> (newest.body ?: newest.context)?.takeIf(String::isNotBlank)
        is FollowNotification -> newest.context?.takeIf(String::isNotBlank)
        is MediaUpdateNotification -> (newest.reason ?: newest.context ?: newest.deletedTitles.joinToString())
            .takeIf(String::isNotBlank)
        is SubmissionNotification -> (newest.notes ?: newest.submittedTitle ?: newest.contexts.firstOrNull())
            ?.takeIf(String::isNotBlank)
        is UnknownNotification -> (newest.context ?: newest.typename ?: newest.type)?.takeIf(String::isNotBlank)
    }
}

/** What the media strip under a social row says, when there is a title. */
internal fun NotificationGroup.mediaTitle(): String? = when (val newest = representative) {
    is ActivityNotification -> newest.media?.title ?: newest.media?.type
    is AiringNotification -> null
    else -> null
}

/** The footer line under a media row. */
internal fun NotificationGroup.mediaDetail(): String? {
    val newest = representative
    return when (newest) {
        is AiringNotification -> {
            val episode = newest.episode?.takeIf { it > 0 }
            if (episode != null) "Episode $episode" else "New episode"
        }
        is MediaUpdateNotification -> when (newest.kind) {
            NotificationKind.RELATED_MEDIA_ADDITION -> "Related title added"
            NotificationKind.MEDIA_DATA_CHANGE -> "Details changed"
            NotificationKind.MEDIA_MERGE -> "Titles merged"
            NotificationKind.MEDIA_DELETION -> newest.deletedTitles.firstOrNull() ?: "Title deleted"
            else -> "Media update"
        }
        is SubmissionNotification -> newest.submittedTitle ?: newest.media?.title
        is ActivityNotification -> newest.media?.title
        else -> null
    }
}

/** The timestamp the row prints, or an empty string when AniList sent none. */
internal fun InboxNotification.relativeTime(now: Instant = Instant.now()): String =
    createdAt?.let { formatRelativeSeconds(it, now) }.orEmpty()
