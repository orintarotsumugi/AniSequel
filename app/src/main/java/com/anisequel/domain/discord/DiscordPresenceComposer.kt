package com.anisequel.domain.discord

/**
 * Turns the live activity, the reader's stats and a joke into the lines
 * Discord actually renders.
 */
object DiscordPresenceComposer {

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

    private fun visibleTitle(settings: DiscordSettings, activity: DiscordActivity): String? {
        val title = activity.title
        if (title.isNullOrBlank()) return null
        return if (settings.hideTitles) null else title
    }

    private fun statsLine(settings: DiscordSettings, stats: DiscordStats): String =
        if (settings.showStats) stats.line() else ""

    private fun detailsTemplate(settings: DiscordSettings, phrasing: DiscordPhrasing): String =
        if (settings.showActivity) phrasing.details else "Browsing AniSequel"

    private fun jokeLine(settings: DiscordSettings, joke: String?): String? {
        if (!settings.jokesScheduled) return null
        return joke?.trim()?.takeIf { it.isNotEmpty() }
    }

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
            startTimestampSeconds = if (settings.showElapsedTime) {
                activity.startedAtMillis.takeIf { it > 0L }?.div(1_000L)
            } else {
                null
            },
            buttons = if (settings.showButtons) buttonsFor(settings, activity, title) else emptyList(),
            // Discord's activity name shares the 128-character text budget. A
            // custom name was passed through whole, so a long one was rejected
            // server-side and the presence silently did not appear.
            activityName = settings.activityName.trim().ifBlank { DISCORD_APP_NAME }
                .fitDiscord(),
            kind = activity.kind,
            joke = lines.joke
        )
    }

    private fun buttonsFor(
        settings: DiscordSettings,
        activity: DiscordActivity,
        title: String?
    ): List<DiscordButton> =
        buildList {
            val primaryUrl = activity.pageUrl?.discordButtonUrl()
            if (title != null && primaryUrl != null) {
                add(
                    DiscordButton(
                        label = settings.primaryButtonLabel.trim().ifBlank { "View on AniList" }
                            .fit(DISCORD_BUTTON_LABEL_LIMIT),
                        url = primaryUrl
                    )
                )
            }
            val secondaryUrl =
                settings.secondaryButtonUrl.trim().ifBlank { DISCORD_PROJECT_URL }
                    .discordButtonUrl()
            if (secondaryUrl != null) {
                add(
                    DiscordButton(
                        label = settings.secondaryButtonLabel.trim().ifBlank { "AniSequel on GitHub" }
                            .fit(DISCORD_BUTTON_LABEL_LIMIT),
                        url = secondaryUrl
                    )
                )
            }
        }

    /**
     * A URL Discord can actually open, or null.
     *
     * A too-long URL used to go through [fit], which kept its first 511
     * characters and added an ellipsis - a guaranteed dead link that still used
     * one of the two button slots. Dropping the button is the honest failure: the
     * presence is still published, just without a button that cannot work. The
     * scheme check matches the one the secondary URL always had, so a page URL
     * that somehow arrived without http(s) is not handed to Discord either.
     */
    private fun String.discordButtonUrl(): String? {
        val trimmed = trim()
        if (trimmed.length > DISCORD_BUTTON_URL_LIMIT) return null
        if (!trimmed.startsWith("https://") && !trimmed.startsWith("http://")) return null
        return trimmed
    }

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

const val JOKE_PLACEHOLDER = "\$joke"

internal fun String.fitDiscord(): String = fit(DISCORD_TEXT_LIMIT)

internal fun String.fit(limit: Int): String {
    if (length <= limit) return this
    var end = limit - 1
    if (end > 0 && this[end - 1].isHighSurrogate()) end--
    val head = substring(0, end).trimEnd()
    return if (head.isEmpty()) "…" else "$head…"
}
