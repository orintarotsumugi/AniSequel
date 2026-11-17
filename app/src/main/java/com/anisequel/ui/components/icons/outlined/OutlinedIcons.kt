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

package com.anisequel.ui.components.icons.outlined

import androidx.compose.ui.graphics.vector.ImageVector
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.materialIcon
import com.anisequel.ui.components.icons.materialPath

public val Icons.Outlined.Api: ImageVector
    get() {
        if (_api != null) {
            return _api!!
        }
        _api = materialIcon(name = "Outlined.Api") {
            materialPath {
                moveTo(14.0f, 12.0f)
                lineToRelative(-2.0f, 2.0f)
                lineToRelative(-2.0f, -2.0f)
                lineToRelative(2.0f, -2.0f)
                lineTo(14.0f, 12.0f)
                close()
                moveTo(12.0f, 6.0f)
                lineToRelative(2.12f, 2.12f)
                lineToRelative(2.5f, -2.5f)
                lineTo(12.0f, 1.0f)
                lineTo(7.38f, 5.62f)
                lineToRelative(2.5f, 2.5f)
                lineTo(12.0f, 6.0f)
                close()
                moveTo(6.0f, 12.0f)
                lineToRelative(2.12f, -2.12f)
                lineToRelative(-2.5f, -2.5f)
                lineTo(1.0f, 12.0f)
                lineToRelative(4.62f, 4.62f)
                lineToRelative(2.5f, -2.5f)
                lineTo(6.0f, 12.0f)
                close()
                moveTo(18.0f, 12.0f)
                lineToRelative(-2.12f, 2.12f)
                lineToRelative(2.5f, 2.5f)
                lineTo(23.0f, 12.0f)
                lineToRelative(-4.62f, -4.62f)
                lineToRelative(-2.5f, 2.5f)
                lineTo(18.0f, 12.0f)
                close()
                moveTo(12.0f, 18.0f)
                lineToRelative(-2.12f, -2.12f)
                lineToRelative(-2.5f, 2.5f)
                lineTo(12.0f, 23.0f)
                lineToRelative(4.62f, -4.62f)
                lineToRelative(-2.5f, -2.5f)
                lineTo(12.0f, 18.0f)
                close()
            }
        }
        return _api!!
    }

private var _api: ImageVector? = null

