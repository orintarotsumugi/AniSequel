package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.RetainedTint
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.DarkColorScheme
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.LightColorScheme
import com.anisequel.ui.theme.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

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

        // The page is grouped the way the reference it was redrawn from groups it:
        // the light-or-dark choice first, then where the colour comes from, then what
        // the surfaces are painted on, then the per-title adaptation. Each label is a
        // heading a screen reader can jump between, which the previous order - one
        // long run of same-sized cards - gave no way to do.
        SettingsSectionLabel("Appearance")
        AppearanceSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        SettingsSectionLabel("Themes")
        AccentColourSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        // Full screen moved to Device Manager, and motion to the Interface page:
        // neither is about what colour the app is, and this page is only about that.
        SettingsSectionLabel("Wallpaper")
        BackgroundSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        SettingsSectionLabel("Poster colour")
        AnimeColourSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        // An opened title's page is an appearance surface too, and it is the one
        // this page can change without a title being open. Kept beside the anime
        // colour section because both are about borrowing a title's artwork.
        SettingsSectionLabel("Anime pages")
        InfoBackdropSection(themePreferences = themePreferences, settings = themeSettings, scope = scope)

        Spacer(modifier = Modifier.height(14.dp))

        val appearanceChanged = themeSettings.themeMode != ThemeMode.DEFAULT ||
            themeSettings.useDynamicColor ||
            themeSettings.paletteId != ThemePalette.ANISDK.id ||
            themeSettings.motionStyle != MotionStyle.DEFAULT ||
            themeSettings.trueBlack ||
            themeSettings.customColorHex != null ||
            // The switch is stored, the layer is not, so testing the stored one is
            // what answers "is there anything here to reset" on a screen where no
            // sheet is open.
            themeSettings.animeThemeEnabled ||
            themeSettings.animeThemeActive ||
            // A retained cover colour is a theme override in its own right now: it
            // tints the scheme after the sheet, so Reset appearance has to be
            // offered for it even if the accent it wrote is cleared some other way.
            themeSettings.retainedAnimeColorHex != null ||
            // Retain and revert are cleared by `reset()` like everything else here,
            // so leaving them out meant a reader who changed only those two had
            // something to reset and no button offering to reset it. Retention
            // defaults to on, which is why the test is negated.
            !themeSettings.animeThemeRetainColor ||
            themeSettings.retainedTint != RetainedTint.DEFAULT ||
            themeSettings.animeThemeRevertDuration != DEFAULT_ANIME_REVERT_SECONDS ||
            themeSettings.appBackgroundColor != null ||
            // Both are Reset appearance's to clear: the backdrop is part of the
            // app's look, not a per-title choice, and leaving it behind would make
            // the button restore everything but the page a title opens on.
            themeSettings.infoBackdropSource != InfoBackdropSource.DEFAULT ||
            themeSettings.infoBackdropBlur != InfoBackdropBlur.DEFAULT ||
            // Full screen is off by default; turning it on is a change, so the
            // reset row has to offer itself for exactly that case.
            themeSettings.immersive

        // The button used to appear with a bare `if`, fully drawn on the next
        // frame - so the page jumped by a button's height the moment the reader
        // changed anything, and jumped back when they reset. The expansion makes
        // the movement the thing they see, matching the reset rows on the
        // Library page.
        AnimatedVisibility(
            visible = appearanceChanged,
            enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
            exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            Column {
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
        subtitle = "Light, dark, or follow the device"
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("theme_mode_row")
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeTile(
                    mode = mode,
                    selected = mode == settings.themeMode,
                    onClick = {
                        if (mode != settings.themeMode) {
                            scope.launch { themePreferences.setThemeMode(mode) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "System follows your phone's light or dark setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One appearance mode as a picture of what it does, not as a word for it.
 *
 * ## Why a mock window rather than a segmented bar
 *
 * The bar said "System / Light / Dark" and the reader had to know which of those
 * words described the app they were about to get. A tile shows a small window in
 * that scheme, so the choice is answered by looking. It is the same reason the
 * seasonal page draws a sky instead of a slider alone, and why the card style on the
 * Interface page is a preview: a setting whose effect is visual is chosen visually.
 *
 * ## Why the window is drawn from the base schemes
 *
 * `LightColorScheme` and `DarkColorScheme` are what the app falls back to before a
 * wallpaper, a palette or a custom hue overrides it. Showing those rather than the
 * live scheme is deliberate: the live scheme is what the preview card above already
 * shows, and a tile that rendered it could not show the *other* mode at all - the
 * one thing a mode chooser has to be able to say.
 */
@Composable
private fun ThemeModeTile(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = when (mode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        // "Follow the phone" has no single scheme to show, so the window is painted
        // half in each: the only honest picture of a mode that answers to another app.
        ThemeMode.SYSTEM -> DarkColorScheme
    }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = if (selected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else {
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            },
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClickLabel = "Use the ${mode.displayName.lowercase()} appearance"
                ) { onClick() }
                .testTag("theme_mode_${mode.name.lowercase()}")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            // "Follow the phone" has no single scheme to show, so the
                            // window is painted half in each. Every other mode gets its
                            // own background outright.
                            if (mode == ThemeMode.SYSTEM) {
                                Brush.horizontalGradient(
                                    listOf(LightColorScheme.background, DarkColorScheme.background)
                                )
                            } else {
                                Brush.verticalGradient(listOf(scheme.background, scheme.background))
                            }
                        )
                        .padding(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WindowDot(scheme.error)
                        WindowDot(scheme.tertiary)
                        WindowDot(scheme.primary)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                    ) {
                        WindowChip(scheme.primaryContainer, modifier = Modifier.weight(1f))
                        WindowChip(scheme.surfaceContainerHighest, modifier = Modifier.weight(1f))
                        WindowChip(scheme.secondaryContainer, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = mode.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

@Composable
private fun WindowDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun WindowChip(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(ExpressiveShapes.pill)
            .background(color)
    )
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
        subtitle = "The canvas every surface is derived from"
    ) {
        // "OLED mode", which is the name a reader meets in the phone's own display
        // settings. The switch has always been about the panel; "true black" described
        // the colour and left the reason out.
        SwitchRow(
            title = "OLED mode",
            subtitle = "Pure black backgrounds drain less power on OLED screens.",
            checked = settings.trueBlack,
            onCheckedChange = { enabled ->
                scope.launch { themePreferences.setTrueBlack(enabled) }
            },
            modifier = Modifier.testTag("true_black_switch")
        )

        Spacer(modifier = Modifier.height(16.dp))
        // `SettingsFieldLabel`, not a hand-rolled bold `labelMedium`: this caption
        // and the ones on the Accent and Jokes cards do the same job, and three
        // private copies is how one of them ends up 12sp bold while the others are
        // 14sp medium.
        SettingsFieldLabel(text = "Canvas colour")
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

/**
 * The background canvas choices; `null` is "follow the theme".
 *
 * These used to be dark colours only, because the canvas override was gated on
 * dark mode and did nothing in light. `applyCanvasColour` now derives the text
 * and surface ramp from whichever chip is picked, so light canvases are honest
 * choices and the light-mode reader gets the same "this changed the whole app"
 * result as the dark-mode one.
 */
private val backgroundColors = listOf(
    null to "Default",
    "#000000" to "Pure Black",
    "#080C14" to "Midnight Navy",
    "#121212" to "Deep Charcoal",
    "#1C1410" to "Warm Espresso",
    "#101820" to "Slate Navy",
    "#F2F0F7" to "Mist White",
    "#F5EFE3" to "Warm Paper"
)

/**
 * Five minutes, which is the default `ThemePreferences.ThemeSettings` stores for
 * the anime revert timer.
 *
 * Repeated here rather than read from the repository because the question this
 * answers is only "has the reader changed anything worth resetting", and the
 * alternative - a public constant on the preferences class - would be a public
 * API for one comparison. `AnimeColourSection` offers the same three values, so
 * if the default moves, both files move with it.
 */
private const val DEFAULT_ANIME_REVERT_SECONDS = 300
