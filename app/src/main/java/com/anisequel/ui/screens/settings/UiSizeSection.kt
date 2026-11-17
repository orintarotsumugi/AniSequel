package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.UiSize
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.rememberBoundSliderState
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * UI size, on Type & reading rather than Interface.
 *
 * It is a reading size, not a layout: the picker changes how large every card,
 * caption and gap is drawn, exactly like the text scale above it in that page. It
 * used to sit under "App layout", where it was one more control in a card about
 * the shelf and the bar, and a reader looking for "make the app bigger" had no
 * reason to open a layout page for it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UiSizeSectionCard(interfacePreferences: InterfacePreferences) {
    val scope = rememberCoroutineScope()
    val interfaceSettings by interfacePreferences.settings.collectAsState(
        initial = InterfacePreferences.InterfaceSettings()
    )
    // The density draft, keyed on the stored value: density re-measures every dp in
    // the app, so a live value would resize the page - and the slider's own track -
    // while the finger is still on it. The preference is written on release, and
    // until then the number beside the label is what will be saved.
    var dpiDraft by rememberSaveable(interfaceSettings.displayDpi) {
        mutableStateOf(interfaceSettings.displayDpi.toFloat())
    }

    SectionCard(
        title = "UI size",
        icon = AppVectorIcons.Tune,
        subtitle = "How large every card, caption and gap is drawn - " +
            "the same on every screen until you say otherwise."
    ) {
        // The named sizes first, the slider second. A reader who wants the app
        // bigger should not have to know that the number going up means the
        // elements going down, and a preset is one tap where the slider is a
        // drag. The slider stays because 447 is not a size, it is a number.
        SettingsFieldLabel(text = "Size")
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("ui_size_row")
        ) {
            UiSize.entries.forEach { option ->
                ExpressiveStateChip(
                    label = option.displayName,
                    selected = UiSize.of(dpiDraft.roundToInt()) == option,
                    onClick = {
                        if (interfaceSettings.displayDpi != option.dpi) {
                            dpiDraft = option.dpi.toFloat()
                            scope.launch { interfacePreferences.setDisplayDpi(option.dpi) }
                        }
                    },
                    minTouchTargetHeight = 48.dp,
                    role = Role.RadioButton,
                    onClickLabel = "Draw the app at the ${option.displayName} UI size",
                    modifier = Modifier.testTag("ui_size_${option.name.lowercase()}")
                )
            }
        }
        CollapsibleText(
            text = (UiSize.of(dpiDraft.roundToInt())?.blurb
                ?: "Between two named sizes - the slider below has the exact value.") +
                " This is the app's own size, not the phone's font setting: it scales " +
                "cards, text and spacing together, so a layout that fits a small phone " +
                "fits at every size you can pick.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SettingsFieldLabel(text = "Exact size")
            Text(
                text = "${DisplayDpi.clamp(dpiDraft.roundToInt())} dpi",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Slider(
            // Bound, not owned: Reset writes this preference from the button below,
            // and a slider that only read its first value would keep the old thumb.
            state = rememberBoundSliderState(
                value = dpiDraft,
                // The same divisor rule as the lift slider: ticks follow the range
                // and step in [DisplayDpi] rather than being counted by hand.
                steps = ((DisplayDpi.MAX_DPI - DisplayDpi.MIN_DPI) / DisplayDpi.STEP_DPI - 1)
                    .coerceAtLeast(0),
                trackRange = DisplayDpi.MIN_DPI.toFloat()..DisplayDpi.MAX_DPI.toFloat()
            ),
            onValueChange = { dpiDraft = it },
            onValueChangeFinished = {
                scope.launch { interfacePreferences.setDisplayDpi(dpiDraft.roundToInt()) }
            },
            modifier = Modifier.testTag("display_density_slider")
        )
        CollapsibleText(
            // Said plainly because it is the surprising part: this is not the
            // phone's panel, and it applies to this very page.
            text = "Every length in the app is a dp, and a dp is only as big as the " +
                "density says: a higher number fits more on the page, a lower one draws " +
                "text, cards and gaps larger. The value applies to the whole app - this " +
                "page included, the moment you let go - and does not depend on what the " +
                "phone's own panel reports. ${DisplayDpi.DEFAULT_DPI} dpi is the shipped " +
                "default.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        SettingsButton(
            text = "Reset size",
            onClick = {
                scope.launch { interfacePreferences.setDisplayDpi(DisplayDpi.DEFAULT_DPI) }
            },
            icon = AppVectorIcons.Refresh,
            variant = SettingsButtonVariant.Outlined,
            // Disabled rather than hidden at the default: a control that vanishes
            // when it has nothing to do looks like a missing feature, and a reader
            // who does not know the default cannot tell where they are without it.
            enabled = interfaceSettings.displayDpi != DisplayDpi.DEFAULT_DPI,
            fillWidth = true,
            testTag = "display_density_reset"
        )
    }
}
