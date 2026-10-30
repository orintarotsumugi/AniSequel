package com.anisequel.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme
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
                    .height(96.dp)
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
                        .padding(horizontal = 20.dp, vertical = 18.dp),
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
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = Color.White.copy(alpha = 0.22f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.35f))
                    ) {
                        Text(
                            text = activePaletteName,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(14.dp)) {
                // Mockup card
                Surface(
                    modifier = Modifier.fillMaxWidth(),
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

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { isPlanned = !isPlanned },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .bouncyPress(pressedScale = 0.94f)
                                    .testTag("preview_plan_button"),
                                shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
                                colors = if (isPlanned) {
                                    ButtonDefaults.buttonColors(
                                        containerColor = statusColors.successContainer,
                                        contentColor = statusColors.success
                                    )
                                } else {
                                    ButtonDefaults.buttonColors(
                                        containerColor = primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = if (isPlanned) AppVectorIcons.BookmarkDone else AppVectorIcons.BookmarkAdd,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isPlanned) "Planned" else "+ Plan",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            OutlinedButton(
                                onClick = { isWatching = !isWatching },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .bouncyPress(pressedScale = 0.94f)
                                    .testTag("preview_watch_button"),
                                shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
                                colors = if (isWatching) {
                                    ButtonDefaults.outlinedButtonColors(
                                        containerColor = statusColors.infoContainer,
                                        contentColor = statusColors.info
                                    )
                                } else {
                                    ButtonDefaults.outlinedButtonColors(
                                        contentColor = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            ) {
                                Icon(
                                    imageVector = AppVectorIcons.Play,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isWatching) "Watching" else "Watch",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Role swatches: the five roles the eye reads first, in the
                // current scheme.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
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