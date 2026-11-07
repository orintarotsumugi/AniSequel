package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.theme.StatusColors
import com.anisequel.ui.theme.ThemePalette

/**
 * The Material 3 Expressive hero of the Theme tab.
 *
 * A wide gradient header leads with the scheme's own key colours — so the card
 * is literally a colour chip for whatever is driving the theme — and the active
 * colour source (wallpaper, palette, custom hue or retained anime colour) is
 * named in a pill. Below the band is the same interactive mockup as before, so
 * the spring physics can be felt before leaving the screen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemePreviewCard(
    themeSettings: ThemePreferences.ThemeSettings,
    modifier: Modifier = Modifier
) {
    var isPlanned by remember { mutableStateOf(false) }
    var isWatching by remember { mutableStateOf(false) }

    val statusColors = AniSequelTheme.statusColors
    // Ordered the way `AniSequelTheme` resolves the scheme, so this names the colour
    // source that is actually driving the app. It listed the custom hue above the
    // wallpaper, and the two are independent preferences - a hue set before the switch
    // was flipped stays set - so the pill used to label the card with a colour the app
    // was not drawing.
    // Read once into a local: `retainedAnimeColorHex` has a getter, so it cannot be
    // smart-cast by the null check below the way a stored `val` would be.
    val keptAnimeColor = themeSettings.retainedAnimeColorHex
    val activePaletteName = when {
        themeSettings.animeThemeActive && themeSettings.animeThemeColorHex != null ->
            "Anime (${themeSettings.animeThemeColorHex})"
        // The kept cover colour outranks Material You and the custom hue now: the
        // theme is built from the retained marker whenever it is present (see
        // `AniSequelTheme`), so listing wallpaper above it named a colour the app
        // was not drawing - the same bug this ordering already fixed once for the
        // custom hue, reintroduced by retention gaining the background.
        keptAnimeColor != null &&
            keptAnimeColor.equals(themeSettings.customColorHex, ignoreCase = true) ->
            "Anime, kept ($keptAnimeColor)"
        themeSettings.useDynamicColor -> "Dynamic Wallpaper"
        themeSettings.customColorHex != null -> "Custom (${themeSettings.customColorHex})"
        else -> ThemePalette.fromStorage(themeSettings.paletteId).displayName
    }

    // Read straight, not wrapped in another animation: `rememberAnimatedColorScheme`
    // already crossfades every role through the same `ThemeColorEffects` spring, so
    // these are the scheme as it is arriving. The mockup used to run `primary`
    // through its own faster `FastColorEffects` spring on top of that - the border,
    // the poster tint and the Plan button reached the new accent before the gradient
    // and the swatches beside them, in the one card whose job is to show the colour
    // as one thing.
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val tertiary = MaterialTheme.colorScheme.tertiary

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("theme_live_preview_card"),
        shape = RoundedCornerShape(ExpressiveShapes.LargeCorner),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 2.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            // Hero band: the scheme's own key colours as a gradient, so the
            // preview is also a swatch of the active source. The scrim keeps
            // white text readable on every palette, light or dark.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    // A minimum, not a fixed height: two lines of title at the
                    // reader's largest type size need more than the 96dp the band
                    // was pinned to, and a pinned band clipped the second line.
                    .heightIn(min = 96.dp)
                    .background(
                        Brush.linearGradient(listOf(primary, secondary, tertiary))
                    )
            ) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(Color.Black.copy(alpha = 0.16f))
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Live theme preview",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Your accent, as the whole app renders it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color.White.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f)),
                        // Capped so a long source name cannot leave the title
                        // column beside it with no width; the label ellipsizes
                        // instead, and the title still gets the row.
                        modifier = Modifier.widthIn(max = 148.dp)
                    ) {
                        Text(
                            text = activePaletteName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            // A retained cover colour makes this label long
                            // ("Anime, kept (#1A2B3C)"). Capped rather than
                            // allowed to squeeze the title beside it off the row.
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(16.dp)) {
                // Mockup card
                Surface(
                    // One node, not eight. This is a picture of a card, and its
                    // two buttons only exist so the press springs can be felt: a
                    // screen-reader user who found "+ Plan" here would be offered
                    // an action that plans nothing. The description says what the
                    // sample is instead.
                    modifier = Modifier
                        .fillMaxWidth()
                        .clearAndSetSemantics {
                            contentDescription =
                                "Sample card in the current theme: Frieren, Airing, finished season one. This is a preview only."
                        },
                    shape = RoundedCornerShape(ExpressiveShapes.MediumCorner),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(1.dp, primary.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Poster placeholder
                            Box(
                                modifier = Modifier
                                    .size(48.dp, 64.dp)
                                    .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                                    .background(primary.copy(alpha = 0.22f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = AppVectorIcons.Movie,
                                    contentDescription = null,
                                    tint = primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Surface(
                                        shape = RoundedCornerShape(percent = 50),
                                        color = statusColors.successContainer,
                                        modifier = Modifier.padding(end = 6.dp)
                                    ) {
                                        Text(
                                            text = "Airing",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = statusColors.success,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                        )
                                    }
                                    Text(
                                        text = "TV • Fall 2026",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Spacer(modifier = Modifier.height(4.dp))

                                Text(
                                    text = "Frieren: Beyond Journey's End S2",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1
                                )

                                Text(
                                    text = "Finished S1 (28/28 eps)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Extracted so the hero stays below the cyclomatic
                        // threshold; each button owns its colour pair and its
                        // label transition. The two used to cut between four
                        // colours and two labels on one frame; they now crossfade
                        // their containers and morph their labels on the same
                        // tokens as the rest of the app, which is the point of a
                        // preview: the reader is judging motion as well as colour.
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PreviewPlanButton(
                                isPlanned = isPlanned,
                                primary = primary,
                                statusColors = statusColors,
                                onClick = { isPlanned = !isPlanned },
                                modifier = Modifier.weight(1f)
                            )
                            PreviewWatchButton(
                                isWatching = isWatching,
                                onClick = { isWatching = !isWatching },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Role swatches: the five roles the eye reads first, in the
                // current scheme. A FlowRow, not a fixed Row: five labels at the
                // reader's largest type size are wider than a phone, and the
                // SpaceEvenly row this replaced let the last two run off the card.
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RoleSwatchDot("Primary", primary)
                    RoleSwatchDot("Secondary", secondary)
                    RoleSwatchDot("Tertiary", tertiary)
                    RoleSwatchDot("Container", MaterialTheme.colorScheme.primaryContainer)
                    RoleSwatchDot("Surface", MaterialTheme.colorScheme.surface)
                }
            }
        }
    }
}

/** The Plan half of the mock card: its own container/content pair and label morph. */
@Composable
private fun PreviewPlanButton(
    isPlanned: Boolean,
    primary: Color,
    statusColors: StatusColors,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container by animateColorAsState(
        targetValue = if (isPlanned) statusColors.successContainer else primary,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "preview_plan_container"
    )
    val content by animateColorAsState(
        targetValue = if (isPlanned) statusColors.success
        else MaterialTheme.colorScheme.onPrimary,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "preview_plan_content"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .height(38.dp)
            .bouncyPress(pressedScale = 0.94f)
            .testTag("preview_plan_button"),
        shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content
        )
    ) {
        // The label crosses rather than swapping: a filled check arriving as the
        // word changes is what makes the tap read as one action instead of two.
        AnimatedContent(
            targetState = isPlanned,
            transitionSpec = {
                fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    ) togetherWith
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    )
            },
            // Centred, not the default TopStart: the two labels are slightly
            // different widths, and left-aligning the box nudged the whole pair.
            contentAlignment = Alignment.Center,
            label = "preview_plan_label"
        ) { planned ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (planned) {
                        AppVectorIcons.BookmarkDone
                    } else {
                        AppVectorIcons.BookmarkAdd
                    },
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (planned) "Planned" else "+ Plan",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** The Watch half of the mock card: outlined until it is active, then filled. */
@Composable
private fun PreviewWatchButton(
    isWatching: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors
    val container by animateColorAsState(
        targetValue = if (isWatching) statusColors.infoContainer else Color.Transparent,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "preview_watch_container"
    )
    val content by animateColorAsState(
        targetValue = if (isWatching) statusColors.info
        else MaterialTheme.colorScheme.onSurface,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "preview_watch_content"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier
            .height(38.dp)
            .bouncyPress(pressedScale = 0.94f)
            .testTag("preview_watch_button"),
        shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = container,
            contentColor = content
        )
    ) {
        AnimatedContent(
            targetState = isWatching,
            transitionSpec = {
                fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleIn(
                        initialScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    ) togetherWith
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleOut(
                        targetScale = 0.9f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    )
            },
            contentAlignment = Alignment.Center,
            label = "preview_watch_label"
        ) { watching ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.Play,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (watching) "Watching" else "Watch",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun RoleSwatchDot(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}