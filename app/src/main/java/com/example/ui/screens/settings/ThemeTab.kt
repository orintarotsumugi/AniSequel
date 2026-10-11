package com.example.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.MotionStyle
import com.example.data.repository.ThemeMode
import com.example.data.repository.ThemePreferences
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.example.ui.components.expressive.SegmentedOption
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.theme.DynamicThemeBuilder
import com.example.ui.theme.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    ThemeMode.LIGHT -> AppVectorIcons.ThemeLight
    ThemeMode.DARK -> AppVectorIcons.ThemeDark
}

/**
 * The dedicated Theme tab.
 *
 * Sections run from most-used to least: appearance, accent colour (dynamic,
 * palette and custom hues in one place), the anime cover-colour adaptation,
 * backgrounds, and motion. The old tab scattered those three accent controls
 * over three separate cards with the anime feature - which does nothing until a
 * colour is set - wedged between the mode picker and the palette, so "what is
 * my accent?" took four scroll positions to answer.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeTab(
    themePreferences: ThemePreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val themeSettings by themePreferences.settings.collectAsState(
        initial = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )

    SettingsScrollColumn(modifier) {
        ThemePreviewCard(themeSettings = themeSettings)
        Spacer(modifier = Modifier.height(14.dp))

        SectionCard(
            title = "Appearance",
            icon = AppVectorIcons.ThemeDark,
            subtitle = "Light, dark, or follow the device."
        ) {
            ExpressivePolygonSegmentedBar(
                options = ThemeMode.entries.map { mode ->
                    SegmentedOption(label = mode.displayName, icon = mode.icon())
                },
                selectedIndex = ThemeMode.entries.indexOf(themeSettings.themeMode),
                onSelect = { index ->
                    scope.launch { themePreferences.setThemeMode(ThemeMode.entries[index]) }
                },
                modifier = Modifier.testTag("theme_mode_row")
            )
        }

        AccentColourSection(
            themePreferences = themePreferences,
            settings = themeSettings,
            scope = scope
        )

        SectionCard(
            title = "Anime colour match",
            icon = AppVectorIcons.AnimeSparkle,
            subtitle = "While an anime detail sheet is open, borrow its cover colour."
        ) {
            SwitchRow(
                title = "Anime Dynamic Theme",
                subtitle = "Apply the anime's colour scheme while you view it.",
                checked = themeSettings.animeThemeActive,
                onCheckedChange = { active ->
                    scope.launch {
                        themePreferences.setAnimeThemeActive(active, themeSettings.animeThemeColorHex)
                    }
                },
                modifier = Modifier.testTag("anime_theme_active_switch")
            )
            Spacer(modifier = Modifier.height(10.dp))
            SwitchRow(
                title = "Retain Anime Color",
                subtitle = "Keep the anime's colour around the sheet.",
                checked = themeSettings.animeThemeRetainColor,
                onCheckedChange = { retain ->
                    scope.launch { themePreferences.setAnimeThemeRetainColor(retain) }
                },
                modifier = Modifier.testTag("anime_theme_retain_switch")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Revert after closing the anime",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            val revertOptions = listOf("Immediately", "5 Minutes", "10 Minutes")
            val revertValues = listOf(0, 300, 600)
            // An unknown stored value falls back to "5 Minutes", but a real 0
            // must survive: the previous `.coerceAtLeast(1)` clamped 0 up to 1, so
            // "Immediately" could never be shown, and picking it snapped straight
            // back to "5 Minutes". Index -1 (a value not in the list) is the only
            // case the fallback is for.
            val storedRevertIndex = revertValues.indexOf(themeSettings.animeThemeRevertDuration)
            val currentRevertIndex = if (storedRevertIndex >= 0) storedRevertIndex else 1
            ExpressivePolygonSegmentedBar(
                options = revertOptions.map { SegmentedOption(label = it, icon = AppVectorIcons.Restore) },
                selectedIndex = currentRevertIndex,
                onSelect = { index ->
                    scope.launch { themePreferences.setAnimeThemeRevertDuration(revertValues[index]) }
                },
                modifier = Modifier.testTag("anime_theme_revert_duration_row")
            )
        }

        BackgroundSection(
            themePreferences = themePreferences,
            settings = themeSettings,
            scope = scope
        )

        SectionCard(
            title = "Motion",
            icon = AppVectorIcons.Trending,
            subtitle = "How the app animates."
        ) {
            ExpressivePolygonSegmentedBar(
                options = MotionStyle.entries.map { style ->
                    SegmentedOption(label = style.displayName, icon = AppVectorIcons.Trending)
                },
                selectedIndex = MotionStyle.entries.indexOf(themeSettings.motionStyle),
                onSelect = { index ->
                    scope.launch { themePreferences.setMotionStyle(MotionStyle.entries[index]) }
                },
                modifier = Modifier.testTag("motion_style_row")
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Instant = no motion at all. Smooth = calm fades with no overshoot. Chill = full expressive physics.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        val appearanceChanged = themeSettings.themeMode != ThemeMode.DEFAULT ||
            themeSettings.useDynamicColor ||
            themeSettings.paletteId != ThemePalette.ANISDK.id ||
            themeSettings.motionStyle != MotionStyle.DEFAULT ||
            themeSettings.trueBlack ||
            themeSettings.customColorHex != null ||
            themeSettings.animeThemeActive ||
            themeSettings.appBackgroundColor != null

        if (appearanceChanged) {
            Spacer(modifier = Modifier.height(8.dp))
            SettingsButton(
                text = "Reset appearance",
                onClick = { scope.launch { themePreferences.reset() } },
                icon = AppVectorIcons.Restore,
                variant = SettingsButtonVariant.Outlined,
                fillWidth = true,
                testTag = "reset_appearance_button"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The whole accent story in one card: wallpaper colour (when on), then the
 * brand palette, then a custom hue. It used to be three cards - "Colour
 * palette", "Custom app colour" and "System colour" - whose precedence was
 * documented nowhere and discoverable only from `Theme.kt`.
 */
