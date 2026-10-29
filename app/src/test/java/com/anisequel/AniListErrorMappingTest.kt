package com.anisequel

import com.anisequel.data.model.AniListErrorEnvelope
import com.anisequel.data.model.ApiStatusData
import com.anisequel.data.model.DeleteMediaListEntryData
import com.anisequel.data.model.EntityPreviewPages
import com.anisequel.data.model.GraphQLError
import com.anisequel.data.model.GraphQLRequest
import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.MediaDetailData
import com.anisequel.data.model.MediaCoverPage
import com.anisequel.data.model.MediaFriendsData
import com.anisequel.data.model.MediaListCollectionData
import com.anisequel.data.model.MediaListEntryData
import com.anisequel.data.model.SaveMediaListEntryData
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.model.StudioData
import com.anisequel.data.model.UserByNameData
import com.anisequel.data.model.ActivityDetailData
import com.anisequel.data.model.ActivityFeedData
import com.anisequel.data.model.ActivityLikesData
import com.anisequel.data.model.FollowersData
import com.anisequel.data.model.FollowingData
import com.anisequel.data.model.DeleteActivityReplyData
import com.anisequel.data.model.SaveActivityReplyData
import com.anisequel.data.model.StaffProfileData
import com.anisequel.data.model.CharacterProfileData
import com.anisequel.data.model.ToggleLikeData
import com.anisequel.data.model.UserOverviewData
import com.anisequel.data.model.ViewerData
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListApiService
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.AniListException
import com.anisequel.data.network.aniListHttpError
import com.anisequel.data.network.parseAniListErrorBody
import com.anisequel.data.repository.AniListRepositoryImpl
import com.squareup.moshi.JsonDataException
import com.squareup.moshi.Moshi
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import java.io.IOException

