package com.example.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MediaNode
import com.example.data.model.displayName
import com.example.data.model.englishName
import com.example.data.model.formatLabel
import com.example.data.model.posterColor
import com.example.data.model.posterUrl
import com.example.data.model.releaseLabel
import com.example.data.model.romajiName
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.PannableBackdrop
import com.example.ui.components.cards.toCoverColorOrNull
import com.example.ui.components.expressive.expressiveHoldGesture

@Composable
fun DetailHeader(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    var showFullScreen by remember { mutableStateOf(false) }
    val coverBg = media.posterColor.toCoverColorOrNull() ?: MaterialTheme.colorScheme.primary

    // The header backdrop is the banner when AniList has one, and the entry's own
    // key visual when it does not.
    //
    // It used to be banner-or-nothing, and nothing is the common case rather than
    // the exception: the dashboard's list query deliberately does not ask for
    // `bannerImage` (it is one wide image per entry on the hottest request in the
    // app, and it is fetched later by the detail query), so *every* detail sheet
    // opened from the dashboard started as a flat grey slab under the scrim until
    // the detail response landed. Entries AniList has no banner for at all stayed
    // that way. Falling back to the cover costs no request - the list query already
    // sent it - and makes the header the show's own artwork from its first frame.
    val backdrop = media.bannerImage ?: media.posterUrl

    // What shows through the 45%-to-85% scrim before (or instead of) the artwork.
    // AniList's dominant colour for the key visual is the difference between a
    // header that is already recognisably this show and a grey rectangle.
    val backdropBase = media.posterColor.toCoverColorOrNull()
        ?: MaterialTheme.colorScheme.surfaceContainerHigh

    if (showFullScreen) {
        FullScreenCoverViewer(
            media = media,
            onDismiss = { showFullScreen = false }
        )
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(MaterialTheme.shapes.large)
            .background(backdropBase)
    ) {
        if (backdrop != null) {
            // Pannable rather than cropped.
            //
            // Banners are authored wide and a 280dp box is not, so `ContentScale
            // .Crop` threw away the left and right thirds of the artwork permanently
            // - and the composition is authored for the whole frame. Now it scales to
            // cover and the reader can drag to the rest.
            //
            // `FillWidth` rather than `Crop` because the picture is being panned, not
            // cropped: a crop would fight the translation and the two would disagree
            // about what is on screen as soon as the offset was non-zero.
            PannableBackdrop(
                model = backdrop,
                contentDescription = null,
                contentScale = ContentScale.FillWidth
            )
        }

        // `matchParentSize`, not a literal height: this scrim was 216dp while the box
        // holding it grew to 280dp, so the bottom 64dp of a taller, pannable image
        // was going to sit under the title lockup with no scrim over it at all.
        //
        // Lighter than the old 0.45/0.85 pair for the same reason the profile
        // banner's was: the reader can now move the artwork, so the scrim darkens
        // whichever part happens to be on screen. A heavy one means the same
        // artwork is legible in one position and murky in another.
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.18f),
                            Color.Black.copy(alpha = 0.62f)
                        )
                    )
                )
        )

        Surface(
            shape = RoundedCornerShape(bottomEnd = 10.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                text = media.formatLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
            )
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(14.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Enhanced interactive anime cover with hold-to-fullscreen
            Box(
                modifier = Modifier
                    .width(92.dp)
                    .height(132.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = MaterialTheme.shapes.small,
                        ambientColor = coverBg,
                        spotColor = Color.Black
                    )
                    .clip(MaterialTheme.shapes.small)
                    .border(1.dp, Color.White.copy(alpha = 0.25f), MaterialTheme.shapes.small)
                    .background(coverBg)
                    .expressiveHoldGesture(
                        onHold = { showFullScreen = true },
                        onClick = { showFullScreen = true }
                    )
            ) {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = media.displayName,
                    modifier = Modifier.matchParentSize(),
                    contentScale = ContentScale.Crop
                )

                // Fullscreen indicator pill at bottom right
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.65f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                ) {
                    Icon(
                        imageVector = AppVectorIcons.FullscreenExpand,
                        contentDescription = "Hold to expand",
                        tint = Color.White,
                        modifier = Modifier
                            .padding(4.dp)
                            .size(14.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                // Romaji and English title tiles
                val romaji = media.romajiName
                val english = media.englishName

                if (!romaji.isNullOrBlank() && romaji != media.displayName) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "ROMAJI",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = romaji,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (!english.isNullOrBlank() && english != media.displayName) {
                    Spacer(modifier = Modifier.height(3.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = MaterialTheme.shapes.extraSmall,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f)
                        ) {
                            Text(
                                text = "EN",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = english,
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.90f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = media.releaseLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.80f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
