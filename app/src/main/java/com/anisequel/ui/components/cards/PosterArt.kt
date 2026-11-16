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
import com.anisequel.data.network.BetterPostersApi
import com.anisequel.domain.ui.PosterSource

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
 * The btttr lookup every card shares.
 *
 * One instance for the whole app, because its answer cache is the point: a second
 * instance would mean a second set of ARM requests for the same list. Null in
 * previews and tests that compose cards without the app root, which [cardPosterUrl]
 * reads as "no BetterPosters poster" rather than as a reason to crash.
 */
val LocalBetterPosters = staticCompositionLocalOf<BetterPostersApi?> { null }

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
fun cardPosterUrl(media: MediaNode): String? = cardPosterUrl(media.id, media.posterUrl)

/**
 * The same answer for a card that carries an id and a cover but not the node.
 *
 * The schedule is that card: an [com.anisequel.data.model.ArrivingEntry] is a
 * projection of a list entry - id, title, cover, dates - built for the page, so it has
 * no `MediaNode` to hand in. Looking the poster up by id keeps the schedule on the
 * reader's chosen source instead of being the one list still drawing AniList art.
 */
@Composable
fun cardPosterUrl(mediaId: Int, fallback: String?): String? {
    if (LocalPosterSource.current != PosterSource.BETTER_POSTERS) return fallback
    val api = LocalBetterPosters.current ?: return fallback

    var better by remember(mediaId) { mutableStateOf<String?>(null) }
    LaunchedEffect(mediaId, api) {
        better = api.posterUrl(mediaId)
    }
    return better ?: fallback
}
