package com.anisequel.data.network

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass
import com.squareup.moshi.Moshi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request

/*
 * gifukai's reaction index (api.gifukai.com), the new default.
 *
 * ## The catalogue, and why it is default
 *
 * Every GIF on gifukai is hand-picked: near-16:9, no bugged frames, no baked-in
 * text, and each carries its anime's name - which is exactly what a *comment*
 * wants, because AniList renders the file itself at whatever size the embed
 * dictates. Requests randomise one reaction per action (`/v1/hug`, `/v1/pat`,
 * ...), so "search" here means "match the words the reader typed to an action",
 * not "ask an index for a term". No key, no signup, and no published rate limit;
 * it is free to use (https://github.com/lucialv/gifukai-api).
 *
 * It cannot answer free text the way Klipy or a booru can. That limit is stated
 * on the refusal (see the search body) instead of being answered by an empty
 * grid, and the browser's suggestion chips spell the actions that do answer -
 * for this provider the chips *are* the documented endpoint list, so a chip can
 * no more miss than the endpoint behind it exists.
 *
 * ## Why one GIF per ask, and no pages
 *
 * The endpoint has no cursor: a second identical request returns a different
 * random GIF, which is a re-roll, not a duplicate to de-duplicate into a wall.
 * `next` is therefore always null, and the picker's "shuffle for another one"
 * affordance (see GifSearchStore.reroll) is the natural way to move through the
 * same reaction.
 */

/**
 * gifukai's `/{action}` endpoints on api.gifukai.com, keyless.
 */
