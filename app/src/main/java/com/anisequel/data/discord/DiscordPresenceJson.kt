package com.anisequel.data.discord

import com.anisequel.domain.discord.DiscordActivityType
import com.anisequel.domain.discord.DiscordOnlineStatus
import com.anisequel.domain.discord.DiscordPresence

/**
 * Builds the JSON payloads used for direct Discord gateway presence updates.
 *
 * Hand-written to keep escaping explicit and to make the exact bytes easy to
 * pin in tests.
 */
object DiscordPresenceJson {

    fun gatewayPresenceUpdate(
        presence: DiscordPresence?,
        status: DiscordOnlineStatus,
        activityType: DiscordActivityType
    ): String = buildString {
        append("{\"op\":3,\"d\":{")
        append("\"since\":")
        append(presence?.startTimestampSeconds?.times(1000L) ?: 0L)
        append(",\"activities\":")
        if (presence == null || status == DiscordOnlineStatus.INVISIBLE) {
            append("[]")
        } else {
            append('[')
            appendActivity(presence, activityType)
            append(']')
        }
        append(",\"status\":")
        appendQuoted(status.gatewayValue)
        append(",\"afk\":false}}")
    }

    fun activityObject(
        presence: DiscordPresence,
        activityType: DiscordActivityType
    ): String = buildString {
        appendActivity(presence, activityType)
    }

    private fun StringBuilder.appendActivity(
        presence: DiscordPresence,
        activityType: DiscordActivityType
    ) {
        append('{')
        append("\"name\":")
        appendQuoted(presence.activityName)
        append(",\"type\":")
        append(activityType.gatewayType)
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
            append(start * 1000L)
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
