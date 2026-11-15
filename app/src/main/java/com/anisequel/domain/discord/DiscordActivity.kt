package com.anisequel.domain.discord

/**
 * The activities AniSequel can announce to Discord.
 *
 * An enum rather than a free string because each arm is a *template* the reader can
 * edit (Settings > Discord RPC > Activity phrasing) and a row in that editor. A
 * free string would let the screen offer a phrasing for an activity the presence
 * composer never produces, and nothing would fail - the row would simply never
 * apply.
 *
 * [defaultDetails] and [defaultState] are the shipped phrasing, and they are also
 * what the editor's Reset restores. `$name` placeholders are resolved by
 * [DiscordPresenceComposer]; the escaping is only Kotlin's.
 *
 * The kinds mirror what the app can actually see: the tabs it is on, and the
 * detail sheet it has open. There is deliberately no "Watching episode N" kind
 * until there is an in-app player - a presence that claims something the app
 * cannot observe is a lie in somebody else's friends list.
 */
enum class DiscordActivityKind(
    val storageValue: String,
    val displayName: String,
    val defaultDetails: String,
    val defaultState: String,
    /** Used when the title is unknown, or hidden by the privacy switch. */
    val anonymousSubject: String
) {
    WATCHING_ANIME(
        storageValue = "watching_anime",
        displayName = "Watching Anime",
        defaultDetails = "Watching \$anime",
        defaultState = "\$stats",
        anonymousSubject = "an anime"
    ),
    VIEWING_ANIME_DETAILS(
        storageValue = "viewing_anime_details",
        displayName = "Viewing Anime Details",
        defaultDetails = "Viewing \$anime",
        defaultState = "\$stats",
        anonymousSubject = "an anime"
    ),
    READING_MANGA(
        storageValue = "reading_manga",
        displayName = "Reading Manga",
        defaultDetails = "Reading \$manga",
        defaultState = "\$stats",
        anonymousSubject = "a manga"
    ),
    VIEWING_MANGA_DETAILS(
        storageValue = "viewing_manga_details",
        displayName = "Viewing Manga Details",
        defaultDetails = "Viewing \$manga",
        defaultState = "\$stats",
        anonymousSubject = "a manga"
    ),
    READING_NOVEL(
        storageValue = "reading_novel",
        displayName = "Reading Novel",
        defaultDetails = "Reading \$novel",
        defaultState = "\$stats",
        anonymousSubject = "a novel"
    ),
    VIEWING_NOVEL_DETAILS(
        storageValue = "viewing_novel_details",
        displayName = "Viewing Novel Details",
        defaultDetails = "Viewing \$novel",
        defaultState = "\$stats",
        anonymousSubject = "a novel"
    ),

    /**
     * The fallback for every page that is not a title: Home, Library, Search,
     * Settings. `$status` is the page's own name, so the line says "Browsing the
     * Library" rather than "Playing AniSequel" on every screen.
     */
    BROWSING(
        storageValue = "browsing",
        displayName = "Browsing / Idle",
        defaultDetails = "Browsing \$status",
        defaultState = "\$stats",
        anonymousSubject = "AniSequel"
    );

    companion object {
        /**
         * Unknown storage values fall back to [BROWSING] rather than throwing.
         *
         * DataStore survives upgrades, and a build that once knew an activity this
         * one does not is a downgrade the app has to survive: losing one phrasing
         * is recoverable, crashing the Settings page is not.
         */
        fun fromStorage(value: String?): DiscordActivityKind =
            entries.firstOrNull { it.storageValue == value } ?: BROWSING

        /**
         * The detail kind for an AniList node.
         *
         * A novel is filed under `MANGA` by AniList, so the format is the only
         * thing that distinguishes it; reading only the type would announce every
         * light novel as a manga and leave the three novel phrasings unreachable.
         */
        fun forDetail(type: String?, format: String?): DiscordActivityKind {
            val isManga = type.equals("MANGA", ignoreCase = true)
            val isNovel = isManga && format?.contains("NOVEL", ignoreCase = true) == true
            return when {
                isNovel -> VIEWING_NOVEL_DETAILS
                isManga -> VIEWING_MANGA_DETAILS
                else -> VIEWING_ANIME_DETAILS
            }
        }

        /** The kinds in the order the phrasing editor lists them. */
        val phrasingOrder: List<DiscordActivityKind> = listOf(
            WATCHING_ANIME,
            VIEWING_ANIME_DETAILS,
            READING_MANGA,
            VIEWING_MANGA_DETAILS,
            READING_NOVEL,
            VIEWING_NOVEL_DETAILS,
            BROWSING
        )
    }
}

