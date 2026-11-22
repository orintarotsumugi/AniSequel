package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.DpiMode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.theme.rememberDeviceDensityDpi
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The Display size half of Type & display.
 *
 * Android's own display-density setting is a number with an implicit default of
 * "whatever this panel reports". Exposing only the number forced every install to
 * draw at 447 dpi, which is fine on a phone and wrong on a tablet. This section
 * makes the two answers separate: Automatic follows the device, Manual keeps the
 * reader's number. Both write one preference pair at the app root, so neither can
 * leave the chosen mode and the chosen number disagreeing.
 *
 * ## Why this no longer has a pointer card to a separate page
 *
 * It used to live on its own page, and the Type page held a card whose only job
 * was a button to this one. Two rows on the Settings shelf named "Type & reading"
 * and "Display size" left the reader to guess which one made the app bigger; the
 * pointer card was the app admitting the guess was hard. Both are one section
 * here, so the pointer has nothing left to point at.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun DisplaySizeSectionBody(
    interfacePreferences: InterfacePreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by interfacePreferences.settings.collectAsStateWithLifecycle(
        initialValue = InterfacePreferences.InterfaceSettings()
    )
    val deviceDpi = rememberDeviceDensityDpi()
    val automatic = settings.dpiMode == DpiMode.AUTOMATIC
    // What the app is actually drawing at. Manual reads the stored number; Automatic
    // reads the panel. The readout is the honest answer even for a hand-edited file.
    val effectiveDpi = if (automatic) deviceDpi else DisplayDpi.clamp(settings.displayDpi)

    SettingsScrollColumn(modifier) {
        SectionCard(
            title = "Display size",
            icon = AppVectorIcons.Tune,
            subtitle = "How large every card, caption and gap is drawn"
        ) {
            Text(
                text = "${settings.dpiMode.displayName} · $effectiveDpi dpi",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(4.dp))
            CollapsibleText(
                text = if (automatic) {
                    "Your device reports $deviceDpi dpi and the app is following it."
                } else {
                    "The app is drawing at $effectiveDpi dpi on every screen, " +
                        "whatever your ${deviceDpi} dpi panel reports."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            SettingsFieldLabel(text = "Mode")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dpi_mode_row")
            ) {
                DpiMode.entries.forEach { mode ->
                    ExpressiveStateChip(
                        label = mode.displayName,
                        selected = mode == settings.dpiMode,
                        onClick = {
                            if (mode != settings.dpiMode) {
                                scope.launch { interfacePreferences.setDpiMode(mode) }
                            }
                        },
                        role = Role.RadioButton,
                        minTouchTargetHeight = 48.dp,
                        onClickLabel = "Use ${mode.displayName} display density",
                        modifier = Modifier.testTag(
                            "dpi_mode_${mode.storageValue}"
                        )
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            CollapsibleText(
                text = when (settings.dpiMode) {
                    DpiMode.AUTOMATIC -> DpiMode.AUTOMATIC.blurb
                    DpiMode.MANUAL -> DpiMode.MANUAL.blurb
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UiSizeSectionCard(interfacePreferences = interfacePreferences)

        SectionCard(
            title = "About density",
            icon = AppVectorIcons.DevicePhone,
            subtitle = "What the number actually changes"
        ) {
            CollapsibleText(
                // The direction is the whole point of the paragraph, and it was
                // written backwards: it said a higher dpi fits *more* on the screen.
                // Density is pixels per dp, so raising it makes every dp - and every
                // card, caption and gap drawn in one - physically larger, which fits
                // less. A reader who followed the old sentence to make the app bigger
                // moved the slider the wrong way and got a finer page.
                text = "Every length in the app is a dp, and a dp is only as large as " +
                    "the density says it is. A higher dpi draws text, cards and gaps " +
                    "larger, so less fits on the screen; a lower one makes everything " +
                    "finer and fits more. Automatic hands that decision back to the " +
                    "device, the same way Android's own display density override does " +
                    "when it is left unset. Your phone reports $deviceDpi dpi; " +
                    "${DisplayDpi.DEFAULT_DPI} dpi is the number a fresh install uses " +
                    "in Manual.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One shelf-level sample of what the two halves do.
 *
 * The hub is where the reader decides which half to open, and both halves are
 * about a change they can already see: the type and the scale they are reading
 * this sentence in. The readout is the same pair of numbers the page below writes,
 * so the sample cannot describe a size the app is not drawing.
 *
 * Deliberately not a slider: this is a picture of the answer, not a second control
 * over the same preference.
 */
@Composable
internal fun TypeSpecimenCard(
    themePreferences: ThemePreferences,
    interfacePreferences: InterfacePreferences,
    modifier: Modifier = Modifier
) {
    val themeSettings by themePreferences.settings.collectAsStateWithLifecycle(initialValue = null)
    val interfaceSettings by interfacePreferences.settings.collectAsStateWithLifecycle(initialValue = null)
    val deviceDpi = rememberDeviceDensityDpi()
    val effectiveDpi = interfaceSettings?.let { settings ->
        if (settings.dpiMode == DpiMode.AUTOMATIC) {
            deviceDpi
        } else {
            DisplayDpi.clamp(settings.displayDpi)
        }
    }
    val automatic = interfaceSettings?.dpiMode == DpiMode.AUTOMATIC

    SectionCard(
        title = "Sample",
        icon = AppVectorIcons.TextBold,
        subtitle = "The type and scale you are reading this in",
        modifier = modifier
    ) {
        Text(
            text = "Aa",
            style = MaterialTheme.typography.displaySmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Sequels you haven't started, sorted by how overdue they are.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = themeSettings?.fontScale?.let { "${(it * 100).roundToInt()}% text" }
                    ?: "Reading your text size",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = when {
                    effectiveDpi == null -> "Reading your display size"
                    automatic -> "Automatic · $effectiveDpi dpi"
                    else -> "Manual · $effectiveDpi dpi"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
