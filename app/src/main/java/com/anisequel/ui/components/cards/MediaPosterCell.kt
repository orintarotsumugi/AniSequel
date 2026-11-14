package com.anisequel.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.domain.card.CardScorePlacement
import com.anisequel.domain.card.PosterCorner
import com.anisequel.domain.card.metaCornerFor
import com.anisequel.domain.card.scoreCornerOf
import com.anisequel.domain.card.CardTitlePlacement
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.MarqueeText
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.theme.AniSequelTheme

/**
 * The card style the app is drawing right now.
 *
 * A composition local rather than a parameter on every card because the style is one
 * app-wide fact read by three unrelated surfaces - the Library grid, the dashboard's
 * sequel rows and the picker's preview - and threading it through each of their
 * signatures would let exactly one of them be handed the stale value. The precedent
 * is `LocalTitleMotion`: a presentation setting resolved once at the root.
 *
 * Defaults to [MediaCardStyle.DEFAULT] so a preview, a dialog or a test composed
 * outside the app root draws the plain card rather than nothing.
 */
val LocalMediaCardStyle = staticCompositionLocalOf { MediaCardStyle.DEFAULT }

/**
 * The corner to an [Alignment], kept here so the mapping exists once.
 *
 * The decision about *which* corner is not here: it is `scoreCornerOf` and
 * `metaCornerFor` in the domain layer, where the placement table can be tested with
 * no Android on the classpath. This only spells the answer in the type Compose
 * draws with.
 */
fun PosterCorner.toAlignment(): Alignment = when (this) {
    PosterCorner.TOP_START -> Alignment.TopStart
    PosterCorner.TOP_END -> Alignment.TopEnd
    PosterCorner.BOTTOM_START -> Alignment.BottomStart
    PosterCorner.BOTTOM_END -> Alignment.BottomEnd
}

/** The AniList poster ratio, 2:3 - the same number the Library grid already used. */
const val POSTER_ASPECT: Float = 2f / 3f

/**
 * One media card: the artwork, the rating and the title, placed by [style].
 *
 * ## Why this is shared rather than copied into each surface
 *
 * The picker's preview has to show what the reader is choosing. If it drew its own
 * idea of "Modern" while the grid drew another, the preview would be an advert for a
 * card the app does not build - and nothing would notice, because the preview is the
 * one copy nobody taps. So the preview calls this with a made-up title and a made-up
 * score, and the grid and the dashboard call it with real ones. There is then exactly
 * one place a style can be wrong.
 *
 * [overlay] is where each surface adds what only it has: the Library's progress pill
 * and favourite star, the dashboard's format pill. It is drawn last, so a surface's
 * own mark is never hidden behind the cell's - and it is a [BoxScope] receiver
 * because the corners belong to the artwork's box, not to the card's column.
 *
 * The caller owns the width. The Library grid passes `fillMaxWidth()` and the
 * dashboard's row passes a fixed poster width, because one card is a grid cell and
 * the other is the leading artwork of a horizontal card; forcing either here is how
 * a fixed-size poster ends up filling a row.
 *
 * [posterModifier] is for a gesture that belongs to the *artwork* rather than to the
 * card. The dashboard's poster opens the full-screen cover viewer on a tap and a
 * hold, and the text beside it does nothing - so that gesture cannot go on the
 * cell's own modifier, which also covers the title and the rating line.
 *
 * A null [score] draws no rating anywhere, in any style: a card with an empty badge
 * is a control that has forgotten what it was for.
 */
