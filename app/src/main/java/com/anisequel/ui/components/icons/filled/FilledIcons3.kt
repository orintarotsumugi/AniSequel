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

package com.anisequel.ui.components.icons.filled

import androidx.compose.ui.graphics.vector.ImageVector
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.materialIcon
import com.anisequel.ui.components.icons.materialPath

public val Icons.Filled.PlayArrow: ImageVector
    get() {
        if (_playArrow != null) {
            return _playArrow!!
        }
        _playArrow = materialIcon(name = "Filled.PlayArrow") {
            materialPath {
                moveTo(8.0f, 5.0f)
                verticalLineToRelative(14.0f)
                lineToRelative(11.0f, -7.0f)
                close()
            }
        }
        return _playArrow!!
    }

private var _playArrow: ImageVector? = null

public val Icons.Filled.Public: ImageVector
    get() {
        if (_public != null) {
            return _public!!
        }
        _public = materialIcon(name = "Filled.Public") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(11.0f, 19.93f)
                curveToRelative(-3.95f, -0.49f, -7.0f, -3.85f, -7.0f, -7.93f)
                curveToRelative(0.0f, -0.62f, 0.08f, -1.21f, 0.21f, -1.79f)
                lineTo(9.0f, 15.0f)
                verticalLineToRelative(1.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                verticalLineToRelative(1.93f)
                close()
                moveTo(17.9f, 17.39f)
                curveToRelative(-0.26f, -0.81f, -1.0f, -1.39f, -1.9f, -1.39f)
                horizontalLineToRelative(-1.0f)
                verticalLineToRelative(-3.0f)
                curveToRelative(0.0f, -0.55f, -0.45f, -1.0f, -1.0f, -1.0f)
                lineTo(8.0f, 12.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(0.55f, 0.0f, 1.0f, -0.45f, 1.0f, -1.0f)
                lineTo(11.0f, 7.0f)
                horizontalLineToRelative(2.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-0.41f)
                curveToRelative(2.93f, 1.19f, 5.0f, 4.06f, 5.0f, 7.41f)
                curveToRelative(0.0f, 2.08f, -0.8f, 3.97f, -2.1f, 5.39f)
                close()
            }
        }
        return _public!!
    }

private var _public: ImageVector? = null

