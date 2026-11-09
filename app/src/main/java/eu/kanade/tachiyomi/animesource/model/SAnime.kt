package eu.kanade.tachiyomi.animesource.model

/**
 * Clean-room declaration of the Aniyomi extensions-lib 14 anime ABI.
 *
 * Extension APKs are compiled against the published *stub* library and expect
 * the host to provide real classes under these exact names and signatures.
 * This is that contract, written fresh for AniSequel's sandboxed runtime; no
 * upstream implementation is copied. Only the lib-14 surface is declared,
 * because that is what the extension repositories build against.
 */
@Suppress("PropertyName", "unused")
interface SAnime {
    var url: String
    var title: String
    var artist: String?
    var author: String?
    var description: String?
    var genre: String?
    var status: Int
    var thumbnail_url: String?
    var update_strategy: AnimeUpdateStrategy
    var initialized: Boolean

    companion object {
        const val UNKNOWN = 0
        const val ONGOING = 1
        const val COMPLETED = 2
        const val LICENSED = 3
        const val PUBLISHING_FINISHED = 4
        const val CANCELLED = 5
        const val ON_HIATUS = 6

        fun create(): SAnime = SAnimeImpl()
    }
}