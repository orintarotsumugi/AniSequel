package com.example

import com.example.data.model.GraphQLError
import com.example.data.model.GraphQLRequest
import com.example.data.model.GraphQLResponse
import com.example.data.model.MediaDetailData
import com.example.data.model.MediaListCollectionData
import com.example.data.model.SaveMediaListEntryData
import com.example.data.model.UserByNameData
import com.example.data.model.ViewerData
import com.example.data.network.AniListApiService
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.network.aniListHttpError
import com.example.data.repository.AniListRepositoryImpl
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
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
}

private fun errorGraphQL(message: String, status: Int? = null) =
    GraphQLResponse<ViewerData>(null, listOf(GraphQLError(message, status)))