/**
 * Failure classification.
 *
 * The dashboard decides between "Try again" and "Sign in again" by looking at
 * the failure kind. When it decided by matching words in an exception message,
 * any error whose text happened to contain "invalid" was treated as a dead
 * session and offered a re-login button. These lock the classification at the
 * boundary where it belongs.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AniListErrorMappingTest {

    private class FakeService(
        private val viewerResponse: () -> GraphQLResponse<ViewerData> = { errorGraphQL("Invalid token") }
    ) : AniListApiService {
        override suspend fun getViewer(request: GraphQLRequest) = viewerResponse()
        override suspend fun getUserByName(request: GraphQLRequest): GraphQLResponse<UserByNameData> =
            GraphQLResponse(null, listOf(GraphQLError("User not found", 404)))

        override suspend fun getMediaListCollection(
            request: GraphQLRequest
        ): GraphQLResponse<MediaListCollectionData> = GraphQLResponse(null)

        override suspend fun saveMediaListEntry(
            request: GraphQLRequest
        ): GraphQLResponse<SaveMediaListEntryData> = GraphQLResponse(null)

        // The My Entry editor's endpoints. Added to AniListApiService for the
        // editor tab, which has its own coverage in MediaEntryEditorTest. Failure
        // classification is not what these tests are about, so they answer with no
        // data rather than growing a fake that can drift.
        override suspend fun getMediaListEntry(
            request: GraphQLRequest
        ): GraphQLResponse<MediaListEntryData> = GraphQLResponse(null)

        override suspend fun deleteMediaListEntry(
            request: GraphQLRequest
        ): GraphQLResponse<DeleteMediaListEntryData> = GraphQLResponse(null)

        override suspend fun getMediaDetail(request: GraphQLRequest): GraphQLResponse<MediaDetailData> =
            GraphQLResponse(null)
        // Added to AniListApiService for the markdown preview covers. These tests
        // cover error classification, which this endpoint takes no part in, so it
        // answers with no data rather than growing a fake that can drift.
        override suspend fun getMediaCovers(
            request: GraphQLRequest
        ): GraphQLResponse<Map<String, MediaCoverPage>> = GraphQLResponse(emptyMap())

        // Added with the entity previews for the same reason as the covers above.
        override suspend fun getEntityPreviews(
            request: GraphQLRequest
        ): GraphQLResponse<EntityPreviewPages> = GraphQLResponse(EntityPreviewPages())

        // The profile screen's four endpoints. Thrown rather than returned as an
        // empty response on purpose: if a test ever starts calling one, that is a
        // change of intent, and a blank `GraphQLResponse` would let it keep
        // passing against data the fake invented.
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

        override suspend fun saveActivityReply(
            request: GraphQLRequest
        ): GraphQLResponse<SaveActivityReplyData> = notServed("saveActivityReply")

        override suspend fun deleteActivityReply(
            request: GraphQLRequest
        ): GraphQLResponse<DeleteActivityReplyData> = notServed("deleteActivityReply")

        // The two person-profile endpoints. Same reason as `getStudio` below: a
        // new endpoint on the interface breaks every fake in the test source set
        // until it is answered.
        override suspend fun getCharacterProfile(
            request: GraphQLRequest
        ): GraphQLResponse<CharacterProfileData> = notServed("getCharacterProfile")

        override suspend fun getStaffProfile(
            request: GraphQLRequest
        ): GraphQLResponse<StaffProfileData> = notServed("getStaffProfile")

        // The reply like, added with the comments feature. Same reason as `getStudio`
        // below: a new endpoint on the interface stops the test *module* compiling
        // until every fake answers it.
        override suspend fun toggleActivityReplyLike(
            request: GraphQLRequest
        ): GraphQLResponse<ToggleLikeData> = notServed("toggleActivityReplyLike")

        // The studio page's endpoint, added to `AniListApiService` with it. Every
        // fake in this file has to implement it or the test *module* stops
        // compiling - which is what happened: the app compiled, the tests did not.
        // `notServed` rather than a blank response, for the reason above.
        override suspend fun getStudio(
            request: GraphQLRequest
        ): GraphQLResponse<StudioData> = notServed("getStudio")

        // Added with the status strip. Failure classification never asks whether
        // AniList is reachable, so an accidental call should fail loudly.
        override suspend fun ping(
            request: GraphQLRequest
        ): GraphQLResponse<ApiStatusData> = notServed("ping")
    }

    private suspend fun failureOf(service: AniListApiService): Throwable {
        val result = AniListRepositoryImpl(service).getViewer()
        assertTrue("expected a failure, got $result", result.isFailure)
        return result.exceptionOrNull()!!
    }

    @Test
    fun `a rejected token is classified as a dead session`() = kotlinx.coroutines.test.runTest {
        val error = failureOf(FakeService())

        assertEquals(AniListErrorKind.INVALID_SESSION, (error as AniListException).kind)
    }

    @Test
    fun `rate limiting keeps the wait time the server asked for`() {
        // AniList sends Retry-After with every 429 and the message the user
        // reads is built from it, so it has to survive the mapping.
        val error = aniListHttpError(code = 429, retryAfterSeconds = 30)

        assertEquals(AniListErrorKind.RATE_LIMITED, error.kind)
        assertEquals(30, error.retryAfterSeconds)
        assertTrue(error.message.orEmpty().contains("30s"))
    }

    @Test
    fun `rate limiting without a header still says something useful`() {
        val error = aniListHttpError(code = 429)

        assertEquals(AniListErrorKind.RATE_LIMITED, error.kind)
        assertTrue(error.message.orEmpty().contains("60s"))
    }

    @Test
    fun `a negative retry-after is not repeated to the reader`() {
        // `Retry-After` is free text, and the interceptor already refuses to
        // sleep on a negative one. It must not become copy either: the old
        // mapping printed "Try again in -1s." and the app-wide notice repeated
        // the number, which reads as a broken app rather than a wait.
        val error = aniListHttpError(code = 429, retryAfterSeconds = -1)

        assertEquals(AniListErrorKind.RATE_LIMITED, error.kind)
        assertNull("a negative wait is not a wait", error.retryAfterSeconds)
        assertTrue(error.message.orEmpty().contains("60s"))
    }

    @Test
    fun `an unauthorized response is a dead session, not a server fault`() = kotlinx.coroutines.test.runTest {
        val error = failureOf(
            FakeService { throw HttpException(retrofit2.Response.error<Any>(401, "".toResponseBody(null))) }
        ) as AniListException

        assertEquals(AniListErrorKind.INVALID_SESSION, error.kind)
    }

    @Test
    fun `a server fault is reported as retryable rather than as a bad token`() = kotlinx.coroutines.test.runTest {
        // This is the exact failure the old list query produced: AniList answers
        // HTTP 500 for large accounts, and the user must be told to retry
        // rather than signed out.
        val error = failureOf(
            FakeService { throw HttpException(retrofit2.Response.error<Any>(500, "".toResponseBody(null))) }
        ) as AniListException

        assertEquals(AniListErrorKind.SERVER, error.kind)
    }

    @Test
    fun `being offline is its own kind so the UI can say so`() = kotlinx.coroutines.test.runTest {
        val error = failureOf(FakeService { throw IOException("Unable to resolve host") }) as AniListException

        assertEquals(AniListErrorKind.OFFLINE, error.kind)
    }

    @Test
    fun `a GraphQL rate limit error is classified even without an HTTP status`() = kotlinx.coroutines.test.runTest {
        val error = failureOf(FakeService { errorGraphQL("Too Many Requests.", 429) }) as AniListException

        assertEquals(AniListErrorKind.RATE_LIMITED, error.kind)
    }

    /**
     * A body this build cannot parse is neither a dead session nor a network
     * blip, and the raw Moshi message names no field a user knows about:
     *
     *     Expected an int but was WINTER at path $.data.MediaListCollection
     *     .lists[0].entries[1].media.relations.edges[2].node.season
     *
     * That was the entire text of the error screen. It is now a sentence, and
     * it keeps its own kind so the UI does not offer "Sign in again" for it -
     * re-authenticating cannot make an unparseable response parse.
     */
    @Test
    fun `an unparseable body is neither a dead session nor a raw Moshi message`() =
        kotlinx.coroutines.test.runTest {
            val error = failureOf(
                FakeService {
                    throw JsonDataException(
                        "Expected an int but was WINTER at path " +
                            "\$.data.MediaListCollection.lists[0].entries[1].media" +
                            ".relations.edges[2].node.season"
                    )
                }
            ) as AniListException

            assertEquals(AniListErrorKind.MALFORMED_RESPONSE, error.kind)
            assertFalse(
                "the JSON path must not be shown to the user: ${error.message}",
                error.message.orEmpty().contains("\$.data")
            )
            assertTrue(
                "the message should say what to do: ${error.message}",
                error.message.orEmpty().contains("Update AniSequel")
            )
        }
}

