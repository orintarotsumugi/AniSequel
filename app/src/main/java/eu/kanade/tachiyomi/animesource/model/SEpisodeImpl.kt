package eu.kanade.tachiyomi.animesource.model

/** Clean-room [SEpisode] holder; see [SAnime] for why the shape is fixed. */
data class SEpisodeImpl(
    override var url: String = "",
    override var name: String = "",
    override var date_upload: Long = 0L,
    override var episode_number: Float = 0f,
    override var scanlator: String? = null
) : SEpisode