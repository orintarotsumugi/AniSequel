package com.anisequel.ui.screens.tier

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.domain.tier.TierCandidate
import com.anisequel.domain.tier.unseedableCount
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.theme.AniSequelTheme

/**
 * The screen's chrome: the header, the notices, the empty states and the legend.
 *
 * The board file is gestures and geometry; these are the things above and below
 * it that say what the board is showing. They were in the screen file, which was
 * doing three jobs at once. The floating undo/export/zoom toolbar moved to
 * `TierBoardToolbar.kt` when the lock and export actions pushed this file past
 * the length one reviewer can hold in a pass.
 */

/**
 * How far through the library this list is, and the way to add more.
 *
 * The count was a plain line of text ("12 of 271 titles ranked") with no shape
 * to it. A ranking is a quantity of work, and the bar is what makes "almost
 * done" and "just started" different at a glance instead of on a second read.
 *
 * "Add" is here rather than only in the tray because the tray is the *pool* of
 * unranked titles and it scrolls sideways: with 200 of them, the title you want
 * is somewhere off-screen, and a picker with a search field is the honest way
 * to find it. This is a second route to the same sheet the empty state opens -
 * the drag is still the fast path and is still the one the legend teaches.
 */
@Composable
internal fun TierBoardHeader(
    ranked: Int,
    total: Int,
    unranked: Int,
    undoLabel: String?,
    editable: Boolean = true,
    onAddTitles: () -> Unit
) {
    val fraction = if (total > 0) (ranked.toFloat() / total).coerceIn(0f, 1f) else 0f
    val progress by animateFloatAsState(
        targetValue = fraction,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "tier_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        total > 0 -> "$ranked of $total ranked"
                        // The library arrives after the document does, so a saved
                        // list can be on screen with nothing to draw it from yet.
                        // "Nothing to rank yet" would contradict the rows below.
                        ranked > 0 -> "$ranked ranked"
                        else -> "Nothing to rank yet"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    // The undo label takes the subtitle slot rather than a row of
                    // its own: it describes the edit the board is currently
                    // showing, which is the same kind of "where am I" fact as the
                    // unranked count, and a second line under the header was a
                    // strip of chrome that pushed the first tier off screen.
                    text = when {
                        undoLabel != null -> "Last: $undoLabel"
                        total == 0 -> "Your library has not loaded"
                        unranked == 0 -> "Every title has a tier"
                        unranked == 1 -> "1 title still unranked"
                        else -> "$unranked titles still unranked"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            AddTitlesPill(onClick = onAddTitles, enabled = total > 0 && editable)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Drawn rather than a `LinearProgressIndicator`: the indicator's own
        // track and indicator both have to be restyled to the theme's capsule
        // anyway, and this is two boxes with one animated fraction.
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .testTag("tier_progress_track")
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer {
                        // Read in the draw phase: `fillMaxWidth(progress)` was a
                        // layout-affecting read, so every frame of the bar's
                        // arrival re-measured and re-placed the child. Scaling a
                        // full-width capsule from the leading edge draws the same
                        // pixels without a layout pass, which is the rule every
                        // other transform in the app follows.
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = progress.coerceIn(0f, 1f)
                    }
                    .clip(ExpressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.primary)
            )
        }
    }
}

/** The compact "add titles" action in the header. */
@Composable
private fun AddTitlesPill(onClick: () -> Unit, enabled: Boolean) {
    val container by animateColorAsState(
        targetValue = if (enabled) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "tier_add_container"
    )
    val content = if (enabled) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .defaultMinSize(minHeight = 48.dp)
            .clip(ExpressiveShapes.pill)
            .background(container)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 14.dp)
            .testTag("tier_add_button"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.NewList,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Add",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = content
        )
    }
}

/**
 * One line of plain reporting, with a close only when there is something to
 * close.
 *
 * The orphan notice has no dismiss because it is not a message that was sent -
 * it is the current state of the data, and it goes away by itself when the
 * person places those titles.
 */
