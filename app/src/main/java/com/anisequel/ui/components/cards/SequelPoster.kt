package com.anisequel.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.displayName
import com.anisequel.ui.components.detail.FullScreenCoverViewer
import com.anisequel.ui.components.expressive.expressiveHoldGesture

/**
 * The sequel card's artwork, drawn by the same cell the Library grid and the
 * settings preview use, so a card style is one definition rather than three.
 *
 * The hold and the tap still belong to the poster alone: they open the cover
 * viewer, and the title and the chips beside the artwork are not covers. That is
 * why the gesture goes on [MediaPosterCell.posterModifier] rather than on the
 * cell's own modifier, which would also cover the text under it.
 *
 * The rating is only handed to the cell when the chosen style puts it *on* the
 * artwork. Otherwise [SequelCardDetails] keeps drawing it in its chip row, exactly
 * as it always has, and the poster shows nothing - which is the difference between
 * a style and a duplicate.
 */
@Composable
fun SequelPoster(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val coverColor = sequel.coverColor.toCoverColorOrNull()
    var showFullScreen by remember { mutableStateOf(false) }
    // Where the cell's artwork is on screen, so the viewer can grow out of the
    // picture the reader tapped instead of fading in over it. Keyed on the media
    // because a recycled lazy slot must not carry the previous title's rectangle.
    var posterBounds by remember(sequel.sequelMedia.id) { mutableStateOf<Rect?>(null) }
    val style = LocalMediaCardStyle.current
    val score = sequel.sequelMedia.averageScore?.takeIf { it >= 75 }

    if (showFullScreen) {
        FullScreenCoverViewer(
            media = sequel.sequelMedia,
            origin = posterBounds,
            onDismiss = { showFullScreen = false }
        )
    }

    MediaPosterCell(
        coverUrl = sequel.sequelCoverUrl,
        coverColor = coverColor,
        title = sequel.sequelTitle,
        score = if (style.scoreOnPoster) score else null,
        modifier = modifier.width(POSTER_WIDTH),
        style = style,
        posterModifier = Modifier
            .onGloballyPositioned { posterBounds = it.boundsInWindow() }
            .expressiveHoldGesture(
                onHold = { showFullScreen = true },
                onClick = { showFullScreen = true }
            )
            // The only way into the cover viewer from a card: tap and hold both
            // opened it, and neither said so. The image is decorative
            // (`contentDescription = null`), so without this the control had no name
            // and no role for TalkBack at all.
            .semantics {
                role = Role.Button
                contentDescription = "Open the cover for ${sequel.sequelMedia.displayName}"
            },
        overlay = {
            // The top scrim exists so the format pill under it stays readable over
            // whichever cover is behind it.
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .fillMaxWidth(0.6f)
                    .height(32.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                        )
                    )
            )
            Surface(
                shape = RoundedCornerShape(bottomStart = 10.dp),
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Text(
                    text = sequel.format,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                )
            }
        }
    )
}

/**
 * The poster's width. The height is the 2:3 ratio the cell enforces, rather than the
 * 104x152 the box used to state twice - two numbers for one shape is how they drift.
 */
private val POSTER_WIDTH = 104.dp

/**
 * Parses an AniList `#RRGGBB` cover colour, or null if it is missing or junk.
 *
 * `internal` rather than `private` so the parent-cover thumbnails in
 * [com.anisequel.ui.components.cards.SequelCard] and the detail sheet can tint
 * their placeholders the same way instead of each re-implementing the parse.
 */
internal fun String?.toCoverColorOrNull(): Color? {
    val hex = this?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val value = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}
