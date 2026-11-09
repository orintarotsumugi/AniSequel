package com.anisequel.data.extensions.runtime

import android.util.Base64
import com.anisequel.data.extensions.SourceAnime
import com.anisequel.data.extensions.SourceDetails
import com.anisequel.data.extensions.SourceEpisode
import com.anisequel.data.extensions.SourceServer
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/** One source a loaded extension published, as the host learns it. */
data class RuntimeSourceInfo(
    val id: String,
    val name: String,
    val lang: String,
    val nsfw: Boolean
)

/** A proxied HTTP request the runtime asks the host to perform. */
data class RuntimeFetchRequest(
    val url: String,
    val method: String,
    val headers: List<Pair<String, String>>,
    val body: ByteArray?
)

/**
 * The JSON wire format between the host and the isolated runtime.
 *
 * Every AIDL string is an envelope: `{"ok":true,...}` or `{"ok":false,"error":...}`.
 * Keeping the protocol in one shared object means both processes encode the
 * same shapes and a JVM test can pin them without a device. DTOs travel as their
 * own field names so a reader can debug a payload by eye.
 */
object RuntimeProtocol {

    fun envelope(build: () -> JSONObject): String = try {
        val body = build()
        body.put("ok", true)
        body.toString()
    } catch (cancelled: kotlinx.coroutines.CancellationException) {
        throw cancelled
    } catch (error: Throwable) {
        JSONObject().put("ok", false).put("error", error.message ?: error.javaClass.simpleName).toString()
    }

    fun unwrap(payload: String): JSONObject {
        val root = runCatching { JSONObject(payload) }.getOrNull()
            ?: throw IOException("The extension runtime returned malformed data")
        if (!root.optBoolean("ok", false)) {
            throw IOException(root.optString("error", "The extension runtime failed"))
        }
        return root
    }

    fun loadRequest(packageName: String, sourceClasses: List<String>, nsfw: Boolean, hostUid: Int): String =
        JSONObject()
            .put("packageName", packageName)
            .put("sourceClasses", JSONArray(sourceClasses))
            .put("nsfw", nsfw)
            .put("hostUid", hostUid)
            .toString()

    fun loadRequestFrom(json: JSONObject): LoadRequest = LoadRequest(
        packageName = json.getString("packageName"),
        sourceClasses = json.optJSONArray("sourceClasses").toStringList(),
        nsfw = json.optBoolean("nsfw", false),
        hostUid = json.optInt("hostUid", -1)
    )

    data class LoadRequest(
        val packageName: String,
        val sourceClasses: List<String>,
        val nsfw: Boolean,
        val hostUid: Int
    )

    fun loadResult(sources: List<RuntimeSourceInfo>): JSONObject = JSONObject().put(
        "sources",
        JSONArray().apply {
            sources.forEach { source ->
                put(
                    JSONObject()
                        .put("id", source.id)
                        .put("name", source.name)
                        .put("lang", source.lang)
                        .put("nsfw", source.nsfw)
                )
            }
        }
    )

    fun loadResultFrom(json: JSONObject): List<RuntimeSourceInfo> =
        json.optJSONArray("sources").toObjects().map { source ->
            RuntimeSourceInfo(
                id = source.getString("id"),
                name = source.optString("name"),
                lang = source.optString("lang", "en"),
                nsfw = source.optBoolean("nsfw", false)
            )
        }

    fun animeToJson(anime: SourceAnime): JSONObject = JSONObject()
        .put("id", anime.id)
        .put("title", anime.title)
        .put("coverUrl", anime.coverUrl ?: JSONObject.NULL)
        .put("description", anime.description ?: JSONObject.NULL)
        .put("year", anime.year ?: JSONObject.NULL)
        .put("score", anime.score ?: JSONObject.NULL)

    fun animeFrom(json: JSONObject): SourceAnime = SourceAnime(
        id = json.getString("id"),
        title = json.optString("title"),
        coverUrl = json.nullableString("coverUrl"),
        description = json.nullableString("description"),
        year = json.nullableInt("year"),
        score = json.nullableInt("score")
    )

    fun serverToJson(server: SourceServer): JSONObject = JSONObject()
        .put("id", server.id)
        .put("name", server.name)
        .put("url", server.url)
        .put("quality", server.quality ?: JSONObject.NULL)
        .put("headers", JSONObject().apply { server.headers.forEach { (k, v) -> put(k, v) } })

    fun serverFrom(json: JSONObject): SourceServer {
        val headers = json.optJSONObject("headers")
        val map = LinkedHashMap<String, String>()
        headers?.keys()?.forEach { key -> map[key] = headers.optString(key) }
        return SourceServer(
            id = json.getString("id"),
            name = json.optString("name"),
            url = json.getString("url"),
            quality = json.nullableString("quality"),
            headers = map
        )
    }

