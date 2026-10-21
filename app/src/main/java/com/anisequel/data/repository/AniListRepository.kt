package com.anisequel.data.repository

import com.anisequel.data.model.ActivityLikeState
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.ListActivity
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaEntryEdit
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.UserOverview
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

interface AniListRepository {
    /**
     * Bumped every time a write changes the viewer's lists.
     *
     * A write made on one screen is invisible to a screen that already loaded
     * its data: the repository drops its caches, but `UserActivityViewModel` has
     * its pages in its own state and the Activity tab is *not* refetched when it
     * is re-entered (that is the pagination guard). So planning a title from the
     * dashboard left the Activity feed claiming nothing had happened until the
     * profile was closed and reopened.
     *
     * A counter rather than an event: there is no replay, no queue, and a
     * collector that starts late reads the current value instead of missing the
     * write. [AniListRepositoryImpl] overrides it; the demo and test doubles keep
     * this never-changing flow, so nothing can depend on a signal they do not
     * send.
     */
    val listMutationVersion: StateFlow<Long> get() = IdleListMutations

    suspend fun getViewer(): Result<ViewerProfile>
    suspend fun getUserByName(userName: String): Result<ViewerProfile>

    /**
     * Asks AniList "are you there?" with the smallest possible query and reports
     * what came back.
     *
     * A default, not an abstract member: the demo repository and the test
     * doubles have no network to probe, and forcing them to implement this
     * would give every one of them a method that lies. They report [UNKNOWN],
     * which is the truth for them - "nothing was checked" - and the UI already
     * treats that as a non-verdict.
     *
     * [AniListRepositoryImpl] measures the round trip so the UI can distinguish
     * "reachable" from "reachable but painfully slow", which is the difference
     * between "wait" and "something is wrong on their side".
     */
    suspend fun checkApiReachability(): ApiStatusSnapshot = ApiStatusSnapshot(ApiReachability.UNKNOWN)
    /**
     * The user's full anime list.
     *
     * [forceRefresh] skips the short-lived response cache and re-asks AniList.
     * It exists because a cache that every caller can bypass is not a cache, it
     * is a stale-data generator: pull-to-refresh went through the cached copy
     * and silently did nothing, which is the exact opposite of what the gesture
     * promises. Default values live here and not on the overrides, which is
     * where Kotlin requires them for an interface method.
     */
    suspend fun getUserAnimeList(
        userId: Int,
        forceRefresh: Boolean = false
    ): Result<MediaListCollection>

    /**
     * Cover art for several media, in **one** request.
     *
     * Batched because AniList is currently degraded to 30 requests a minute and a
     * bio with a dozen AniList links would otherwise spend a dozen of them
     * before the reader had scrolled. Keys already cached cost nothing, and this
     * returns the known ones without a round trip when the batch is entirely
     * satisfied.
     *
     * Keyed by [MediaCoverKey] rather than by a bare id, because an id is only
     * unique *within* a media type and `anilist.co/anime/1` and
     * `anilist.co/manga/1` are different shows. Keying on the id alone makes the
     * two cards share one poster, which looks correct and is not.
     *
     * The title comes back beside the URL in the same response. A media AniList
     * does not serve, or one with no cover, is simply absent from the map - the
     * caller draws a placeholder rather than treating it as an error.
     */
    suspend fun getMediaCovers(keys: List<MediaCoverKey>): Result<Map<MediaCoverKey, MediaCover>> =
        unsupported("getMediaCovers")

    /** As [getUserAnimeList], for a profile looked up by name. */
    suspend fun getUserAnimeListByUsername(
        userName: String,
        forceRefresh: Boolean = false
    ): Result<MediaListCollection>

    /**
     * The user's manga list, for the Library page.
     *
     * Fetched only when somebody actually filters the Library to manga, unlike
     * the anime list which a profile opens with because the Stats tab needs it.
     */
    suspend fun getUserMangaList(
        userId: Int,
        forceRefresh: Boolean = false
    ): Result<MediaListCollection> = unsupported("getUserMangaList")

    /**
     * Description, banner and studio for one entry, fetched on demand because
     * the list query omits them (they are only rendered for the entry the user
     * opens, and asking for them up front makes AniList fail on large lists).
     */
    suspend fun getMediaDetail(mediaId: Int, mediaType: String? = null): Result<MediaNode>

