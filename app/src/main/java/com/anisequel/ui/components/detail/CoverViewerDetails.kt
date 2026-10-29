package com.anisequel.ui.components.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.romajiName
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.liveChartUrl
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * The bottom card of the artwork viewer: the titles, the source switch, the
 * outbound links and the gesture hint.
 *
 * Split out of `FullScreenCoverViewer.kt` because it is the one part of the
 * viewer that is about the *title* rather than the artwork: it never touches the
 * zoom frame or the pan, only reads whether a zoom is in effect for the hint.
 * The viewer above it is gesture and geometry; this card is typography and links.
 */

@Composable
internal fun ViewerDetailsCard(
    media: MediaNode,
    sources: List<CoverSource>,
    sourceIndex: Int,
    onSelectSource: (Int) -> Unit,
    /**
     * Read here rather than at the call site so a pinch recomposes this card
     * - which prints the hint - and not the caller's whole column.
     */
    zoom: CoverZoomState
) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
        tonalElevation = 8.dp,
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 480.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = media.displayName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            // Which AniList image is on screen. Only drawn when there is a
            // choice: a title with no banner would otherwise get a single
            // "Poster" pill that does nothing, which reads as a broken control.
            if (sources.size > 1) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    sources.forEachIndexed { index, candidate ->
                        ViewerPill(
                            label = candidate.label,
                            selected = index == sourceIndex,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelectSource(index) }
                        )
                    }
                }
            }

            // Romaji & English Titles
            val romaji = media.romajiName
            val english = media.englishName

            if (!romaji.isNullOrBlank() || !english.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (!romaji.isNullOrBlank()) {
                        TitleChip(
                            label = "ROMAJI",
                            value = romaji,
                            labelColor = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    if (!english.isNullOrBlank()) {
                        TitleChip(
                            label = "ENGLISH",
                            value = english,
                            labelColor = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Where the reader can look at more of this title's artwork. AniList
            // is the page this viewer was opened from; LiveChart is the only
            // other site the app can honestly send them to for the schedule and
            // its own poster, since it publishes no API to read.
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ViewerActionPill(
                    icon = AppVectorIcons.OpenInNew,
                    label = "AniList",
                    modifier = Modifier.weight(1f),
                    onClick = { openExternalUrlOrCopy(context, media.pageUrl) }
                )
                if (!media.isManga) {
                    ViewerActionPill(
                        icon = AppVectorIcons.CalendarClock,
                        label = "LiveChart",
                        modifier = Modifier.weight(1f),
                        onClick = { openExternalUrlOrCopy(context, media.liveChartUrl) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (zoom.isZoomed) {
                    "Double-tap to reset zoom · Tap anywhere to close"
                } else {
                    "Pinch or double-tap to zoom · Tap anywhere to close"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
            )
        }
    }
}

/** One of the two title spellings, under the language it is spelled in. */
@Composable
private fun TitleChip(
    label: String,
    value: String,
    labelColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = labelColor,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One image source, as a selectable pill.
 *
 * A capsule in [ExpressiveShapes] rather than a Material segmented button: this
 * sits over artwork in a dialog, and the pill matches the cards behind it. The
 * selected state is the primary container, so the current image reads at a
 * glance without a second label.
 */
@Composable
private fun ViewerPill(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = ExpressiveShapes.pill,
        color = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        contentColor = if (selected) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        modifier = modifier
            .bouncyPress(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Show the $label",
                onClick = onClick
            )
            .testTag("cover_source_${label.lowercase()}")
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
        )
    }
}

/** One outbound link from the viewer, wearing its destination's name. */
@Composable
private fun ViewerActionPill(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        modifier = modifier
            .bouncyPress(interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = onClick
            )
            .testTag("cover_action_${label.lowercase()}")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}
