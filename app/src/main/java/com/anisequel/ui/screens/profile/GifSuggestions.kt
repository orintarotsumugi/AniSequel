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
 *   the endpoint table its client embeds, so a chip can no more miss than the
 *   endpoint behind it exists. Its own API answers no other word; a typed name
 *   goes to Klipy instead (see GifukaiGifs), but a chip must be a word the
 *   chosen index itself answers, so the chips stay the documented vocabulary.
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
 * Gifukai's chips: the actions themselves, straight off the client's endpoint
 * table.
 *
 * Deliberately the bare actions rather than the wordier English shapes the alias
 * table also maps - a chip teaches the vocabulary, and the shorter word is the one
 * that is a whole endpoint. Articles ("a hug") are for the search field, whose
 * alias matcher reads them fine; these chips are tapped, not read aloud. Kiss
 * answers without a `type` parameter too, so the chip stays a single word.
 */
internal val GIFUKAI_REACTIONS = listOf(
    "hug", "pat", "kiss", "cuddle", "cry", "laugh", "happy", "smile",
    "blush", "shy", "angry", "sleep", "wave", "dance"
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
    "boku_no_hero_academia*"
)