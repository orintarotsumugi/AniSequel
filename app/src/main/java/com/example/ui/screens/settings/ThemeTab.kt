package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.graphics.Shape
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
import com.example.ui.theme.ThemePalette
import com.example.ui.theme.supportsDynamicColor
import kotlinx.coroutines.launch

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    ThemeMode.LIGHT -> AppVectorIcons.ThemeLight
    ThemeMode.DARK -> AppVectorIcons.ThemeDark
}

/**
 * The dedicated Theme tab: every visual choice in one place, brand palettes
 * plus motion style plus OLED black, all expressive and M3-schemed.
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
            title = "Colour palette",
            icon = AppVectorIcons.SectionAppearance,
            subtitle = "Pick the accent from every screen."
        ) {
            Text(
                text = "Used whenever Dynamic colours is off. Dynamic wins when it is on.",
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
                        selected = themeSettings.paletteId == palette.id,
                        onClick = { scope.launch { themePreferences.setPalette(palette.id) } }
                    )
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
                text = "Expressive = the designed bouncy physics. Standard = calmer fades that skip the overshoot.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SectionCard(
            title = "System colour",
            icon = AppVectorIcons.SectionAniList,
            subtitle = "Material You follows your phone."
        ) {
            SwitchRow(
                title = "Dynamic colours",
                subtitle = if (supportsDynamicColor) {
                    "Match your wallpaper."
                } else {
                    "Needs Android 12+. This device is on Android ${android.os.Build.VERSION.RELEASE}."
                },
                checked = themeSettings.useDynamicColor,
                enabled = supportsDynamicColor,
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
            themeSettings.trueBlack

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
    Column(
        modifier = Modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Surface(
            modifier = Modifier.size(56.dp, 40.dp),
            shape = MaterialTheme.shapes.small,
            color = palette.dark.primaryContainer,
            tonalElevation = 0.dp,
            border = if (selected) {
                androidx.compose.foundation.BorderStroke(2.dp, palette.dark.primary)
            } else {
                androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            }
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
