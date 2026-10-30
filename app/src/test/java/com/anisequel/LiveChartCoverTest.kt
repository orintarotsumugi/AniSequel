package com.anisequel

import com.anisequel.data.network.LiveChartPosterCandidate
import com.anisequel.data.network.liveChartAnimePathFromPoster
import com.anisequel.data.network.normalizeLiveChartImageUrl
import com.anisequel.data.network.normalizeLiveChartTitle
import com.anisequel.data.network.parseLiveChartPosterCandidates
import com.anisequel.data.network.parseLiveChartVisualUrls
import com.anisequel.data.network.selectLiveChartPoster
import com.anisequel.data.network.visualsPageUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The LiveChart cover source: reading a search page's posters and choosing the
 * best title match.
 *
 * This is the whole scraping decision the owner made (see [com.anisequel.ui.components.LiveChart]
 * and NOTES.md) reduced to two pure functions, pinned here without a network. What
 * bites is not the fetch - it is the HTML shape LiveChart serves, the small->large
 * poster swap, and the fact that a search for one title returns a dozen near-miss
 * results (sequels, spin-offs, unrelated shows sharing a word). LiveChart spells
 * its titles in romaji and treats English as an alias, so the matcher has to
 * tolerate punctuation the two sites write differently.
 *
 * The fixtures are the real markup, reduced: each result's poster is an `<img>`
 * tagged `data-anime-item-target="poster"` whose `alt` is the title and whose
 * `src` is the `small.jpg` thumbnail, with the `large.jpg` variant only in the
 * `srcset`.
 */
class LiveChartCoverTest {

    private val frierenThumb =
        "https://u.livechart.me/anime/11376/poster_image/c7df8b9d73b2b0884572cb3ae2059def.webp"
    private val frierenSeason2Thumb =
        "https://u.livechart.me/anime/12860/poster_image/b367ce432235c26ef2d01371e15bcff8.webp"

    private fun posterItem(alt: String, base: String, ext: String = "webp") =
        "<div class=\"anime-item__poster-wrap\"><img alt=\"$alt\" width=\"70\" height=\"100\" " +
            "data-anime-item-target=\"poster\" " +
            "srcset=\"$base/$ext/small.jpg 1x, $base/$ext/large.jpg 2x\" " +
            "loading=\"lazy\" src=\"$base/$ext/small.jpg\" /></div>"

    private val searchPage = listOf(
        posterItem("Sousou no Frieren", frierenThumb),
        posterItem("Sousou no Frieren 2nd Season", frierenSeason2Thumb),
        // A result whose poster is already a non-`small.jpg` shape, to pin the
        // "leave an unexpected URL untouched rather than break it" branch.
        "<img alt=\"Solo Leveling\" src=\"https://u.livechart.me/anime/12/poster_image/x.webp/original.jpg\" />",
        // Noise that must never be mistaken for a cover.
        "<img alt=\"LiveChart.me\" src=\"https://s.livechart.me/assets/logo.png\" />",
        "<img alt=\"\" src=\"https://u.livechart.me/anime/9999/poster_image/deadbeef.webp/small.jpg\" />"
    ).joinToString("\n")

    @Test
    fun `only the poster thumbnails are read, and their alt is the title`() {
        val candidates = parseLiveChartPosterCandidates(searchPage)

        assertEquals(
            listOf("Sousou no Frieren", "Sousou no Frieren 2nd Season", "Solo Leveling"),
            candidates.map { it.title }
        )
        // The src the page loads (the thumbnail), not the srcset's large variant -
        // the swap to large happens later, only on the one chosen result.
        assertEquals(
            "$frierenThumb/webp/small.jpg",
            candidates.first().imageUrl
        )
    }

    @Test
    fun `an exact title match wins and comes back as the large poster`() {
        val url = selectLiveChartPoster(
            parseLiveChartPosterCandidates(searchPage),
            queryTitles = listOf("Sousou no Frieren")
        )

        assertEquals("$frierenThumb/webp/large.jpg", url)
    }

    @Test
    fun `the base title is preferred over its own sequel season`() {
        // LiveChart ranks the base first, but a substring query would match both;
        // the shortest of the agreeing titles is the one the reader means.
        val url = selectLiveChartPoster(
            parseLiveChartPosterCandidates(searchPage),
            queryTitles = listOf("Frieren")
        )

        assertEquals("$frierenThumb/webp/large.jpg", url)
    }

