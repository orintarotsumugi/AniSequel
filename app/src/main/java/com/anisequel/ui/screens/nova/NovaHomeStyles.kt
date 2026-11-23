package com.anisequel.ui.screens.nova

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anisequel.ui.components.cards.PosterPlaceholder
import coil.compose.AsyncImage
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.posterColor
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaSurface
import kotlin.math.abs

@Composable
internal fun NovaSequelBanner(
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
            .height(190.dp)
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .clip(RoundedCornerShape(22.dp)),
        tonalElevation = true
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize()) {
                // Same rule as the plain home card: an unknown dominant colour used to
                // leave nothing behind the artwork - see [PosterPlaceholder].
                PosterPlaceholder(
                    title = sequel.sequelTitle,
                    tint = sequel.sequelMedia.posterColor.toCoverColorOrNull(),
                    modifier = Modifier.matchParentSize()
                )
                AsyncImage(
                    model = sequel.sequelCoverUrl,
                    contentDescription = sequel.sequelTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.72f)
                    .align(Alignment.CenterStart)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                Text(
                    text = sequel.relationLabel.uppercase(java.util.Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primaryContainer
                )
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
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
            // A real IconButton with a 48dp target rather than a 30dp boxed
            // glyph: the close sits on artwork that is itself one big tap, so a
            // fingertip that overshoots by a few dp dismisses a recommendation
            // instead of opening it. The visual circle stays compact; the target
            // around it is the platform minimum.
            IconButton(
                onClick = onHide,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .size(48.dp)
                    .testTag("nova_sequel_banner_hide_${sequel.sequelId}")
            ) {
                // The target is 48dp; the dark disc stays 32dp, so the picture
                // is not wearing a larger close button than it did before.
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(ExpressiveShapes.pill)
                        .background(Color.Black.copy(alpha = 0.36f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Not interested",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
internal fun NovaSequelShelfCard(
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
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(width = 82.dp, height = 110.dp)
                    .clip(RoundedCornerShape(13.dp))
            ) {
                PosterPlaceholder(
                    title = sequel.sequelTitle,
                    tint = sequel.sequelMedia.posterColor.toCoverColorOrNull(),
                    modifier = Modifier.matchParentSize()
                )
                AsyncImage(
                    model = sequel.sequelCoverUrl,
                    contentDescription = sequel.sequelTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = sequel.relationLabel.uppercase(java.util.Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
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
            IconButton(
                onClick = onHide,
                modifier = Modifier
                    .size(48.dp)
                    .testTag("nova_sequel_shelf_hide_${sequel.sequelId}")
            ) {
                Icon(
                    imageVector = AppVectorIcons.Close,
                    contentDescription = "Not interested",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * A missed sequel drawn as an admission ticket.
 *
 * The perforated stub on the right is the whole idea: one torn-off end carrying the
 * only two things a ticket stub carries, so the artwork side can stay a single
 * reading column. Notches are cut *through* the card with [BlendMode.Clear] on an
 * offscreen layer, which is why they show the page behind rather than a circle of
 * some guessed background colour - the Home backdrop is a gradient, so any fill
 * value would be right at one scroll position and wrong at the next.
 */
@Composable
internal fun NovaSequelTicket(
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
    val perforation = MaterialTheme.colorScheme.outlineVariant
    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
            .drawWithContent {
                drawContent()
                val x = size.width - TicketStubWidth.toPx()
                val radius = 7.dp.toPx()
                drawCircle(Color.Black, radius, Offset(x, 0f), blendMode = BlendMode.Clear)
                drawCircle(Color.Black, radius, Offset(x, size.height), blendMode = BlendMode.Clear)
            }
            .clip(RoundedCornerShape(16.dp)),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = true
    ) {
        Row(modifier = Modifier.fillMaxWidth().height(132.dp)) {
            Box(
                modifier = Modifier
                    .padding(start = 12.dp, top = 12.dp, bottom = 12.dp)
                    .size(width = 76.dp, height = 108.dp)
                    .clip(RoundedCornerShape(10.dp))
            ) {
                PosterPlaceholder(
                    title = sequel.sequelTitle,
                    tint = sequel.sequelMedia.posterColor.toCoverColorOrNull(),
                    modifier = Modifier.matchParentSize()
                )
                AsyncImage(
                    model = sequel.sequelCoverUrl,
                    contentDescription = sequel.sequelTitle,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp, top = 14.dp, bottom = 12.dp)
            ) {
                Text(
                    text = sequel.relationLabel.uppercase(java.util.Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.weight(1f))
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
            Box(
                modifier = Modifier
                    .width(TicketStubWidth)
                    .fillMaxHeight(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.matchParentSize()) {
                    drawLine(
                        color = perforation,
                        start = Offset(1.dp.toPx(), 0f),
                        end = Offset(1.dp.toPx(), size.height),
                        strokeWidth = 2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(
                            floatArrayOf(6.dp.toPx(), 6.dp.toPx())
                        )
                    )
                }
                IconButton(
                    onClick = onHide,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nova_sequel_ticket_hide_${sequel.sequelId}")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Not interested",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** Width of the torn-off end of a [NovaSequelTicket]. */
private val TicketStubWidth = 56.dp

/** The small tilts a print is pinned at. Indexed by the sequel so it never jitters. */
private val PolaroidTilts = listOf(-1.4f, 0.9f, -0.6f)

/**
 * A missed sequel drawn as an instant print.
 *
 * The print is the white frame with its caption written into the thick bottom
 * margin - the one shape everyone recognises as a Polaroid - and it lies on the
 * card's own surface rather than filling it. The quick-add control stays on that
 * surface, not on the paper: it is drawn in theme colours, and a light-theme
 * button on cream is a control nobody can find in dark mode.
 *
 * The tilt is derived from the sequel's ID, so a given row always leans the same
 * way. A random angle would look like the prints had been tossed every time the
 * list recomposed, which is precisely the fidgeting the style switch is meant to
 * stop doing.
 */
@Composable
internal fun NovaSequelPolaroid(
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
    val paper = Color(0xFFF7F3EA)
    val ink = Color(0xFF23211F)
    val tilt = remember(sequel.sequelId) {
        PolaroidTilts[abs(sequel.sequelId.hashCode()) % PolaroidTilts.size]
    }
    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer { rotationZ = tilt }
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = true
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(4.dp))
                    .background(paper)
                    .padding(8.dp)
            ) {
                Box {
                    AsyncImage(
                        model = sequel.sequelCoverUrl,
                        contentDescription = sequel.sequelTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.76f)
                            .clip(RoundedCornerShape(2.dp))
                            .background(
                                sequel.sequelMedia.posterColor.toCoverColorOrNull()
                                    ?: Color(0xFFD9D3C7)
                            )
                    )
                    IconButton(
                        onClick = onHide,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .size(40.dp)
                            .testTag("nova_sequel_polaroid_hide_${sequel.sequelId}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(ExpressiveShapes.pill)
                                .background(Color.Black.copy(alpha = 0.38f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Close,
                                contentDescription = "Not interested",
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = sequel.sequelTitle,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = ink,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = sequel.relationLabel.uppercase(java.util.Locale.ROOT),
                    style = MaterialTheme.typography.labelSmall,
                    color = ink.copy(alpha = 0.55f),
                    letterSpacing = 1.1.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
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

/**
 * A missed sequel drawn as the top card of a stack of prints.
 *
 * The two sheets behind it are static shapes, not animation: a deck that fanned
 * itself on every recomposition would be the fidgeting the style switch exists to
 * stop, and the reader would learn nothing from the movement. They are offset down
 * and to the right so the pile reads at a glance, and the front card is the usual
 * wide artwork with the title over a scrim - the same information as a banner, at
 * two thirds of its height, because the pile has already said "there is more here"
 * without spending a row on it.
 *
 * The quick-add control sits on the card's own surface rather than on the artwork,
 * for the reason the Polaroid records: it is drawn in theme colours, and a
 * theme-coloured button over a photograph is a control that disappears in one of
 * the two themes.
 */
@Composable
internal fun NovaSequelDeck(
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
    Box(modifier = modifier.fillMaxWidth()) {
        // `matchParentSize` rather than a fixed height: the Box is sized by the
        // front card, so the two sheets cannot disagree with it about the pile's
        // size when the title wraps to a second line.
        NovaSurface(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 20.dp, top = 20.dp)
                .graphicsLayer { alpha = 0.35f },
            shape = RoundedCornerShape(20.dp)
        ) {}
        NovaSurface(
            modifier = Modifier
                .matchParentSize()
                .padding(start = 10.dp, top = 10.dp)
                .graphicsLayer { alpha = 0.6f },
            shape = RoundedCornerShape(20.dp)
        ) {}
        NovaSurface(
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
                .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
                .clip(RoundedCornerShape(20.dp)),
            shape = RoundedCornerShape(20.dp),
            tonalElevation = true
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.fillMaxWidth().height(150.dp)) {
                    PosterPlaceholder(
                        title = sequel.sequelTitle,
                        tint = sequel.sequelMedia.posterColor.toCoverColorOrNull(),
                        modifier = Modifier.matchParentSize()
                    )
                    AsyncImage(
                        model = sequel.sequelCoverUrl,
                        contentDescription = sequel.sequelTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.88f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth(0.78f)
                            .padding(start = 14.dp, end = 14.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Text(
                            text = sequel.relationLabel.uppercase(java.util.Locale.ROOT),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primaryContainer
                        )
                        Text(
                            text = sequel.sequelTitle,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(
                        onClick = onHide,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(6.dp)
                            .size(48.dp)
                            .testTag("nova_sequel_deck_hide_${sequel.sequelId}")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(30.dp)
                                .clip(ExpressiveShapes.pill)
                                .background(Color.Black.copy(alpha = 0.38f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Close,
                                contentDescription = "Not interested",
                                tint = Color.White,
                                modifier = Modifier.size(17.dp)
                            )
                        }
                    }
                }
                QuickAddPlanningButton(
                    mediaId = sequel.sequelId,
                    isAddedToPlanning = sequel.isAddedToPlanning,
                    isAddedToWatching = sequel.isAddedToWatching,
                    isBusy = sequel.isSavingToList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                )
            }
        }
    }
}

/** Width of one sprocket gutter of a [NovaSequelFilmstrip]. */
private val FilmSprocketWidth = 14.dp

/**
 * A missed sequel drawn as one frame of a film strip.
 *
 * The strip is black in both themes on purpose, the way the Polaroid's paper is
 * cream in both: a film strip is a physical object, and a Material-tinted one reads
 * as a card with dots on it. The sprocket holes are drawn rather than shipped as an
 * asset - they are four rectangles in a loop, and the app has no PNGs.
 *
 * The frame is the artwork and the caption over it; the actions are a strip below,
 * because a film frame has no room for a button without covering the picture.
 */
@Composable
internal fun NovaSequelFilmstrip(
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
    val film = Color(0xFF16151A)
    val frameInk = Color(0xFFF4F1EA)
    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            .bouncyPress(pressedScale = 0.985f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, indication = null, onClick = onOpen)
            .clip(RoundedCornerShape(14.dp)),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = true
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .background(film)
            ) {
                FilmSprockets()
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .padding(vertical = 7.dp)
                        .clip(RoundedCornerShape(5.dp))
                ) {
                    PosterPlaceholder(
                        title = sequel.sequelTitle,
                        tint = sequel.sequelMedia.posterColor.toCoverColorOrNull(),
                        modifier = Modifier.matchParentSize()
                    )
                    AsyncImage(
                        model = sequel.sequelCoverUrl,
                        contentDescription = sequel.sequelTitle,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(Color.Black.copy(alpha = 0.86f), Color.Transparent)
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .fillMaxWidth(0.8f)
                            .padding(start = 12.dp, end = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = sequel.relationLabel.uppercase(java.util.Locale.ROOT),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = frameInk.copy(alpha = 0.72f),
                            letterSpacing = 1.4.sp
                        )
                        Text(
                            text = sequel.sequelTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = frameInk,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                FilmSprockets()
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickAddPlanningButton(
                    mediaId = sequel.sequelId,
                    isAddedToPlanning = sequel.isAddedToPlanning,
                    isAddedToWatching = sequel.isAddedToWatching,
                    isBusy = sequel.isSavingToList,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    modifier = Modifier.weight(1f)
                )
                IconButton(
                    onClick = onHide,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("nova_sequel_filmstrip_hide_${sequel.sequelId}")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Not interested",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

/** One gutter of perforations down the side of a film-strip frame. */
@Composable
private fun FilmSprockets(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.width(FilmSprocketWidth).fillMaxHeight()) {
        val holeWidth = size.width * 0.52f
        val holeHeight = 6.dp.toPx()
        val gap = 9.dp.toPx()
        val radius = CornerRadius(1.5.dp.toPx())
        var top = gap
        // Drawn to the frame's own height rather than a fixed count: the frame is
        // 126dp today, and a hard-coded six holes would leave a gap at the bottom
        // the first time that number moved.
        while (top + holeHeight < size.height - gap) {
            drawRoundRect(
                color = Color.White.copy(alpha = 0.20f),
                topLeft = Offset((size.width - holeWidth) / 2f, top),
                size = Size(holeWidth, holeHeight),
                cornerRadius = radius
            )
            top += holeHeight + gap
        }
    }
}