private fun errorGraphQL(message: String, status: Int? = null) =
    GraphQLResponse<ViewerData>(null, listOf(GraphQLError(message, status)))

/**
 * Which requests may share a response.
 *
 * `isReadOnly` was `MUTATION_PATTERN.containsMatchIn(...)` with no negation, so
 * it returned `true` for mutations and `false` for queries. Two real bugs fell
 * out of that one missing `!`: reads were never coalesced (the feature did
 * nothing), and two concurrent `SaveMediaListEntry` calls collapsed into one,
 * silently dropping a write the user had asked for.
 *
 * A dropped Planning entry is the worst kind of bug for this app to have - the
 * UI confirms the save, AniList never receives it, and nothing reports a
 * failure.
 */
class RequestCoalescingPolicyTest {

    private class CountingService : AniListApiService {
        val viewerCalls = mutableListOf<GraphQLRequest>()
        val saveCalls = mutableListOf<GraphQLRequest>()
        val userByNameCalls = mutableListOf<GraphQLRequest>()
        val release = kotlinx.coroutines.CompletableDeferred<Unit>()

        override suspend fun getViewer(request: GraphQLRequest): GraphQLResponse<ViewerData> {
            viewerCalls += request
            release.await()
            return GraphQLResponse(ViewerData(ViewerProfile(id = 1, name = "Tester")))
        }

        override suspend fun saveMediaListEntry(
            request: GraphQLRequest
        ): GraphQLResponse<SaveMediaListEntryData> {
            saveCalls += request
            release.await()
            return GraphQLResponse(SaveMediaListEntryData(SimpleMediaListEntry(id = 1)))
        }

        // The My Entry editor's endpoints. Coalescing is asserted against
        // getViewer, saveMediaListEntry and getUserByName, and the editor has its
        // own coverage in MediaEntryEditorTest, so these answer with no data
        // rather than growing a fake that can drift.
        override suspend fun getMediaListEntry(
            request: GraphQLRequest
        ): GraphQLResponse<MediaListEntryData> = GraphQLResponse(null)

        override suspend fun deleteMediaListEntry(
            request: GraphQLRequest
        ): GraphQLResponse<DeleteMediaListEntryData> = GraphQLResponse(null)

        // Counted rather than silent because the sequential-repeat test below
        // needs a read with no snapshot cache behind it: `getViewer` carries a
        // deliberate thirty-minute one now, so counting *its* calls would assert
        // the cache instead of the coalescer.
        override suspend fun getUserByName(request: GraphQLRequest): GraphQLResponse<UserByNameData> {
            userByNameCalls += request
            return GraphQLResponse(null)
        }

        override suspend fun getMediaListCollection(
            request: GraphQLRequest
        ): GraphQLResponse<MediaListCollectionData> = GraphQLResponse(null)

        override suspend fun getMediaDetail(request: GraphQLRequest): GraphQLResponse<MediaDetailData> =
            GraphQLResponse(null)

        // Added to AniListApiService for the markdown preview covers. These tests
        // cover error classification, which this endpoint takes no part in, so it
        // answers with no data rather than growing a fake that can drift.
        override suspend fun getMediaCovers(
            request: GraphQLRequest
        ): GraphQLResponse<Map<String, MediaCoverPage>> = GraphQLResponse(emptyMap())

