package com.example.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shapes for this app's surfaces.
 *
 * M3 Expressive moved shape from a per-component `shape:` parameter to a named
 * set of slots, so components interpolate between the right pair as they change
 * state instead of jumping. Players that differ from the M3 material
 * defaults read as Expressive: containers are rounder (M3 defaults are 7dp,
 * 10dp, 14dp, 16dp and 24dp), so this scale runs 10 / 14 / 20 / 28 / 36 -
 * large enough that cards read as curved panels rather than hard rectangles,
 * but still inside the scale where adjacent slots stay visually related.
 */
val ExpressiveShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp)
)
