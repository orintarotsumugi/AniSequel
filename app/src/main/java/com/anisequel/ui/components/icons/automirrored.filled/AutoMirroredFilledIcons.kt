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

package com.anisequel.ui.components.icons.automirrored.filled

import androidx.compose.ui.graphics.vector.ImageVector
import com.anisequel.ui.components.icons.Icons
import com.anisequel.ui.components.icons.materialIcon
import com.anisequel.ui.components.icons.materialPath

public val Icons.AutoMirrored.Filled.ArrowBack: ImageVector
    get() {
        if (_arrowBack != null) {
            return _arrowBack!!
        }
        _arrowBack = materialIcon(name = "AutoMirrored.Filled.ArrowBack", autoMirror = true) {
            materialPath {
                moveTo(20.0f, 11.0f)
                horizontalLineTo(7.83f)
                lineToRelative(5.59f, -5.59f)
                lineTo(12.0f, 4.0f)
                lineToRelative(-8.0f, 8.0f)
                lineToRelative(8.0f, 8.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(7.83f, 13.0f)
                horizontalLineTo(20.0f)
                verticalLineToRelative(-2.0f)
                close()
            }
        }
        return _arrowBack!!
    }

private var _arrowBack: ImageVector? = null

public val Icons.AutoMirrored.Filled.ArrowForward: ImageVector
    get() {
        if (_arrowForward != null) {
            return _arrowForward!!
        }
        _arrowForward = materialIcon(name = "AutoMirrored.Filled.ArrowForward", autoMirror = true) {
            materialPath {
                moveTo(12.0f, 4.0f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(16.17f, 11.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(12.17f)
                lineToRelative(-5.58f, 5.59f)
                lineTo(12.0f, 20.0f)
                lineToRelative(8.0f, -8.0f)
                close()
            }
        }
        return _arrowForward!!
    }

private var _arrowForward: ImageVector? = null

public val Icons.AutoMirrored.Filled.Comment: ImageVector
    get() {
        if (_comment != null) {
            return _comment!!
        }
        _comment = materialIcon(name = "AutoMirrored.Filled.Comment", autoMirror = true) {
            materialPath {
                moveTo(21.99f, 4.0f)
                curveToRelative(0.0f, -1.1f, -0.89f, -2.0f, -1.99f, -2.0f)
                lineTo(4.0f, 2.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                lineToRelative(4.0f, 4.0f)
                lineToRelative(-0.01f, -18.0f)
                close()
                moveTo(18.0f, 14.0f)
                lineTo(6.0f, 14.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(18.0f, 11.0f)
                lineTo(6.0f, 11.0f)
                lineTo(6.0f, 9.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(18.0f, 8.0f)
                lineTo(6.0f, 8.0f)
                lineTo(6.0f, 6.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _comment!!
    }

private var _comment: ImageVector? = null

public val Icons.AutoMirrored.Filled.KeyboardArrowRight: ImageVector
    get() {
        if (_keyboardArrowRight != null) {
            return _keyboardArrowRight!!
        }
        _keyboardArrowRight = materialIcon(name = "AutoMirrored.Filled.KeyboardArrowRight",
                autoMirror = true) {
            materialPath {
                moveTo(8.59f, 16.59f)
                lineTo(13.17f, 12.0f)
                lineTo(8.59f, 7.41f)
                lineTo(10.0f, 6.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(-1.41f, -1.41f)
                close()
            }
        }
        return _keyboardArrowRight!!
    }

private var _keyboardArrowRight: ImageVector? = null

public val Icons.AutoMirrored.Filled.List: ImageVector
    get() {
        if (_list != null) {
            return _list!!
        }
        _list = materialIcon(name = "AutoMirrored.Filled.List", autoMirror = true) {
            materialPath {
                moveTo(3.0f, 13.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 17.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-2.0f)
                lineTo(3.0f, 15.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 9.0f)
                horizontalLineToRelative(2.0f)
                lineTo(5.0f, 7.0f)
                lineTo(3.0f, 7.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 13.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(7.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 17.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                lineTo(7.0f, 15.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(7.0f, 7.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(14.0f)
                lineTo(21.0f, 7.0f)
                lineTo(7.0f, 7.0f)
                close()
            }
        }
        return _list!!
    }

private var _list: ImageVector? = null

public val Icons.AutoMirrored.Filled.Login: ImageVector
    get() {
        if (_login != null) {
            return _login!!
        }
        _login = materialIcon(name = "AutoMirrored.Filled.Login", autoMirror = true) {
            materialPath {
                moveTo(11.0f, 7.0f)
                lineTo(9.6f, 8.4f)
                lineToRelative(2.6f, 2.6f)
                horizontalLineTo(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(10.2f)
                lineToRelative(-2.6f, 2.6f)
                lineTo(11.0f, 17.0f)
                lineToRelative(5.0f, -5.0f)
                lineTo(11.0f, 7.0f)
                close()
                moveTo(20.0f, 19.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(19.0f)
                close()
            }
        }
        return _login!!
    }

private var _login: ImageVector? = null

public val Icons.AutoMirrored.Filled.Logout: ImageVector
    get() {
        if (_logout != null) {
            return _logout!!
        }
        _logout = materialIcon(name = "AutoMirrored.Filled.Logout", autoMirror = true) {
            materialPath {
                moveTo(17.0f, 7.0f)
                lineToRelative(-1.41f, 1.41f)
                lineTo(18.17f, 11.0f)
                horizontalLineTo(8.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(10.17f)
                lineToRelative(-2.58f, 2.58f)
                lineTo(17.0f, 17.0f)
                lineToRelative(5.0f, -5.0f)
                close()
                moveTo(4.0f, 5.0f)
                horizontalLineToRelative(8.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(4.0f)
                curveToRelative(-1.1f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(5.0f)
                close()
            }
        }
        return _logout!!
    }

private var _logout: ImageVector? = null

public val Icons.AutoMirrored.Filled.MenuBook: ImageVector
    get() {
        if (_menuBook != null) {
            return _menuBook!!
        }
        _menuBook = materialIcon(name = "AutoMirrored.Filled.MenuBook", autoMirror = true) {
            materialPath {
                moveTo(21.0f, 5.0f)
                curveToRelative(-1.11f, -0.35f, -2.33f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.95f, 0.0f, -4.05f, 0.4f, -5.5f, 1.5f)
                curveToRelative(-1.45f, -1.1f, -3.55f, -1.5f, -5.5f, -1.5f)
                reflectiveCurveTo(2.45f, 4.9f, 1.0f, 6.0f)
                verticalLineToRelative(14.65f)
                curveToRelative(0.0f, 0.25f, 0.25f, 0.5f, 0.5f, 0.5f)
                curveToRelative(0.1f, 0.0f, 0.15f, -0.05f, 0.25f, -0.05f)
                curveTo(3.1f, 20.45f, 5.05f, 20.0f, 6.5f, 20.0f)
                curveToRelative(1.95f, 0.0f, 4.05f, 0.4f, 5.5f, 1.5f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.65f, 0.0f, 3.35f, 0.3f, 4.75f, 1.05f)
                curveToRelative(0.1f, 0.05f, 0.15f, 0.05f, 0.25f, 0.05f)
                curveToRelative(0.25f, 0.0f, 0.5f, -0.25f, 0.5f, -0.5f)
                verticalLineTo(6.0f)
                curveTo(22.4f, 5.55f, 21.75f, 5.25f, 21.0f, 5.0f)
                close()
                moveTo(21.0f, 18.5f)
                curveToRelative(-1.1f, -0.35f, -2.3f, -0.5f, -3.5f, -0.5f)
                curveToRelative(-1.7f, 0.0f, -4.15f, 0.65f, -5.5f, 1.5f)
                verticalLineTo(8.0f)
                curveToRelative(1.35f, -0.85f, 3.8f, -1.5f, 5.5f, -1.5f)
                curveToRelative(1.2f, 0.0f, 2.4f, 0.15f, 3.5f, 0.5f)
                verticalLineTo(18.5f)
                close()
            }
            materialPath {
                moveTo(17.5f, 10.5f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(9.24f)
                curveTo(19.21f, 9.09f, 18.36f, 9.0f, 17.5f, 9.0f)
                curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveTo(14.13f, 10.85f, 15.7f, 10.5f, 17.5f, 10.5f)
                close()
            }
            materialPath {
                moveTo(13.0f, 12.49f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineTo(11.9f)
                curveToRelative(-0.79f, -0.15f, -1.64f, -0.24f, -2.5f, -0.24f)
                curveTo(15.8f, 11.66f, 14.26f, 11.96f, 13.0f, 12.49f)
                close()
            }
            materialPath {
                moveTo(17.5f, 14.33f)
                curveToRelative(-1.7f, 0.0f, -3.24f, 0.29f, -4.5f, 0.83f)
                verticalLineToRelative(1.66f)
                curveToRelative(1.13f, -0.64f, 2.7f, -0.99f, 4.5f, -0.99f)
                curveToRelative(0.88f, 0.0f, 1.73f, 0.09f, 2.5f, 0.26f)
                verticalLineToRelative(-1.52f)
                curveTo(19.21f, 14.41f, 18.36f, 14.33f, 17.5f, 14.33f)
                close()
            }
        }
        return _menuBook!!
    }

private var _menuBook: ImageVector? = null

public val Icons.AutoMirrored.Filled.OpenInNew: ImageVector
    get() {
        if (_openInNew != null) {
            return _openInNew!!
        }
        _openInNew = materialIcon(name = "AutoMirrored.Filled.OpenInNew", autoMirror = true) {
            materialPath {
                moveTo(19.0f, 19.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(7.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(5.0f)
                curveToRelative(-1.11f, 0.0f, -2.0f, 0.9f, -2.0f, 2.0f)
                verticalLineToRelative(14.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineToRelative(-7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(7.0f)
                close()
                moveTo(14.0f, 3.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(3.59f)
                lineToRelative(-9.83f, 9.83f)
                lineToRelative(1.41f, 1.41f)
                lineTo(19.0f, 6.41f)
                verticalLineTo(10.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(-7.0f)
                close()
            }
        }
        return _openInNew!!
    }

private var _openInNew: ImageVector? = null

public val Icons.AutoMirrored.Filled.Redo: ImageVector
    get() {
        if (_redo != null) {
            return _redo!!
        }
        _redo = materialIcon(name = "AutoMirrored.Filled.Redo", autoMirror = true) {
            materialPath {
                moveTo(18.4f, 10.6f)
                curveTo(16.55f, 8.99f, 14.15f, 8.0f, 11.5f, 8.0f)
                curveToRelative(-4.65f, 0.0f, -8.58f, 3.03f, -9.96f, 7.22f)
                lineTo(3.9f, 16.0f)
                curveToRelative(1.05f, -3.19f, 4.05f, -5.5f, 7.6f, -5.5f)
                curveToRelative(1.95f, 0.0f, 3.73f, 0.72f, 5.12f, 1.88f)
                lineTo(13.0f, 16.0f)
                horizontalLineToRelative(9.0f)
                verticalLineTo(7.0f)
                lineToRelative(-3.6f, 3.6f)
                close()
            }
        }
        return _redo!!
    }

private var _redo: ImageVector? = null

public val Icons.AutoMirrored.Filled.Send: ImageVector
    get() {
        if (_send != null) {
            return _send!!
        }
        _send = materialIcon(name = "AutoMirrored.Filled.Send", autoMirror = true) {
            materialPath {
                moveTo(2.01f, 21.0f)
                lineTo(23.0f, 12.0f)
                lineTo(2.01f, 3.0f)
                lineTo(2.0f, 10.0f)
                lineToRelative(15.0f, 2.0f)
                lineToRelative(-15.0f, 2.0f)
                close()
            }
        }
        return _send!!
    }

private var _send: ImageVector? = null

public val Icons.AutoMirrored.Filled.TrendingUp: ImageVector
    get() {
        if (_trendingUp != null) {
            return _trendingUp!!
        }
        _trendingUp = materialIcon(name = "AutoMirrored.Filled.TrendingUp", autoMirror = true) {
            materialPath {
                moveTo(16.0f, 6.0f)
                lineToRelative(2.29f, 2.29f)
                lineToRelative(-4.88f, 4.88f)
                lineToRelative(-4.0f, -4.0f)
                lineTo(2.0f, 16.59f)
                lineTo(3.41f, 18.0f)
                lineToRelative(6.0f, -6.0f)
                lineToRelative(4.0f, 4.0f)
                lineToRelative(6.3f, -6.29f)
                lineTo(22.0f, 12.0f)
                verticalLineTo(6.0f)
                close()
            }
        }
        return _trendingUp!!
    }

private var _trendingUp: ImageVector? = null

public val Icons.AutoMirrored.Filled.Undo: ImageVector
    get() {
        if (_undo != null) {
            return _undo!!
        }
        _undo = materialIcon(name = "AutoMirrored.Filled.Undo", autoMirror = true) {
            materialPath {
                moveTo(12.5f, 8.0f)
                curveToRelative(-2.65f, 0.0f, -5.05f, 0.99f, -6.9f, 2.6f)
                lineTo(2.0f, 7.0f)
                verticalLineToRelative(9.0f)
                horizontalLineToRelative(9.0f)
                lineToRelative(-3.62f, -3.62f)
                curveToRelative(1.39f, -1.16f, 3.16f, -1.88f, 5.12f, -1.88f)
                curveToRelative(3.54f, 0.0f, 6.55f, 2.31f, 7.6f, 5.5f)
                lineToRelative(2.37f, -0.78f)
                curveTo(21.08f, 11.03f, 17.15f, 8.0f, 12.5f, 8.0f)
                close()
            }
        }
        return _undo!!
    }

private var _undo: ImageVector? = null
