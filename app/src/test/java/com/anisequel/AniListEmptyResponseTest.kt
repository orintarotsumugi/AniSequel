package com.anisequel

import com.anisequel.data.model.ActivityDetail
import com.anisequel.data.model.ActivityDetailData
import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.ActivityFeedPage
import com.anisequel.data.model.ActivityLikesData
import com.anisequel.data.model.ActivityReply
import com.anisequel.data.model.ApiStatusData
import com.anisequel.data.model.CharacterProfile
import com.anisequel.data.model.CharacterProfileData
import com.anisequel.data.model.DeleteActivityReplyData
import com.anisequel.data.model.DeleteMediaListEntryData
import com.anisequel.data.model.EntityPreviewPages
import com.anisequel.data.model.FollowersData
import com.anisequel.data.model.FollowingData
import com.anisequel.data.model.GraphQLError
import com.anisequel.data.model.GraphQLRequest
import com.anisequel.data.model.ReceivedMessagesData
import com.anisequel.data.model.SentMessagesData
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.ProfileActivity
import com.anisequel.data.model.MediaCoverPage
import com.anisequel.data.model.MediaDetailData
import com.anisequel.data.model.MediaFriendsData
import com.anisequel.data.model.MediaListCollectionData
import com.anisequel.data.model.MediaListEntryData
import com.anisequel.data.model.PageInfo
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.model.ProfileAppearances
import com.anisequel.data.model.SaveActivityReplyData
import com.anisequel.data.model.SaveMediaListEntryData
import com.anisequel.data.model.StaffProfile
import com.anisequel.data.model.StaffProfileData
import com.anisequel.data.model.StudioData
import com.anisequel.data.model.ToggleLikeData
import com.anisequel.data.model.UserActivityPage
import com.anisequel.data.model.UserByNameData
import com.anisequel.data.model.UserOverviewData
import com.anisequel.data.model.ViewerData
import com.anisequel.data.model.VoiceActorCredit
import com.anisequel.data.model.toLoadedState
import com.anisequel.data.network.AniListApiService
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.repository.AniListRepositoryImpl
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What the app does when AniList answers with nothing.
 *
 * "Nothing" is two different answers - the report that started this work ran
 * them together - and each has to stay on its own side of the line:
 *
 *  - **No payload**: a 200 whose `data` is null and whose `errors` array has
 *    nothing to read. That is a classified failure, so the screen draws its
 *    error state instead of "loaded, nothing here".
 *  - **An empty connection**: `Page.activities == null`, `activity.replies ==
 *    null`, a character whose `media` edges and nodes are both null, or an
 *    empty list. That is a *valid* answer and must read as an empty list, so
 *    the screen can say "nothing here yet".
 *
 * The third failure pinned here reached a build as a crash. A null node went
 * through `require(...)` inside `Result.map`, which rethrows whatever its
 * transform throws, so the not-found `AniListException` the call site wrote
 * escaped the repository and killed the coroutine that was going to draw its
 * message. These tests assert the message, because whether an absent person
 * fails as "no longer available" or as a generic network error is the part a
 * reader can act on.
 */
class AniListEmptyResponseTest {

    // ---- no payload: a classified failure -------------------------------------