@Composable
internal fun TierNotice(text: String, onDismiss: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(ExpressiveShapes.SmallCorner))
            .background(AniSequelTheme.statusColors.infoContainer)
            .padding(start = 12.dp, end = if (onDismiss == null) 12.dp else 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.Info,
            contentDescription = null,
            tint = AniSequelTheme.statusColors.onInfoContainer,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = AniSequelTheme.statusColors.onInfoContainer,
            modifier = Modifier.weight(1f)
        )
        if (onDismiss != null) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = AppVectorIcons.Close,
                    contentDescription = "Dismiss",
                    tint = AniSequelTheme.statusColors.onInfoContainer
                )
            }
        }
    }
}

/**
 * "N hidden by filters", with the way back into the filter sheet.
 *
 * State rather than a message: it has no dismiss, because dismissing it would not
 * change what is hidden. It goes away when the filters do.
 */
@Composable
internal fun TierFilterNotice(count: Int, onOpenFilters: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(ExpressiveShapes.SmallCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = AppVectorIcons.Filter,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = "$count " + (if (count == 1) "title is" else "titles are") +
                " hidden by filters",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onOpenFilters,
            modifier = Modifier
                .defaultMinSize(minHeight = 48.dp)
                .testTag("tier_filter_notice_button")
        ) {
            Text("Filters", fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * The three lines the board can show above its rows.
 *
 * One composable rather than three blocks in the screen for the same reason the
 * sheets moved into a host: each notice is a dismissible message with its own open
 * condition, and keeping them here lets the screen's composition root stay about
 * layout. The filter notice is state, not a message - it has no dismiss because
 * dismissing it would not change what is hidden - while the orphan notice stays
 * until those placements are made again.
 */
@Composable
internal fun TierBoardNotices(
    hiddenTotal: Int,
    assignmentEmpty: Boolean,
    visibleAssignmentEmpty: Boolean,
    onOpenFilters: () -> Unit,
    notice: String?,
    onDismissNotice: () -> Unit,
    orphaned: Int
) {
    // While every placement is filtered out the empty state below already says so,
    // and two notices stacked over it is one explanation too many. A filtered
    // assignment is only worth a notice when something *else* is still drawn.
    if (hiddenTotal > 0 && (!visibleAssignmentEmpty || assignmentEmpty)) {
        TierFilterNotice(count = hiddenTotal, onOpenFilters = onOpenFilters)
    }

    // Both messages are `AnimatedContent` rather than a nullable row: the outgoing
    // text is kept alive while it fades and the container animates its height, so
    // dismissing a notice shrinks the message it was showing instead of collapsing
    // an empty box.
    AnimatedContent(
        targetState = notice,
        transitionSpec = {
            fadeIn(ExpressiveMotion.FastEffects) togetherWith
                fadeOut(ExpressiveMotion.FastEffects)
        },
        label = "tier_notice"
    ) { message ->
        if (message != null) {
            TierNotice(text = message, onDismiss = onDismissNotice)
        }
    }

    // The ViewModel already drops orphaned entries from the displayed assignment,
    // so saying so is what stops the missing posters reading as data loss.
    if (orphaned > 0) {
        TierNotice(
            text = "$orphaned " +
                (if (orphaned == 1) "title was" else "titles were") +
                " filed under a tier that no longer exists. They are not shown" +
                " until you place them again.",
            onDismiss = null
        )
    }
}

/**
 * The board when every ranked title is hidden by the current filters.
 *
 * Distinct from [TierEmptyState]: "you have ranked nothing" and "you have hidden
 * everything you ranked" are different problems with different fixes, and showing
 * the seeding button for the second one would invite a person to overwrite a list
 * they already built.
 */
@Composable
internal fun TierFilteredEmptyState(
    hiddenCount: Int,
    onOpenFilters: () -> Unit,
    onShowAll: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.Filter,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Everything is filtered out",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "$hiddenCount " + (if (hiddenCount == 1) "title is" else "titles are") +
                " hidden by the current filters. Nothing has been removed from your list.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(20.dp))
        ExpressivePrimaryButton(
            text = "Change filters",
            onClick = onOpenFilters,
            icon = AppVectorIcons.Filter,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        ExpressiveStateChip(
            label = "Show everything",
            selected = false,
            onClick = onShowAll,
            minTouchTargetHeight = 48.dp
        )
    }
}

/**
 * The state a list with nothing in it is in, offering both ways out of it.
 *
 * The unscored count is stated *before* the button, not after the press. Seeding
 * skips those titles - correctly, since filing an unrated title under F asserts a
 * judgement nobody made - and a person who presses the button expecting 271
 * posters and gets 259 has been told nothing about where the other twelve went.
 */
@Composable
internal fun TierEmptyState(
    candidates: List<TierCandidate>,
    unrankedCount: Int,
    isSeeding: Boolean,
    onSeed: () -> Unit,
    onAddTitles: () -> Unit
) {
    val unscored = remember(candidates) { unseedableCount(candidates) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.Trophy,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Nothing ranked yet",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (candidates.isEmpty()) {
                "Your library has not loaded, so there is nothing to rank yet."
            } else {
                "Rank ${candidates.size} titles by their AniList score, or place them yourself."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (unscored > 0) {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .clip(ExpressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = AppVectorIcons.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "$unscored " +
                        (if (unscored == 1) "title has" else "titles have") +
                        " no score and stay for you to place",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))
        ExpressivePrimaryButton(
            text = "Rank from AniList scores",
            onClick = onSeed,
            icon = AppVectorIcons.Trophy,
            loading = isSeeding,
            enabled = candidates.isNotEmpty(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(10.dp))
        ExpressiveStateChip(
            // Three states, not two, and the middle one is what the old copy got
            // wrong: with no library loaded `unrankedCount` is 0 because there are
            // no titles at all, and the chip offered "No titles left to place" on
            // a list that had never had a title to begin with - while its tap
            // opened the sheet for adding one. The count is only an answer once
            // there is a library to count.
            label = when {
                candidates.isEmpty() -> "Add titles to rank"
                unrankedCount == 0 -> "No titles left to place"
                else -> "Place them yourself ($unrankedCount)"
            },
            selected = false,
            onClick = onAddTitles,
            minTouchTargetHeight = 48.dp
        )
    }
}

/**
 * What the gestures are, under the board.
 *
 * A long press and a pinch are both invisible: nothing on a board full of posters
 * says either exists, and the drag is the whole feature. It is the last row of the
 * screen rather than a hint card, because it is a legend for the thing above it -
 * and it says the one non-obvious thing, which is that releasing off every row
 * takes a title out of the list.
 */
@Composable
internal fun TierBoardLegend(locked: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .clip(RoundedCornerShape(ExpressiveShapes.SmallCorner))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (locked) AppVectorIcons.Privacy else AppVectorIcons.Trophy,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        // Crossfaded rather than swapped in place: locking the board is a mode
        // change, and the line under it changing between two frames reads as the
        // legend flickering. `AnimatedContent` also animates its own height, so
        // the shorter locked copy does not leave a gap.
        AnimatedContent(
            targetState = locked,
            transitionSpec = {
                fadeIn(ExpressiveMotion.FastEffects) togetherWith
                    fadeOut(ExpressiveMotion.FastEffects)
            },
            contentAlignment = Alignment.CenterStart,
            modifier = Modifier.weight(1f),
            label = "tier_legend"
        ) { isLocked ->
            Text(
                text = if (isLocked) {
                    "View only: scroll and pinch freely. Tap the lock to rank " +
                        "titles again."
                } else {
                    "Hold a cover to move it, and drop it between two others to place " +
                        "it exactly. Pinch to zoom. Release off the rows to take it out."
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The line under the title, stated as counts rather than as a claim.
 *
 * "Your library has not loaded yet" is its own case because the alternative -
 * "0 of 0 titles ranked" - is a true statement about an empty set and a false
 * one about the person's account.
 */
internal fun tierListSubtitle(ranked: Int, librarySize: Int): String = when {
    librarySize == 0 -> "Your library has not loaded yet"
    ranked == 0 -> "Nothing ranked yet"
    else -> "$ranked of $librarySize titles ranked"
}
