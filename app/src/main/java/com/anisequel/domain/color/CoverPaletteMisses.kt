package com.anisequel.domain.color

/**
 * A bounded memory of covers that decoded but yielded no palette.
 *
 * The palette cache next door only ever held successes, so the *expensive*
 * covers were the ones re-read: a grey cover, or one whose palette is all
 * near-grey, was decoded by Coil, copied to a software bitmap and quantized
 * again on every open - and the detail page asks for the card URL and the poster
 * URL, so a single open could pay for the same artwork twice. Every answer was
 * the same "no".
 *
 * Only *definitive* misses belong here: an image that decoded and had nothing to
 * give. A request that failed, timed out or was cancelled is not remembered,
 * because the next open may have a network the first one did not, and a session-
 * long "this title has no colour" would outlive the reason.
 *
 * Pure Kotlin so the eviction rule can be tested on the JVM; the repository owns
 * the instance and the threading.
 */
class CoverPaletteMisses(private val capacity: Int = 96) {

    private val urls = object : LinkedHashMap<String, Unit>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Unit>?): Boolean =
            size > capacity
    }

    init {
        require(capacity > 0) { "a miss cache with no room never remembers anything" }
    }

    // `get`, not `containsKey`: access ordering is refreshed by `get` and the
    // `put` family, and *not* by `containsKey`. Asking with `containsKey` left the
    // recency order frozen at insertion, so the eviction took a cover the reader
    // had opened most recently whenever an older one had been written after it.
    fun contains(url: String): Boolean = synchronized(urls) { urls[url] != null }

    fun remember(url: String) {
        synchronized(urls) { urls[url] = Unit }
    }

    fun clear() {
        synchronized(urls) { urls.clear() }
    }
}
