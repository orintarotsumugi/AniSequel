package com.anisequel.ui.components.detail

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.posterUrl
import com.anisequel.data.network.BetterPosterVariant
import com.anisequel.data.network.LiveChartArtwork
import com.anisequel.domain.ui.PosterSource

/**
 * The viewer's sources, in paint order, for one title.
 *
 * AniList's poster and banner paint before any request this function makes, so a
 * title with nothing else still opens instantly. btttr's renders arrive late and
 * are four pages rather than one; LiveChart's gallery arrives later still and can
 * be nothing at all. The ordering only decides which pill sits first and which
 * source the viewer opens on - the selection is by label, so a late insertion
 * cannot change what is on screen.
 *
 * [betterPostersLead] is the reader's card source. It is passed in rather than read
 * from the composition local so this stays pure and the ordering is one readable
 * list instead of a branch in the dialog's body.
 */
internal fun buildCoverSources(
    media: MediaNode,
    betterPosters: List<BetterPosterVariant>,
    liveChartArtwork: LiveChartArtwork?,
    betterPostersLead: Boolean
): List<CoverSource> {
    val anilist = buildList {
        media.posterUrl?.takeIf { it.isNotBlank() }?.let {
            add(CoverSource(ANILIST_POSTER, listOf(CoverImage(it)), DEFAULT_COVER_ASPECT))
        }
        // AniList banners are about 1900x400. The fallback is only ever read
        // for the frame before the image reports its real size.
        media.bannerImage?.takeIf { it.isNotBlank() }?.let {
            add(CoverSource(ANILIST_BANNER, listOf(CoverImage(it)), DEFAULT_BANNER_ASPECT))
        }
    }
    val better = betterPosters.takeIf { it.isNotEmpty() }?.let { variants ->
        listOf(
            CoverSource(
                label = PosterSource.BETTER_POSTERS.displayName,
                images = variants.map { CoverImage(url = it.url, variant = it.overlays.variantName()) },
                fallbackAspect = DEFAULT_COVER_ASPECT
            )
        )
    }.orEmpty()
    val liveChart = liveChartArtwork?.let { artwork ->
        val urls = artwork.visualUrls.ifEmpty { listOfNotNull(artwork.posterUrl) }
        urls.takeIf { it.isNotEmpty() }?.let {
            listOf(
                CoverSource(
                    label = LIVE_CHART,
                    images = urls.map { url -> CoverImage(url) },
                    fallbackAspect = DEFAULT_COVER_ASPECT
                )
            )
        }
    }.orEmpty()

    return if (betterPostersLead) {
        better + anilist + liveChart
    } else {
        anilist + better + liveChart
    }
}

/** One image of one source, with the overlay it was rendered with when it has one. */
internal data class CoverImage(
    val url: String,
    /** "Score only", "Clean poster" - btttr's renders; null for AniList and LiveChart. */
    val variant: String? = null
)

/**
 * One source the viewer can show, with the shape to assume before it loads.
 *
 * [images] is a gallery rather than a single image because every one of the three
 * providers answers with more than one: btttr renders four overlays of the same art,
 * LiveChart's `/visuals` tab is a gallery, and AniList contributes a poster and a
 * banner. A source with one image simply has no second page to offer.
 *
 * `internal` because the details card's source switch takes the same list the
 * viewer shows; the type travels with the image, not with the composable that
 * happens to declare it.
 */
internal data class CoverSource(
    val label: String,
    val images: List<CoverImage>,
    val fallbackAspect: Float
)

/**
 * The labels a source is selected by.
 *
 * Strings rather than an enum because one of them is btttr's own name from
 * [PosterSource.BETTER_POSTERS], and the pill the reader taps and the key the pager
 * resolves must be the same value. They are also what the save button writes into
 * a file name, so they read as words.
 */
internal const val ANILIST_POSTER = "Poster"
internal const val ANILIST_BANNER = "Banner"
private const val LIVE_CHART = "LiveChart"

/** AniList's banner shape (about 1900x400), used until the image reports its own. */
private const val DEFAULT_BANNER_ASPECT = 1900f / 400f
