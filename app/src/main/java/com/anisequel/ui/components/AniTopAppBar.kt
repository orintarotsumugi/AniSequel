package com.anisequel.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.ViewerProfile
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AniTopAppBar(
    viewer: ViewerProfile?,
    missedCount: Int,
    onOpenFilter: () -> Unit,
    onRefresh: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenProfile: () -> Unit,
    isRefreshing: Boolean = false,
    hasActiveFilters: Boolean = false,
    scrollBehavior: androidx.compose.material3.TopAppBarScrollBehavior? = null,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    TopAppBar(
        modifier = modifier.testTag("ani_top_app_bar"),
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            // One step stronger than the un-scrolled container. The bar used
            // `surfaceContainer` here, which in the dark palette is barely a
            // shade off `surface`, so scrolling the feed under the bar changed
            // almost nothing and the elevation signal the scroll behavior is
            // meant to give was invisible. `surfaceContainerHigh` is the first
            // step that actually reads as "content is passing under this now".
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileAvatarButton(
                    avatarUrl = viewer?.avatar?.medium,
                    enabled = viewer != null,
                    onClick = onOpenProfile
                )

                Spacer(modifier = Modifier.width(10.dp))

                // Plain text header - profile navigation is cleanly triggered by the avatar button
                Column {
                    Text(
                        text = "AniSequel",
                        // `titleLarge` is the M3 top-app-bar title role; the bar
                        // was rendering `titleMedium`, one step small, which is
                        // why the app's own name read as a section header rather
                        // than as the screen's title.
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 1
                    )
                    if (viewer != null) {
                        Text(
                            text = "@${viewer.name}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        },
        actions = {
            // One interaction source per action, shared between the `IconButton`
            // and `bouncyPress`. Without a source `bouncyPress` falls back to
            // its own pointer loop, which springs the control down on the first
            // pointer-down and releases only once the finger travels past touch
            // slop - so a finger that lands here and starts a scroll squashes
            // the icon for the whole drag and then springs back, reading as two
            // presses. Reading the button's own source emits the press on down
            // and *cancels* it the moment the gesture is claimed as a drag.
            //
            // It also puts the ripple and the scale on the same source, so the
            // highlight bounds and the press animation cannot disagree about
            // when the button is down.
            val filterSource = remember { MutableInteractionSource() }
            val refreshSource = remember { MutableInteractionSource() }
            val settingsSource = remember { MutableInteractionSource() }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onOpenFilter()
                },
                modifier = Modifier
                    .bouncyPress(interactionSource = filterSource)
                    .testTag("filter_button"),
                interactionSource = filterSource
            ) {
                BadgedBox(
                    badge = {
                        if (missedCount > 0 && hasActiveFilters) {
                            Badge(
                                containerColor = MaterialTheme.colorScheme.tertiary,
                                contentColor = MaterialTheme.colorScheme.onTertiary
                            ) {
                                Text(text = if (missedCount > 99) "99+" else "$missedCount")
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Filter,
                        contentDescription = "Filter and sort sequels"
                    )
                }
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onRefresh()
                },
                enabled = !isRefreshing,
                modifier = Modifier
                    .bouncyPress(interactionSource = refreshSource)
                    .testTag("refresh_button"),
                interactionSource = refreshSource
            ) {
                if (isRefreshing) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
                    Icon(
                        imageVector = AppVectorIcons.Refresh,
                        contentDescription = "Refresh lists"
                    )
                }
            }

            IconButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onOpenSettings()
                },
                modifier = Modifier
                    .bouncyPress(interactionSource = settingsSource)
                    .testTag("settings_button"),
                interactionSource = settingsSource
            ) {
                Icon(
                    imageVector = AppVectorIcons.Settings,
                    contentDescription = "Settings and account"
                )
            }
        }
    )
}
