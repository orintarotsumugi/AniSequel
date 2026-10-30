package com.anisequel.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.isSystemInDarkTheme
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
import com.anisequel.data.repository.RetainedTint
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
 * field - so the palette follows the artwork the reader is looking at.
 *
 * "Retain anime colour" decides what survives the sheet. Retention stores the
 * cover colour as the app's accent *and* as the seed the retained scheme is built
 * from, so closing a title no longer drops the background back to the palette's
 * navy: the tint the reader was looking at stays, and the next launch finds it.
 * With retention off the colour is a preview on a timer and the app returns to
 * its own theme. It used to be one stored flag doing both jobs, so a retained
 * Chainsaw Man left every screen tinted *forever* with no way back, and a
 * temporary colour whose timer was an in-memory job came back from a restart
 * still applied. The marker is the fix for both: it is stored, so it survives a
 * restart, and it is removed by every exit this screen offers.
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
    val dark = isSystemInDarkTheme()
    // The colour the app is drawn with right now: the layer while a title is on
    // screen, otherwise whatever this feature kept as the accent. The hex is
    // printed as text and the *swatch* is the derived accent, so a dark poster
    // does not produce an unidentifiable black chip in a dark app.
    val liveHex = settings.animeThemeColorHex ?: settings.retainedAnimeColorHex
    val layerIsLive = settings.animeThemeColorHex != null
    val swatch = remember(liveHex, dark) {
        DynamicThemeBuilder.parseHexColor(liveHex)?.let {
            DynamicThemeBuilder.accentColor(it, dark = dark)
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
        if (swatch != null) {
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
                        color = swatch,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant
                        ),
                        modifier = Modifier.size(30.dp)
                    ) {}
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (layerIsLive) "Now matching this anime" else "Keeping this show's colour",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (layerIsLive) {
                                liveHex ?: "cover colour"
                            } else {
                                "Accent and background, kept"
                            },
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
            checked = settings.animeThemeEnabled,
            onCheckedChange = { enabled ->
                // One call. Ending the feature also ends the layer and takes back
                // the accent *only if this feature wrote it*, which is the part
                // that used to be guessed by comparing hexes here.
                scope.launch { themePreferences.setAnimeThemeEnabled(enabled) }
            },
            modifier = Modifier.testTag("anime_theme_active_switch")
        )
        Spacer(modifier = Modifier.height(10.dp))
        SwitchRow(
            title = "Retain anime colour",
            subtitle = "Keep the background and accent after the sheet closes.",
            checked = settings.animeThemeRetainColor,
            onCheckedChange = { retain ->
                // The preference, the accent it writes or takes back, and the
                // layer that has to stop outranking every other source when
                // retention ends - all in the repository, because doing that
                // bookkeeping a row at a time is what made this switch look dead.
                scope.launch { themePreferences.setAnimeThemeRetainColor(retain) }
            },
            modifier = Modifier.testTag("anime_theme_retain_switch")
        )

        Spacer(modifier = Modifier.height(14.dp))
        SettingsFieldLabel(text = "Background tint after closing the anime")
        Spacer(modifier = Modifier.height(6.dp))
        // Only meaningful while retaining: with a temporary colour there is no
        // "after" to tint. The control stays visible and disabled rather than
        // appearing with the switch, because a row that shows up on a tap moves
        // every control below it under the reader's finger.
        val tintEnabled = settings.animeThemeRetainColor
        ExpressivePolygonSegmentedBar(
            options = RetainedTint.entries.map { SegmentedOption(label = it.displayName) },
            selectedIndex = RetainedTint.entries.indexOf(settings.retainedTint),
            onSelect = { index ->
                if (tintEnabled) {
                    scope.launch { themePreferences.setRetainedTint(RetainedTint.entries[index]) }
                }
            },
            modifier = Modifier
                .alpha(if (tintEnabled) 1f else 0.4f)
                .testTag("anime_retained_tint_row")
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Full keeps the sheet's whole background. Subtle keeps the " +
                "show's colour in the accent and buttons while your palette keeps " +
                "the surface. The sheet itself always uses the full tint.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))
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
                "Being kept: the colour survives the sheet and the next launch, tinted " +
                    "background included. Pick a palette, a hue, or Reset appearance to " +
                    "take it back."
            } else {
                "Return to your usual theme the moment the sheet closes, or after a set time."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}