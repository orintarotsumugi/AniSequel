package com.example.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrightnessAuto
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import kotlinx.coroutines.launch

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    ThemeMode.LIGHT -> AppVectorIcons.ThemeLight
    ThemeMode.DARK -> AppVectorIcons.ThemeDark
}

/**
 * The dedicated Theme tab: every visual choice in one place, brand palettes
 * plus motion style plus OLED black, anime theme adaptation, and background colors,
 * all expressive and M3-schemed.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
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
            title = "Dark mode",
            icon = AppVectorIcons.ThemeDark,
            subtitle = "When the app goes dark."
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

        SectionCard(
            title = "Anime Theme Adaptation",
            icon = AppVectorIcons.AnimeSparkle,
            subtitle = "Adapt UI colors to viewed anime with immersive dark theme."
        ) {
            SwitchRow(
                title = "Anime Dynamic Theme",
                subtitle = "Apply anime cover color scheme when viewing anime.",
                checked = themeSettings.animeThemeActive,
                onCheckedChange = { active ->
                    scope.launch { themePreferences.setAnimeThemeActive(active, themeSettings.animeThemeColorHex) }
                },
                modifier = Modifier.testTag("anime_theme_active_switch")
            )
            Spacer(modifier = Modifier.height(10.dp))
            SwitchRow(
                title = "Retain Anime Color",
                subtitle = "Keep anime color for Material You UI design.",
                checked = themeSettings.animeThemeRetainColor,
                onCheckedChange = { retain ->
                    scope.launch { themePreferences.setAnimeThemeRetainColor(retain) }
                },
                modifier = Modifier.testTag("anime_theme_retain_switch")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Revert Timer (After leaving anime info)",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            val revertOptions = listOf("Immediately", "5 Minutes", "10 Minutes")
            val revertValues = listOf(0, 300, 600)
            val currentRevertIndex = revertValues.indexOf(themeSettings.animeThemeRevertDuration).coerceAtLeast(1)
            ExpressivePolygonSegmentedBar(
                options = revertOptions.map { SegmentedOption(label = it, icon = AppVectorIcons.Restore) },
                selectedIndex = currentRevertIndex,
                onSelect = { index ->
                    scope.launch { themePreferences.setAnimeThemeRevertDuration(revertValues[index]) }
                },
                modifier = Modifier.testTag("anime_theme_revert_duration_row")
            )
        }

        SectionCard(
            title = "Colour palette",
            icon = AppVectorIcons.SectionAppearance,
            subtitle = "The accent colour for the whole app."
        ) {
            Text(
                text = "Custom palettes apply when Dynamic colours is off; when it is on, the wallpaper wins.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemePalette.entries.forEach { palette ->
                    PaletteSwatch(
                        palette = palette,
                        selected = themeSettings.paletteId == palette.id && themeSettings.customColorHex == null && !themeSettings.animeThemeActive,
                        onClick = {
                            scope.launch {
                                themePreferences.setCustomColorHex(null)
                                themePreferences.setAnimeThemeActive(false, null)
                                themePreferences.setPalette(palette.id)
                            }
                        }
                    )
                }
            }
        }

        SectionCard(
            title = "Custom app colour",
            icon = AppVectorIcons.AnimeSparkle,
            subtitle = "Pick any accent hue for the entire app."
        ) {
            val customHues = listOf(
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

            Text(
                text = if (themeSettings.customColorHex != null) {
                    "Active custom colour: ${themeSettings.customColorHex}"
                } else {
                    "Select a hue to override palette colours dynamically."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                customHues.forEach { (hex, name) ->
                    val isSelected = themeSettings.customColorHex.equals(hex, ignoreCase = true)
                    val color = DynamicThemeBuilder.parseHexColor(hex) ?: Color.Gray

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = color,
                        tonalElevation = if (isSelected) 6.dp else 1.dp,
                        border = if (isSelected) {
                            androidx.compose.foundation.BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface)
                        } else null,
                        modifier = Modifier
                            .size(38.dp)
                            .clip(MaterialTheme.shapes.small)
                            .bouncyPress(pressedScale = 0.90f)
                            .clickable {
                                scope.launch {
                                    if (isSelected) {
                                        themePreferences.setCustomColorHex(null)
                                    } else {
                                        themePreferences.setCustomColorHex(hex)
                                    }
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
        }

        SectionCard(
            title = "App Background Color",
            icon = AppVectorIcons.SectionAppearance,
            subtitle = "Change the full background color of the app."
        ) {
            val bgColors = listOf(
                null to "Default",
                "#000000" to "Pure Black",
                "#080C14" to "Midnight Navy",
                "#121212" to "Deep Charcoal",
                "#1C1410" to "Warm Espresso",
                "#101820" to "Slate Navy"
            )

            Text(
                text = "Customize the background canvas color across all screens.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                bgColors.forEach { (hex, name) ->
                    val isSelected = themeSettings.appBackgroundColor.equals(hex, ignoreCase = true) || (hex == null && themeSettings.appBackgroundColor == null)
                    val color = if (hex != null) DynamicThemeBuilder.parseHexColor(hex) ?: Color.DarkGray else MaterialTheme.colorScheme.surfaceContainer

                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = color,
                        tonalElevation = if (isSelected) 6.dp else 1.dp,
                        border = androidx.compose.foundation.BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier
                            .size(54.dp, 38.dp)
                            .clip(MaterialTheme.shapes.small)
                            .bouncyPress(pressedScale = 0.90f)
                            .clickable {
                                scope.launch {
                                    themePreferences.setAppBackgroundColor(hex)
                                }
                            }
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

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

        SectionCard(
            title = "System colour",
            icon = AppVectorIcons.SectionAniList,
            subtitle = "Material You wallpaper colours."
        ) {
            SwitchRow(
                title = "Dynamic colours",
                subtitle = "Match your wallpaper.",
                checked = themeSettings.useDynamicColor,
                onCheckedChange = { enabled ->
                    scope.launch { themePreferences.setUseDynamicColor(enabled) }
                },
                modifier = Modifier.testTag("dynamic_color_switch")
            )
        }

        SectionCard(
            title = "Dark surface",
            icon = AppVectorIcons.ThemeDark,
            subtitle = "How black the dark theme is."
        ) {
            SwitchRow(
                title = "True black",
                subtitle = "Pure black backgrounds drain less power on OLED screens.",
                checked = themeSettings.trueBlack,
                onCheckedChange = { enabled ->
                    scope.launch { themePreferences.setTrueBlack(enabled) }
                },
                modifier = Modifier.testTag("true_black_switch")
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
            border = androidx.compose.foundation.BorderStroke(
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
