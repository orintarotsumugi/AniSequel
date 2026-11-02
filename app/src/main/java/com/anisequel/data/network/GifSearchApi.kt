package com.anisequel.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * GIF search, from whichever provider the reader has.
 *
 * ## Why there are two of them
 *
 * Tenor used to answer anonymous searches - the public key in its documentation
 * was how every hobby client got GIFs - and it does not any more: `g.tenor.com/v1`
 * replies `{"code":7,"error":"Tenor API is discontinued"}` and `tenor.googleapis.com`
 * rejects the same string as an invalid key. Giphy's public beta key is 401,
 * DuckDuckGo's image endpoint answers a bot challenge, and Wikimedia Commons has
 * turned MIME search off at the API level. Something had to give, so the app offers
 * the two that actually answer:
 *
 * - **Safebooru**, anonymous, no account and no key, asked for `animated_gif` at
 *   `rating:safe`. Its catalogue is anime, which is what a reader on AniList is
 *   looking for, and this is the source the browser GIF pickers in that community
 *   already use.
 * - **Tenor v2**, when the reader has pasted their own key in Settings. This is
 *   what a reaction-GIF picker used to be, with the coverage that implies, and the
 *   key is theirs, obtained free from Google.
 *
 * A key is never bundled. The whole point of the anonymous default is that nobody
 * has to hand over a credential to use the app, and a committed key would be a
 * rate limit shared by every install plus a secret in a public repository.
 *
 * ## Never the AniList token
 *
 * Both clients are their own [OkHttpClient] with no interceptors, the same rule
 * [AnimeThemesApi] and [CatboxApi] record: the shared client attaches the reader's
 * bearer token to everything, and neither a search index nor a file host has any
 * business holding it.
 */
interface GifSearchApi {
    /**
     * Whether an empty answer is a gap in this provider's catalogue rather than a
     * miss.
     *
     * A booru matches tags, not sentences, so an obscure phrase can legitimately
     * return nothing - and the reader deserves the sentence that says to try fewer
     * words rather than a grid that looks broken. A tagged catalogue like Tenor's
     * has no such hole, so it does not get the explanation.
     */
    val catalogueIsNarrow: Boolean get() = false

    /**
     * One page of results for [query].
     *
     * [cursor] is whatever the previous page said to ask for next - a zero-based
     * post index for Safebooru, a `pos` token for Tenor - which is why it is a
     * string and not an int: the providers do not page the same way and neither
     * should have to pretend to. Null asks for the first page.
     *
     * A blank query is not a search: the caller is expected not to ask, and
     * implementations answer an empty page rather than an error, so a picker that
     * has not typed anything yet draws its placeholder instead of a failure.
     */
    suspend fun search(query: String, cursor: String? = null): Result<GifPage>
}

/**
 * One page of GIFs.
 *
 * [gridUrl] is what the picker draws (the small copy, animated whenever the provider
 * has an animation small enough for a tile); [embedUrl] is what the
 * comment gets, because the URL a reader pastes into a comment has to be the file
 * itself, at full size, not the thumbnail a search index uses to save bandwidth.
 */
data class GifResult(
    val id: String,
    val embedUrl: String,
    val gridUrl: String,
    val title: String?,
    val width: Int?,
    val height: Int?
)

data class GifPage(
    val results: List<GifResult>,
    val next: String? = null,
    val totalCount: Int? = null
)

/**
 * A GIF search that did not succeed, with the reason in the reader's terms.
 *
 * The three failure shapes a picker can meet - rate limited, refused, unreadable
 * answer - are worth separating from each other only in that the first tells the
 * reader to wait and the others tell them the provider itself is the problem. Each
 * is one sentence, because the panel has to show it under a search field.
 */
class GifSearchFailure(message: String) : IOException(message)