@Composable
private fun AccentColourSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Accent colour",
        icon = AppVectorIcons.SectionAppearance,
        subtitle = "Wallpaper colour wins, then a custom hue, then the palette."
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

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Palette",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ThemePalette.entries.forEach { palette ->
                PaletteSwatch(
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
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            customHues.forEach { (hex, name) ->
                val isSelected = settings.customColorHex.equals(hex, ignoreCase = true)
                val color = DynamicThemeBuilder.parseHexColor(hex) ?: Color.Gray

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color,
                    tonalElevation = if (isSelected) 6.dp else 1.dp,
                    border = if (isSelected) {
                        BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                    } else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier
                        .size(38.dp)
                        .clip(MaterialTheme.shapes.small)
                        .bouncyPress(pressedScale = 0.90f)
                        .clickable {
                            scope.launch {
                                // Picking a hue used to leave an active anime
                                // colour in place, and because the anime colour
                                // outranks everything in `Theme.kt`, the new hue
                                // appeared to do nothing. It now steps the anime
                                // adaptation off, like the palette picker does.
                                themePreferences.setAnimeThemeActive(false, null)
                                themePreferences.setCustomColorHex(if (isSelected) null else hex)
                            }
                        }
                ) {
                    if (isSelected) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = AppVectorIcons.CheckCircle,
                                contentDescription = name,
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
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
 * How dark the app gets: the OLED-black switch and the background canvas colour
 * that overrides every screen. These were two one-switch/one-row cards before;
 * there is nothing else in either of them, so grouping them reads as "the
 * background" instead of two features.
 */
@Composable
private fun BackgroundSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Background",
        icon = AppVectorIcons.SectionAppearance,
        subtitle = "How dark the app is in dark mode."
    ) {
        SwitchRow(
            title = "True black",
            subtitle = "Pure black backgrounds drain less power on OLED screens.",
            checked = settings.trueBlack,
            onCheckedChange = { enabled ->
                scope.launch { themePreferences.setTrueBlack(enabled) }
            },
            modifier = Modifier.testTag("true_black_switch")
        )

        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Canvas colour",
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backgroundColors.forEach { (hex, name) ->
                val isSelected = settings.appBackgroundColor.equals(hex, ignoreCase = true) ||
                    (hex == null && settings.appBackgroundColor == null)
                val color = if (hex != null) {
                    DynamicThemeBuilder.parseHexColor(hex) ?: Color.DarkGray
                } else {
                    MaterialTheme.colorScheme.surfaceContainer
                }

                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = color,
                    tonalElevation = if (isSelected) 6.dp else 1.dp,
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outlineVariant
                    ),
                    modifier = Modifier
                        .size(54.dp, 38.dp)
                        .clip(MaterialTheme.shapes.small)
                        .bouncyPress(pressedScale = 0.90f)
                        .clickable {
                            scope.launch { themePreferences.setAppBackgroundColor(hex) }
                        }
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = name,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
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

/** The background canvas choices; `null` is "follow the theme". */
private val backgroundColors = listOf(
    null to "Default",
    "#000000" to "Pure Black",
    "#080C14" to "Midnight Navy",
    "#121212" to "Deep Charcoal",
    "#1C1410" to "Warm Espresso",
    "#101820" to "Slate Navy"
)

/**
 * One brand in the palette picker: a swatch in the selection ring with the
 * palette's own primaries as three dots, so the name is never the only way to
 * tell two similar blues apart.
 */
@Composable
private fun PaletteSwatch(
    palette: ThemePalette,
    selected: Boolean,
    onClick: () -> Unit
) {
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (selected) palette.dark.primary else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = com.example.ui.components.expressive.ExpressiveMotion.FastColorEffects,
        label = "palette_border_color"
    )

    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .bouncyPress(pressedScale = 0.94f)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp, 40.dp),
            shape = MaterialTheme.shapes.small,
            color = palette.dark.primaryContainer,
            tonalElevation = if (selected) 4.dp else 0.dp,
            border = BorderStroke(
                width = if (selected) 2.dp else 1.dp,
                color = borderColor
            )
        ) {
            Row {
                Surface(modifier = Modifier.width(32.dp).height(40.dp), color = palette.dark.primary) {}
                Surface(modifier = Modifier.width(12.dp).height(40.dp), color = palette.light.primary) {}
                Surface(modifier = Modifier.width(12.dp).height(40.dp), color = palette.dark.secondary) {}
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = palette.displayName,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}