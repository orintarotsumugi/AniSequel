package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.DpiMode
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.theme.rememberDeviceDensityDpi
import kotlinx.coroutines.launch

/**
 * Display size: one page for how large the app draws, with an advanced mode.
 *
 * Android's own display-density setting is a number with an implicit default of
 * "whatever this panel reports". Exposing only the number forced every install to
 * draw at 447 dpi, which is fine on a phone and wrong on a tablet. This page makes
 * the two answers separate: Automatic follows the device, Manual keeps the reader's
 * number. Both write one preference pair at the app root, so neither can leave the
 * chosen mode and the chosen number disagreeing.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DpiSettingsTab(
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
 * A small door from Type & reading to the full page.
 *
 * Text size stays where it is - it scales only text - while the density scales
 * every dp in the app. A pointer here is enough: duplicating the slider would
 * leave two controls on one preference, and the one off screen would go stale.
 */
@Composable
internal fun DisplaySizePointerCard(
    effectiveDpi: Int,
    automatic: Boolean,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = "Display size",
        icon = AppVectorIcons.Tune,
        subtitle = "Every card, caption and gap, not just text",
        modifier = modifier
    ) {
        Text(
            text = if (automatic) {
                "Automatic · following your device ($effectiveDpi dpi)"
            } else {
                "Manual · $effectiveDpi dpi"
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(10.dp))
        SettingsButton(
            text = "Display size settings",
            onClick = onOpen,
            icon = AppVectorIcons.Tune,
            variant = SettingsButtonVariant.Outlined,
            fillWidth = true,
            testTag = "open_display_size"
        )
    }
}
