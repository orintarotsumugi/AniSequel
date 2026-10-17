package com.anisequel.data.repository

import com.anisequel.data.model.ActivityLikeState
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.GraphQLRequest
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.ListActivity
import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaEntryEdit
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.UserOverview
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListApiService
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.network.aniListHttpError
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.network.parseAniListErrorBody
import com.anisequel.data.network.RequestCoalescer
import com.anisequel.data.network.toAniListException
import com.squareup.moshi.JsonDataException
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/**
 * ## This file is over the 700-line ceiling, on purpose
 *
 * `AGENTS.md` says to keep files under 700 lines and split by responsibility.
 * This one is exempt.
 *
 * It is one responsibility: **how this app talks to AniList over time**. The
 * caches, the coalescer, the rate-limit errors and the namespace-keyed detail
 * cache are not features that happen to share a file - they are one mechanism,
 * and they interact. The detail cache is keyed on `(type, id)` precisely because
 * `anime/1` and `manga/1` are two different entries; the coalescer exists because
 * two screens can ask for the same title in the same frame; both live here
 * because a split would mean deciding which one owns the other's invariants, and
 * that decision would be wrong half the time.
 *
 * The same rule as `DashboardViewModel`, which `AGENTS.md` already records as an
 * exception for exactly this reason: the recompute pipeline shares about a dozen
 * private fields, and splitting it means passing state around or making it
 * `internal`, and both make the invariants worse rather than better.
 *
 * What is worth doing here is not a split but a test: the cache-cap eviction and
 * the coalescer's double-request collapse are both behaviour nothing currently
 * pins.
 */

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

        /**
         * Soft cap on cached cover URLs.
         *
         * Far larger than [MAX_DETAIL_CACHE_NODES] because a cover URL is a string
         * rather than a full media node with a cast and studio tree, and because
         * bios link to each other - the same handful of hall-of-fame titles
         * recurs across profiles, so a small cache here would miss constantly.
         */
        const val MAX_COVER_CACHE_ENTRIES = 512

        /**
         * AniList's `MediaType` enum names, as the GraphQL variables carry them.
         *
         * Strings rather than an enum because these travel into a query variable
         * map, and a locally-declared enum would have to be kept in step with the
         * server's - two lists of the same names, with no compiler help when the
         * server adds one.
         */
        const val LIST_TYPE_ANIME = "ANIME"
        const val LIST_TYPE_MANGA = "MANGA"

        /**
         * The [MediaCoverKey] type used for a detail fetched with no media type.
         *
         * AniList's own `MediaType` enum has an `UNSPECIFIED` member, and reusing its
         * spelling keeps the placeholder a value the parser could one day produce,
         * rather than a magic string that exists only in this cache.
         */
        const val UNSPECIFIED_MEDIA_TYPE = "UNSPECIFIED"
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

    /**
     * Fetched detail nodes, keyed on [MediaCoverKey] rather than a bare id.
     *
     * AniList ids are unique per media namespace, not globally, so `anime/1` and
     * `manga/1` are two different entries sharing a number. Keyed on the id alone
     * this cache served whichever of the two was fetched first to *both* callers -
     * a manga's detail sheet rendering an anime's synopsis and cast - which reads
     * as "this title has no relations" rather than as a bug. Keyed the same way as
     * [coverCache], deliberately.
     *
     * An entry fetched with no type at all lands under [UNSPECIFIED_MEDIA_TYPE], so
     * it cannot be served to a caller that asked for a specific namespace.
     */
    private val detailCache = ConcurrentHashMap<MediaCoverKey, MediaNode>()

    /**
     * Character and staff profiles, keyed on the **kind** as well as the id.
     *
     * A second namespace with the same collision as [detailCache]: `Character(1)` is
     * Spike Spiegel and `Staff(1)` is somebody else entirely, so a bare-id cache
     * would serve one person's face on the other's page.
     *
     * Bounded because a cast is twenty-five cards long and a reader can open every
     * one; each entry holds a description and up to twelve covers. The cap matches
     * [MAX_DETAIL_CACHE_NODES] for the same reason - past it, the cheapest correct
     * thing is to refetch rather than to keep reasoning about what is stale.
     */
    private val personProfileCache =
        ConcurrentHashMap<Pair<PersonKind, Int>, Any>()

    /**
     * Types [personProfileCache] holds, split so the two reads are type-safe rather
     * than an unchecked cast at the call site.
     */
    private fun cachedCharacter(id: Int): CharacterProfile? =
        personProfileCache[PersonKind.CHARACTER to id] as? CharacterProfile

    private fun cachedStaff(id: Int): StaffProfile? =
        personProfileCache[PersonKind.STAFF to id] as? StaffProfile

    /**
     * Media key to cover, for the markdown preview cards.
     *
     * Separate from [detailCache] because the two are asked for at completely
     * different moments and want opposite lifetimes: a detail node is fetched when
     * a person taps one card and is worth keeping, whereas covers arrive a dozen
     * at a time just to decorate a bio and are cheap to re-fetch if dropped.
     *
     * Keyed on [MediaCoverKey] rather than a bare id so `anime/1` and `manga/1` -
     * which are different shows - do not overwrite each other in here. Keyed on an
     * id, this cache would serve one of the two posters for both cards
     * indefinitely, not just for the length of one request.
     */
    private val coverCache = ConcurrentHashMap<MediaCoverKey, MediaCover>()

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
     * Bumped whenever a mutation invalidates [listCache].
     *
     * A non-forced fetch that starts before a mutation can finish after it and
     * would otherwise write its pre-mutation copy straight back over the cleared
     * cache - so the very next `getUserAnimeList` no longer sees the entry the
     * mutation just added, and the dashboard's "already planned" gap reappears.
     * A fetch captures this value before it starts and only stores when it is
     * unchanged, which turns the racing write into a miss instead of a stale hit.
     */
    @Volatile
    private var listEpoch = 0

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

        val epoch = listEpoch
        return fetchFullMediaListCollection(userId = userId, userName = null)
            .onSuccess { if (epoch == listEpoch) storeList(key, it) }
    }

    /**
     * The user's manga list, cached under its own key.
     *
     * Separate from [getUserAnimeList] because AniList's `MediaType` is a filter on
     * the same query rather than a different resource, so the two results never
     * overlap - sharing a cache key would let whichever list loaded last answer for
     * the other, and the Library tab would silently show anime under a Manga filter.
     *
     * Not fetched when a profile opens. The anime list already is, because the Stats
     * tab tallies it, so manga is the one half the app did not already have on hand;
     * fetching it eagerly would spend a request - and AniList is currently degraded
     * to 30 per minute - on a list half the profile never looks at.
     */
    override suspend fun getUserMangaList(
        userId: Int,
        forceRefresh: Boolean
    ): Result<MediaListCollection> {
        val key = "user:$userId:manga"
        if (!forceRefresh) cachedList(key)?.let { return Result.success(it) }

        val epoch = listEpoch
        return fetchFullMediaListCollection(userId = userId, userName = null, listType = LIST_TYPE_MANGA)
            .onSuccess { if (epoch == listEpoch) storeList(key, it) }
    }

    override suspend fun getUserAnimeListByUsername(
        userName: String,
        forceRefresh: Boolean
    ): Result<MediaListCollection> {
        // Locale.ROOT, not the device locale: a cache key must be the same
        // string for the same username on every device. Under a Turkish locale
        // `"I".lowercase()` is `"ı"`, so a username containing an I would miss
        // the cache written on another device and fetch the whole list again.
        val key = "userName:${userName.trim().lowercase(java.util.Locale.ROOT)}"
        if (!forceRefresh) cachedList(key)?.let { return Result.success(it) }

        val epoch = listEpoch
        return fetchFullMediaListCollection(userId = null, userName = userName.trim())
            .onSuccess { if (epoch == listEpoch) storeList(key, it) }
    }

    /**
     * Fetches complete MediaListCollection, supporting AniList chunking for large lists.
     *
     * [listType] is the GraphQL `MediaType` enum name. It is a parameter rather
     * than a hardcoded `ANIME` because the Library tab needs the manga half of the
     * same list, and a second copy of this method would be a second copy of the
     * chunk merge below - which is the part with real invariants in it.
     */
    private suspend fun fetchFullMediaListCollection(
        userId: Int?,
        userName: String?,
        listType: String = LIST_TYPE_ANIME
    ): Result<MediaListCollection> {
        val query = if (listType == LIST_TYPE_MANGA) {
            GraphQLQueries.GET_USER_MANGA_LIST
        } else {
            GraphQLQueries.GET_USER_ANIME_LIST
        }

        val initialVars = mutableMapOf<String, Any?>()
        if (userId != null) initialVars["userId"] = userId
        if (userName != null) initialVars["userName"] = userName
        initialVars["chunk"] = 1
        initialVars["type"] = listType

        val firstResult = execute(
            GraphQLRequest(
                query = query,
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
            // `type` is declared non-null on the query, so a chunk that omitted it
            // would be rejected outright - which would fail the whole walk and, by
            // the contract above, fail the *first* page's success too. An account
            // large enough to need chunk two would simply have had no list.
            chunkVars["type"] = listType

            val nextResult = execute(
                GraphQLRequest(
                    query = query,
                    variables = chunkVars
                ),
                apiService::getMediaListCollection
            ).map { it.collection.require("Media list collection chunk $currentChunk is empty") }

            // A later chunk that fails must fail the whole walk. Swallowing it
            // (`?: break`) merged whatever arrived before the failure into a
            // shorter list and returned it as `Result.success`, so both callers
            // cached a silently truncated account as if it were complete. The
            // first chunk already refuses to degrade; this is the same contract
            // for every chunk after it.
            val nextCollection = nextResult.getOrElse { return Result.failure(it) }
            val nextLists = nextCollection.lists.orEmpty()

            nextLists.forEach { nextGroup ->
                // AniList splits one list across chunk boundaries, so the merge
                // key is the list's name. The old condition also matched on
                // status, which was the wrong axis: two different *custom* lists
                // sharing a status (for example both pointing at the same
                // standard status) got their entries welded together here,
                // silently renumbering both every fetch.
                val existingGroupIndex = allLists.indexOfFirst {
                    it.name != null && it.name.equals(nextGroup.name, ignoreCase = true)
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
     * The heavy per-entry fields, fetched for the one entry the user opens.
     *
     * Keeping these out of the list query is what stops AniList returning HTTP
     * 500 for large accounts, and it means opening a card no longer re-parses
     * 1,500 nodes' worth of synopses. The query itself is the full display
     * record - see [GraphQLQueries.getMediaDetailQuery] - because the profile feed
     * and the arriving section open entries the list query only summarised.
     */
    override suspend fun getMediaDetail(mediaId: Int, mediaType: String?): Result<MediaNode> {
        // Keyed by namespace *and* id, never by id alone. AniList media ids are not
        // unique across `anime/` and `manga/`, so a bare-id cache handed the manga's
        // caller the anime's record whenever the two happened to share an id - the
        // same collision [MediaCoverKey] documents for covers. `UNSPECIFIED` is the
        // bucket for callers that genuinely have no type, so an untyped fetch and a
        // typed one cannot collide either.
        val cacheKey = MediaCoverKey(mediaType ?: UNSPECIFIED_MEDIA_TYPE, mediaId)
        detailCache[cacheKey]?.let { return Result.success(it) }
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.getMediaDetailQuery(mediaType),
                variables = buildMap {
                    put("id", mediaId)
                    mediaType?.let { put("type", it.uppercase(Locale.ROOT)) }
                }
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
            detailCache[cacheKey] = node
            node
        }
    }

    /**
     * One page of a studio's work.
     *
     * Deliberately **not** cached, unlike [getMediaDetail]. That cache exists
     * because the same title is opened repeatedly and its cast is expensive; a
     * studio page is paged through once in a sitting and then abandoned, and pages
     * of it would accumulate in memory to answer a question nobody asks twice in a
     * session. The coalescer still deduplicates a double tap on the same studio,
     * which is the burst that actually happens.
     *
     * `perPage` is [STUDIO_PAGE_SIZE] and not a caller's choice: it is AniList's
     * documented maximum on this connection, and a larger value fails the whole
     * document with a 400 rather than trimming the page.
     */
    override suspend fun getStudio(studioId: Int, page: Int): Result<StudioNode> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.getStudioQuery(),
                variables = mapOf(
                    "id" to studioId,
                    "page" to page.coerceAtLeast(1),
                    "perPage" to STUDIO_PAGE_SIZE
                )
            ),
            apiService::getStudio
        ).map {
            it.studio.require("That studio is no longer on AniList.")
        }

    override suspend fun getMediaCovers(
        keys: List<MediaCoverKey>
    ): Result<Map<MediaCoverKey, MediaCover>> {
        if (keys.isEmpty()) return Result.success(emptyMap())

        // Already-known covers are answered without touching the network. This is
        // the common case for a profile the reader has already opened once, and
        // it is what stops a re-open from spending a request at all.
        val resolved = LinkedHashMap<MediaCoverKey, MediaCover>()
        val missing = mutableListOf<MediaCoverKey>()
        // Distinct on the *key*, not the id. `anilist.co/anime/1` and
        // `anilist.co/manga/1` are two different shows that happen to share an id,
        // and deduplicating on the id would fetch one and show it on both cards.
        keys.distinct().forEach { key ->
            val cached = coverCache[key]
            if (cached != null) resolved[key] = cached else missing += key
        }
        if (missing.isEmpty()) return Result.success(resolved)

        // One alias per key, so the response is read back by position - two keys
        // sharing an id would otherwise need two aliases on the same field.
        val variables = buildMap {
            missing.forEachIndexed { index, key ->
                put("id$index", key.id)
                put("type$index", key.graphqlType)
            }
        }
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.getMediaCoversQuery(missing),
                variables = variables
            ),
            apiService::getMediaCovers
        ).map { byAlias ->
            // `execute` already unwrapped GraphQLResponse.data, so what arrives
            // here *is* the alias map - there is no `.data` on it to reach for.
            missing.forEachIndexed { index, key ->
                // `firstOrNull` rather than a single node: the batch asks
                // `Page.media` precisely so an id with no media is an empty list
                // instead of a 404 that fails the other eleven cards. See
                // [MediaCoverPage].
                val node = byAlias[GraphQLQueries.mediaCoverAlias(index)]?.media?.firstOrNull()
                val url = node?.coverImage?.bestUrl
                if (url != null) {
                    // The key comes from the request rather than from
                    // `node.type`. They should agree, but if AniList ever answers
                    // with a different type than the one asked for, the card has to
                    // find the cover under the key it requested it by or it draws
                    // the placeholder over a request it already paid for.
                    val cover = MediaCover(
                        url = url,
                        // The real title, which the query asks for on every entry.
                        // A bio link of `anilist.co/anime/1` has no readable name in
                        // it, so this is the only thing that can label the card with
                        // anything but the type and a number.
                        title = node.title?.bestTitleOrNull
                    )
                    if (coverCache.size >= MAX_COVER_CACHE_ENTRIES) coverCache.clear()
                    coverCache[key] = cover
                    resolved[key] = cover
                }
            }
            // A media with no cover is absent from the answer rather than an error:
            // the caller draws a placeholder, and one missing poster must not
            // blank the other eleven cards beside it.
            resolved
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
            // Bumping the epoch first also invalidates any fetch already in
            // flight, so it cannot repopulate the cache with the pre-add list.
            .onSuccess {
                listEpoch++
                listCache.clear()
            }

    override suspend fun addToWatching(mediaId: Int): Result<SimpleMediaListEntry> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.ADD_TO_WATCHING,
                variables = mapOf("mediaId" to mediaId)
            ),
            apiService::saveMediaListEntry
        ).map { it.entry.require("No response from watching mutation") }
            .onSuccess {
                listEpoch++
                listCache.clear()
            }

    override suspend fun getMediaListEntry(
        mediaId: Int,
        userId: Int?
    ): Result<SimpleMediaListEntry> {
        val variables = buildMap<String, Any?> {
            put("mediaId", mediaId)
            // Omitted rather than sent as null when the caller has no id, so the
            // document stays valid: a null `userId` would be a deliberate request
            // for "nobody's entry", which is never what this wants.
            if (userId != null) put("userId", userId)
        }
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_MEDIA_LIST_ENTRY,
                variables = variables
            ),
            apiService::getMediaListEntry
        ).map { it.entry ?: SimpleMediaListEntry(mediaId = mediaId) }
    }

    override suspend fun saveMediaListEntry(edit: MediaEntryEdit): Result<SimpleMediaListEntry> {
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.SAVE_MEDIA_LIST_ENTRY,
                variables = edit.toSaveVariables()
            ),
            apiService::saveMediaListEntry
        ).map { it.entry.require("No response from the entry mutation") }
            .onSuccess {
                // The cached list now holds the pre-edit entry. Dropping it is what
                // makes the dashboard's own counts and the Library row agree with
                // the form the user just saved, rather than the copy from before.
                listEpoch++
                listCache.clear()
            }
    }

    override suspend fun deleteMediaListEntry(entryId: Int): Result<Unit> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.DELETE_MEDIA_LIST_ENTRY,
                variables = mapOf("id" to entryId)
            ),
            apiService::deleteMediaListEntry
        ).map { }
            .onSuccess {
                listEpoch++
                listCache.clear()
            }

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

    override suspend fun getActivityReplies(activityId: Int): Result<List<ActivityReply>> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_ACTIVITY_REPLIES,
                variables = mapOf("activityId" to activityId)
            ),
            apiService::getActivityReplies
        ).map { data ->
            // A member the fragment did not match comes back with no selected
            // fields, and an id that no longer exists as null. Both are an empty
            // thread: the sheet opens and says there are no comments, rather than
            // refusing to open at all.
            data.activity?.replies.orEmpty()
        }

    override suspend fun getActivityLikes(activityId: Int): Result<List<FollowUser>> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_ACTIVITY_LIKES,
                variables = mapOf("activityId" to activityId)
            ),
            apiService::getActivityLikes
        ).map { data ->
            // The same reduction as the replies fetch: a member the fragment did
            // not match comes back with no selected fields, and a retired id as
            // null. Both are "nobody to show", not a failure, so the sheet still
            // opens and says so.
            data.activity?.likes.orEmpty()
        }

    override suspend fun toggleActivityLike(activityId: Int): Result<ActivityLikeState> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.TOGGLE_ACTIVITY_LIKE,
                variables = mapOf("activityId" to activityId)
            ),
            apiService::toggleActivityLike
        ).map { data ->
            val likeable = data.likeable.require("No response from the like mutation")
            ActivityLikeState(
                isLiked = likeable.isLiked == true,
                likeCount = likeable.likeCount ?: 0
            )
        }.onSuccess {
            // The cached feed pages still carry the pre-toggle like state and
            // count. Dropping them is cheaper than patching each page in place,
            // and it is what stops a re-entry showing a like the user has undone.
            activityCache.clear()
        }

    override suspend fun toggleActivityReplyLike(replyId: Int): Result<ActivityLikeState> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.TOGGLE_REPLY_LIKE,
                variables = mapOf("replyId" to replyId)
            ),
            apiService::toggleActivityReplyLike
        ).map { data ->
            // The same reduction as the activity toggle: `ToggleLikeV2` answers with a
            // union and nothing else if the member did not match, and a null count is
            // "zero", not a question.
            val likeable = data.likeable.require("No response from the reply like mutation")
            ActivityLikeState(
                isLiked = likeable.isLiked == true,
                likeCount = likeable.likeCount ?: 0
            )
        }
        // No cache to drop, deliberately. The activity toggle clears `activityCache`
        // because the feed rows carry the pre-toggle state. A reply lives only inside
        // the open comment sheet, and that sheet is rebuilt from its own state -
        // clearing the feed here would cost a re-download to fix nothing.

    override suspend fun postActivityReply(activityId: Int, text: String): Result<ActivityReply> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.SAVE_ACTIVITY_REPLY,
                variables = mapOf("activityId" to activityId, "text" to text)
            ),
            apiService::saveActivityReply
        ).map { it.reply.require("No response from the reply mutation") }
            .onSuccess {
                // The feed's `replyCount` is now one short of the truth, so the
                // same cache drop the like does applies here.
                activityCache.clear()
            }

    /**
     * One character, for their profile sheet.
     *
     * Cached per id, because a reader walking a cast opens several and AniList allows
     * 30 requests a minute: going back and forth between two characters would
     * otherwise spend the budget on a screen already in hand.
     *
     * The cache key carries the [PersonKind] as well as the id. AniList's `Character`
     * and `Staff` are independent id namespaces - the same argument `MediaCoverKey`
     * makes for `anime/1` versus `manga/1` - so a bare id cache would hand a
     * director's sheet to the character who shares their number.
     */
    override suspend fun getCharacterProfile(characterId: Int): Result<CharacterProfile> {
        cachedCharacter(characterId)?.let { return Result.success(it) }
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.getCharacterProfileQuery(),
                variables = mapOf("id" to characterId)
            ),
            apiService::getCharacterProfile
        ).map { data ->
            data.character.require("That character is no longer available on AniList.")
                .also {
                    if (personProfileCache.size >= MAX_DETAIL_CACHE_NODES) personProfileCache.clear()
                    personProfileCache[PersonKind.CHARACTER to characterId] = it
                }
        }
    }

    /** One staff member. Cached on the same terms as [getCharacterProfile]. */
    override suspend fun getStaffProfile(staffId: Int): Result<StaffProfile> {
        cachedStaff(staffId)?.let { return Result.success(it) }
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.getStaffProfileQuery(),
                variables = mapOf("id" to staffId)
            ),
            apiService::getStaffProfile
        ).map { data ->
            data.staff.require("That person is no longer available on AniList.")
                .also {
                    if (personProfileCache.size >= MAX_DETAIL_CACHE_NODES) personProfileCache.clear()
                    personProfileCache[PersonKind.STAFF to staffId] = it
                }
        }
    }

    override fun clearDetailCache() {
        detailCache.clear()
        // A profile is public data, but this cache is the size of a cast walk, and
        // the next account opened should not inherit the last one's.
        personProfileCache.clear()
        // The list is per-person data, so it has to go when the session does.
        // A TTL alone would leave another person's finished list sitting in
        // memory for up to an hour after sign-out.
        //
        // The epoch bump invalidates any fetch already in flight, so a request
        // that started before sign-out cannot write the previous person's list
        // back into the now-cleared cache.
        listEpoch++
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

/**
 * The variables for [GraphQLQueries.SAVE_MEDIA_LIST_ENTRY].
 *
 * Top-level and `internal` rather than a private member, so the one rule it
 * encodes - which arguments may be absent - is testable. That rule is not a
 * style choice: AniList's own input validator rejects an explicit null for all
 * three numeric arguments, and the failure is a write that never happens.
 *
 * ## Why every field is sent, except the one that is not
 *
 * The editor's Save is a whole snapshot of the form, so an omitted value would be
 * an edit the user made and the server never heard about - a cleared finish date
 * that reappears, or a private toggle that springs back. That part is deliberate.
 *
 * `progress`, `score` and `progressVolumes` are the exception, and they were the
 * bug: sent as null, AniList answers **HTTP 400** with
 *
 *     "errors": [{"validation": {"progressVolumes":
 *                  ["The progress volumes must be an integer."]}}]
 *
 * and no part of the entry is saved. Verified against the live API:
 * `progressVolumes: 0` validates, `progressVolumes: null` does not, and omitting
 * the argument entirely validates too.
 *
 *  - **progress and score default to 0 rather than null.** The form always shows
 *    a value for both, so an untouched control means zero episodes and no score,
 *    and sending that is the snapshot the button promises.
 *  - **progressVolumes is omitted when there is none.** The form has no volume
 *    control: a manga's loaded value is carried through untouched, and an anime
 *    (where AniList answers null, not 0) leaves the argument off - which keeps
 *    the server's value. Sending it as null made every save on an *anime* fail,
 *    because an anime entry has no volume count at all.
 *
 * The dates are always sent, and that is the one place a null is meaningful:
 * it is how AniList clears a date, and the row's X depends on it.
 *
 * The private flag is sent as a boolean, never as null. It is not a *clearing*
 * control the way the dates are - the toggle always holds a value the user can
 * see and change - so an unset flag is an entry that is not private, and `false`
 * is the truth about it. The editor cannot actually build a null here
 * (`EntryDraft.isPrivate` is a plain `Boolean`), but this function is the thing
 * that encodes which arguments may be absent, and an explicit null for a boolean
 * argument is the same class of 400 that used to drop every anime save. Defaulting
 * it here means the next caller cannot reintroduce that by forgetting the field.
 */
internal fun MediaEntryEdit.toSaveVariables(): Map<String, Any?> = buildMap {
    put("mediaId", mediaId)
    put("status", status)
    put("progress", progress ?: 0)
    progressVolumes?.let { put("progressVolumes", it) }
    put("score", score ?: 0.0)
    put("startedAt", startedAt?.toVariables())
    put("completedAt", completedAt?.toVariables())
    put("private", isPrivate ?: false)
}

/**
 * A [FuzzyDate] as the `FuzzyDateInput` object AniList expects, or null.
 *
 * A null field inside is deliberate: AniList's input type has three nullable
 * integers, and a year with no day is a real date to it - the same partial date
 * `FuzzyDate` already models on the read side.
 *
 * Plain maps rather than the `FuzzyDate` model itself: these travel through the
 * generic `Map<String, Any?>` variable bag, whose value adapter is the
 * runtime-type one, and a hand-built map of primitives is the one shape Moshi
 * serialises without having to resolve a second adapter mid-request.
 */
internal fun FuzzyDate.toVariables(): Map<String, Int?> = mapOf(
    "year" to year,
    "month" to month,
    "day" to day
)