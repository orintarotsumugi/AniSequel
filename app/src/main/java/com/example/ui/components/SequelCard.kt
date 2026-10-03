package com.example.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.MissedSequel

@Composable
fun SequelCard(
    sequel: MissedSequel,
    onClick: () -> Unit,
    onAddToPlanning: () -> Unit,
    modifier: Modifier = Modifier
) {
    com.example.ui.components.cards.SequelCard(
        sequel = sequel,
        onClick = onClick,
        onAddToPlanning = onAddToPlanning,
        modifier = modifier
    )
}
