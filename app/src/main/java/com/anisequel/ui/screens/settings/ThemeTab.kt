package com.anisequel.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
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
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.SegmentedOption
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun ThemeMode.icon(): ImageVector = when (this) {
    ThemeMode.SYSTEM -> Icons.Outlined.BrightnessAuto
    ThemeMode.LIGHT -> AppVectorIcons.ThemeLight
    ThemeMode.DARK -> AppVectorIcons.ThemeDark
}

private fun MotionStyle.icon(): ImageVector = when (this) {
    MotionStyle.INSTANT -> AppVectorIcons.MotionInstant
    MotionStyle.SMOOTH -> AppVectorIcons.MotionSmooth
    MotionStyle.CHILL -> AppVectorIcons.MotionChill
}

/**
 * The dedicated Theme tab.
 *
 * Ordered as three questions a reader asks in sequence: how the app looks
 * (appearance mode, then how dark the background is), where the colour comes
 * from (accent colour, then the anime cover-colour adaptation), and how it
 * moves. A live preview hero leads, Reset appearance trails. The heavy colour
 * sections live in their own files so this one stays a readable index.
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

        AppearanceSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        BackgroundSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        AccentColourSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        AnimeColourSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        MotionSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

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

@Composable
private fun AppearanceSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Appearance",
        icon = AppVectorIcons.ThemeDark,
        subtitle = "Light, dark, or follow the device."
    ) {
        ExpressivePolygonSegmentedBar(
            options = ThemeMode.entries.map { mode ->
                SegmentedOption(label = mode.displayName, icon = mode.icon())
            },
            selectedIndex = ThemeMode.entries.indexOf(settings.themeMode),
            onSelect = { index ->
                scope.launch { themePreferences.setThemeMode(ThemeMode.entries[index]) }
            },
            modifier = Modifier.testTag("theme_mode_row")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "System follows your phone's own light/dark setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BackgroundSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Background",
        icon = AppVectorIcons.SectionBackground,
        subtitle = "How dark the app is in dark mode.",
        iconContainer = MaterialTheme.colorScheme.secondaryContainer,
        iconContent = MaterialTheme.colorScheme.onSecondaryContainer
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
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backgroundColors.forEach { (hex, name) ->
                CanvasChip(
                    hex = hex,
                    name = name,
                    isSelected = settings.appBackgroundColor?.equals(hex, ignoreCase = true) == true ||
                        (hex == null && settings.appBackgroundColor == null),
                    onClick = {
                        scope.launch { themePreferences.setAppBackgroundColor(hex) }
                    }
                )
            }
        }
    }
}

/**
 * One canvas choice as a pill chip: a leading dot of the actual colour plus the
 * name. `null` is "follow the theme", drawn with the current surface colour.
 */
@Composable
private fun CanvasChip(
    hex: String?,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dotColor = if (hex != null) {
        DynamicThemeBuilder.parseHexColor(hex) ?: Color.DarkGray
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val container by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "canvas_chip_container"
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "canvas_chip_content"
    )

    Surface(
        shape = ExpressiveShapes.pill,
        color = container,
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outlineVariant
        ),
        modifier = Modifier
            .bouncyPress(pressedScale = 0.92f)
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MotionSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Motion",
        icon = AppVectorIcons.SectionMotion,
        subtitle = "How the app animates.",
        iconContainer = MaterialTheme.colorScheme.tertiaryContainer,
        iconContent = MaterialTheme.colorScheme.onTertiaryContainer
    ) {
        ExpressivePolygonSegmentedBar(
            options = MotionStyle.entries.map { style ->
                SegmentedOption(label = style.displayName, icon = style.icon())
            },
            selectedIndex = MotionStyle.entries.indexOf(settings.motionStyle),
            onSelect = { index ->
                scope.launch { themePreferences.setMotionStyle(MotionStyle.entries[index]) }
            },
            modifier = Modifier.testTag("motion_style_row")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Instant = no motion at all. Smooth = calm fades with no overshoot. Chill = full expressive physics.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The background canvas choices; `null` is "follow the theme". */
private val backgroundColors = listOf(
    null to "Default",
    "#000000" to "Pure Black",
    "#080C14" to "Midnight Navy",
    "#121212" to "Deep Charcoal",
    "#1C1410" to "Warm Espresso",
    "#101820" to "Slate Navy"
)