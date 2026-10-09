package com.example.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
import com.example.data.repository.QuickAddPreferences
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveTabBar
import kotlinx.coroutines.launch

/**
 * Settings section card for configuring quick add actions:
 *  - Hold duration (3 seconds vs 5 seconds) to switch to Currently Watching
 *  - Enabling or disabling horizontal swipe to watch gesture
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
        title = "Quick Add Gestures",
        icon = AppVectorIcons.Play,
        subtitle = "Hold or swipe to add to Currently Watching.",
        modifier = modifier.testTag("quick_add_settings_card")
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            SettingsFieldLabel(text = "Hold duration to switch to Watching")
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Hold the button on any anime card to automatically switch from Planning to Currently Watching.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            val holdOptions = listOf("3 seconds", "5 seconds")
            val selectedIndex = if (settings.holdDurationSeconds == 5) 1 else 0

            ExpressiveTabBar(
                tabs = holdOptions,
                selectedIndex = selectedIndex,
                onSelect = { index ->
                    val chosen = if (index == 1) 5 else 3
                    coroutineScope.launch {
                        quickAddPreferences.setHoldDuration(chosen)
                    }
                },
                modifier = Modifier.fillMaxWidth().testTag("hold_duration_selector")
            )

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(8.dp))

            SwitchRow(
                title = "Swipe to Watch",
                subtitle = "Swipe the card's button horizontally to confirm Currently Watching immediately.",
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
}
