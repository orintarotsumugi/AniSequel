package com.anisequel.ui.theme

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Color
import com.anisequel.domain.color.ArgbHex

/**
 * The cover colours extracted this session, keyed by poster URL.
 *
 * ## Why this is a store and not a field on the model
 *
 * The seed is read from the image, and the image is read by the detail sheet. The
 * hero that wants the colour is a descendant of that sheet, several composables
 * down a page whose parameters are the AniList model. Threading a new field
 * through every card on the way would have made the extraction the model's
 * problem - and the colour is a view concern that should disappear when the
 * sheet is gone.
 *
 * A snapshot map gives the hero the one thing it needs: when the sheet finishes
 * reading the pixels, the composable that read the entry is invalidated and the
 * backdrop recolours, without the sheet re-composing its whole page.
 *
 * ## Why it is capped
 *
 * A reader can open hundreds of titles in a session. The seed is small, but an
 * unbounded map keyed by URL is still a leak with extra steps. Old entries are
 * dropped in insertion order; the open sheet's entry is always the newest.
 */
object CoverColors {

    private const val MAX_ENTRIES = 96

    private val colors = mutableStateMapOf<String, String>()
    private val accents = mutableStateMapOf<String, String>()
    private val insertionOrder = ArrayDeque<String>()

    /** Stores [argb] for [url]. Safe to call on every recomposition. */
    fun put(url: String?, argb: Int) {
        if (url.isNullOrBlank()) return
        val hex = ArgbHex.format(argb)
        if (colors[url] == hex) return

        colors[url] = hex
        insertionOrder.remove(url)
        insertionOrder.addLast(url)
        while (insertionOrder.size > MAX_ENTRIES) {
            val dropped = insertionOrder.removeFirst()
            colors.remove(dropped)
            accents.remove(dropped)
        }
    }

    /**
     * Stores a second, distinct hue from the same artwork.
     *
     * The seed answers "what colour is this title"; the accent answers "what else
     * is in the picture". A gradient built from both reads as artwork rather than
     * as one colour washed over the screen, and the hue is the quantizer's, not a
     * rotation of the seed's.
     */
    fun putAccent(url: String?, argb: Int) {
        if (url.isNullOrBlank()) return
        // An accent belongs to a seed. Storing one for an unknown URL used to add
        // that URL to the shared eviction order, so a handful of accent-only
        // entries could evict real seeds from a full cache. It also left accents
        // with no bound of their own once their seed was gone.
        if (colors[url] == null) return
        val hex = ArgbHex.format(argb)
        if (accents[url] == hex) return
        // No insertion-order change: the seed's entry already owns this URL's
        // place in the cache, and eviction removes both halves together.
        accents[url] = hex
    }

    /** The extracted hex for [url], or null if it has not been read yet. */
    fun hex(url: String?): String? = url?.let(colors::get)

    /** The extracted colour for [url], or null. Reads snapshot state. */
    fun color(url: String?): Color? = hex(url)?.let { ArgbHex.parse(it) }?.let { Color(it) }

    /** The artwork's second hue for [url], or null. Reads snapshot state. */
    fun accent(url: String?): Color? =
        url?.let(accents::get)?.let { ArgbHex.parse(it) }?.let { Color(it) }

    /** For tests and for a full reset; the app has no reason to call this. */
    fun clear() {
        colors.clear()
        accents.clear()
        insertionOrder.clear()
    }
}
