package com.anisequel.ui.theme

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
// Aliased rather than imported: this file's own `ExpressiveShapes` shadows the
// token object of the same name, and a bare `import ...ExpressiveShapes` would
// resolve to whichever one wins and silently change every radius below.
private val Corner = com.anisequel.ui.components.expressive.ExpressiveShapes

val ExpressiveShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(Corner.ExtraSmallCorner),
    small = RoundedCornerShape(Corner.SmallCorner),
    medium = RoundedCornerShape(Corner.MediumCorner),
    large = RoundedCornerShape(Corner.LargeCorner),
    extraLarge = RoundedCornerShape(36.dp)
)
