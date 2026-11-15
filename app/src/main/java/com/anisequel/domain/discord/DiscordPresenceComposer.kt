package com.anisequel.domain.discord

/**
 * Turns the live activity, the reader's stats and a joke into the two lines
 * Discord actually renders.
 *
 * ## Why this is a pure object
 *
 * Formatting is where a presence feature goes wrong quietly: a template with a
 * typo, a hidden title that still leaks through a button, an empty `$stats`
 * leaving a dangling "·", a line one character over Discord's 128 and cut
 * mid-word. None of those throw, and all of them are visible to somebody else
 * before the reader sees them. Keeping the rules here means the tests can pin
 * them.
 *
 * ## What it deliberately does not do
 *
 * It does not decide *when* to publish and it does not touch a socket. The host
 * owns the clock and the transport; this owns the sentence.
 */
object DiscordPresenceComposer {

    /**
     * A presence is always composed, even while the feature is off.
     *
     * The Settings page's preview is the feature's proof, and it has to draw
     * before the master switch is on - otherwise the reader has to publish to
     * their friends to find out what publishing would look like.
     */
    fun compose(
        settings: DiscordSettings,
        activity: DiscordActivity,
        stats: DiscordStats,
        joke: String? = null
    ): DiscordPresence {
        val phrasing = settings.phrasingFor(activity.kind)
        val title = visibleTitle(settings, activity)
        val lines = composeLines(
            settings = settings,
            activity = activity,
            subject = title ?: activity.kind.anonymousSubject,
            statsLine = statsLine(settings, stats),
            joke = jokeLine(settings, joke),
            detailsTemplate = detailsTemplate(settings, phrasing),
            stateTemplate = phrasing.state
        )
        return presence(settings, activity, title, lines)
    }

    /**
     * The title Discord is allowed to name.
     *
     * `hideTitles` is checked once here rather than at each of the four places a
     * title is read (the two lines, the cover, the button), so the privacy switch
     * cannot be honoured by three of them and forgotten by the fourth.
     */
    private fun visibleTitle(settings: DiscordSettings, activity: DiscordActivity): String? {
        val title = activity.title
        if (title.isNullOrBlank()) return null
        return if (settings.hideTitles) null else title
    }

    private fun statsLine(settings: DiscordSettings, stats: DiscordStats): String =
        if (settings.showStats) stats.line() else ""

    private fun detailsTemplate(settings: DiscordSettings, phrasing: DiscordPhrasing): String =
        if (settings.showActivity) phrasing.details else "Browsing AniSequel"

    /**
     * The joke that may appear, if any.
     *
     * `jokesScheduled`, not `showJokes`: the interval's Off is also a way to say
     * no jokes, and the host stops rotating on it. Using the switch alone left the
     * last joke stuck in the presence after the reader chose Off.
     */
    private fun jokeLine(settings: DiscordSettings, joke: String?): String? {
        if (!settings.jokesScheduled) return null
        return joke?.trim()?.takeIf { it.isNotEmpty() }
    }

    /** The two lines, already cleaned and cut, plus the joke that made it in. */
    private data class Lines(val first: String, val second: String, val joke: String?)

    private fun composeLines(
        settings: DiscordSettings,
        activity: DiscordActivity,
        subject: String,
        statsLine: String,
        joke: String?,
        detailsTemplate: String,
        stateTemplate: String
    ): Lines {
        var details = resolve(detailsTemplate, subject, activity, statsLine, joke)
        var state = resolve(stateTemplate, subject, activity, statsLine, joke)

        // `$joke` in a template is the reader asking for the joke in that line.
        // When neither template mentions it, it goes on the state line - the
        // lower one - unless the reader moved it up. Two lines with the same joke
        // would be a wall of the same sentence on a friend's profile.
        val templateWantsJoke = detailsTemplate.contains(JOKE_PLACEHOLDER) ||
            stateTemplate.contains(JOKE_PLACEHOLDER)
        if (joke != null && !templateWantsJoke) {
            if (settings.jokeInDetails) {
                details = join(details, joke)
            } else {
                state = join(state, joke)
            }
        }

        return Lines(
            first = clean(details).fitDiscord(),
            second = clean(state).fitDiscord(),
            joke = joke
        )
    }