@Composable
fun MediaPosterCell(
    coverUrl: String?,
    coverColor: Color?,
    title: String,
    score: Int?,
    modifier: Modifier = Modifier,
    style: MediaCardStyle = LocalMediaCardStyle.current,
    aspect: Float = POSTER_ASPECT,
    contentDescription: String? = null,
    posterModifier: Modifier = Modifier,
    /**
     * Whether the caption is drawn at all.
     *
     * The Cover wall library view is the one caller that turns this off: it wants
     * the artwork and the reader's chosen style, but not a repeated name under
     * every cover. It is a separate flag from [style] rather than a fake
     * `COVER_ONLY` style, because the wall must still honour the borders, glow and
     * score placement the reader chose.
     */
    titleVisible: Boolean = true,
    overlay: @Composable BoxScope.() -> Unit = {}
) {
    val scoreOnTop = score != null && scoreCornerOf(style) == PosterCorner.TOP_START
    val scoreOnBottom = score != null && scoreCornerOf(style) == PosterCorner.BOTTOM_END
    val titleOverlaid = titleVisible && style.titlePlacement == CardTitlePlacement.OVERLAY_BOTTOM
    val titleHidden = !titleVisible || style.titlePlacement == CardTitlePlacement.HIDDEN

    Column(modifier = modifier) {
        PosterArtwork(
            coverUrl = coverUrl,
            coverColor = coverColor,
            title = title,
            score = score,
            scoreOnTop = scoreOnTop,
            scoreOnBottom = scoreOnBottom,
            titleOverlaid = titleOverlaid,
            style = style,
            contentDescription = contentDescription,
            posterModifier = posterModifier,
            aspect = aspect,
            overlay = overlay
        )

        if (!titleOverlaid && !titleHidden) {
            Spacer(modifier = Modifier.height(5.dp))
            // The title in the body is the accessible name for the whole cell, which
            // is why the image above is handed a null description here: the grid would
            // otherwise read the same name twice in a row on every swipe.
            MarqueeText(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (score != null && scoreCornerOf(style) == null && !titleHidden) {
            Spacer(modifier = Modifier.height(if (titleOverlaid) 6.dp else 1.dp))
            if (style.scorePlacement == CardScorePlacement.BANNER_BELOW_TITLE) {
                ScoreBanner(score = score)
            } else {
                Text(
                    text = "$score%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * The rating as a corner badge on the artwork.
 *
 * `scrim` rather than the theme's surface: it sits on somebody's cover art, whose
 * palette is not the app's, and a container picked from the scheme can match the
 * artwork closely enough to disappear.
 */
@Composable
private fun PosterScoreBadge(score: Int?, modifier: Modifier = Modifier) {
    if (score == null) return
    Row(
        modifier = modifier
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.72f))
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.AnimeSparkle,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = "$score%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The rating as a band the width of the card.
 *
 * The loudest of the four placements, and the only one that is a *button-shaped*
 * thing - which is why it is a filled container rather than a capsule on the art: it
 * reads as part of the card, so it takes the card's own colour.
 */
@Composable
fun ScoreBanner(score: Int, modifier: Modifier = Modifier) {
    val strong = score >= 80
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(ExpressiveShapes.SmallCorner))
            .background(
                if (strong) {
                    AniSequelTheme.statusColors.success
                } else {
                    MaterialTheme.colorScheme.secondaryContainer
                }
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.AnimeSparkle,
            contentDescription = null,
            tint = if (strong) Color.White else MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.size(12.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "$score%",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (strong) Color.White else MaterialTheme.colorScheme.onSecondaryContainer
        )
    }
}

/**
 * Just the artwork: the image, its tint, and whatever the caller lays over it.
 *
 * Split from `MediaPosterCell` because the cell is two responsibilities - the
 * artwork block and the words under it - and only one of them knows anything
 * about the overlay, the scrim and the corner badges. The call sites hand in a
 * poster, sometimes a rating, sometimes an extra overlay layer; this is the one
 * function that draws any of it.
 */
@Composable
private fun PosterArtwork(
    coverUrl: String?,
    coverColor: Color?,
    title: String,
    score: Int?,
    scoreOnTop: Boolean,
    scoreOnBottom: Boolean,
    titleOverlaid: Boolean,
    style: MediaCardStyle,
    contentDescription: String?,
    posterModifier: Modifier,
    aspect: Float,
    overlay: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = posterModifier
            .fillMaxWidth()
            .aspectRatio(aspect)
            .then(
                if (style.glowing) {
                    Modifier.shadow(
                        elevation = 10.dp,
                        shape = MaterialTheme.shapes.small,
                        ambientColor = coverColor ?: Color.Black,
                        spotColor = coverColor ?: Color.Black
                    )
                } else {
                    Modifier
                }
            )
            .clip(MaterialTheme.shapes.small)
            .background(coverColor ?: MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(
                if (style.bordered) {
                    Modifier.border(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant,
                        MaterialTheme.shapes.small
                    )
                } else {
                    Modifier
                }
            )
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = contentDescription,
                // One percent of overdraw from the top edge, so `Crop`'s whole-pixel
                // rounding cannot leave a row of the box's own tint showing across
                // the artwork - see [unseamedCover]. Every cover in the app had this
                // fix; a shared cell is where it stops being something each caller has
                // to remember to ask for.
                modifier = Modifier
                    .matchParentSize()
                    .unseamedCover(),
                contentScale = ContentScale.Crop
            )
        }

        // The scrim under an overlaid title. Weighted to the foot of the poster
        // only, because the artwork is the thing the reader came to look at and a
        // full-height wash is how a cover stops being a cover.
        if (titleOverlaid) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(64.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.78f))
                        )
                    )
            )
        }

        if (scoreOnTop) {
            PosterScoreBadge(
                score = score,
                modifier = Modifier
                    .align(PosterCorner.TOP_START.toAlignment())
                    .padding(5.dp)
            )
        }
        if (scoreOnBottom) {
            PosterScoreBadge(
                score = score,
                modifier = Modifier
                    .align(PosterCorner.BOTTOM_END.toAlignment())
                    .padding(5.dp)
            )
        }

        if (titleOverlaid) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 6.dp)
            )
        }

        overlay()
    }
}