    fun episodeToJson(episode: SourceEpisode): JSONObject = JSONObject()
        .put("id", episode.id)
        .put("name", episode.name)
        .put("number", episode.number)
        .put("thumbnailUrl", episode.thumbnailUrl ?: JSONObject.NULL)
        .put("durationSeconds", episode.durationSeconds ?: JSONObject.NULL)
        .put("servers", JSONArray().apply { episode.servers.forEach { put(serverToJson(it)) } })

    fun episodeFrom(json: JSONObject): SourceEpisode = SourceEpisode(
        id = json.getString("id"),
        name = json.optString("name"),
        number = json.optInt("number"),
        thumbnailUrl = json.nullableString("thumbnailUrl"),
        durationSeconds = json.nullableLong("durationSeconds"),
        servers = json.optJSONArray("servers").toObjects().map { serverFrom(it) }
    )

    fun episodesToJson(episodes: List<SourceEpisode>): JSONObject = JSONObject().put(
        "episodes",
        JSONArray().apply { episodes.forEach { put(episodeToJson(it)) } }
    )

    fun episodesFrom(json: JSONObject): List<SourceEpisode> =
        json.optJSONArray("episodes").toObjects().map { episodeFrom(it) }

    fun animesToJson(animes: List<SourceAnime>): JSONObject = JSONObject().put(
        "animes",
        JSONArray().apply { animes.forEach { put(animeToJson(it)) } }
    )

    fun animesFrom(json: JSONObject): List<SourceAnime> =
        json.optJSONArray("animes").toObjects().map { animeFrom(it) }

    fun detailsToJson(details: SourceDetails): JSONObject = JSONObject()
        .put("anime", animeToJson(details.anime))
        .put("description", details.description ?: JSONObject.NULL)
        .put("episodes", JSONArray().apply { details.episodes.forEach { put(episodeToJson(it)) } })

    fun serversToJson(servers: List<SourceServer>): JSONObject = JSONObject().put(
        "servers",
        JSONArray().apply { servers.forEach { put(serverToJson(it)) } }
    )

    fun serversFrom(json: JSONObject): List<SourceServer> =
        json.optJSONArray("servers").toObjects().map { serverFrom(it) }

    fun fetchRequestFrom(payload: String): RuntimeFetchRequest {
        val json = JSONObject(payload)
        val headers = json.optJSONArray("headers").toPairs()
        return RuntimeFetchRequest(
            url = json.getString("url"),
            method = json.optString("method", "GET"),
            headers = headers,
            body = json.nullableString("body")?.let { Base64.decode(it, Base64.NO_WRAP) }
        )
    }

    fun fetchRequest(request: RuntimeFetchRequest): String = JSONObject()
        .put("url", request.url)
        .put("method", request.method)
        .put("headers", JSONArray().apply { request.headers.forEach { (k, v) -> put(JSONArray().put(k).put(v)) } })
        .put("body", request.body?.let { Base64.encodeToString(it, Base64.NO_WRAP) } ?: JSONObject.NULL)
        .toString()

    fun fetchResponseObject(
        code: Int,
        message: String,
        url: String,
        headers: List<Pair<String, String>>,
        body: ByteArray
    ): JSONObject = JSONObject()
        .put("code", code)
        .put("message", message)
        .put("url", url)
        .put("headers", JSONArray().apply { headers.forEach { (k, v) -> put(JSONArray().put(k).put(v)) } })
        .put("body", Base64.encodeToString(body, Base64.NO_WRAP))

    fun fetchResponseFrom(json: JSONObject): FetchResponse = FetchResponse(
        code = json.getInt("code"),
        message = json.optString("message"),
        url = json.getString("url"),
        headers = json.optJSONArray("headers").toPairs(),
        body = Base64.decode(json.getString("body"), Base64.NO_WRAP)
    )

    data class FetchResponse(
        val code: Int,
        val message: String,
        val url: String,
        val headers: List<Pair<String, String>>,
        val body: ByteArray
    )

    private fun JSONObject.nullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key).takeIf { it.isNotEmpty() }

    private fun JSONObject.nullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.nullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null) return emptyList()
        return (0 until length()).map { optString(it) }
    }

    private fun JSONArray?.toObjects(): List<JSONObject> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { optJSONObject(it) }
    }

    private fun JSONArray?.toPairs(): List<Pair<String, String>> {
        if (this == null) return emptyList()
        return (0 until length()).mapNotNull { index ->
            val pair = optJSONArray(index) ?: return@mapNotNull null
            pair.optString(0) to pair.optString(1)
        }
    }
}