    /**
     * One studio, with one page of everything it worked on.
     *
     * Reached by tapping a studio name anywhere it is drawn - the title page's
     * credits, its hero chip, or another studio page - so it is deliberately not
     * viewer-scoped: a studio is public data, and the page works signed out, in
     * demo mode and on somebody else's profile.
     *
     * Returns the studio node itself rather than a wrapped page type, because the
     * page of titles *is* a field of that node ([StudioNode.media]); a second type
     * holding the same thing would be one more place for `hasNextPage` to be
     * computed differently. "Is there another page" is answered by whether a full
     * page came back, which is how the activity feed already decides it.
     */
    suspend fun getStudio(studioId: Int, page: Int): Result<StudioNode> = unsupported("getStudio")

    /**
     * One character, for the in-app profile sheet.
     *
     * The cast row used to hand its tap to the browser. That is the one thing the rest
     * of the title page does not do - a studio credit opens a studio page - so a
     * character credit now opens a character page, and a staff credit a staff page.
     *
     * Defaulted like every other read here so the demo and fake implementations keep
     * compiling; a profile is a read, so it has no session requirement and the demo
     * answers it rather than refusing.
     */
    suspend fun getCharacterProfile(characterId: Int): Result<CharacterProfile> =
        unsupported("getCharacterProfile")

    /** One staff member. Same shape and same reason as [getCharacterProfile]. */
    suspend fun getStaffProfile(staffId: Int): Result<StaffProfile> =
        unsupported("getStaffProfile")

    suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry>
    suspend fun addToWatching(mediaId: Int): Result<SimpleMediaListEntry> = unsupported("addToWatching")

    /**
     * The viewer's own entry for one media, as the editor tab opens on it.
     *
     * A missing entry is an *empty* [SimpleMediaListEntry] rather than a failure:
     * "not on my list yet" is the state the editor is most likely to be opened in
     * for an arriving show, and turning it into an error would draw a failure over
     * a form that is perfectly able to create the entry.
     *
     * [userId] is the viewer's own id and is required by the editor; the demo and
     * test doubles that do not implement this answer with [unsupported].
     */
    suspend fun getMediaListEntry(
        mediaId: Int,
        userId: Int?
    ): Result<SimpleMediaListEntry> = unsupported("getMediaListEntry")

    /** Creates or replaces the viewer's entry with the fields [edit] carries. */
    suspend fun saveMediaListEntry(edit: MediaEntryEdit): Result<SimpleMediaListEntry> =
        unsupported("saveMediaListEntry")

    /** Removes the entry with this list-entry id from the viewer's list. */
    suspend fun deleteMediaListEntry(entryId: Int): Result<Unit> =
        unsupported("deleteMediaListEntry")

    /**
     * Bio, list statistics and favourites for the profile screen.
     *
     * Exactly one of [userId] / [userName] identifies who; passing neither
     * resolves to nothing and AniList answers with an error the caller surfaces.
     *
     * [forceRefresh] skips the short-lived response cache for the same reason
     * [getUserAnimeList]'s does: a refresh that returns the cached copy is not a
     * refresh.
     */
    suspend fun getUserOverview(
        userId: Int?,
        userName: String?,
        forceRefresh: Boolean = false
    ): Result<UserOverview> = unsupported("getUserOverview")

    /**
     * One page of the person's list activity, newest first.
     *
     * [userId] only - the feed is not addressable by name, and the profile
     * screen always has the id it arrived with. [page] is 1-based, as everywhere
     * else on this API.
     */
    suspend fun getUserActivity(
        userId: Int,
        page: Int = 1,
        forceRefresh: Boolean = false
    ): Result<List<ListActivity>> = unsupported("getUserActivity")

    /** The people who follow this account. Empty list means no followers. */
    suspend fun getUserFollowers(
        userId: Int,
        forceRefresh: Boolean = false
    ): Result<List<FollowUser>> = unsupported("getUserFollowers")

    /** The people this account follows. */
    suspend fun getUserFollowing(
        userId: Int,
        forceRefresh: Boolean = false
    ): Result<List<FollowUser>> = unsupported("getUserFollowing")

    /**
     * One activity's comment thread, oldest first as AniList returns them.
     *
     * Fetched when the sheet opens rather than with the feed, so a person who
     * never taps a comment never pays for a thread.
     */
    suspend fun getActivityReplies(activityId: Int): Result<List<ActivityReply>> =
        unsupported("getActivityReplies")

    /**
     * The people who liked one activity.
     *
     * The default is an empty list rather than [unsupported], unlike every sibling
     * above. Liking is the one engagement feature whose answer is still useful
     * without a session - anyone can read who liked a public activity - so a
     * repository that cannot serve it degrades to "no likers" instead of throwing.
     * Only test doubles exercise the default, and none of them opens this sheet.
     */
    suspend fun getActivityLikes(activityId: Int): Result<List<FollowUser>> =
        Result.success(emptyList())

