package com.anisequel.ui.components.novamarkdown

/**
 * An access-ordered cache with a byte budget instead of an entry count.
 *
 * Both of NovaMarkdown's SVG caches hold things whose size has nothing to do with
 * how many of them there are: a sanitised markup string is up to 2 MiB, and the
 * raster of one card is up to [NOVA_SVG_RASTER_MAX_PIXELS] of ARGB_8888. The
 * count-bounded map this replaced (`LinkedHashMap` capped at twelve entries) could
 * retain 24 MiB of markup - and a `String` is UTF-16, so 48 MiB of heap - for a
 * bio the reader had already scrolled past, and every one of those bytes had to be
 * collected before the scroll got its frames. Counting bytes is what makes the
 * ceiling mean what it says.
 *
 * The newest entry is never evicted, even when it alone is over budget: throwing
 * away the thing that was just paid for would make the card that paid re-render on
 * every pass. It is evicted by the next [put] instead.
 */
internal class NovaByteCache<V>(
    private val maxBytes: Int,
    private val weight: (V) -> Int
) {
    private val entries = LinkedHashMap<String, V>(16, 0.75f, true)
    private var used = 0

    val size: Int get() = entries.size

    /** Bytes currently held. Exposed so a test can assert the budget, not the count. */
    val usedBytes: Int get() = used

    /** Reading counts as a use: the entry moves to the tail of the eviction order. */
    fun get(key: String): V? = entries[key]

    fun put(key: String, value: V) {
        entries.remove(key)?.let { used -= weight(it) }
        entries[key] = value
        used += weight(value)

        val iterator = entries.entries.iterator()
        while (used > maxBytes && entries.size > 1) {
            val eldest = iterator.next()
            iterator.remove()
            used -= weight(eldest.value)
        }
    }

    fun clear() {
        entries.clear()
        used = 0
    }
}