    /**
     * A 200 with `data: null` on every read this report named.
     *
     * AniList answers this way to some of its own GraphQL failures, and the
     * caller must get a `Result.failure` it can draw - not an empty success
     * pretending the feed or the thread loaded, and not a thrown NPE.
     *
     * `MALFORMED_RESPONSE` and not `NOT_FOUND`: nothing in the body says the
     * user, activity or character does not exist, and it is not
     * `INVALID_REQUEST` either, because AniList did not complain about the
     * query. The message has to tell the reader what to try next.
     */
    @Test
    fun `a null payload is a classified failure on the reads around comments and people`() = runTest {
        val repository = AniListRepositoryImpl(
            FakeService(
                replies = { GraphQLResponse(null) },
                feed = { GraphQLResponse(null) },
                character = { GraphQLResponse(null) },
                staff = { GraphQLResponse(null) }
            )
        )

        listOf(
            "comment thread" to repository.getActivityReplies(1),
            "activity feed" to repository.getUserActivity(1),
            "character profile" to repository.getCharacterProfile(1),
            "staff profile" to repository.getStaffProfile(1)
        ).forEach { (path, result) ->
            assertTrue("$path: a 200 with no payload must fail, got $result", result.isFailure)

            val error = result.exceptionOrNull()
            assertTrue("$path: the failure must be classified, got $error", error is AniListException)
            assertTrue(
                "$path: a body with no payload is malformed, not a missing resource, got " +
                    "${(error as? AniListException)?.kind}",
                error is AniListException && error.kind == AniListErrorKind.MALFORMED_RESPONSE
            )
            assertTrue(
                "$path: the reader needs an action, got \"${(error as? AniListException)?.message}\"",
                error is AniListException && error.message.orEmpty().contains("Try again")
            )
        }
    }

    /**
     * `data: null` with an error beside it is what the classification is *for*.
     *
     * The null-payload branch above only runs when there is no error to read;
     * when AniList says what it objected to - here a field the document asked
     * for and the schema does not have - that error's kind must survive rather
     * than being replaced by the generic malformed-response one.
     */
    @Test
    fun `a null payload that carries an error is classified from the error`() = runTest {
        val repository = AniListRepositoryImpl(
            FakeService(
                replies = {
                    GraphQLResponse(
                        data = null,
                        errors = listOf(GraphQLError("Cannot query field \"Nope\" on type \"Query\".", 400))
                    )
                }
            )
        )

        val error = repository.getActivityReplies(1).exceptionOrNull()

        assertTrue("expected a classified failure, got $error", error is AniListException)
        assertTrue(
            "the server's own complaint outranks the empty body, got ${(error as? AniListException)?.kind}",
            error is AniListException && error.kind == AniListErrorKind.INVALID_REQUEST
        )
    }

    // ---- empty connections: empty lists ---------------------------------------

    /**
     * The feed's three ways of saying nothing: no page, a page whose connection
     * answered null, and a connection that answered an empty list.
     *
     * All three mean "this person has not done anything yet", and the screen
     * draws its empty state for them. Called a failure - which is what a null
     * that reaches a `!!` or a `first()` would be - it would instead draw an
     * error over a feed that is simply quiet.
     */
    @Test
    fun `a feed that answered nothing reads as an empty list, not a failure`() = runTest {
        val noPage = AniListRepositoryImpl(
            FakeService(feed = { GraphQLResponse(ActivityFeedData(page = null)) })
        )
        assertEquals(
            Result.success(UserActivityPage(emptyList(), hasMore = false)),
            noPage.getUserActivity(1)
        )

        val noConnection = AniListRepositoryImpl(
            FakeService(feed = { GraphQLResponse(ActivityFeedData(ActivityFeedPage(activities = null))) })
        )
        assertEquals(
            Result.success(UserActivityPage(emptyList(), hasMore = false)),
            noConnection.getUserActivity(1)
        )

        val emptyConnection = AniListRepositoryImpl(
            FakeService(feed = { GraphQLResponse(ActivityFeedData(ActivityFeedPage(activities = emptyList()))) })
        )
        assertEquals(
            Result.success(UserActivityPage(emptyList(), hasMore = false)),
            emptyConnection.getUserActivity(1)
        )
    }

    /**
     * The feed's end is AniList's answer, not the number of rows that survived.
     *
     * A `ProfileActivity` whose media has since been removed is dropped at this
     * boundary so no blank card is drawn. That makes the page shorter than the one
     * the server sent, which is why "fewer rows than `perPage`" used to end the
     * feed one page early - with the rest of the history behind it and nothing on
     * screen to say so. `pageInfo.hasNextPage` is the documented signal and has to
     * survive the filter.
     */
    @Test
    fun `a filtered activity page keeps the next-page answer`() = runTest {
        val removedMedia = ProfileActivity(id = 7, status = "completed", media = null)
        val repository = AniListRepositoryImpl(
            FakeService(
                feed = {
                    GraphQLResponse(
                        ActivityFeedData(
                            ActivityFeedPage(
                                pageInfo = PageInfo(hasNextPage = true),
                                activities = listOf(removedMedia)
                            )
                        )
                    )
                }
            )
        )

        val page = repository.getUserActivity(1).getOrThrow()

        assertTrue("an update whose media is gone cannot be drawn", page.activities.isEmpty())
        assertTrue(
            "the page was filtered, not emptied by AniList, and there is another one",
            page.hasMore
        )
    }