    @Test
    fun `an exact season match beats a prefix match on the base`() {
        val url = selectLiveChartPoster(
            parseLiveChartPosterCandidates(searchPage),
            queryTitles = listOf("Sousou no Frieren 2nd Season")
        )

        assertEquals("$frierenSeason2Thumb/webp/large.jpg", url)
    }

    @Test
    fun `a title the page does not carry yields no poster`() {
        val url = selectLiveChartPoster(
            parseLiveChartPosterCandidates(searchPage),
            queryTitles = listOf("Naruto")
        )

        assertNull(url)
    }

    @Test
    fun `a non-small poster URL is returned unchanged rather than rewritten`() {
        val url = selectLiveChartPoster(
            parseLiveChartPosterCandidates(searchPage),
            queryTitles = listOf("Solo Leveling")
        )

        assertEquals("https://u.livechart.me/anime/12/poster_image/x.webp/original.jpg", url)
    }

    @Test
    fun `english and romaji spellings are matched on the folded title`() {
        // AniList hands over an English title; LiveChart writes romaji and treats
        // English as an alias. When the page does carry the English form - as some
        // entries do - the two must match despite punctuation the sites differ on.
        val page = posterItem(
            "Frieren: Beyond Journey's End",
            "https://u.livechart.me/anime/50000/poster_image/abc"
        )
        val url = selectLiveChartPoster(
            parseLiveChartPosterCandidates(page),
            queryTitles = listOf("Sousou no Frieren", "Frieren: Beyond Journey’s End")
        )

        assertEquals(
            "https://u.livechart.me/anime/50000/poster_image/abc/webp/large.jpg",
            url
        )
    }

    @Test
    fun `an empty page and empty query both yield nothing`() {
        assertNull(selectLiveChartPoster(emptyList(), listOf("Frieren")))
        assertNull(selectLiveChartPoster(listOf(LiveChartPosterCandidate("X", "u/small.jpg")), emptyList()))
        assertNull(selectLiveChartPoster(listOf(LiveChartPosterCandidate("X", "u/small.jpg")), listOf("  ")))
        assertEquals(emptyList<LiveChartPosterCandidate>(), parseLiveChartPosterCandidates(""))
    }

    /**
     * A result whose image has not been hydrated yet.
     *
     * This is the shape a lazily-loaded search page serves: the real URL lives in
     * a data attribute and `src` is a transparent placeholder. Reading only `src`
     * - which the parser did - produced no candidates at all, so the viewer showed
     * no LiveChart pill for a page that did carry the poster.
     */
    @Test
    fun `a lazily loaded result is read from its data attribute`() {
        val page = "<img alt=\"Frieren\" data-src=\"$frierenThumb/webp/small.jpg\" " +
            "src=\"data:image/gif;base64,R0lGODlhAQABAAAAACw=\" />"

        val candidates = parseLiveChartPosterCandidates(page)

        assertEquals(1, candidates.size)
        assertEquals("$frierenThumb/webp/small.jpg", candidates.single().imageUrl)
        assertEquals(
            "$frierenThumb/webp/large.jpg",
            selectLiveChartPoster(candidates, listOf("Sousou no Frieren"))
        )
    }

    /** A page that offers only a `srcset` still names its smallest variant first. */
    @Test
    fun `a srcset only result is read from its smallest variant`() {
        val page = "<img alt=\"Frieren\" " +
            "srcset=\"//u.livechart.me/anime/11376/poster_image/c7df.webp/small.webp 1x, " +
            "//u.livechart.me/anime/11376/poster_image/c7df.webp/large.webp 2x\" />"

        val candidates = parseLiveChartPosterCandidates(page)

        assertEquals(
            "https://u.livechart.me/anime/11376/poster_image/c7df.webp/small.webp",
            candidates.single().imageUrl
        )
    }

