package com.anisequel.ui.screens.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.displayName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaProgressPill
import com.anisequel.ui.components.nova.NovaSurface

/**
 * The pieces the new Home is assembled from.
 *
 * Kept apart from [NovaHomeScreen] because they are also the vocabulary the
 * Other hub and the Settings preview draw with: a shelf, a poster rail, a
 * shortcut card and a compact sequel row. The screen file then reads as the order
 * of its sections, which is the part a reader actually changes.
 */

/** The greeting, and the one sentence that says what this page is for. */
@Composable
fun NovaGreeting(
    viewerName: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp)
    ) {
        Text(
            text = "Hey $viewerName,",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "what are we doing today?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Find your favourite anime or manga, manhwa or whatever you like!",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One of the three doors on Home: a count, a name, and the art that belongs to it.
 *
 * The cover is one the dashboard already holds - the anime from `cardPreview`,
 * the manga from its one-row preview - so the card itself costs no request. One
 * image at the right edge, not a two-slice collage: at 44dp a pair of posters
 * reads as a clipping bug rather than as a stack.
 */
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
                onClick = onClick
            ),
        tonalElevation = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(104.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 14.dp, end = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = meta,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // One cover, not the collage the first draft stacked. Two slices of
            // two different posters read as a rendering mistake at 44dp, and the
            // card is a door to a list - the count and the label are the content.
            if (coverUrl != null) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight()
                )
            } else {
                Box(
                    modifier = Modifier
                        .width(52.dp)
                        .fillMaxHeight()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    Color.Transparent
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}

/**
 * The full-width "Other" door, with its sparkle.
 *
 * Not a [NovaShortcutCard] with a different size: it goes somewhere that is not a
 * list, so it should not look like the two cards that do. The quieter surface is
 * the distinction.
 */
@Composable
fun NovaOtherShortcut(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    NovaSurface(
        modifier = modifier
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.AnimeSparkle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "OTHER",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Calendar, tier lists and more",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Icon(
                imageVector = AppVectorIcons.ExpandMore,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

/**
 * A horizontal poster rail of the entries the viewer is currently on.
 *
 * The progress capsule sits on the cover's top-left, which is the corner AniList's
 * own art leaves free; the title is below rather than over the image, because a
 * cover is the series' own typography and a caption on top of it is unreadable.
 */
@Composable
fun NovaContinueRail(
    title: String,
    entries: List<MediaListEntryItem>,
    onOpen: (MediaListEntryItem) -> Unit,
    modifier: Modifier = Modifier
) {
    if (entries.isEmpty()) return

    Column(modifier = modifier.fillMaxWidth()) {
        com.anisequel.ui.components.nova.NovaSectionHeading(
            title = title,
            count = entries.size,
            modifier = Modifier.padding(horizontal = 20.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(
                items = entries,
                key = { entry -> entry.media.id },
                contentType = { "continue_card" }
            ) { entry ->
                NovaContinueCard(entry = entry, onOpen = { onOpen(entry) })
            }
        }
    }
}

@Composable
private fun NovaContinueCard(
    entry: MediaListEntryItem,
    onOpen: () -> Unit
) {
    val media = entry.media
    val isManga = media.isManga
    val total = if (isManga) media.chapters else media.episodes
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(118.dp)
            .bouncyPress(pressedScale = 0.96f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen
            )
            .testTag("nova_continue_card_${media.id}")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(158.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(media.coverImage?.color.toCoverColorOrNull() ?: Color.Transparent)
        ) {
            AsyncImage(
                model = media.posterUrl,
                contentDescription = media.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (entry.progress != null) {
                NovaProgressPill(
                    progress = entry.progress,
                    total = total,
                    isManga = isManga,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = media.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * One missed sequel, as a compact row on the new Home.
 *
 * The planning control is the shared [QuickAddPlanningButton], which is how the
 * tick/cross confirmation reaches this layout without a second implementation -
 * and why the setting switches both looks at once.
 */
@Composable
fun NovaSequelRow(
    sequel: MissedSequel,
    onOpen: () -> Unit,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit,
    onHide: () -> Unit,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }

    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onOpen
            )
            .testTag("nova_sequel_row_${sequel.sequelId}"),
        tonalElevation = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(sequel.sequelMedia.posterColor.toCoverColorOrNull() ?: Color.Transparent)
            ) {
                AsyncImage(
                    model = sequel.sequelCoverUrl,
                    contentDescription = sequel.sequelTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = sequel.relationLabel.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = onHide,
                        modifier = Modifier
                            .size(28.dp)
                            .bouncyPress(pressedScale = 0.88f)
                            .testTag("nova_sequel_hide_${sequel.sequelId}")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Close,
                            contentDescription = "Not interested",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        sequel.format.takeIf { it.isNotBlank() },
                        sequel.episodes.takeIf { it.isNotBlank() && it != "-" },
                        sequel.score.takeIf { it.isNotBlank() }
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                QuickAddPlanningButton(
                    mediaId = sequel.sequelId,
                    isAddedToPlanning = sequel.isAddedToPlanning,
                    isAddedToWatching = sequel.isAddedToWatching,
                    isBusy = sequel.isSavingToList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/**
 * The search field and the one filter control above a new-layout list.
 *
 * A field that is always there rather than a search icon that swaps the header:
 * typing is how a reader finds a half-remembered title, and hiding it behind a
 * tap made the page feel like it was saving space it had.
 */
@Composable
fun NovaSearchRow(
    query: String,
    onQueryChange: (String) -> Unit,
    onOpenFilter: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "Search your list"
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .weight(1f)
                .widthIn(min = 0.dp)
                .testTag("nova_search_input"),
            placeholder = {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            singleLine = true,
            shape = ExpressiveShapes.pill,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
            ),
            leadingIcon = {
                Icon(
                    imageVector = AppVectorIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(19.dp)
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(
                            imageVector = AppVectorIcons.Close,
                            contentDescription = "Clear search",
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        )

        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier
                .size(48.dp)
                .bouncyPress(pressedScale = 0.9f)
                .testTag("nova_filter_button")
        ) {
            IconButton(onClick = onOpenFilter) {
                Icon(
                    imageVector = AppVectorIcons.Filter,
                    contentDescription = "Filter and sort",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** A quiet sentence where a list would be, with no fake cards around it. */
@Composable
fun NovaEmptyNote(
    text: String,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
