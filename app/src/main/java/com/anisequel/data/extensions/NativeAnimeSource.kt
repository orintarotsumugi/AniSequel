package com.anisequel.data.extensions

import kotlinx.coroutines.delay
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.URLEncoder

/**
 * The contract a source compiled into this app implements.
 *
 * It is deliberately smaller than Aniyomi's source API: a title search, a detail
 * lookup, and the episodes/servers a title carries. That is the whole surface the
 * Watch page needs, and it is what makes the bundled source real rather than a
 * mock - search, episode discovery and playback all run against a live HTTP API.
 *
 * Third-party APK/JS extensions do not implement this interface; they need a
 * sandboxed loader, which is Phase 2 in `ANIME-WATCH-PHASES.md`.
 */
interface NativeAnimeSource {
    val id: String
    val name: String
    val kind: ExtensionKind
    val content: ExtensionContent
    val language: String
    val nsfw: Boolean
    val note: String

    suspend fun search(query: String): List<SourceAnime>
    suspend fun details(animeId: String): SourceDetails
}

/**
 * Internet Archive as a real, legal, playable source.
 *
 * It is not a pirate index: every file it returns is an item Archive.org itself
 * hosts and serves, and most of the animation in it is public domain or
 * Creative Commons. That makes it the honest demo for a beta whose third-party
 * runtimes are still being written - the reader can search a title, pick an
 * episode, choose a server (the Archive reports each file format as one) and
 * watch it end to end.
 *
 * The mapping is item -> title and video file -> episode. Archive items are
 * frequently one film, in which case there is exactly one episode, which is the
 * right answer for a film.
 */
class ArchiveAnimeSource : NativeAnimeSource {

