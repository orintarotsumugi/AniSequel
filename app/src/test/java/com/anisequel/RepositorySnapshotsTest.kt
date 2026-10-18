package com.anisequel

import com.anisequel.data.model.ActivityDetailData
import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.ActivityLikesData
import com.anisequel.data.model.CharacterProfileData
import com.anisequel.data.model.DeleteMediaListEntryData
import com.anisequel.data.model.FollowersData
import com.anisequel.data.model.FollowingData
import com.anisequel.data.model.GraphQLRequest
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.MediaCoverPage
import com.anisequel.data.model.MediaDetailData
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaListCollectionData
import com.anisequel.data.model.MediaListEntryData
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.SaveActivityReplyData
import com.anisequel.data.model.SaveMediaListEntryData
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.StaffProfileData
import com.anisequel.data.model.StudioData
import com.anisequel.data.model.ToggleLikeData
import com.anisequel.data.model.UserByNameData
import com.anisequel.data.model.UserOverviewData
import com.anisequel.data.model.ViewerData
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListApiService
import com.anisequel.data.repository.AniListRepositoryImpl
import com.anisequel.data.repository.SnapshotStore
import com.squareup.moshi.Moshi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * The repository's caches across a restart: what a second launch is allowed to
 * answer without asking AniList, and what it still has to ask for.
 *
 * ## What was broken
 *
 * Every cache in [AniListRepositoryImpl] died with the process. Closing the app
 * meant the next launch re-bought the viewer's profile (before it could even name
 * the account), the whole list (one request per chunk), and every title whose
 * synopsis, cast and studios had already been read - none of which had changed.
 *
 * ## What this test pins
 *
 * - a launch inside the windows spends nothing: the same profile, list and title
 *   a previous launch fetched are served from disk;
 * - the windows are windows: the viewer's own profile is reused for half an hour
 *   and not reused a millisecond past it, the list follows the refresh interval
 *   the user chose, and a title's facts are reused for a day;
 * - a mutation that invalidates the list clears the disk copy too, or the next
 *   launch serves the list the mutation just replaced;
 * - signing out deletes what is on disk, so the next account on the device
 *   cannot read the previous one's list, bio or progress;
 * - a title page served from disk does not carry yesterday's viewer progress:
 *   its viewer-scoped entry is corrected against the list in hand.
 */
class RepositorySnapshotsTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val moshi = Moshi.Builder().build()
    private val service = SnapshotTestService()

    /** A fixed, movable clock: the windows below are minutes and days long. */
    private var now = 1_700_000_000_000L

    /**
     * A **new process** over the same cache directory: a fresh repository with
     * empty in-memory caches and a store that starts by reading the files.
     */
    private fun launch(): AniListRepositoryImpl = AniListRepositoryImpl(
        apiService = service,
        listCacheTtlMillis = { LIST_WINDOW_MILLIS },
        snapshots = SnapshotStore(
            directory = folder.root,
            moshi = moshi,
            formatTag = "test-release",
            nowMillis = { now }
        ),
        nowMillis = { now }
    )

    @Test
    fun `a launch inside the windows spends no requests at all`() = runTest {
        val first = launch()
        first.getViewer().getOrThrow()
        first.getUserAnimeList(VIEWER_ID).getOrThrow()
        first.getMediaDetail(MEDIA_ID, ANIME).getOrThrow()
        val spentByTheFirstLaunch = service.requests

        now += 5 * 60 * 1000L
        val second = launch()
        second.getViewer().getOrThrow()
        second.getUserAnimeList(VIEWER_ID).getOrThrow()
        second.getMediaDetail(MEDIA_ID, ANIME).getOrThrow()

        assertEquals(
            "a second launch is the requests the app used to spend before it could " +
                "draw anything: the viewer, the list, and the title it opened",
            spentByTheFirstLaunch,
            service.requests
        )
    }

    @Test
    fun `the list follows the refresh interval the user chose`() = runTest {
        launch().getUserAnimeList(VIEWER_ID).getOrThrow()
        assertEquals(1, service.listRequests)

        now += 5 * 60 * 1000L
        val list = launch().getUserAnimeList(VIEWER_ID).getOrThrow()
        assertEquals("inside the window the list is not re-fetched", 1, service.listRequests)
        assertEquals("and it is the list that was fetched", 9, list.progress())

        now += LIST_WINDOW_MILLIS
        launch().getUserAnimeList(VIEWER_ID).getOrThrow()
        assertEquals("past the window it is", 2, service.listRequests)
    }

    @Test
    fun `the viewer's own profile is reused for half an hour and not a minute more`() = runTest {
        launch().getViewer().getOrThrow()
        assertEquals(1, service.viewerRequests)

        now += 30 * 60 * 1000L
        launch().getViewer().getOrThrow()
        assertEquals("at exactly half an hour the profile is still the one we have", 1, service.viewerRequests)

        now += 1
        launch().getViewer().getOrThrow()
        assertEquals("a millisecond past it, the account is asked about again", 2, service.viewerRequests)
    }

    @Test
    fun `a title's facts are reused for a day`() = runTest {
        val first = launch()
        val fetched = first.getMediaDetail(MEDIA_ID, ANIME).getOrThrow()
        assertEquals("A synopsis the list query does not carry", fetched.description)
        assertEquals(1, service.detailRequests)

        now += DAY_MILLIS
        assertEquals(
            "the synopsis, cast and studios of a title do not change in a day",
            fetched,
            launch().getMediaDetail(MEDIA_ID, ANIME).getOrThrow()
        )
        assertEquals(1, service.detailRequests)

        now += 1
        launch().getMediaDetail(MEDIA_ID, ANIME).getOrThrow()
        assertEquals("a day later it is worth asking again", 2, service.detailRequests)
    }

    @Test
    fun `a mutation clears the disk copy of the list too`() = runTest {
        val first = launch()
        first.getUserAnimeList(VIEWER_ID).getOrThrow()
        assertEquals(1, service.listRequests)

        first.addToPlanning(MEDIA_ID).getOrThrow()
        assertEquals(1, service.mutationRequests)

        launch().getUserAnimeList(VIEWER_ID).getOrThrow()
        assertEquals(
            "a mutation that invalidates the list has to invalidate the snapshot " +
                "with it, or the next launch serves exactly the list the mutation " +
                "replaced - the failure the epoch bump exists to prevent",
            2,
            service.listRequests
        )
    }

    @Test
    fun `signing out takes the snapshots with it`() = runTest {
        val first = launch()
        first.getViewer().getOrThrow()
        first.getUserAnimeList(VIEWER_ID).getOrThrow()
        first.getMediaDetail(MEDIA_ID, ANIME).getOrThrow()

        first.clearDetailCache()

        assertTrue(
            "sign-out has to leave nothing on disk: the list, the profile and the " +
                "viewer's progress inside a detail node are that person's data",
            folder.root.listFiles().orEmpty().isEmpty()
        )

        val next = launch()
        next.getViewer().getOrThrow()
        next.getUserAnimeList(VIEWER_ID).getOrThrow()
        next.getMediaDetail(MEDIA_ID, ANIME).getOrThrow()
        assertEquals(2, service.viewerRequests)
        assertEquals(2, service.listRequests)
        assertEquals(2, service.detailRequests)
    }

    @Test
    fun `a title page from disk carries the list's progress, not the snapshot's`() = runTest {
        // The list and the fetched detail disagree about the viewer's progress
        // (the detail node is the one carrying a `mediaListEntry`): 3 in the
        // title page, 9 on the list. Both are snapshotted.
        val first = launch()
        first.getViewer().getOrThrow()
        first.getUserAnimeList(VIEWER_ID).getOrThrow()
        assertEquals(3, first.getMediaDetail(MEDIA_ID, ANIME).getOrThrow().mediaListEntry?.progress)

        // Second launch, list in hand: the page follows the list, which is what
        // the row behind it draws too.
        val second = launch()
        second.getViewer().getOrThrow()
        second.getUserAnimeList(VIEWER_ID).getOrThrow()
        assertEquals(
            "a title opened from disk must not show yesterday's progress while " +
                "the list draws today's",
            9,
            second.getMediaDetail(MEDIA_ID, ANIME).getOrThrow().mediaListEntry?.progress
        )

        // Third launch with no list in hand: there is nothing better to correct
        // it with, so the snapshot's own entry is the fallback.
        assertEquals(
            3,
            launch().getMediaDetail(MEDIA_ID, ANIME).getOrThrow().mediaListEntry?.progress
        )

        // A list that no longer has the title: the entry is gone, not drawn as
        // tracked from the snapshot's copy.
        service.listHasEntry = false
        val fourth = launch()
        fourth.getViewer().getOrThrow()
        fourth.getUserAnimeList(VIEWER_ID, forceRefresh = true).getOrThrow()
        assertNull(fourth.getMediaDetail(MEDIA_ID, ANIME).getOrThrow().mediaListEntry)
    }

    private fun MediaListCollection.progress(): Int? =
        lists?.firstOrNull()?.entries?.firstOrNull()?.progress

    private companion object {
        const val VIEWER_ID = 7
        const val MEDIA_ID = 1
        const val ANIME = "ANIME"
        const val LIST_WINDOW_MILLIS = 30 * 60 * 1000L
        const val DAY_MILLIS = 24 * 60 * 60 * 1000L
    }
}

/**
 * Answers the four calls a launch makes - viewer, list, title, and the one
 * mutation the invalidation test needs - and nothing else.
 *
 * Every other method is `notServed` for the same reason as
 * `MediaListChunkWalkTest.ChunkedListService`: a new endpoint on
 * [AniListApiService] stops the test module compiling until it is answered here,
 * and an unanswered one fails loudly rather than quietly inventing data.
 */
