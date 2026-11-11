package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.expressive.ExpressivePolygonSegmentedBar
import com.anisequel.ui.components.expressive.SegmentedOption
import kotlinx.coroutines.launch

/**
 * The quick-add gestures: how long a press has to be, and whether a sideways
 * swipe skips the hold.
 *
 * ## Why the segmented bar rather than `ExpressiveTabBar`
 *
 * It was a tab bar, which is the control for *switching between pages* - it draws
 * two capsules that spring and change colour, and it pads itself 16dp horizontally
 * because it expects to be a bar at the edge of a screen. Inside a card that
 * already has 18dp of padding it made this one control sit 34dp in from the card's
 * edge while the switch below it sat at 18dp, and it overrode the `testTag` the
 * caller passed with its own. `ExpressivePolygonSegmentedBar` is what the Theme
 * page uses for exactly this kind of two-value choice, so the card now matches the
 * rest of Settings and the tag survives.
 */
@Composable
fun QuickAddSectionCard(
    quickAddPreferences: QuickAddPreferences,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val settings by quickAddPreferences.settings.collectAsState(
        initial = QuickAddPreferences.QuickAddSettings()
    )

    SectionCard(
        title = "Quick add gestures",
        icon = AppVectorIcons.Play,
        subtitle = "Hold or swipe to add to Currently Watching",
        modifier = modifier.testTag("quick_add_settings_card")
    ) {
        // The real control, not a drawing of one: it reads the same
        // `LocalConfirmPlanningAdd` the cards do, so the tick/cross the reader
        // sees here is the tick/cross they will see on Home. Tapping it writes
        // nothing - the two callbacks are empty because a settings page has no
        // title to plan - which is what makes the preview safe.
        SettingsFieldLabel(text = "Preview")
        Spacer(modifier = Modifier.height(6.dp))
        QuickAddPlanningButton(
            mediaId = 0,
            isAddedToPlanning = false,
            isAddedToWatching = false,
            isBusy = false,
            onAddToPlanning = {},
            onAddToWatching = {},
            modifier = Modifier
                .fillMaxWidth()
                .testTag("quick_add_preview_button")
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Tap it to see the confirmation, or hold it to fill toward " +
                "Currently Watching. Nothing is saved from this page.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(10.dp))

        SettingsFieldLabel(text = "Hold duration")
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Hold the button on any anime card to move it from Planning to " +
                "Currently Watching without opening it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(10.dp))

        // `QuickAddPreferences` coerces anything that is not 5 back to 3, so the
        // index is a two-way question rather than a lookup.
        val holdOptions = listOf("3 seconds", "5 seconds")
        val selectedIndex = if (settings.holdDurationSeconds == 5) 1 else 0

        ExpressivePolygonSegmentedBar(
            options = holdOptions.map { SegmentedOption(label = it) },
            selectedIndex = selectedIndex,
            onSelect = { index ->
                val chosen = if (index == 1) 5 else 3
                coroutineScope.launch {
                    quickAddPreferences.setHoldDuration(chosen)
                }
            },
            modifier = Modifier.testTag("hold_duration_selector")
        )

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        // On by default, and deliberately so: the tick/cross is a confirmation,
        // not an extra step the reader has to discover. Turning it off restores
        // the single-tap add that shipped before it.
        SwitchRow(
            title = "Confirm before adding",
            subtitle = "Ask with a green tick or red cross before a title joins Planning.",
            checked = settings.confirmAdds,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    quickAddPreferences.setConfirmAdds(enabled)
                }
            },
            modifier = Modifier.testTag("confirm_planning_add_switch")
        )

        Spacer(modifier = Modifier.height(8.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(8.dp))

        SwitchRow(
            title = "Swipe to watch",
            subtitle = "Swipe the card's button sideways to set Currently Watching immediately.",
            checked = settings.swipeEnabled,
            onCheckedChange = { enabled ->
                coroutineScope.launch {
                    quickAddPreferences.setSwipeEnabled(enabled)
                }
            },
            modifier = Modifier.testTag("swipe_to_watch_switch")
        )
    }
}
