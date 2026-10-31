package com.anisequel.data.repository

import android.util.Log
import com.anisequel.data.model.ActivityLikeState
import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.EntityPreview
import com.anisequel.data.model.EntityPreviewKey
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.FollowUser
import com.anisequel.data.model.GraphQLRequest
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.MessageNode
import com.anisequel.data.model.ProfileActivity
import com.anisequel.data.model.FuzzyDate
import com.anisequel.data.model.MediaCover
import com.anisequel.data.model.MediaCoverKey
import com.anisequel.data.model.MediaEntryEdit
import com.anisequel.data.model.MediaFriend
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaName
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.ReceivedMessagesData
import com.anisequel.data.model.SentMessagesData
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.UserActivityPage
import com.anisequel.data.model.UserMessagesPage
import com.anisequel.data.model.UserOverview
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.model.sortedForDisplay
import com.anisequel.data.model.toMediaFriend
import com.anisequel.data.network.AniListApiService
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.data.network.toReachability
import com.anisequel.data.network.aniListHttpError
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.data.network.parseAniListErrorBody
import com.anisequel.data.network.readCapped
import com.anisequel.data.network.RequestCoalescer
import com.anisequel.data.network.SystemNotices
import com.anisequel.data.network.toAniListException
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withTimeoutOrNull
import retrofit2.HttpException
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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
 * The caches now have a second tier. [SnapshotStore], in its own file, writes
 * what would otherwise be forgotten the moment the process dies - which is a
 * different mechanism with different failure modes (a half-written file, a file
 * from another release, a device whose clock jumped), so it lives where its
 * rules can be read on their own. What stays here is the policy: which answers
 * are worth keeping, for how long, and what a mutation invalidates.
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

/**
 * The most cover links one GraphQL document may carry.
 *
 * AniList rejects a document over its complexity limit with HTTP 400 for the
 * whole document, and covers are the one request whose failure is deliberately
 * invisible - a missing cover leaves the card on its placeholder, with nothing on
 * screen to say why. A bio can link a dozen titles and a "hall of fame" can link
 * fifty; before this cap they were all aliases in one document, so one refusal
 * left every poster in the bio permanently blank.
 *
 * Ten keeps each document comfortably inside the limit and bounds the damage of
 * a refusal to the ten cards that shared it. The rest of the bio is carried by
 * further batches.
 */
internal const val MAX_COVER_BATCH_KEYS = 10

/**
 * Rows requested per activity page.
 *
 * Passed to the query as `$perPage` rather than written into the document, so
 * the requested size and the fallback used when a response omits `pageInfo`
 * cannot drift apart: a constant that quietly disagreed with the document would
 * read a full page as short, or a short one as full.
 */
private const val ACTIVITY_PAGE_SIZE = 25

/**
 * Rows requested per message page.
 *
 * The same size as [ACTIVITY_PAGE_SIZE], but a separate constant: an inbox is
 * read in bursts and could reasonably ask for more rows than a feed that draws
 * full cards, and tying the two together would make that change a change to the
 * feed as well.
 */
private const val MESSAGE_PAGE_SIZE = 25

/** Logcat tag for the cover batches. One line, warning level, never a UI message. */
private const val COVER_LOG_TAG = "AniSequelCovers"

/**
 * The most entity links one preview document may carry.
 *
 * Equal to `GraphQLQueries.ENTITY_PREVIEW_PAGE_SIZE` by construction rather than
 * by a second constant: that value is the `perPage` the document asks for, and a
 * batch larger than it would be silently truncated by AniList. A favourites hall
 * that links twenty characters therefore spends one request, not twenty.
 */
private const val ENTITY_BATCH_KEYS = GraphQLQueries.ENTITY_PREVIEW_PAGE_SIZE

/** Logcat tag for the entity-preview batches, for the same reason as [COVER_LOG_TAG]. */
private const val ENTITY_LOG_TAG = "AniSequelPreviews"

