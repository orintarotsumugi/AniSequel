package eu.kanade.tachiyomi.animesource.model

/** Clean-room Aniyomi update-strategy ABI. */
@Suppress("unused")
enum class AnimeUpdateStrategy {
    ALWAYS_UPDATE,
    ONLY_FETCH_ONCE
}