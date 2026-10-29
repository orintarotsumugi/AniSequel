package com.anisequel.ui.components

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * Where this title can be found on LiveChart.me, as a search rather than a page.
 *
 * ## Why the app links out instead of reading LiveChart itself
 *
 * LiveChart publishes no API - its FAQ says so outright and points at RSS feeds
 * as the only machine-readable surface - so there is nothing here to call that
 * would not be scraping somebody else's site behind their back. The alternative
 * integrations people write against LiveChart are reverse-engineered endpoints
 * that can change, throttle or block without notice, and a title page that
 * quietly loses its schedule is worse than one that hands the reader the site.
 *
 * The URL is the search LiveChart advertises in its own pages' schema.org
 * `SearchAction` (`https://www.livechart.me/search?q={search_term}`), so it is a
 * shape the site itself promises rather than one guessed from its address bar.
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
