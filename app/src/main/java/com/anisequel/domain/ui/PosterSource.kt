package com.anisequel.domain.ui

/**
 * Where a card's cover art comes from.
 *
 * Two sources, because they are two different pictures of the same show.
 *
 * [ANILIST] is the cover AniList itself carries - the key visual the site shows and
 * the one the app has always drawn. It is the default, and it stays the default
 * because it is the only source that every entry in a list is guaranteed to have:
 * it arrives with the list request itself, costs no extra call, and needs no
 * matching.
 *
 * [BETTER_POSTERS] is a *rendered* poster from btttr.cc: the same key art with the
 * title, score and badges composited on it, which is what the posters look like on
 * a streaming service rather than on a database. Better art when it exists, but it
 * does not arrive with the list - see `BetterPostersApi` for what has to happen
 * before a URL exists - so every card falls back to [ANILIST] while it waits, and
 * stays on it when btttr has no match.
 *
 * The choice lives on the enum, not in the composable, for the same reason
 * [LibraryViewMode] carries its own column count: the Settings picker and the cards
 * must not be able to describe different things.
 */
enum class PosterSource(
    val storageValue: String,
    val displayName: String,
    val blurb: String
) {
    ANILIST(
        storageValue = "anilist",
        displayName = "AniList",
        blurb = "The cover AniList carries. Arrives with the list, so it never waits " +
            "and every entry has one."
    ),

    BETTER_POSTERS(
        storageValue = "btttr",
        displayName = "BetterPosters",
        blurb = "btttr.cc's rendered poster - the cover with the show's name set " +
            "into it. Matched by IMDb id, so a title it does not carry keeps its " +
            "AniList cover; the score, genre and trend overlays are off by default " +
            "and can be added in Poster art."
    );

    companion object {
        /** What every build before this setting existed drew, so nothing moves on update. */
        val DEFAULT = ANILIST

        /**
         * Stored as the enum's `storageValue`, never its ordinal: reordering the
         * entries would otherwise repaint every card with a source nobody picked,
         * and a value a later build no longer knows reads back as the default.
         */
        fun fromStorage(value: String?): PosterSource =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}