    private fun presence(
        settings: DiscordSettings,
        activity: DiscordActivity,
        title: String?,
        lines: Lines
    ): DiscordPresence {
        val coverVisible = settings.showCover && title != null
        return DiscordPresence(
            details = lines.first,
            state = lines.second.takeIf { it.isNotBlank() },
            largeImage = if (coverVisible) activity.coverUrl else null,
            largeText = if (coverVisible) title else null,
            startTimestampSeconds = activity.startedAtMillis
                .takeIf { it > 0L }
                ?.div(1_000L),
            buttons = if (settings.showButtons) buttonsFor(activity, title) else emptyList(),
            kind = activity.kind,
            joke = lines.joke
        )
    }

    private fun buttonsFor(activity: DiscordActivity, title: String?): List<DiscordButton> =
        buildList {
            // The link follows *buttons*, not cover art. It used to require
            // `showCover`, so turning off a picture also removed the way to
            // open the title - two unrelated switches sharing one condition.
            // A title is only absent here when `hideTitles` removed it, and
            // that case is already covered by `visibleTitle`.
            if (title != null && activity.pageUrl != null) {
                add(
                    DiscordButton(
                        label = "View on AniList",
                        url = activity.pageUrl.fit(DISCORD_BUTTON_URL_LIMIT)
                    )
                )
            }
            add(
                DiscordButton(
                    label = "AniSequel on GitHub",
                    url = DISCORD_PROJECT_URL
                )
            )
        }

    /**
     * Resolves one template against the live values.
     *
     * Unknown `$placeholders` are removed rather than printed: a reader who typed
     * `$animeTitle` should see a clean line in Discord, not a literal prompt in
     * somebody else's friends list. The editor lists the valid ones directly
     * above the field.
     */
    fun resolve(
        template: String,
        subject: String,
        activity: DiscordActivity,
        statsLine: String,
        joke: String?
    ): String {
        val values = mapOf(
            "\$anime" to subject,
            "\$manga" to subject,
            "\$novel" to subject,
            "\$title" to subject,
            "\$status" to (activity.status?.takeIf { it.isNotBlank() } ?: "AniSequel"),
            "\$episode" to (activity.progress?.takeIf { it.isNotBlank() } ?: ""),
            "\$joke" to (joke ?: ""),
            "\$stats" to statsLine
        )
        return placeholderPattern.replace(template) { match ->
            values[match.value] ?: ""
        }
    }

    /**
     * Collapses whitespace, then removes separators left behind by an empty
     * placeholder.
     *
     * Without the second half, a reader who turns stats off gets
     * `"Watching Frieren · "` - a trailing dot that is small in a diff and
     * obvious in a friends list.
     */
    internal fun clean(text: String): String {
        var result = text.replace(whitespace, " ").trim()
        if (result.isEmpty()) return result
        result = result.replace(separatorRuns, " · ")
        result = result.trim(' ', '·', '|', ',', ';', '-')
        return result
    }

    private fun join(first: String, second: String): String =
        listOf(first.trim(), second.trim())
            .filter { it.isNotEmpty() }
            .joinToString(" · ")

    private val placeholderPattern = Regex("\\$[A-Za-z][A-Za-z0-9]*")
    private val whitespace = Regex("\\s+")
    private val separatorRuns = Regex("(\\s*[·|]\\s*)+")
}

/** `$joke` as a template token, without Kotlin interpolating it. */
const val JOKE_PLACEHOLDER = "\$joke"

/**
 * Discord truncates long detail/state strings, and it does so at its own
 * boundary. Cutting here keeps the ellipsis and avoids splitting a surrogate
 * pair, which would render as a replacement glyph on the other side.
 */
internal fun String.fitDiscord(): String = fit(DISCORD_TEXT_LIMIT)

internal fun String.fit(limit: Int): String {
    if (length <= limit) return this
    var end = limit - 1
    if (end > 0 && this[end - 1].isHighSurrogate()) end--
    val head = substring(0, end).trimEnd()
    return if (head.isEmpty()) "…" else "$head…"
}
