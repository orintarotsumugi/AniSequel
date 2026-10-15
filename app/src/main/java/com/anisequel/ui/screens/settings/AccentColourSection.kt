package com.anisequel.ui.screens.settings
import com.anisequel.ui.components.expressive.ExpressiveShapes

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The whole accent story in one card: the wallpaper toggle (which wins), the
 * brand palettes, then a custom hue. Picking a palette or a hue steps the anime
 * colour adaptation off, because the anime colour outranks everything in
 * `Theme.kt` and an active one would silently ignore the new choice.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun AccentColourSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Accent colour",
        icon = AppVectorIcons.SectionAppearance,
        subtitle = "Wallpaper colour wins, then a custom hue, then the palette.",
        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
        iconContent = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        SwitchRow(
            title = "Dynamic colour",
            subtitle = "Take the accent from your wallpaper.",
            checked = settings.useDynamicColor,
            onCheckedChange = { enabled ->
                scope.launch { themePreferences.setUseDynamicColor(enabled) }
            },
            modifier = Modifier.testTag("dynamic_color_switch")
        )

        // While wallpaper theming is on, the current scheme already *is* the
        // wallpaper palette, so the roles are the live preview — no extra system
        // call to read the wallpaper again.
        if (settings.useDynamicColor) {
            Spacer(modifier = Modifier.padding(top = 2.dp))
            Surface(
                shape = RoundedCornerShape(ExpressiveShapes.SmallCorner),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "From your wallpaper",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                    ColorTriple(
                        first = MaterialTheme.colorScheme.primary,
                        second = MaterialTheme.colorScheme.secondary,
                        third = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Palette",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemePalette.entries.forEach { palette ->
                PaletteCard(
                    palette = palette,
                    // A palette is "selected" only when nothing outranks it: a
                    // custom hue or an active anime colour means no palette read
                    // is actually driving the scheme.
                    selected = settings.paletteId == palette.id &&
                        settings.customColorHex == null &&
                        !settings.animeThemeActive,
                    onClick = {
                        scope.launch {
                            // The palette picker used to clear only the custom
                            // hue, leaving an active anime colour to silently
                            // overrule whatever the user had just chosen.
                            themePreferences.setCustomColorHex(null)
                            themePreferences.setAnimeThemeActive(false, null)
                            themePreferences.setPalette(palette.id)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Custom hue",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            customHues.forEach { (hex, name) ->
                HueChip(
                    hex = hex,
                    name = name,
                    isSelected = settings.customColorHex?.equals(hex, ignoreCase = true) == true,
                    onClick = {
                        scope.launch {
                            // Picking a hue used to leave an active anime colour
                            // in place, and because the anime colour outranks
                            // everything in `Theme.kt`, the new hue appeared to
                            // do nothing. It now steps the anime adaptation off,
                            // like the palette picker does.
                            themePreferences.setAnimeThemeActive(false, null)
                            themePreferences.setCustomColorHex(
                                if (settings.customColorHex?.equals(hex, ignoreCase = true) == true) null else hex
                            )
                        }
                    }
                )
            }
        }

        Text(
            text = if (settings.customColorHex != null) {
                "Selected: ${settings.customColorHex}"
            } else {
                "A custom hue refines the accent above any palette."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 10.dp)
        )
    }
}

/**
 * One brand in the palette picker: a rounded card washed with the palette's own
 * key colours as a gradient, so two similar blues are told apart by their
 * primary family rather than by reading the label.
 */
@Composable
private fun PaletteCard(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (selected) palette.dark.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "palette_border_color"
    )
    val cardShape = RoundedCornerShape(ExpressiveShapes.MediumCorner)
    val swatchShape = RoundedCornerShape(ExpressiveShapes.SmallCorner)

    Column(
        modifier = Modifier
            .clip(cardShape)
            .bouncyPress(pressedScale = 0.94f)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 46.dp)
                .clip(swatchShape)
                .background(
                    Brush.linearGradient(
                        listOf(palette.dark.primary, palette.light.primary, palette.dark.secondary)
                    )
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = borderColor,
                    shape = swatchShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Surface(
                    shape = CircleShape,
                    color = Color.Black.copy(alpha = 0.30f),
                    modifier = Modifier.size(22.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = AppVectorIcons.CheckCircle,
                            contentDescription = palette.displayName,
                            tint = Color.White,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = palette.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One custom hue as a round chip with a white/black check chosen for contrast
 * against the hue itself.
 */
@Composable
private fun HueChip(
    hex: String,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val color = DynamicThemeBuilder.parseHexColor(hex) ?: Color.Gray
    val checkColor = if (ColorUtils.calculateLuminance(color.toArgb()) > 0.5f) {
        Color.Black
    } else {
        Color.White
    }

    Surface(
        shape = CircleShape,
        color = color,
        tonalElevation = if (isSelected) 6.dp else 1.dp,
        border = BorderStroke(
            width = if (isSelected) 2.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .size(44.dp)
            .bouncyPress(pressedScale = 0.88f)
            .clickable(onClick = onClick)
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    imageVector = AppVectorIcons.CheckCircle,
                    contentDescription = name,
                    tint = checkColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

/** Three dots showing a colour family at a glance (used for the wallpaper row). */
@Composable
private fun ColorTriple(
    first: Color,
    second: Color,
    third: Color
) {
    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        listOf(first, second, third).forEach { color ->
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(color)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            )
        }
    }
}

/** The fixed hues the custom-accent picker offers, with display names. */
private val customHues = listOf(
    "#0057B7" to "Blue",
    "#E91E63" to "Rose",
    "#9C27B0" to "Violet",
    "#673AB7" to "Indigo",
    "#00BCD4" to "Cyan",
    "#00BFA5" to "Jade",
    "#4CAF50" to "Green",
    "#FF9800" to "Amber",
    "#FF5722" to "Flame",
    "#F44336" to "Crimson",
    "#607D8B" to "Slate"
)