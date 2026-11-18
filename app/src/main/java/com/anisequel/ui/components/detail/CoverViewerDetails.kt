package com.anisequel.ui.components.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.anisequel.domain.ui.PosterOverlays
import com.anisequel.domain.ui.SequelSeason
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.liveChartUrl
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * The bottom sheet of the artwork viewer: the titles, the source switch, the
 * outbound links and the gesture hint.
 *
 * Split out of `FullScreenCoverViewer.kt` because it is the one part of the
 * viewer that is about the *title* rather than the artwork: it never touches the
 * zoom frame or the pan, only reads whether a zoom is in effect for the hint.
 * The viewer above it is gesture and geometry; this card is typography and links.
 *
 * ## Why it is a sheet now
 *
 * The viewer's artwork runs edge to edge, so this no longer floats as a centred
 * card over a dark dialog: it is anchored to the bottom of the display with only
 * its top corners rounded, which is what tells the reader the screen continues
 * behind it. It draws its own gradient above and its own navigation-bar clearance,
 * because the window underneath is edge to edge and nothing else pads the bottom.
 */
@Composable
internal fun ViewerDetailsCard(
    media: MediaNode,
    sources: List<CoverSource>,
    sourceIndex: Int,
    /** The image on screen, so the sheet can name its btttr variant. */
    image: CoverImage?,
    /** The page inside the selected source, and how many images it holds. */
    imageIndex: Int,
    imageCount: Int,
    /**
     * The season this entry is, when btttr's render is the *series* art rather than
     * this entry's own; null when the picture is the entry's own.
     *
     * See `FullScreenCoverViewer.seriesSeasonOf`. btttr addresses one IMDb id per
     * series, so a Season 2 entry is offered season one's render - the viewer shows
     * it because it is the only place the series art can be seen whole, and this is
     * how it says so instead of passing it off as season two's poster.
     */
    seriesSeason: Int?,
    onSelectSource: (Int) -> Unit,
    /**
     * Read here rather than at the call site so a pinch recomposes this card
     * - which prints the hint - and not the caller's whole column.
     */
    zoom: CoverZoomState
) {
    val context = LocalContext.current
    val provider = sources.getOrNull(sourceIndex)?.label.orEmpty()
    // Compact by default. The sheet's job on a full-screen picture is to say what
    // is on screen and let the reader switch source; the two title spellings are
    // reference material, not the reason the sheet is there, and printing them
    // under every image pushed the gesture hint off a short phone. Expanded is the
    // reader asking for the rest, and the handle and the chevron both answer.
    var expanded by rememberSaveable(media.id) { mutableStateOf(false) }
    val toggleExpanded = { expanded = !expanded }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            // The gradient under the sheet is what keeps the artwork legible up to
            // the sheet's own edge: without it a pale cover met a pale sheet in a
            // hard line across the bottom of the screen.
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.55f)
                )
            )
            // Edge to edge at the bottom: the sheet's own background reaches the
            // nav bar (or the gesture handle), and only the content is lifted.
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
            tonalElevation = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 560.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = 18.dp)
                    .padding(top = 6.dp, bottom = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SheetHandle(expanded = expanded, onToggle = toggleExpanded)

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = media.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.ExtraBold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )

                        CoverOnScreenCaption(
                            provider = provider,
                            image = image,
                            imageIndex = imageIndex,
                            imageCount = imageCount,
                            seriesSeason = seriesSeason
                        )
                    }

                    IconButton(
                        onClick = toggleExpanded,
                        modifier = Modifier.testTag("cover_details_toggle")
                    ) {
                        Icon(
                            imageVector = if (expanded) {
                                AppVectorIcons.ExpandLess
                            } else {
                                AppVectorIcons.ExpandMore
                            },
                            contentDescription = if (expanded) {
                                "Hide the title spellings"
                            } else {
                                "Show the title spellings"
                            },
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                ViewerSourcePills(
                    sources = sources,
                    sourceIndex = sourceIndex,
                    onSelectSource = onSelectSource
                )

                ViewerOutboundLinks(media = media, context = context)

                AnimatedVisibility(
                    visible = expanded,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                    label = "cover_title_spellings"
                ) {
                    ViewerTitleChips(media = media)
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = when {
                        zoom.isZoomed -> "Double-tap to reset zoom · Tap to show the controls"
                        imageCount > 1 ->
                            "Tap to hide the controls · Swipe for more art · Pinch to zoom"
                        else -> "Tap to hide the controls · Pinch or double-tap to zoom"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}


/**
 * The grabber and its chevron: one control, two hit targets.
 *
 * The capsule is what tells the reader the sheet has more below it, and tapping
 * the whole padded row - not just the four-pixel line - is what makes that a
 * target rather than a decoration. It is a button with a label for TalkBack, so
 * the gesture is not the only way to reach the title spellings.
 */
@Composable
private fun SheetHandle(expanded: Boolean, onToggle: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.99f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = if (expanded) "Hide the title spellings" else "Show the title spellings",
                onClick = onToggle
            )
            .padding(vertical = 8.dp)
            .testTag("cover_details_handle"),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(4.dp)
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f))
        )
    }
}

