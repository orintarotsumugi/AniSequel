package com.example.data.repository

import com.example.data.model.FollowUser
import com.example.data.model.GraphQLRequest
import com.example.data.model.GraphQLResponse
import com.example.data.model.ListActivity
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.UserOverview
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListApiService
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.network.aniListHttpError
import com.example.data.network.GraphQLQueries
import com.example.data.network.parseAniListErrorBody
import com.example.data.network.RequestCoalescer
import com.example.data.network.toAniListException
import com.squareup.moshi.JsonDataException
import retrofit2.HttpException
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Matches the `mutation` keyword of a GraphQL document.
 *
 * Anchored so a `mutation` appearing inside a string or comment in a *query*
 * cannot flip the classification.
 */
private val MUTATION_PATTERN = Regex("""^\s*mutation\b""")

class AniListRepositoryImpl(
    private val apiService: AniListApiService,
    /**
     * How long a completed list response may be reused, in milliseconds.
     *
     * A supplier rather than a value because the user can change it from
     * Settings, and the cache has to agree with that choice rather than hold a
     * second, private opinion about staleness. It used to be a `const val` here
     * set to an hour, with nothing anywhere else that could express the same
     * decision - so "always refresh on open" could only ever have meant "always
     * get the cached hour back", which is the bug [RefreshInterval] exists to fix.
     *
     * Called per lookup, not per construction, so a setting changed in Settings
     * applies to the very next load. The default keeps the previous behaviour
     * for the call sites that have no store to read (the demo dashboard).
     */
    private val listCacheTtlMillis: () -> Long = { RefreshInterval.DEFAULT.staleAfterMillis }
) : AniListRepository {

    private companion object {
        /**
         * Soft bound on the in-memory detail cache. One node per opened card
         * is held for the lifetime of the VM; without a ceiling, opening a
         * lot of cards keeps their full cast/studio trees resident.
         */
        const val MAX_DETAIL_CACHE_NODES = 128
    }

    /**
     * Identical read queries that arrive while one is already running share its
     * response instead of each issuing their own POST.
     *
     * This is the one form of request de-duplication available on a POST-only,
     * no-cache-header API: rotating the device or re-entering the app used to
     * fire the same multi-megabyte `MediaListCollection` twice in quick
     * succession, spending two of AniList's ~30 requests a minute to learn
     * nothing new the second time.
     *
     * Keyed on operation + variables, so the list query and the detail query
     * never collide.
     */
    private val readCoalescer = RequestCoalescer<String>()
    private val detailCache = ConcurrentHashMap<Int, MediaNode>()

    /**
     * Completed list responses, kept briefly so re-entering the app does not
     * re-buy the same multi-megabyte answer.
     *
     * [readCoalescer] already stops two *simultaneous* identical queries from
     * both being sent, but it does nothing for the common case of one finishing
     * before the next is asked for. AniList allows roughly 30 requests a
     * minute, and this query is the single most expensive one the app makes, so
     * leaving it uncached meant rotating the device or leaving and returning
     * could spend two of those on identical data - and hit the limit exactly
     * when someone was already having a bad time.
     *
     * Deliberately bounded. The whole point of the list is to notice what the
     * user has finished watching, and that changes; an unbounded cache would
     * show a stale gap list that quietly refused to update. The window is the
     * user's [RefreshInterval] - half an hour by default, and never longer than
     * the longest choice they can pick - which is what makes a manual refresh
     * the way to force a re-fetch and, separately, returning to the app enough
     * of a reason to look for one.
     *
     * (This comment used to say "five minutes", and `clearDetailCache` repeated
     * it. The constant was an hour in both cases, so the prose was the only
     * thing wrong - but a reader trusting it would have reasoned about the
     * wrong staleness window entirely.)
     *
     * Keyed on the user, not on the query, so one account's list can never be
     * served to another.
     */
    private val listCache = ConcurrentHashMap<String, CachedList>()

    private class CachedList(val collection: MediaListCollection, val storedAtMillis: Long)

    /**
     * Profile responses, on the same window and the same reasoning as
     * [listCache]: coming back to the profile within the user's chosen refresh
     * interval should not spend another of AniList's ~30 requests a minute.
     *
     * Kept apart from [listCache] rather than folded into it because the two hold
     * different types, and the dashboard already asks for a list far more often
     * than anything asks for a profile - sharing one map would mean the profile
     * fetch evicted the list the dashboard is about to reuse.
     */
    private val overviewCache = ConcurrentHashMap<String, CachedOverview>()

    private class CachedOverview(val overview: UserOverview, val storedAtMillis: Long)

    /**
     * Activity pages and follower lists, keyed by user.
     *
     * Kept apart from [overviewCache] for the same reason the profile itself is:
     * the Social tab is a second request the Overview tab does not make, and
     * re-fetching fifty avatars because the bio was refetched would spend
     * AniList's budget for a list that cannot have changed in that window.
     */
    private val activityCache = ConcurrentHashMap<String, CachedItems<ListActivity>>()
    private val followersCache = ConcurrentHashMap<String, CachedItems<FollowUser>>()
    private val followingCache = ConcurrentHashMap<String, CachedItems<FollowUser>>()

    private class CachedItems<T>(val items: List<T>, val storedAtMillis: Long)

    /**
     * A cached read that is not a media list.
     *
     * Same window and same reasoning as [cachedList]; separate because the key and
     * the payload differ. `listCacheTtlMillis` is read per lookup, so a refresh
     * interval changed in Settings applies to the very next request rather than to
     * whatever was in force when this repository was built.
     */
    private fun <T> cachedItems(cache: ConcurrentHashMap<String, CachedItems<T>>, key: String): List<T>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.storedAtMillis > listCacheTtlMillis()) {
            cache.remove(key)
            return null
        }
        return entry.items
    }

    private fun <T> storeItems(
        cache: ConcurrentHashMap<String, CachedItems<T>>,
        key: String,
        items: List<T>
    ) {
        cache[key] = CachedItems(items, System.currentTimeMillis())
    }

    private fun cachedOverview(key: String): UserOverview? {
        val entry = overviewCache[key] ?: return null
        val ttl = listCacheTtlMillis()
        if (System.currentTimeMillis() - entry.storedAtMillis > ttl) {
            overviewCache.remove(key)
            return null
        }
        return entry.overview
    }

    private fun cachedList(key: String): MediaListCollection? {
        val entry = listCache[key] ?: return null
        val ttl = listCacheTtlMillis()
        if (System.currentTimeMillis() - entry.storedAtMillis > ttl) {
            listCache.remove(key)
            return null
        }
        return entry.collection
    }

    private fun storeList(key: String, collection: MediaListCollection) {
        listCache[key] = CachedList(collection, System.currentTimeMillis())
    }

    override suspend fun getViewer(): Result<ViewerProfile> =
        execute(GraphQLRequest(query = GraphQLQueries.GET_VIEWER), apiService::getViewer)
            .map { it.viewer.require("Viewer data not found") }

    override suspend fun getUserByName(userName: String): Result<ViewerProfile> {
        val name = userName.trim()
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_BY_NAME,
                variables = mapOf("userName" to name)
            ),
            apiService::getUserByName
        ).map { it.user.require("No AniList user called \"$name\".") }
    }

    override suspend fun getUserAnimeList(
        userId: Int,
        forceRefresh: Boolean
    ): Result<MediaListCollection> {
        val key = "user:$userId"
        if (!forceRefresh) cachedList(key)?.let { return Result.success(it) }

        return fetchFullMediaListCollection(userId = userId, userName = null)
            .onSuccess { storeList(key, it) }
    }

    override suspend fun getUserAnimeListByUsername(
        userName: String,
        forceRefresh: Boolean
    ): Result<MediaListCollection> {
        val key = "userName:${userName.trim().lowercase()}"
        if (!forceRefresh) cachedList(key)?.let { return Result.success(it) }

        return fetchFullMediaListCollection(userId = null, userName = userName.trim())
            .onSuccess { storeList(key, it) }
    }

    /**
     * Fetches complete MediaListCollection, supporting AniList chunking for large lists.
     */
    private suspend fun fetchFullMediaListCollection(
        userId: Int?,
        userName: String?
    ): Result<MediaListCollection> {
        val initialVars = mutableMapOf<String, Any?>()
        if (userId != null) initialVars["userId"] = userId
        if (userName != null) initialVars["userName"] = userName
        initialVars["chunk"] = 1

        val firstResult = execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_ANIME_LIST,
                variables = initialVars
            ),
            apiService::getMediaListCollection
        ).map { it.collection.require("Media list collection is empty") }

        val firstCollection = firstResult.getOrElse { return Result.failure(it) }
        if (firstCollection.hasNextChunk != true) {
            return Result.success(firstCollection)
        }

        // Multi-chunk list merge for accounts spanning multiple chunks
        val allLists = firstCollection.lists?.map { it.copy(entries = it.entries?.toMutableList()) }?.toMutableList()
            ?: mutableListOf()
        var currentChunk = 2
        var hasNext = true

        while (hasNext && currentChunk <= 10) {
            val chunkVars = mutableMapOf<String, Any?>()
            if (userId != null) chunkVars["userId"] = userId
            if (userName != null) chunkVars["userName"] = userName
            chunkVars["chunk"] = currentChunk

            val nextResult = execute(
                GraphQLRequest(
                    query = GraphQLQueries.GET_USER_ANIME_LIST,
                    variables = chunkVars
                ),
                apiService::getMediaListCollection
            ).map { it.collection.require("Media list collection chunk $currentChunk is empty") }

            val nextCollection = nextResult.getOrNull() ?: break
            val nextLists = nextCollection.lists.orEmpty()

            nextLists.forEach { nextGroup ->
                val existingGroupIndex = allLists.indexOfFirst {
                    it.name.equals(nextGroup.name, ignoreCase = true) ||
                        (it.status != null && it.status.equals(nextGroup.status, ignoreCase = true))
                }
                if (existingGroupIndex >= 0) {
                    val existingGroup = allLists[existingGroupIndex]
                    val mergedEntries = ((existingGroup.entries ?: emptyList()) + (nextGroup.entries ?: emptyList()))
                    allLists[existingGroupIndex] = existingGroup.copy(entries = mergedEntries)
                } else {
                    allLists.add(nextGroup)
                }
            }

            hasNext = nextCollection.hasNextChunk == true
            currentChunk++
        }

        return Result.success(MediaListCollection(lists = allLists, hasNextChunk = false))
    }

    /**
     * The heavy per-entry fields, fetched for the one sequel the user opens.
     *
     * Keeping these out of the list query is what stops AniList returning HTTP
     * 500 for large accounts, and it means opening a card no longer re-parses
     * 1,500 nodes' worth of synopses.
     */
    override suspend fun getMediaDetail(mediaId: Int): Result<MediaNode> {
        detailCache[mediaId]?.let { return Result.success(it) }
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_MEDIA_DETAIL,
                variables = mapOf("id" to mediaId)
            ),
            apiService::getMediaDetail
        ).map {
            val node = it.media.require("That entry is no longer available on AniList.")
            if (detailCache.size >= MAX_DETAIL_CACHE_NODES) {
                // Soft cap: falling back to a fresh fetch on the next open
                // is cheaper than holding every full cast/studio tree the
                // user has ever tapped.
                detailCache.clear()
            }
            detailCache[mediaId] = node
            node
        }
    }

    override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.ADD_TO_PLANNING,
                variables = mapOf("mediaId" to mediaId)
            ),
            apiService::saveMediaListEntry
        ).map { it.entry.require("No response from planning mutation") }
            // The completed list has one more "planned" entry than the copy
            // this add just made. Both cache keys (`user:` for the signed-in
            // query, `userName:` for the public one) can be holding the
            // same franchise's entry as "missed", so the safest correct move
            // is dropping them rather than discovering the gap entry it
            // added still shows as a missed sequel on the next list recompute.
            .onSuccess { listCache.clear() }

    override suspend fun addToWatching(mediaId: Int): Result<SimpleMediaListEntry> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.ADD_TO_WATCHING,
                variables = mapOf("mediaId" to mediaId)
            ),
            apiService::saveMediaListEntry
        ).map { it.entry.require("No response from watching mutation") }
            .onSuccess { listCache.clear() }

    /**
     * Keyed on whichever identifier the caller supplied, so the signed-in
     * profile and a scanned public one get separate entries - and so a
     * username that has been renamed cannot serve the old account's bio.
     */
    private fun overviewCacheKey(userId: Int?, userName: String?): String = when {
        userId != null -> "user:$userId"
        else -> "userName:${userName.orEmpty().trim()}"
    }

    override suspend fun getUserOverview(
        userId: Int?,
        userName: String?,
        forceRefresh: Boolean
    ): Result<UserOverview> {
        val key = overviewCacheKey(userId, userName)
        if (!forceRefresh) cachedOverview(key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_OVERVIEW,
                variables = mapOf(
                    "userId" to userId,
                    "userName" to userName?.trim()
                )
            ),
            apiService::getUserOverview
        ).map { data ->
            val overview = data.user.require(
                userName?.let { "No AniList user called \"$it\"." }
                    ?: "AniList returned no profile for this account."
            )
            overviewCache[key] = CachedOverview(overview, System.currentTimeMillis())
            overview
        }
    }

    override fun getDemoProfile(): ViewerProfile = DemoDataProvider.getDemoViewer()

    override fun getDemoAnimeList(): MediaListCollection = DemoDataProvider.getDemoMediaList()

    override fun getDemoUserOverview(): UserOverview = DemoProfileProvider.getDemoUserOverview()

    override fun getDemoUserActivity(): List<ListActivity> = DemoProfileProvider.getDemoActivity()

    override fun getDemoFollowers(): List<FollowUser> = DemoProfileProvider.getDemoFollowers()

    override fun getDemoFollowing(): List<FollowUser> = DemoProfileProvider.getDemoFollowing()

    override suspend fun getUserActivity(
        userId: Int,
        page: Int,
        forceRefresh: Boolean
    ): Result<List<ListActivity>> {
        val key = "user:$userId:page:$page"
        if (!forceRefresh) cachedItems(activityCache, key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_ACTIVITY,
                variables = mapOf("userId" to userId, "page" to page)
            ),
            apiService::getUserActivity
        ).map { data ->
            // `isUnrecognised` is the union member this app does not model. The
            // query filters with `type_in`, so in practice the server only sends
            // list updates - but the filter is a `type_in` argument, not a
            // guarantee, and a forum post arriving here would parse into an
            // activity with no media and no status. Dropping those here means a
            // blank card is never rendered; see `ListActivity.isUnrecognised`.
            val activities = data.page?.activities.orEmpty()
                .filterNot { it.isUnrecognised || it.media == null }
            storeItems(activityCache, key, activities)
            activities
        }
    }

    override suspend fun getUserFollowers(userId: Int, forceRefresh: Boolean): Result<List<FollowUser>> =
        followList(userId, forceRefresh, followersCache, "followers") { request ->
            execute(request, apiService::getUserFollowers).map { data ->
                data.page?.followers.orEmpty()
            }
        }

    override suspend fun getUserFollowing(userId: Int, forceRefresh: Boolean): Result<List<FollowUser>> =
        followList(userId, forceRefresh, followingCache, "following") { request ->
            execute(request, apiService::getUserFollowing).map { data ->
                data.page?.following.orEmpty()
            }
        }

    private suspend fun followList(
        userId: Int,
        forceRefresh: Boolean,
        cache: ConcurrentHashMap<String, CachedItems<FollowUser>>,
        which: String,
        fetch: suspend (GraphQLRequest) -> Result<List<FollowUser>>
    ): Result<List<FollowUser>> {
        val key = "user:$userId:$which"
        if (!forceRefresh) cachedItems(cache, key)?.let { return Result.success(it) }

        val query = when (which) {
            "followers" -> GraphQLQueries.GET_USER_FOLLOWERS
            else -> GraphQLQueries.GET_USER_FOLLOWING
        }
        return fetch(GraphQLRequest(query = query, variables = mapOf("userId" to userId)))
            .onSuccess { storeItems(cache, key, it) }
    }

    override fun clearDetailCache() {
        detailCache.clear()
        // The list is per-person data, so it has to go when the session does.
        // A TTL alone would leave another person's finished list sitting in
        // memory for up to an hour after sign-out.
        listCache.clear()
        // Same reasoning for the profile, and it matters more here: the bio and
        // the favourites are the most identifying thing the app holds about
        // anyone, and a public-profile scan is exactly how someone else's ends
        // up in this process.
        overviewCache.clear()
        // As are the activity feed and the follower lists, which are just as
        // personal and are fetched for other people too.
        activityCache.clear()
        followersCache.clear()
        followingCache.clear()
    }

    /**
     * One call, every failure classified.
     *
     * Failures used to reach the UI as whatever Retrofit or the socket produced -
     * "HTTP 429 Client Error", "Unable to resolve host", a GraphQL message - and
     * the screen guessed what to offer from the wording. Wording is not a
     * contract, so the classification happens here, once.
     */
    private suspend fun <T : Any> execute(
        request: GraphQLRequest,
        call: suspend (GraphQLRequest) -> GraphQLResponse<T>
    ): Result<T> {
        return try {
            val response = if (isReadOnly(request)) {
                readCoalescer.coalesce(coalescingKey(request)) { call(request) }
            } else {
                call(request)
            }
            response.errors?.firstOrNull()?.let { throw it.toAniListException() }
            Result.success(
                response.data ?: throw AniListException(
                    AniListErrorKind.NOT_FOUND,
                    "AniList returned no data for this request."
                )
            )
        } catch (e: HttpException) {
            Result.failure(e.toAniListException())
        } catch (e: AniListException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(
                AniListException(
                    kind = AniListErrorKind.OFFLINE,
                    message = "Can't reach AniList. Check your connection and try again.",
                    cause = e
                )
            )
        } catch (e: JsonDataException) {
            // A response we cannot parse is neither an auth problem nor a
            // network blip, and the raw Moshi message is unusable to a reader:
            //
            //   "Expected an int but was WINTER at path
            //    $.data.MediaListCollection.lists[0].entries[1]....node.season"
            //
            // That is what the error screen was showing. It names no field a
            // user knows about and offers no way forward, so it gets a plain
            // sentence here. The cause is kept for logcat.
            Result.failure(
                AniListException(
                    kind = AniListErrorKind.MALFORMED_RESPONSE,
                    message = "AniList sent data this version of the app couldn't read. " +
                            "Update AniSequel, or try again in a moment.",
                    cause = e
                )
            )
        } catch (e: kotlinx.coroutines.CancellationException) {
            // Cancellation must propagate: treating it as failure reports a
            // bogus error and leaves the structured-concurrency tree alive.
            throw e
        } catch (e: Exception) {
            Result.failure(
                AniListException(
                    kind = AniListErrorKind.UNKNOWN,
                    message = e.message ?: "Something went wrong talking to AniList.",
                    cause = e
                )
            )
        }
    }

    private fun <T : Any> T?.require(message: String): T =
        this ?: throw AniListException(AniListErrorKind.NOT_FOUND, message)

    /**
     * Reads only. A mutation must never be shared: collapsing two
     * `SaveMediaListEntry` calls into one would silently drop a write the user
     * asked for.
     *
     * This was `MUTATION_PATTERN.containsMatchIn(...)` with no negation, so it
     * answered `true` for mutations and `false` for queries - exactly backwards.
     * The consequences were both real and both invisible:
     *
     *  - Two concurrent "Add to Planning" taps collapsed into one write, and the
     *    second entry the user asked for was silently never saved.
     *  - Reads were never coalesced at all, so the whole feature - and the
     *    request budget it exists to protect - was doing nothing.
     */
    private fun isReadOnly(request: GraphQLRequest): Boolean =
        !MUTATION_PATTERN.containsMatchIn(request.query)

    /**
     * The cache key.
     *
     * Variables are part of it because the list and detail queries share an
     * operation name shape but not their arguments; coalescing them would hand
     * one user's request another's response.
     */
    private fun coalescingKey(request: GraphQLRequest): String =
        buildString {
            append(request.query)
            append('|')
            // Sorted so two logically identical variable maps always produce the
            // same key regardless of insertion order.
            request.variables.entries.sortedBy { it.key }.forEach { (key, value) ->
                append(key).append('=').append(value).append(';')
            }
        }

    /**
     * Classifies a failed HTTP call, reading the reason out of the body.
     *
     * AniList answers a rejected token with HTTP 400 and
     * `{"errors":[{"message":"Invalid token","status":400}]}` - the same status
     * it uses for a malformed query. Retrofit discards that body before the
     * repository ever sees it, so it is read here; without it every dead session
     * looked like an unknown error and the dashboard offered a Retry button that
     * could never succeed instead of "Sign in again".
     */
    private fun HttpException.toAniListException(): AniListException =
        aniListHttpError(
            code = code(),
            retryAfterSeconds = response()?.headers()?.get("Retry-After")?.toIntOrNull(),
            cause = this,
            reportedError = parseAniListErrorBody(
                runCatching { response()?.errorBody()?.string() }.getOrNull()
            )?.errors?.firstOrNull()
        )
}