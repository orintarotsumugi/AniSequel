package com.anisequel.ui.navigation

import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.ThreadComment
import com.anisequel.data.model.UserAvatar
import com.anisequel.domain.notifications.NotificationActor
import com.anisequel.domain.notifications.NotificationTarget

/**
 * Carries the inbox row's own comment into the thread destination.
 *
 * Navigation arguments are scalars, and a comment body is neither short nor safe
 * to put in a route. The inbox already selected `comment { comment createdAt
 * user }`, so copying it into this one-slot handoff avoids a second walk through
 * pages that can stop at AniList's 5,000-entry depth limit long before the
 * notification's target. This is not a cache: it is cleared whenever another
 * thread row is opened, and its absence only means the destination falls back to
 * the newest page it can fetch.
 */
class NotificationThreadSeedStore {
    private var seed: ThreadComment? = null

    fun record(target: NotificationTarget.Thread) {
        val commentId = target.commentId
        seed = if (commentId == null || commentId <= 0 || target.commentText.isNullOrBlank()) {
            null
        } else {
            ThreadComment(
                id = commentId,
                threadId = target.threadId,
                comment = target.commentText,
                createdAt = target.commentCreatedAt,
                user = target.commentAuthor?.toFollowUser()
            )
        }
    }

    fun find(threadId: Int, commentId: Int?): ThreadComment? =
        seed?.takeIf {
            it.threadId == threadId && (commentId == null || it.id == commentId)
        }
}

private fun NotificationActor.toFollowUser() = FollowUser(
    id = id,
    name = name,
    avatar = avatarUrl?.let { UserAvatar(medium = it, large = it) }
)