    /**
     * Archive.org metadata is large and stable for minutes; the Watch page asks
     * for it again on every return from the player. A tiny access-ordered cache
     * turns that into one request per title and keeps the episode list instant
     * when switching servers. It is bounded because a source that remembers an
     * unbounded search history is a leak with a nicer name.
     */
    private val cacheLock = Any()
    private val searchCache = object : LinkedHashMap<String, Cached<List<SourceAnime>>>(
        12,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Cached<List<SourceAnime>>>?
        ): Boolean = size > 12
    }
    private val detailsCache = object : LinkedHashMap<String, Cached<SourceDetails>>(
        12,
        0.75f,
        true
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, Cached<SourceDetails>>?
        ): Boolean = size > 24
    }

    override val id = "native:archive"
    override val name = "Internet Archive"
    override val kind = ExtensionKind.ANIYOMI
    override val content = ExtensionContent.ANIME
    override val language = "en"
    override val nsfw = false
    override val note = "Public-domain and Creative Commons animation. Playable in this beta."

    override suspend fun search(query: String): List<SourceAnime> {
        val cleaned = query.trim()
        if (cleaned.isBlank()) return emptyList()
        val key = cleaned.lowercase()
        cached(searchCache, key, SEARCH_TTL_MS)?.let { return it }

        val strict = searchOnce(
            "($cleaned) AND mediatype:(movies) AND " +
                "(subject:(animation) OR subject:(anime) OR subject:(cartoons))"
        )
        if (strict.isNotEmpty()) {
            remember(searchCache, key, strict)
            return strict
        }
        // A narrower subject filter is the right first question and the wrong
        // only question: Archive.org's animation tagging is volunteer work, and a
        // perfectly good item can carry no subject at all. One fallback keeps a
        // title that exists from reading as a missing source.
        val fallback = searchOnce("($cleaned) AND mediatype:(movies)")
        remember(searchCache, key, fallback)
        return fallback
    }

    private suspend fun searchOnce(query: String): List<SourceAnime> {
        val url = buildString {
            append("https://archive.org/advancedsearch.php?q=")
            append(URLEncoder.encode(query, "UTF-8"))
            append("&fl%5B%5D=identifier&fl%5B%5D=title&fl%5B%5D=year")
            append("&fl%5B%5D=description&fl%5B%5D=downloads")
            append("&rows=30&page=1&output=json&sort%5B%5D=downloads+desc")
        }
        val body = ExtensionHttp.getText(url)
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IOException("Archive.org answered with something other than JSON")
        val docs = root.optJSONObject("response")?.optJSONArray("docs")
            ?: throw IOException("Archive.org did not return a result page")

        val out = ArrayList<SourceAnime>(docs.length())
        for (index in 0 until docs.length()) {
            val doc = docs.optJSONObject(index) ?: continue
            val identifier = doc.textOf("identifier") ?: continue
            val title = doc.textOf("title") ?: identifier
            val cover = "https://archive.org/services/img/${encodePathSegment(identifier)}"
            out += SourceAnime(
                id = identifier,
                title = title,
                coverUrl = cover,
                description = doc.textOf("description")?.stripMarkup()?.take(400),
                year = doc.textOf("year")?.take(4)?.toIntOrNull(),
                score = null
            )
        }
        return out
    }

    override suspend fun details(animeId: String): SourceDetails {
        cached(detailsCache, animeId, DETAILS_TTL_MS)?.let { return it }
        val metadataUrl = "https://archive.org/metadata/${encodePathSegment(animeId)}"
        val body = ExtensionHttp.getText(metadataUrl)
        val root = runCatching { JSONObject(body) }.getOrNull()
            ?: throw IOException("Archive.org answered with something other than JSON")
        val metadata = root.optJSONObject("metadata") ?: JSONObject()
        val files = root.optJSONArray("files") ?: JSONArray()

        val title = metadata.textOf("title") ?: animeId
        val description = metadata.textOf("description")?.stripMarkup()
            ?: metadata.textOf("synopsis")?.stripMarkup()
        val cover = "https://archive.org/services/img/${encodePathSegment(animeId)}"

        val episodes = episodesFrom(files, animeId, cover)
        if (episodes.isEmpty()) {
            throw IOException("This Archive item has no video file")
        }
        val details = SourceDetails(
            anime = SourceAnime(
                id = animeId,
                title = title,
                coverUrl = cover,
                description = description,
                year = metadata.textOf("year")?.take(4)?.toIntOrNull(),
                score = null
            ),
            description = description,
            episodes = episodes
        )
        remember(detailsCache, animeId, details)
        return details
    }

    private fun <T> cached(
        map: LinkedHashMap<String, Cached<T>>,
        key: String,
        ttlMs: Long
    ): T? = synchronized(cacheLock) {
        val entry = map[key]
        when {
            entry == null -> null
            System.currentTimeMillis() - entry.at > ttlMs -> {
                map.remove(key)
                null
            }
            else -> entry.value
        }
    }

    private fun <T> remember(
        map: LinkedHashMap<String, Cached<T>>,
        key: String,
        value: T
    ) {
        synchronized(cacheLock) {
            map[key] = Cached(System.currentTimeMillis(), value)
        }
    }

    private fun episodesFrom(
        files: JSONArray,
        identifier: String,
        cover: String
    ): List<SourceEpisode> {
        data class FileServer(
            val fileName: String,
            val title: String,
            val format: String,
            val quality: String?,
            val duration: Long?,
            val sortNumber: Int?
        )

        val groups = LinkedHashMap<String, MutableList<FileServer>>()
        for (index in 0 until files.length()) {
            val file = files.optJSONObject(index) ?: continue
            val fileName = file.textOf("name") ?: continue
            val extension = fileName.substringAfterLast('.', "").lowercase()
            if (extension !in VIDEO_EXTENSIONS) continue
            val format = file.textOf("format").orEmpty()
            if (format.contains("Thumbnail", ignoreCase = true)) continue
            val base = fileName.substringBeforeLast('.')
            val key = episodeKey(base)
            val height = file.textOf("height")?.toIntOrNull()
            val quality = when {
                height != null && height > 0 -> "${height}p"
                file.textOf("width") != null -> file.textOf("width") + "w"
                else -> null
            }
            groups.getOrPut(key) { mutableListOf() } += FileServer(
                fileName = fileName,
                title = prettyName(base),
                format = format.ifBlank { extension.uppercase() },
                quality = quality,
                duration = parseLength(file.textOf("length")),
                sortNumber = numberIn(base)
            )
        }

        val ordered = groups.entries.sortedWith(
            compareBy<Map.Entry<String, MutableList<FileServer>>> { entry ->
                entry.value.firstOrNull()?.sortNumber ?: Int.MAX_VALUE
            }.thenBy { it.key.lowercase() }
        )

        var fallbackNumber = 1
        return ordered.mapIndexed { index, (_, servers) ->
            val first = servers.first()
            val number = first.sortNumber ?: (index + 1).also { fallbackNumber = it }
            SourceEpisode(
                // The episode id must identify the group, not a server: the server
                // chooser is per episode, and two formats of the same file are two
                // servers of one episode.
                id = "$identifier#$index",
                name = first.title,
                number = number,
                thumbnailUrl = cover,
                durationSeconds = first.duration,
                servers = servers.map { server ->
                    SourceServer(
                        id = "${server.format}:${server.fileName}",
                        name = server.format,
                        quality = server.quality,
                        url = downloadUrl(identifier, server.fileName)
                    )
                }
            )
        }
    }

    private fun downloadUrl(identifier: String, fileName: String): String {
        val encodedFile = fileName.split('/').joinToString("/") { encodePathSegment(it) }
        return "https://archive.org/download/${encodePathSegment(identifier)}/$encodedFile"
    }

    private fun episodeKey(base: String): String =
        base.replace(
            Regex(
                "(?i)(?:[ _.-]+(?:\\d{3,4}p|h[ .]?264|h[ .]?265|hevc|av1|aac|mp4|" +
                    "mpeg4|webm|ogv|512kb|256kb|ia))$"
            ),
            ""
        ).trim().ifBlank { base }

    private fun prettyName(base: String): String =
        base.replace(Regex("[._]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "Episode" }

    /** The first episode-like number in a file name, if there is one. */
    private fun numberIn(base: String): Int? {
        val episode = Regex("(?i)(?:episode|ep|e)\\s*0*(\\d{1,4})").find(base)
        if (episode != null) return episode.groupValues[1].toIntOrNull()
        val seasonEpisode = Regex("(?i)s\\d{1,2}e0*(\\d{1,4})").find(base)
        if (seasonEpisode != null) return seasonEpisode.groupValues[1].toIntOrNull()
        return Regex("(?:^|[ _.-])(\\d{1,4})(?:[ _.-]|$)").find(base)
            ?.groupValues?.get(1)?.toIntOrNull()
    }

    private fun parseLength(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        if ('.' in value && ':' !in value) {
            return value.toDoubleOrNull()?.toLong()
        }
        val parts = value.split(':').mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return null
        var seconds = 0L
        for (part in parts) seconds = seconds * 60 + part
        return seconds
    }

    private fun JSONObject.textOf(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return when (val value = opt(key)) {
            is String -> value.takeIf { it.isNotBlank() }
            is Number -> value.toString()
            is JSONArray -> (0 until value.length())
                .joinToString(", ") { value.optString(it) }
                .takeIf { it.isNotBlank() }
            else -> null
        }
    }

    private fun String.stripMarkup(): String = this
        .replace(Regex("<[^>]+>"), " ")
        .replace("&amp;", "&")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace(Regex("\\s+"), " ")
        .trim()

    private data class Cached<T>(val at: Long, val value: T)

    private companion object {
        val VIDEO_EXTENSIONS = setOf("mp4", "m4v", "webm", "ogv", "mkv")
        const val SEARCH_TTL_MS = 2 * 60 * 1_000L
        const val DETAILS_TTL_MS = 10 * 60 * 1_000L
    }
}
