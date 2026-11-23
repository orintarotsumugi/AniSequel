package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem

/**
 * The two newest sequel styles, drawn at the size the picker has room for.
 *
 * Split out of `LayoutPreviews.kt`, which was over the line budget for a file of
 * previews - and these two are the ones with drawing of their own rather than a
 * row of bars re-arranged. Each is a miniature of the real card: the shape is what
 * the reader is choosing, and the geometry here is the geometry there, scaled.
 */

/** A ticket: artwork, a reading column, and a perforated stub on the right. */
@Composable
internal fun TicketStylePreview(entry: MediaListEntryItem?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        MiniCover(
            entry = entry,
            modifier = Modifier
                .padding(6.dp)
                .width(44.dp)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp, horizontal = 6.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            // Real words rather than bars: a ticket's stub and its text column are
            // only worth choosing if the reader can see what fits in the column, and
            // a bar cannot show that a name wraps to a second line.
            Text(
                text = "SEQUEL",
                style = miniCaptionStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1
            )
            Text(
                text = previewTitle(entry),
                style = miniBodyStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "Airing \u00b7 12 episodes",
                style = miniCaptionStyle(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "Watch sequel",
                style = miniCaptionStyle(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 4.dp)
            )
        }
        // A row of dots rather than the real card's dashes: at this size a dash
        // pattern reads as a grey smear, and the reader is choosing a shape.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .size(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

/** An instant print: a cream frame with the caption margin underneath, tilted. */
@Composable
internal fun PolaroidStylePreview(entry: MediaListEntryItem?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .fillMaxHeight(0.92f)
                // The same fixed lean the real card gets, so the sample is the shape
                // the reader will see in the list rather than a straight-on card.
                .graphicsLayer { rotationZ = -2f }
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF7F3EA))
                .padding(5.dp)
        ) {
            MiniCover(
                entry = entry,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = previewTitle(entry),
                style = miniCaptionStyle(),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF23211F).copy(alpha = 0.85f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "12 episodes",
                style = miniCaptionStyle(),
                // The mock on this page is a printed card - fixed paper and fixed
                // ink on purpose - but the ink was still being faded to 0.5, which
                // is 3.1:1 on #F7F3EA. 0.68 is 5.3:1: the same printed look, and
                // the episode count is actually readable at the size this is drawn.
                color = Color(0xFF23211F).copy(alpha = 0.68f),
                maxLines = 1
            )
        }
    }
}

/**
 * A deck: two sheets behind, the top card carrying the artwork and the caption.
 *
 * Drawn at the same relationship the real card uses - the sheets offset down and to
 * the right by their own shares of the card - so the reader is choosing the pile
 * they will actually scroll past, not a diagram of one.
 */
@Composable
internal fun DeckStylePreview(entry: MediaListEntryItem?, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.86f)
                .graphicsLayer {
                    translationX = 10.dp.toPx()
                    translationY = 8.dp.toPx()
                    alpha = 0.35f
                }
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.86f)
                .graphicsLayer {
                    translationX = 5.dp.toPx()
                    translationY = 4.dp.toPx()
                    alpha = 0.6f
                }
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .fillMaxHeight(0.86f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box(modifier = Modifier.fillMaxWidth().weight(1f)) {
                MiniCover(entry = entry, modifier = Modifier.fillMaxSize())
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f))
                            )
                        )
                )
                Text(
                    text = previewTitle(entry),
                    style = miniBodyStyle(),
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(8.dp)
                )
            }
            Text(
                text = "Watch sequel",
                style = miniCaptionStyle(),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onPrimary,
                maxLines = 1,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .padding(vertical = 4.dp)
            )
        }
    }
}

/** A film strip: sprockets down both sides of one wide frame. */
@Composable
internal fun FilmstripStylePreview(entry: MediaListEntryItem?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF16151A)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniSprockets()
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(4.dp))
        ) {
            MiniCover(entry = entry, modifier = Modifier.fillMaxSize())
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(Color.Black.copy(alpha = 0.84f), Color.Transparent)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "SEQUEL",
                    style = miniCaptionStyle(),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF4F1EA).copy(alpha = 0.72f),
                    maxLines = 1
                )
                Text(
                    text = previewTitle(entry),
                    style = miniBodyStyle(),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFF4F1EA),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        MiniSprockets()
    }
}

/** The perforations, at the size this preview can carry. */
@Composable
private fun MiniSprockets() {
    Column(
        modifier = Modifier
            .fillMaxHeight()
            .width(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        repeat(5) {
            Box(
                modifier = Modifier
                    .size(width = 5.dp, height = 3.dp)
                    .clip(RoundedCornerShape(1.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            )
        }
    }
}
