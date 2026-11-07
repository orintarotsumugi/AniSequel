package com.anisequel.data.network

/**
 * The Klipy key AniSequel ships with.
 *
 * Klipy is no longer the default - that seat belongs to Gifukai, which is keyless
 * by construction (see [GifProvider.DEFAULT]) - but it is the general catalogue
 * the default sends readers to when they type a sentence, and it still answers on
 * this key without asking the reader to register (see the factory in
 * [gifSearchApiFor]). Carrying one key rather than asking every reader to register
 * at partner.klipy.com is what makes that hand-off seamless; [GifProvider.KLIPY]'s
 * own `needsKey` is false for exactly this reason.
 *
 * ## The tradeoff a reader should know about
 *
 * A key inside an APK is not a secret. Every install carries the same one, anyone
 * who decompiles the app has it, and Klipy's rate limit is therefore shared across
 * everybody at once - which is also why the picker still answers when the quota is
 * spent by telling the reader it is (see [KlipyGifs.search]'s 429 message) rather
 * than pretending to be an empty catalogue. Nothing can be charged to the key and
 * no reader data travels with a request. A reader with a key of their own pastes it
 * in Settings; that key is stored encrypted and sent only to api.klipy.com, and it
 * replaces this one for as long as it is saved - see [gifSearchApiFor].
 */
internal const val BUNDLED_KLIPY_KEY =
    "22cpRnjQxST9dd7dlvFQQuNufwjiN14GZ7KXzFHy6e1vqLBlrKoa2W0S0HEcP9WY"
