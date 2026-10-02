package com.example.data.repository

import com.example.data.model.GraphQLRequest
import com.example.data.model.GraphQLResponse
import com.example.data.model.GraphQLError
import com.example.data.model.MediaListCollection
import com.example.data.model.MediaNode
import com.example.data.model.SimpleMediaListEntry
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListApiService
import com.example.data.network.AniListErrorKind
import com.example.data.network.AniListException
import com.example.data.network.aniListHttpError
import com.example.data.network.GraphQLQueries
import retrofit2.HttpException
import java.io.IOException

class AniListRepositoryImpl(
    private val apiService: AniListApiService
) : AniListRepository {

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

    override suspend fun getUserAnimeList(userId: Int): Result<MediaListCollection> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_ANIME_LIST,
                variables = mapOf("userId" to userId)
            ),
            apiService::getMediaListCollection
        ).map { it.collection.require("Media list collection is empty") }

    override suspend fun getUserAnimeListByUsername(userName: String): Result<MediaListCollection> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_USER_ANIME_LIST,
                variables = mapOf("userName" to userName.trim())
            ),
            apiService::getMediaListCollection
        ).map { it.collection.require("Media list collection is empty") }

    /**
     * The heavy per-entry fields, fetched for the one sequel the user opens.
     *
     * Keeping these out of the list query is what stops AniList returning HTTP
     * 500 for large accounts, and it means opening a card no longer re-parses
     * 1,500 nodes' worth of synopses.
     */
    override suspend fun getMediaDetail(mediaId: Int): Result<MediaNode> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.GET_MEDIA_DETAIL,
                variables = mapOf("id" to mediaId)
            ),
            apiService::getMediaDetail
        ).map { it.media.require("That entry is no longer available on AniList.") }

    override suspend fun addToPlanning(mediaId: Int): Result<SimpleMediaListEntry> =
        execute(
            GraphQLRequest(
                query = GraphQLQueries.ADD_TO_PLANNING,
                variables = mapOf("mediaId" to mediaId)
            ),
            apiService::saveMediaListEntry
        ).map { it.entry.require("No response from planning mutation") }

    override fun getDemoProfile(): ViewerProfile = DemoDataProvider.getDemoViewer()

    override fun getDemoAnimeList(): MediaListCollection = DemoDataProvider.getDemoMediaList()

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
            val response = call(request)
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

    private fun HttpException.toAniListException(): AniListException =
        aniListHttpError(
            code = code(),
            retryAfterSeconds = response()?.headers()?.get("Retry-After")?.toIntOrNull(),
            cause = this
        )

    private fun GraphQLError.toAniListException(): AniListException {
        val text = message.orEmpty()
        return when {
            status == 429 -> AniListException(
                kind = AniListErrorKind.RATE_LIMITED,
                message = "AniList is rate limiting this device. Try again in a moment."
            )
            text.contains("invalid", ignoreCase = true) ||
                text.contains("token", ignoreCase = true) ||
                text.contains("unauthorized", ignoreCase = true) -> AniListException(
                kind = AniListErrorKind.INVALID_SESSION,
                message = "Session expired. Please re-authenticate."
            )
            text.contains("not found", ignoreCase = true) ->
                AniListException(AniListErrorKind.NOT_FOUND, text)
            else -> AniListException(AniListErrorKind.INVALID_REQUEST, text)
        }
    }
}