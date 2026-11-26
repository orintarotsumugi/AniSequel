package com.anisequel.domain.notifications

import java.util.Locale

/** The tray colour/behaviour family a draft belongs to. */
enum class NotificationAlertTone {
    SOCIAL,
    MEDIA,
    STATUS
}

/**
 * One Android notification, ready to post.
 *
 * The copy is built from the event, never from a template that only says
 * "you have a notification". [key] is the stable tray identity: a folded like
 * group keeps `activity_like:<id>` as members arrive, so the tray entry updates
 * in place instead of growing a second copy. [notificationIds] carries the whole
 * group so a tap can mark every member read.
 */
data class NotificationAlertDraft(
    val key: String,
    val title: String,
    val text: String,
    val expandedText: String? = null,
    val tone: NotificationAlertTone = NotificationAlertTone.SOCIAL,
    val timestampSeconds: Int? = null,
    val route: String = NotificationDestinations.INBOX,
    val notificationIds: List<Int> = emptyList()
)

/**
 * Turns one folded inbox group into a tray alert.
 *
 * Returns null for an event this build cannot describe honestly - an unknown
 * GraphQL union member - because a generic alert is worse than no alert. Every
 * other kind uses the event's own names, titles and preview, and falls back to
 * the inbox route when the payload has no addressable target (a deleted media
 * row is the real case: the notification survives the title it pointed at).
 */
fun buildNotificationAlertDraft(group: NotificationGroup): NotificationAlertDraft? {
    val newest = group.representative
    if (newest.kind == NotificationKind.UNKNOWN) return null
    val ids = group.ids
    if (ids.isEmpty()) return null

    val copy = when (newest) {
        is AiringNotification -> airingCopy(newest)
        is ActivityNotification -> activityCopy(group, newest)
        is ThreadNotification -> threadCopy(group, newest)
        is MessageNotification -> messageCopy(group, newest)
        is FollowNotification -> followCopy(group, newest)
        is MediaUpdateNotification -> mediaUpdateCopy(newest)
        is SubmissionNotification -> submissionCopy(newest)
        is UnknownNotification -> return null
    }

    return NotificationAlertDraft(
        key = group.key,
        title = copy.title.trim().limit(MAX_TITLE_LENGTH),
        text = copy.text.trim().limit(MAX_TEXT_LENGTH),
        expandedText = copy.expanded?.trim()?.limit(MAX_EXPANDED_LENGTH),
        tone = newest.alertTone(),
        timestampSeconds = newest.createdAt,
        route = NotificationDestinations.forTarget(newest.target),
        notificationIds = ids
    )
}

private data class AlertCopy(val title: String, val text: String, val expanded: String? = null)

private fun InboxNotification.alertTone(): NotificationAlertTone = when (this) {
    is AiringNotification,
    is MediaUpdateNotification -> NotificationAlertTone.MEDIA
    is SubmissionNotification -> NotificationAlertTone.STATUS
    else -> NotificationAlertTone.SOCIAL
}

private fun airingCopy(notification: AiringNotification): AlertCopy {
    val title = notification.media?.title?.takeIf(String::isNotBlank) ?: "A title you follow"
    val episode = notification.episode?.takeIf { it > 0 }
    val line = if (episode != null) "Episode $episode is out now." else "A new episode is out now."
    val context = notification.contexts.firstOrNull()
        ?.takeIf(String::isNotBlank)
        ?.takeUnless { episode != null && it.contains("Episode $episode", ignoreCase = true) }
    return AlertCopy(title = title, text = line, expanded = context)
}

private fun activityCopy(group: NotificationGroup, notification: ActivityNotification): AlertCopy {
    val subject = group.subject()
    val title = when (notification.kind) {
        NotificationKind.ACTIVITY_LIKE -> "$subject liked your activity"
        NotificationKind.ACTIVITY_REPLY_LIKE -> "$subject liked your reply"
        NotificationKind.ACTIVITY_REPLY -> "$subject replied to your activity"
        NotificationKind.ACTIVITY_REPLY_SUBSCRIBED -> "$subject replied to a conversation you follow"
        NotificationKind.ACTIVITY_MENTION -> "$subject mentioned you"
        else -> "$subject interacted with your activity"
    }
    val body = preview(notification.context)
        ?: preview(notification.activityText)
        ?: notification.media?.title?.takeIf(String::isNotBlank)
        ?: "Open AniSequel to see the activity."
    return AlertCopy(title = title, text = body)
}

