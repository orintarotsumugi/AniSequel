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

/**
 * How long a line stays on screen.
 *
 * The duration used to be a private constant in the host, which made the one
 * property of the pill a reader actually notices the one property they could not
 * change. A line about a forty-minute video essay is not readable in the same
 * three seconds as "Short show, short season", so the choice is real rather than
 * decorative.
 *
 * [UNTIL_DISMISSED] is the manual arm, and it is the reason [visibleMillis] is
 * nullable: zero would collide with "hide immediately", and a separate boolean
 * would be a second way to spell a state this enum already names.
 */
enum class JokeDwell(
    val storageValue: String,
    val displayName: String,
    /** Null means the pill waits for a tap instead of a timer. */
    val visibleMillis: Long?
) {
    QUICK("quick", "Quick", 3_000L),
    STANDARD("standard", "Standard", 5_000L),
    RELAXED("relaxed", "Relaxed", 8_000L),
    LONG("long", "Long", 12_000L),
    UNTIL_DISMISSED("until_dismissed", "Until tapped", null);

    val staysUntilDismissed: Boolean get() = visibleMillis == null

    companion object {
        fun fromStorage(value: String?): JokeDwell =
            entries.firstOrNull { it.storageValue == value } ?: STANDARD
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

/**
 * The kinds of line in [com.anisequel.ui.components.AppJokes].
 *
 * A single pool meant the only way to dislike one register was to dislike the
 * feature: a reader who wants the backlog lines has no way to say "less of this,
 * please". The split is by *subject* rather than by mood because the reader can
 * see the subject in a line and cannot reliably see their own mood, and because
 * a line's subject is stable while its funniness is not.
 *
 * [DARK] is the one added after the fact, and it earns its own arm: the jokes
 * about cancelled sequels, hiatuses and fictional deaths are the funniest lines
 * in the pool to one reader and the reason to switch the feature off to another.
 * Folding them into META would make "I want the app's voice" and "I want a joke
 * about a grave" the same switch.
 *
 * "Mixed" is not a member. It is every tone at once - what the default has
 * always been - and a member would be a copy of the other five that could drift
 * from them. The Settings screen offers it as a shortcut that turns them all on.
 *
 * The constants live in the domain rather than beside the text pool so the
 * filter can be stored, tested and described without importing Compose.
 */
enum class JokeTone(
    val storageValue: String,
    val displayName: String,
    val subtitle: String
) {
    FEELINGS(
        "feelings",
        "Feelings",
        "Finales, comfort shows and the episodes that hurt."
    ),
    BACKLOG(
        "backlog",
        "Backlog",
        "Watchlists, one more episode and your sleep schedule."
    ),
    CRAFT(
        "craft",
        "Craft",
        "Openings, animation, composers and the manga."
    ),
    DARK(
        "dark",
        "Dark",
        "Cancelled sequels, hiatuses and fictional deaths."
    ),
    META(
        "meta",
        "Ani-chan & the app",
        "Tier lists, the toast itself, and the app noticing you."
    );

    companion object {
        fun fromStorage(values: Set<String>): Set<JokeTone> =
            entries.filter { it.storageValue in values }.toSet()
    }
}

/**
 * The moments a reader can invite a line into, besides the timer.
 *
 * The timer is the feature's spine - it is the only trigger that needs no
 * explanation - but it also means a reader who opens the app for a minute never
 * sees a joke, while a reader who leaves it open for an hour gets several.
 * These three moments put a line where something already happened, which is why
 * each carries a minimum gap: the point is a line now and then, not one per tap.
 *
 * [OPEN] has no gap because it can only fire once per launch; the other two are
 * gated so the toast cannot become a reaction to every gesture.
 */
enum class JokeMoment(
    val storageValue: String,
    val displayName: String,
    val subtitle: String,
    val minGapMillis: Long
) {
    OPEN(
        "open",
        "When you open the app",
        "One line once per launch, while the first page settles.",
        0L
    ),
    TAB_CHANGE(
        "tab_change",
        "When you change tab",
        "At most one every six minutes, so browsing stays quiet.",
        6 * 60_000L
    ),
    REFRESH(
        "refresh",
        "After you refresh",
        "A line after you ask the dashboard to reload.",
        3 * 60_000L
    );

    companion object {
        fun fromStorage(values: Set<String>): Set<JokeMoment> =
            entries.filter { it.storageValue in values }.toSet()
    }
}

/**
 * How a line arrives on screen.
 *
 * **Off by default.** The pill's job is to say something and go, and a reveal
 * delays the sentence for the reader who did not ask for one; the app's motion
 * setting is a whole-app choice, this is a joke-specific flourish. It is offered
 * because text that writes itself is a genuinely different pleasure from text that
 * appears, and because the dwell can be raised to match it.
 *
 * Only two arms are writers: [FADE_UP] is a single entrance rather than a
 * character loop, and [NONE] is the shipped behaviour. All of them are the same
 * text once revealed - the choice changes when it is readable, never what it says.
 */
enum class JokeTextEffect(
    val storageValue: String,
    val displayName: String,
    val subtitle: String
) {
    NONE("none", "Off", "The line appears all at once."),
    TYPEWRITER("typewriter", "Typewriter", "Characters arrive one at a time."),
    WORD_WRITER("word", "Word by word", "Words arrive one at a time."),
    FADE_UP("fade_up", "Fade up", "The line fades and rises into place.");

    /** True for the arms that reveal text progressively. */
    val revealsProse: Boolean
        get() = this == TYPEWRITER || this == WORD_WRITER

    companion object {
        val DEFAULT = NONE

        fun fromStorage(value: String?): JokeTextEffect =
            entries.firstOrNull { it.storageValue == value } ?: DEFAULT
    }
}

/**
 * Reveal cadence, in milliseconds, and the budget the writers share.
 *
 * The per-character step is deliberately brisk; the budget is what keeps a long
 * line from still typing when a Quick dwell is already taking the pill down. Both
 * live here so the toast and its Settings preview reveal at exactly the same
 * speed - a preview that types faster than the product is a lie about the product.
 */
const val JOKE_WRITER_CHAR_MILLIS = 26L
const val JOKE_WRITER_WORD_MILLIS = 90L
const val JOKE_WRITER_BUDGET_MILLIS = 2_600L

/** Everything the joke toast reads, in one snapshot. */
data class JokeSettings(
    val enabled: Boolean = true,
    val frequency: JokeFrequency = JokeFrequency.NORMAL,
    val position: JokePosition = JokePosition.BOTTOM,
    val scope: JokeScope = JokeScope.MAIN_PAGES,
    val dwell: JokeDwell = JokeDwell.STANDARD,
    /** The light tick when a line arrives. Nothing else in the toast has a haptic. */
    val haptics: Boolean = true,
    val tones: Set<JokeTone> = JokeTone.entries.toSet(),
    /** Extra moments beyond the timer. Empty is the shipped, quiet behaviour. */
    val moments: Set<JokeMoment> = emptySet(),
    /**
     * What the pill calls itself.
     *
     * The toast used to be signed "Ani-chan" in the code. That is the app's voice,
     * not a law: the line arrives on the reader's own phone and they are allowed to
     * rename the speaker. Stored trimmed, and read through [displayName] so an
     * empty value falls back rather than printing a blank name.
     */
    val speakerName: String = DEFAULT_SPEAKER_NAME,
    /**
     * The glyph in the pill's badge - a reader-chosen stamp, default a star.
     *
     * Deliberately a text glyph rather than one of the app's vectors. This one is
     * *content* the reader authors, like the saved lines, not a piece of chrome the
     * icon rule in AGENTS.md §2 governs; it ships as "★" and the Settings page
     * offers a small palette plus a free field, which is how it ends up as anything
     * from a heart to a ghost.
     */
    val stamp: String = DEFAULT_STAMP,
    /** How the line itself arrives. Off unless the reader asks for a reveal. */
    val textEffect: JokeTextEffect = JokeTextEffect.DEFAULT,
    /**
     * Draw the pill with the line and the heart only.
     *
     * Off by default. The speaker line and the dismiss caption are useful the
     * first week and noise by the hundredth joke; a reader who has learned the
     * pill can ask for the smallest version of it. The stamp still shows - it is
     * the pill's face - and the caption only appears on the "until tapped" dwell,
     * where it is the only instruction that matters.
     */
    val compact: Boolean = false
) {
    /**
     * The single question the host asks before scheduling a wait. Keeping it here
     * means "the switch is on but the frequency is Off" cannot mean two things in
     * the settings screen and the toast.
     */
    val autoJokesEnabled: Boolean get() = enabled && frequency.showsOnItsOwn

    /** [speakerName], or the default when the reader left it blank. */
    val displayName: String
        get() = speakerName.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_SPEAKER_NAME

    /** [stamp], or the default star when the reader left it blank. */
    val displayStamp: String
        get() = stamp.trim().takeIf { it.isNotEmpty() } ?: DEFAULT_STAMP

    /**
     * The tones a draw may use, with "nothing selected" read as "everything".
     *
     * The Settings screen refuses to switch the last tone off, so an empty set
     * should not reach here from the UI. It still has to mean something, because
     * it is what a cleared preference or a downgrade looks like, and a toast that
     * draws from an empty pool would either crash or go quiet with no explanation.
     * All tones is the only answer that cannot look like a bug.
     */
    val activeTones: Set<JokeTone>
        get() = if (tones.isEmpty()) JokeTone.entries.toSet() else tones

    /**
     * Whether the reader is drawing from everything, which is what Settings calls
     * "Mixed".
     *
     * Derived from [activeTones] rather than stored: it has to stay true when the
     * five switches are turned on one at a time, and it has to go false the moment
     * one is turned off. A stored flag would be a second answer to a question the
     * tone set already answers, and the two would eventually disagree.
     */
    val isMixed: Boolean get() = activeTones.size == JokeTone.entries.size
}

/**
 * The shipped persona, and the bounds the Settings fields accept.
 *
 * The glyph is a star rather than the app's sparkle vector on purpose: it is the
 * reader's stamp, and a text glyph can become any symbol they want without an
 * asset. Twelve UTF-16 units is enough for a multi-part emoji and short enough
 * that the badge cannot become a paragraph.
 */
const val DEFAULT_SPEAKER_NAME = "Ani-chan"
const val DEFAULT_STAMP = "\u2605"
const val MAX_SPEAKER_NAME_LENGTH = 24
const val MAX_STAMP_LENGTH = 12
