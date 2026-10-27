package com.anisequel.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.SegmentedOption
import com.anisequel.ui.theme.DynamicThemeBuilder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Anime colour matching — content-based dynamic colour.
 *
 * While you view a detail sheet, the app borrows the anime's cover colour the
 * way Dynamic colour borrows the wallpaper. The seed is read from the cover's
 * pixels - quantized and scored, not taken from AniList's single dominant-colour
 * field - so the palette follows the artwork the reader is looking at. "Retain
 * Anime Color" is the half that used to do nothing: when it is on, the cover
 * colour is persisted as the app's own custom hue, so the whole app keeps the
 * anime's palette after the sheet closes — there is nothing to revert.
 *
 * Both switches leave a clear exit: picking a palette, a hue or Reset
 * appearance in the other sections owns the accent again.
 */
@Composable
internal fun AnimeColourSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    val animeColor = remember(settings.animeThemeActive, settings.animeThemeColorHex) {
        if (settings.animeThemeActive) {
            DynamicThemeBuilder.parseHexColor(settings.animeThemeColorHex)
        } else {
            null
        }
    }

    SectionCard(
        title = "Anime colour match",
        icon = AppVectorIcons.AnimeSparkle,
        subtitle = "Borrow the cover art's colour for the whole app",
        iconContainer = MaterialTheme.colorScheme.primaryContainer,
        iconContent = MaterialTheme.colorScheme.onPrimaryContainer
    ) {
        // A live snapshot of the colour currently driving the scheme, so the
        // feature is never a mystery while it is active.
        if (animeColor != null) {
            Surface(
                shape = RoundedCornerShape(ExpressiveShapes.MediumCorner),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner),
                        color = animeColor,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.size(30.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Now matching this anime",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = settings.animeThemeColorHex ?: "cover colour",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Surface(
                        shape = RoundedCornerShape(percent = 50),
                        color = if (settings.animeThemeRetainColor) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        }
                    ) {
                        Text(
                            text = if (settings.animeThemeRetainColor) "Retained" else "Temporary",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (settings.animeThemeRetainColor) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }

        SwitchRow(
            title = "Anime dynamic theme",
            subtitle = "Recolour the whole app while a detail sheet is open.",
            checked = settings.animeThemeActive,
            onCheckedChange = { active ->
                scope.launch {
                    themePreferences.setAnimeThemeActive(active, settings.animeThemeColorHex)
                    if (!active &&
                        settings.customColorHex?.equals(settings.animeThemeColorHex, ignoreCase = true) == true
                    ) {
                        // Turning the switch off ends a retained anime accent:
                        // the custom hue it left behind only makes sense while
                        // the feature is on, so clear the one the anime wrote.
                        themePreferences.setCustomColorHex(null)
                    }
                }
            },
            modifier = Modifier.testTag("anime_theme_active_switch")
        )
        Spacer(modifier = Modifier.height(10.dp))
        SwitchRow(
            title = "Retain anime colour",
            subtitle = "Keep that colour as the app's accent after the sheet.",
            checked = settings.animeThemeRetainColor,
            onCheckedChange = { retain ->
                scope.launch {
                    themePreferences.setAnimeThemeRetainColor(retain)
                    if (retain && settings.animeThemeActive && settings.animeThemeColorHex != null) {
                        // Switching retention on mid-session persists the colour
                        // that is already live; the detail sheet does the same
                        // on every new anime while this stays on.
                        themePreferences.setCustomColorHex(settings.animeThemeColorHex)
                    } else if (!retain) {
                        // Turning Retain off has to actually end the retained
                        // state. The layer outranks every other colour source in
                        // `Theme.kt`, so clearing only the custom hue left the
                        // anime scheme drawing and the switch looked like it did
                        // nothing at all. The sheet is not open on this screen,
                        // so the layer can be stepped off now; it returns the
                        // reader to the theme they had before.
                        if (settings.animeThemeActive) {
                            themePreferences.setAnimeThemeActive(false, null)
                        }
                        if (settings.customColorHex?.equals(settings.animeThemeColorHex, ignoreCase = true) == true) {
                            // Drop the accent the anime wrote, so the toggle has
                            // a visible effect in both directions.
                            themePreferences.setCustomColorHex(null)
                        }
                    }
                }
            },
            modifier = Modifier.testTag("anime_theme_retain_switch")
        )

        Spacer(modifier = Modifier.height(12.dp))
        SettingsFieldLabel(text = "Revert after closing the anime")
        Spacer(modifier = Modifier.height(6.dp))
        val revertOptions = listOf("Immediately", "5 minutes", "10 minutes")
        val revertValues = listOf(0, 300, 600)
        // An unknown stored value falls back to "5 minutes", but a real 0
        // must survive: the previous `.coerceAtLeast(1)` clamped 0 up to 1, so
        // "Immediately" could never be shown, and picking it snapped straight
        // back to "5 minutes". Index -1 (a value not in the list) is the only
        // case the fallback is for.
        val storedRevertIndex = revertValues.indexOf(settings.animeThemeRevertDuration)
        val currentRevertIndex = if (storedRevertIndex >= 0) storedRevertIndex else 1
        // While retaining, there is no revert to configure: the colour is kept,
        // so the picker is shown dimmed rather than silently ignored.
        val revertEnabled = !settings.animeThemeRetainColor
        ExpressivePolygonSegmentedBar(
            // No icons: the same `Restore` glyph three times over is three
            // identical pictures next to three different answers, so it decorated
            // the choice instead of helping it.
            options = revertOptions.map { SegmentedOption(label = it) },
            selectedIndex = currentRevertIndex,
            onSelect = { index ->
                if (revertEnabled) {
                    scope.launch { themePreferences.setAnimeThemeRevertDuration(revertValues[index]) }
                }
            },
            modifier = Modifier
                .alpha(if (revertEnabled) 1f else 0.4f)
                .testTag("anime_theme_revert_duration_row")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (settings.animeThemeRetainColor) {
                "Being retained: the colour stays applied to the app. Pick a palette, a hue, " +
                    "or Reset appearance to take back control."
            } else {
                "Return to your usual theme the moment the sheet closes, or after a set time."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}