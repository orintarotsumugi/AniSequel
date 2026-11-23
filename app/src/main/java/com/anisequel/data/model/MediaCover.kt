package com.anisequel.data.model

import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * What identifies a media well enough to ask AniList for its cover art.
 *
 * ## Why the type is part of the key
 *
 * It used to be a bare `Int`, on the reasoning that AniList media ids are unique.
 * They are not: `anilist.co/anime/1` and `anilist.co/manga/1` are different
 * entities in different id spaces, and the same collision exists for every id
 * both namespaces happen to use.
 *
 * Collapsing them is not a rare miss, it is a confidently wrong one. The two
 * links deduplicate to a single id, `Media(id: 1)` is asked for without a type,
 * AniList resolves that against *both* media types and answers with whichever it
 * prefers - and then **both** cards read that one URL out of the map. So a bio
 * linking an anime and a manga with a shared id shows the same poster twice.
 * Nothing about that looks broken. It looks correct.
 *
 * Filtering character/staff/studio/user out of the key (`novaMediaCoverKeysIn` in the
 * markdown package) does not help: those are filtered because they name a
 * different thing entirely, and anime vs manga are filtered for the opposite
 * reason - they *are* media, in different namespaces.
 *
 * [type] is stored uppercase, which is both what AniList's `MediaType` enum
 * spells and what the parser produces. [graphqlType] uppercases anyway so a key
 * built by hand cannot quietly ask for the wrong type.
 */
data class MediaCoverKey(
    val type: String,
    val id: Int
) {
    /** The value AniList's `MediaType` enum expects for this key. */
    val graphqlType: String get() = type.uppercase(Locale.ROOT)
}

/** The cache key for a media node, including AniList's independent id namespace. */
val MediaNode.mediaCoverKey: MediaCoverKey
    get() = MediaCoverKey(type ?: "ANIME", id)

/**
 * A page of cover answers, as the batched cover query returns it.
 *
 * One `Page.media` connection for the whole batch, aliased `media`, so this is
 * read as a fixed field rather than as a map keyed by alias. See
 * [GraphQLQueries.getMediaCoversQuery] for why the batch is one root field and
 * not one alias per id - AniList's limiter counts each alias separately now, and
 * an alias per link spent the budget the batch existed to save.
 *
 * ## Why the batch is a connection and not the `Media` root field
 *
 * `Media(id:)` is nullable, and AniList answers a batch containing an id it
 * cannot resolve with **HTTP 404** and `errors: ["Not Found."]` rather than with
 * a null field - so one stale link in a bio (a deleted entry) failed the *whole*
 * response, and every poster on the card fell back to the placeholder even
 * though the other ids had resolved. `Page.media` filters instead of failing: an
 * id with no media is simply absent, on a 200.
 *
 * [media] is a list rather than a single node because that is what a connection
 * gives, and it is null only if the field itself is absent. An empty list, a
 * null list and an absent page are all the same "no cover for these" to the
 * caller rather than three branches.
 */
@JsonClass(generateAdapter = true)
data class MediaCoverPage(
    val media: List<MediaNode>? = null
)

/** The payload of the batched cover query, with its single `media` page. */
@JsonClass(generateAdapter = true)
data class MediaCoversData(
    val media: MediaCoverPage? = null
)

/**
 * A cover image for one media, with the title that arrived in the same response.
 *
 * ## Why the title is here rather than guessed at the card
 *
 * A bio link to `https://anilist.co/anime/1` carries no readable name at all, so
 * the card heading had to be built from the URL - which produced headings like
 * `ANIME #1`. That is a debug row, and it is the heading for the *most common*
 * link shape there is, because the address bar hands people exactly that URL.
 *
 * The covers query already asked for `title { romaji english userPreferred }` on
 * every entry and the answer was thrown away, so the real title was on the wire
 * and unused. It costs nothing to carry it through, and it is the one answer that
 * is always right about what the poster depicts.
 *
 * [title] is null when the media has none of the three spellings, and the card
 * falls back to whatever the URL gave it.
 *
 * Annotated for a generated adapter because these are snapshotted to disk: a bio
 * reopened the next day should not re-spend a batched request for covers the app
 * already knows. The adapter is the only thing the annotation adds - the query
 * answer is unchanged.
 */
@JsonClass(generateAdapter = true)
data class MediaCover(
    val url: String,
    val title: String? = null
)