        // Added with the entity previews for the same reason as the covers above.
        override suspend fun getEntityPreviews(
            request: GraphQLRequest
        ): GraphQLResponse<EntityPreviewPages> = GraphQLResponse(EntityPreviewPages())

        // The profile screen's four endpoints. Thrown rather than returned as an
        // empty response on purpose: if a test ever starts calling one, that is a
        // change of intent, and a blank `GraphQLResponse` would let it keep
        // passing against data the fake invented.
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

        override suspend fun saveActivityReply(
            request: GraphQLRequest
        ): GraphQLResponse<SaveActivityReplyData> = notServed("saveActivityReply")

        override suspend fun deleteActivityReply(
            request: GraphQLRequest
        ): GraphQLResponse<DeleteActivityReplyData> = notServed("deleteActivityReply")

        // The two person-profile endpoints. Same reason as `getStudio` below: a
        // new endpoint on the interface breaks every fake in the test source set
        // until it is answered.
        override suspend fun getCharacterProfile(
            request: GraphQLRequest
        ): GraphQLResponse<CharacterProfileData> = notServed("getCharacterProfile")

        override suspend fun getStaffProfile(
            request: GraphQLRequest
        ): GraphQLResponse<StaffProfileData> = notServed("getStaffProfile")

        // See the note on the same override in `FakeService`.
        override suspend fun toggleActivityReplyLike(
            request: GraphQLRequest
        ): GraphQLResponse<ToggleLikeData> = notServed("toggleActivityReplyLike")

        // See the note on the same override in `FakeService`: a new endpoint on the
        // interface breaks every fake in the test source set until it is answered.
        override suspend fun getStudio(
            request: GraphQLRequest
        ): GraphQLResponse<StudioData> = notServed("getStudio")

        // See the note on the same override in `FakeService`.
        override suspend fun ping(
            request: GraphQLRequest
        ): GraphQLResponse<ApiStatusData> = notServed("ping")
    }

    @Test
    fun `identical concurrent reads cost one request`() = kotlinx.coroutines.test.runTest {
        val service = CountingService()
        val repository = AniListRepositoryImpl(service)

        val reads = List(3) { async { repository.getViewer() } }
        testScheduler.advanceUntilIdle()
        service.release.complete(Unit)
        reads.forEach { it.await() }

        assertEquals(
            "concurrent identical reads must share one response",
            1,
            service.viewerCalls.size
        )
    }

    @Test
    fun `concurrent writes are never collapsed into one`() = kotlinx.coroutines.test.runTest {
        val service = CountingService()
        val repository = AniListRepositoryImpl(service)

        // The same entry, twice, concurrently - two taps of the same card while the
        // first is still in flight. Different ids would not prove anything here:
        // the coalescing key includes the variables, so two different ids can
        // never collide and would pass even with the policy inverted. Only an
        // identical concurrent mutation actually exercises it.
        val writes = listOf(
            async { repository.addToPlanning(111) },
            async { repository.addToPlanning(111) }
        )
        testScheduler.advanceUntilIdle()
        service.release.complete(Unit)
        writes.forEach { it.await() }

        assertEquals(
            "a mutation must never be collapsed into another request",
            2,
            service.saveCalls.size
        )
        assertTrue(
            service.saveCalls.all { it.variables["mediaId"] == 111 }
        )
    }

    @Test
    fun `a sequential repeat of a read is not served from the previous one`() =
        kotlinx.coroutines.test.runTest {
            val service = CountingService()
            val repository = AniListRepositoryImpl(service)

            // Released up front so each call returns immediately: this test is
            // about two *sequential* requests, not about overlapping ones.
            service.release.complete(Unit)

            // `getViewer` cannot carry this assertion any more: the viewer's own
            // profile is deliberately served from a thirty-minute snapshot
            // (`RepositorySnapshotsTest` pins the window), so a second sequential
            // read never reaches the service. The policy is the same question
            // asked of a read with no cache behind it: a *finished* call must
            // leave the in-flight map.
            repository.getUserByName("Tester")
            repository.getUserByName("Tester")

            assertEquals(
                "a completed read must not be served from the previous one",
                2,
                service.userByNameCalls.size
            )
        }
}

