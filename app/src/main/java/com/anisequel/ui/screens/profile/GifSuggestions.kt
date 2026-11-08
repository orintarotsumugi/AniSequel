package com.anisequel.ui.screens.profile

import com.anisequel.domain.gif.GifProvider

/*
 * The suggestion vocabulary the GIF picker's blank field offers.
 *
 * Split from GifBrowser because the two halves change for different reasons:
 * the pane changes when the picker's states change, these lists change when a
 * live index is re-measured. Every word here was checked against the index it
 * is offered on - a chip that searches into an empty grid reads as the picker
 * being broken rather than as a gap in the catalogue - so the lists are data
 * with a measurement rule, not UI copy.
 */

/**
 * The reactions the blank field offers, per provider.
 *
 * Each provider's chips are words its own catalogue answers, because a chip that
 * searches into an empty grid reads as the picker being broken rather than as a
 * gap in the index:
 *
 * - [Gifukai][GifProvider.GIFUKAI] gets [GIFUKAI_REACTIONS] - exact actions from
 *   the endpoint table its client embeds, so the reaction chips can no more miss
 *   than the endpoint behind it exists. Names are not in this row: they live in
 *   the Anime and Characters rows, which Gifukai answers through its Klipy
 *   hand-off (see GifukaiGifs) and labels with the index that gave them.
 * - Safebooru and nekos.best get [GIF_REACTIONS], measured against their live
 *   indexes (the narrow booru tags, the reaction categories - nekos.best reads
 *   the word forms too, see NekosBestGifs.resolveCategory).
 * - Klipy, GIPHY and Tenor are general catalogues: free text works, so the
 *   measured [GIF_REACTIONS] row is the nudge and nothing else is needed.
 */
internal fun reactionsFor(provider: GifProvider): List<String> = when (provider) {
    GifProvider.GIFUKAI -> GIFUKAI_REACTIONS
    else -> GIF_REACTIONS
}

/**
 * Gifukai's chips: actions themselves, straight off the client's endpoint table.
 *
 * Deliberately the bare actions rather than the wordier English shapes the alias
 * table also maps - a chip teaches the vocabulary, and the shorter word is the one
 * that is a whole endpoint. Articles ("a hug") are for the search field, whose
 * alias matcher reads them fine; these chips are tapped, not read aloud. Kiss
 * answers without a `type` parameter too, so the chip stays a single word.
 *
 * The row is the API's breadth, not its first page: the endpoint table carries
 * 68 actions and the original fourteen made most of them undiscoverable. The
 * order is by how often a comment reaches for one, so the useful end is reached
 * without scrolling on a phone.
 */
internal val GIFUKAI_REACTIONS = listOf(
    "hug", "pat", "kiss", "cuddle", "cry", "laugh", "happy", "smile",
    "blush", "shy", "angry", "sleep", "wave", "dance", "bonk", "highfive",
    "thumbsup", "facepalm", "shrug", "pout", "smug", "handhold", "carry", "feed",
    "tickle", "wag", "nya", "salute"
)

/**
 * The pairings gifukai alone can name, as whole searches rather than as a lone
 * modifier.
 *
 * `?pairing=` is the one filter no other index in the app has, and the client
 * reads it from the query's own words (`resolvePairing`), so the chip has to be
 * a phrase a reader would say: "two girls hug" is a request the endpoint answers,
 * "ff" is not a sentence. The mixed pair is offered in both orders because the
 * API spells them differently - female-first is `fm`, male-first is `mf` - and a
 * chip that hid one of them would be teaching half the feature.
 */
internal val GIFUKAI_PAIRINGS = listOf(
    "two girls hug", "two boys hug", "boy and girl hug", "girl and boy hug"
)

/**
 * Reactions: what a reader wants to *say*, checked against the live index.
 *
 * This list is not the obvious set of words. `smiling` and `wave` answer nothing in
 * the `animated_gif rating:safe` corner of the catalogue while `laughing` and
 * `blush` answer plenty, and a chip that searches into an empty grid is worse than
 * no chip: it reads as the picker being broken rather than as a gap in the index.
 */
internal val GIF_REACTIONS = listOf("laughing", "crying", "angry", "surprised", "confused", "blush")

/**
 * Anime titles a reader is likely to name, offered in the pickers that answer
 * free text.
 *
 * Gifukai answers these through its Klipy hand-off (see GifukaiGifs), Klipy and
 * the other general catalogues answer them directly, and every answer says which
 * index gave it. These are the titles a comment reaches for, not a ranking: a
 * chip here is a demonstration that a name works, which is the one thing the
 * reaction vocabulary cannot teach.
 */
internal val GIF_ANIME = listOf(
    "frieren", "jujutsu kaisen", "chainsaw man", "spy x family", "demon slayer",
    "one piece", "naruto", "attack on titan", "sailor moon", "evangelion",
    "studio ghibli", "dragon ball"
)

/**
 * Character names, the other half of the name hand-off.
 *
 * A title finds a show's key art; a character finds the expression a comment
 * actually wants. The names are the ones that dominate discussion, and each is
 * distinctive enough not to drown in an unrelated meaning on a general catalogue
 * ("fern" is a plant, "rem" is a unit - both give plenty of anime results anyway,
 * but "anya" and "gojo" are the ones that pay off).
 */
internal val GIF_CHARACTERS = listOf(
    "gojo", "makima", "nezuko", "anya", "yor", "rem", "asuka", "luffy",
    "totoro", "miku", "holo", "vegeta"
)

/**
 * Series: what a reader wants to *watch*, each one a tag the index really carries.
 *
 * Typed as booru tags rather than as release titles, because that is what the field
 * is being taught to accept: `sousou no frieren` is the search a reader types and
 * `sousou_no_frieren*` is what answers - the app's own phrase tier rewrites the
 * words, and the prefix tier finds the series when the exact tag is not the one the
 * site uses (`frieren_beyond_journeys_end` answers nothing; `frieren*` answers the
 * show). Each entry was measured against the live index with the app's request
 * shape before it went in, the same rule [GIF_REACTIONS] is held to - and the row
 * is offered on Safebooru only, because these are booru tags everywhere else.
 */
internal val GIF_SERIES = listOf(
    "spice_and_wolf",
    "sousou_no_frieren*",
    "kaguya*",
    "evangelion*",
    "mob_psycho_100*",
    "boku_no_hero_academia*",
    "chainsaw_man*",
    "jujutsu_kaisen*"
)
