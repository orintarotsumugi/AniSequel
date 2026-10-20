package com.anisequel.data.network

import com.anisequel.data.model.ThemeSong
import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.Locale
import java.util.concurrent.TimeUnit

/**
 * AnimeThemes.moe's public JSON API, read for one title's openings and endings.
 *
 * ## Why AniSequel talks to a second service at all
 *
 * It does so for exactly one section. AniList's GraphQL API has **no theme-song
 * data** - there is no `openings`, no `themes`, nothing on `Media` and nothing a
 * relation could reach - so a page that shows OP/ED entries cannot get them from
 * the API the rest of the app is built on. AnimeThemes is the community database
 * for that data and its API is public, key-free and matched to AniList ids.
 *
 * ## Why the JSON API and not its GraphQL one
 *
 * AnimeThemes documents its JSON API as deprecated in favour of GraphQL, but the
 * JSON endpoint is still live and is a single GET with a tiny response. The
 * GraphQL alternative would be a second GraphQL client, a second schema and a
 * second set of generated models for one row of cards - more moving parts than the
 * feature is worth. If the JSON API is ever removed, this file is the whole blast
 * radius: the section disappears rather than the page failing, because everything
 * here answers an empty list on any failure.
 *
 * ## What it is not allowed to do
 *
 * - **Never send the AniList token.** This is a third party. The client below is
 *   its own `OkHttpClient` with no interceptors, for the same reason
 *   `WidgetCover` and `UpdateManager` build theirs.
 * - **Never block the page.** The request has short timeouts, runs on
 *   `Dispatchers.IO`, and every failure path returns an empty list. A title page
 *   that waited on a third party would trade a section for the whole screen.
 */
class AnimeThemesApi(
    private val client: OkHttpClient = defaultClient(),
    moshi: Moshi = NetworkClient.json
) {

    private val adapter = moshi.adapter(AnimeThemesResponse::class.java)

    /**
     * The themes for [anilistId], with [artworkUrl] used as each card's art.
     *
     * The API's video entries carry no thumbnail, and its only images are the
     * anime's covers - a row of six cards all showing the same poster would be
     * noise. The page hands in the title's own banner/cover instead, which at
     * least matches the page the cards sit on; the artwork is decorative and
     * the badge and song title carry the meaning.
     */
    suspend fun themes(anilistId: Int, artworkUrl: String?): List<ThemeSong> =
        withContext(Dispatchers.IO) {
            val url = runCatching {
                BASE_URL.toHttpUrl().newBuilder()
                    // `filter[has]=resources` + the AniList pair is the documented
                    // way to resolve a theme set from an AniList id; `filter[id]`
                    // is AnimeThemes' own primary key and would return nothing.
                    .addQueryParameter("filter[has]", "resources")
                    .addQueryParameter("filter[site]", "Anilist")
                    .addQueryParameter("filter[external_id]", anilistId.toString())
                    .addQueryParameter("include", "animethemes.animethemeentries.videos,animethemes.song")
                    .addQueryParameter("page[size]", "1")
                    .build()
            }.getOrNull() ?: return@withContext emptyList()

            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/json")
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@withContext emptyList()
                    val body = response.body?.string() ?: return@withContext emptyList()
                    val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                        ?: return@withContext emptyList()
                    parsed.anime.orEmpty()
                        .firstOrNull()
                        ?.animethemes.orEmpty()
                        .toThemeSongs(artworkUrl)
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                // Leaving the page cancels this; that is not a failure to report.
                throw e
            } catch (e: Exception) {
                // Offline, a 404, a CDN hiccup, a body that is suddenly HTML: all
                // of them mean "no themes to show", never "fail the title page".
                emptyList()
            }
        }

    private companion object {
        /**
         * The documented base. The path is `/anime/` on the index endpoint.
         *
         * Deliberately not `https://api.animethemes.moe/v2/...`: there is no `v2`
         * prefix on the JSON API, and a made-up one answers 404.
         */
        const val BASE_URL = "https://api.animethemes.moe/"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .callTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * The themes of one AnimeThemes anime, ordered the way the row reads.
 *
 * Pure, and separate from the request, because this is the part that has edge
 * cases (a missing sequence, an entry with no video, two entries for one OP) and
 * the part a JVM test can pin without a network.
 *
 * - The **slug** is preferred for the badge: `sequence` is documented as nullable
 *   and the fetched examples include a theme whose slug is `ED1` while its
 *   sequence is `null`.
 * - A theme with no playable video is still listed. The song is information; the
 *   card's play control simply does nothing without a URL.
 * - Openings sort before endings, then by their own label, so the row reads
 *   OP1, OP2, ED1 rather than in whatever order the service answered.
 */
internal fun List<AnimeThemeDto>.toThemeSongs(fallbackArt: String?): List<ThemeSong> =
    mapNotNull { theme ->
        val type = theme.type?.uppercase(Locale.ROOT)?.takeIf { it == "OP" || it == "ED" }
            ?: return@mapNotNull null
        val label = theme.slug?.takeIf { it.isNotBlank() }?.uppercase(Locale.ROOT)
            ?: theme.sequence?.let { "$type$it" }
            ?: type
        val video = theme.animethemeentries.orEmpty()
            .asSequence()
            .flatMap { entry -> entry.videos.orEmpty().asSequence() }
            .mapNotNull { it.link?.takeIf(String::isNotBlank) }
            .firstOrNull()
        ThemeSong(
            label = label,
            type = type,
            title = theme.song?.title?.takeIf { it.isNotBlank() },
            videoUrl = video,
            artworkUrl = fallbackArt
        )
    }
        // One card per theme: an OP with several broadcast versions is several
        // entries under one slug, and three copies of "OP1" is not three themes.
        .distinctBy { it.label }
        .sortedWith(
            compareBy({ if (it.isOpening) 0 else 1 }, { it.label })
        )

@JsonClass(generateAdapter = true)
internal data class AnimeThemesResponse(
    val anime: List<AnimeThemesAnimeDto>? = null
)

@JsonClass(generateAdapter = true)
internal data class AnimeThemesAnimeDto(
    val name: String? = null,
    val animethemes: List<AnimeThemeDto>? = null
)

@JsonClass(generateAdapter = true)
internal data class AnimeThemeDto(
    val type: String? = null,
    val sequence: Int? = null,
    val slug: String? = null,
    val song: AnimeThemeSongDto? = null,
    @Json(name = "animethemeentries") val animethemeentries: List<AnimeThemeEntryDto>? = null
)

@JsonClass(generateAdapter = true)
internal data class AnimeThemeSongDto(val title: String? = null)

@JsonClass(generateAdapter = true)
internal data class AnimeThemeEntryDto(val videos: List<AnimeThemeVideoDto>? = null)

@JsonClass(generateAdapter = true)
internal data class AnimeThemeVideoDto(val link: String? = null)