    /** The size segment is the only thing rewritten, and the file format is kept. */
    @Test
    fun `a webp thumbnail is swapped for the large webp`() {
        val page = "<img alt=\"Solo Leveling\" " +
            "src=\"https://u.livechart.me/anime/12/poster_image/y.webp/small.webp\" />"

        assertEquals(
            "https://u.livechart.me/anime/12/poster_image/y.webp/large.webp",
            selectLiveChartPoster(parseLiveChartPosterCandidates(page), listOf("Solo Leveling"))
        )
    }

    @Test
    fun `normalisation folds case, punctuation and runs of spaces`() {
        assertEquals("attack on titan", normalizeLiveChartTitle("Attack on Titan"))
        assertEquals("shingeki no kyojin", normalizeLiveChartTitle("  SHINGEKI   NO: KYOJIN! "))
        assertEquals("one piece", normalizeLiveChartTitle("One-Piece"))
    }

    /**
     * The gallery is derived from the poster the matcher chose, not from a second
     * title search.
     *
     * AnymeX takes the first result's anchor and appends `/visuals`; doing that
     * here would mean matching the same title twice against two different pieces
     * of markup, and a near-miss could open the wrong show's gallery under the
     * right show's poster. The poster URL already carries the id of the result the
     * matcher accepted.
     */
    @Test
    fun `the anime path is read from the chosen poster URL`() {
        assertEquals(
            "/anime/11376",
            liveChartAnimePathFromPoster("$frierenThumb/webp/large.jpg")
        )
        assertEquals(
            "/anime/11376",
            liveChartAnimePathFromPoster("https://u.livechart.me/anime/11376")
        )
        assertNull(liveChartAnimePathFromPoster("https://u.livechart.me/anime/ten"))
        assertNull(liveChartAnimePathFromPoster("https://u.livechart.me/poster_image/x"))
    }

    @Test
    fun `the visuals page is one path away`() {
        assertEquals(
            "https://www.livechart.me/anime/11376/visuals",
            visualsPageUrl("/anime/11376")
        )
        assertEquals(
            "https://www.livechart.me/anime/11376/visuals",
            visualsPageUrl("/anime/11376/")
        )
    }

    /**
     * The visuals tab marks each lightbox image in a data attribute.
     *
     * Reading `src` would take the thumbnail - often a different crop - and
     * reading a whole `<img>` would miss the cards that render their real URL only
     * in the attribute. The `data:` placeholder and a relative advertisement must
     * not become gallery pages.
     */
    @Test
    fun `every visual image is read, normalised and de-duplicated`() {
        val page = """
            <div data-anime-visual-url-value="https://u.livechart.me/anime_visuals/local_version/1/image/a.webp/small.jpg"></div>
            <div data-anime-visual-url-value='//u.livechart.me/anime_visuals/local_version/1/image/b.webp/small.jpg'></div>
            <div data-anime-visual-url-value="/uploads/ad/banner.jpg"></div>
            <div data-anime-visual-url-value="data:image/gif;base64,R0lGODlhAQABAAAAACw="></div>
            <div data-anime-visual-url-value="https://u.livechart.me/anime_visuals/local_version/1/image/a.webp/small.jpg"></div>
            <div data-anime-visual-url-value="https://example.com/anime_visuals/image/c.jpg"></div>
        """.trimIndent()

        assertEquals(
            listOf(
                "https://u.livechart.me/anime_visuals/local_version/1/image/a.webp/small.jpg",
                "https://u.livechart.me/anime_visuals/local_version/1/image/b.webp/small.jpg",
                "https://www.livechart.me/uploads/ad/banner.jpg"
            ),
            parseLiveChartVisualUrls(page)
        )
    }

    @Test
    fun `an image URL keeps its own size and format, and upgrades only the scheme`() {
        assertEquals(
            "https://u.livechart.me/anime_visuals/x/image/a.webp/small.webp",
            normalizeLiveChartImageUrl("//u.livechart.me/anime_visuals/x/image/a.webp/small.webp")
        )
        assertEquals(
            "https://u.livechart.me/anime_visuals/x/image/a.webp/large.jpg",
            normalizeLiveChartImageUrl("http://u.livechart.me/anime_visuals/x/image/a.webp/large.jpg")
        )
        assertNull(normalizeLiveChartImageUrl("data:image/png;base64,AAAA"))
        assertNull(normalizeLiveChartImageUrl("not a url"))
    }
}
