package eu.kanade.tachiyomi.animesource.model

/** Clean-room [SAnime] holder; see [SAnime] for why the shape is fixed. */
data class SAnimeImpl(
    override var url: String = "",
    override var title: String = "",
    override var artist: String? = null,
    override var author: String? = null,
    override var description: String? = null,
    override var genre: String? = null,
    override var status: Int = SAnime.UNKNOWN,
    override var thumbnail_url: String? = null,
    override var update_strategy: AnimeUpdateStrategy = AnimeUpdateStrategy.ALWAYS_UPDATE,
    override var initialized: Boolean = false
) : SAnime