    /**
     * The comment sheet's three ways of saying nothing: the activity is gone,
     * the replies connection answered null, or it answered an empty list.
     *
     * `Activity(id:)` returns a union, so an id that no longer resolves to a
     * `ProfileActivity` is an object with no selected fields rather than an error
     * - and the sheet still has to open and say there are no comments.
     */
    @Test
    fun `a comment thread that answered nothing reads as an empty list`() = runTest {
        val missingActivity = AniListRepositoryImpl(
            FakeService(replies = { GraphQLResponse(ActivityDetailData(activity = null)) })
        )
        assertEquals(Result.success(emptyList<ActivityReply>()), missingActivity.getActivityReplies(1))

        val nullReplies = AniListRepositoryImpl(
            FakeService(replies = { GraphQLResponse(ActivityDetailData(ActivityDetail(replies = null))) })
        )
        assertEquals(Result.success(emptyList<ActivityReply>()), nullReplies.getActivityReplies(1))

        val emptyReplies = AniListRepositoryImpl(
            FakeService(replies = { GraphQLResponse(ActivityDetailData(ActivityDetail(replies = emptyList()))) })
        )
        assertEquals(Result.success(emptyList<ActivityReply>()), emptyReplies.getActivityReplies(1))
    }

    /**
     * A person's media connection, when it answered nothing.
     *
     * The character query asks for `edges` and the staff query for `nodes`, and
     * each has to survive the other half being null - as does a connection whose
     * null node inside an edge (`ProfileAppearances.titles` drops those) leaves
     * no rows. The sheet can then hide the "Appears in" row rather than draw a
     * broken one.
     */
    @Test
    fun `a person whose media connection answered nothing has no rows to draw`() = runTest {
        val character = CharacterProfile(
            id = 1,
            media = ProfileAppearances(nodes = null, edges = null)
        )
        val characterRepository = AniListRepositoryImpl(
            FakeService(character = { GraphQLResponse(CharacterProfileData(character = character)) })
        )

        val characterResult = characterRepository.getCharacterProfile(1)
        assertTrue("a profile AniList did send is a success, got $characterResult", characterResult.isSuccess)
        val loadedCharacter = characterResult.getOrThrow().toLoadedState()
        assertEquals(emptyList<ProfileAppearance>(), loadedCharacter.appearances)
        assertEquals(emptyList<VoiceActorCredit>(), loadedCharacter.voiceActors)

        val staff = StaffProfile(id = 2, media = ProfileAppearances(nodes = null, edges = null))
        val staffRepository = AniListRepositoryImpl(
            FakeService(staff = { GraphQLResponse(StaffProfileData(staff = staff)) })
        )

        val staffResult = staffRepository.getStaffProfile(2)
        assertTrue("a profile AniList did send is a success, got $staffResult", staffResult.isSuccess)
        val loadedStaff = staffResult.getOrThrow().toLoadedState()
        assertEquals(emptyList<ProfileAppearance>(), loadedStaff.appearances)
        assertEquals(emptyList<VoiceActorCredit>(), loadedStaff.voiceActors)
    }

    // ---- a null node: the call site's not-found failure ------------------------