public val Icons.Outlined.ColorLens: ImageVector
    get() {
        if (_colorLens != null) {
            return _colorLens!!
        }
        _colorLens = materialIcon(name = "Outlined.ColorLens") {
            materialPath {
                moveTo(12.0f, 22.0f)
                curveTo(6.49f, 22.0f, 2.0f, 17.51f, 2.0f, 12.0f)
                reflectiveCurveTo(6.49f, 2.0f, 12.0f, 2.0f)
                reflectiveCurveToRelative(10.0f, 4.04f, 10.0f, 9.0f)
                curveToRelative(0.0f, 3.31f, -2.69f, 6.0f, -6.0f, 6.0f)
                horizontalLineToRelative(-1.77f)
                curveToRelative(-0.28f, 0.0f, -0.5f, 0.22f, -0.5f, 0.5f)
                curveToRelative(0.0f, 0.12f, 0.05f, 0.23f, 0.13f, 0.33f)
                curveToRelative(0.41f, 0.47f, 0.64f, 1.06f, 0.64f, 1.67f)
                curveToRelative(0.0f, 1.38f, -1.12f, 2.5f, -2.5f, 2.5f)
                close()
                moveTo(12.0f, 4.0f)
                curveToRelative(-4.41f, 0.0f, -8.0f, 3.59f, -8.0f, 8.0f)
                reflectiveCurveToRelative(3.59f, 8.0f, 8.0f, 8.0f)
                curveToRelative(0.28f, 0.0f, 0.5f, -0.22f, 0.5f, -0.5f)
                curveToRelative(0.0f, -0.16f, -0.08f, -0.28f, -0.14f, -0.35f)
                curveToRelative(-0.41f, -0.46f, -0.63f, -1.05f, -0.63f, -1.65f)
                curveToRelative(0.0f, -1.38f, 1.12f, -2.5f, 2.5f, -2.5f)
                lineTo(16.0f, 15.0f)
                curveToRelative(2.21f, 0.0f, 4.0f, -1.79f, 4.0f, -4.0f)
                curveToRelative(0.0f, -3.86f, -3.59f, -7.0f, -8.0f, -7.0f)
                close()
            }
            materialPath {
                moveTo(6.5f, 11.5f)
                moveToRelative(-1.5f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
            }
            materialPath {
                moveTo(9.5f, 7.5f)
                moveToRelative(-1.5f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
            }
            materialPath {
                moveTo(14.5f, 7.5f)
                moveToRelative(-1.5f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
            }
            materialPath {
                moveTo(17.5f, 11.5f)
                moveToRelative(-1.5f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, 3.0f, 0.0f)
                arcToRelative(1.5f, 1.5f, 0.0f, true, true, -3.0f, 0.0f)
            }
        }
        return _colorLens!!
    }

private var _colorLens: ImageVector? = null

public val Icons.Outlined.Contrast: ImageVector
    get() {
        if (_contrast != null) {
            return _contrast!!
        }
        _contrast = materialIcon(name = "Outlined.Contrast") {
            materialPath {
                moveTo(12.0f, 22.0f)
                curveToRelative(5.52f, 0.0f, 10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                reflectiveCurveTo(2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveTo(6.48f, 22.0f, 12.0f, 22.0f)
                close()
                moveTo(13.0f, 4.07f)
                curveToRelative(3.94f, 0.49f, 7.0f, 3.85f, 7.0f, 7.93f)
                reflectiveCurveToRelative(-3.05f, 7.44f, -7.0f, 7.93f)
                verticalLineTo(4.07f)
                close()
            }
        }
        return _contrast!!
    }

private var _contrast: ImageVector? = null

public val Icons.Outlined.DarkMode: ImageVector
    get() {
        if (_darkMode != null) {
            return _darkMode!!
        }
        _darkMode = materialIcon(name = "Outlined.DarkMode") {
            materialPath {
                moveTo(9.37f, 5.51f)
                curveTo(9.19f, 6.15f, 9.1f, 6.82f, 9.1f, 7.5f)
                curveToRelative(0.0f, 4.08f, 3.32f, 7.4f, 7.4f, 7.4f)
                curveToRelative(0.68f, 0.0f, 1.35f, -0.09f, 1.99f, -0.27f)
                curveTo(17.45f, 17.19f, 14.93f, 19.0f, 12.0f, 19.0f)
                curveToRelative(-3.86f, 0.0f, -7.0f, -3.14f, -7.0f, -7.0f)
                curveTo(5.0f, 9.07f, 6.81f, 6.55f, 9.37f, 5.51f)
                close()
                moveTo(12.0f, 3.0f)
                curveToRelative(-4.97f, 0.0f, -9.0f, 4.03f, -9.0f, 9.0f)
                reflectiveCurveToRelative(4.03f, 9.0f, 9.0f, 9.0f)
                reflectiveCurveToRelative(9.0f, -4.03f, 9.0f, -9.0f)
                curveToRelative(0.0f, -0.46f, -0.04f, -0.92f, -0.1f, -1.36f)
                curveToRelative(-0.98f, 1.37f, -2.58f, 2.26f, -4.4f, 2.26f)
                curveToRelative(-2.98f, 0.0f, -5.4f, -2.42f, -5.4f, -5.4f)
                curveToRelative(0.0f, -1.81f, 0.89f, -3.42f, 2.26f, -4.4f)
                curveTo(12.92f, 3.04f, 12.46f, 3.0f, 12.0f, 3.0f)
                lineTo(12.0f, 3.0f)
                close()
            }
        }
        return _darkMode!!
    }

private var _darkMode: ImageVector? = null

public val Icons.Outlined.FavoriteBorder: ImageVector
    get() {
        if (_favoriteBorder != null) {
            return _favoriteBorder!!
        }
        _favoriteBorder = materialIcon(name = "Outlined.FavoriteBorder") {
            materialPath {
                moveTo(16.5f, 3.0f)
                curveToRelative(-1.74f, 0.0f, -3.41f, 0.81f, -4.5f, 2.09f)
                curveTo(10.91f, 3.81f, 9.24f, 3.0f, 7.5f, 3.0f)
                curveTo(4.42f, 3.0f, 2.0f, 5.42f, 2.0f, 8.5f)
                curveToRelative(0.0f, 3.78f, 3.4f, 6.86f, 8.55f, 11.54f)
                lineTo(12.0f, 21.35f)
                lineToRelative(1.45f, -1.32f)
                curveTo(18.6f, 15.36f, 22.0f, 12.28f, 22.0f, 8.5f)
                curveTo(22.0f, 5.42f, 19.58f, 3.0f, 16.5f, 3.0f)
                close()
                moveTo(12.1f, 18.55f)
                lineToRelative(-0.1f, 0.1f)
                lineToRelative(-0.1f, -0.1f)
                curveTo(7.14f, 14.24f, 4.0f, 11.39f, 4.0f, 8.5f)
                curveTo(4.0f, 6.5f, 5.5f, 5.0f, 7.5f, 5.0f)
                curveToRelative(1.54f, 0.0f, 3.04f, 0.99f, 3.57f, 2.36f)
                horizontalLineToRelative(1.87f)
                curveTo(13.46f, 5.99f, 14.96f, 5.0f, 16.5f, 5.0f)
                curveToRelative(2.0f, 0.0f, 3.5f, 1.5f, 3.5f, 3.5f)
                curveToRelative(0.0f, 2.89f, -3.14f, 5.74f, -7.9f, 10.05f)
                close()
            }
        }
        return _favoriteBorder!!
    }

private var _favoriteBorder: ImageVector? = null

public val Icons.Outlined.LightMode: ImageVector
    get() {
        if (_lightMode != null) {
            return _lightMode!!
        }
        _lightMode = materialIcon(name = "Outlined.LightMode") {
            materialPath {
                moveTo(12.0f, 9.0f)
                curveToRelative(1.65f, 0.0f, 3.0f, 1.35f, 3.0f, 3.0f)
                reflectiveCurveToRelative(-1.35f, 3.0f, -3.0f, 3.0f)
                reflectiveCurveToRelative(-3.0f, -1.35f, -3.0f, -3.0f)
                reflectiveCurveTo(10.35f, 9.0f, 12.0f, 9.0f)
                moveTo(12.0f, 7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveTo(14.76f, 7.0f, 12.0f, 7.0f)
                lineTo(12.0f, 7.0f)
                close()
                moveTo(2.0f, 13.0f)
                lineToRelative(2.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineToRelative(-2.0f, 0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveTo(1.45f, 13.0f, 2.0f, 13.0f)
                close()
                moveTo(20.0f, 13.0f)
                lineToRelative(2.0f, 0.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                reflectiveCurveToRelative(-0.45f, -1.0f, -1.0f, -1.0f)
                lineToRelative(-2.0f, 0.0f)
                curveToRelative(-0.55f, 0.0f, -1.0f, 0.45f, -1.0f, 1.0f)
                reflectiveCurveTo(19.45f, 13.0f, 20.0f, 13.0f)
                close()
                moveTo(11.0f, 2.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineTo(2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                reflectiveCurveTo(11.0f, 1.45f, 11.0f, 2.0f)
                close()
                moveTo(11.0f, 20.0f)
                verticalLineToRelative(2.0f)
                curveToRelative(0.0f, 0.55f, 0.45f, 1.0f, 1.0f, 1.0f)
                reflectiveCurveToRelative(1.0f, -0.45f, 1.0f, -1.0f)
                verticalLineToRelative(-2.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                curveTo(11.45f, 19.0f, 11.0f, 19.45f, 11.0f, 20.0f)
                close()
                moveTo(5.99f, 4.58f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
                reflectiveCurveToRelative(0.39f, -1.03f, 0.0f, -1.41f)
                lineTo(5.99f, 4.58f)
                close()
                moveTo(18.36f, 16.95f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                lineToRelative(1.06f, 1.06f)
                curveToRelative(0.39f, 0.39f, 1.03f, 0.39f, 1.41f, 0.0f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                lineTo(18.36f, 16.95f)
                close()
                moveTo(19.42f, 5.99f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
                lineTo(19.42f, 5.99f)
                close()
                moveTo(7.05f, 18.36f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.03f, 0.0f, -1.41f)
                curveToRelative(-0.39f, -0.39f, -1.03f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.06f, 1.06f)
                curveToRelative(-0.39f, 0.39f, -0.39f, 1.03f, 0.0f, 1.41f)
                reflectiveCurveToRelative(1.03f, 0.39f, 1.41f, 0.0f)
                lineTo(7.05f, 18.36f)
                close()
            }
        }
        return _lightMode!!
    }

private var _lightMode: ImageVector? = null

public val Icons.Outlined.NewReleases: ImageVector
    get() {
        if (_newReleases != null) {
            return _newReleases!!
        }
        _newReleases = materialIcon(name = "Outlined.NewReleases") {
            materialPath {
                moveTo(23.0f, 12.0f)
                lineToRelative(-2.44f, -2.78f)
                lineToRelative(0.34f, -3.68f)
                lineToRelative(-3.61f, -0.82f)
                lineToRelative(-1.89f, -3.18f)
                lineTo(12.0f, 3.0f)
                lineTo(8.6f, 1.54f)
                lineTo(6.71f, 4.72f)
                lineToRelative(-3.61f, 0.81f)
                lineToRelative(0.34f, 3.68f)
                lineTo(1.0f, 12.0f)
                lineToRelative(2.44f, 2.78f)
                lineToRelative(-0.34f, 3.69f)
                lineToRelative(3.61f, 0.82f)
                lineToRelative(1.89f, 3.18f)
                lineTo(12.0f, 21.0f)
                lineToRelative(3.4f, 1.46f)
                lineToRelative(1.89f, -3.18f)
                lineToRelative(3.61f, -0.82f)
                lineToRelative(-0.34f, -3.68f)
                lineTo(23.0f, 12.0f)
                close()
                moveTo(18.49f, 14.11f)
                lineToRelative(0.26f, 2.79f)
                lineToRelative(-2.74f, 0.62f)
                lineToRelative(-1.43f, 2.41f)
                lineTo(12.0f, 18.82f)
                lineToRelative(-2.58f, 1.11f)
                lineToRelative(-1.43f, -2.41f)
                lineToRelative(-2.74f, -0.62f)
                lineToRelative(0.26f, -2.8f)
                lineTo(3.66f, 12.0f)
                lineToRelative(1.85f, -2.12f)
                lineToRelative(-0.26f, -2.78f)
                lineToRelative(2.74f, -0.61f)
                lineToRelative(1.43f, -2.41f)
                lineTo(12.0f, 5.18f)
                lineToRelative(2.58f, -1.11f)
                lineToRelative(1.43f, 2.41f)
                lineToRelative(2.74f, 0.62f)
                lineToRelative(-0.26f, 2.79f)
                lineTo(20.34f, 12.0f)
                lineToRelative(-1.85f, 2.11f)
                close()
                moveTo(11.0f, 15.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                close()
            }
        }
        return _newReleases!!
    }

private var _newReleases: ImageVector? = null

public val Icons.Outlined.RestartAlt: ImageVector
    get() {
        if (_restartAlt != null) {
            return _restartAlt!!
        }
        _restartAlt = materialIcon(name = "Outlined.RestartAlt") {
            materialPath {
                moveTo(6.0f, 13.0f)
                curveToRelative(0.0f, -1.65f, 0.67f, -3.15f, 1.76f, -4.24f)
                lineTo(6.34f, 7.34f)
                curveTo(4.9f, 8.79f, 4.0f, 10.79f, 4.0f, 13.0f)
                curveToRelative(0.0f, 4.08f, 3.05f, 7.44f, 7.0f, 7.93f)
                verticalLineToRelative(-2.02f)
                curveTo(8.17f, 18.43f, 6.0f, 15.97f, 6.0f, 13.0f)
                close()
                moveTo(20.0f, 13.0f)
                curveToRelative(0.0f, -4.42f, -3.58f, -8.0f, -8.0f, -8.0f)
                curveToRelative(-0.06f, 0.0f, -0.12f, 0.01f, -0.18f, 0.01f)
                lineToRelative(1.09f, -1.09f)
                lineTo(11.5f, 2.5f)
                lineTo(8.0f, 6.0f)
                lineToRelative(3.5f, 3.5f)
                lineToRelative(1.41f, -1.41f)
                lineToRelative(-1.08f, -1.08f)
                curveTo(11.89f, 7.01f, 11.95f, 7.0f, 12.0f, 7.0f)
                curveToRelative(3.31f, 0.0f, 6.0f, 2.69f, 6.0f, 6.0f)
                curveToRelative(0.0f, 2.97f, -2.17f, 5.43f, -5.0f, 5.91f)
                verticalLineToRelative(2.02f)
                curveTo(16.95f, 20.44f, 20.0f, 17.08f, 20.0f, 13.0f)
                close()
            }
        }
        return _restartAlt!!
    }

private var _restartAlt: ImageVector? = null

public val Icons.Outlined.Tune: ImageVector
    get() {
        if (_tune != null) {
            return _tune!!
        }
        _tune = materialIcon(name = "Outlined.Tune") {
            materialPath {
                moveTo(3.0f, 17.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 17.0f)
                close()
                moveTo(3.0f, 5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(10.0f)
                lineTo(13.0f, 5.0f)
                lineTo(3.0f, 5.0f)
                close()
                moveTo(13.0f, 21.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 9.0f)
                verticalLineToRelative(2.0f)
                lineTo(3.0f, 11.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                lineTo(9.0f, 9.0f)
                lineTo(7.0f, 9.0f)
                close()
                moveTo(21.0f, 13.0f)
                verticalLineToRelative(-2.0f)
                lineTo(11.0f, 11.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(10.0f)
                close()
                moveTo(15.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                lineTo(17.0f, 7.0f)
                horizontalLineToRelative(4.0f)
                lineTo(21.0f, 5.0f)
                horizontalLineToRelative(-4.0f)
                lineTo(17.0f, 3.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(6.0f)
                close()
            }
        }
        return _tune!!
    }

private var _tune: ImageVector? = null

public val Icons.Outlined.VisibilityOff: ImageVector
    get() {
        if (_visibilityOff != null) {
            return _visibilityOff!!
        }
        _visibilityOff = materialIcon(name = "Outlined.VisibilityOff") {
            materialPath {
                moveTo(12.0f, 6.0f)
                curveToRelative(3.79f, 0.0f, 7.17f, 2.13f, 8.82f, 5.5f)
                curveToRelative(-0.59f, 1.22f, -1.42f, 2.27f, -2.41f, 3.12f)
                lineToRelative(1.41f, 1.41f)
                curveToRelative(1.39f, -1.23f, 2.49f, -2.77f, 3.18f, -4.53f)
                curveTo(21.27f, 7.11f, 17.0f, 4.0f, 12.0f, 4.0f)
                curveToRelative(-1.27f, 0.0f, -2.49f, 0.2f, -3.64f, 0.57f)
                lineToRelative(1.65f, 1.65f)
                curveTo(10.66f, 6.09f, 11.32f, 6.0f, 12.0f, 6.0f)
                close()
                moveTo(10.93f, 7.14f)
                lineTo(13.0f, 9.21f)
                curveToRelative(0.57f, 0.25f, 1.03f, 0.71f, 1.28f, 1.28f)
                lineToRelative(2.07f, 2.07f)
                curveToRelative(0.08f, -0.34f, 0.14f, -0.7f, 0.14f, -1.07f)
                curveTo(16.5f, 9.01f, 14.48f, 7.0f, 12.0f, 7.0f)
                curveToRelative(-0.37f, 0.0f, -0.72f, 0.05f, -1.07f, 0.14f)
                close()
                moveTo(2.01f, 3.87f)
                lineToRelative(2.68f, 2.68f)
                curveTo(3.06f, 7.83f, 1.77f, 9.53f, 1.0f, 11.5f)
                curveTo(2.73f, 15.89f, 7.0f, 19.0f, 12.0f, 19.0f)
                curveToRelative(1.52f, 0.0f, 2.98f, -0.29f, 4.32f, -0.82f)
                lineToRelative(3.42f, 3.42f)
                lineToRelative(1.41f, -1.41f)
                lineTo(3.42f, 2.45f)
                lineTo(2.01f, 3.87f)
                close()
                moveTo(9.51f, 11.37f)
                lineToRelative(2.61f, 2.61f)
                curveToRelative(-0.04f, 0.01f, -0.08f, 0.02f, -0.12f, 0.02f)
                curveToRelative(-1.38f, 0.0f, -2.5f, -1.12f, -2.5f, -2.5f)
                curveToRelative(0.0f, -0.05f, 0.01f, -0.08f, 0.01f, -0.13f)
                close()
                moveTo(6.11f, 7.97f)
                lineToRelative(1.75f, 1.75f)
                curveToRelative(-0.23f, 0.55f, -0.36f, 1.15f, -0.36f, 1.78f)
                curveToRelative(0.0f, 2.48f, 2.02f, 4.5f, 4.5f, 4.5f)
                curveToRelative(0.63f, 0.0f, 1.23f, -0.13f, 1.77f, -0.36f)
                lineToRelative(0.98f, 0.98f)
                curveToRelative(-0.88f, 0.24f, -1.8f, 0.38f, -2.75f, 0.38f)
                curveToRelative(-3.79f, 0.0f, -7.17f, -2.13f, -8.82f, -5.5f)
                curveToRelative(0.7f, -1.43f, 1.72f, -2.61f, 2.93f, -3.53f)
                close()
            }
        }
        return _visibilityOff!!
    }

private var _visibilityOff: ImageVector? = null
