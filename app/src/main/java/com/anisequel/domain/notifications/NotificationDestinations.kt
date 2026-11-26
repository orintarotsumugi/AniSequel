package com.anisequel.domain.notifications

import java.net.URLEncoder

/**
 * In-app routes a tapped system notification can open.
 *
 * These strings are the contract between a `PendingIntent` and the NavHost. They
 * are built here, in the domain, because the worker has to write the payload
 * long before there is a composition to ask, and the same route has to survive
 * the process being killed between the post and the tap. The formats mirror
 * `AppRoutes.notificationMedia/Activity/Thread` and `ProfileRoutes.byId`; a
 * change to one of those patterns has to change this object too, which is why
 * the tests pin the exact strings.
 *
 * The encoding is `URLEncoder` rather than `android.net.Uri.encode` so this
 * stays a pure JVM object: the delivery planner and its tests must run without
 * an Android runtime. A display name is free text, and a `/`, `?` or `#` in it
 * used to build a route no destination matched - which navigates nowhere,
 * silently, instead of failing.
 */
object NotificationDestinations {
    /** The inbox itself, the fallback for an event with no addressable target. */
    const val INBOX = "notifications"

    /** The Messages segment of the viewer's own Activity tab. */
    const val MESSAGES = "profile?tab=activity&segment=messages"

    fun media(mediaId: Int, type: String?): String =
        "notifications/media/$mediaId?type=${encode(type.orEmpty())}"

    fun activity(activityId: Int): String =
        "notifications/activity/$activityId"

    fun thread(threadId: Int, commentId: Int?): String =
        "notifications/thread/$threadId?comment=${commentId ?: -1}"

    fun user(userId: Int, name: String?): String =
        "profile_id/$userId?name=${encode(name.orEmpty())}"

    fun forTarget(target: NotificationTarget): String = when (target) {
        is NotificationTarget.Media -> media(target.mediaId, target.type)
        is NotificationTarget.User -> user(target.userId, target.name)
        is NotificationTarget.Message -> MESSAGES
        is NotificationTarget.Thread -> thread(target.threadId, target.commentId)
        is NotificationTarget.Activity -> activity(target.activityId)
        NotificationTarget.None -> INBOX
    }

    /**
     * Whether [route] is a route this app itself would build.
     *
     * `MainActivity` is exported (it is the launcher), so another app can start
     * it with any extras it likes. Navigation only ever accepts the shapes
     * below; an unrecognised extra is ignored rather than pushed onto the back
     * stack, which is both a smaller surface and the reason a malformed payload
     * fails quietly instead of showing a blank destination.
     */
    fun isSafe(route: String?): Boolean {
        if (route.isNullOrBlank() || route.length > MAX_ROUTE_LENGTH) return false
        return when {
            route == INBOX -> true
            route == MESSAGES -> true
            MEDIA_PATTERN.matches(route) -> true
            ACTIVITY_PATTERN.matches(route) -> true
            THREAD_PATTERN.matches(route) -> true
            USER_PATTERN.matches(route) -> true
            else -> false
        }
    }

    private fun encode(value: String): String =
        URLEncoder.encode(value, Charsets.UTF_8.name()).replace("+", "%20")

    /** Guards against an unbounded extra being copied into NavController state. */
    private const val MAX_ROUTE_LENGTH = 512

    private val MEDIA_PATTERN =
        Regex("""notifications/media/\d+\?type=[A-Za-z0-9%._~-]*""")
    private val ACTIVITY_PATTERN = Regex("""notifications/activity/\d+""")
    private val THREAD_PATTERN = Regex("""notifications/thread/\d+\?comment=-?\d+""")
    private val USER_PATTERN = Regex("""profile_id/\d+\?name=[^/?#]*""")
}
