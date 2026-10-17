package com.anisequel.ui.screens.studio

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.chaptersLabel
import com.anisequel.data.model.displayName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.detail.FullScreenCoverViewer
import com.anisequel.ui.components.expressive.expressiveHoldGesture

/**
 * One title on a studio's shelf: poster, name, and what kind of thing it is.
 *
 * Not [com.anisequel.ui.components.cards.SequelPoster]: that card is built around a
 * [com.anisequel.data.model.MissedSequel], and a studio page has bare
 * [MediaNode]s - there is no parent title and no "you have not seen this" story to
 * tell, which is the entire point of the other card. Forcing these into that shape
 * would mean inventing a fake parent for every one of a studio's two hundred
 * credits.
 *
 * Tap opens the detail sheet; hold opens the artwork on its own, the same pair of
 * gestures every other poster in the app answers to. The name sits *under* the
 * poster rather than over it, because a shelf is read by scanning titles and a
 * gradient over the bottom third of the art hides the one thing being scanned.
 */
@Composable
fun StudioTitleCard(
    media: MediaNode,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val coverColor = media.posterColor.toCoverColorOrNull()
    var showArtwork by remember { mutableStateOf(false) }

    if (showArtwork) {
        FullScreenCoverViewer(media = media, onDismiss = { showArtwork = false })
    }

    Column(
        modifier = modifier
            .width(STUDIO_CARD_WIDTH)
            // On the whole column, not the poster: the title is part of the target,
            // and a card whose caption does not respond reads as a broken image.
            .expressiveHoldGesture(
                onHold = { showArtwork = true },
                onClick = onClick
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(STUDIO_POSTER_RATIO)
                .clip(MaterialTheme.shapes.medium)
                .background(coverColor ?: MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            // One solid tint rather than a gradient: `AsyncImage` covers it as soon
            // as the cover arrives, so anything richer is drawn and then hidden on
            // every card of every shelf.
            Icon(
                imageVector = if (media.isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.5f),
                modifier = Modifier.size(26.dp)
            )

            media.posterUrl?.let { url ->
                AsyncImage(
                    model = url,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // The score is the one number worth overdrawing: it is how a reader
            // decides which of a studio's titles to open, and on a shelf of fifteen
            // posters it cannot be found anywhere else without tapping each one.
            media.averageScore?.let { score ->
                Surface(
                    shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 10.dp),
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                ) {
                    Text(
                        text = "$score%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = media.displayName,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = studioTitleMeta(media),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * "TV · 12 eps" for a series, "Manga · 96 chs" for a book.
 *
 * Counts are dropped when AniList does not know them - a zero here is the API
 * saying it has no count, and "0 eps" is a claim. The score is already on the
 * poster, so repeating it in the line would only cost a row its width.
 */
private fun studioTitleMeta(media: MediaNode): String = buildList {
    add(media.formatLabel)
    if (media.isManga) {
        media.chaptersLabel?.let { add(it) }
    } else {
        media.episodes?.takeIf { it > 0 }?.let { add(if (it == 1) "1 ep" else "$it eps") }
    }
}.joinToString(" · ")

/**
 * The card's width, and the poster's shape.
 *
 * 132dp is three cards and a gutter on a 360dp phone, which is what makes a shelf
 * read as a shelf rather than as a list. The 2:3 ratio is AniList's own cover
 * shape; anything squarer crops the faces off the posters.
 */
private val STUDIO_CARD_WIDTH = 132.dp
private val STUDIO_POSTER_RATIO = 2f / 3f
