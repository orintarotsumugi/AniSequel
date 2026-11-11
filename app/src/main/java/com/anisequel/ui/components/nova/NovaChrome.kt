package com.anisequel.ui.components.nova

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ProfileAvatarButton
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The new look's shared surfaces.
 *
 * These are deliberately *not* a second theme: every colour still comes from
 * `MaterialTheme.colorScheme`, every radius from `ExpressiveShapes`, and every
 * spring from `ExpressiveMotion`. What is new here is the composition - a
 * translucent shelf over a faint ambient wash, and a rounded square app mark -
 * because a layout can feel like a different app without becoming a different
 * design system. That is also what makes switching back to the classic layout
 * safe: nothing in the palette or the motion changed.
 */

/** The shape every shelf and header in the new layout shares. */
private val NovaSurfaceShape = RoundedCornerShape(26.dp)

/**
 * A translucent panel that reads as sitting above the page.
 *
 * No blur: a real backdrop blur is a render effect, and this app draws a full
 * list over it while scrolling. A translucent container plus a hairline border
 * gives the same layering cue at a fraction of the cost - and it is what makes
 * the cards legible on both the dark and the light palettes.
 */
@Composable
fun NovaSurface(
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = NovaSurfaceShape,
    tonalElevation: Boolean = false,
    content: @Composable BoxScope.() -> Unit
) {
    val container = MaterialTheme.colorScheme.surfaceContainerLow
    val tint = if (tonalElevation) {
        container.copy(alpha = 0.94f)
    } else {
        container.copy(alpha = 0.78f)
    }
    Surface(
        modifier = modifier,
        shape = shape,
        color = tint,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f)
        ),
        shadowElevation = if (tonalElevation) 2.dp else 0.dp
    ) {
        Box(content = content)
    }
}

/**
 * A faint wash behind a new-layout page.
 *
 * Two gradients and nothing animated: the seasonal sky already owns movement in
 * this app, and a second full-screen clock would spend frames competing with it.
 * The gradient is drawn behind the page's own opaque content, so it only shows
 * where the content is transparent - which is the point.
 */
@Composable
fun NovaBackdrop(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier.background(MaterialTheme.colorScheme.background)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.13f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(230.dp)
                .offset(x = (-70).dp, y = (-30).dp)
                .background(
                    Brush.radialGradient(
                        listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f),
                            Color.Transparent
                        )
                    ),
                    CircleShape
                )
        )
        content()
    }
}

/**
 * The new Home's bar: the app mark, its tagline, and the two doors out.
 *
 * A floating shelf rather than a flat `TopAppBar`: the new look reads the top of
 * the screen as a header card over the page, the same way the reference layout
 * does. It carries the status-bar inset itself because it is handed to
 * `Scaffold` as a custom `topBar`, which does not add the inset for us.
 *
 * The settings control is the same destination the classic bar's gear opens, and
 * the avatar is the shared [ProfileAvatarButton], so the two entries to a
 * profile cannot drift about whether they are tappable while the viewer loads.
 */
@Composable
fun NovaTopBar(
    viewerName: String?,
    avatarUrl: String?,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: (() -> Unit)? = null,
    onOpenTiers: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NovaSurface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = true
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.tertiary
                                )
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = AppVectorIcons.AnimeSparkle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(10.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "AniSequel",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Text(
                        text = viewerName?.let { "@$it" } ?: "Your list, read forwards",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (onOpenSearch != null) {
                    val searchSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onOpenSearch,
                        modifier = Modifier
                            .size(40.dp)
                            .bouncyPress(pressedScale = 0.9f, interactionSource = searchSource)
                            .testTag("nova_top_search"),
                        interactionSource = searchSource
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Search,
                            contentDescription = "Search anime and manga",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                if (onOpenTiers != null) {
                    val tiersSource = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onOpenTiers,
                        modifier = Modifier
                            .size(40.dp)
                            .bouncyPress(pressedScale = 0.9f, interactionSource = tiersSource)
                            .testTag("nova_top_tiers"),
                        interactionSource = tiersSource
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Trophy,
                            contentDescription = "Tier lists",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                val settingsSource = remember { MutableInteractionSource() }
                IconButton(
                    onClick = onOpenSettings,
                    modifier = Modifier
                        .size(40.dp)
                        .bouncyPress(pressedScale = 0.9f, interactionSource = settingsSource),
                    interactionSource = settingsSource
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Settings,
                        contentDescription = "Settings and account",
                        modifier = Modifier.size(20.dp)
                    )
                }

                ProfileAvatarButton(
                    avatarUrl = avatarUrl,
                    enabled = true,
                    onClick = onOpenProfile,
                    testTag = "nova_profile_button"
                )
            }
        }
    }
}

/**
 * A section's name, its count, and its one trailing action.
 *
 * The count is a small filled capsule rather than a clause in the title: it is
 * the number a reader scans for, and parenthesised text in a long heading was
 * the first thing an ellipsis cut.
 */
@Composable
fun NovaSectionHeading(
    title: String,
    count: Int? = null,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (count != null) {
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = count.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.weight(1f))
            trailing()
        }
    }
}

/**
 * The progress capsule on a cover in the new Library.
 *
 * `20 | 37` rather than `Ep 20/37`: at this size the icon already says which
 * medium and which kind of unit, so the words only crowd the digits. A missing
 * total is `??`, which is AniList's own way of saying "not published yet".
 */
@Composable
fun NovaProgressPill(
    progress: Int?,
    total: Int?,
    isManga: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(ExpressiveShapes.pill)
            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.94f))
            .padding(horizontal = 7.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isManga) {
                AppVectorIcons.SourceBook
            } else {
                AppVectorIcons.Play
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = "${progress ?: "-"} | ${total ?: "??"}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1
        )
    }
}
