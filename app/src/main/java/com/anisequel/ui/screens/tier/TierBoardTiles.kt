package com.anisequel.ui.screens.tier

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.TierItemBox
import com.anisequel.domain.tier.TierRowBox
import com.anisequel.domain.tier.TierTitlePosition
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The things the board draws: a tier's row, a poster, the drop caret and the
 * poster under the finger.
 *
 * Split out of `TierBoard.kt` when the board's drag maths and its drawing grew
 * past the file limit together. The split is by responsibility: this file is
 * paint, the board is gestures and geometry, and a change to one rarely touches
 * the other.
 */

/**
 * A tier row's fill: its colour, mixed into the surface rather than painted over
 * it.
 *
 * The row used to be the tier colour at 0.92 alpha, which on six rows is a
 * screen of six saturated bars - legible as a *diagram* and unreadable as a
 * list of posters, because every cover sat on a different coloured field. The
 * colour is an identity, not the background: it now tints the surface, and the
 * solid colour is left to the label gutter where it is the only thing on screen.
 */
@Composable
internal fun tierRowFill(colorArgb: Long, highlighted: Boolean): Color {
    val base = if (highlighted) {
        MaterialTheme.colorScheme.surfaceContainerHigh
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    // A stronger tint while a poster is over the row: the row has to say "it
    // will land here" from under the finger that is covering half of it.
    return lerp(base, tierRowColor(colorArgb), if (highlighted) 0.26f else 0.14f)
}

/**
 * A tier's row: the solid colour gutter with its label, and a tinted body.
 *
 * The label is vertically centred for the same reason the SVG writer centres it:
 * a one-item row and a four-item row are different heights, and a top-aligned
 * label makes the short one read as a section header rather than as the same
 * control with less in it.
 *
 * The count under the label is the number the row holds. It is there because a
 * row of posters is countable by eye only up to about five, and the question
 * "how many are in S" is one people ask of their own list.
 *
 * An empty row says "Drop titles here" instead of being a coloured void: it is
 * a drop target, and nothing else on the board announces that. The text sits
 * inside the poster area rather than in the gutter so it does not compete with
 * the label.
 *
 * The highlight animates colour and border width, never the row's size. Growing
 * a row while a tile is being dropped onto it would reflow every other tile in
 * that row under the finger holding the tile.
 */
@Composable
internal fun TierRowStrip(
    row: TierRowBox,
    labelGutter: Float,
    memberCount: Int,
    highlighted: Boolean
) {
    val tierColor = tierRowColor(row.colorArgb)
    val shape = RoundedCornerShape(ExpressiveShapes.MediumCorner)

    val fill by animateColorAsState(
        targetValue = tierRowFill(row.colorArgb, highlighted),
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "tier_row_fill"
    )
    val borderColor by animateColorAsState(
        targetValue = if (highlighted) {
            tierColor
        } else {
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "tier_row_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (highlighted) 2.dp else 1.dp,
        animationSpec = ExpressiveMotion.FastSpatialDp,
        label = "tier_row_border_width"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(fill)
            .border(borderWidth, borderColor, shape)
    ) {
        Box(
            modifier = Modifier
                .width(labelGutter.dp)
                .fillMaxHeight()
                .background(tierColor),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(horizontal = 2.dp)
            ) {
                Text(
                    text = row.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (memberCount > 0) {
                    Text(
                        text = memberCount.toString(),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.82f),
                        maxLines = 1
                    )
                }
            }
        }

        if (memberCount == 0) {
            Text(
                text = "Drop titles here",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = (labelGutter + 14f).dp, end = 12.dp)
            )
        }
    }
}

/**
 * The bar that shows where a dropped tile will land.
 *
 * The row highlight says *which* row; this says *where in it*, which is the
 * difference the positional drop introduced - dropping onto the third poster now
 * means "third", and without a mark at that seam the tile would appear somewhere
 * in the row with nothing having said where. It is a capsule rather than a
 * rectangle for the same reason every other bar in this app is: `toShape()` on a
 * stretched polygon draws an ellipse.
 */
@Composable
internal fun TierDropCaret() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.primary)
            .testTag("tier_drop_caret")
    )
}

/**
 * One poster, its caption, and - when there is one - its AniList score.
 *
 * The caption sits *under* the poster by default. `FavouriteMediaRow` records why
 * this app already tried the other way: AniList covers carry the series' own
 * typography, frequently vertical and running to the edges, and a caption over
 * that is unreadable. The board's display options offer the other placement
 * anyway, because a wall of covers with no names is a memory test; when it is on,
 * the name is drawn over a scrim that fades into the artwork rather than over the
 * cover's own lettering. The score badge moves to the opposite corner in that
 * case, so the two never overlap.
 *
 * The score badge otherwise stays on the cover because a two-digit number in the
 * top-start corner lands on padding that is empty on almost every poster, which is
 * the same reason the Library page's progress badge survives in the same corner.
 *
 * `defaultMinSize` is deliberately absent here. Every tile is measured with
 * `Constraints.fixed` at `itemWidth`, which is already above 48dp, so a minimum
 * would be a no-op that reads like a guarantee it is not enforcing - the real
 * floor is `ScreenTierOptions.itemWidth`, and it is the thing to change if it
 * ever needs to move.
 */