/** Tenor v2, with the reader's own key. */
class TenorGifs(
    private val apiKey: String,
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    private val adapter = moshi.adapter(TenorResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            if (apiKey.isBlank()) {
                return@withContext Result.failure(
                    GifSearchFailure("No Tenor key is saved. Add one in Settings, or leave it blank to search Safebooru.")
                )
            }

            val built = runCatching {
                ENDPOINT.toHttpUrl().newBuilder()
                    .addQueryParameter("q", term)
                    .addQueryParameter("key", apiKey.trim())
                    .addQueryParameter("limit", PAGE_SIZE.toString())
                    // Only the formats the picker actually uses. Requesting all of
                    // them makes every response several times bigger for tiles that
                    // are never drawn.
                    .addQueryParameter("media_filter", "gif,tinygif,previewgif")
                    .addQueryParameter("contentfilter", CONTENT_FILTER)
                    .apply { cursor?.let { addQueryParameter("pos", it) } }
                    .build()
            }
            val request = built.getOrNull()?.let {
                Request.Builder().url(it).header("Accept", "application/json").build()
            } ?: return@withContext Result.failure(GifSearchFailure("That search could not be built."))

            try {
                client.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            GifSearchFailure(
                                if (response.code == 403 || response.code == 400) {
                                    "Tenor rejected the saved key. Check it in Settings."
                                } else {
                                    "Tenor is not answering right now (HTTP ${response.code})."
                                }
                            )
                        )
                    }
                    val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                        ?: return@withContext Result.failure(
                            GifSearchFailure("Tenor answered with something unreadable.")
                        )
                    Result.success(parsed.toGifPage())
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(GifSearchFailure("Could not reach Tenor."))
            }
        }

    companion object {
        private const val ENDPOINT = "https://tenor.googleapis.com/v2/search"

        /**
         * Tenor's content filter, fixed rather than chosen.
         *
         * `medium` is the level a comment thread on AniList is at: enough of the
         * catalogue to be useful, without the app deciding on the reader's behalf
         * that they want everything. Making it a setting would be a second list of
         * names for one dropdown.
         */
        private const val CONTENT_FILTER = "medium"
        private const val PAGE_SIZE = 20
    }
}


/** Tenor v2's search response. */
/**
 * Tenor's media map, flattened to the two URLs this app needs.
 *
 * `gif` is the embed, `tinygif` is the tile, and `previewgif` is the fallback for a
 * record that carries only the smallest copy. A result with no GIF URL at all is
 * dropped rather than embedded as a blank. Top-level and internal so a renamed
 * field shows up in a JVM test rather than as an empty grid in somebody's picker.
 */
internal fun TenorResponse.toGifPage(): GifPage {
    val rows = results.orEmpty().filterNotNull().mapNotNull { entry ->
        val formats = entry.mediaFormats ?: return@mapNotNull null
        val embed = formats.gif?.url ?: formats.tinygif?.url ?: formats.previewGif?.url
            ?: return@mapNotNull null
        val grid = formats.tinygif?.url ?: formats.previewGif?.url ?: embed
        val dims = formats.gif?.dims
        GifResult(
            id = entry.id ?: embed,
            embedUrl = embed,
            gridUrl = grid,
            title = entry.title,
            width = dims?.getOrNull(0),
            height = dims?.getOrNull(1)
        )
    }
    // A blank `next` is Tenor's end of results; passing it back as `pos=` asked for
    // the first page again and appended it under itself.
    return GifPage(results = rows, next = next?.takeIf { rows.isNotEmpty() && it.isNotBlank() })
}

@JsonClass(generateAdapter = true)
internal data class TenorResponse(
    val results: List<TenorEntry?>? = null,
    val next: String? = null
)

@JsonClass(generateAdapter = true)
internal data class TenorEntry(
    val id: String? = null,
    val title: String? = null,
    @Json(name = "media_formats") val mediaFormats: TenorFormats? = null
)

@JsonClass(generateAdapter = true)
internal data class TenorFormats(
    val gif: TenorFormat? = null,
    val tinygif: TenorFormat? = null,
    @Json(name = "previewgif") val previewGif: TenorFormat? = null
)

