package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shapes for this app's surfaces.
 *
 * M3 Expressive moved shape from a per-component `shape:` parameter to a named
 * set of slots, so components interpolate between the right pair as they change
 * state instead of jumping. These are the expressive defaults with the corners
 * pulled in: this app's cards are dense lists read at arm's length, not
 * full-bleed hero surfaces.
 */
val ExpressiveShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(26.dp),
    extraLarge = RoundedCornerShape(34.dp)
)