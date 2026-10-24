package com.anisequel.domain.jokes

import kotlin.random.Random

/**
 * How often Ani-chan says something on her own.
 *
 * The waits live here rather than in [com.anisequel.ui.components.JokeToastHost]
 * because they are the thing the reader is choosing between: "Calm" and "Chatty"
 * are only words for two pairs of numbers, and a host that owned the numbers
 * would leave the settings screen describing a frequency it did not control.
 *
 * [OFF] is the switch in numeric form. The separate `enabled` flag exists so a
 * reader can turn jokes off without losing the frequency they picked, which is
 * what every other switch in Settings does.
 *
 * `firstDelay` is deliberately shorter than the repeat delay: a fifteen-minute
 * first wait means most sessions never see the feature at all, and a reader
 * cannot tell a bug from a long wait.
 */
enum class JokeFrequency(
    val storageValue: String,
    val displayName: String,
    val firstDelayMinMillis: Long,
    val firstDelayMaxMillis: Long,
    val repeatDelayMinMillis: Long,
    val repeatDelayMaxMillis: Long
) {
    OFF("off", "Off", 0L, 0L, 0L, 0L),
    CALM("calm", "Calm", 120_000L, 300_000L, 10 * 60_000L, 22 * 60_000L),
    NORMAL("normal", "Normal", 45_000L, 120_000L, 4 * 60_000L, 15 * 60_000L),
    CHATTY("chatty", "Chatty", 20_000L, 45_000L, 90_000L, 3 * 60_000L);

    /** False for [OFF], which is the only arm that never schedules a wait. */
    val showsOnItsOwn: Boolean get() = this != OFF

    fun firstWaitMillis(random: Random): Long =
        if (showsOnItsOwn) random.nextLong(firstDelayMinMillis, firstDelayMaxMillis) else 0L

    fun repeatWaitMillis(random: Random): Long =
        if (showsOnItsOwn) random.nextLong(repeatDelayMinMillis, repeatDelayMaxMillis) else 0L

    companion object {
        /**
         * An unknown stored value falls back to [NORMAL] rather than throwing.
         *
         * DataStore survives app upgrades, so a value written by a future build
         * and then downgraded is a real case. Losing a frequency is recoverable;
         * a crash on launch is not.
         */
        fun fromStorage(value: String?): JokeFrequency =
            entries.firstOrNull { it.storageValue == value } ?: NORMAL
    }
}

/** Which edge of the screen the joke pill sits on. */
enum class JokePosition(val storageValue: String, val displayName: String) {
    BOTTOM("bottom", "Bottom"),
    TOP("top", "Top");

    companion object {
        fun fromStorage(value: String?): JokePosition =
            entries.firstOrNull { it.storageValue == value } ?: BOTTOM
    }
}

/**
 * How much of the app jokes are allowed to appear over.
 *
 * [MAIN_PAGES] is the shape the feature shipped with: the bar's three pages,
 * where the reader has settled. [EVERYWHERE] includes the screens pushed from
 * those pages - Settings, the tier list, a profile - which is the "when I open
 * something" case. Login is excluded by the host in both arms: a joke over the
 * sign-in screen is an interruption at the one moment the reader is deciding.
 */
enum class JokeScope(val storageValue: String, val displayName: String) {
    MAIN_PAGES("main_pages", "Main pages"),
    EVERYWHERE("everywhere", "Everywhere");

    companion object {
        fun fromStorage(value: String?): JokeScope =
            entries.firstOrNull { it.storageValue == value } ?: MAIN_PAGES
    }
}

/** Everything the joke toast reads, in one snapshot. */
data class JokeSettings(
    val enabled: Boolean = true,
    val frequency: JokeFrequency = JokeFrequency.NORMAL,
    val position: JokePosition = JokePosition.BOTTOM,
    val scope: JokeScope = JokeScope.MAIN_PAGES
) {
    /**
     * The single question the host asks before scheduling a wait. Keeping it here
     * means "the switch is on but the frequency is Off" cannot mean two things in
     * the settings screen and the toast.
     */
    val autoJokesEnabled: Boolean get() = enabled && frequency.showsOnItsOwn
}