@Composable
private fun CoverOnScreenCaption(
    provider: String,
    image: CoverImage?,
    imageIndex: Int,
    imageCount: Int,
    seriesSeason: Int?
) {
    // What is on screen, in words: the provider is not obvious from the picture
    // alone, and btttr's four renders differ only by an overlay the reader cannot
    // name without being told which one is up.
    val onScreenLabel = when {
        imageCount > 1 && image?.variant != null ->
            "$provider · ${image.variant} · ${imageIndex + 1} of $imageCount"
        imageCount > 1 ->
            "$provider · Image ${imageIndex + 1} of $imageCount"
        image?.variant != null -> "$provider · ${image.variant}"
        else -> provider.takeIf { it.isNotBlank() && seriesSeason != null }
    }?.let { label ->
        // The caption is where a reader reads what the picture is, so the correction
        // belongs at the end of it rather than as a second caption; the line below
        // explains the term.
        if (seriesSeason != null) "$label · series art" else label
    }
    if (onScreenLabel != null) {
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = onScreenLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("cover_image_caption")
        )
    }

    if (seriesSeason == null) return
    // "Series art" is a term of art, not an explanation. Without this line it reads
    // as a label the reader has to decode, and the alternative - hiding the render -
    // is worse: btttr has one picture per series and this is the only screen that
    // can show it at its own size.
    // "Final Season" has no number - [SequelSeason] answers 99 for it, which must not
    // reach the reader as "Season 99".
    val seasonName =
        if (seriesSeason == SequelSeason.FINAL_SEASON) "the final season"
        else "Season $seriesSeason"
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = "BetterPosters has one render per series, so this is " +
            "the series artwork rather than $seasonName's.",
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
        textAlign = TextAlign.Center,
        modifier = Modifier.testTag("cover_series_art_note")
    )
}

@Composable
private fun ViewerSourcePills(
    sources: List<CoverSource>,
    sourceIndex: Int,
    onSelectSource: (Int) -> Unit
) {
    // Which provider - and, inside btttr, which of its renders. Only drawn when
    // there is a choice: a title with no banner and no btttr art would otherwise get
    // a single pill that does nothing, which reads as a broken control.
    if (sources.size <= 1) return
    Spacer(modifier = Modifier.height(10.dp))
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        sources.forEachIndexed { index, candidate ->
            ViewerPill(
                label = candidate.label,
                selected = index == sourceIndex,
                modifier = Modifier.widthIn(min = 88.dp),
                onClick = { onSelectSource(index) }
            )
        }
    }
}

@Composable
private fun ViewerTitleChips(media: MediaNode) {
    val romaji = media.romajiName
    val english = media.englishName
    if (romaji.isNullOrBlank() && english.isNullOrBlank()) return

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

@Composable
private fun ViewerOutboundLinks(media: MediaNode, context: android.content.Context) {
    // Where the reader can look at more of this title's artwork. AniList is the
    // page this viewer was opened from; LiveChart is the only other site the app can
    // honestly send them to for the airing schedule. Its poster also appears up above
    // as a third image source - the owner reversed the earlier no-scrape rule (see
    // [FullScreenCoverViewer] and [com.anisequel.ui.components.LiveChart]) - but this
    // pill is still just the outbound link it has always been, to the search page.
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
}

/**
 * What one of btttr's renders has printed on it.
 *
 * The viewer names the page the reader is on, so the four renders cannot read as
 * four copies of the same picture that "Swipe for more art" does not explain.
 * Lives here rather than in the data layer because it is UI text about btttr's
 * overlay flags, and the save button borrows it for a file name.
 */
internal fun PosterOverlays.variantName(): String = when {
    showRating && showGenre -> "Full poster"
    showRating -> "Score only"
    showGenre -> "Genre only"
    else -> "Clean poster"
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
        Column(modifier = Modifier.padding(6.dp)) {
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
            // One line, always. Three equal-width pills on a 360dp phone leave
            // about 90dp each; "LiveChart" at this size is close to that, and a
            // wrapped pill made the row taller than the card it sits in the moment
            // the third source arrived - which read as the switch breaking when the
            // LiveChart cover finally loaded.
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