/**
 * The numbers the presence can quote, folded from the list the dashboard already
 * downloaded.
 *
 * Counts rather than a percentage or an average score: AniList's score is stored
 * in whatever format the account chose (100-point, 10-point, 5 stars), so an
 * "average" here would be a number the reader never gave. Episode and chapter
 * progress are unambiguous and are the thing a friend can actually read.
 */
data class DiscordStats(
    val animeCompleted: Int = 0,
    val episodesWatched: Int = 0,
    val mangaCompleted: Int = 0,
    val chaptersRead: Int = 0,
    val sequelsFound: Int = 0
) {
    val isEmpty: Boolean
        get() = animeCompleted == 0 &&
            episodesWatched == 0 &&
            mangaCompleted == 0 &&
            chaptersRead == 0 &&
            sequelsFound == 0

    /**
     * One compact line, e.g. `"24 anime · 318 episodes · 12 sequels"`.
     *
     * Empty when there is nothing to say, so `$stats` can disappear from a
     * template without leaving a dangling separator - the composer cleans those
     * up, but an empty string is the honest input.
     */
    fun line(): String = buildList {
        if (animeCompleted > 0) add("$animeCompleted anime")
        if (episodesWatched > 0) add("$episodesWatched episodes")
        if (mangaCompleted > 0) add("$mangaCompleted manga")
        if (chaptersRead > 0) add("$chaptersRead chapters")
        if (sequelsFound > 0) add("$sequelsFound sequels")
    }.joinToString(" · ")

    companion object {
        val Empty = DiscordStats()
    }
}

/**
 * What the reader is doing *right now*, as the presence composer sees it.
 *
 * [startedAtMillis] is carried in the value rather than read from the clock by the
 * composer so a change of page and a recomposition are distinguishable: the
 * Discord timer should restart when the activity changes, not every time Compose
 * re-reads the settings. The bus that owns the value stamps it on a real change.
 */
data class DiscordActivity(
    val kind: DiscordActivityKind = DiscordActivityKind.BROWSING,
    val title: String? = null,
    val coverUrl: String? = null,
    val pageUrl: String? = null,
    /** `"Episode 5"`, `"Chapter 12"`, or null when the app does not know. */
    val progress: String? = null,
    /** The page or list status, e.g. `"the Library"` or `"currently watching"`. */
    val status: String? = null,
    val startedAtMillis: Long = 0L
) {
    /** True for the kinds that are about one named title. */
    val isTitleActivity: Boolean
        get() = kind != DiscordActivityKind.BROWSING && !title.isNullOrBlank()
}

/** One of Discord's two presence buttons. */
data class DiscordButton(val label: String, val url: String)

/**
 * A composed, Discord-ready activity.
 *
 * Kept free of Android and Compose so the 128-character limits, the privacy
 * switch and the joke append are pinned by a JVM test rather than by opening the
 * app and looking at a card.
 */
data class DiscordPresence(
    val details: String,
    val state: String?,
    val largeImage: String? = null,
    val largeText: String? = null,
    val startTimestampSeconds: Long? = null,
    val buttons: List<DiscordButton> = emptyList(),
    val activityName: String = DISCORD_APP_NAME,
    val kind: DiscordActivityKind = DiscordActivityKind.BROWSING,
    /** The line this presence is currently quoting, if any. Preview-only. */
    val joke: String? = null
)

/** Discord's own limits. A longer string is rejected or silently cut server-side. */
const val DISCORD_TEXT_LIMIT = 128
const val DISCORD_BUTTON_LABEL_LIMIT = 32
const val DISCORD_BUTTON_URL_LIMIT = 512

/** The name the presence card and the Discord activity both use. */
const val DISCORD_APP_NAME = "AniSequel"

/** Where the presence's second button goes when there is no title page to open. */
const val DISCORD_PROJECT_URL = "https://github.com/orintarotsumugi/AniSequel"
