package com.anisequel.domain.usecase

/**
 * Which profile may be read as a *library*, and what has to be fetched to do it.
 *
 * ## Why the viewer's own list is not offered here
 *
 * The Library left the profile and became a page of the app's main bar, and the
 * profile kept the list only for the person it belongs to. Offering the reader their
 * own list from their own profile would put two doors on one page with two different
 * remembered views behind them - the bar's page reads the viewer's saved type, filter
 * and sort out of DataStore, and a borrowed list must not (see
 * [canBrowseProfileLibrary]'s counterpart in `LibrarySelection`). One of the two would
 * quietly stop being the reader's view of their own things.
 */

/**
 * Whether this profile should carry a tab for the account it belongs to's list.
 *
 * True for a follower's, a followed account's or a scanned stranger's profile, and
 * false for the reader's own - whose list is the bar's page - and for the demo, whose
 * fixture has an anime list but no way to ask for a second account's.
 */
fun canBrowseProfileLibrary(isOwnProfile: Boolean, isDemo: Boolean): Boolean =
    !isOwnProfile && !isDemo

/**
 * Whether the chunked anime list has to come down with the profile.
 *
 * Opening somebody's profile used to download their whole list - up to one request
 * per 500 entries, sequentially - for two tallies on a Stats tab the reader may not
 * even scroll to, and may leave after a second. Only the two pages that *cannot* ask
 * for it themselves are worth that: the viewer's own profile, whose list the main
 * bar's Library page renders with no trigger of its own, and the demo, which reads a
 * local fixture rather than spending the rate limit.
 *
 * Everybody else's list waits for the tab that shows it, which is exactly how the
 * manga half already works, and for the same reason.
 */
fun fetchProfileListEagerly(isOwnProfile: Boolean, isDemo: Boolean): Boolean =
    isOwnProfile || isDemo

/**
 * Why this profile's manga list cannot be asked for, or null when it can.
 *
 * The guard used to `return` silently, which left the Manga filter spinning on a
 * half that was never arriving: no list, no error, no reason. A list nobody can
 * fetch is not an empty list, and the difference is only visible if something says
 * which it is.
 */
fun mangaLibraryProblem(isDemo: Boolean, userId: Int?): String? = when {
    isDemo -> "The demo has no manga list to read."
    // A scanned profile is opened by name, and `getUserMangaList` only takes an id.
    // The id arrives with the overview; until then there is nothing to ask with.
    userId == null -> "AniList hasn't said who this profile is yet, so there's no " +
        "id to ask for their manga with."
    else -> null
}
