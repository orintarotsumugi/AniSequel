package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ViewerProfile
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.expressiveHoldGesture

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
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface
        ),
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ProfileButton(viewer = viewer, onClick = onOpenProfile)

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = "AniSequel",
                        style = MaterialTheme.typography.titleMedium,
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
            IconButton(
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    onOpenFilter()
                },
                modifier = Modifier
                    .bouncyPress()
                    .testTag("filter_button")
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
                    .bouncyPress()
                    .testTag("refresh_button")
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
                    .bouncyPress()
                    .testTag("settings_button")
            ) {
                Icon(
                    imageVector = AppVectorIcons.Settings,
                    contentDescription = "Settings and account"
                )
            }
        }
    )
}

/**
 * The avatar, as the way into the person's own AniList profile.
 *
 * It was a picture until the profile screen existed. Three things changed with
 * it, and all three are the reason it is not an `IconButton`:
 *
 *  - **Tap *and* hold both open it.** [expressiveHoldGesture] takes both
 *    callbacks, and a gesture that answers to either one means a user who does
 *    not know which of the two they did still gets the screen. The one thing a
 *    hold-to-open target must never do is nothing at all.
 *  - **No ripple, no indirection.** The gesture modifier draws its own press
 *    scale from the same spring the other two actions use, so all three controls
 *    in this bar feel identical.
 *  - **A `Role.Button` and a description, by hand.** `expressiveHoldGesture`
 *    installs a pointer handler, not a clickable, so nothing sets the semantics
 *    a screen reader and TalkBack's keyboard navigation rely on. A control that
 *    only looks like a button is a control that cannot be reached without a
 *    screen reader seeing.
 *
 * The avatar is 36dp, which is under the 48dp minimum tap target. The gesture
 * area is padded out past that by the padding below, which is why the image and
 * the padding are one element rather than two.
 *
 * Inert while [viewer] is null, which is the state the dashboard is in for the
 * second or two before the viewer query returns. Without that, the placeholder
 * box is tappable and the only profile it could open is the demo one - so a tap
 * during a cold start would answer with a stranger's bio and five studios
 * nobody chose. A control that cannot do the thing must not accept the gesture.
 */
@Composable
private fun ProfileButton(
    viewer: ViewerProfile?,
    onClick: () -> Unit
) {
    val enabled = viewer != null

    Box(
        modifier = Modifier
            .padding(6.dp)
            .then(
                if (enabled) {
                    Modifier.expressiveHoldGesture(onHold = onClick, onClick = onClick)
                } else {
                    Modifier
                }
            )
            .semantics {
                role = Role.Button
                if (enabled) {
                    contentDescription = "Open your AniList profile"
                } else {
                    disabled()
                }
            }
            .testTag("profile_button")
    ) {
        Avatar(viewer = viewer)
    }
}

@Composable
private fun Avatar(viewer: ViewerProfile?) {
    val avatarUrl = viewer?.avatar?.medium

    if (avatarUrl != null) {
        AsyncImage(
            model = avatarUrl,
            contentDescription = null,
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentScale = ContentScale.Crop
        )
    } else {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.SequelJump,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}
