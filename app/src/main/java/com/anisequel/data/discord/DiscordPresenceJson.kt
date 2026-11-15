package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordPresence

/**
 * The request body a Discord relay expects.
 *
 * ## Why this is hand-written JSON
 *
 * It is the one payload in the app that is not a Retrofit model, and it is
 * deliberately so: the presence is assembled from user text that may contain
 * quotes, newlines and emoji, and a Moshi adapter for this would still have to be
 * pinned by a test. A small, explicit writer keeps the escaping in one place and
 * lets the test assert the exact bytes.
 */
object DiscordPresenceJson {

    /**
     * Wraps [presence] in the relay's envelope.
     *
     * The relay is the only party that talks to Discord; `applicationId` is what
     * tells it which registered Discord application the presence belongs to.
     */
    fun envelope(applicationId: String, presence: DiscordPresence): String = buildString {
        append("{\"applicationId\":")
        appendQuoted(applicationId)
        append(",\"activity\":")
        appendActivity(presence)
        append('}')
    }

    private fun StringBuilder.appendActivity(presence: DiscordPresence) {
        append('{')
        append("\"name\":")
        appendQuoted(presence.activityName)
        // 0 is Discord's Playing type, and the only one a game-like presence
        // should use: Watching/Listening mean Spotify and embedded activities.
        append(",\"type\":0")
        append(",\"details\":")
        appendQuoted(presence.details)
        presence.state?.let { state ->
            append(",\"state\":")
            appendQuoted(state)
        }
        if (presence.largeImage != null || presence.largeText != null) {
            append(",\"assets\":{")
            var wrote = false
            presence.largeImage?.let { image ->
                append("\"large_image\":")
                appendQuoted(image)
                wrote = true
            }
            presence.largeText?.let { text ->
                if (wrote) append(',')
                append("\"large_text\":")
                appendQuoted(text)
            }
            append('}')
        }
        presence.startTimestampSeconds?.let { start ->
            append(",\"timestamps\":{\"start\":")
            append(start)
            append('}')
        }
        if (presence.buttons.isNotEmpty()) {
            append(",\"buttons\":[")
            presence.buttons.forEachIndexed { index, button ->
                if (index > 0) append(',')
                append("{\"label\":")
                appendQuoted(button.label)
                append(",\"url\":")
                appendQuoted(button.url)
                append('}')
            }
            append(']')
        }
        append('}')
    }

    /**
     * A JSON string with the escapes Discord's gateway accepts.
     *
     * Control characters are escaped rather than dropped: a joke is allowed to
     * contain a newline, and emitting a literal one inside a JSON string would
     * make the whole request unparseable at the relay.
     */
    private fun StringBuilder.appendQuoted(value: String) {
        append('"')
        value.forEach { char ->
            when (char) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                else -> if (char < ' ') {
                    append("\\u")
                    append(char.code.toString(16).padStart(4, '0'))
                } else {
                    append(char)
                }
            }
        }
        append('"')
    }
}
