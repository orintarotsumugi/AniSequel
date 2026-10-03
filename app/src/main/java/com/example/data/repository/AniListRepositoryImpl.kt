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
import com.example.data.network.RequestCoalescer
import com.squareup.moshi.JsonDataException
import retrofit2.HttpException
import java.io.IOException

/**
 * Matches the `mutation` keyword of a GraphQL document.
 *
 * Anchored so a `mutation` appearing inside a string or comment in a *query*
 * cannot flip the classification.
 */
private val MUTATION_PATTERN = Regex("""^\s*mutation\b""")

class AniListRepositoryImpl(
    private val apiService: AniListApiService
) : AniListRepository {

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
    private val detailCache = java.util.concurrent.ConcurrentHashMap<Int, MediaNode>()

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

    override fun getDemoProfile(): ViewerProfile = DemoDataProvider.getDemoViewer()

    override fun getDemoAnimeList(): MediaListCollection = DemoDataProvider.getDemoMediaList()

    override fun clearDetailCache() {
        detailCache.clear()
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