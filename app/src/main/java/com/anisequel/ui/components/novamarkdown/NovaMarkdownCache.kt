package com.anisequel.ui.components.novamarkdown

/**
 * Parsed bios, kept between screens for as long as the process lives.
 *
 * ## Why this exists
 *
 * A bio used to be parsed twice on the way to the page, and both copies were then
 * thrown away. [NovaMarkdown.parse] turns AniList's Markdown into the block list
 * [NovaMarkdownView] draws, and it is not free: every paragraph goes through the
 * block rules and then through the inline ones. `UserOverviewViewModel` parses the
 * bio to find the AniList links whose posters it has to fetch, and `AboutCard`
 * parses the same text to draw it - two full passes per open, the second answering
 * a question the first had already answered, and both discarded the moment the
 * screen moved on.
 *
 * The key is the document itself. Two people cannot edit each other's bio, and the
 * same text always parses to the same blocks, so there is nothing to invalidate:
 * an edited bio is different text and therefore a different key.
 *
 * It is bounded, because a bio can be arbitrarily long and the reader can open as
 * many profiles as they like, and it is deliberately only an in-memory tier - the
 * bio *text* is already persisted with the profile snapshot, so nothing here needs
 * to survive the process.
 *
 * Sharing the returned list is safe: [NovaBlock] is immutable and the renderer adds
 * no state to it, so two compositions reading the same blocks at once are two reads
 * of a `val`.
 */
internal object NovaMarkdownCache {

    /**
     * How many documents are held at once.
     *
     * A dozen is more profiles than a reader can navigate between and come back
     * to inside one session, and small enough that the cache cannot become a
     * second copy of every bio the app has ever seen. Least recently used goes
     * first: `LinkedHashMap`'s access order is what makes "come back to the last
     * profile" a hit rather than an eviction.
     */
    internal const val MAX_ENTRIES = 12

    private val entries = object : LinkedHashMap<String, List<NovaBlock>>(16, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<NovaBlock>>?
        ): Boolean = size > MAX_ENTRIES
    }

    /**
     * The parsed form of [about], parsed at most once per distinct document.
     *
     * Synchronized because the two callers are not on one coroutine: the
     * ViewModel parses on `viewModelScope` and the card parses in composition.
     * Both are on the main thread today, and the lock is what keeps that from
     * being a load-bearing fact.
     */
    @Synchronized
    fun parse(about: String): List<NovaBlock> =
        entries.getOrPut(about) { NovaMarkdown.parse(about) }

    /** Empties the cache. Tests only: nothing in the app has a reason to. */
    @Synchronized
    internal fun clear() {
        entries.clear()
    }

    /** How many documents are held. Tests only. */
    @Synchronized
    internal fun size(): Int = entries.size
}
