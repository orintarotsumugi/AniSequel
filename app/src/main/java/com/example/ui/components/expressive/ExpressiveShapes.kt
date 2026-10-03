package com.example.ui.components.expressive

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.graphics.shapes.RoundedPolygon

/**
 * Shape tokens and utilities for Material 3 Expressive surfaces.
 */
object ExpressiveShapes {
    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val orb: RoundedPolygon get() = MaterialShapes.Cookie9Sided

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val burst: RoundedPolygon get() = MaterialShapes.SoftBurst

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val diamond: RoundedPolygon get() = MaterialShapes.Diamond

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val pill: RoundedPolygon get() = MaterialShapes.Pill

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val pillSoft: RoundedPolygon get() = MaterialShapes.Cookie7Sided

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val cookie4: RoundedPolygon get() = MaterialShapes.Cookie4Sided

    @OptIn(ExperimentalMaterial3ExpressiveApi::class)
    val flower: RoundedPolygon get() = MaterialShapes.Flower
}

/**
 * Converts a [RoundedPolygon] to a Jetpack Compose [androidx.compose.ui.graphics.Shape].
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun expressiveShape(polygon: RoundedPolygon) = polygon.toShape()
