package com.anisequel.ui.components

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Where this title can be found on LiveChart.me, as a search rather than a page.
 *
 * ## The app both links out here and reads one poster from it
 *
 * LiveChart publishes no API - its FAQ says so outright and points at RSS feeds
 * as the only machine-readable surface. For a long time the rule was that this
 * meant the app could only *link* to LiveChart and never read it, on the
 * reasoning that scraping a site that has asked not to be consumed that way is
 * someone else's call to make.
 *
 * **The owner has since reversed that for one narrow thing: the cover viewer now
 * shows LiveChart's own poster**, which is more numerous and higher quality than
 * AniList's for many titles. See [com.anisequel.data.network.LiveChartPosterApi]
 * and the NOTES.md section of the same name for what is actually fetched and why.
 * The reversal is deliberately bounded: nothing but the best title match's poster
 * is read, the reverse-engineered endpoints the old comment warned about are still
 * untouched, and this file still does exactly what it always did - build the
 * search URL the outbound "LiveChart" pill uses.
 *
 * The URL is the search LiveChart advertises in its own pages' schema.org
 * `SearchAction` (`https://www.livechart.me/search?q={search_term}`), so it is a
 * shape the site itself promises rather than one guessed from its address bar. It
 * is also the page the cover fetch opens, which is why the same builder is the
 * honest single definition of "a LiveChart search for this title".
 *
 * ## Why the romaji title
 *
 * LiveChart titles its entries in romaji and treats the English form as an
 * alias, so "Attack on Titan" finds less than "Shingeki no Kyojin". [MediaNode.liveChartUrl]
 * therefore prefers `title.romaji` and only falls back to the display name.
 *
 * The link opens in the browser like every other external link in the app. It is
 * here because LiveChart is where a reader checks an airing schedule, a
 * countdown and which services stream a show - none of which AniList answers -
 * and a search for the title is the one hop this app can add without pretending
 * to an integration it does not have.
 *
 * The encoder emits `+` for spaces, which is what every query-string parser,
 * LiveChart's included, reads back as a space.
 */
fun liveChartSearchUrl(title: String): String =
    "https://www.livechart.me/search?q=" +
        URLEncoder.encode(title, StandardCharsets.UTF_8.name())

/**
 * [liveChartSearchUrl] for a media node, romaji first.
 *
 * Anime-only by construction: LiveChart tracks anime, so its search answers
 * nothing useful for a manga and no surface offers it for one. See
 * [MediaNode.isManga].
 */
val MediaNode.liveChartUrl: String
    get() = liveChartSearchUrl(
        title?.romaji?.takeIf { it.isNotBlank() } ?: displayName
    )
