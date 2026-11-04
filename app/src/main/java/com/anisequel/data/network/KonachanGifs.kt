package com.anisequel.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Konachan's safe instance, as the third index that needs no key.
 *
 * ## What gap it fills
 *
 * The other two keyless indexes fail in opposite directions. Safebooru is a deep tag
 * catalogue whose animated section is unevenly tagged - whether `hatsune_miku` returns
 * anything depends on somebody having applied that tag to the file. nekos.best is
 * perfectly reliable and answers only its own list of reaction words, so a search for a
 * character name is refused outright (see [NekosBestGifs.search]).
 *
 * Konachan's `.net` host sits in the middle: it is the safe-only instance of a
 * danbooru-style board, its `/post.json` takes no account and no key, and its tags are
 * the conventional character tags a reader types. Measured against a live search, all 24
 * rows of `animated order:score` carry `rating: "s"` and 23 of the 24 are `.gif`.
 *
 * ## What it costs
 *
 * A thin catalogue - the series names a reader reaches for are often not there
 * (`spice_and_wolf` and `miku` both answer nothing, while `vocaloid` answers four) - and
 * an origin that answered HTTP 522 on one probe and the full catalogue on the next. That
 * is why it is an opt-in index rather than the anonymous default, and why
 * [catalogueIsNarrow] is true: an empty grid here is usually the board having nothing,
 * not the search being asked wrong. The three tag spellings Safebooru is asked with are
 * reused, because it is the same failure: see [booruTiers].
 */
class KonachanGifs(
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    override val providerName: String get() = "Konachan"

    private val adapter = moshi.adapter<List<KonachanPost?>>(
        Types.newParameterizedType(List::class.java, KonachanPost::class.java)
    )

    override val catalogueIsNarrow: Boolean get() = true

    /**
     * The miss, in this board's terms.
     *
     * It holds fewer animated files than a full booru and tags them by character and
     * franchise rather than by mood, so the useful advice is the opposite of nekos.best's:
     * name who, not how they feel.
     */
    override fun emptyHint(query: String): String =
        "No animated GIF tagged \"$query\" on Konachan - its animated section is small " +
            "and tagged by character, so try a franchise like \"vocaloid\", or pick " +
            "Safebooru for reactions and nekos.best for feelings."

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val tiers = booruTiers(query)
            if (tiers.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            val (tierIndex, page) = booruCursor(cursor)
            val tier = tiers[tierIndex.coerceIn(tiers.indices)]
            val first = fetchPage(tier.tagString, page, tier.cursorPrefix)
            // A cursor means the tier was already chosen by the first page; only a first
            // page walks the cascade, the rule SafebooruGifs.search runs on.
            if (cursor != null) return@withContext first
            if (tierAnswers(first)) return@withContext first
            var answer = first
            for (next in tiers.drop(tierIndex + 1)) {
                answer = fetchPage(next.tagString, 1, next.cursorPrefix)
                if (tierAnswers(answer)) return@withContext answer
            }
            answer
        }

    private fun fetchPage(tags: String, page: Int, cursorPrefix: String): Result<GifPage> {
        // 1-based, unlike Safebooru's `pid`, which is 0-based and lives beside a
        // `page=dapi` selector that already owns the name. A first search arrives with
        // no cursor, which decodes to 0, so the clamp happens *here* rather than only in
        // the query string: the next-page number is computed from the page that was
        // actually asked for, and clamping only the parameter handed the reader page 1 as
        // the cursor to page 1, which a grid happily fetched again and again.
        val pageNumber = page.coerceAtLeast(1)
        val built = runCatching {
            ENDPOINT.toHttpUrl().newBuilder()
                .addQueryParameter("tags", konachanRequestTags(tags))
                .addQueryParameter("limit", PAGE_SIZE.toString())
                .addQueryParameter("page", pageNumber.toString())
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
                        GifSearchFailure("Konachan refused that search (HTTP ${response.code}).")
                    )
                }
                val body = response.body?.string().orEmpty()
                // Unlike Safebooru's DAPI, which answers a miss with *no body at all*,
                // this board answers `[]`. The shared check covers both spellings, and a
                // miss must not reach the parser as a corrupt response in either case.
                if (isNoMatchesBody(body)) return Result.success(GifPage(emptyList()))
                val parsed = runCatching { adapter.fromJson(body) }.getOrNull()
                    ?: return Result.failure(
                        GifSearchFailure("Konachan answered with something unreadable.")
                    )
                // The server's raw count, not the filtered one: a page emptied by the
                // rating or file-type check still had rows behind it.
                val next = booruNextCursor(parsed.size, PAGE_SIZE, pageNumber.toString())
                Result.success(
                    parsed.toGifPage().copy(next = next?.let { "$cursorPrefix$it" })
                )
            }
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(GifSearchFailure("Could not reach Konachan."))
        }
    }

    companion object {
        private const val ENDPOINT = "https://konachan.net/post.json"

        /** Same grid budget as the other indexes: 24 fills a phone picker page. */
        private const val PAGE_SIZE = 24

        // A browser-like agent was tried first and is not needed here: this host answered
        // HTTP 200 with the full catalogue to a bare `okhttp/4.12.0`, and to no
        // User-Agent at all. Naming the app is the honest option and is what the other
        // keyless index sends, so that is what goes in the header.
        private const val USER_AGENT = "AniSequel (Android; GIF search)"
    }
}