private fun threadCopy(group: NotificationGroup, notification: ThreadNotification): AlertCopy {
    val subject = group.subject()
    val title = when (notification.kind) {
        NotificationKind.THREAD_LIKE -> "$subject liked your thread"
        NotificationKind.THREAD_COMMENT_LIKE -> "$subject liked your comment"
        NotificationKind.THREAD_COMMENT_REPLY -> "$subject replied to your comment"
        NotificationKind.THREAD_COMMENT_SUBSCRIBED -> "$subject replied to a thread you follow"
        NotificationKind.THREAD_COMMENT_MENTION -> "$subject mentioned you"
        else -> "$subject replied in a thread"
    }
    val body = preview(notification.comment?.text)
        ?: preview(notification.context)
        ?: notification.thread?.title?.takeIf(String::isNotBlank)
        ?: "Open the thread in AniSequel."
    return AlertCopy(title = title, text = body, expanded = notification.thread?.title)
}

private fun messageCopy(group: NotificationGroup, notification: MessageNotification): AlertCopy {
    val body = preview(notification.body)
        ?: preview(notification.context)
        ?: "Open the conversation in AniSequel."
    return AlertCopy(title = "${group.subject()} sent you a message", text = body)
}

private fun followCopy(group: NotificationGroup, notification: FollowNotification): AlertCopy =
    AlertCopy(
        title = "${group.subject()} followed you",
        text = preview(notification.context) ?: "Open their profile in AniSequel."
    )

private fun mediaUpdateCopy(notification: MediaUpdateNotification): AlertCopy {
    val title = notification.media?.title?.takeIf(String::isNotBlank) ?: "Media update"
    val line = when (notification.kind) {
        NotificationKind.RELATED_MEDIA_ADDITION -> "A related title was added."
        NotificationKind.MEDIA_DATA_CHANGE -> "The title details changed."
        NotificationKind.MEDIA_MERGE -> "Two titles were merged."
        NotificationKind.MEDIA_DELETION -> notification.deletedTitles.firstOrNull()
            ?.let { "$it was deleted." }
            ?: "A title was deleted."
        else -> "A title you follow changed."
    }
    return AlertCopy(
        title = title,
        text = line,
        expanded = preview(notification.reason) ?: preview(notification.context)
    )
}

private fun submissionCopy(notification: SubmissionNotification): AlertCopy {
    val title = notification.submittedTitle?.takeIf(String::isNotBlank)
        ?: notification.media?.title?.takeIf(String::isNotBlank)
        ?: "Your submission"
    val status = notification.status
        ?.replace('_', ' ')
        ?.lowercase(Locale.ROOT)
        ?.replaceFirstChar { it.uppercase(Locale.ROOT) }
        ?.takeIf(String::isNotBlank)
    return AlertCopy(
        title = "Submission update",
        text = if (status != null) "$title is $status." else "$title was updated.",
        expanded = preview(notification.notes)
    )
}

/**
 * The people in a folded group, as a subject a sentence can start with.
 *
 * Distinct actors, because the same person liking two replies of one thread is
 * still one person - and "Ana and 1 other" for two likes from Ana reads as a
 * bug. Falls back to "Someone" only when the payload genuinely carried no user.
 */
private fun NotificationGroup.subject(): String {
    val names = actors.mapNotNull { it.name.takeIf(String::isNotBlank) }
    return when {
        names.isEmpty() -> "Someone"
        names.size == 1 -> names.first()
        names.size == 2 -> "${names[0]} and ${names[1]}"
        else -> "${names.first()} and ${names.size - 1} others"
    }
}

/** Collapses AniList markup whitespace and trims a preview to a tray-sized line. */
internal fun preview(value: String?, limit: Int = 140): String? {
    val text = value
        ?.replace("\r\n", "\n")
        ?.replace(Regex("\\s+"), " ")
        ?.trim()
        ?.takeIf(String::isNotBlank)
        ?: return null
    return text.limit(limit)
}

private fun String.limit(max: Int): String =
    if (length <= max) this else take(max - 1).trimEnd() + "\u2026"

private const val MAX_TITLE_LENGTH = 72
private const val MAX_TEXT_LENGTH = 160
private const val MAX_EXPANDED_LENGTH = 600