/** [keys] split into documents of at most [MAX_COVER_BATCH_KEYS] aliases each. */
internal fun mediaCoverBatches(keys: List<MediaCoverKey>): List<List<MediaCoverKey>> =
    keys.chunked(MAX_COVER_BATCH_KEYS)

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
    private val listCacheTtlMillis: () -> Long = { RefreshInterval.DEFAULT.staleAfterMillis },

    /**
     * The disk half of these caches, or null for an in-memory-only repository.
     *
     * Null by default so a test that only cares about the network paths - and
     * the call sites that never had a store to read - keep exactly the
     * behaviour they had before there was a disk tier.
     */
    private val snapshots: SnapshotStore? = null,

    /**
     * The clock every cache reads, injectable so the windows can be tested at
     * their edges without sleeping.
     *
     * One clock for the whole class, deliberately: the memory caches and the
     * snapshots are stamped from the same reading, so "how old is this answer",
     * "has it expired", and "which snapshot is newest when the store has to
     * evict" are all answered from one timeline.
     */
    private val nowMillis: () -> Long = System::currentTimeMillis
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
         * Soft cap on cached entity previews.
         *
         * Same order as [MAX_COVER_CACHE_ENTRIES], for the same reason: a name and
         * a portrait URL are a few dozen bytes, and the same hall-of-fame cast
         * recurs across profiles.
         */
        const val MAX_ENTITY_PREVIEW_CACHE_ENTRIES = 512

        /**
         * How long the reachability probe may take before it is declared a
         * non-answer. See [checkApiReachability] for why this is local to the
         * probe rather than an OkHttp timeout.
         */
        const val API_PROBE_TIMEOUT_MILLIS = 8_000L

        /**
         * Above this, a successful probe is reported as [ApiReachability.SLOW]
         * rather than ONLINE.
         *
         * AniList's normal round trip from a phone is a few hundred
         * milliseconds. Two and a half seconds is not "slow-ish" - it is the
         * server visibly labouring, and telling the reader that is more useful
         * than a green tick that hides an impending timeout.
         */
        const val API_SLOW_PROBE_MILLIS = 2_500L

        /**
         * How many chunks of a large list the walk will chase before it gives up.
         *
         * A runaway guard, not a truncation point: the walk follows AniList's own
         * `hasNextChunk` and stops when the server says there is nothing left.
         * Reaching this ceiling means the account is bigger than one load can
         * assemble, and [fetchFullMediaListCollection] reports that as a failure.
         * It used to be a literal `currentChunk <= 10` in the loop condition,
         * which cut every list past five thousand entries down to its first ten
         * chunks and returned the remains as a success - both callers cache a
         * success for the refresh interval, so the only symptom was sequels near
         * the tail that never appeared and never went away.
         */
        const val MAX_LIST_CHUNKS = 40

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

        /**
         * Snapshot kinds: one file per kind, under the store's directory.
         *
         * Named on this side because they are this class's judgement about what is
         * worth keeping, not the store's: the store knows how to write a bounded,
         * versioned, atomic file, and nothing about AniList.
         */
        const val SNAPSHOT_LIST = "list"
        const val SNAPSHOT_OVERVIEW = "overview"
        const val SNAPSHOT_VIEWER = "viewer"
        const val SNAPSHOT_DETAIL = "detail"
        const val SNAPSHOT_COVER = "cover"

        /** The only key under [SNAPSHOT_VIEWER]: the signed-in viewer's own profile. */
        const val SNAPSHOT_VIEWER_KEY = "viewer"

        /**
         * How long the signed-in viewer's own profile may be reused.
         *
         * Thirty minutes, fixed, and deliberately **not** [listCacheTtlMillis].
         * The list's window is a user setting because the list is about noticing
         * what they finished watching; this is the request the app made *before*
         * it could even name the account, on every launch, including the ones
         * where it was killed in the browser during sign-in and immediately
         * reopened. Nobody's username, avatar or score totals change between two
         * launches a minute apart, and half an hour is a window in which a real
         * change still arrives while the user is holding the phone.
         */
        const val VIEWER_PROFILE_WINDOW_MILLIS = 30 * 60 * 1000L

        /**
         * How old a saved copy may be and still be drawn instead of a failure page.
         *
         * Thirty days, not "forever". A list that is a month old is still this
         * account's list and infinitely more useful than a screen saying AniList is
         * asleep; a list from two years ago is a different season of somebody's
         * life and would quietly claim they had not added anything since. Past this
         * the snapshot is treated as absent and the error page is the honest
         * answer.
         */
        const val STALE_FALLBACK_WINDOW_MILLIS = 30L * 24 * 60 * 60 * 1000L

        /**
         * How long a title's fetched facts may be reused, by the title's own
         * release status.
         *
         * A flat window was wrong in both directions. A finished show's synopsis,
         * cast and studios do not change, yet it was re-bought every day; a
         * currently-airing show's next-episode countdown and score move hourly,
         * yet it was held for the same day. The status is already in the detail
         * the app fetched, so it can choose:
         *
         *  - **RELEASING** (3h): the countdown and score are moving now.
         *  - **NOT_YET_RELEASED** (6h): announcements and air dates change on
         *    the scale of a day, not a week.
         *  - **FINISHED / CANCELLED** (7d): the facts are settled.
         *  - everything else, including an absent status: a day, as before.
         *
         * The airing countdown is a second, independent override: see
         * [isDetailStale]. A cached `nextAiringEpisode` whose `airingAt` has
         * passed is provably out of date regardless of how young the record is,
         * because it describes an episode that has now aired.
         */
        const val DETAIL_RELEASING_WINDOW_MILLIS = 3 * 60 * 60 * 1000L
        const val DETAIL_NOT_YET_RELEASED_WINDOW_MILLIS = 6 * 60 * 60 * 1000L
        const val DETAIL_FINISHED_WINDOW_MILLIS = 7 * 24 * 60 * 60 * 1000L
        const val DETAIL_DEFAULT_WINDOW_MILLIS = 24 * 60 * 60 * 1000L

        /**
         * The longest window any status may have.
         *
         * The disk read uses this as its TTL because the status - and therefore
         * the real window - is inside the payload. Reading with the longest
         * window and then checking [isDetailStale] against the parsed node keeps
         * the policy in one place instead of asking the store to parse first.
         */
        const val DETAIL_MAX_WINDOW_MILLIS = DETAIL_FINISHED_WINDOW_MILLIS

        /**
         * A [MediaCoverKey] as the store's flat string.
         *
         * `graphqlType` rather than `type` so the namespace is spelled the one way
         * everywhere: two spellings of `anime` would be two snapshot entries, and
         * the memory cache keyed on `(type, id)` would then answer from one while
         * the disk held the other. The separator cannot collide because the type
         * is a fixed enum name.
         */
        fun snapshotKey(key: MediaCoverKey): String = "${key.graphqlType}:${key.id}"
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
     *
     * The timestamp is what lets a day-old entry be refused. Before there was a
     * disk tier this map lasted exactly as long as the process, so an entry could
     * not outlive the launch that fetched it; now that the same answer can be
     * read back tomorrow, the memory and the file have to agree on the window -
     * see [detailWindowMillis].
     */
    private val detailCache = ConcurrentHashMap<MediaCoverKey, CachedDetail>()

    private class CachedDetail(val node: MediaNode, val storedAtMillis: Long)

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
     *
     * Stamped because the disk tier can answer for a bio reopened tomorrow; see
     * [detailWindowMillis] for the same refuse-it-when-old rule.
     */
    private val coverCache = ConcurrentHashMap<MediaCoverKey, CachedCover>()

    private class CachedCover(val cover: MediaCover, val storedAtMillis: Long)

    /**
     * Character, staff and studio previews, keyed by their own kind and id.
     *
     * Not cleared on sign-out with the list and profile caches: a public name and
     * portrait carry no session, and clearing them would only make the next
     * account's first bio re-spend the requests. Keyed on [EntityPreviewKey]
     * because a character and a staff member can share a numeric id and are two
     * different people - the same namespace collision the media caches guard.
     */
    private val entityPreviewCache = ConcurrentHashMap<EntityPreviewKey, EntityPreview>()

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
     *
     * An `AtomicInteger` rather than `@Volatile private var`: `++` on a volatile is
     * a read and a write with a gap between them, and two mutations that overlap in
     * that gap - a quick-add landing while the editor's save is still on the wire,
     * both on their own IO dispatchers - bump the same number and record one
     * invalidation. The fetch that started before *both* then matches the epoch it
     * read, and writes a list from before the writes back over the cleared cache.
     * Which is the exact failure the counter exists to make a miss.
     */
    private val listEpoch = AtomicInteger(0)

    /**
     * What [AniListRepository.listMutationVersion] publishes.
     *
     * Bumped beside [listEpoch] rather than derived from it, because the epoch is
     * a private reconciliation counter for racing fetches and this is a public
     * "a write happened" signal for screens that already loaded their data. They
     * move together today; they are not the same question.
     */
    private val _listMutationVersion = MutableStateFlow(0L)

    override val listMutationVersion: StateFlow<Long> = _listMutationVersion.asStateFlow()

    /**
     * The signed-in viewer's own profile, on a fixed window.
     *
     * The one answer every launch used to buy before it could draw anything:
     * `DashboardViewModel` cannot ask for a list without the viewer's id, and the
     * widget and the Settings header cannot name the account without the name -
     * so a cold start spent a request on data that had not changed since the last
     * one, before it could show a single row.
     *
     * Kept apart from [overviewCache] because the two are different answers and
     * different windows: `getUserOverview` is the profile screen's bio and
     * favourites for anyone, scanned or signed in, and follows the list's
     * user-chosen window; this one is fixed at
     * [VIEWER_PROFILE_WINDOW_MILLIS].
     *
     * Visible across dispatchers rather than synchronised: a fetch completes on
     * whichever dispatcher Retrofit used and the next read is on main. One
     * reference assignment is all it takes, so visibility is enough here - unlike
     * [listEpoch], which has to be atomic because every bump reads it first.
     */
    @Volatile
    private var viewerCache: CachedViewer? = null

    private class CachedViewer(val profile: ViewerProfile, val storedAtMillis: Long)

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
    private val activityCache = ConcurrentHashMap<String, CachedValue<UserActivityPage>>()
    private val followersCache = ConcurrentHashMap<String, CachedValue<List<FollowUser>>>()
    private val followingCache = ConcurrentHashMap<String, CachedValue<List<FollowUser>>>()

    /**
     * The viewer's own friends feed, keyed by page alone.
     *
     * Viewer-scoped rather than per-user: `isFollowing` resolves to whoever the
     * request's token belongs to, so there is no id in the key that could keep
     * two accounts apart. That is safe only because `clearDetailCache()` drops
     * this map on sign-out - it is followers-only data and the next account must
     * not inherit it. Kept apart from [activityCache] so opening one feed does
     * not throw away the other's pages; both share the same TTL.
     */
    private val followingActivityCache =
        ConcurrentHashMap<String, CachedValue<UserActivityPage>>()

    /**
     * Sent and received messages, kept per source.
     *
     * Two documents with two cursors: the sent list pages through
     * `Page.activities`, the received list through `Page.notifications`, and a
     * page numbered 2 in one says nothing about page 2 in the other. One cache
     * would need a source in its key anyway, so two maps make the separation
     * impossible to get wrong.
     */
    private val sentMessagesCache =
        ConcurrentHashMap<String, CachedValue<UserMessagesPage>>()
    private val receivedMessagesCache =
        ConcurrentHashMap<String, CachedValue<UserMessagesPage>>()

    /**
     * Followed users' entries per title, keyed by namespace and id.
     *
     * Namespaced for the same reason [detailCache] is: `anime/1` and `manga/1` are
     * different titles sharing a number, so a key of `media:1:friends` would serve
     * a manga's readers out of the anime's entries. The window is the same
     * `listCacheTtlMillis` as every other cached read - a friend's progress moves
     * slowly, and re-asking on every open of the same title is a request the
     * budget cannot afford.
     */
    private val friendsCache = ConcurrentHashMap<String, CachedValue<List<MediaFriend>>>()

    /**
     * One cached answer, whatever its shape - a page, or a list of users.
     *
     * This held `List<T>` until 1.6.0, which is why the activity feed could not
     * cache its own answer: a `UserActivityPage` is a page *and* AniList's
     * `hasNextPage`, and caching only the rows threw the pagination answer away,
     * so a cached page would have ended the feed wherever the cache happened to
     * stop. Keeping `T` lets the page be cached whole and lets the follower lists
     * stay lists, without two caches and two copies of the TTL rule.
     */
    private class CachedValue<T>(val value: T, val storedAtMillis: Long)

    /**
     * A cached read that is not a media list.
     *
     * Same window and same reasoning as [cachedList]; separate because the key and
     * the payload differ. `listCacheTtlMillis` is read per lookup, so a refresh
     * interval changed in Settings applies to the very next request rather than to
     * whatever was in force when this repository was built.
     */
    private fun <T> cachedValue(cache: ConcurrentHashMap<String, CachedValue<T>>, key: String): T? {
        val entry = cache[key] ?: return null
        if (nowMillis() - entry.storedAtMillis > listCacheTtlMillis()) {
            cache.remove(key)
            return null
        }
        return entry.value
    }

    private fun <T> storeValue(
        cache: ConcurrentHashMap<String, CachedValue<T>>,
        key: String,
        value: T
    ) {
        cache[key] = CachedValue(value, nowMillis())
    }

    private fun cachedOverview(key: String): UserOverview? {
        val entry = overviewCache[key] ?: return null
        val ttl = listCacheTtlMillis()
        if (nowMillis() - entry.storedAtMillis > ttl) {
            overviewCache.remove(key)
            return null
        }
        return entry.overview
    }

    /**
     * The profile held for [key]: memory first, then [snapshots].
     *
     * A snapshot goes into memory stamped with its **own** time, never with now.
     * Stamping a read as a write is how a cache stops expiring - the entry would
     * be renewed by every lookup, so a profile fetched last week would keep
     * answering as long as somebody opened the screen now and then.
     *
     * The disk read happens once per key per process in practice: a hit lands in
     * [overviewCache] above, so the second caller of the same profile is a map
     * lookup again.
     */
    private suspend fun knownOverview(key: String): UserOverview? {
        cachedOverview(key)?.let { return it }
        val stored = snapshots?.read(
            kind = SNAPSHOT_OVERVIEW,
            key = key,
            type = UserOverview::class.java,
            ttlMillis = listCacheTtlMillis()
        ) ?: return null
        overviewCache[key] = CachedOverview(stored.value, stored.storedAtMillis)
        return stored.value
    }

    private fun cachedList(key: String): MediaListCollection? {
        val entry = listCache[key] ?: return null
        val ttl = listCacheTtlMillis()
        if (nowMillis() - entry.storedAtMillis > ttl) {
            listCache.remove(key)
            return null
        }
        return entry.collection
    }

    /**
     * The list held for [key], on the same terms as [knownOverview].
     *
     * This is the call that makes "close the app and open it again" cost nothing:
     * a list snapshotted twenty minutes ago is served from disk, keeps its
     * original timestamp, and the freshness watch upstream decides from *that*
     * whether another fetch is due.
     */
    private suspend fun knownList(key: String): MediaListCollection? {
        cachedList(key)?.let { return it }
        val stored = snapshots?.read(
            kind = SNAPSHOT_LIST,
            key = key,
            type = MediaListCollection::class.java,
            ttlMillis = listCacheTtlMillis()
        ) ?: return null
        listCache[key] = CachedList(stored.value, stored.storedAtMillis)
        return stored.value
    }

    /**
     * Stores [collection] for [key] in both tiers, from one clock reading.
     *
     * One reading on purpose: a launch that follows must not be able to decide
     * the answer is older - or younger - than the launch that fetched it did.
     */
    private suspend fun storeList(key: String, collection: MediaListCollection) {
        val storedAt = nowMillis()
        listCache[key] = CachedList(collection, storedAt)
        snapshots?.write(SNAPSHOT_LIST, key, MediaListCollection::class.java, collection, storedAt)
    }

    /**
     * Drops everything a list write has made stale, in both tiers.
     *
     * Called by every mutation. The epoch bump plus [listCache].clear() is what it
     * always did; the snapshot has to go with them, or the next lookup would read
     * the pre-mutation list straight back off disk and the very thing the
     * invalidation exists to prevent would be served to the dashboard.
     *
     * It also has to reach three caches it used to leave alone:
     *
     *  - [detailCache], because a detail node carries the viewer's
     *    `mediaListEntry`. A title added to Planning from one screen was served
     *    from the cache to the next screen with no entry at all, so its title page
     *    offered to add it *again*. The disk tier is repaired against the list
     *    when it is read, so dropping the memory tier is enough.
     *  - [activityCache], because the feed now has the entry the write created.
     *    Leaving it is what made "added to Planning" invisible on the Activity
     *    tab until the profile was reopened.
     *  - [overviewCache] and its snapshot: the profile's stats total a list that
     *    just changed, and a progress edit moves them.
     *
     * [_listMutationVersion] is what tells a screen that already loaded its data -
     * the Activity feed, in particular - to fetch again; the caches above only
     * help the next screen that asks.
     */
    private suspend fun invalidateLists() {
        listEpoch.incrementAndGet()
        listCache.clear()
        detailCache.clear()
        activityCache.clear()
        overviewCache.clear()
        snapshots?.removeKind(SNAPSHOT_LIST)
        snapshots?.removeKind(SNAPSHOT_OVERVIEW)
        _listMutationVersion.update { it + 1 }
    }

    /**
     * The signed-in viewer, answered from [viewerCache] or disk when it is fresh.
     *
     * Everything that has to know *who* is signed in goes through here - the
     * dashboard needs the id before it can ask for a list, the widget and the
     * Settings header need the name - so this is the one call whose caching shows
     * on every cold start rather than only on a screen the user reopens.
     *
     * A note for callers using it as a *token check*: it can answer without a
     * request, so it can vouch for a session AniList has since refused.
     * `AuthViewModel.saveToken` clears the caches before it leans on this;
     * nothing else should treat it as proof that a token works.
     */
    override suspend fun getViewer(): Result<ViewerProfile> {
        knownViewer()?.let { return Result.success(it) }
        return execute(GraphQLRequest(query = GraphQLQueries.GET_VIEWER), apiService::getViewer)
            .mapRequire { response ->
                val profile = response.viewer.require("Viewer data not found")
                storeViewer(profile)
                profile
            }
    }

    /**
     * One tiny query, classified by the same [execute] every real request uses.
     *
     * ## Why it cannot just check `isConnected`
     *
     * A connected Wi-Fi radio proves nothing about AniList: captive portals,
     * DNS failures, a blocked host and a 500 all have a perfectly healthy
     * socket underneath them. This asks the actual API and reports what the
     * actual API did, which is the only definition of "reachable" the status
     * card is allowed to use.
     *
     * ## Why the timeout is here rather than on the OkHttp client
     *
     * The app's client is configured for real list queries - generous read
     * timeouts so a 476-entry account can finish. A probe that inherits those
     * would leave "Checking..." on screen for half a minute, which reads as a
     * frozen app. Eight seconds is long past any healthy round trip and short
     * enough that the dialog can still be cancelled by a human.
     *
     * `withTimeoutOrNull` rather than `withTimeout`: the timeout cancels
     * [execute]'s coroutine, and [execute] deliberately rethrows cancellation,
     * so a raw timeout would propagate as a cancellation of the *caller*.
     * Returning null keeps the distinction between "AniList said no" and "we
     * stopped waiting" explicit.
     */
    override suspend fun checkApiReachability(): ApiStatusSnapshot {
        val startedAt = nowMillis()
        val result = withTimeoutOrNull(API_PROBE_TIMEOUT_MILLIS) {
            execute(GraphQLRequest(query = GraphQLQueries.API_STATUS), apiService::ping)
        }
        val elapsed = nowMillis() - startedAt
        if (result == null) {
            return ApiStatusSnapshot(reachability = ApiReachability.NO_ANSWER)
        }
        return result.fold(
            onSuccess = {
                ApiStatusSnapshot(
                    reachability = if (elapsed >= API_SLOW_PROBE_MILLIS) {
                        ApiReachability.SLOW
                    } else {
                        ApiReachability.ONLINE
                    },
                    latencyMillis = elapsed
                )
            },
            onFailure = { error ->
                ApiStatusSnapshot(reachability = (error as? AniListException)?.kind.toReachability())
            }
        )
    }

    /** Memory half of [knownViewer]; see [VIEWER_PROFILE_WINDOW_MILLIS]. */
    private fun cachedViewer(): ViewerProfile? {
        val entry = viewerCache ?: return null
        if (nowMillis() - entry.storedAtMillis > VIEWER_PROFILE_WINDOW_MILLIS) {
            viewerCache = null
            return null
        }
        return entry.profile
    }

    private suspend fun knownViewer(): ViewerProfile? {
        cachedViewer()?.let { return it }
        val stored = snapshots?.read(
            kind = SNAPSHOT_VIEWER,
            key = SNAPSHOT_VIEWER_KEY,
            type = ViewerProfile::class.java,
            ttlMillis = VIEWER_PROFILE_WINDOW_MILLIS
        ) ?: return null
        viewerCache = CachedViewer(stored.value, stored.storedAtMillis)
        return stored.value
    }

    /**
     * The viewer from memory at any age, for [lastKnownViewer].
     *
     * Deliberately not [cachedViewer]: that one treats an old entry as absent, and
     * the whole point here is to answer with it anyway. The timestamp is kept, so
     * the freshness watch upstream can still tell how old the copy is.
     */
    private fun viewerAnyAge(): ViewerProfile? = viewerCache?.profile

    override suspend fun lastKnownViewer(): ViewerProfile? {
        viewerAnyAge()?.let { return it }
        val stored = snapshots?.read(
            kind = SNAPSHOT_VIEWER,
            key = SNAPSHOT_VIEWER_KEY,
            type = ViewerProfile::class.java,
            ttlMillis = STALE_FALLBACK_WINDOW_MILLIS
        ) ?: return null
        viewerCache = CachedViewer(stored.value, stored.storedAtMillis)
        return stored.value
    }

    override suspend fun lastKnownOverview(userId: Int?, userName: String?): UserOverview? {
        val key = overviewCacheKey(userId, userName)
        overviewCache[key]?.let { return it.overview }
        val stored = snapshots?.read(
            kind = SNAPSHOT_OVERVIEW,
            key = key,
            type = UserOverview::class.java,
            ttlMillis = STALE_FALLBACK_WINDOW_MILLIS
        ) ?: return null
        overviewCache[key] = CachedOverview(stored.value, stored.storedAtMillis)
        return stored.value
    }

    override suspend fun lastKnownAnimeList(userId: Int): MediaListCollection? =
        lastKnownList("user:$userId")

    override suspend fun lastKnownAnimeListByUsername(userName: String): MediaListCollection? =
        lastKnownList("userName:${userName.trim().lowercase(Locale.ROOT)}")

    /**
     * The list held for [key], however old - the one read that ignores the window.
     *
     * The memory tier is consulted first because a fetch that has already happened
     * this session is fresher than the file, and because the file read parses the
     * whole multi-megabyte payload. Either way the entry keeps its own timestamp,
     * so nothing here extends a cache's life.
     */
    private suspend fun lastKnownList(key: String): MediaListCollection? {
        listCache[key]?.let { return it.collection }
        val stored = snapshots?.read(
            kind = SNAPSHOT_LIST,
            key = key,
            type = MediaListCollection::class.java,
            ttlMillis = STALE_FALLBACK_WINDOW_MILLIS
        ) ?: return null
        listCache[key] = CachedList(stored.value, stored.storedAtMillis)
        return stored.value
    }

    private suspend fun storeViewer(profile: ViewerProfile) {
        val storedAt = nowMillis()
        viewerCache = CachedViewer(profile, storedAt)
        snapshots?.write(SNAPSHOT_VIEWER, SNAPSHOT_VIEWER_KEY, ViewerProfile::class.java, profile, storedAt)
    }

    override suspend fun getUserByName(userName: String): Result<ViewerProfile> {
        val name = userName.trim()
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_BY_NAME,
                variables = mapOf("userName" to name)
            ),
            apiService::getUserByName
        ).mapRequire { it.user.require("No AniList user called \"$name\".") }
    }

    override suspend fun getUserAnimeList(
        userId: Int,
        forceRefresh: Boolean
    ): Result<MediaListCollection> {
        val key = "user:$userId"
        if (!forceRefresh) knownList(key)?.let { return Result.success(it) }

        val epoch = listEpoch.get()
        return fetchFullMediaListCollection(userId = userId, userName = null)
            .rejectUnexpectedEmpty(key)
            .onSuccess { if (epoch == listEpoch.get()) storeList(key, it) }
    }

    /**
     * The user's manga list, cached under its own key.
     *
     * Separate from [getUserAnimeList] because AniList's `MediaType` is a filter on
     * the same query rather than a different resource, so the two results never
     * overlap - sharing a cache key would let whichever list loaded last answer for
     * the other, and the Library page would silently show anime under a Manga filter.
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
        if (!forceRefresh) knownList(key)?.let { return Result.success(it) }

        val epoch = listEpoch.get()
        return fetchFullMediaListCollection(userId = userId, userName = null, listType = LIST_TYPE_MANGA)
            .rejectUnexpectedEmpty(key)
            .onSuccess { if (epoch == listEpoch.get()) storeList(key, it) }
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
        if (!forceRefresh) knownList(key)?.let { return Result.success(it) }

        val epoch = listEpoch.get()
        return fetchFullMediaListCollection(userId = null, userName = userName.trim())
            .rejectUnexpectedEmpty(key)
            .onSuccess { if (epoch == listEpoch.get()) storeList(key, it) }
    }

    /**
     * Refuses an empty list that would replace a populated one.
     *
     * AniList's throttling and its edge failures both answer a list query with
     * a well-formed collection containing no entries. Storing that is how a 429
     * becomes "the user deleted everything": the dashboard's snapshot is
     * replaced by an empty account, every sequel disappears, and the next launch
     * serves the emptiness from disk too. An empty answer is still a valid
     * answer for an account that has never tracked anything, so it is only
     * refused when this repository already holds a populated list under the same
     * key - including an expired one, because expiry is a reason to re-fetch,
     * not evidence that the list is empty.
     *
     * Only the list this process is *holding* counts. A disk snapshot is not
     * proof: a fresh launch that is answered "empty" may be a reader who removed
     * their last title, and that answer has to be allowed to land. What the
     * guard is against is a throttled answer replacing a list the app is already
     * reading in this session, and that is exactly the case a fresh launch is
     * not in.
     */
    private suspend fun Result<MediaListCollection>.rejectUnexpectedEmpty(
        key: String
    ): Result<MediaListCollection> {
        val response = getOrElse { return this }
        if (!response.isEmptyCollection()) return this

        val previous = listCache[key]?.collection ?: return this

        if (previous.isEmptyCollection()) return this

        return Result.failure(
            AniListException(
                kind = AniListErrorKind.UNKNOWN,
                message = "AniList returned an empty list even though yours has entries. " +
                    "Nothing was replaced - try again in a moment."
            )
        )
    }

    private fun MediaListCollection.isEmptyCollection(): Boolean =
        lists.orEmpty().all { it.entries.isNullOrEmpty() }

    /**
     * Fetches complete MediaListCollection, supporting AniList chunking for large lists.
     *
     * [listType] is the GraphQL `MediaType` enum name. It is a parameter rather
     * than a hardcoded `ANIME` because the Library page needs the manga half of the
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
        ).mapRequire { it.collection.require("Media list collection is empty") }

        val firstCollection = firstResult.getOrElse { return Result.failure(it) }
        if (firstCollection.hasNextChunk != true) {
            return Result.success(firstCollection)
        }

        // Multi-chunk list merge for accounts spanning multiple chunks
        val allLists = firstCollection.lists?.map { it.copy(entries = it.entries?.toMutableList()) }?.toMutableList()
            ?: mutableListOf()
        var currentChunk = 2
        var hasNext = true

        while (hasNext && currentChunk <= MAX_LIST_CHUNKS) {
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
            ).mapRequire { it.collection.require("Media list collection chunk $currentChunk is empty") }

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

        // A walk that stopped because it ran out of ceiling, while the server still
        // had chunks to give, is an *incomplete* list. Returning it as a success is
        // what used to happen at chunk ten: the callers cache a success for the
        // refresh interval, so the missing tail looked identical to a complete
        // account and stayed that way until the cache expired. A reader is better
        // served by "it did not load" than by an account quietly missing its end.
        if (hasNext) {
            return Result.failure(
                AniListException(
                    kind = AniListErrorKind.UNKNOWN,
                    message = "This AniList list is too large for AniSequel to load in one go."
                )
            )
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
        knownDetail(cacheKey)?.let { return Result.success(it) }
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.getMediaDetailQuery(mediaType),
                variables = buildMap {
                    put("id", mediaId)
                    mediaType?.let { put("type", it.uppercase(Locale.ROOT)) }
                }
            ),
            apiService::getMediaDetail
        ).mapRequire {
            val node = it.media.require("That entry is no longer available on AniList.")
            if (detailCache.size >= MAX_DETAIL_CACHE_NODES) {
                // Soft cap: falling back to a fresh fetch on the next open
                // is cheaper than holding every full cast/studio tree the
                // user has ever tapped.
                detailCache.clear()
            }
            storeDetail(cacheKey, node)
            node
        }
    }

    /** Memory half of [knownDetail], on the status-keyed window below. */
    private fun cachedDetail(key: MediaCoverKey): MediaNode? {
        val entry = detailCache[key] ?: return null
        if (isDetailStale(entry.node, entry.storedAtMillis)) {
            detailCache.remove(key)
            return null
        }
        return entry.node
    }

    /**
     * The window [node]'s own status earns, in milliseconds.
     *
     * `uppercase(Locale.ROOT)` rather than the device locale for the same reason
     * the list cache key uses it: status names are API tokens, not display text,
     * and a Turkish locale would turn `RELEASING`'s `I` into a dotless one.
     */
    private fun detailWindowMillis(node: MediaNode): Long =
        when (node.status?.uppercase(Locale.ROOT)) {
            "RELEASING" -> DETAIL_RELEASING_WINDOW_MILLIS
            "NOT_YET_RELEASED" -> DETAIL_NOT_YET_RELEASED_WINDOW_MILLIS
            "FINISHED", "CANCELLED" -> DETAIL_FINISHED_WINDOW_MILLIS
            else -> DETAIL_DEFAULT_WINDOW_MILLIS
        }

    /**
     * Whether a cached detail is stale, by either clock that applies.
     *
     * The age check is the status-keyed window. The countdown check is the one
     * that cannot be expressed as a TTL: an episode that was an hour away when
     * the record was written has aired since, so the record's "next episode" is
     * already wrong even though its window has barely started. `airingAt` is
     * seconds, the app's clock is milliseconds.
     */
    private fun isDetailStale(node: MediaNode, storedAtMillis: Long): Boolean {
        if (nowMillis() - storedAtMillis > detailWindowMillis(node)) return true
        val airingAtSeconds = node.nextAiringEpisode?.airingAt ?: return false
        return nowMillis() >= airingAtSeconds * 1000L
    }

    /**
     * The detail held for [key]: memory first, then [snapshots].
     *
     * This is what makes reopening the app and tapping a title cost nothing. The
     * synopsis, cast, studios, release year and relations the sheet draws are
     * already on disk, written by a launch that had them on screen. Past the
     * status-keyed window, or past the cached episode's airing time, the fetch
     * happens again - so the fields that do move are refreshed while settled ones
     * are not re-bought on every launch in between. What comes off disk is passed
     * through [repairedViewerEntry] first, because its viewer-scoped entry may be
     * old.
     *
     * The store is read with [DETAIL_MAX_WINDOW_MILLIS] and the real window is
     * checked after parsing, because the status that chooses it lives inside the
     * payload. A node past its own window is refused here rather than deleted:
     * the next successful fetch overwrites it, and deleting would cost a file
     * rewrite on a read.
     */
    private suspend fun knownDetail(key: MediaCoverKey): MediaNode? {
        cachedDetail(key)?.let { return it }
        val stored = snapshots?.read(
            kind = SNAPSHOT_DETAIL,
            key = snapshotKey(key),
            type = MediaNode::class.java,
            ttlMillis = DETAIL_MAX_WINDOW_MILLIS
        ) ?: return null
        if (isDetailStale(stored.value, stored.storedAtMillis)) return null
        val node = repairedViewerEntry(stored.value, key)
        detailCache[key] = CachedDetail(node, stored.storedAtMillis)
        return node
    }

    /**
     * A snapshot's viewer-scoped field, corrected against the list in hand.
     *
     * The entry is the one part of a detail node that belongs to the viewer
     * rather than to the title, and the one part that keeps moving while the
     * title sits still - so a snapshot written yesterday carries yesterday's
     * progress, and serving it raw would put a stale number in the sheet while
     * the row behind it draws today's. Everything else in the node - synopsis,
     * cast, studios, release year, relations - is the same answer it was, which
     * is why the node is worth keeping at all.
     *
     * The viewer's own list is the authority whenever it is in hand: present in
     * it, the entry is replaced with the list's copy; absent from it, the title
     * is not on their list any more (it was deleted) and the snapshot's old
     * entry is dropped rather than drawn as tracked. When no list is in hand
     * there is nothing better to say, so the snapshot's own entry is left
     * alone - it is still the progress the user last set, one launch behind
     * rather than missing.
     *
     * Only the viewer's **own** list is consulted, and by id: a scanned
     * profile's list holds *that person's* progress on the same title, and "you
     * are six episodes in" versus "they are six episodes in" is the entire
     * reason this reads the signed-in id off [viewerCache] rather than taking
     * the first list it can find.
     */
    private suspend fun repairedViewerEntry(node: MediaNode, key: MediaCoverKey): MediaNode {
        // The memory cache, never a fetch: a title being opened is not a reason
        // to spend a request on the viewer's profile, and the dashboard has
        // already put it there on every launch that reaches a sheet.
        val viewerId = cachedViewer()?.id ?: return node
        val lists = viewerListsInHand(viewerId, key.graphqlType)
        if (lists.isEmpty()) return node

        val entry = lists.firstNotNullOfOrNull { collection ->
            collection.lists.orEmpty()
                .flatMap { it.entries.orEmpty() }
                .firstNotNullOfOrNull { item ->
                    if (item.media.id != key.id) {
                        null
                    } else {
                        SimpleMediaListEntry(
                            id = item.id,
                            status = item.status,
                            mediaId = item.media.id,
                            progress = item.progress,
                            score = item.score
                        )
                    }
                }
        }
        // An untyped open can be a title whose list this app has not fetched, so
        // "not in the list I hold" is not "not on their list" there.
        if (entry == null && key.type == UNSPECIFIED_MEDIA_TYPE) return node
        return node.copy(mediaListEntry = entry)
    }

    /** The viewer's own lists for [type] that are already in hand, anime first. */
    private suspend fun viewerListsInHand(viewerId: Int, type: String): List<MediaListCollection> {
        val keys = when (type) {
            LIST_TYPE_MANGA -> listOf("user:$viewerId:manga")
            LIST_TYPE_ANIME -> listOf("user:$viewerId")
            else -> listOf("user:$viewerId", "user:$viewerId:manga")
        }
        return keys.mapNotNull { key -> cachedList(key) ?: knownList(key) }
    }

    private suspend fun storeDetail(key: MediaCoverKey, node: MediaNode) {
        val storedAt = nowMillis()
        detailCache[key] = CachedDetail(node, storedAt)
        // The viewer's entry goes into the snapshot too, rather than being
        // stripped: it is the fallback for a launch that has no list in hand to
        // correct it from. Reading it back goes through [repairedViewerEntry],
        // which prefers the live list whenever there is one.
        snapshots?.write(SNAPSHOT_DETAIL, snapshotKey(key), MediaNode::class.java, node, storedAt)
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
        ).mapRequire {
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
            val known = knownCover(key)
            if (known != null) resolved[key] = known else missing += key
        }
        if (missing.isEmpty()) return Result.success(resolved)

        // One alias per key in each batch, so the response is read back by
        // position - two keys sharing an id would otherwise need two aliases on
        // the same field. Batches rather than one document: see
        // [MAX_COVER_BATCH_KEYS].
        val fresh = LinkedHashMap<MediaCoverKey, MediaCover>()
        var anyAccepted = false
        var firstFailure: Throwable? = null

        mediaCoverBatches(missing).forEach { batch ->
            val variables = buildMap {
                batch.forEachIndexed { index, key ->
                    put("id$index", key.id)
                    put("type$index", key.graphqlType)
                }
            }
            execute(
                GraphQLRequest(
                    query = GraphQLQueries.getMediaCoversQuery(batch),
                    variables = variables
                ),
                apiService::getMediaCovers
            ).fold(
                onSuccess = { byAlias ->
                    anyAccepted = true
                    batch.forEachIndexed { index, key ->
                        // `firstOrNull` rather than a single node: the batch asks
                        // `Page.media` precisely so an id with no media is an empty
                        // list instead of a 404 that fails the other eleven cards.
                        // See [MediaCoverPage].
                        val node = byAlias[GraphQLQueries.mediaCoverAlias(index)]
                            ?.media
                            ?.firstOrNull()
                        val url = node?.coverImage?.bestUrl
                        if (url != null) {
                            // The key comes from the request rather than from
                            // `node.type`. They should agree, but if AniList ever
                            // answers with a different type than the one asked for,
                            // the card has to find the cover under the key it
                            // requested it by or it draws the placeholder over a
                            // request it already paid for.
                            val cover = MediaCover(
                                url = url,
                                // The real title, which the query asks for on every
                                // entry. A bio link of `anilist.co/anime/1` has no
                                // readable name in it, so this is the only thing that
                                // can label the card with anything but the type and a
                                // number.
                                title = node.title?.bestTitleOrNull
                            )
                            if (coverCache.size >= MAX_COVER_CACHE_ENTRIES) coverCache.clear()
                            fresh[key] = cover
                            resolved[key] = cover
                        }
                    }
                },
                onFailure = { error ->
                    if (firstFailure == null) firstFailure = error
                    // A warning rather than an error banner: the reader cannot act
                    // on "AniList refused a cover batch", and the cards are already
                    // handling the absence. The line exists so the next occurrence
                    // is diagnosable from logcat instead of merely quiet.
                    Log.w(
                        COVER_LOG_TAG,
                        "AniList refused a cover batch of ${batch.size}",
                        error
                    )
                }
            )
        }

        // One store call for the whole response: covers are the one cache the app
        // fills a dozen entries at a time, and a rewrite per entry would serialize
        // this kind a dozen times for one bio.
        storeCovers(fresh)
        // A media with no cover is absent from the answer rather than an error: the
        // caller draws a placeholder, and one missing poster must not blank the
        // other eleven cards beside it. A partial answer is therefore a success -
        // only a response where every batch was refused is reported as a failure.
        val failure = firstFailure
        return if (!anyAccepted && failure != null) {
            Result.failure(failure)
        } else {
            Result.success(resolved)
        }
    }

    override suspend fun getEntityPreviews(
        keys: List<EntityPreviewKey>
    ): Result<Map<EntityPreviewKey, EntityPreview>> {
        if (keys.isEmpty()) return Result.success(emptyMap())

        val resolved = LinkedHashMap<EntityPreviewKey, EntityPreview>()
        val missing = mutableListOf<EntityPreviewKey>()
        keys.distinct().forEach { key ->
            // A key this request cannot answer for is dropped rather than sent:
            // asking the character root for a media id would draw a stranger's
            // portrait on the card, and an unknown type would fail the document.
            if (key.graphqlType !in EntityPreviewKey.SUPPORTED_TYPES) return@forEach
            val known = entityPreviewCache[key]
            if (known != null) resolved[key] = known else missing += key
        }
        if (missing.isEmpty()) return Result.success(resolved)

        var anyAccepted = false
        var firstFailure: Throwable? = null

        missing.chunked(ENTITY_BATCH_KEYS).forEach { batch ->
            // One root per type, so twenty characters cost one field and one
            // variable rather than twenty aliases.
            val groups = batch.groupBy { it.graphqlType }
            val variables = buildMap<String, Any> {
                groups.forEach { (type, group) ->
                    put(GraphQLQueries.entityPreviewVariable(type), group.map { it.id })
                }
            }
            execute(
                GraphQLRequest(
                    query = GraphQLQueries.getEntityPreviewsQuery(groups.keys),
                    variables = variables
                ),
                apiService::getEntityPreviews
            ).fold(
                onSuccess = { pages ->
                    anyAccepted = true
                    groups.forEach { (type, group) ->
                        val found: Map<Int, EntityPreview> = when (type) {
                            "CHARACTER" -> pages.characters?.characters.orEmpty()
                                .associate { profile ->
                                    profile.id to EntityPreview(
                                        name = profile.name?.previewName(),
                                        imageUrl = profile.image?.bestUrl
                                    )
                                }

                            "STAFF" -> pages.staff?.staff.orEmpty()
                                .associate { profile ->
                                    profile.id to EntityPreview(
                                        name = profile.name?.previewName(),
                                        imageUrl = profile.image?.bestUrl
                                    )
                                }

                            // A studio has no portrait on AniList, and a novel
                            // with no id is not a row this batch can match.
                            "STUDIO" -> pages.studios?.studios.orEmpty()
                                .mapNotNull { studio ->
                                    studio.id?.let { it to EntityPreview(name = studio.name) }
                                }
                                .toMap()

                            else -> emptyMap()
                        }
                        group.forEach { key ->
                            val preview = found[key.id] ?: return@forEach
                            if (entityPreviewCache.size >= MAX_ENTITY_PREVIEW_CACHE_ENTRIES) {
                                entityPreviewCache.clear()
                            }
                            entityPreviewCache[key] = preview
                            resolved[key] = preview
                        }
                    }
                },
                onFailure = { error ->
                    if (firstFailure == null) firstFailure = error
                    // Silent on screen, like the cover batches: a missing preview
                    // leaves the card with its label and type icon, which is a
                    // complete card. The line exists for logcat.
                    Log.w(
                        ENTITY_LOG_TAG,
                        "AniList refused an entity preview batch of ${batch.size}",
                        error
                    )
                }
            )
        }

        // Same contract as the covers: a partial answer is a success, because each
        // card is independently useful; only a wholly refused document is a
        // failure the caller can retry.
        val failure = firstFailure
        return if (!anyAccepted && failure != null) {
            Result.failure(failure)
        } else {
            Result.success(resolved)
        }
    }

    /** The name a preview card draws: AniList's full form, then native, then an alias. */
    private fun MediaName.previewName(): String? =
        full?.takeIf { it.isNotBlank() }
            ?: native?.takeIf { it.isNotBlank() }
            ?: alternative?.firstOrNull { it.isNotBlank() }

    /** Memory half of [knownCover], on the list's window. */
    private fun cachedCover(key: MediaCoverKey): MediaCover? {
        val entry = coverCache[key] ?: return null
        if (nowMillis() - entry.storedAtMillis > listCacheTtlMillis()) {
            coverCache.remove(key)
            return null
        }
        return entry.cover
    }

    /**
     * The cover held for [key]: memory first, then [snapshots].
     *
     * A URL and a title are a few hundred bytes, and the *image* behind them is
     * already on disk in Coil's cache - so a bio reopened the next day draws its
     * posters from two files that are both still there, with no request for
     * either. The window follows the list's, since this is fetched as decoration
     * for a profile that itself follows that window.
     */
    private suspend fun knownCover(key: MediaCoverKey): MediaCover? {
        cachedCover(key)?.let { return it }
        val stored = snapshots?.read(
            kind = SNAPSHOT_COVER,
            key = snapshotKey(key),
            type = MediaCover::class.java,
            ttlMillis = listCacheTtlMillis()
        ) ?: return null
        coverCache[key] = CachedCover(stored.value, stored.storedAtMillis)
        return stored.value
    }

    private suspend fun storeCovers(covers: Map<MediaCoverKey, MediaCover>) {
        if (covers.isEmpty()) return
        val storedAt = nowMillis()
        covers.forEach { (key, cover) -> coverCache[key] = CachedCover(cover, storedAt) }
        snapshots?.writeAll(
            kind = SNAPSHOT_COVER,
            values = covers.entries.associate { snapshotKey(it.key) to it.value },
            type = MediaCover::class.java,
            storedAtMillis = storedAt
        )
    }

    override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.ADD_TO_PLANNING,
                variables = mapOf("mediaId" to mediaId)
            ),
            apiService::saveMediaListEntry
        ).mapRequire { it.entry.require("No response from planning mutation") }
            // The completed list has one more "planned" entry than the copy
            // this add just made. Both cache keys (`user:` for the signed-in
            // query, `userName:` for the public one) can be holding the
            // same franchise's entry as "missed", so the safest correct move
            // is dropping them rather than discovering the gap entry it
            // added still shows as a missed sequel on the next list recompute.
            // Bumping the epoch first also invalidates any fetch already in
            // flight, so it cannot repopulate the cache with the pre-add list.
            .onSuccess {
                invalidateLists()
            }

    override suspend fun addToWatching(mediaId: Int): Result<SimpleMediaListEntry> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.ADD_TO_WATCHING,
                variables = mapOf("mediaId" to mediaId)
            ),
            apiService::saveMediaListEntry
        ).mapRequire { it.entry.require("No response from watching mutation") }
            .onSuccess {
                invalidateLists()
            }

    /**
     * Reads the viewer's entry, and the account's score format with it.
     *
     * The format is attached to the entry rather than returned separately so the
     * editor has one object to build its form from - see
     * [SimpleMediaListEntry.scoreFormat]. It is also what makes a title with *no*
     * entry yet draw the right slider: the empty entry below still carries the
     * format, so a hundred-point account creating an entry for the first time
     * gets a 0-100 control instead of the old 0-10 guess.
     */
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
        ).map { data ->
            val scoreFormat = data.viewer?.mediaListOptions?.scoreFormat
            (data.entry ?: SimpleMediaListEntry(mediaId = mediaId)).copy(scoreFormat = scoreFormat)
        }
    }

    override suspend fun saveMediaListEntry(edit: MediaEntryEdit): Result<SimpleMediaListEntry> {
        return execute(
            GraphQLRequest(
                query = GraphQLQueries.SAVE_MEDIA_LIST_ENTRY,
                variables = edit.toSaveVariables()
            ),
            apiService::saveMediaListEntry
        ).mapRequire { it.entry.require("No response from the entry mutation") }
            .onSuccess {
                // The cached list now holds the pre-edit entry. Dropping it is what
                // makes the dashboard's own counts and the Library row agree with
                // the form the user just saved, rather than the copy from before.
                invalidateLists()
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
                invalidateLists()
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
        if (!forceRefresh) knownOverview(key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_OVERVIEW,
                variables = mapOf(
                    "userId" to userId,
                    "userName" to userName?.trim()
                )
            ),
            apiService::getUserOverview
        ).mapRequire { data ->
            val overview = data.user.require(
                userName?.let { "No AniList user called \"$it\"." }
                    ?: "AniList returned no profile for this account."
            )
            storeOverview(key, overview)
            overview
        }
    }

    private suspend fun storeOverview(key: String, overview: UserOverview) {
        val storedAt = nowMillis()
        overviewCache[key] = CachedOverview(overview, storedAt)
        snapshots?.write(SNAPSHOT_OVERVIEW, key, UserOverview::class.java, overview, storedAt)
    }

    override fun getDemoProfile(): ViewerProfile = DemoDataProvider.getDemoViewer()

    override fun getDemoAnimeList(): MediaListCollection = DemoDataProvider.getDemoMediaList()

    override fun getDemoUserOverview(): UserOverview = DemoProfileProvider.getDemoUserOverview()

    override fun getDemoUserActivity(): List<ProfileActivity> = DemoProfileProvider.getDemoActivity()

    override fun getDemoFollowers(): List<FollowUser> = DemoProfileProvider.getDemoFollowers()

    override fun getDemoFollowing(): List<FollowUser> = DemoProfileProvider.getDemoFollowing()

    override fun getDemoFollowingActivity(): List<ProfileActivity> =
        DemoProfileProvider.getDemoFollowingActivity()

    override fun getDemoMessages(): List<MessageNode> = DemoProfileProvider.getDemoMessages()

    override suspend fun getUserActivity(
        userId: Int,
        page: Int,
        forceRefresh: Boolean
    ): Result<UserActivityPage> {
        val key = "user:$userId:page:$page"
        if (!forceRefresh) cachedValue(activityCache, key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_ACTIVITY,
                variables = mapOf(
                    "userId" to userId,
                    "page" to page,
                    "perPage" to ACTIVITY_PAGE_SIZE
                )
            ),
            apiService::getUserActivity
        ).map { data ->
            val feedPage = parseActivityFeed(data)
            storeValue(activityCache, key, feedPage)
            feedPage
        }
    }

    override suspend fun getFollowingActivity(
        page: Int,
        forceRefresh: Boolean
    ): Result<UserActivityPage> {
        val key = "following:page:$page"
        if (!forceRefresh) cachedValue(followingActivityCache, key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_FOLLOWING_ACTIVITY,
                variables = mapOf(
                    "page" to page,
                    "perPage" to ACTIVITY_PAGE_SIZE
                )
            ),
            apiService::getFollowingActivity
        ).map { data ->
            val feedPage = parseActivityFeed(data)
            storeValue(followingActivityCache, key, feedPage)
            feedPage
        }
    }

    override suspend fun getSentMessages(
        messengerId: Int,
        page: Int,
        forceRefresh: Boolean
    ): Result<UserMessagesPage> {
        // The sender id stays in the key even though it is the viewer's own:
        // `clearDetailCache()` is the real account boundary, and a key that
        // cannot be served to the wrong account is the cheaper second line of it.
        val key = "sent:$messengerId:page:$page"
        if (!forceRefresh) cachedValue(sentMessagesCache, key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_SENT_MESSAGES,
                variables = mapOf(
                    "messengerId" to messengerId,
                    "page" to page,
                    "perPage" to MESSAGE_PAGE_SIZE
                )
            ),
            apiService::getSentMessages
        ).map { data ->
            val pageResult = parseSentMessages(data)
            storeValue(sentMessagesCache, key, pageResult)
            pageResult
        }
    }

    override suspend fun getReceivedMessages(
        page: Int,
        forceRefresh: Boolean
    ): Result<UserMessagesPage> {
        // No account in this key because there is no account *in the request*:
        // notifications resolve from the token. Sign-out clearing this map is
        // what keeps the next account from reading this inbox; see
        // `clearDetailCache()`.
        val key = "received:page:$page"
        if (!forceRefresh) cachedValue(receivedMessagesCache, key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_RECEIVED_MESSAGES,
                variables = mapOf(
                    "page" to page,
                    "perPage" to MESSAGE_PAGE_SIZE
                )
            ),
            apiService::getReceivedMessages
        ).map { data ->
            val pageResult = parseReceivedMessages(data)
            storeValue(receivedMessagesCache, key, pageResult)
            pageResult
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
        cache: ConcurrentHashMap<String, CachedValue<List<FollowUser>>>,
        which: String,
        fetch: suspend (GraphQLRequest) -> Result<List<FollowUser>>
    ): Result<List<FollowUser>> {
        val key = "user:$userId:$which"
        if (!forceRefresh) cachedValue(cache, key)?.let { return Result.success(it) }

        val query = when (which) {
            "followers" -> GraphQLQueries.GET_USER_FOLLOWERS
            else -> GraphQLQueries.GET_USER_FOLLOWING
        }
        return fetch(GraphQLRequest(query = query, variables = mapOf("userId" to userId)))
            .onSuccess { storeValue(cache, key, it) }
    }

    override suspend fun getMediaFriends(
        mediaId: Int,
        mediaType: String?,
        forceRefresh: Boolean
    ): Result<List<MediaFriend>> {
        val type = mediaType?.uppercase(Locale.ROOT)
        val key = "media:${type ?: UNSPECIFIED_MEDIA_TYPE}:$mediaId:friends"
        if (!forceRefresh) cachedValue(friendsCache, key)?.let { return Result.success(it) }

        return execute(
            GraphQLRequest(
                query = GraphQLQueries.getMediaFriendsQuery(type),
                variables = buildMap {
                    put("mediaId", mediaId)
                    type?.let { put("type", it) }
                }
            ),
            apiService::getMediaFriends
        ).map { data ->
            // Filtered, mapped and sorted here rather than in the card: the same
            // title opened twice must come back in the same order, and the cached
            // answer is the one the second open reads.
            data.page?.mediaList.orEmpty()
                .mapNotNull { it.toMediaFriend() }
                .sortedForDisplay()
        }.onSuccess { storeValue(friendsCache, key, it) }
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
        ).mapRequire { data ->
            val likeable = data.likeable.require("No response from the like mutation")
            ActivityLikeState(
                isLiked = likeable.isLiked == true,
                likeCount = likeable.likeCount ?: 0
            )
        }.onSuccess {
            // The cached feed pages still carry the pre-toggle like state and
            // count. Dropping them is cheaper than patching each page in place,
            // and it is what stops a re-entry showing a like the user has undone.
            // The friends feed caches the same rows under its own keys and has
            // to go with them, or the same activity would disagree with itself
            // depending on which tab was opened second.
            activityCache.clear()
            followingActivityCache.clear()
        }

    override suspend fun toggleActivityReplyLike(replyId: Int): Result<ActivityLikeState> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.TOGGLE_REPLY_LIKE,
                variables = mapOf("replyId" to replyId)
            ),
            apiService::toggleActivityReplyLike
        ).mapRequire { data ->
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
        ).mapRequire { it.reply.require("No response from the reply mutation") }
            .onSuccess {
                // The feed's `replyCount` is now one short of the truth, so the
                // same cache drop the like does applies here - on both feeds,
                // which carry the same activity under different keys.
                activityCache.clear()
                followingActivityCache.clear()
            }

    override suspend fun editActivityReply(
        replyId: Int,
        activityId: Int,
        text: String
    ): Result<ActivityReply> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.EDIT_ACTIVITY_REPLY,
                variables = mapOf("id" to replyId, "activityId" to activityId, "text" to text)
            ),
            apiService::saveActivityReply
        ).mapRequire { it.reply.require("No response from the reply mutation") }
        // No cache to drop. A feed page carries the reply's *count*, never its
        // text, and an edit cannot change the count - the same argument
        // `toggleActivityReplyLike` makes for leaving `activityCache` alone.

    override suspend fun deleteActivityReply(replyId: Int): Result<Unit> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.DELETE_ACTIVITY_REPLY,
                variables = mapOf("id" to replyId)
            ),
            apiService::deleteActivityReply
        ).map { }
            .onSuccess {
                // The cached pages carry the pre-delete reply count, and unlike a
                // like there is no toggle back: dropping them is cheaper than
                // patching each page in place and is what stops a re-entry
                // showing a count for a comment that no longer exists. Both
                // feeds hold the same activity, so both are dropped.
                activityCache.clear()
                followingActivityCache.clear()
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
        ).mapRequire { data ->
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
        ).mapRequire { data ->
            data.staff.require("That person is no longer available on AniList.")
                .also {
                    if (personProfileCache.size >= MAX_DETAIL_CACHE_NODES) personProfileCache.clear()
                    personProfileCache[PersonKind.STAFF to staffId] = it
                }
        }
    }

    override suspend fun clearDetailCache() {
        detailCache.clear()
        // A profile is public data, but this cache is the size of a cast walk, and
        // the next account opened should not inherit the last one's.
        personProfileCache.clear()
        // The viewer's own profile is the one answer the next launch would read
        // before anything else, so it goes first here - a 30-minute window is a
        // window for the *same* person, not a grace period for the next one.
        viewerCache = null
        // The list is per-person data, so it has to go when the session does.
        // A TTL alone would leave another person's finished list sitting in
        // memory for up to an hour after sign-out.
        //
        // The epoch bump invalidates any fetch already in flight, so a request
        // that started before sign-out cannot write the previous person's list
        // back into the now-cleared cache.
        listEpoch.incrementAndGet()
        listCache.clear()
        // Same reasoning for the profile, and it matters more here: the bio and
        // the favourites are the most identifying thing the app holds about
        // anyone, and a public-profile scan is exactly how someone else's ends
        // up in this process.
        overviewCache.clear()
        // As are the activity feed and the follower lists, which are just as
        // personal and are fetched for other people too. The friends cache is the
        // same data one step further in: it holds which of the viewer's friends
        // have a title on their list, which is friends-only information and must
        // not survive a sign-out - it was simply missed here when it was added.
        activityCache.clear()
        followersCache.clear()
        followingCache.clear()
        friendsCache.clear()
        // The friends feed and the inbox are viewer-only reads: both resolve from
        // the token rather than from an id in the URL, so unlike every cache
        // above their keys cannot keep two accounts apart on their own. Clearing
        // here is the entire account boundary for them.
        followingActivityCache.clear()
        sentMessagesCache.clear()
        receivedMessagesCache.clear()
        // And the same again for the disk tier. This is the half that outlives
        // the process: without this, signing out would clear what the app is
        // holding and leave the list, the bio, the viewer's record and the
        // progress inside every snapshotted detail node in the cache directory
        // for the next account - and unlike the maps above, nothing about the
        // process ending would ever take them away.
        snapshots?.clear()
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
            // A 200 whose payload is absent *and* whose errors array has nothing to
            // read is AniList answering one of its own GraphQL failures without the
            // reason in the body. Classified MALFORMED_RESPONSE rather than the
            // NOT_FOUND this used to be - that claimed the user or media did not
            // exist, which is a fact about AniList nobody ever stated - and rather
            // than INVALID_REQUEST, which would blame a query AniList did not
            // complain about. The throw is caught below, so callers still get a
            // `Result.failure`; a null payload must never reach a screen as an
            // empty success.
            Result.success(
                response.data ?: throw AniListException(
                    kind = AniListErrorKind.MALFORMED_RESPONSE,
                    message = "AniList sent an empty answer to this request. Try again in a moment."
                )
            )
        } catch (e: HttpException) {
            Result.failure(e.toAniListException())
        } catch (e: AniListException) {
            Result.failure(e)
        } catch (e: IOException) {
            // Every read and write that cannot leave the device lands here, and
            // it is the one place an offline failure is classified - so the
            // app-wide notice is raised exactly once per episode, deduplicated in
            // [SystemNotices]. A screen that has no connection surface of its own
            // (a sheet, a background paging request) would otherwise fail with a
            // dead spinner and no "there is no network".
            SystemNotices.report(SystemNotices.Notice.Offline)
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

    /**
     * A node AniList did not send, as the not-found failure the call site names.
     *
     * It throws, so it is only total under [mapRequire]; see that function for
     * what happens - and what used to happen - under a bare `Result.map`.
     */
    private fun <T : Any> T?.require(message: String): T =
        this ?: throw AniListException(AniListErrorKind.NOT_FOUND, message)

    /**
     * [Result.map] for every transform that rejects a null payload through [require].
     *
     * `Result.map` rethrows anything its transform throws - that is its documented
     * contract, and `mapCatching` is the variant that catches - so
     * `data.staff.require(...)` used to leave the repository as a thrown
     * [AniListException] rather than the `Result.failure` it names. The callers
     * fold the result inside a `launch` with no exception handler, so an absent
     * node did not draw the call site's sentence ("That person is no longer
     * available on AniList."); it killed the coroutine that was going to draw
     * anything at all.
     *
     * Only [AniListException] is caught, deliberately: cancellation must still
     * propagate - [execute] rethrows it for the same reason - and a bug in a
     * transform is not an answer from AniList.
     */
    private inline fun <T : Any, R> Result<T>.mapRequire(transform: (T) -> R): Result<R> {
        val value = getOrElse { return Result.failure(it) }
        return try {
            Result.success(transform(value))
        } catch (e: AniListException) {
            Result.failure(e)
        }
    }

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
                response()?.errorBody()?.readCapped()
            )?.errors?.firstOrNull()
        )
}