    /**
     * The crash this class exists for.
     *
     * `Response<Character>` null when AniList has no such character is a normal
     * answer, and the call site names it: "That character is no longer available
     * on AniList." That sentence used to be thrown out of the repository through
     * `Result.map` - its contract rethrows - and reach the caller's `launch` as
     * an unhandled exception, so the sheet crashed instead of drawing the
     * message. It must arrive as `Result.failure`, with the kind and wording the
     * call site chose.
     */
    @Test
    fun `a missing character is the not-found failure the call site names`() = runTest {
        val repository = AniListRepositoryImpl(
            FakeService(character = { GraphQLResponse(CharacterProfileData(character = null)) })
        )

        val result = repository.getCharacterProfile(1)

        assertTrue("a null node must be a failure rather than a crash, got $result", result.isFailure)
        val classified = result.exceptionOrNull() as? AniListException
        assertEquals(
            "the failure must be classified, got ${result.exceptionOrNull()}",
            AniListErrorKind.NOT_FOUND,
            classified?.kind
        )
        assertEquals(
            "the call site's sentence is the one the reader needs",
            "That character is no longer available on AniList.",
            classified?.message
        )
    }

    @Test
    fun `a missing staff member is the not-found failure the call site names`() = runTest {
        val repository = AniListRepositoryImpl(
            FakeService(staff = { GraphQLResponse(StaffProfileData(staff = null)) })
        )

        val result = repository.getStaffProfile(1)

        assertTrue("a null node must be a failure rather than a crash, got $result", result.isFailure)
        val classified = result.exceptionOrNull() as? AniListException
        assertEquals(
            "the failure must be classified, got ${result.exceptionOrNull()}",
            AniListErrorKind.NOT_FOUND,
            classified?.kind
        )
        assertEquals(
            "the call site's sentence is the one the reader needs",
            "That person is no longer available on AniList.",
            classified?.message
        )
    }

    /**
     * The same contract on the write beside the thread: a save that answers
     * without the created reply is a failure the sheet can show, not a crash.
     *
     * Named here rather than left to the character tests because the comment
     * path is the other half of the report - a reply the server did not echo
     * back used to throw past `postComment`'s coroutine.
     */
    @Test
    fun `a save that did not echo a reply is the not-found failure the call site names`() = runTest {
        val repository = AniListRepositoryImpl(
            FakeService(saveReply = { GraphQLResponse(SaveActivityReplyData(reply = null)) })
        )

        val result = repository.postActivityReply(activityId = 1, text = "hello")

        assertTrue("a null reply must be a failure rather than a crash, got $result", result.isFailure)
        val classified = result.exceptionOrNull() as? AniListException
        assertEquals(
            "the failure must be classified, got ${result.exceptionOrNull()}",
            AniListErrorKind.NOT_FOUND,
            classified?.kind
        )
        assertEquals(
            "the call site's sentence is the one the reader needs",
            "No response from the reply mutation",
            classified?.message
        )
    }
}

/**
 * Serves the five calls these tests make and refuses the rest.
 *
 * `notServed` rather than a blank response, for the same reason
 * `AniListErrorMappingTest` gives: a blank `GraphQLResponse` is itself under
 * test here, so a fake that answered one by accident would make a test pass
 * against data this fake invented. An unanswered endpoint fails loudly.
 */
