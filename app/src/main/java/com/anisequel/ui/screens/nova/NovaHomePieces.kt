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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
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
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaProgressPill
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.navigation.mainBarClearance

/**
 * The pieces the new Home is assembled from.
 *
 * Kept apart from [NovaHomeScreen] because they are also the vocabulary the
 * Other hub and the Settings preview draw with: a shelf, a poster rail, a
 * shortcut card and a compact sequel row. The screen file then reads as the order
 * of its sections, which is the part a reader actually changes.
 */

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
    // AniList leaves `progress` null on a completed entry, which is the common
    // case rather than an edge: the rail therefore dropped its badge exactly when
    // a title had been finished. The total is the honest number to show, and the
    // library grid already falls back to it - this rail is the other surface that
    // reads the same list, so it must not disagree with the shelf behind it.
    val completed = entry.status.equals("COMPLETED", ignoreCase = true)
    val progress = entry.progress ?: if (completed) total else null
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .width(118.dp)
            .bouncyPress(pressedScale = 0.96f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                role = Role.Button,
                onClickLabel = "Open ${media.displayName}",
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
                // The caption below names the card; a description on the artwork
                // too made TalkBack read every title twice in the rail.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (progress != null) {
                NovaProgressPill(
                    progress = progress,
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
 * One missed sequel as a poster card: the same row, with the artwork given the
 * room it needs to be recognised.
 *
 * The second of the two shapes [com.anisequel.domain.ui.HomeSequelStyle] offers.
 * Everything it draws is the row's own vocabulary - the relation label, the
 * title, the compact metadata line, the shared planning button and the hide
 * cross - so switching between them changes the picture and not the behaviour.
 * The cover is the only part that is genuinely new, and it is drawn from the
 * URL the row already had; no request is made for this layout either.
 */
@Composable
fun NovaSequelCard(
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
            .testTag("nova_sequel_card_${sequel.sequelId}"),
        tonalElevation = true
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .background(
                        sequel.sequelMedia.posterColor.toCoverColorOrNull() ?: Color.Transparent
                    )
            ) {
                AsyncImage(
                    model = sequel.sequelCoverUrl,
                    contentDescription = sequel.sequelTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = ExpressiveShapes.pill,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.94f),
                        // The one label on the artwork, and the reason the card
                        // can say "this is a sequel to a show you finished"
                        // without a second column of text.
                        modifier = Modifier.testTag("nova_sequel_card_relation")
                    ) {
                        Text(
                            text = sequel.relationLabel.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            maxLines = 1,
                            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    IconButton(
                        onClick = onHide,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(
                                MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f)
                            )
                            .bouncyPress(pressedScale = 0.88f)
                            .testTag("nova_sequel_card_hide_${sequel.sequelId}")
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Close,
                            contentDescription = "Not interested",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 12.dp)
            ) {
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
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
                Spacer(modifier = Modifier.height(10.dp))
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
 * What the new Home draws while its first answer is in flight.
 *
 * The classic dashboard's skeleton is built from the classic page's sections -
 * a stats banner, a search field, five sequel cards - and drawing it under the
 * new look put the new bar over a page the reader had not chosen: the loading
 * state was the only screen in the app that ignored the layout setting. This
 * mirrors the shelf instead: the centred greeting, the two shortcut cards, the
 * continue rail and the rows below.
 *
 * One shimmer clock for the whole page, and no scrolling - there is nothing to
 * scroll to yet, and a skeleton that can be dragged implies content behind it.
 */
@Composable
fun NovaHomeSkeleton(modifier: Modifier = Modifier) {
    val phase = rememberShimmerPhase()
    val maxWidth = 640.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("nova_home_skeleton"),
        contentPadding = PaddingValues(top = 8.dp, bottom = mainBarClearance() + 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        userScrollEnabled = false
    ) {
        item(key = "skeleton_greeting") {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.58f)
                        .height(28.dp),
                    phase = phase
                )
                Spacer(modifier = Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.44f)
                        .height(24.dp),
                    phase = phase
                )
                Spacer(modifier = Modifier.height(10.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.86f)
                        .height(14.dp),
                    phase = phase
                )
            }
        }

        item(key = "skeleton_shortcuts") {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(2) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(NovaShortcutHeight),
                            shape = RoundedCornerShape(26.dp),
                            phase = phase
                        )
                    }
                }
                // The Other door is part of the same block, so the skeleton must
                // reserve it too - a two-row skeleton put the Continue rail 76dp
                // too high and it jumped down when the real content arrived.
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(NovaOtherShortcutHeight),
                    shape = RoundedCornerShape(26.dp),
                    phase = phase
                )
            }
        }

        item(key = "skeleton_rail") {
            Column(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
            ) {
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.42f)
                        .height(20.dp),
                    phase = phase
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    repeat(3) {
                        ShimmerBox(
                            modifier = Modifier
                                .weight(1f)
                                .height(150.dp),
                            shape = RoundedCornerShape(18.dp),
                            phase = phase
                        )
                    }
                }
            }
        }

        items(count = 3, key = { "skeleton_row_$it" }) {
            ShimmerBox(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .height(104.dp),
                shape = RoundedCornerShape(26.dp),
                phase = phase
            )
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
