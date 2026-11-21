package com.anisequel.ui.components.novamarkdown

/**
 * The public entry point of the NovaMarkdown system.
 *
 * ## The document, not the view
 *
 * A caller parses once and keeps the AST. The profile About card, a comment row
 * and a title's synopsis all render documents that can be re-composed on every
 * scroll frame, and reparsing Markdown between frames is the kind of cost that
 * only shows up on a long bio. [parse] therefore keeps a small LRU of the last
 * documents; the AST is immutable and safe to share.
 *
 * The parser reads *raw* AniList Markdown. AniList's GraphQL `User.about`
 * field is requested with `asHtml: false` (see `GraphQLQueries.GET_USER_OVERVIEW`),
 * so the string that arrives here is exactly what the person typed, not the
 * site's pre-rendered HTML. That distinction matters: `asHtml: true` loses the
 * source of a centered block, a spoiler and an AniList link card, which are the
 * three things this renderer exists to draw.
 */
object NovaMarkdown {

    private const val CACHE_ENTRIES = 48

    /**
     * A tiny access-ordered LRU.
     *
     * Deliberately not `android.util.LruCache`: the parser tests are plain JVM
     * tests, and a cache that is only available under Robolectric would push
     * every parser test onto an Android runner for no reason.
     */
    private class NovaParseCache(private val max: Int) :
        LinkedHashMap<String, List<NovaBlock>>(max, 0.75f, true) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, List<NovaBlock>>?
        ): Boolean = size > max
    }

    private val cache = NovaParseCache(CACHE_ENTRIES)

    /** Parses a raw AniList Markdown document. */
    @Synchronized
    fun parse(source: String): List<NovaBlock> {
        if (source.isBlank()) return emptyList()
        cache[source]?.let { return it }
        val parsed = parseNovaMarkdown(source)
        cache[source] = parsed
        return parsed
    }

    /** Parses one inline expression, for a preview or a test. */
    fun parseInlines(source: String): List<NovaInline> = parseNovaInlineLine(source)
}