/**
 * The classification of the responses AniList *actually* returns.
 *
 * Recorded verbatim against `https://graphql.anilist.co` rather than written
 * from the docs, because the docs do not list status codes at all and the whole
 * failure here was a disagreement between the status and the body:
 *
 *   bad token   -> 400 {"errors":[{"message":"Invalid token","status":400}]}
 *   no token    -> 401 {"errors":[{"message":"Unauthorized.","status":401}]}
 *   no such user-> 404 {"errors":[{"message":"Not Found.","status":404}]}
 *   bad query   -> 400 {"errors":[{"message":"Cannot query field ...","status":400}]}
 *
 * AniList uses **400 for a rejected token**, which is the same status it uses
 * for a malformed query. Classifying on the status alone therefore made every
 * dead session an "unknown error": the dashboard offered a Retry button that
 * could never succeed rather than "Sign in again", and `AuthViewModel` - which
 * only discards a token AniList refused - stored the refused token anyway.
 */
class AniListLiveResponseClassificationTest {

    private val moshi = Moshi.Builder().build()
    private val envelopeAdapter = moshi.adapter(AniListErrorEnvelope::class.java)

    private fun httpErrorOf(status: Int, body: String): AniListException {
        val rawBody = body.toResponseBody("application/json".toMediaType())
        val exception = HttpException(retrofit2.Response.error<Any>(status, rawBody))

        // The repository's own path: Retrofit throws away the body, so the
        // reason is recovered from it before the status is classified.
        val reported = parseAniListErrorBody(rawBody.string())?.errors?.firstOrNull()
        return aniListHttpError(code = status, cause = exception, reportedError = reported)
    }

    @Test
    fun `an invalid token reported as HTTP 400 is a dead session`() {
        val error = httpErrorOf(
            400,
            """{"data":null,"errors":[{"message":"Invalid token","status":400}]}"""
        )

        assertEquals(
            "AniList answers a refused token with 400, not 401 - without reading the " +
                "body this is indistinguishable from a malformed query",
            AniListErrorKind.INVALID_SESSION,
            error.kind
        )
        assertTrue(
            "the paste dialog needs to be able to name the failure",
            error.message.orEmpty().contains("Session expired")
        )
    }

    @Test
    fun `a missing token reported as HTTP 401 is a dead session`() {
        val error = httpErrorOf(
            401,
            """{"errors":[{"message":"Unauthorized.","status":401}],"data":{"Viewer":null}}"""
        )

        assertEquals(AniListErrorKind.INVALID_SESSION, error.kind)
    }

    @Test
    fun `an unknown user is still a missing resource rather than a dead session`() {
        val error = httpErrorOf(
            404,
            """{"errors":[{"message":"Not Found.","status":404}],"data":{"User":null}}"""
        )

        assertEquals(AniListErrorKind.NOT_FOUND, error.kind)
    }

    @Test
    fun `a genuinely malformed query is not blamed on the session`() {
        // Same HTTP 400 as a refused token. Reading only the status would sign a
        // working user out over a typo in a query.
        val error = httpErrorOf(
            400,
            // A raw string passes backslashes through verbatim, so the JSON
            // escape for the embedded quotes is a single backslash each - `\\"`
            // would be an escaped *backslash* and then a bare quote, which is
            // not JSON at all.
            """{"errors":[{"message":"Cannot query field \"Nope\" on type \"Query\".","status":400}],"data":null}"""
        )

        assertEquals(
            "a typo in a query must not be reported as a dead session",
            AniListErrorKind.INVALID_REQUEST,
            error.kind
        )
    }

    @Test
    fun `a body that is missing or unreadable does not replace the real failure`() {
        // Retrofit hands over an empty body for some failures; classifying must
        // never throw or invent a reason.
        assertNull(parseAniListErrorBody(null))
        assertNull(parseAniListErrorBody(""))
        assertNull(parseAniListErrorBody("<html>502 Bad Gateway</html>"))

        val error = aniListHttpError(code = 500, reportedError = null)
        assertEquals(AniListErrorKind.SERVER, error.kind)
    }

    @Test
    fun `the envelope AniList sends parses into the error it describes`() {
        val envelope = envelopeAdapter.fromJson(
            """{"data":null,"errors":[{"message":"Invalid token","status":400}]}"""
        )

        assertEquals(listOf(GraphQLError("Invalid token", 400)), envelope?.errors)
    }
}

/**
 * For the endpoints a fake deliberately does not serve.
 *
 * A test double is allowed to answer "I don't do that", but it must say so rather
 * than answering with an empty response: `GraphQLResponse(null)` parses into a
 * `null` payload, which the repository turns into a `NOT_FOUND` failure that reads
 * like AniList being unhelpful. A fake that claims a screen it does not implement
 * would let that sentence be tested by accident.
 */
private fun notServed(operation: String): Nothing =
    throw UnsupportedOperationException("this fake does not serve $operation")
