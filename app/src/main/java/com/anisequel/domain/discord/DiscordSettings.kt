package com.anisequel.domain.discord

import com.anisequel.domain.jokes.JokeTone

/**
 * How often the joke line in the presence is replaced.
 *
 * The floor is a minute and the default is ten: a Discord presence is read by
 * other people, so a fast rotation would turn a member list into a feed.
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

/** How the first line is labeled to Discord. */
enum class DiscordActivityType(
    val storageValue: String,
    val displayName: String,
    val gatewayType: Int
) {
    PLAYING("playing", "Playing", 0),
    WATCHING("watching", "Watching", 3),
    LISTENING("listening", "Listening", 2),
    COMPETING("competing", "Competing in", 5);

    companion object {
        val DEFAULT = PLAYING

        fun fromStorage(value: String?): DiscordActivityType =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

enum class DiscordOnlineStatus(
    val storageValue: String,
    val displayName: String,
    val gatewayValue: String
) {
    ONLINE("online", "Online", "online"),
    IDLE("idle", "Idle", "idle"),
    DND("dnd", "Do Not Disturb", "dnd"),
    INVISIBLE("invisible", "Invisible", "invisible");

    companion object {
        val DEFAULT = ONLINE

        fun fromStorage(value: String?): DiscordOnlineStatus =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/** The two editable lines of one activity's presence. */
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
 * The default is off. A presence publishes the reader's activity to their
 * friends, so an opt-in is the only acceptable default.
 *
 * The app connects directly with the reader's own Discord token and speaks to
 * Discord's gateway. No relay is involved and no token leaves this device except
 * to Discord's official endpoints.
 */
data class DiscordSettings(
    val enabled: Boolean = false,
    val token: String = "",
    /**
     * Which credential [token] is, because Discord reads the two differently.
     *
     * A pasted user token travels raw in `Authorization` and in the gateway's
     * IDENTIFY; an access token minted by the app's own OAuth grant has to carry a
     * `Bearer ` prefix in both. Storing the kind alongside the token is what stops
     * a sign-in that worked from producing a credential the gateway refuses.
     */
    val authKind: DiscordAuthKind = DiscordAuthKind.TOKEN,
    /**
     * The Discord application this app signs in with.
     *
     * Supplied by the reader, the way AniList's client ID is: an OAuth flow needs
     * an application whose registered redirect is [DiscordOAuth.REDIRECT_URI], and
     * an app cannot invent one. Blank means the automatic sign-in is unavailable
     * and the page offers the paste-token path instead, which is what it offered
     * for every install before this existed.
     */
    val oauthClientId: String = "",
    val linked: Boolean = false,
    val username: String = "",
    val userId: String = "",
    val avatarUrl: String = "",
    /** Whether the current page/title is part of the line at all. */
    val showActivity: Boolean = true,
    val showCover: Boolean = true,
    val showStats: Boolean = true,
    val showJokes: Boolean = true,
    val showButtons: Boolean = true,
    val showElapsedTime: Boolean = true,
    /**
     * Replaces every title with its kind and drops the cover and the AniList
     * button. A privacy switch for people who do not want titles named.
     */
    val hideTitles: Boolean = false,
    /** The preview dock at the bottom of the Discord page. On by default. */
    val previewPinned: Boolean = true,
    val jokeInterval: DiscordJokeInterval = DiscordJokeInterval.DEFAULT,
    /** Selected tones. Empty is read as "all". */
    val jokeTones: Set<JokeTone> = JokeTone.entries.toSet(),
    /** True puts the joke on Discord's first line, false on the second. */
    val jokeInDetails: Boolean = false,
    /** Lines the reader added on top of the shipped pool. */
    val customJokes: List<String> = emptyList(),
    /** Per-activity overrides. An absent arm means [DiscordPhrasing.defaultFor]. */
    val phrasing: Map<DiscordActivityKind, DiscordPhrasing> = emptyMap(),
    val activityType: DiscordActivityType = DiscordActivityType.DEFAULT,
    val onlineStatus: DiscordOnlineStatus = DiscordOnlineStatus.DEFAULT,
    /** Custom label shown as the activity name in the preview/card. */
    val activityName: String = DISCORD_APP_NAME,
    val primaryButtonLabel: String = "View on AniList",
    val secondaryButtonLabel: String = "AniSequel on GitHub",
    val secondaryButtonUrl: String = DISCORD_PROJECT_URL,
    /** Use smoother presence refreshes with less aggressive reconnect timing. */
    val reduceNetworkChatter: Boolean = true
) {
    fun phrasingFor(kind: DiscordActivityKind): DiscordPhrasing =
        phrasing[kind] ?: DiscordPhrasing.defaultFor(kind)

    /** Empty is read as everything. */
    val activeJokeTones: Set<JokeTone>
        get() = if (jokeTones.isEmpty()) JokeTone.entries.toSet() else jokeTones

    /** Jokes can be drawn only when the switch and the interval both allow one. */
    val jokesScheduled: Boolean
        get() = showJokes && jokeInterval.schedules

    /** True once a token is linked and publishing is allowed. */
    val canPublish: Boolean
        get() = enabled && linked && token.trim().isNotEmpty()

    /** True when the reader can use the one-tap Discord sign-in on this install. */
    val canSignInWithDiscord: Boolean
        get() = DiscordOAuth.isValidClientId(oauthClientId)

    /**
     * Whether the page can honestly claim a Discord connection right now.
     *
     * `linked` alone is not enough: a relay-era install carries the flag with no
     * token, because the relay held the credentials, and calling that "Connected"
     * drew a live card over a presence that could never publish. [canPublish]
     * already refuses the same install, so the label asks the same question.
     */
    val connectionLabel: String
        get() = when {
            !enabled -> "Rich Presence disabled"
            canPublish -> username.takeIf { it.isNotBlank() }?.let { "Connected as $it" }
                ?: "Connected to Discord"
            oauthClientId.isNotBlank() -> "Preview only - sign in with Discord"
            else -> "Preview only - no Discord account linked"
        }
}

/** Validation helpers for direct Discord login. */
object DiscordLogin {

    /**
     * The page the device's browser is sent to when the reader asks to sign in.
     *
     * The app does not load this itself. It used to - a `WebView` hosted the page
     * and polled it for a token - and that was a login page inside an app the
     * reader could not check the address bar of. The browser is the version where
     * the host is visible and the session belongs to the reader.
     *
     * Constant rather than a literal at the call site so a test can pin it, and so
     * the two settings cards cannot end up pointing at different pages.
     */
    const val BROWSER_LOGIN_URL: String = "https://discord.com/login"

    private val whitespace = Regex("\\s+")

    /**
     * A pasted token with any scheme prefix removed and no whitespace left.
     *
     * `Bot` and `Bearer` are the two prefixes Discord's own documentation uses, and
     * a paste carries them in whatever case the source header had. The old version
     * used `removePrefix`, which is case-sensitive - so `bearer eyJ...` kept the word,
     * passed [looksLikeToken] on length alone once the space was stripped, and was
     * stored as a token Discord refuses with nothing in the message to suggest the
     * paste was the problem.
     *
     * The strip repeats until there is no prefix left, because a paste can carry
     * both (`"Bot Bearer eyJ..."`). It also accepts a tab or any other whitespace
     * between the prefix and the token: the old check was for a literal `"Bearer "`,
     * so a copied header with a tab survived as `"BearereyJ..."` once all whitespace
     * was removed - a value that is the right length and still the wrong token. A
     * prefix only counts when it is followed by whitespace, so a token that happens
     * to begin with the letters "bot" is left alone.
     */
    fun normalizeToken(raw: String): String {
        var value = raw.trim()
        var stripped = true
        while (stripped) {
            stripped = false
            for (prefix in listOf("Bearer", "Bot")) {
                if (!value.startsWith(prefix, ignoreCase = true)) continue
                val rest = value.substring(prefix.length)
                if (rest.isEmpty() || !rest.first().isWhitespace()) continue
                value = rest.trimStart()
                stripped = true
            }
        }
        return value.replace(whitespace, "")
    }

    fun looksLikeToken(raw: String): Boolean {
        val token = normalizeToken(raw)
        // Discord user/bot tokens vary in shape, so this is a plausibility check and
        // not a grammar: the dotted form is what the web client stores, and a long
        // opaque value is accepted because bot tokens have no dots at all. The
        // parentheses matter - without them `&&` binds first and the second clause
        // alone would accept a 50-character string with whitespace removed.
        return (token.length >= 30 && token.contains('.')) || token.length >= 50
    }

    fun maskedToken(token: String): String {
        val normalized = normalizeToken(token)
        if (normalized.length <= 10) return "••••••••"
        return normalized.take(4) + "••••••••" + normalized.takeLast(4)
    }
}
