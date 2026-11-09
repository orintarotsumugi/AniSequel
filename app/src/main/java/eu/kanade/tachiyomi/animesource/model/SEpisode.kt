package eu.kanade.tachiyomi.animesource.model

/** Clean-room Aniyomi lib-14 episode ABI; see [SAnime] for the contract. */
@Suppress("PropertyName", "unused")
interface SEpisode {
    var url: String
    var name: String
    var date_upload: Long
    var episode_number: Float
    var scanlator: String?

    companion object {
        fun create(): SEpisode = SEpisodeImpl()
    }
}