/**
 * The board's own tag for a file that moves.
 *
 * Not optional: without it this index returns wallpapers, and the picker fills with
 * stills it cannot embed as a GIF. Top-level, like Safebooru's [REQUIRED_TAGS], because
 * a companion's members are not visible to the builder function that assembles the
 * request from outside the class.
 */
internal const val ANIMATED_TAG = "animated"

/**
 * Best-voted first.
 *
 * The board's default order is newest post, so the first page of `animated` was
 * whatever had been uploaded that week - scores of 8, 28, 22 measured on one live page,
 * against 321, 273, 270 with this tag. `order:` is this engine's spelling; Safebooru's
 * DAPI takes [SORT_TAG] and ignores this one.
 */
internal const val ORDER_TAG = "order:score"

/**
 * The whole `tags` parameter a Konachan search sends.
 *
 * Top-level and internal so a JVM test can pin both required words: [ANIMATED_TAG] is
 * the one that keeps the grid from filling with wallpapers, and [ORDER_TAG] is the one
 * whose absence makes the results newest-instead-of-best without anything failing.
 */
internal fun konachanRequestTags(tags: String): String = "$ANIMATED_TAG $ORDER_TAG $tags".trim()

/** One Konachan post, as far as this picker cares. */
@JsonClass(generateAdapter = true)
internal data class KonachanPost(
    val id: Long? = null,
    @Json(name = "file_url") val fileUrl: String? = null,
    @Json(name = "file_ext") val fileExt: String? = null,
    @Json(name = "preview_url") val previewUrl: String? = null,
    @Json(name = "sample_url") val sampleUrl: String? = null,
    val width: Int? = null,
    val height: Int? = null,
    val rating: String? = null,
    val tags: String? = null
)

/**
 * Konachan's post list, flattened to the two URLs the picker needs.
 *
 * `file_url` is the GIF the comment carries, and a post the board does not describe as a
 * GIF is dropped even when it is tagged `animated` - see [konachanFileIsGif] for why the
 * `file_ext` field decides that rather than the URL. A post with no file at all cannot be
 * embedded and is dropped too.
 */
internal fun List<KonachanPost?>?.toGifPage(): GifPage {
    val rows = konachanRows(this)
    return GifPage(results = rows, next = null, totalCount = rows.size)
}

/** The filtering half of [toGifPage], separate so a test can hand it a crafted page. */
internal fun konachanRows(posts: List<KonachanPost?>?): List<GifResult> =
    posts.orEmpty().filterNotNull().mapNotNull { post ->
        // Shared with Safebooru on purpose: this board spells its rating `"s"`, that one
        // spells it `"safe"`, and the rule is about the content, not the string.
        if (!isSafeRating(post.rating)) return@mapNotNull null
        val embed = post.fileUrl ?: return@mapNotNull null
        if (!embed.startsWith("http") || !konachanFileIsGif(post)) return@mapNotNull null
        GifResult(
            id = post.id?.toString() ?: embed,
            embedUrl = embed,
            gridUrl = konachanTileUrl(post) ?: embed,
            title = post.tags?.split(' ')
                ?.filterNot { it.contains(':') || it == ANIMATED_TAG }
                ?.take(4)
                ?.joinToString(" ")
                ?.replace('_', ' '),
            width = post.width,
            height = post.height
        )
    }

/**
 * Whether the board says this post's own file is a GIF.
 *
 * `file_ext` is the answer rather than the URL's tail because it is the field the board
 * maintains for the file it is serving; the extension is the fallback for a response that
 * omits it. Measured on a live page of `animated`, one row in twenty-four was `png` - an
 * APNG, which animates in a browser and in this app, but is not the file type a GIF picker
 * promised, and telling the two apart from the bytes means fetching them.
 */
internal fun konachanFileIsGif(post: KonachanPost): Boolean =
    post.fileExt?.lowercase() == "gif" ||
        (post.fileExt == null && post.fileUrl?.isGifUrl() == true)

/**
 * Which copy of a Konachan post the picker's tile should draw.
 *
 * The preview still, unless the post's own GIF is small enough for a cell and there is
 * no preview. Measured on a live page of `animated`: 23 of 24 rows answer `sample_url`
 * **equal to** `file_url` - the board has no smaller animated copy to give - and 9 of
 * 24 GIFs are past the tile budget [fitsTileBudget] describes. An animated drawable is
 * decoded at its intrinsic frame size however it is displayed, so drawing the files here
 * would put nine 1920x1080 animations in a scrolling grid at once.
 *
 * [GifResult.embedUrl] is still the moving file, so the comment the reader posts
 * animates; only the grid shows a 150px JPEG of its first frame.
 */
internal fun konachanTileUrl(post: KonachanPost): String? {
    val preview = post.previewUrl?.takeIf { it.startsWith("http") }
    val file = post.fileUrl?.takeIf { it.startsWith("http") }
    val sample = post.sampleUrl?.takeIf { it.startsWith("http") }
    if (preview != null) return preview
    if (file != null && fitsTileBudget(post.width, post.height)) return file
    // The sample is only a tile when it is a genuinely different file from the post's
    // own - here it usually is not, and handing `sample_url` back for those rows would
    // return the full-resolution frame the budget just refused. Leading the fallback
    // with it is the mistake booruTileUrl records.
    if (sample != null && sample != file && fitsTileBudget(post.width, post.height)) return sample
    return file
}
