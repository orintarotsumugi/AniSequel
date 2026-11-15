package com.anisequel.domain.discord

import com.anisequel.domain.jokes.JokeTone

/**
 * How often the joke line in the presence is replaced.
 *
 * The same idea as `JokeFrequency` in the in-app toast, but not the same values:
 * a Discord presence is read by other people, so "every 90 seconds" - which is
 * fine for a pill on your own phone - would turn a friend's member list into a
 * feed. The floor is a minute and the default is ten.
 *
 * [OFF] and the `showJokes` switch are separate on purpose, matching every other
 * joke setting in this app: turning jokes off should not throw away the interval
 * the reader picked.
 */
enum class DiscordJokeInterval(
    val storageValue: String,
    val displayName: String,
    val minutes: Int
) {
    OFF("off", "Off", 0),
    ONE("one", "Every minute", 1),
    FIVE("five", "Every 5 minutes", 5),
    TEN("ten", "Every 10 minutes", 10),
    FIFTEEN("fifteen", "Every 15 minutes", 15),
    THIRTY("thirty", "Every 30 minutes", 30);

    val schedules: Boolean get() = minutes > 0

    companion object {
        val DEFAULT = TEN

        fun fromStorage(value: String?): DiscordJokeInterval =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * The two editable lines of one activity's presence.
 *
 * Both are templates rather than finished strings: `$anime`, `$stats` and the rest
 * are resolved at compose time from the live activity. The Settings editor shows
 * the placeholders as chips so a reader can write "Watching $anime" without
 * guessing the spelling.
 */
data class DiscordPhrasing(
    val details: String,
    val state: String
) {
    companion object {
        fun defaultFor(kind: DiscordActivityKind): DiscordPhrasing =
            DiscordPhrasing(details = kind.defaultDetails, state = kind.defaultState)

        /** Every activity at its shipped phrasing. */
        val defaults: Map<DiscordActivityKind, DiscordPhrasing> =
            DiscordActivityKind.entries.associateWith(::defaultFor)
    }
}

/**
 * Everything the presence engine and its settings page remember.
 *
 * One value rather than a dozen flows: the card at the bottom of the page, the
 * composer and the publish loop are three readers that must not be able to
 * disagree about what they are drawing.
 *
 * The default is **off**. A presence publishes the reader's list to their friends
 * without them ever opening Settings, and this app is sideloaded; an opt-in is the
 * only acceptable default for that.
 */
data class DiscordSettings(
    val enabled: Boolean = false,
    val relayUrl: String = "",
    val applicationId: String = "",
    val linked: Boolean = false,
    val linkedUser: String = "",
    /**
     * Whether the current page/title is part of the line at all.
     *
     * Off leaves the stats and the joke, which is the "show what I watched, not
     * what I am reading right now" choice. It is separate from [enabled], which
     * stops the whole feature.
     */
    val showActivity: Boolean = true,
    val showCover: Boolean = true,
    val showStats: Boolean = true,
    val showJokes: Boolean = true,
    val showButtons: Boolean = true,
    /**
     * Replaces every title with its kind ("an anime", "a manga") and drops the
     * cover and the AniList button.
     *
     * The one switch that is about other people rather than the reader: a list can
     * contain something a person does not want named in a server's member list.
     */
    val hideTitles: Boolean = false,
    /** The preview dock at the bottom of the Discord page. On by default. */
    val previewPinned: Boolean = true,
    val jokeInterval: DiscordJokeInterval = DiscordJokeInterval.DEFAULT,
    /** Selected tones. Empty is read as "all", the same as the joke toast. */
    val jokeTones: Set<JokeTone> = JokeTone.entries.toSet(),
    /** True puts the joke on Discord's first line, false on the second. */
    val jokeInDetails: Boolean = false,
    /**
     * Lines the reader added on top of the shipped pool.
     *
     * Stored whole and drawn into the presence; the app's own pool is not copied
     * here, so an updated build's new lines arrive without touching these.
     */
    val customJokes: List<String> = emptyList(),
    /** Per-activity overrides. An absent arm means [DiscordPhrasing.defaultFor]. */
    val phrasing: Map<DiscordActivityKind, DiscordPhrasing> = emptyMap()
) {
    fun phrasingFor(kind: DiscordActivityKind): DiscordPhrasing =
        phrasing[kind] ?: DiscordPhrasing.defaultFor(kind)

    /** Empty is read as everything, exactly like `JokeSettings.activeTones`. */
    val activeJokeTones: Set<JokeTone>
        get() = if (jokeTones.isEmpty()) JokeTone.entries.toSet() else jokeTones

    /** Jokes can be drawn only when the switch and the interval both allow one. */
    val jokesScheduled: Boolean
        get() = showJokes && jokeInterval.schedules

    /** True once the relay has been proven reachable and an app id was given. */
    val canPublish: Boolean
        get() = enabled && linked && relayUrl.isNotBlank() && applicationId.isNotBlank()

    /** Whether the page can honestly claim a Discord connection right now. */
    val connectionLabel: String
        get() = when {
            !enabled -> "Rich Presence disabled"
            linked -> linkedUser.takeIf { it.isNotBlank() }?.let { "Connected as $it" }
                ?: "Connected to your relay"
            else -> "Preview only - no Discord relay linked"
        }
}

/**
 * The relay contract's client-side validation.
 *
 * Discord's own RPC transport is not available to a third-party Android app
 * without the native Social SDK (see `DiscordRpcClient`), so the app speaks to a
 * small relay the reader runs. That makes the URL untrusted input and the only
 * thing standing between a typo and a request with the reader's list in it.
 *
 * **HTTPS only.** A plain-HTTP relay would be blocked by the platform's cleartext
 * policy on this target SDK anyway, and accepting it in the field would turn a
 * blocked request into an unexplained failure. The message says which scheme is
 * required instead.
 */
object DiscordRelay {
    /** Discord application ids are snowflakes: 17-20 decimal digits. */
    private val applicationIdPattern = Regex("^\\d{17,20}$")

    /** Trailing slashes are dropped so the client can append its own path once. */
    fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return null
        val uri = runCatching { java.net.URI(trimmed) }.getOrNull() ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true)) return null
        if (uri.host.isNullOrBlank()) return null
        if (uri.userInfo != null) return null
        return trimmed
    }

    fun isValidApplicationId(raw: String): Boolean =
        applicationIdPattern.matches(raw.trim())

    fun healthUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}/v1/health"

    fun presenceUrl(baseUrl: String): String = "${baseUrl.trimEnd('/')}/v1/presence"
}
