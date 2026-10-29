package com.anisequel.domain.usecase

/**
 * Who a profile route is asking for, once its arguments have been read.
 *
 * ## The zero this replaces
 *
 * The by-id route used to read its argument as `getInt("userId") ?: 0`. A missing
 * argument is not AniList user 0: it is a request for *somebody*, and the fallback
 * turned a routing bug into a live query for whatever account happens to own that
 * id, painted as a profile with a real bio and a name the caller never asked for.
 * The only honest answer to "which person?" when the route carries neither a name
 * nor an id is [ProfileIdentity.problem], which the profile screen renders as a
 * failure the reader can leave.
 *
 * An id of `0` (or less) is treated the same way rather than as a real account, for
 * the same reason: AniList ids start at 1, so a non-positive id can only come from a
 * default, an unfilled placeholder or a serialisation that gave up.
 */
data class ProfileIdentity(
    /** The account to fetch by, when the route carried one. */
    val userId: Int?,
    /** The name to label and - failing an id - fetch by. Null when the route had none. */
    val username: String?,
    /** Why this route cannot be fetched at all, or null when it can. */
    val problem: String?
) {
    val isResolvable: Boolean get() = problem == null
}

fun profileIdentityFromRoute(rawUserId: Int?, rawUsername: String?): ProfileIdentity {
    val userId = rawUserId?.takeIf { it > 0 }
    val username = rawUsername?.takeIf { it.isNotBlank() }
    return when {
        userId != null -> ProfileIdentity(userId = userId, username = username, problem = null)
        username != null -> ProfileIdentity(userId = null, username = username, problem = null)
        else -> ProfileIdentity(
            userId = null,
            username = null,
            problem = "This profile was opened without a name or an id, so there is " +
                "nobody to ask AniList about."
        )
    }
}

/**
 * The key the three profile ViewModels - and the screen's saved tab - are built on.
 *
 * Both halves are load-bearing. The id alone is not enough because a scanned profile
 * arrives with `userId = null` and every one of them would then share one key; the
 * name alone is not enough because AniList lets two accounts keep the same
 * *display* name, and a follower grid is exactly where you find out. A key that
 * collides hands the second profile the first one's bio, favourites and tab, which
 * nothing on screen reports.
 */
fun profileViewModelKey(username: String?, userId: Int?, isDemo: Boolean): String {
    val who = when {
        isDemo -> "demo"
        else -> username?.takeIf { it.isNotBlank() } ?: "unnamed"
    }
    return "profile_${who}_${userId ?: "byname"}"
}
