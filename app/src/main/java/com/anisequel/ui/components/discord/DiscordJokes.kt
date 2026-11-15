package com.anisequel.ui.components.discord

import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.jokes.JokeTone
import com.anisequel.ui.components.AppJokes
import kotlin.random.Random

/**
 * The lines the Discord presence may quote, and how one is chosen.
 *
 * The app's joke pool lives in the UI layer (it is copy, not domain), and the
 * composer deliberately takes a finished string so it stays pure. This object is
 * the join: the reader's custom lines, the selected tones, and a draw that avoids
 * repeating the line already on screen.
 *
 * A custom line is kept even when its tone is switched off. The reader wrote it;
 * the tone filter belongs to the shipped pool, and silently hiding their own line
 * because they turned off "Backlog" would look like the feature lost their text.
 */
object DiscordJokes {

    /** Custom lines first, then the shipped pool in its own tone order. */
    fun pool(settings: DiscordSettings): List<String> {
        val custom = settings.customJokes
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        val shipped = AppJokes.poolFor(settings.activeJokeTones).map { it.text }
        return (custom + shipped).distinct()
    }

    /**
     * One line, never the same as [previous] unless it is the only one.
     *
     * `Random` is a parameter so a test can make the draw deterministic; the host
     * passes the default.
     */
    fun next(
        settings: DiscordSettings,
        previous: String?,
        random: Random = Random.Default
    ): String? = next(settings, listOfNotNull(previous), random)

    /**
     * One line, avoiding everything in [recent], not only the line on screen.
     *
     * A two-line draw from a large pool repeats the *same* line about as often as
     * any other, and on a ten-minute rotation the reader sees a repeat long before
     * they see the whole pool. Remembering the last few is what makes the rotation
     * feel like a pool rather than a coin. Falls back to the whole pool when every
     * line is recent (a reader with two custom lines and Off on every tone).
     */
    fun next(
        settings: DiscordSettings,
        recent: Collection<String>,
        random: Random = Random.Default
    ): String? {
        val lines = pool(settings)
        if (lines.isEmpty()) return null
        if (lines.size == 1) return lines.first()
        val candidates = lines.filterNot { it in recent }.ifEmpty { lines }
        return candidates.random(random)
    }

    /**
     * The tone filter as a short label for the dropdown.
     *
     * "Mixed" is every tone, matching the joke toast's vocabulary. An empty set
     * is read as everything rather than as nothing, the same as
     * `JokeSettings.activeTones` - "no tones selected" is what a cleared
     * preference looks like, not a choice the UI can produce.
     */
    fun toneSummary(tones: Set<JokeTone>): String {
        val active = tones.ifEmpty { JokeTone.entries.toSet() }
        return when {
            active.size == JokeTone.entries.size -> "Mixed (every tone)"
            active.size == 1 -> active.first().displayName
            else -> "${active.size} tones"
        }
    }
}
