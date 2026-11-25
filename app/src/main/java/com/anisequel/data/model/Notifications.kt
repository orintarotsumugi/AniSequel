package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * The notification union, flattened.
 *
 * Every member is modelled as nullable optional fields on one node rather than a
 * Moshi polymorphic hierarchy. GraphQL already sends one flat object per row;
 * Moshi polymorphism would need a discriminator that the API only guarantees
 * when `__typename` is asked for, and a missing or new typename would fail the
 * whole page instead of one row. The repository maps by typename and turns any
 * member it does not know into a safe fallback.
 */
@JsonClass(generateAdapter = true)
data class NotificationNode(
    /**
     * Nullable on purpose. The GraphQL field is a union, so a member this build
     * does not know can only be selected as `__typename` - the server sends no id
     * for it. A non-null Kotlin field made that missing id fail the whole page.
     */
    val id: Int? = null,
    @Json(name = "__typename") val typename: String? = null,
    val type: String? = null,
    val createdAt: Int? = null,
    val context: String? = null,
    val contexts: List<String>? = null,
    val episode: Int? = null,
    val activityId: Int? = null,
    val user: FollowUser? = null,
    val media: MediaNode? = null,
    val activity: ProfileActivity? = null,
    val message: MessageNode? = null,
    val thread: NotificationThreadNode? = null,
    val comment: NotificationCommentNode? = null,
    val reason: String? = null,
    val deletedMediaTitles: List<String>? = null,
    val deletedMediaTitle: String? = null,
    val status: String? = null,
    val notes: String? = null,
    val submittedTitle: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationThreadNode(
    val id: Int,
    val title: String? = null,
    val siteUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationCommentNode(
    val id: Int,
    val comment: String? = null,
    val siteUrl: String? = null
)

@JsonClass(generateAdapter = true)
data class NotificationsViewer(
    val unreadNotificationCount: Int? = null
)

@JsonClass(generateAdapter = true)
data class NotificationsPage(
    val pageInfo: PageInfo? = null,
    val notifications: List<NotificationNode>? = null
)

@JsonClass(generateAdapter = true)
data class NotificationsData(
    @Json(name = "Viewer") val viewer: NotificationsViewer? = null,
    @Json(name = "Page") val page: NotificationsPage? = null
)

/** One wire page plus the server count that seeds the first local baseline. */
data class NotificationPage(
    val notifications: List<com.anisequel.domain.notifications.InboxNotification>,
    val hasMore: Boolean,
    val serverUnreadCount: Int?
)