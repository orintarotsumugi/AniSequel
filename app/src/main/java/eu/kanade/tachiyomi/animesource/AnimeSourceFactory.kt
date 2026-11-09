package eu.kanade.tachiyomi.animesource

/** Clean-room Aniyomi factory ABI: one APK can publish several sources. */
interface AnimeSourceFactory {
    fun createSources(): List<AnimeSource>
}