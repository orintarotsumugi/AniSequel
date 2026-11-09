package eu.kanade.tachiyomi.animesource.model

/** Clean-room Aniyomi lib-14 filter-list ABI. */
data class AnimeFilterList(val list: List<AnimeFilter<*>>) : List<AnimeFilter<*>> by list {
    constructor(vararg filters: AnimeFilter<*>) : this(filters.asList())
}