    /**
     * Likes an activity if it is not liked, un-likes it if it is. AniList does
     * not have a separate "unlike"; the same call toggles, which is why the
     * return value - not the request - is what the UI trusts.
     *
     * Requires a session. Called with none (the demo feed) it fails with
     * [com.anisequel.data.network.AniListErrorKind.INVALID_SESSION], and the UI
     * hides the control rather than offering an action that cannot work.
     */
    suspend fun toggleActivityLike(activityId: Int): Result<ActivityLikeState> =
        unsupported("toggleActivityLike")

    /**
     * Likes or un-likes a comment, by the reply's own id.
     *
     * A reply id is a different namespace from an activity id, which is why this is
     * not `toggleActivityLike` with a different argument - see
     * [com.anisequel.data.network.GraphQLQueries.TOGGLE_REPLY_LIKE].
     */
    suspend fun toggleActivityReplyLike(replyId: Int): Result<ActivityLikeState> =
        unsupported("toggleActivityReplyLike")

    /** Posts [text] as a comment on an activity, returning the created reply. */
    suspend fun postActivityReply(activityId: Int, text: String): Result<ActivityReply> =
        unsupported("postActivityReply")

    /**
     * Rewrites one of the viewer's own comments, returning the stored reply.
     *
     * [activityId] travels with the edit even though the reply id identifies the
     * row: `SaveActivityReply` declares it, and a resolver that re-checks the
     * thread is the one this cannot afford to starve. See
     * [com.anisequel.data.network.GraphQLQueries.EDIT_ACTIVITY_REPLY].
     *
     * Requires a session. The server enforces ownership, so a reply by somebody
     * else fails rather than being rewritten.
     */
    suspend fun editActivityReply(replyId: Int, activityId: Int, text: String): Result<ActivityReply> =
        unsupported("editActivityReply")

    /**
     * Deletes one of the viewer's own comments.
     *
     * [Unit] rather than the deleted reply: `DeleteActivityReply` answers with a
     * `deleted` boolean and nothing else, and the sheet already holds the row by
     * the id it sent. Requires a session, and the reply must be the viewer's own.
     */
    suspend fun deleteActivityReply(replyId: Int): Result<Unit> =
        unsupported("deleteActivityReply")

    fun getDemoProfile(): ViewerProfile
    fun getDemoAnimeList(): MediaListCollection

    /**
     * The same shape as the real thing, so the demo profile screen is the real
     * screen - including its failures, which is the point.
     */
    fun getDemoUserOverview(): UserOverview = unsupported("getDemoUserOverview")
    fun getDemoUserActivity(): List<ListActivity> = unsupported("getDemoUserActivity")
    fun getDemoFollowers(): List<FollowUser> = unsupported("getDemoFollowers")
    fun getDemoFollowing(): List<FollowUser> = unsupported("getDemoFollowing")

    /**
     * Drops everything the repository holds about the signed-in person.
     *
     * Suspending because it reaches the disk snapshots as well as the memory
     * caches: the list, the bio and the viewer's own record are the most
     * identifying things the app holds, and a sign-out that left them on disk
     * would hand them to the next account signed in on this device.
     */
    suspend fun clearDetailCache() {}
}

/**
 * The default for a screen an implementation does not serve.
 *
 * Thrown rather than returned as an empty list, on purpose. An empty activity
 * feed and a failed one look identical on screen - both are a blank list - so a
 * silent default would turn "this repository cannot answer that" into "this
 * person has not done anything", which is the one claim this screen must never
 * make about somebody.
 *
 * `AniListRepositoryImpl` overrides every one. Only the test doubles that exist
 * to exercise the dashboard's two calls rely on the default, and none of them
 * opens a profile.
 */
private fun unsupported(operation: String): Nothing =
    throw UnsupportedOperationException("AniListRepository does not serve $operation")

/**
 * The never-changing [AniListRepository.listMutationVersion] a double gets.
 *
 * One instance shared by every implementation that does not override it, rather
 * than a fresh flow per property read: a `get()` that allocated would hand a
 * collector a different object on each recomposition, and the collector would
 * restart on every frame of a title sheet.
 */
private val IdleListMutations = MutableStateFlow(0L)

/**
 * How many titles one studio page asks for.
 *
 * **25 is AniList's documented maximum on `Studio.media`** - not a preference, and
 * not the root `Page` query's 50, which is where a copy-pasted page size comes
 * from. Asking for 50 there is a 400 on the whole document, so the size lives in
 * one named place that both the request and the "is there another page" test
 * read, rather than as a literal at each site.
 */
const val STUDIO_PAGE_SIZE = 25
