package com.example.ui.components.cards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.MissedSequel

/**
 * Quick-action list button for missed sequel cards.
 * Supports quick tap to plan, hold to add to Currently Watching with progress fill,
 * and horizontal swipe to confirm Currently Watching.
 */
@Composable
fun SequelPlanningButton(
    sequel: MissedSequel,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit = {},
    modifier: Modifier = Modifier,
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    HoldAndSwipePlanningButton(
        sequel = sequel,
        onAddToPlanning = onAddToPlanning,
        onAddToWatching = onAddToWatching,
        holdDurationSeconds = holdDurationSeconds,
        swipeEnabled = swipeEnabled,
        modifier = modifier
    )
}
