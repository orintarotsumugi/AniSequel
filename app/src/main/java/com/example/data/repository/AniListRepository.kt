package com.example.data.repository

import com.example.data.model.FollowUser
import com.example.data.model.ListActivity
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.UserOverview
import com.example.data.model.ViewerProfile

interface AniListRepository {
    suspend fun getViewer(): Result<ViewerProfile>
    suspend fun getUserByName(userName: String): Result<ViewerProfile>
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

    /** As [getUserAnimeList], for a profile looked up by name. */
    suspend fun getUserAnimeListByUsername(
        userName: String,
        forceRefresh: Boolean = false
    ): Result<MediaListCollection>

    /**
     * Description, banner and studio for one entry, fetched on demand because
     * the list query omits them (they are only rendered for the entry the user
     * opens, and asking for them up front makes AniList fail on large lists).
     */
    suspend fun getMediaDetail(mediaId: Int): Result<MediaNode>
    suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry>
    suspend fun addToWatching(mediaId: Int): Result<SimpleMediaListEntry> = unsupported("addToWatching")

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
    fun clearDetailCache() {}
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
 * `AniListRepositoryImpl` overrides all six. Only the test doubles that exist to
 * exercise the dashboard's two calls rely on the default, and none of them opens
 * a profile.
 */
private fun unsupported(operation: String): Nothing =
    throw UnsupportedOperationException("AniListRepository does not serve $operation")