/**
 * One activity response reduced to the rows the feed can draw, and the honest
 * answer about whether another page exists.
 *
 * Shared by the profile feed and the friends feed: the two queries select the
 * same fragments and differ only in *whose* activities the server picks, so the
 * filtering and the pagination rule have to be identical or the two tabs would
 * end at different rows for the same account.
 *
 * `isUnrecognised` is the union member this feed does not render. The query asks
 * for list updates and text posts, but the filter is a `type_in` argument, not a
 * guarantee, and anything else arriving here parses into an activity with no
 * media, status or text. Dropping those means a blank card is never rendered; a
 * text post has no media by definition, so it must not be caught by the media
 * check.
 *
 * `hasNextPage` is AniList's own answer first, because the filtering above is
 * exactly why a page's row count cannot be trusted as "is this the end". The
 * fallback only exists for a response that did not carry `pageInfo` at all, and
 * it asks the unfiltered count so it stays as honest as it can be.
 */
private fun parseActivityFeed(data: ActivityFeedData): UserActivityPage {
    val raw = data.page?.activities.orEmpty()
    val activities = raw.filterNot { it.isUnrecognised || (!it.isTextPost && it.media == null) }
    val hasMore = data.page?.pageInfo?.hasNextPage ?: (raw.size >= ACTIVITY_PAGE_SIZE)
    return UserActivityPage(activities = activities, hasMore = hasMore)
}

