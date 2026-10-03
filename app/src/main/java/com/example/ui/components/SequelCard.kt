package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.MissedSequel

/**
 * Forwards to the real card in `ui.components.cards`.
 *
 * This wrapper exists only so the wider `ui.components` package can expose the
 * card without every caller importing the nested one. It has to mirror the
 * inner signature exactly - when `onHide` was added to the card below and this
 * was left alone, it was the only thing that failed to compile.
 */
@Composable
fun SequelCard(
    sequel: MissedSequel,
    onClick: () -> Unit,
    onAddToPlanning: () -> Unit,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.cards.SequelCard(
        sequel = sequel,
        onClick = onClick,
        onAddToPlanning = onAddToPlanning,
        onHide = onHide,
        modifier = modifier
    )
}