package com.anisequel.domain.gif

/**
 * The GIF index a picker searches.
 *
 * ## Why more than one
 *
 * The default is Klipy: it is a *general* catalogue - reaction GIFs, spoken-word
 * phrases, the shapes an anime comment actually makes - and the app carries a key
 * for it, so it answers out of the box (see
 * `data/network/BundledGifKeys.kt` for why a shared key ships and what it costs).
 * Before 2026 the unkeyed default would have been Tenor, whose public API is gone.
 * Safebooru is the fallback tag index: it answers `holo crying` and nothing that a
 * reader typed as a sentence. GIPHY is the second general catalogue, and it is
 * keyed-only: a reader brings a beta key for it, because a general catalogue that
 * leaves the key to the reader is exactly what made the substitute Tenor became.
 * nekos.best is a second keyless index for the reaction categories anime comments
 * use most.
 *
 * ## Why the key is per-provider
 *
 * A key is a credential for one service. Reusing the Tenor field for Klipy would
 * send a Klipy key to Google - and a reader who switched back would have to find the
 * old key again. Each key is stored under its own encrypted entry; see
 * [com.anisequel.data.repository.MediaPreferences].
 *
 * Nothing here performs a request. The enum is the choice; the clients are in
 * `data/network/GifProviders.kt`.
 */
enum class GifProvider(
    val storageValue: String,
    val displayName: String,
    /** Whether this provider is useless without a key the reader supplies. */
    val needsKey: Boolean,
    /** The label for its key field, in the reader's words. Empty for a keyless index. */
    val keyLabel: String,
    /** Where the key comes from. Empty for a keyless index. */
    val keyHint: String
) {
    SAFEBOORU(
        storageValue = "safebooru",
        displayName = "Safebooru",
        needsKey = false,
        keyLabel = "",
        keyHint = ""
    ),
    NEKOS_BEST(
        storageValue = "nekos_best",
        displayName = "nekos.best",
        needsKey = false,
        keyLabel = "",
        keyHint = ""
    ),
    TENOR(
        storageValue = "tenor",
        displayName = "Tenor",
        needsKey = true,
        keyLabel = "Tenor API key",
        // Tenor's public API was retired in 2026. The field stays because an
        // install that already saved a key should not lose it, and because the
        // failure message is the place that tells the reader what happened.
        keyHint = "Tenor's public API was retired in 2026. A key that still works can be pasted here; otherwise try Klipy or GIPHY."
    ),
    KLIPY(
        storageValue = "klipy",
        displayName = "Klipy",
        // The app carries a working key (data/network/BundledGifKeys.kt), so a
        // search answers whether or not the reader pasted one. `keyFrom` still
        // returns a reader-supplied key when there is one, and that key wins -
        // needsKey means "useless without a key the reader supplies", and Klipy
        // no longer is.
        needsKey = false,
        keyLabel = "Klipy API key",
        keyHint = "The app already carries a working key, so searches answer " +
            "without this. A key of your own from partner.klipy.com replaces it " +
            "and is sent only to api.klipy.com."
    ),
    GIPHY(
        storageValue = "giphy",
        displayName = "GIPHY",
        needsKey = true,
        keyLabel = "GIPHY API key",
        keyHint = "Beta key from developers.giphy.com, 100 calls an hour. It is sent only to api.giphy.com."
    );

    /** The key this provider needs, or null for a keyless index. */
    fun keyFrom(
        tenorKey: String?,
        klipyKey: String?,
        giphyKey: String?
    ): String? = when (this) {
        SAFEBOORU, NEKOS_BEST -> null
        TENOR -> tenorKey
        KLIPY -> klipyKey
        GIPHY -> giphyKey
    }

    companion object {
        /**
         * Klipy is the default: it is a general catalogue and it answers on the
         * app's own key, so no reader has to register anywhere before their first
         * search. Safebooru used to be the default and remains the tag index and
         * the last resort of the substitution ladder in
         * [com.anisequel.data.network.gifSearchApiFor] - a general catalogue that
         * leaves its reader to find a key was what the retired Tenor became.
         */
        val DEFAULT = KLIPY

        /** The providers offered on the Settings page, keyless ones first. */
        val selectable: List<GifProvider> = entries.sortedBy { it.needsKey }

        fun fromStorage(value: String?): GifProvider? =
            entries.firstOrNull { it.storageValue == value }
    }
}
