package com.anisequel.ui.components

import com.anisequel.domain.jokes.JokeTone
import kotlin.random.Random

/**
 * One line and the register it is written in.
 *
 * The tone is attached at the source rather than derived from keywords: "the
 * show has a beach episode" could be filed under craft or feelings depending on
 * the punchline, and a heuristic that guesses would file it differently on a
 * Tuesday.
 */
data class Joke(val text: String, val tone: JokeTone)

/**
 * The lines the random joke toast draws from.
 *
 * ## Why this is not [ApiFailureBody]'s pool
 *
 * The failure pools are about AniList being missing. This one appears while
 * everything is working, so a line about naps and servers would read as a
 * warning about the problem the reader does not have. These are about the hobby
 * instead - backlogs, recaps, rewatches - which is what a random toast can
 * safely be about during normal use.
 *
 * ## Why the text lives in two other files
 *
 * The pool grew past the point where this object could hold the selection rules
 * *and* five registers of prose without becoming a file nobody reviews. The text
 * is in [JokeLinesReader] (the reader's side of the hobby and the app's own
 * voice) and [JokeLinesShow] (the shows themselves); this object is only the
 * index and the draw. That is a split by subject, so a line moving between the
 * files is a line whose subject changed - which is worth seeing in a diff.
 *
 * ## Why it is an object with no Android imports
 *
 * The pool is user-facing text that has to stay duplicate-free and large enough
 * to survive a long session; that is a plain JVM test, not a screenshot. Keeping
 * it free of Compose and Android means the test that pins it runs with the rest
 * of the unit tests instead of needing a device.
 *
 * ## Why it is grouped by tone
 *
 * The reader can switch a tone off in Settings, so the pool has to be filterable
 * without a second copy of the text. Five lists of strings, one pass that tags
 * them, and [poolFor] is the only place that knows how a tone becomes a draw.
 * The grouping is also what makes the balance reviewable: a tone that grows to
 * ten lines and one that grows to two hundred are visible in the file.
 *
 * "Mixed" in Settings is not a sixth list - it is every tone switched on at
 * once, which is what the default has always been. A tone that was a copy of the
 * other five would drift from them, and the shuffle bag would then count the same
 * line twice.
 *
 * None of these are emoji: the app draws its own vectors, and a joke is carried
 * by the words rather than by a face.
 */
object AppJokes {

    /**
     * Every line, in one list, tagged once.
     *
     * Built from the groups rather than written as `Joke("...", tone)` pairs: the
     * pairs put the tone on every line and made the file read as a roll call,
     * while the groups keep each register's voice together and make an accidental
     * tone change visible in a diff.
     *
     * The order matters in exactly one place - `orderedFavourites` renders the
     * reader's saved lines in it - so it is the order the tones are listed in
     * Settings: the reader's side, then the shows', then the app talking about
     * itself last.
     */
    val pool: List<Joke> = buildList {
        JokeLinesReader.feelings.forEach { add(Joke(it, JokeTone.FEELINGS)) }
        JokeLinesReader.backlog.forEach { add(Joke(it, JokeTone.BACKLOG)) }
        JokeLinesShow.craft.forEach { add(Joke(it, JokeTone.CRAFT)) }
        JokeLinesShow.dark.forEach { add(Joke(it, JokeTone.DARK)) }
        JokeLinesReader.meta.forEach { add(Joke(it, JokeTone.META)) }
    }

    /** Just the text, in pool order. The tests and the shuffle bag both key on it. */
    val lines: List<String> = pool.map { it.text }

    /**
     * The lines a draw may use.
     *
     * An empty filter means the whole pool, matching
     * [com.anisequel.domain.jokes.JokeSettings.activeTones]. The fallback is
     * defensive as well as semantic: an empty candidate list would make every
     * draw below return nothing, and a quiet toast with no explanation is harder
     * to diagnose than a slightly wrong filter.
     */
    fun poolFor(tones: Set<JokeTone>): List<Joke> {
        if (tones.isEmpty()) return pool
        val filtered = pool.filter { it.tone in tones }
        return filtered.ifEmpty { pool }
    }

    /** How many lines the current filter can draw from. */
    fun countFor(tones: Set<JokeTone>): Int = poolFor(tones).size

    /**
     * The reader's saved lines in a stable order, with lines this build no
     * longer knows about kept at the end.
     *
     * Pool order rather than save order because DataStore stores a set: the
     * insertion order was never persisted, and pretending otherwise would mean a
     * second key recording timestamps for a list that rarely has ten entries.
     * Unknown lines are not dropped - a saved line that outlives a pool edit is
     * still the reader's line, and deleting it silently is the one outcome worse
     * than an odd sort position.
     */
    fun orderedFavourites(saved: Set<String>): List<String> {
        val known = pool.mapNotNull { joke -> joke.text.takeIf { it in saved } }
        val knownSet = known.toSet()
        val unknown = saved.filterNot { it in knownSet }.sorted()
        return known + unknown
    }

    /** One line, uniformly at random from the tones that are switched on. */
    fun pick(
        tones: Set<JokeTone> = JokeTone.entries.toSet(),
        random: Random = Random.Default
    ): String {
        val candidates = poolFor(tones)
        return candidates[random.nextInt(candidates.size)].text
    }

    /**
     * One draw from the shuffle bag, including the rule at the seam.
     *
     * [seen] is the bag, updated in place: the drawn line joins it, and a spent
     * bag is cleared *while keeping [last]* - the line currently on screen - so
     * the first draw of the new cycle cannot be the last draw of the old one. A
     * reader does not experience the pool as adjacent draws, they experience it as
     * "has this app told me this before", which is why the seam is the one repeat
     * worth spending a rule on.
     *
     * The rule lived in the host for two releases, where only a comment pinned it:
     * a bag cleared *including* the last line passes every other test here and
     * still repeats at the seam. It is a pure function of the bag and the filter,
     * so it belongs beside [pickUnseen] where a test can fail on it.
     *
     * [last] is empty before anything has been shown, which is the one value that
     * is not a pool line and therefore cannot shrink the bag.
     */
    fun draw(
        seen: MutableSet<String>,
        last: String,
        tones: Set<JokeTone> = JokeTone.entries.toSet(),
        random: Random = Random.Default
    ): String {
        val candidates = poolFor(tones)
        if (candidates.all { it.text in seen }) {
            seen.clear()
            if (last.isNotEmpty()) seen += last
        }
        val drawn = pickUnseen(seen, tones, random)
        seen += drawn
        return drawn
    }

    /**
     * One line that is not in [seen], or a fresh pick once the pool is spent.
     *
     * Avoiding only the previous line meant the toast could repeat a joke it had
     * told an hour ago - a reader does not experience the pool as a sequence of
     * adjacent draws, they experience it as "has this app told me this before".
     * The caller keeps the set and clears it when every line in the *current
     * filter* has been used, which turns the pool into a shuffle bag: each line
     * once per cycle, in random order, and never the same line twice in a row
     * across the seam.
     *
     * [tones] is the filter rather than the caller's problem: a reader who
     * switched Craft off must not have the bag count Craft lines as unread and
     * then spend eleven draws on lines it will never show.
     */
    fun pickUnseen(
        seen: Set<String>,
        tones: Set<JokeTone> = JokeTone.entries.toSet(),
        random: Random = Random.Default
    ): String {
        val candidates = poolFor(tones)
        val unseen = candidates.filterNot { it.text in seen }
        val drawable = unseen.ifEmpty { candidates }
        return drawable[random.nextInt(drawable.size)].text
    }
}