@JsonClass(generateAdapter = true)
internal data class TenorFormat(
    val url: String? = null,
    val dims: List<Int>? = null
)
/**
 * Safebooru's anonymous JSON API, asked for animated GIFs at its safe rating only.
 *
 * ## Why this is the default
 *
 * It answers with **anime** reaction GIFs and it needs no key, no account and no
 * signup - which is precisely what Openverse could not do (a Creative Commons
 * index has no `spice and wolf` GIFs to give you) and what Tenor used to do before
 * it retired its public API. This is also the source the browser GIF pickers in
 * this community already reach for, so a reader who has used one meets the same
 * catalogue here.
 *
 * ## What is filtered, and twice
 *
 * `rating:safe` goes into the query, and a post whose own `rating` field is not in
 * the safe set is dropped on the way out ([isSafeRating] - the site spells its ratings
 * out as `safe`/`general`, so the check is a set of strings, not one letter). A booru
 * is user-tagged: the filter argument is a
 * request, not a guarantee, and this app ships an image picker into a comments
 * section that other people read. The tag is also the reason the search box says
 * "tags" - boorus match `spice_and_wolf holo`, not a sentence, so the query is
 * rewritten into that shape rather than passed through.
 *
 * The spelling matters as much as the filter: this used to ask for
 * `rating:general`, which the site does not know, so it matched nothing and every
 * search answered an empty page. See [REQUIRED_TAGS].
 */
