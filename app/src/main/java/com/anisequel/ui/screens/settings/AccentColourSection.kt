package com.anisequel.ui.screens.settings

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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.graphics.ColorUtils
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The whole accent story in one card: the wallpaper toggle, the brand palettes,
 * then a custom hue.
 *
 * Picking a palette or a hue steps off everything that outranks it in `Theme.kt`,
 * because an active override silently ignores the new choice. That used to mean
 * only the anime colour adaptation, and the wallpaper switch was missed: with
 * dynamic colour on, the palette cards and hue chips did nothing at all - the
 * wallpaper scheme kept drawing - yet the card the reader had just tapped moved its
 * check mark and lit its border, so the pick looked applied and was not. The same
 * rule now covers both, and turning the switch itself *on* steps the anime layer off
 * for the same reason.
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
        subtitle = "Wallpaper, a palette, or a custom hue - the last pick wins",
        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
        iconContent = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        SwitchRow(
            title = "Dynamic colour",
            subtitle = "Take the accent from your wallpaper.",
            checked = settings.useDynamicColor,
            onCheckedChange = { enabled ->
                scope.launch {
                    // Turning it on has to clear the anime layer for exactly the
                    // reason a palette pick does: the anime colour outranks the
                    // wallpaper scheme in `Theme.kt`, so a live adaptation would make
                    // this switch look broken until it timed out on its own.
                    if (enabled) themePreferences.setAnimeThemeActive(false, null)
                    themePreferences.setUseDynamicColor(enabled)
                }
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
        SettingsFieldLabel(text = "Palette")
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemePalette.entries.forEach { palette ->
                PaletteCard(
                    palette = palette,
                    // A palette is "selected" only when nothing outranks it: the
                    // wallpaper scheme, a custom hue, or an active anime colour means
                    // no palette read is actually driving the scheme. Without the
                    // dynamic-colour term the check mark moved to a card that had
                    // changed nothing.
                    selected = settings.paletteId == palette.id &&
                        settings.customColorHex == null &&
                        !settings.animeThemeActive &&
                        !settings.useDynamicColor,
                    onClick = {
                        scope.launch {
                            // The palette picker used to clear only the custom
                            // hue, leaving an active anime colour to silently
                            // overrule whatever the user had just chosen. The
                            // wallpaper does the same, so it is stepped off here
                            // too, and `setCustomColorHex(null)` is what makes the
                            // chosen palette the basis again.
                            themePreferences.setCustomColorHex(null)
                            themePreferences.setAnimeThemeActive(false, null)
                            themePreferences.setUseDynamicColor(false)
                            themePreferences.setPalette(palette.id)
                        }
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsFieldLabel(text = "Custom hue")
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            customHues.forEach { (hex, name) ->
                HueChip(
                    hex = hex,
                    name = name,
                    isSelected = settings.customColorHex?.equals(hex, ignoreCase = true) == true &&
                        !settings.useDynamicColor,
                    onClick = {
                        scope.launch {
                            // Picking a hue used to leave an active anime colour
                            // in place, and because the anime colour outranks
                            // everything in `Theme.kt`, the new hue appeared to
                            // do nothing. It now steps the anime adaptation off,
                            // like the palette picker does - and the wallpaper
                            // with it, which is the other override that made this
                            // chip a control that could not change anything.
                            themePreferences.setAnimeThemeActive(false, null)
                            themePreferences.setUseDynamicColor(false)
                            themePreferences.setCustomColorHex(
                                if (settings.customColorHex?.equals(hex, ignoreCase = true) == true) null else hex
                            )
                        }
                    }
                )
            }
        }

        Text(
            text = when {
                // The wallpaper outranks a stored hue, and the two are separate
                // preferences, so naming the hue here would name a colour the app
                // is not drawing.
                settings.useDynamicColor ->
                    "Your wallpaper is providing the accent. Pick a palette or a hue to switch to it."
                // The hue chips toggle, and nothing on the chip says so. Naming the
                // way back is cheaper than a second "None" chip that would be the
                // only uncoloured circle in the row.
                settings.customColorHex != null ->
                    "Custom hue: ${settings.customColorHex}. Tap it again to go back to the palette."
                else -> "A custom hue refines the accent above any palette."
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
            .semantics(mergeDescendants = true) {
                // The name is the one drawn under the swatch, and `clickable`
                // already merges that in - so this adds what the card could not
                // say before: which one is chosen. The check mark used to carry a
                // `contentDescription` equal to that same visible name, which the
                // merge then read out twice once a palette was selected ("Kitsu
                // Kitsu"); the description is gone and the state is a property.
                this.selected = selected
                role = Role.RadioButton
            }
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
                            // The merged semantics above already name the card, and
                            // the name is drawn below it: a description here would
                            // make a screen reader say it twice.
                            contentDescription = null,
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
            // The chip is a colour and nothing else, so the name has to be spoken:
            // it used to hang off the check mark, which only exists once the chip is
            // selected, leaving every other hue as an unlabelled circle.
            .semantics {
                contentDescription = name
                this.selected = isSelected
                role = Role.RadioButton
            }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (isSelected) {
                Icon(
                    imageVector = AppVectorIcons.CheckCircle,
                    // Named on the chip above, not here.
                    contentDescription = null,
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