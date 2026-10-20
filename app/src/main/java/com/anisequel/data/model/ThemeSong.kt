package com.anisequel.data.model

/**
 * One opening or ending theme, as the title page draws it.
 *
 * ## Where this comes from
 *
 * Not AniList. AniList's GraphQL API has no theme-song fields at all - not on
 * `Media`, not behind a relation - so this is the one thing on the title page that
 * is fetched from a second service. [com.anisequel.data.network.AnimeThemesApi]
 * reads AnimeThemes.moe's public JSON API, matched to the title by its AniList id.
 *
 * [label] is the theme's own slug (`OP1`, `ED2`) where the service has one; the
 * sequence number is not always present even when the slug carries it, which is why
 * the slug is preferred and a sequence fallback exists in the mapper.
 *
 * [videoUrl] is a direct `.webm` stream. The app does not play it in-process - that
 * would be a video player dependency for one row of cards - it hands the URL to the
 * system, which is what the play button means here.
 */
data class ThemeSong(
    /** `OP1`, `ED2`, and so on - the badge on the card. */
    val label: String,
    /** `OP` or `ED`. Kept apart from [label] so the header can count each. */
    val type: String,
    /** The song's title, when AnimeThemes has it. */
    val title: String? = null,
    /** A direct video URL to open externally, or null when none was supplied. */
    val videoUrl: String? = null,
    /** Card artwork: the theme's own cover image, else the title's cover. */
    val artworkUrl: String? = null
) {
    val isOpening: Boolean get() = type.equals("OP", ignoreCase = true)
    val isEnding: Boolean get() = type.equals("ED", ignoreCase = true)
}

/** The header's second line: "2 openings · 2 endings". */
fun List<ThemeSong>.themeSongsSummary(): String {
    val openings = count { it.isOpening }
    val endings = count { it.isEnding }
    val parts = buildList {
        if (openings > 0) add(if (openings == 1) "1 opening" else "$openings openings")
        if (endings > 0) add(if (endings == 1) "1 ending" else "$endings endings")
    }
    return parts.joinToString(" · ")
}