private class FakeService(
    private val replies: () -> GraphQLResponse<ActivityDetailData> = { notServed("getActivityReplies") },
    private val feed: () -> GraphQLResponse<ActivityFeedData> = { notServed("getUserActivity") },
    private val character: () -> GraphQLResponse<CharacterProfileData> = { notServed("getCharacterProfile") },
    private val staff: () -> GraphQLResponse<StaffProfileData> = { notServed("getStaffProfile") },
    private val saveReply: () -> GraphQLResponse<SaveActivityReplyData> = { notServed("saveActivityReply") }
) : AniListApiService {
    // The fake does not serve direct messages; a test that ever sends one should
    // fail loudly rather than invent an answer.
    override suspend fun saveActivityMessage(
        request: com.anisequel.data.model.GraphQLRequest
    ): com.anisequel.data.model.GraphQLResponse<
        com.anisequel.data.model.SendActivityMessageData
        > = error("This fake does not send messages")


    override suspend fun getActivityReplies(request: GraphQLRequest): GraphQLResponse<ActivityDetailData> =
        replies()

    override suspend fun getUserActivity(request: GraphQLRequest): GraphQLResponse<ActivityFeedData> =
        feed()

    override suspend fun getFollowingActivity(
        request: GraphQLRequest
    ): GraphQLResponse<ActivityFeedData> = notServed("getFollowingActivity")

    override suspend fun getSentMessages(
        request: GraphQLRequest
    ): GraphQLResponse<SentMessagesData> = notServed("getSentMessages")

    override suspend fun getReceivedMessages(
        request: GraphQLRequest
    ): GraphQLResponse<ReceivedMessagesData> = notServed("getReceivedMessages")

    override suspend fun getCharacterProfile(request: GraphQLRequest): GraphQLResponse<CharacterProfileData> =
        character()

    override suspend fun getStaffProfile(request: GraphQLRequest): GraphQLResponse<StaffProfileData> =
        staff()

    override suspend fun saveActivityReply(request: GraphQLRequest): GraphQLResponse<SaveActivityReplyData> =
        saveReply()

    override suspend fun getViewer(request: GraphQLRequest): GraphQLResponse<ViewerData> =
        notServed("getViewer")

    override suspend fun getUserByName(request: GraphQLRequest): GraphQLResponse<UserByNameData> =
        notServed("getUserByName")

    override suspend fun getMediaListCollection(
        request: GraphQLRequest
    ): GraphQLResponse<MediaListCollectionData> = notServed("getMediaListCollection")

    override suspend fun saveMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<SaveMediaListEntryData> = notServed("saveMediaListEntry")

    override suspend fun getMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<MediaListEntryData> = notServed("getMediaListEntry")

    override suspend fun deleteMediaListEntry(
        request: GraphQLRequest
    ): GraphQLResponse<DeleteMediaListEntryData> = notServed("deleteMediaListEntry")

    override suspend fun getMediaDetail(request: GraphQLRequest): GraphQLResponse<MediaDetailData> =
        notServed("getMediaDetail")

    override suspend fun getStudio(request: GraphQLRequest): GraphQLResponse<StudioData> =
        notServed("getStudio")

    override suspend fun getMediaCovers(
        request: GraphQLRequest
    ): GraphQLResponse<Map<String, MediaCoverPage>> = notServed("getMediaCovers")

    override suspend fun getEntityPreviews(
        request: GraphQLRequest
    ): GraphQLResponse<EntityPreviewPages> = notServed("getEntityPreviews")

    override suspend fun getUserOverview(request: GraphQLRequest): GraphQLResponse<UserOverviewData> =
        notServed("getUserOverview")

    override suspend fun getUserFollowers(request: GraphQLRequest): GraphQLResponse<FollowersData> =
        notServed("getUserFollowers")

    override suspend fun getUserFollowing(request: GraphQLRequest): GraphQLResponse<FollowingData> =
        notServed("getUserFollowing")

    override suspend fun getMediaFriends(request: GraphQLRequest): GraphQLResponse<MediaFriendsData> =
        notServed("getMediaFriends")

    override suspend fun getActivityLikes(request: GraphQLRequest): GraphQLResponse<ActivityLikesData> =
        notServed("getActivityLikes")

    override suspend fun toggleActivityLike(request: GraphQLRequest): GraphQLResponse<ToggleLikeData> =
        notServed("toggleActivityLike")

    override suspend fun toggleActivityReplyLike(request: GraphQLRequest): GraphQLResponse<ToggleLikeData> =
        notServed("toggleActivityReplyLike")

    override suspend fun deleteActivityReply(
        request: GraphQLRequest
    ): GraphQLResponse<DeleteActivityReplyData> = notServed("deleteActivityReply")

    // The reachability probe, added with the status strip. These tests do not
    // check reachability; refusing keeps an accidental call from inventing one.
    override suspend fun ping(request: GraphQLRequest): GraphQLResponse<ApiStatusData> =
        notServed("ping")
}

private fun notServed(operation: String): Nothing =
    throw UnsupportedOperationException("this fake does not serve $operation")
