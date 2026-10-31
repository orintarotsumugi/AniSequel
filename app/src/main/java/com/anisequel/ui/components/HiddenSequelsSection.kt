package com.anisequel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.posterUrl
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes

/**
 * The anime the user chose to stop being reminded about, and the only way back.
 *
 * This exists because hiding was a one-way door. "Not interested" persisted an
 * AniList id and told the user to *"find it under Filters > Hidden"* - a place
 * that was never built. The gesture worked, the promise did not, and the only
 * route back to a hidden anime was clearing the app's data, which also threw
 * away their theme, AniList client id and everything else stored alongside it.
 *
 * The list is driven by the ViewModel's `hiddenSequels`, which is the hidden
 * half of the same candidate list the dashboard draws from. That matters: the
 * rows carry a real title, poster and parent rather than a bare media id, and
 * they stay populated regardless of what the search box and status chips are
 * doing, because none of those filters apply to a standing decision.
 *
 * Collapsed by default. It is a rarely-wanted list, and the section header
 * doubles as the count of how many entries it holds - which is the number
 * someone needs in order to decide whether to open it at all.
 */
@Composable
fun HiddenSequelsSection(
    hiddenSequels: List<MissedSequel>,
    onRestoreOne: (MissedSequel) -> Unit,
    onRestoreAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    // Guarded by the list being non-empty rather than checked at each call
    // site: restoring the last row from inside the expanded body would
    // otherwise leave an open, empty section behind.
    AnimatedVisibility(
        visible = hiddenSequels.isNotEmpty(),
        // `FastSpatialSize`, not a literal spring: the same open/close gesture is on
        // seven other sections and every one of them passes this token. The four
        // copies of `spring<IntSize>(StiffnessMediumLow, IntSize.Zero)` that used to
        // sit here were exactly the drift §3 warns about - and they also ignored
        // MotionStyle, so the section expanded at full spring speed in the one mode
        // that asks for none.
        enter = expandVertically(
            animationSpec = ExpressiveMotion.FastSpatialSize
        ) + fadeIn(ExpressiveMotion.FastEffects),
        exit = shrinkVertically(
            animationSpec = ExpressiveMotion.FastSpatialSize
        ) + fadeOut(ExpressiveMotion.FastEffects)
    ) {
        Column(
            modifier = modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .testTag("hidden_sequels_section")
        ) {
            HiddenSectionHeader(
                count = hiddenSequels.size,
                expanded = expanded,
                onToggle = { expanded = !expanded }
            )

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(
                    animationSpec = ExpressiveMotion.FastSpatialSize
                ) + fadeIn(ExpressiveMotion.FastEffects),
                exit = shrinkVertically(
                    animationSpec = ExpressiveMotion.FastSpatialSize
                ) + fadeOut(ExpressiveMotion.FastEffects)
            ) {
                Column {
                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "These are never offered again until you restore them. " +
                            "Hiding does not change anything on your AniList account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    hiddenSequels.forEach { sequel ->
                        HiddenSequelRow(
                            sequel = sequel,
                            onRestore = { onRestoreOne(sequel) }
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    TextButton(
                        onClick = onRestoreAll,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("restore_all_hidden_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Visibility,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Show all ${hiddenSequels.size} again",
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }
        }
    }
}

/**
 * The collapsible header: a count, the label, and a chevron that rotates.
 *
 * Rotates the chevron rather than swapping two icons so the affordance reads as
 * one control opening and closing, which is what it is.
 */
@Composable
private fun HiddenSectionHeader(
    count: Int,
    expanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The chevron's rotation is applied in the draw phase, not through
    // `Modifier.rotate`: that takes a plain value, so reading the animation while
    // composing re-runs layout for every frame of the turn, and §3 asks for transforms
    // in a `graphicsLayer`. The container colour is the one that genuinely has no
    // layer to hand to - `Modifier.background` takes its colour by value - and that
    // cost is bounded to the two state changes of a header the user tapped. It uses
    // `FastEffects` (NoBouncy) rather than a spring that can overshoot, because a
    // spring on `Color` interpolates componentwise and an overshoot can push a
    // channel out of gamut mid-flight.
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "hidden_chevron_rotation"
    )
    val container by animateColorAsState(
        targetValue = if (expanded) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            Color.Transparent
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "hidden_header_container"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ExpressiveShapes.pillSoft)
            .background(container)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("hidden_section_header"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Hidden anime",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        SurfaceCount(count = count)
        Spacer(modifier = Modifier.width(6.dp))
        Icon(
            imageVector = AppVectorIcons.SequelJump,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(16.dp)
                .graphicsLayer { rotationZ = chevronRotation }
        )
    }
}

/** The badge showing how many entries are hidden. */
@Composable
private fun SurfaceCount(count: Int, modifier: Modifier = Modifier) {
    // Deliberately *not* an `animateColorAsState`. Its target was a
    // `MaterialTheme.colorScheme` read with no `if` in front of it, so the
    // animation was structurally incapable of ever animating: it installed an
    // `AnimationState` and a running coroutine, settled on the first frame, and
    // then sat there for the life of the composable doing nothing - while costing
    // one extra recomposition on every first composition to feed
    // `.background(container)`.
    val container = MaterialTheme.colorScheme.tertiaryContainer

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(container)
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .testTag("hidden_count_badge"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * One hidden anime, with the single control that brings it back.
 *
 * No exit animation of its own: restoring removes the row from [hiddenSequels],
 * which disposes this composable outright, so an `AnimatedVisibility` here
 * would never get to play its exit - it would only ever add an enter animation
 * for a row that is already on screen. The whole section does animate open and
 * closed, which is where the motion actually reads.
 */
@Composable
private fun HiddenSequelRow(
    sequel: MissedSequel,
    onRestore: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onRestore)
            .padding(vertical = 8.dp, horizontal = 4.dp)
            .testTag("hidden_row_${sequel.sequelId}"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The entry's own cover, not only its colour. The colour block was a
        // decode-while-scrolling trade-off, but this sheet's whole job is finding
        // a title by eye - a column of tinted rectangles is the one thing that
        // cannot be done with it. The image is bounded to the 32x44 tile, so the
        // decode stays cheap.
        Box(
            modifier = Modifier
                .size(width = 32.dp, height = 44.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(
                    sequel.coverColor.toCoverColorOrNull()
                        ?: MaterialTheme.colorScheme.surfaceContainerHighest
                )
        ) {
            val cover = sequel.sequelMedia.posterUrl
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = "Cover for ${sequel.sequelTitle}",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = sequel.sequelTitle,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${sequel.relationLabel} ${sequel.parentTitle}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        Text(
            text = "Restore",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )

        Spacer(modifier = Modifier.width(4.dp))

        Icon(
            imageVector = Icons.Filled.Visibility,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(16.dp)
        )
    }
}