@Composable
internal fun TierPosterTile(
    candidate: TierCandidate?,
    width: Float,
    posterHeight: Float,
    captionHeight: Float,
    showTitle: Boolean,
    titlePosition: TierTitlePosition,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val placeholder = rememberVectorPainter(AppVectorIcons.List)
    val shape = RoundedCornerShape(ExpressiveShapes.SmallCorner)
    val titleOnCover = showTitle && titlePosition == TierTitlePosition.ON_COVER
    val titleUnderCover = showTitle && titlePosition == TierTitlePosition.UNDER_COVER

    Column(
        modifier = modifier
            .width(width.dp)
            // Before the clickable, sharing one source: sharing is what cancels
            // the press spring the moment the gesture turns into a scroll instead
            // of leaving the tile squashed under a finger that is scrolling.
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onTap)
            .testTag("tier_tile")
    ) {
        Box(
            modifier = Modifier
                .width(width.dp)
                .height(posterHeight.dp)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            AsyncImage(
                model = candidate?.coverUrl,
                contentDescription = candidate?.title,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = placeholder,
                error = placeholder,
                fallback = placeholder
            )

            // AniList answers 0 as readily as null for "nobody has scored this",
            // and a badge reading "0" claims a title is terrible rather than
            // unrated.
            val score = candidate?.averageScore
            if (score != null && score > 0) {
                Text(
                    text = score.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        // With the name across the top, the score moves to the
                        // bottom: two badges in one corner would overlap, and the
                        // name is the thing being read.
                        .align(if (titleOnCover) Alignment.BottomStart else Alignment.TopStart)
                        .padding(5.dp)
                        .clip(ExpressiveShapes.pill)
                        .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.66f))
                        .padding(horizontal = 7.dp, vertical = 2.dp)
                )
            }

            if (titleOnCover) {
                // A scrim rather than a solid strip: covers are art, and a band of
                // paint across the top of every one turns a board into a table.
                // The gradient fades into the artwork so the title has a readable
                // background without a visible edge.
                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    Color.Black.copy(alpha = 0.78f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(horizontal = 5.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = candidate?.title.orEmpty(),
                        style = MaterialTheme.typography.labelSmall.copy(lineHeight = 13.sp),
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tier_tile_title_on_cover")
                    )
                }
            }
        }

        if (titleUnderCover) {
            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = candidate?.title.orEmpty(),
                // A tighter line height than `labelSmall` ships with: two lines
                // have to fit the band the layout reserved, and a clipped second
                // line reads as a missing title rather than as a long one.
                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(width.dp)
                    .height((captionHeight - 2f).dp)
            )
        }
    }
}

/**
 * The poster under the finger, lifted clear of the board.
 *
 * Both the translation and the lift are read inside `graphicsLayer`, so a drag
 * invalidates the draw pass alone. Offsetting the node during layout instead would
 * re-measure and re-place the whole board on every pointer move, which is what
 * makes a drag inside a scrolling list stutter.
 *
 * Drawn as a sibling *after* `TierGeometry` so it lands on top. A `Layout` draws
 * its children in the order it emitted them and has no z-index, so the only way to
 * put the dragged tile above the row it is hovering over is not to emit it there.
 *
 * The dimensions handed in are already multiplied by `zoom`: the ghost has to be
 * the same size on screen as the slot it came out of, or releasing it lands the
 * finger on a different part of the tile than the one they picked up.
 *
 * [scrollShift] is the second half of that promise. The board moves when
 * auto-scroll runs and the finger does not, so a ghost translated by the drag
 * alone would slide away from the finger by exactly the distance the list had
 * scrolled - at the moment the person is dragging the furthest and can see the
 * least.
 */
@Composable
internal fun TierDragGhost(
    box: TierItemBox,
    candidate: TierCandidate?,
    zoom: Float,
    posterHeight: Float,
    captionHeight: Float,
    showTitle: Boolean,
    titlePosition: TierTitlePosition,
    travel: State<Offset>,
    scrollShift: () -> Float
) {
    val lift by animateFloatAsState(
        targetValue = 1.08f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "tier_drag_lift"
    )
    // Hoisted rather than built inside the layer block: that block runs on every
    // frame of the drag, and a fresh shape per frame is an allocation on the one
    // path that has to stay cheap.
    val ghostShape = remember { RoundedCornerShape(ExpressiveShapes.SmallCorner) }

    TierPosterTile(
        candidate = candidate,
        width = box.width * zoom,
        posterHeight = posterHeight,
        captionHeight = captionHeight,
        showTitle = showTitle,
        titlePosition = titlePosition,
        onTap = {},
        modifier = Modifier
            .offset(x = (box.x * zoom).dp, y = (box.y * zoom).dp)
            // `travel.value` is read *inside* the layer block, which is what keeps
            // a pointer move down to a draw invalidation instead of a rebuild of
            // this modifier chain every frame. The shadow is what separates the
            // carried tile from the row it is over; without it the ghost is the
            // same flat rectangle as the tile it left behind.
            .graphicsLayer {
                translationX = travel.value.x
                translationY = travel.value.y + scrollShift()
                scaleX = lift
                scaleY = lift
                shadowElevation = 18f
                shape = ghostShape
            }
            .testTag("tier_drag_ghost")
    )
}
