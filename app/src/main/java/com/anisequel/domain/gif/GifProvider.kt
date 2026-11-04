package com.anisequel.domain.gif

/**
 * The GIF index a picker searches.
 *
 * ## Why more than one
 *
 * The anonymous default is Safebooru, and it is a *tag* index: it answers
 * `holo crying` and nothing that a reader typed as a sentence. Tenor used to cover
 * the other half - reaction GIFs from a general catalogue - and its public API was
 * retired in 2026, so an install that only offered Tenor now offers a search that
 * cannot answer. Klipy and GIPHY are the two replacements a reader can bring a key
 * for, and nekos.best is a second anonymous index for the reaction shapes anime
 * comments use most. Konachan is the third: its safe board is tagged by character, so
 * it reaches the names that fall between nekos.best's category words and Safebooru's
 * uneven tag coverage - and its catalogue is thinner than either, which is why it is
 * offered rather than defaulted.
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
    KONACHAN(
        storageValue = "konachan",
        displayName = "Konachan",
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
        needsKey = true,
        keyLabel = "Klipy API key",
        keyHint = "Free test key from partner.klipy.com. It is sent only to api.klipy.com."
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
        SAFEBOORU, NEKOS_BEST, KONACHAN -> null
        TENOR -> tenorKey
        KLIPY -> klipyKey
        GIPHY -> giphyKey
    }

    companion object {
        /** Safebooru is the default: it needs no account and answers anime tags. */
        val DEFAULT = SAFEBOORU

        /** The providers offered on the Settings page, keyless ones first. */
        val selectable: List<GifProvider> = entries.sortedBy { it.needsKey }

        fun fromStorage(value: String?): GifProvider? =
            entries.firstOrNull { it.storageValue == value }
    }
}
