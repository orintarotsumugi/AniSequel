/*
 * Copyright 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.anisequel.ui.components.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.DefaultFillType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * The Material icons this app draws, in the shape the Compose icon API expects.
 *
 * This is a vendored subset of `androidx.compose.material:material-icons-core` and
 * `material-icons-extended` 1.7.8, the versions the Compose BOM pinned. It exists
 * for build time, not for style: R8 was spending about eleven minutes of every
 * release shrinking roughly thirty thousand icon classes, almost all of which no
 * screen references. The vectors below are the generated Material sources for the
 * icons the app actually imports, so `AppVectorIcons` and the screens that read
 * `Icons.Filled.Movie` did not have to change which artwork they name.
 *
 * The generated icon files are under the same `filled`, `outlined` and
 * `automirrored.filled` packages, which is why the imports read like the library's
 * own. Apache-2.0, as the header on each file says.
 *
 * Adding an icon means copying its generated vector from the Material artifact
 * (or drawing one in [com.anisequel.ui.components.AppVectorIcons]) - not putting
 * the artifact back, which is what this file is here to prevent.
 */
object Icons {
    /** Filled icons, the baseline Material theme. */
    object Filled

    /** Outlined icons: a thin stroke and empty space. */
    object Outlined

    /** Icons that mirror themselves in a right-to-left layout. */
    object AutoMirrored {
        /** Auto-mirrored filled icons. */
        object Filled
    }

    /** Alias for [Filled], as the library spells it. */
    val Default = Filled
}

/**
 * Utility delegate to construct a Material icon with default size information.
 * This is used by the generated icons below, and should not be used manually.
 *
 * @param name the full name of the generated icon
 * @param autoMirror determines if the vector asset should automatically be mirrored
 * for right-to-left locales
 * @param block builder lambda to add paths to this vector asset
 */
inline fun materialIcon(
    name: String,
    autoMirror: Boolean = false,
    block: ImageVector.Builder.() -> ImageVector.Builder
): ImageVector = ImageVector.Builder(
    name = name,
    defaultWidth = MaterialIconDimension.dp,
    defaultHeight = MaterialIconDimension.dp,
    viewportWidth = MaterialIconDimension,
    viewportHeight = MaterialIconDimension,
    autoMirror = autoMirror
).block().build()

/**
 * Adds a vector path to this icon with Material defaults.
 *
 * @param fillAlpha fill alpha for this path
 * @param strokeAlpha stroke alpha for this path
 * @param pathFillType [PathFillType] for this path
 * @param pathBuilder builder lambda to add commands to this path
 */
inline fun ImageVector.Builder.materialPath(
    fillAlpha: Float = 1f,
    strokeAlpha: Float = 1f,
    pathFillType: PathFillType = DefaultFillType,
    pathBuilder: PathBuilder.() -> Unit
) =
    path(
        fill = SolidColor(Color.Black),
        fillAlpha = fillAlpha,
        stroke = null,
        strokeAlpha = strokeAlpha,
        strokeLineWidth = 1f,
        strokeLineCap = StrokeCap.Butt,
        strokeLineJoin = StrokeJoin.Bevel,
        strokeLineMiter = 1f,
        pathFillType = pathFillType,
        pathBuilder = pathBuilder
    )

// All Material icons are 24dp by 24dp, with a viewport size of 24 by 24.
@PublishedApi
internal const val MaterialIconDimension = 24f
