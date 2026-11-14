package com.anisequel.ui.screens.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaSurface
import java.util.Locale

/**
 * The three doors on Home: Anime, Manga and Other.
 *
 * Extracted from NovaHomePieces.kt when they ran to twice the reference's height:
 * the old 120dp card was a hero panel, and two of them plus Other pushed Continue
 * Watching off a phone's first screen. 80dp is a doorway - the count, the name and
 * one piece of art on the row, no empty space between.
 *
 * The artwork is a full-height panel rather than a thumbnail: at this height a
 * small square poster reads as a bullet point, while the edge-to-edge slice still
 * makes the door recognisable. No request is made here - the anime cover is the
 * random AniList node the dashboard already fetched, the manga cover comes from
 * the existing preview page, and Other borrows the same node's banner.
 */

/** Height of the two list doors. Shared with the Home skeleton so they match. */
internal val NovaShortcutHeight = 80.dp

/** Height of the full-width Other door. */
internal val NovaOtherShortcutHeight = 76.dp

@Composable
fun NovaShortcutCard(
    title: String,
    meta: String,
    icon: ImageVector,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    NovaSurface(
        modifier = modifier
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open $title",
                onClick = onClick
            ),
        tonalElevation = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NovaShortcutHeight)
                .testTag("nova_shortcut_${title.lowercase(Locale.ROOT).replace(' ', '_')}")
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 12.dp, end = 4.dp, top = 9.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Spacer(modifier = Modifier.width(1.dp))
                    Icon(
                        imageVector = AppVectorIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(11.dp)
                    )
                }
                Text(
                    text = title,
                    // Two lines are deliberate: the list doors share a 360dp
                    // phone, so "ANIME LIST" is two short words, not one long
                    // title. ExtraBold at titleSmall reads as a label without
                    // inventing a third heading size for a 96dp column.
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    lineHeight = MaterialTheme.typography.titleSmall.lineHeight * 0.94f
                )
            }
            ShortcutArtwork(
                coverUrl = coverUrl,
                icon = icon,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.36f)
            )
        }
    }
}

/**
 * The full-width "Other" door.
 *
 * Not a [NovaShortcutCard] with different art: it goes somewhere that is not a
 * list, so it should not look like the two cards that do. It is also one line
 * shorter, because it has no count to sit above its name. The quieter surface is
 * the distinction.
 */
@Composable
fun NovaOtherShortcut(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** The wide artwork on the right; the AniList banner when one is available. */
    coverUrl: String? = null
) {
    val interactionSource = remember { MutableInteractionSource() }
    NovaSurface(
        modifier = modifier
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open the Other hub",
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(NovaOtherShortcutHeight)
                .testTag("nova_other_shortcut")
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppVectorIcons.AnimeSparkle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = "Calendar, tier lists and more",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = AppVectorIcons.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(12.dp)
                    )
                }
                Text(
                    text = "OTHER",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            ShortcutArtwork(
                coverUrl = coverUrl,
                icon = AppVectorIcons.AnimeSparkle,
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.34f)
            )
        }
    }
}

/**
 * The artwork panel both Home shortcuts wear on their right edge.
 *
 * The gradient is the card's own surface colour rather than a black scrim, so the
 * title column and the artwork meet without a hard edge on either palette. A null
 * URL is a real answer - the demo, or a response that has not landed - and draws
 * the icon on a themed wash instead of an empty hole.
 */
@Composable
private fun ShortcutArtwork(
    coverUrl: String?,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.background(
            Brush.linearGradient(
                listOf(
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.tertiaryContainer
                )
            )
        ),
        contentAlignment = Alignment.Center
    ) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerLow
                                    .copy(alpha = 0.92f),
                                Color.Transparent
                            )
                        )
                    )
            )
        } else {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                modifier = Modifier.size(24.dp)
            )
        }
    }
}