class SafebooruGifs(
    private val client: OkHttpClient = defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    private val adapter = moshi.adapter<List<SafebooruPost?>>(
        Types.newParameterizedType(List::class.java, SafebooruPost::class.java)
    )

    // A booru matches tags, not sentences, so an empty answer is usually a gap in
    // the index rather than a provider failure, and the reader needs the "try fewer
    // words" sentence to know which. This was `false`, which meant every miss on
    // this provider got the flat "Nothing found" line meant for Tenor.
    override val catalogueIsNarrow: Boolean get() = true

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val tags = booruTags(query)
            if (tags.isBlank()) return@withContext Result.success(GifPage(emptyList()))
            val phrase = booruPhraseTag(query)
            val (phraseMode, pid) = booruCursor(cursor)
            if (phraseMode && phrase != null) {
                return@withContext fetchPage(phrase, pid, phraseMode = true)
            }
            val first = fetchPage(tags, pid, phraseMode = false)
            // "spice and wolf" ANDs `spice` and `wolf`, which no post carries, while
            // the series is one tag, `spice_and_wolf`. The words are tried as tags
            // first because that is what most searches are ("holo crying"); only a
            // first page that found nothing at all is asked again as one tag.
            val missed = first.getOrNull()?.results?.isEmpty() == true && first.getOrNull()?.next == null
            if (cursor == null && missed && phrase != null) fetchPage(phrase, 0, phraseMode = true) else first
        }

    private fun fetchPage(tags: String, pid: Int, phraseMode: Boolean): Result<GifPage> {
        val built = runCatching {
            ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("page", "dapi")
                .addQueryParameter("s", "post")
                .addQueryParameter("q", "index")
                .addQueryParameter("json", "1")
                .addQueryParameter("limit", PAGE_SIZE.toString())
                // `pid` is the index here, zero-based: `page` is already taken by
                // the API selector, and asking for page 2 of a post list would ask
                // the API itself to switch pages.
                .addQueryParameter("pid", pid.toString())
                .addQueryParameter("tags", booruRequestTags(tags))
                .build()
        }
        val request = built.getOrNull()?.let {
            Request.Builder()
                .url(it)
                .header("Accept", "application/json")
                .header("User-Agent", USER_AGENT)
                .build()
        } ?: return Result.failure(GifSearchFailure("That search could not be built."))

        return try {
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return Result.failure(
                        GifSearchFailure("Safebooru refused that search (HTTP ${response.code}).")
                    )
                }
                val body = response.body?.string().orEmpty()
                // An unknown tag combination answers HTTP 200 with *no body at
                // all* - not `[]`, nothing. That is the site's "no matches", and
                // it used to fall through to the parser, fail there and tell the
                // reader Safebooru had answered with something unreadable.
                if (isNoMatchesBody(body)) return Result.success(GifPage(emptyList()))
                val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                    ?: return Result.failure(
                        GifSearchFailure("Safebooru answered with something unreadable.")
                    )
                // Pagination asks the *server's* page how full it was, not the
                // filtered one. Dropping a post for its rating, or a tile for
                // having no file, shortens `results` below the requested limit
                // while the site still had rows behind it, so a search that had
                // three pages left would stop after the first.
                val next = booruNextCursor(parsed.size, PAGE_SIZE, pid.toString())
                Result.success(
                    parsed.toGifPage().copy(next = next?.let { if (phraseMode) "$PHRASE_CURSOR$it" else it })
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(GifSearchFailure("Could not reach Safebooru."))
        }
    }

    companion object {
        private const val ENDPOINT = "https://safebooru.org/index.php"

        private const val PAGE_SIZE = 24
        private const val USER_AGENT = "AniSequel (Android; GIF search)"

        internal fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(12, TimeUnit.SECONDS)
            .callTimeout(20, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

/**
 * A search phrase as booru tags.
 *
 * A booru matches tags, not sentences: a search for `spice and wolf holo` has to
 * become tag words or it returns nothing. Stop words (`a`, `and`, `of`, `the`) are
 * dropped, because they are never part of a tag and a phrase containing them
 * matches nothing; what is left is passed as space-separated tags, which the site
 * ANDs together. Tags the reader spelled with underscores are left exactly as
 * written, so somebody who knows the syntax is not fighting the rewrite - this does
 * not try to know that `spice and wolf` is one tag, which needs the site's tag list
 * to guess and would guess wrong.
 */
internal fun booruTags(query: String): String =
    query.trim().lowercase()
        .split(Regex("""\s+"""))
        .filterNot { it.lowercase() in DROP_WORDS }
        .joinToString(" ") { word -> word.ifBlank { return@joinToString "" }.replace(' ', '_') }

private val DROP_WORDS = setOf("a", "an", "and", "of", "the", "with", "in", "on")

/**
 * The whole `tags` parameter a booru search sends.
 *
 * Top-level and internal so the rating spelling is pinned by a JVM test: it is
 * the one argument whose wrong value does not fail, it answers an empty page.
 */
internal fun booruSearchTags(query: String): String = booruRequestTags(booruTags(query))

/**
 * The `tags` parameter for tags that are already in booru form.
 *
 * Sorted by score, best first: the index's default order is newest upload, so the
 * first page of "laughing" was whatever had been posted that week rather than the
 * reactions people had actually voted up.
 */
internal fun booruRequestTags(tags: String): String = "$REQUIRED_TAGS $SORT_TAG $tags".trim()

internal const val SORT_TAG = "sort:score:desc"

/**
 * A multi-word phrase as one booru tag, or null when it is one word already.
 *
 * Stop words are kept here, unlike [booruTags]: they are part of series tags
 * (`spice_and_wolf`, `kaguya-sama_wa_kokurasetai`), which is exactly the case the
 * word-by-word search cannot find. Measured live: `spice wolf` answers nothing,
 * `spice_and_wolf` answers the series.
 */
internal fun booruPhraseTag(query: String): String? {
    val words = query.trim().lowercase().split(Regex("""\s+""")).filter { it.isNotBlank() }
    return if (words.size < 2) null else words.joinToString("_")
}

/** Cursor prefix for pages of the phrase-as-one-tag search. */
internal const val PHRASE_CURSOR = "p:"

/** A cursor as (phrase mode, zero-based post index). */
internal fun booruCursor(cursor: String?): Pair<Boolean, Int> {
    val phrase = cursor?.startsWith(PHRASE_CURSOR) == true
    val index = cursor?.removePrefix(PHRASE_CURSOR)?.toIntOrNull() ?: 0
    return phrase to index.coerceAtLeast(0)
}

/**
 * The rating the catalogue is limited to, whatever the tags say.
 *
 * `rating:safe`, not `rating:general`. The site's DAPI does not reject a rating
 * value it does not know; it simply matches nothing, and a search for
 * `animated_gif rating:general holo` answers HTTP 200 with an **empty body** -
 * which is this provider's "no matches". Every search in the picker therefore
 * came back empty regardless of the query, while the same tags with
 * `rating:safe` return the catalogue. The response's own `rating` field is
 * spelled `safe` too (see [isSafeRating]); the request and the filter have to
 * agree or one of them is decorative.
 */
internal const val REQUIRED_TAGS = "animated_gif rating:safe"

/** One Safebooru post, as far as this picker cares. */
@JsonClass(generateAdapter = true)
internal data class SafebooruPost(
    val id: Int? = null,
    @Json(name = "file_url") val fileUrl: String? = null,
    @Json(name = "sample_url") val sampleUrl: String? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    val rating: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    /**
     * The sample's own size, which is the only evidence that a sample exists.
     *
     * Asked for because [booruTileUrl] needs it: Safebooru answers `sample_url`
     * equal to `file_url` with `sample_width: 0` when it never made a smaller copy,
     * and fifteen of the twenty posts in a live page of `animated_gif` were like
     * that. Reading the URL alone therefore called the original a thumbnail.
     */
    @Json(name = "sample_width") val sampleWidth: Int? = null,
    @Json(name = "sample_height") val sampleHeight: Int? = null,
    val tags: String? = null
)

/**
 * Safebooru's post list, flattened to the two URLs the picker needs.
 *
 * `file_url` is the GIF a comment should carry. The tile is the harder choice and
 * [booruTileUrl] decides it: the site's `sample_url` is a second, smaller file for
 * about a quarter of these posts and **the same file as `file_url`** for most of the
 * rest - and the thumbnail is always a JPEG still - so "smallest file" and "the thing
 * that moves" are not the same URL, and an extension cannot tell them apart.
 *
 * A post without a file URL cannot be embedded and is dropped; a post whose rating is
 * not in the safe set is dropped too, whatever the query asked for.
 */
internal fun List<SafebooruPost?>?.toGifPage(): GifPage {
    val rows = this.orEmpty().filterNotNull().mapNotNull { post ->
        if (!isSafeRating(post.rating)) return@mapNotNull null
        val embed = post.fileUrl ?: post.sampleUrl ?: return@mapNotNull null
        if (!embed.startsWith("http")) return@mapNotNull null
        GifResult(
            id = post.id?.toString() ?: embed,
            embedUrl = embed,
            gridUrl = booruTileUrl(post) ?: embed,
            title = post.tags?.split(' ')?.take(6)?.joinToString(" ")?.replace('_', ' '),
            width = post.width,
            height = post.height
        )
    }
    return GifPage(results = rows, next = null, totalCount = rows.size)
}

/**
 * Whether a response body is Safebooru's way of saying "that search matched
 * nothing": an empty body, or an empty array.
 *
 * The site gives no JSON at all for a tag combination it cannot resolve, which is a
 * legitimate answer and not a broken one - so it must not reach the parser, where it
 * reads as a corrupt response and blames the provider.
 */
internal fun isNoMatchesBody(body: String): Boolean =
    body.isBlank() || body.trim() == "[]"

/**
 * The post index to ask for next, or null when this page was not full.
 *
 * [rawPostCount] is what the site returned *before* this app filtered any of it: a
 * page emptied by the rating check is still a full page of posts, and treating it as
 * a short one ended the search at its first page.
 */
internal fun booruNextCursor(rawPostCount: Int, requestedLimit: Int, cursor: String?): String? =
    if (rawPostCount >= requestedLimit) ((cursor?.toIntOrNull() ?: 0) + 1).toString() else null

/**
 * The rating strings that mean "safe to put in a comment other people read".
 *
 * Safebooru's own API spells its ratings out - a post answers `rating: "general"` or
 * `rating: "safe"`, never the one-letter form this check used to compare against.
 * Matching only `"g"` therefore dropped **every** post, and the picker drew an empty
 * grid over a search that had just returned twenty-four. Both spellings are accepted
 * because the rule is about the content, not about the string the site happens to
 * use today, and `rating:` is a request the site can ignore rather than a guarantee.
 */
internal fun isSafeRating(rating: String?): Boolean =
    rating == null || rating.lowercase() in SAFE_RATINGS

private val SAFE_RATINGS = setOf("g", "general", "s", "safe")

/**
 * Which copy of a post the picker's tile should draw.
 *
 * Prefer a real animated sample; then the post's own GIF, but only while that file
 * is small enough to belong in a 104dp cell; and otherwise the thumbnail still. An
 * animated drawable is decoded at its **intrinsic** frame size however it is
 * displayed, and this catalogue carries GIFs up to 5200x5200, so twenty of those in
 * a scrolling grid is a crash rather than a preview. A post whose dimensions the
 * response did not give is treated as too big for the same reason.
 *
 * ## The failure this replaced
 *
 * The rule used to be "is `sample_url` a `.gif`", which is not the same question.
 * Measured against a live page of `animated_gif rating:general`: fifteen of twenty
 * posts answer `sample_url` **equal to** `file_url`, with `sample_width: 0` beside
 * them - the site had no smaller copy to give. Extension-only matching therefore
 * called the original a thumbnail, returned it, and drew up to twenty full-resolution
 * animations at once: a picker that stalls, then a sheet that the system kills. The
 * sample is now taken as a sample only when it is a different file **and** its own
 * dimensions fit, which is what `sample_width` is on the model for.
 *
 * When nothing animated fits, the tile is the JPEG thumbnail - a still that costs
 * almost nothing - and the comment still gets [GifResult.embedUrl], the full
 * animation. The reader embeds the moving file either way; only the grid stops
 * pretending it can hold a 5200x5200 frame in 104dp.
 */
internal fun booruTileUrl(post: SafebooruPost): String? {
    val sample = post.sampleUrl?.takeIf { it.startsWith("http") }
    val file = post.fileUrl?.takeIf { it.startsWith("http") }
    val preview = post.previewUrl?.takeIf { it.startsWith("http") }
    val realSample = sample?.takeIf { it != file && it.isGifUrl() }
    if (realSample != null && fitsTileBudget(post.sampleWidth, post.sampleHeight)) return realSample
    if (file != null && file.isGifUrl() && fitsTileBudget(post.width, post.height)) return file
    // A still wins over an animation this grid cannot afford - and leading the
    // fallback with `sample` was the way to miss that. For most of these posts
    // `sample_url` IS the full file, so the fallback handed back the poster-sized
    // GIF the budget had just refused, and a 5200x5200 frame in a 104dp cell is a
    // crash rather than a preview.
    return preview ?: realSample ?: file
}

/**
 * The ceiling for one tile's animation: [TILE_MAX_PIXELS] in total, with no edge past
 * [TILE_MAX_EDGE]. Both halves are needed - a 4000x40 strip is 160k pixels and still
 * 4000 wide.
 */
internal fun fitsTileBudget(width: Int?, height: Int?): Boolean {
    if (width == null || height == null) return false
    if (width <= 0 || height <= 0) return false
    return width.toLong() * height.toLong() <= TILE_MAX_PIXELS &&
        maxOf(width, height) <= TILE_MAX_EDGE
}

private fun String.isGifUrl(): Boolean = endsWith(".gif", ignoreCase = true)

/** Roughly the largest sample the site serves, so an animated sample is always in. */
private const val TILE_MAX_PIXELS = 1_500_000L
private const val TILE_MAX_EDGE = 1_500
