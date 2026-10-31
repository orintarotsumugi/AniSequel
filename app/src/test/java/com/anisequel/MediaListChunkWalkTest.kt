package com.anisequel

import com.anisequel.data.model.ActivityDetailData
import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.ActivityLikesData
import com.anisequel.data.model.ApiStatusData
import com.anisequel.data.model.CharacterProfileData
import com.anisequel.data.model.DeleteMediaListEntryData
import com.anisequel.data.model.EntityPreviewPages
import com.anisequel.data.model.FollowersData
import com.anisequel.data.model.FollowingData
import com.anisequel.data.model.GraphQLRequest
import com.anisequel.data.model.ReceivedMessagesData
import com.anisequel.data.model.SentMessagesData
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.MediaCoverPage
import com.anisequel.data.model.MediaDetailData
import com.anisequel.data.model.MediaListCollection
import com.anisequel.data.model.MediaFriendsData
import com.anisequel.data.model.MediaListCollectionData
import com.anisequel.data.model.MediaListEntryData
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MediaListGroup
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.DeleteActivityReplyData
import com.anisequel.data.model.SaveActivityReplyData
import com.anisequel.data.model.SaveMediaListEntryData
import com.anisequel.data.model.StaffProfileData
import com.anisequel.data.model.StudioData
import com.anisequel.data.model.ToggleLikeData
import com.anisequel.data.model.UserByNameData
import com.anisequel.data.model.UserOverviewData
import com.anisequel.data.model.ViewerData
import com.anisequel.data.network.AniListApiService
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How the repository walks a list that AniList pages across several chunks.
 *
 * ## The bug
 *
 * The walk stopped at `while (hasNext && currentChunk <= 10)` and returned the
 * first ten chunks as `Result.success`. Both callers cache a successful list for
 * the user's refresh interval, so an account past five thousand entries was
 * served - and kept - a list with its tail missing, and the only symptom was a
 * sequel near the end of the list that never appeared and never went away. Ten
 * was never a contract; the walk follows AniList's own `hasNextChunk`.
 *
 * The second test covers the honest end of that: a walk that reaches its own
 * runaway ceiling without the server ever saying "done" has *not* assembled the
 * list, and reporting those chunks as a success is the same failure one layer
 * down.
 */
class MediaListChunkWalkTest {

    /**
     * A list longer than the old ten-chunk ceiling must come back whole.
     *
     * Twelve chunks is the smallest case that fails the old loop condition twice:
     * the walk has to keep going past chunk ten and then stop because the server
     * said so, not because the count ran out.
     */
    @Test
    fun `a twelve-chunk list is merged whole rather than truncated at ten`() = runTest {
        val service = ChunkedListService { chunk ->
            GraphQLResponse(
                data = MediaListCollectionData(
                    collection = MediaListCollection(
                        lists = listOf(
                            MediaListGroup(
                                name = "Watching",
                                status = "CURRENT",
                                entries = listOf(
                                    // `media` is required on the entry, and the
                                    // chunk id is the only identity these entries
                                    // have in this fake, so it is both fields.
                                    MediaListEntryItem(id = chunk, media = MediaNode(id = chunk))
                                )
                            )
                        ),
                        hasNextChunk = chunk < 12
                    )
                )
            )
        }

        val result = AniListRepositoryImpl(service)
            .getUserAnimeListByUsername("someone", forceRefresh = true)

        assertEquals(
            "the walk has to chase every chunk the server offers, in order; it used " +
                "to stop at the tenth and return the first ten as the whole account",
            (1..12).toList(),
            service.requestedChunks.toList()
        )
        assertEquals(
            "every chunk names the same list, so the merge has to leave one list",
            1,
            result.getOrNull()?.lists?.size
        )
        assertEquals(
            "all twelve chunks' entries belong to that one list",
            (1..12).toList(),
            result.getOrNull()?.lists?.firstOrNull()?.entries?.map { it.id }
        )
        assertEquals(
            "a fully walked collection must not still claim another chunk",
            false,
            result.getOrNull()?.hasNextChunk
        )
    }

    /**
     * A walk the ceiling stops must fail, not come back short.
     *
     * The ceiling exists so a server that answers "another chunk" forever cannot
     * spin the walk indefinitely. But when it trips, the list is incomplete, and
     * calling that a success is exactly the truncation the twelve-chunk test
     * above pins - so it is reported as a failure instead.
     */
    @Test
    fun `a walk that cannot finish is reported as a failure, not a short list`() = runTest {
        val service = ChunkedListService { chunk ->
            GraphQLResponse(
                data = MediaListCollectionData(
                    collection = MediaListCollection(
                        lists = listOf(
                            MediaListGroup(
                                name = "Watching",
                                status = "CURRENT",
                                entries = listOf(
                                    // `media` is required on the entry, and the
                                    // chunk id is the only identity these entries
                                    // have in this fake, so it is both fields.
                                    MediaListEntryItem(id = chunk, media = MediaNode(id = chunk))
                                )
                            )
                        ),
                        // Always another chunk: nothing short of the walk's own
                        // ceiling ends the loop.
                        hasNextChunk = true
                    )
                )
            )
        }

        val result = AniListRepositoryImpl(service)
            .getUserAnimeListByUsername("someone", forceRefresh = true)

        val failure = result.exceptionOrNull()
        assertTrue(
            "a list the walk could not finish must not be reported as complete - the " +
                "callers cache a success for the whole refresh interval",
            failure != null
        )
        assertTrue(
            "the failure has to be classified, and not as a dead session: nothing " +
                "about a list this large is fixed by signing in again",
            failure is AniListException && failure.kind != AniListErrorKind.INVALID_SESSION
        )
        assertTrue(
            "the walk must leave the old ten-chunk truncation point behind",
            service.requestedChunks.size > 10
        )
        assertTrue(
            "and it must still stop, rather than following a server that never says " +
                "stop for as long as the request budget allows",
            service.requestedChunks.size < 1000
        )
    }
}

