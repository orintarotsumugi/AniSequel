package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.MissedSequel

/**
 * Forwards to the real card in `ui.components.cards`.
 *
 * This wrapper exists only so the wider `ui.components` package can expose the
 * card without every caller importing the nested one.
 */
@Composable
fun SequelCard(
    sequel: MissedSequel,
    onClick: () -> Unit,
    onAddToPlanning: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier,
    onAddToWatching: () -> Unit = {},
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    com.example.ui.components.cards.SequelCard(
        sequel = sequel,
        onClick = onClick,
        onAddToPlanning = onAddToPlanning,
        onHide = onHide,
        modifier = modifier,
        onAddToWatching = onAddToWatching,
        holdDurationSeconds = holdDurationSeconds,
        swipeEnabled = swipeEnabled
    )
}