class GifukaiGifs(
    private val client: OkHttpClient = SafebooruGifs.defaultClient(),
    moshi: Moshi = NetworkClient.json
) : GifSearchApi {

    override val providerName: String get() = "Gifukai"

    override val catalogueIsNarrow: Boolean get() = true

    override fun emptyHint(query: String): String =
        "Gifukai carries hand-picked reaction scenes: \"$query\" matched none of " +
            "them. Try a reaction like \"hug\", \"pat\", \"laugh\" or \"cry\", or " +
            "pick Klipy or Safebooru to search a title."

    private val adapter = moshi.adapter(GifukaiResponse::class.java)

    override suspend fun search(query: String, cursor: String?): Result<GifPage> =
        withContext(Dispatchers.IO) {
            val term = query.trim()
            if (term.isEmpty()) return@withContext Result.success(GifPage(emptyList()))
            if (cursor != null) {
                // There is no page after the page; a cursor here means a caller
                // ignored `next = null`, and answering it with the same random
                // draw would pretend to page a catalogue that does not.
                return@withContext Result.success(GifPage(emptyList()))
            }
            val resolved = resolve(term)
                ?: return@withContext Result.failure(
                    GifSearchFailure(
                        "Gifukai answers reaction scenes by action - " +
                            resolveGifukaiHint.joinToString(", ") +
                            ". Pick Klipy or Safebooru to search a title or a sentence."
                    )
                )

            val built = runCatching {
                ENDPOINT.toHttpUrl().newBuilder()
                    // A path segment, so a query cannot reach another endpoint.
                    .addPathSegment(resolved.action)
                    .apply {
                        if (resolved.type != null) {
                            addQueryParameter("type", resolved.type)
                        }
                    }
                    .build()
            }
            val request = built.getOrNull()?.let {
                Request.Builder()
                    .url(it)
                    .header("Accept", "application/json")
                    .header("User-Agent", USER_AGENT)
                    .build()
            } ?: return@withContext Result.failure(GifSearchFailure("That search could not be built."))

            try {
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        return@withContext Result.failure(
                            GifSearchFailure(
                                when (response.code) {
                                    404 -> "Gifukai does not carry that reaction."
                                    429 -> "Gifukai is limiting requests. Wait a moment and try again."
                                    else -> "Gifukai is not answering right now (HTTP ${response.code})."
                                }
                            )
                        )
                    }
                    val parsed = runCatching { adapter.fromJson(response.body?.string().orEmpty()) }
                        .getOrNull()
                        ?.toGifResult()
                        ?: return@withContext Result.failure(
                            GifSearchFailure("Gifukai answered with something unreadable.")
                        )
                    Result.success(GifPage(results = listOf(parsed), next = null, totalCount = 1))
                }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(GifSearchFailure("Could not reach Gifukai."))
            }
        }

    companion object {
        private const val ENDPOINT = "https://api.gifukai.com/v1"
        private const val USER_AGENT =
            "AniSequel (https://github.com/orintarotsumugi/AniSequel)"

        /**
         * The actions the live API offers, checked against `GET /v1/actions`.
         * A query that names one of these - or one of the aliases besides -
         * answers with a random hand-picked scene.
         */
        internal val ACTIONS = setOf(
            "angry", "bite", "bleh", "blowkiss", "blush", "bonk", "bored", "bye",
            "carry", "clap", "confused", "cry", "cuddle", "dance", "eat",
            "facepalm", "feed", "handhold", "handshake", "happy", "hi", "highfive",
            "hug", "kick", "kill", "kiss", "lappillow", "laugh", "lick", "nod",
            "nope", "nya", "pat", "peek", "poke", "pout", "punch", "run", "salute",
            "scared", "shake", "shocked", "shoot", "shrug", "shy", "sing", "sip",
            "slap", "sleep", "smile", "smug", "sorry", "spin", "stare", "surprised",
            "taunt", "teehee", "think", "thumbsup", "tickle", "tired", "wag",
            "wallslam", "wave", "wink", "yawn", "yay", "yeet"
        )

        /**
         * Words a reader actually types, mapped to the action that answers.
         *
         * The first half is the API's own alias table (`GET /v1/actions`); the
         * second half is the ordinary English shapes of those reactions - a
         * reader types "crying", not the bare "cry", and every search chip and
         * substitution sentence in this app assumes the reader's word, not the
         * endpoint's.
         */
        internal val ALIASES: Map<String, String> = mapOf(
            // The API's own aliases.
            "mad" to "angry", "rage" to "angry",
            "mwah" to "blowkiss",
            "flustered" to "blush",
            "cya" to "bye", "goodbye" to "bye",
            "claps" to "clap",
            "sob" to "cry",
            "snuggle" to "cuddle",
            "nom" to "eat",
            "hello" to "hi", "hey" to "hi",
            "murder" to "kill",
            "peck" to "kiss",
            "lmao" to "laugh", "lol" to "laugh",
            "agree" to "nod", "yes" to "nod",
            "deny" to "nope", "no" to "nope",
            "meow" to "nya", "neko" to "nya",
            "headpat" to "pat",
            "boop" to "poke",
            "bang" to "shoot",
            "dunno" to "shrug", "idk" to "shrug",
            "drink" to "sip",
            "nap" to "sleep", "zzz" to "sleep",
            "gaze" to "stare",
            "like" to "thumbsup",
            "thinking" to "think",
            "kabedon" to "wallslam",
            // The word forms the reaction chips and a phone keyboard spell out.
            "crying" to "cry",
            "laughing" to "laugh",
            "smiling" to "smile",
            "waving" to "wave",
            "hugging" to "hug",
            "patting" to "pat",
            "kissing" to "kiss",
            "sleeping" to "sleep",
            "angried" to "angry",
            "sad" to "cry",
            "cheeks" to "blush"
        )

        /**
         * Kiss scenes are split into exactly typed folders (`mouth`, `cheek`,
         * `forehead` - `kiss.has_types` is true and it is the only typed action),
         * so a query that says which kind carries the folder. Nothing else can
         * carry a `type`, and sending an unknown one makes the index answer
         * nothing, so only these three map.
         */
        private val KISS_TYPES = setOf("mouth", "cheek", "forehead")

        /** Named in the refusal above, so the reader has something to type. */
        private val resolveGifukaiHint = listOf("hug", "pat", "kiss", "cry", "laugh", "sleep")

        /**
         * Matches [query] against the catalogue: an exact action, an alias, or a
         * single word *inside* the phrase — "crying scared" is a cry, because the
         * first reaction word wins. Returns the action, and a `type` folder when
         * the action is a typed one and the query names the folder.
         */
        internal fun resolve(query: String): Pair<String, String?>? {
            val words = query.lowercase()
                .split(Regex("[^a-z]+"))
                .filter { it.isNotBlank() }
            val action = words.firstOrNull { it in ACTIONS }
                ?: words.firstNotNullOfOrNull { ALIASES[it] }
                ?: return null
            val type = if (action == "kiss") {
                words.firstOrNull { it in KISS_TYPES }
            } else {
                null
            }
            return action to type
        }
    }
}

/**
 * gifukai's answer, flattened to the picker's shape.
 *
 * The dimensions do not exist in this response - the size is given in bytes
 * only - so they arrive null. The tile does not need them (it crops to a square
 * cell) and the comment embeds the file itself; what the reader's comment gains
 * is the anime's name, which is why [GifukaiResponse.anime] is the title.
 */
internal fun GifukaiResponse.toGifResult(): GifResult? {
    val url = url ?: return null
    if (!contentType.orEmpty().startsWith("image/")) return null
    return GifResult(
        id = url,
        embedUrl = url,
        gridUrl = url,
        title = listOfNotNull(anime, action?.let { it.replaceFirstChar { c -> c.uppercase() } })
            .joinToString(" - ")
            .takeIf { it.isNotBlank() },
        width = null,
        height = null
    )
}

@JsonClass(generateAdapter = true)
internal data class GifukaiResponse(
    val action: String? = null,
    val type: String? = null,
    val pairing: String? = null,
    val anime: String? = null,
    val url: String? = null,
    @Json(name = "content_type") val contentType: String? = null,
    @Json(name = "size_bytes") val sizeBytes: Long? = null
)