public val Icons.Filled.RadioButtonChecked: ImageVector
    get() {
        if (_radioButtonChecked != null) {
            return _radioButtonChecked!!
        }
        _radioButtonChecked = materialIcon(name = "Filled.RadioButtonChecked") {
            materialPath {
                moveTo(12.0f, 7.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, 2.24f, -5.0f, 5.0f)
                reflectiveCurveToRelative(2.24f, 5.0f, 5.0f, 5.0f)
                reflectiveCurveToRelative(5.0f, -2.24f, 5.0f, -5.0f)
                reflectiveCurveToRelative(-2.24f, -5.0f, -5.0f, -5.0f)
                close()
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
        }
        return _radioButtonChecked!!
    }

private var _radioButtonChecked: ImageVector? = null

public val Icons.Filled.Refresh: ImageVector
    get() {
        if (_refresh != null) {
            return _refresh!!
        }
        _refresh = materialIcon(name = "Filled.Refresh") {
            materialPath {
                moveTo(17.65f, 6.35f)
                curveTo(16.2f, 4.9f, 14.21f, 4.0f, 12.0f, 4.0f)
                curveToRelative(-4.42f, 0.0f, -7.99f, 3.58f, -7.99f, 8.0f)
                reflectiveCurveToRelative(3.57f, 8.0f, 7.99f, 8.0f)
                curveToRelative(3.73f, 0.0f, 6.84f, -2.55f, 7.73f, -6.0f)
                horizontalLineToRelative(-2.08f)
                curveToRelative(-0.82f, 2.33f, -3.04f, 4.0f, -5.65f, 4.0f)
                curveToRelative(-3.31f, 0.0f, -6.0f, -2.69f, -6.0f, -6.0f)
                reflectiveCurveToRelative(2.69f, -6.0f, 6.0f, -6.0f)
                curveToRelative(1.66f, 0.0f, 3.14f, 0.69f, 4.22f, 1.78f)
                lineTo(13.0f, 11.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(4.0f)
                lineToRelative(-2.35f, 2.35f)
                close()
            }
        }
        return _refresh!!
    }

private var _refresh: ImageVector? = null

public val Icons.Filled.Remove: ImageVector
    get() {
        if (_remove != null) {
            return _remove!!
        }
        _remove = materialIcon(name = "Filled.Remove") {
            materialPath {
                moveTo(19.0f, 13.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _remove!!
    }

private var _remove: ImageVector? = null

public val Icons.Filled.Schedule: ImageVector
    get() {
        if (_schedule != null) {
            return _schedule!!
        }
        _schedule = materialIcon(name = "Filled.Schedule") {
            materialPath {
                moveTo(11.99f, 2.0f)
                curveTo(6.47f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.47f, 10.0f, 9.99f, 10.0f)
                curveTo(17.52f, 22.0f, 22.0f, 17.52f, 22.0f, 12.0f)
                reflectiveCurveTo(17.52f, 2.0f, 11.99f, 2.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-4.42f, 0.0f, -8.0f, -3.58f, -8.0f, -8.0f)
                reflectiveCurveToRelative(3.58f, -8.0f, 8.0f, -8.0f)
                reflectiveCurveToRelative(8.0f, 3.58f, 8.0f, 8.0f)
                reflectiveCurveToRelative(-3.58f, 8.0f, -8.0f, 8.0f)
                close()
            }
            materialPath {
                moveTo(12.5f, 7.0f)
                horizontalLineTo(11.0f)
                verticalLineToRelative(6.0f)
                lineToRelative(5.25f, 3.15f)
                lineToRelative(0.75f, -1.23f)
                lineToRelative(-4.5f, -2.67f)
                close()
            }
        }
        return _schedule!!
    }

private var _schedule: ImageVector? = null

public val Icons.Filled.Search: ImageVector
    get() {
        if (_search != null) {
            return _search!!
        }
        _search = materialIcon(name = "Filled.Search") {
            materialPath {
                moveTo(15.5f, 14.0f)
                horizontalLineToRelative(-0.79f)
                lineToRelative(-0.28f, -0.27f)
                curveTo(15.41f, 12.59f, 16.0f, 11.11f, 16.0f, 9.5f)
                curveTo(16.0f, 5.91f, 13.09f, 3.0f, 9.5f, 3.0f)
                reflectiveCurveTo(3.0f, 5.91f, 3.0f, 9.5f)
                reflectiveCurveTo(5.91f, 16.0f, 9.5f, 16.0f)
                curveToRelative(1.61f, 0.0f, 3.09f, -0.59f, 4.23f, -1.57f)
                lineToRelative(0.27f, 0.28f)
                verticalLineToRelative(0.79f)
                lineToRelative(5.0f, 4.99f)
                lineTo(20.49f, 19.0f)
                lineToRelative(-4.99f, -5.0f)
                close()
                moveTo(9.5f, 14.0f)
                curveTo(7.01f, 14.0f, 5.0f, 11.99f, 5.0f, 9.5f)
                reflectiveCurveTo(7.01f, 5.0f, 9.5f, 5.0f)
                reflectiveCurveTo(14.0f, 7.01f, 14.0f, 9.5f)
                reflectiveCurveTo(11.99f, 14.0f, 9.5f, 14.0f)
                close()
            }
        }
        return _search!!
    }

private var _search: ImageVector? = null

public val Icons.Filled.Settings: ImageVector
    get() {
        if (_settings != null) {
            return _settings!!
        }
        _settings = materialIcon(name = "Filled.Settings") {
            materialPath {
                moveTo(19.14f, 12.94f)
                curveToRelative(0.04f, -0.3f, 0.06f, -0.61f, 0.06f, -0.94f)
                curveToRelative(0.0f, -0.32f, -0.02f, -0.64f, -0.07f, -0.94f)
                lineToRelative(2.03f, -1.58f)
                curveToRelative(0.18f, -0.14f, 0.23f, -0.41f, 0.12f, -0.61f)
                lineToRelative(-1.92f, -3.32f)
                curveToRelative(-0.12f, -0.22f, -0.37f, -0.29f, -0.59f, -0.22f)
                lineToRelative(-2.39f, 0.96f)
                curveToRelative(-0.5f, -0.38f, -1.03f, -0.7f, -1.62f, -0.94f)
                lineTo(14.4f, 2.81f)
                curveToRelative(-0.04f, -0.24f, -0.24f, -0.41f, -0.48f, -0.41f)
                horizontalLineToRelative(-3.84f)
                curveToRelative(-0.24f, 0.0f, -0.43f, 0.17f, -0.47f, 0.41f)
                lineTo(9.25f, 5.35f)
                curveTo(8.66f, 5.59f, 8.12f, 5.92f, 7.63f, 6.29f)
                lineTo(5.24f, 5.33f)
                curveToRelative(-0.22f, -0.08f, -0.47f, 0.0f, -0.59f, 0.22f)
                lineTo(2.74f, 8.87f)
                curveTo(2.62f, 9.08f, 2.66f, 9.34f, 2.86f, 9.48f)
                lineToRelative(2.03f, 1.58f)
                curveTo(4.84f, 11.36f, 4.8f, 11.69f, 4.8f, 12.0f)
                reflectiveCurveToRelative(0.02f, 0.64f, 0.07f, 0.94f)
                lineToRelative(-2.03f, 1.58f)
                curveToRelative(-0.18f, 0.14f, -0.23f, 0.41f, -0.12f, 0.61f)
                lineToRelative(1.92f, 3.32f)
                curveToRelative(0.12f, 0.22f, 0.37f, 0.29f, 0.59f, 0.22f)
                lineToRelative(2.39f, -0.96f)
                curveToRelative(0.5f, 0.38f, 1.03f, 0.7f, 1.62f, 0.94f)
                lineToRelative(0.36f, 2.54f)
                curveToRelative(0.05f, 0.24f, 0.24f, 0.41f, 0.48f, 0.41f)
                horizontalLineToRelative(3.84f)
                curveToRelative(0.24f, 0.0f, 0.44f, -0.17f, 0.47f, -0.41f)
                lineToRelative(0.36f, -2.54f)
                curveToRelative(0.59f, -0.24f, 1.13f, -0.56f, 1.62f, -0.94f)
                lineToRelative(2.39f, 0.96f)
                curveToRelative(0.22f, 0.08f, 0.47f, 0.0f, 0.59f, -0.22f)
                lineToRelative(1.92f, -3.32f)
                curveToRelative(0.12f, -0.22f, 0.07f, -0.47f, -0.12f, -0.61f)
                lineTo(19.14f, 12.94f)
                close()
                moveTo(12.0f, 15.6f)
                curveToRelative(-1.98f, 0.0f, -3.6f, -1.62f, -3.6f, -3.6f)
                reflectiveCurveToRelative(1.62f, -3.6f, 3.6f, -3.6f)
                reflectiveCurveToRelative(3.6f, 1.62f, 3.6f, 3.6f)
                reflectiveCurveTo(13.98f, 15.6f, 12.0f, 15.6f)
                close()
            }
        }
        return _settings!!
    }

private var _settings: ImageVector? = null

public val Icons.Filled.Star: ImageVector
    get() {
        if (_star != null) {
            return _star!!
        }
        _star = materialIcon(name = "Filled.Star") {
            materialPath {
                moveTo(12.0f, 17.27f)
                lineTo(18.18f, 21.0f)
                lineToRelative(-1.64f, -7.03f)
                lineTo(22.0f, 9.24f)
                lineToRelative(-7.19f, -0.61f)
                lineTo(12.0f, 2.0f)
                lineTo(9.19f, 8.63f)
                lineTo(2.0f, 9.24f)
                lineToRelative(5.46f, 4.73f)
                lineTo(5.82f, 21.0f)
                close()
            }
        }
        return _star!!
    }

private var _star: ImageVector? = null

public val Icons.Filled.SystemUpdate: ImageVector
    get() {
        if (_systemUpdate != null) {
            return _systemUpdate!!
        }
        _systemUpdate = materialIcon(name = "Filled.SystemUpdate") {
            materialPath {
                moveTo(17.0f, 1.01f)
                lineTo(7.0f, 1.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(18.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(10.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(19.0f, 3.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -1.99f, -2.0f, -1.99f)
                close()
                moveTo(17.0f, 19.0f)
                lineTo(7.0f, 19.0f)
                lineTo(7.0f, 5.0f)
                horizontalLineToRelative(10.0f)
                verticalLineToRelative(14.0f)
                close()
                moveTo(16.0f, 13.0f)
                horizontalLineToRelative(-3.0f)
                lineTo(13.0f, 8.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(5.0f)
                lineTo(8.0f, 13.0f)
                lineToRelative(4.0f, 4.0f)
                lineToRelative(4.0f, -4.0f)
                close()
            }
        }
        return _systemUpdate!!
    }

private var _systemUpdate: ImageVector? = null

public val Icons.Filled.Tv: ImageVector
    get() {
        if (_tv != null) {
            return _tv!!
        }
        _tv = materialIcon(name = "Filled.Tv") {
            materialPath {
                moveTo(21.0f, 3.0f)
                lineTo(3.0f, 3.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(5.0f)
                curveToRelative(1.1f, 0.0f, 1.99f, -0.9f, 1.99f, -2.0f)
                lineTo(23.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
                moveTo(21.0f, 17.0f)
                lineTo(3.0f, 17.0f)
                lineTo(3.0f, 5.0f)
                horizontalLineToRelative(18.0f)
                verticalLineToRelative(12.0f)
                close()
            }
        }
        return _tv!!
    }

private var _tv: ImageVector? = null

public val Icons.Filled.Visibility: ImageVector
    get() {
        if (_visibility != null) {
            return _visibility!!
        }
        _visibility = materialIcon(name = "Filled.Visibility") {
            materialPath {
                moveTo(12.0f, 4.5f)
                curveTo(7.0f, 4.5f, 2.73f, 7.61f, 1.0f, 12.0f)
                curveToRelative(1.73f, 4.39f, 6.0f, 7.5f, 11.0f, 7.5f)
                reflectiveCurveToRelative(9.27f, -3.11f, 11.0f, -7.5f)
                curveToRelative(-1.73f, -4.39f, -6.0f, -7.5f, -11.0f, -7.5f)
                close()
                moveTo(12.0f, 17.0f)
                curveToRelative(-2.76f, 0.0f, -5.0f, -2.24f, -5.0f, -5.0f)
                reflectiveCurveToRelative(2.24f, -5.0f, 5.0f, -5.0f)
                reflectiveCurveToRelative(5.0f, 2.24f, 5.0f, 5.0f)
                reflectiveCurveToRelative(-2.24f, 5.0f, -5.0f, 5.0f)
                close()
                moveTo(12.0f, 9.0f)
                curveToRelative(-1.66f, 0.0f, -3.0f, 1.34f, -3.0f, 3.0f)
                reflectiveCurveToRelative(1.34f, 3.0f, 3.0f, 3.0f)
                reflectiveCurveToRelative(3.0f, -1.34f, 3.0f, -3.0f)
                reflectiveCurveToRelative(-1.34f, -3.0f, -3.0f, -3.0f)
                close()
            }
        }
        return _visibility!!
    }

private var _visibility: ImageVector? = null

public val Icons.Filled.Warning: ImageVector
    get() {
        if (_warning != null) {
            return _warning!!
        }
        _warning = materialIcon(name = "Filled.Warning") {
            materialPath {
                moveTo(1.0f, 21.0f)
                horizontalLineToRelative(22.0f)
                lineTo(12.0f, 2.0f)
                lineTo(1.0f, 21.0f)
                close()
                moveTo(13.0f, 18.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(13.0f, 14.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(4.0f)
                close()
            }
        }
        return _warning!!
    }

private var _warning: ImageVector? = null

public val Icons.Filled.Wifi: ImageVector
    get() {
        if (_wifi != null) {
            return _wifi!!
        }
        _wifi = materialIcon(name = "Filled.Wifi") {
            materialPath {
                moveTo(1.0f, 9.0f)
                lineToRelative(2.0f, 2.0f)
                curveToRelative(4.97f, -4.97f, 13.03f, -4.97f, 18.0f, 0.0f)
                lineToRelative(2.0f, -2.0f)
                curveTo(16.93f, 2.93f, 7.08f, 2.93f, 1.0f, 9.0f)
                close()
                moveTo(9.0f, 17.0f)
                lineToRelative(3.0f, 3.0f)
                lineToRelative(3.0f, -3.0f)
                curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6.0f, 0.0f)
                close()
                moveTo(5.0f, 13.0f)
                lineToRelative(2.0f, 2.0f)
                curveToRelative(2.76f, -2.76f, 7.24f, -2.76f, 10.0f, 0.0f)
                lineToRelative(2.0f, -2.0f)
                curveTo(15.14f, 9.14f, 8.87f, 9.14f, 5.0f, 13.0f)
                close()
            }
        }
        return _wifi!!
    }

private var _wifi: ImageVector? = null

public val Icons.Filled.WifiOff: ImageVector
    get() {
        if (_wifiOff != null) {
            return _wifiOff!!
        }
        _wifiOff = materialIcon(name = "Filled.WifiOff") {
            materialPath {
                moveTo(22.99f, 9.0f)
                curveTo(19.15f, 5.16f, 13.8f, 3.76f, 8.84f, 4.78f)
                lineToRelative(2.52f, 2.52f)
                curveToRelative(3.47f, -0.17f, 6.99f, 1.05f, 9.63f, 3.7f)
                lineToRelative(2.0f, -2.0f)
                close()
                moveTo(18.99f, 13.0f)
                curveToRelative(-1.29f, -1.29f, -2.84f, -2.13f, -4.49f, -2.56f)
                lineToRelative(3.53f, 3.53f)
                lineToRelative(0.96f, -0.97f)
                close()
                moveTo(2.0f, 3.05f)
                lineTo(5.07f, 6.1f)
                curveTo(3.6f, 6.82f, 2.22f, 7.78f, 1.0f, 9.0f)
                lineToRelative(1.99f, 2.0f)
                curveToRelative(1.24f, -1.24f, 2.67f, -2.16f, 4.2f, -2.77f)
                lineToRelative(2.24f, 2.24f)
                curveTo(7.81f, 10.89f, 6.27f, 11.73f, 5.0f, 13.0f)
                verticalLineToRelative(0.01f)
                lineTo(6.99f, 15.0f)
                curveToRelative(1.36f, -1.36f, 3.14f, -2.04f, 4.92f, -2.06f)
                lineTo(18.98f, 20.0f)
                lineToRelative(1.27f, -1.26f)
                lineTo(3.29f, 1.79f)
                lineTo(2.0f, 3.05f)
                close()
                moveTo(9.0f, 17.0f)
                lineToRelative(3.0f, 3.0f)
                lineToRelative(3.0f, -3.0f)
                curveToRelative(-1.65f, -1.66f, -4.34f, -1.66f, -6.0f, 0.0f)
                close()
            }
        }
        return _wifiOff!!
    }

private var _wifiOff: ImageVector? = null
