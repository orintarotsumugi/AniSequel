package com.example

import com.example.data.model.GraphQLError
import com.example.data.model.GraphQLRequest
import com.example.data.model.GraphQLResponse
import com.example.data.model.MediaDetailData
import com.example.data.model.MediaListCollectionData
import com.example.data.model.SaveMediaListEntryData
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.UserByNameData
import com.example.data.model.ViewerData
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListApiService
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.network.aniListHttpError
import com.example.data.repository.AniListRepositoryImpl
import com.squareup.moshi.JsonDataException
import kotlinx.coroutines.async
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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

        override suspend fun getMediaDetail(request: GraphQLRequest): GraphQLResponse<MediaDetailData> =
            GraphQLResponse(null)
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

        override suspend fun getUserByName(request: GraphQLRequest): GraphQLResponse<UserByNameData> =
            GraphQLResponse(null)

        override suspend fun getMediaListCollection(
            request: GraphQLRequest
        ): GraphQLResponse<MediaListCollectionData> = GraphQLResponse(null)

        override suspend fun getMediaDetail(request: GraphQLRequest): GraphQLResponse<MediaDetailData> =
            GraphQLResponse(null)
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

            repository.getViewer()
            repository.getViewer()

            assertEquals(
                "an explicit refresh must reach the server",
                2,
                service.viewerCalls.size
            )
        }
}
