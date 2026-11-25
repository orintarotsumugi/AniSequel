package com.anisequel.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

/**
 * Comments requested per thread page.
 *
 * The repository passes this as `perPage`, and the thread screen divides the
 * thread's reply count by it to open at the newest page that can hold a
 * notification's comment. Keeping one constant is what makes those two answers
 * agree: a screen that guessed 25 while the request asked for 20 would open one
 * page off.
 */
const val THREAD_COMMENTS_PER_PAGE = 20

/**
 * The deepest page AniList will serve for any `Page` connection.
 *
 * The API rejects a request whose `page * perPage` exceeds 5000 entries with
 * "Page depth exceeds maximum allowed", even when the thread really has more
 * comments. The thread screen clamps its last-page guess here so a very old
 * thread still opens instead of showing a comments error.
 */
const val THREAD_COMMENT_PAGE_DEPTH_LIMIT = 5000 / THREAD_COMMENTS_PER_PAGE

/**
 * The read-only forum thread a notification can point at.
 *
 * AniList's `Thread` root field is the only way to open a thread from an id: the
 * inbox carries no body, title or author, and guessing those from a comment
 * notification is exactly the kind of invented context the old external link
 * avoided by leaving the app. The models stay flat because Moshi decodes the
 * capitalised root fields through [ThreadDetailData].
 */
@JsonClass(generateAdapter = true)
data class ThreadDetailData(
    @Json(name = "Thread") val thread: ThreadNode? = null
)

@JsonClass(generateAdapter = true)
data class ThreadNode(
    val id: Int? = null,
    val title: String? = null,
    val body: String? = null,
    val createdAt: Int? = null,
    val replyCount: Int? = null,
    val viewCount: Int? = null,
    val likeCount: Int? = null,
    val isLiked: Boolean? = null,
    val user: FollowUser? = null,
    val categories: List<ThreadCategory>? = null
)

@JsonClass(generateAdapter = true)
data class ThreadCategory(
    val id: Int? = null,
    val name: String? = null
)

@JsonClass(generateAdapter = true)
data class ThreadCommentsData(
    @Json(name = "Page") val page: ThreadCommentsPage? = null
)

@JsonClass(generateAdapter = true)
data class ThreadCommentsPage(
    val pageInfo: PageInfo? = null,
    val threadComments: List<ThreadComment>? = null
)

@JsonClass(generateAdapter = true)
data class ThreadComment(
    val id: Int,
    val threadId: Int? = null,
    /**
     * AniList's `ThreadComment` calls the body `comment`, not `text`.
     *
     * The field name is easy to guess wrong from the activity model, where a
     * reply really is `text`; asking for the wrong one is a GraphQL validation
     * error, not an empty string, so the whole thread would fail to open.
     */
    @Json(name = "comment") val comment: String? = null,
    val createdAt: Int? = null,
    val likeCount: Int? = null,
    val isLiked: Boolean? = null,
    val user: FollowUser? = null
)

/** One fetched page, with AniList's own answer about what follows. */
data class ThreadCommentPage(
    val comments: List<ThreadComment>,
    val hasMore: Boolean
)