private class SnapshotTestService : AniListApiService {

    var viewerRequests = 0
    var listRequests = 0
    var detailRequests = 0
    var mutationRequests = 0

    /** Every request this service has been asked to spend, across all calls. */
    val requests: Int get() = viewerRequests + listRequests + detailRequests + mutationRequests

    /** Whether the viewer's list currently holds an entry for the title. */
    var listHasEntry = true

    /** The progress the list says the viewer is at. */
    var listedProgress: Int? = 9

    /** The progress the fetched detail node's own entry says - what a snapshot keeps. */
    var detailProgress: Int? = 3

    override suspend fun getViewer(request: GraphQLRequest): GraphQLResponse<ViewerData> {
        viewerRequests++
        return GraphQLResponse(data = ViewerData(viewer = ViewerProfile(id = 7, name = "reader")))
    }

    override suspend fun getMediaListCollection(
        request: GraphQLRequest
    ): GraphQLResponse<MediaListCollectionData> {
        listRequests++
        return GraphQLResponse(
            data = MediaListCollectionData(
                collection = MediaListCollection(
                    lists = listOf(
                        MediaListGroup(
                            name = "Watching",
                            entries = if (listHasEntry) {
                                listOf(
                                    MediaListEntryItem(
                                        id = 11,
                                        status = "CURRENT",
                                        progress = listedProgress,
                                        media = MediaNode(
                                            id = 1,
                                            type = "ANIME",
                                            title = MediaTitle(romaji = "A Show")
                                        )
                                    )
                                )
                            } else {
                                emptyList()
                            }
                        )
                    ),
                    hasNextChunk = false
                )
            )
        )
    }

    override suspend fun getMediaDetail(
        request: GraphQLRequest
    ): GraphQLResponse<MediaDetailData> {
        detailRequests++
        return GraphQLResponse(
            data = MediaDetailData(
                media = MediaNode(
                    id = 1,
                    type = "ANIME",
                    title = MediaTitle(romaji = "A Show"),
                    description = "A synopsis the list query does not carry",
                    mediaListEntry = detailProgress?.let {
                        SimpleMediaListEntry(mediaId = 1, status = "CURRENT", progress = it)
                    }
                )
            )
        )
    }

    override suspend fun saveMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<SaveMediaListEntryData> {
        mutationRequests++
        return GraphQLResponse(
            data = SaveMediaListEntryData(entry = SimpleMediaListEntry(mediaId = 1, progress = 0))
        )
    }

    override suspend fun getUserByName(
        request: GraphQLRequest
    ): GraphQLResponse<UserByNameData> = notServed("getUserByName")

    override suspend fun getMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<MediaListEntryData> = notServed("getMediaListEntry")

    override suspend fun deleteMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<DeleteMediaListEntryData> = notServed("deleteMediaListEntry")

    override suspend fun getStudio(
        request: GraphQLRequest
    ): GraphQLResponse<StudioData> = notServed("getStudio")

    override suspend fun getMediaCovers(
        request: GraphQLRequest
    ): GraphQLResponse<Map<String, MediaCoverPage>> = notServed("getMediaCovers")

    override suspend fun getUserOverview(
        request: GraphQLRequest
    ): GraphQLResponse<UserOverviewData> = notServed("getUserOverview")

    override suspend fun getUserActivity(
        request: GraphQLRequest
    ): GraphQLResponse<ActivityFeedData> = notServed("getUserActivity")

    override suspend fun getUserFollowers(
        request: GraphQLRequest
    ): GraphQLResponse<FollowersData> = notServed("getUserFollowers")

    override suspend fun getUserFollowing(
        request: GraphQLRequest
    ): GraphQLResponse<FollowingData> = notServed("getUserFollowing")

    override suspend fun getActivityReplies(
        request: GraphQLRequest
    ): GraphQLResponse<ActivityDetailData> = notServed("getActivityReplies")

    override suspend fun getActivityLikes(
        request: GraphQLRequest
    ): GraphQLResponse<ActivityLikesData> = notServed("getActivityLikes")

    override suspend fun toggleActivityLike(
        request: GraphQLRequest
    ): GraphQLResponse<ToggleLikeData> = notServed("toggleActivityLike")

    override suspend fun toggleActivityReplyLike(
        request: GraphQLRequest
    ): GraphQLResponse<ToggleLikeData> = notServed("toggleActivityReplyLike")

    override suspend fun saveActivityReply(
        request: GraphQLRequest
    ): GraphQLResponse<SaveActivityReplyData> = notServed("saveActivityReply")

    override suspend fun getCharacterProfile(
        request: GraphQLRequest
    ): GraphQLResponse<CharacterProfileData> = notServed("getCharacterProfile")

    override suspend fun getStaffProfile(
        request: GraphQLRequest
    ): GraphQLResponse<StaffProfileData> = notServed("getStaffProfile")
}

private fun notServed(name: String): Nothing =
    error("$name is not served by this test's fake")