/**
 * Sent messages as a page of drawable rows.
 *
 * A message whose body is gone - deleted on the site while its activity row
 * survived - is dropped rather than drawn as an empty bubble, the same rule the
 * activity feed applies to union members it cannot render. `hasMore` keeps the
 * feed's two-tier rule: the server's answer first, the unfiltered row count only
 * when the response carried no `pageInfo`.
 */
private fun parseSentMessages(data: SentMessagesData): UserMessagesPage {
    val raw = data.page?.activities.orEmpty()
    val messages = raw.filter { it.hasBody }
    val hasMore = data.page?.pageInfo?.hasNextPage ?: (raw.size >= MESSAGE_PAGE_SIZE)
    return UserMessagesPage(messages = messages, hasMore = hasMore)
}

/**
 * Received messages, unwrapped from their notifications.
 *
 * `ActivityMessageNotification` embeds the whole `MessageActivity` under
 * `message`, so the row the inbox draws is that field, not the notification.
 * A notification with no message (a retired activity, or a member the fragment
 * did not match) is dropped for the same reason as above. The count for the
 * pagination fallback is the raw *notification* count - the only number this
 * response actually carries.
 */
private fun parseReceivedMessages(data: ReceivedMessagesData): UserMessagesPage {
    val raw = data.page?.notifications.orEmpty()
    val messages = raw
        .asSequence()
        .filterNot { it.isUnrecognised }
        .mapNotNull { notification ->
            val message = notification.message ?: return@mapNotNull null
            // The notification's own timestamp is the fallback, and it is not
            // cosmetic. `ActivityMessageNotification.createdAt` and the embedded
            // `MessageActivity.createdAt` are the same moment in practice, but only
            // one of them is guaranteed - and a received row with a null time sorted
            // to the bottom of the merged inbox (behind every sent message, however
            // old) and printed no age at all. The notification is the envelope, so
            // its timestamp is the honest stand-in for the envelope's contents.
            if (message.createdAt == null && notification.createdAt != null) {
                message.copy(createdAt = notification.createdAt)
            } else {
                message
            }
        }
        .filter { it.hasBody }
        .toList()
    val hasMore = data.page?.pageInfo?.hasNextPage ?: (raw.size >= MESSAGE_PAGE_SIZE)
    return UserMessagesPage(messages = messages, hasMore = hasMore)
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
    // Omitted rather than sent as null when the caller does not edit notes: a null
    // argument is a no-op on AniList, while "" is the explicit clear the editor's
    // empty field means.
    notes?.let { put("notes", it) }
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