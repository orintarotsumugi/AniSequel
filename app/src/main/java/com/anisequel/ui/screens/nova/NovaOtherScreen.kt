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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaBackdrop
import com.anisequel.ui.components.nova.NovaSectionHeading
import com.anisequel.ui.components.nova.NovaSurface

/**
 * The new look's "Other" hub.
 *
 * The reference layout this follows groups feature cards under Anime and Manga;
 * the cards here name only things this app actually has. That rule is the whole
 * design: a hub is a promise, and a card that opens an empty page is worse than a
 * section with three rows instead of four.
 */
@Composable
fun NovaOtherScreen(
    onBack: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenLibrary: () -> Unit,
    onOpenTiers: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    NovaBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .testTag("nova_other_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .bouncyPress(pressedScale = 0.9f)
                        .testTag("nova_other_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to Home"
                    )
                }
                Spacer(modifier = Modifier.size(12.dp))
                Text(
                    text = "Other Features",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                // The remaining height, not the whole screen's.
                //
                // `fillMaxSize` here measured the grid against the Column's full
                // incoming height and then placed it *below* the header, so its
                // viewport ran past the bottom of the window: the last row of
                // cards could not be scrolled into view, because reaching it
                // meant scrolling the grid's edge - which was already off screen.
                // A weight is the grid asking the Column for what is left.
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 18.dp,
                    bottom = 24.dp
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item(key = "anime_heading", span = { GridItemSpan(maxLineSpan) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppVectorIcons.Tv,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        NovaSectionHeading(
                            title = "Anime",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(key = "schedule", span = { GridItemSpan(maxLineSpan) }) {
                    NovaFeatureCard(
                        icon = AppVectorIcons.Calendar,
                        title = "Schedule",
                        subtitle = "Airing now and upcoming from your list",
                        onClick = onOpenSchedule,
                        wide = true
                    )
                }
                item(key = "tiers", span = { GridItemSpan(maxLineSpan) }) {
                    NovaFeatureCard(
                        icon = AppVectorIcons.Trophy,
                        title = "Tier Lists",
                        subtitle = "Rank what you have watched or read, then share the board",
                        onClick = onOpenTiers,
                        wide = true
                    )
                }

                item(key = "manga_heading", span = { GridItemSpan(maxLineSpan) }) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.SourceBook,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        NovaSectionHeading(
                            title = "Manga",
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                item(key = "manga_library") {
                    NovaFeatureCard(
                        icon = AppVectorIcons.Library,
                        title = "Library",
                        subtitle = "Every anime and manga on your list",
                        onClick = onOpenLibrary
                    )
                }
                item(key = "profile") {
                    NovaFeatureCard(
                        icon = AppVectorIcons.ProfileStats,
                        title = "Your Profile",
                        subtitle = "Stats, activity and favourites",
                        onClick = onOpenProfile
                    )
                }

                item(key = "settings", span = { GridItemSpan(maxLineSpan) }) {
                    NovaFeatureCard(
                        icon = AppVectorIcons.Settings,
                        title = "Make it yours",
                        subtitle = "Appearance, layout and quick add",
                        onClick = onOpenSettings,
                        wide = true
                    )
                }
            }
        }
    }
}

/**
 * One card in the hub.
 *
 * A 148dp square (or a shorter wide row) rather than a list item: these are
 * destinations, and the grid is the shape that says "pick one". The whole card is
 * the target and it uses the app's press spring, so it behaves like the shelf
 * cards on Home rather than like a settings switch.
 */
@Composable
private fun NovaFeatureCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    wide: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }

    NovaSurface(
        modifier = modifier
            .fillMaxWidth()
            // Wide cards fit the icon, the title and one subtitle line inside
            // their own padding; the first draft was 104dp and clipped the line.
            .height(if (wide) 124.dp else 148.dp)
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            ),
        tonalElevation = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(13.dp))
                    .background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.height(if (wide) 8.dp else 12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = if (wide) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
