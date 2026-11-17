package com.anisequel.ui.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.posterUrl
import com.anisequel.data.network.BetterPosterVariant
import com.anisequel.data.network.BetterPostersApi
import com.anisequel.domain.ui.PosterOverlays
import com.anisequel.domain.ui.PosterSource
import com.anisequel.domain.ui.SequelSeason

/**
 * Where cards get their cover art, handed down from the app root.
 *
 * A composition local for the same reason [LocalCardDesign] is one: the picker lives
 * in Settings and the cards live on six surfaces - the Library grid and list, a
 * profile's list, Home's rails, search results and the schedule - and threading a
 * source through every layer between them is how one of those surfaces ends up
 * still drawing AniList art after the reader switched.
 */
val LocalPosterSource = staticCompositionLocalOf { PosterSource.DEFAULT }

/**
 * What btttr prints on its own poster, handed down beside [LocalPosterSource].
 *
 * A second local rather than a field on the source because the two answer different
 * questions - which picture, and what is written on it - and a reader can change
 * either without the other. Provided at the app root so no card surface can be the
 * one still asking for the full poster after the reader stripped it.
 */
val LocalPosterOverlays = staticCompositionLocalOf { PosterOverlays.DEFAULT }

/**
 * Whether a sequel season keeps its own cover, handed down beside the poster source.
 *
 * Its own local rather than a fourth field on [PosterOverlays], because it is not an
 * overlay btttr draws: it is the decision not to ask btttr at all for a season it
 * cannot tell apart from season one - see [SequelSeason].
 */
val LocalPosterUniqueSeasons = staticCompositionLocalOf { true }

/**
 * The btttr lookup every card shares.
 *
 * One instance for the whole app, because its answer cache is the point: a second
 * instance would mean a second set of ARM requests for the same list. Null in
 * previews and tests that compose cards without the app root, which [cardPosterUrl]
 * reads as "no BetterPosters poster" rather than as a reason to crash.
 */
val LocalBetterPosters = staticCompositionLocalOf<BetterPostersApi?> { null }

/**
 * Every name AniList sent for [media], joined for the season check.
 *
 * Not just the preferred title: a sequel's English title often drops the "Season 2"
 * that its romaji title still carries, and the check must not miss the season because
 * of which translation the reader happens to show.
 */
internal fun posterLookupTitle(media: MediaNode): String? = listOfNotNull(
    media.title?.english,
    media.title?.romaji,
    media.title?.displayTitle
).joinToString(" ").takeIf { it.isNotBlank() }

/**
 * The model a card hands to its image loader: btttr's rendered poster when the reader
 * chose it *and* one exists for this title, the cover AniList sent otherwise.
 *
 * ## Why the AniList cover is drawn while the lookup runs
 *
 * The btttr URL does not exist until a second service has answered, and a card
 * cannot wait for that without becoming a blank rectangle for as long as two third
 * parties take. The cover is already in the list response, so it paints immediately
 * and is replaced only when a better poster actually arrives - and stays when it does
 * not, which is the honest answer for a title btttr does not carry.
 *
 * The lookup is keyed on the media id, so scrolling a card out and back re-reads the
 * answer from `BetterPostersApi`'s cache instead of asking again.
 */
@Composable
fun cardPosterUrl(media: MediaNode): String? = cardPosterUrl(
    mediaId = media.id,
    fallback = media.posterUrl,
    title = posterLookupTitle(media)
)

/**
 * The same answer for a card that carries an id and a cover but not the node.
 *
 * The schedule is that card: an [com.anisequel.data.model.ArrivingEntry] is a
 * projection of a list entry - id, title, cover, dates - built for the page, so it has
 * no `MediaNode` to hand in. Looking the poster up by id keeps the schedule on the
 * reader's chosen source instead of being the one list still drawing AniList art.
 */
@Composable
fun cardPosterUrl(mediaId: Int, fallback: String?, title: String? = null): String? {
    if (LocalPosterSource.current != PosterSource.BETTER_POSTERS) return fallback
    // btttr addresses the IMDb *series*, so every season resolves to one picture.
    // A sequel keeps its own AniList cover instead of repeating season one's art;
    // the cover is the version that carries that season's name and key visual.
    // The title check is the fast path; the API applies the same rule from ARM's
    // season number for sequels whose name does not say "Season 2" - Nisemonogatari
    // is season 2 of the same IMDb series as Bakemonogatari.
    val uniqueSeasons = LocalPosterUniqueSeasons.current
    if (uniqueSeasons && SequelSeason.isSequel(title)) return fallback
    val api = LocalBetterPosters.current ?: return fallback
    // Part of the key, not just of the request: the same title at a different
    // overlay variant is a different URL, and reusing the previous answer would
    // keep drawing the score the reader had just removed.
    val overlays = LocalPosterOverlays.current

    var better by remember(mediaId, overlays, uniqueSeasons) { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaId, api, overlays, uniqueSeasons) {
        better = api.posterUrl(mediaId, overlays, skipSequels = uniqueSeasons)
    }
    return better ?: fallback
}

/**
 * Every poster btttr has for [media], the reader's chosen overlay first - empty when
 * there is nothing to add.
 *
 * ## Why the viewer does not use [cardPosterUrl]
 *
 * A card asks "what should I draw", and its answer for a title btttr does not carry
 * is the AniList cover, because a blank card would be worse. The viewer asks a
 * different question - "what else does btttr have for this title" - and handing it
 * the fallback was a real defect: with the reader on AniList, `cardPosterUrl` answers
 * with the AniList URL, so the full-screen viewer drew a **BetterPosters pill holding
 * the AniList picture** next to the actual Poster pill holding the same picture.
 * Two pills, one image, one of them mislabelled.
 *
 * It also deliberately ignores [LocalPosterSource]: that local says which provider a
 * *card* should draw, and the viewer is where every provider's art is on offer. A
 * reader who leaves their cards on AniList can still look at btttr's posters here,
 * the same way they can look at LiveChart's.
 *
 * The season rule is the cards' rule, unchanged: btttr addresses one IMDb id for a
 * whole series, so a later season keeps its own AniList cover rather than repeating
 * season one's art (see [SequelSeason] and `BetterPostersApi.posterVariants`).
 */
@Composable
fun betterPosterGallery(media: MediaNode): List<BetterPosterVariant> {
    val api = LocalBetterPosters.current ?: return emptyList()
    val overlays = LocalPosterOverlays.current
    val uniqueSeasons = LocalPosterUniqueSeasons.current
    if (uniqueSeasons && SequelSeason.isSequel(posterLookupTitle(media))) return emptyList()

    var variants by remember(media.id, overlays, uniqueSeasons) {
        mutableStateOf(emptyList<BetterPosterVariant>())
    }
    LaunchedEffect(media.id, api, overlays, uniqueSeasons) {
        variants = api.posterVariants(media.id, overlays, skipSequels = uniqueSeasons)
    }
    return variants
}