/**
 * Answers `MediaListCollection` with whatever [chunks] returns for the chunk it
 * was asked for, and nothing else.
 *
 * Every other method is `notServed` for the same reason as
 * `AniListErrorMappingTest.FakeService`: a new endpoint on [AniListApiService]
 * stops the test *module* compiling until it is answered here, and an
 * unanswered one - were these tests ever to grow into touching it - would fail
 * loudly instead of quietly inventing data.
 */
private class ChunkedListService(
    private val chunks: (Int) -> GraphQLResponse<MediaListCollectionData>
) : AniListApiService {
    // The fake does not serve direct messages; a test that ever sends one should
    // fail loudly rather than invent an answer.
    override suspend fun saveActivityMessage(
        request: com.anisequel.data.network.GraphQLRequest
    ): com.anisequel.data.network.GraphQLResponse<
        com.anisequel.data.model.SendActivityMessageData
        > = error("This fake does not send messages")


    /** Every `chunk` variable the walk asked for, in the order it asked. */
    val requestedChunks = mutableListOf<Int>()

    override suspend fun getMediaListCollection(
        request: GraphQLRequest
    ): GraphQLResponse<MediaListCollectionData> {
        val chunk = request.variables["chunk"] as? Int
            ?: error("the list query must carry a chunk variable")
        requestedChunks += chunk
        return chunks(chunk)
    }

    override suspend fun getViewer(
        request: GraphQLRequest
    ): GraphQLResponse<ViewerData> = notServed("getViewer")

    override suspend fun getUserByName(
        request: GraphQLRequest
    ): GraphQLResponse<UserByNameData> = notServed("getUserByName")

    override suspend fun saveMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<SaveMediaListEntryData> = notServed("saveMediaListEntry")

    override suspend fun getMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<MediaListEntryData> = notServed("getMediaListEntry")

    override suspend fun deleteMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<DeleteMediaListEntryData> = notServed("deleteMediaListEntry")

    override suspend fun getMediaDetail(
        request: GraphQLRequest
    ): GraphQLResponse<MediaDetailData> = notServed("getMediaDetail")

    override suspend fun getStudio(
        request: GraphQLRequest
    ): GraphQLResponse<StudioData> = notServed("getStudio")

    override suspend fun getMediaCovers(
        request: GraphQLRequest
    ): GraphQLResponse<Map<String, MediaCoverPage>> = notServed("getMediaCovers")

    override suspend fun getEntityPreviews(
        request: GraphQLRequest
    ): GraphQLResponse<EntityPreviewPages> = notServed("getEntityPreviews")

    override suspend fun getUserOverview(
        request: GraphQLRequest
    ): GraphQLResponse<UserOverviewData> = notServed("getUserOverview")

    override suspend fun getUserActivity(
        request: GraphQLRequest
    ): GraphQLResponse<ActivityFeedData> = notServed("getUserActivity")

    override suspend fun getFollowingActivity(
        request: GraphQLRequest
    ): GraphQLResponse<ActivityFeedData> = notServed("getFollowingActivity")

    override suspend fun getSentMessages(
        request: GraphQLRequest
    ): GraphQLResponse<SentMessagesData> = notServed("getSentMessages")

    override suspend fun getReceivedMessages(
        request: GraphQLRequest
    ): GraphQLResponse<ReceivedMessagesData> = notServed("getReceivedMessages")

    override suspend fun getUserFollowers(
        request: GraphQLRequest
    ): GraphQLResponse<FollowersData> = notServed("getUserFollowers")

    override suspend fun getUserFollowing(
        request: GraphQLRequest
    ): GraphQLResponse<FollowingData> = notServed("getUserFollowing")

    override suspend fun getMediaFriends(
        request: GraphQLRequest
    ): GraphQLResponse<MediaFriendsData> = notServed("getMediaFriends")

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

    override suspend fun deleteActivityReply(
        request: GraphQLRequest
    ): GraphQLResponse<DeleteActivityReplyData> = notServed("deleteActivityReply")

    override suspend fun getCharacterProfile(
        request: GraphQLRequest
    ): GraphQLResponse<CharacterProfileData> = notServed("getCharacterProfile")

    override suspend fun getStaffProfile(
        request: GraphQLRequest
    ): GraphQLResponse<StaffProfileData> = notServed("getStaffProfile")

    // The reachability probe, added with the status strip. Refusing rather than
    // answering keeps an accidental call from inventing a reachable AniList.
    override suspend fun ping(request: GraphQLRequest): GraphQLResponse<ApiStatusData> =
        notServed("ping")
}

private fun notServed(name: String): Nothing =
    error("$name is not served by this test's fake")
