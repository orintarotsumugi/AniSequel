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

public val Icons.Filled.AccountCircle: ImageVector
    get() {
        if (_accountCircle != null) {
            return _accountCircle!!
        }
        _accountCircle = materialIcon(name = "Filled.AccountCircle") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(12.0f, 6.0f)
                curveToRelative(1.93f, 0.0f, 3.5f, 1.57f, 3.5f, 3.5f)
                reflectiveCurveTo(13.93f, 13.0f, 12.0f, 13.0f)
                reflectiveCurveToRelative(-3.5f, -1.57f, -3.5f, -3.5f)
                reflectiveCurveTo(10.07f, 6.0f, 12.0f, 6.0f)
                close()
                moveTo(12.0f, 20.0f)
                curveToRelative(-2.03f, 0.0f, -4.43f, -0.82f, -6.14f, -2.88f)
                curveTo(7.55f, 15.8f, 9.68f, 15.0f, 12.0f, 15.0f)
                reflectiveCurveToRelative(4.45f, 0.8f, 6.14f, 2.12f)
                curveTo(16.43f, 19.18f, 14.03f, 20.0f, 12.0f, 20.0f)
                close()
            }
        }
        return _accountCircle!!
    }

private var _accountCircle: ImageVector? = null

public val Icons.Filled.Add: ImageVector
    get() {
        if (_add != null) {
            return _add!!
        }
        _add = materialIcon(name = "Filled.Add") {
            materialPath {
                moveTo(19.0f, 13.0f)
                horizontalLineToRelative(-6.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineTo(5.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(6.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(6.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _add!!
    }

private var _add: ImageVector? = null

public val Icons.Filled.Badge: ImageVector
    get() {
        if (_badge != null) {
            return _badge!!
        }
        _badge = materialIcon(name = "Filled.Badge") {
            materialPath {
                moveTo(20.0f, 7.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineTo(4.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-2.0f)
                curveTo(9.9f, 2.0f, 9.0f, 2.9f, 9.0f, 4.0f)
                verticalLineToRelative(3.0f)
                horizontalLineTo(4.0f)
                curveTo(2.9f, 7.0f, 2.0f, 7.9f, 2.0f, 9.0f)
                verticalLineToRelative(11.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(9.0f)
                curveTo(22.0f, 7.9f, 21.1f, 7.0f, 20.0f, 7.0f)
                close()
                moveTo(9.0f, 12.0f)
                curveToRelative(0.83f, 0.0f, 1.5f, 0.67f, 1.5f, 1.5f)
                reflectiveCurveTo(9.83f, 15.0f, 9.0f, 15.0f)
                reflectiveCurveToRelative(-1.5f, -0.67f, -1.5f, -1.5f)
                reflectiveCurveTo(8.17f, 12.0f, 9.0f, 12.0f)
                close()
                moveTo(12.0f, 18.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(-0.75f)
                curveToRelative(0.0f, -1.0f, 2.0f, -1.5f, 3.0f, -1.5f)
                reflectiveCurveToRelative(3.0f, 0.5f, 3.0f, 1.5f)
                verticalLineTo(18.0f)
                close()
                moveTo(13.0f, 9.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(4.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(9.0f)
                close()
                moveTo(18.0f, 16.5f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(15.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(16.5f)
                close()
                moveTo(18.0f, 13.5f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(12.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(13.5f)
                close()
            }
        }
        return _badge!!
    }

private var _badge: ImageVector? = null

public val Icons.Filled.BarChart: ImageVector
    get() {
        if (_barChart != null) {
            return _barChart!!
        }
        _barChart = materialIcon(name = "Filled.BarChart") {
            materialPath {
                moveTo(4.0f, 9.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(11.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
            materialPath {
                moveTo(16.0f, 13.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(7.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
            materialPath {
                moveTo(10.0f, 4.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(16.0f)
                horizontalLineToRelative(-4.0f)
                close()
            }
        }
        return _barChart!!
    }

private var _barChart: ImageVector? = null

public val Icons.Filled.Bookmark: ImageVector
    get() {
        if (_bookmark != null) {
            return _bookmark!!
        }
        _bookmark = materialIcon(name = "Filled.Bookmark") {
            materialPath {
                moveTo(17.0f, 3.0f)
                horizontalLineTo(7.0f)
                curveToRelative(-1.1f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(5.0f, 21.0f)
                lineToRelative(7.0f, -3.0f)
                lineToRelative(7.0f, 3.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                close()
            }
        }
        return _bookmark!!
    }

private var _bookmark: ImageVector? = null

public val Icons.Filled.BookmarkAdd: ImageVector
    get() {
        if (_bookmarkAdd != null) {
            return _bookmarkAdd!!
        }
        _bookmarkAdd = materialIcon(name = "Filled.BookmarkAdd") {
            materialPath {
                moveTo(21.0f, 7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(7.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineTo(5.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(3.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(7.0f)
                close()
                moveTo(19.0f, 21.0f)
                lineToRelative(-7.0f, -3.0f)
                lineToRelative(-7.0f, 3.0f)
                verticalLineTo(5.0f)
                curveToRelative(0.0f, -1.1f, 0.9f, -2.0f, 2.0f, -2.0f)
                lineToRelative(7.0f, 0.0f)
                curveToRelative(-0.63f, 0.84f, -1.0f, 1.87f, -1.0f, 3.0f)
                curveToRelative(0.0f, 2.76f, 2.24f, 5.0f, 5.0f, 5.0f)
                curveToRelative(0.34f, 0.0f, 0.68f, -0.03f, 1.0f, -0.1f)
                verticalLineTo(21.0f)
                close()
            }
        }
        return _bookmarkAdd!!
    }

private var _bookmarkAdd: ImageVector? = null

public val Icons.Filled.CalendarMonth: ImageVector
    get() {
        if (_calendarMonth != null) {
            return _calendarMonth!!
        }
        _calendarMonth = materialIcon(name = "Filled.CalendarMonth") {
            materialPath {
                moveTo(19.0f, 4.0f)
                horizontalLineToRelative(-1.0f)
                verticalLineTo(2.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(8.0f)
                verticalLineTo(2.0f)
                horizontalLineTo(6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineTo(5.0f)
                curveTo(3.89f, 4.0f, 3.01f, 4.9f, 3.01f, 6.0f)
                lineTo(3.0f, 20.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveTo(21.0f, 4.9f, 20.1f, 4.0f, 19.0f, 4.0f)
                close()
                moveTo(19.0f, 20.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(10.0f)
                horizontalLineToRelative(14.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(9.0f, 14.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(13.0f, 14.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(17.0f, 14.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(14.0f)
                close()
                moveTo(9.0f, 18.0f)
                horizontalLineTo(7.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(18.0f)
                close()
                moveTo(13.0f, 18.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(18.0f)
                close()
                moveTo(17.0f, 18.0f)
                horizontalLineToRelative(-2.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(18.0f)
                close()
            }
        }
        return _calendarMonth!!
    }

private var _calendarMonth: ImageVector? = null

public val Icons.Filled.CalendarViewMonth: ImageVector
    get() {
        if (_calendarViewMonth != null) {
            return _calendarViewMonth!!
        }
        _calendarViewMonth = materialIcon(name = "Filled.CalendarViewMonth") {
            materialPath {
                moveTo(20.0f, 4.0f)
                horizontalLineTo(4.0f)
                curveTo(2.9f, 4.0f, 2.0f, 4.9f, 2.0f, 6.0f)
                verticalLineToRelative(12.0f)
                curveToRelative(0.0f, 1.1f, 0.9f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(16.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                verticalLineTo(6.0f)
                curveTo(22.0f, 4.9f, 21.1f, 4.0f, 20.0f, 4.0f)
                close()
                moveTo(8.0f, 11.0f)
                horizontalLineTo(4.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(11.0f)
                close()
                moveTo(14.0f, 11.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(11.0f)
                close()
                moveTo(20.0f, 11.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(6.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(11.0f)
                close()
                moveTo(8.0f, 18.0f)
                horizontalLineTo(4.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(18.0f)
                close()
                moveTo(14.0f, 18.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(18.0f)
                close()
                moveTo(20.0f, 18.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(-5.0f)
                horizontalLineToRelative(4.0f)
                verticalLineTo(18.0f)
                close()
            }
        }
        return _calendarViewMonth!!
    }

private var _calendarViewMonth: ImageVector? = null

public val Icons.Filled.Category: ImageVector
    get() {
        if (_category != null) {
            return _category!!
        }
        _category = materialIcon(name = "Filled.Category") {
            materialPath {
                moveTo(12.0f, 2.0f)
                lineToRelative(-5.5f, 9.0f)
                horizontalLineToRelative(11.0f)
                close()
            }
            materialPath {
                moveTo(17.5f, 17.5f)
                moveToRelative(-4.5f, 0.0f)
                arcToRelative(4.5f, 4.5f, 0.0f, true, true, 9.0f, 0.0f)
                arcToRelative(4.5f, 4.5f, 0.0f, true, true, -9.0f, 0.0f)
            }
            materialPath {
                moveTo(3.0f, 13.5f)
                horizontalLineToRelative(8.0f)
                verticalLineToRelative(8.0f)
                horizontalLineTo(3.0f)
                close()
            }
        }
        return _category!!
    }

private var _category: ImageVector? = null

public val Icons.Filled.CheckCircle: ImageVector
    get() {
        if (_checkCircle != null) {
            return _checkCircle!!
        }
        _checkCircle = materialIcon(name = "Filled.CheckCircle") {
            materialPath {
                moveTo(12.0f, 2.0f)
                curveTo(6.48f, 2.0f, 2.0f, 6.48f, 2.0f, 12.0f)
                reflectiveCurveToRelative(4.48f, 10.0f, 10.0f, 10.0f)
                reflectiveCurveToRelative(10.0f, -4.48f, 10.0f, -10.0f)
                reflectiveCurveTo(17.52f, 2.0f, 12.0f, 2.0f)
                close()
                moveTo(10.0f, 17.0f)
                lineToRelative(-5.0f, -5.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(10.0f, 14.17f)
                lineToRelative(7.59f, -7.59f)
                lineTo(19.0f, 8.0f)
                lineToRelative(-9.0f, 9.0f)
                close()
            }
        }
        return _checkCircle!!
    }

private var _checkCircle: ImageVector? = null

public val Icons.Filled.CheckCircleOutline: ImageVector
    get() {
        if (_checkCircleOutline != null) {
            return _checkCircleOutline!!
        }
        _checkCircleOutline = materialIcon(name = "Filled.CheckCircleOutline") {
            materialPath {
                moveTo(16.59f, 7.58f)
                lineTo(10.0f, 14.17f)
                lineToRelative(-3.59f, -3.58f)
                lineTo(5.0f, 12.0f)
                lineToRelative(5.0f, 5.0f)
                lineToRelative(8.0f, -8.0f)
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
        return _checkCircleOutline!!
    }

private var _checkCircleOutline: ImageVector? = null

public val Icons.Filled.Close: ImageVector
    get() {
        if (_close != null) {
            return _close!!
        }
        _close = materialIcon(name = "Filled.Close") {
            materialPath {
                moveTo(19.0f, 6.41f)
                lineTo(17.59f, 5.0f)
                lineTo(12.0f, 10.59f)
                lineTo(6.41f, 5.0f)
                lineTo(5.0f, 6.41f)
                lineTo(10.59f, 12.0f)
                lineTo(5.0f, 17.59f)
                lineTo(6.41f, 19.0f)
                lineTo(12.0f, 13.41f)
                lineTo(17.59f, 19.0f)
                lineTo(19.0f, 17.59f)
                lineTo(13.41f, 12.0f)
                close()
            }
        }
        return _close!!
    }

private var _close: ImageVector? = null

public val Icons.Filled.Done: ImageVector
    get() {
        if (_done != null) {
            return _done!!
        }
        _done = materialIcon(name = "Filled.Done") {
            materialPath {
                moveTo(9.0f, 16.2f)
                lineTo(4.8f, 12.0f)
                lineToRelative(-1.4f, 1.4f)
                lineTo(9.0f, 19.0f)
                lineTo(21.0f, 7.0f)
                lineToRelative(-1.4f, -1.4f)
                lineTo(9.0f, 16.2f)
                close()
            }
        }
        return _done!!
    }

private var _done: ImageVector? = null

public val Icons.Filled.Download: ImageVector
    get() {
        if (_download != null) {
            return _download!!
        }
        _download = materialIcon(name = "Filled.Download") {
            materialPath {
                moveTo(5.0f, 20.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineTo(5.0f)
                verticalLineTo(20.0f)
                close()
                moveTo(19.0f, 9.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineTo(3.0f)
                horizontalLineTo(9.0f)
                verticalLineToRelative(6.0f)
                horizontalLineTo(5.0f)
                lineToRelative(7.0f, 7.0f)
                lineTo(19.0f, 9.0f)
                close()
            }
        }
        return _download!!
    }

private var _download: ImageVector? = null

public val Icons.Filled.Edit: ImageVector
    get() {
        if (_edit != null) {
            return _edit!!
        }
        _edit = materialIcon(name = "Filled.Edit") {
            materialPath {
                moveTo(3.0f, 17.25f)
                verticalLineTo(21.0f)
                horizontalLineToRelative(3.75f)
                lineTo(17.81f, 9.94f)
                lineToRelative(-3.75f, -3.75f)
                lineTo(3.0f, 17.25f)
                close()
                moveTo(20.71f, 7.04f)
                curveToRelative(0.39f, -0.39f, 0.39f, -1.02f, 0.0f, -1.41f)
                lineToRelative(-2.34f, -2.34f)
                curveToRelative(-0.39f, -0.39f, -1.02f, -0.39f, -1.41f, 0.0f)
                lineToRelative(-1.83f, 1.83f)
                lineToRelative(3.75f, 3.75f)
                lineToRelative(1.83f, -1.83f)
                close()
            }
        }
        return _edit!!
    }

private var _edit: ImageVector? = null

public val Icons.Filled.EmojiPeople: ImageVector
    get() {
        if (_emojiPeople != null) {
            return _emojiPeople!!
        }
        _emojiPeople = materialIcon(name = "Filled.EmojiPeople") {
            materialPath {
                moveTo(12.0f, 4.0f)
                moveToRelative(-2.0f, 0.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, true, true, 4.0f, 0.0f)
                arcToRelative(2.0f, 2.0f, 0.0f, true, true, -4.0f, 0.0f)
            }
            materialPath {
                moveTo(15.89f, 8.11f)
                curveTo(15.5f, 7.72f, 14.83f, 7.0f, 13.53f, 7.0f)
                curveToRelative(-0.21f, 0.0f, -1.42f, 0.0f, -2.54f, 0.0f)
                curveTo(8.24f, 6.99f, 6.0f, 4.75f, 6.0f, 2.0f)
                horizontalLineTo(4.0f)
                curveToRelative(0.0f, 3.16f, 2.11f, 5.84f, 5.0f, 6.71f)
                verticalLineTo(22.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(-6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineToRelative(6.0f)
                horizontalLineToRelative(2.0f)
                verticalLineTo(10.05f)
                lineTo(18.95f, 14.0f)
                lineToRelative(1.41f, -1.41f)
                lineTo(15.89f, 8.11f)
                close()
            }
        }
        return _emojiPeople!!
    }

private var _emojiPeople: ImageVector? = null

public val Icons.Filled.Event: ImageVector
    get() {
        if (_event != null) {
            return _event!!
        }
        _event = materialIcon(name = "Filled.Event") {
            materialPath {
                moveTo(17.0f, 12.0f)
                horizontalLineToRelative(-5.0f)
                verticalLineToRelative(5.0f)
                horizontalLineToRelative(5.0f)
                verticalLineToRelative(-5.0f)
                close()
                moveTo(16.0f, 1.0f)
                verticalLineToRelative(2.0f)
                lineTo(8.0f, 3.0f)
                lineTo(8.0f, 1.0f)
                lineTo(6.0f, 1.0f)
                verticalLineToRelative(2.0f)
                lineTo(5.0f, 3.0f)
                curveToRelative(-1.11f, 0.0f, -1.99f, 0.9f, -1.99f, 2.0f)
                lineTo(3.0f, 19.0f)
                curveToRelative(0.0f, 1.1f, 0.89f, 2.0f, 2.0f, 2.0f)
                horizontalLineToRelative(14.0f)
                curveToRelative(1.1f, 0.0f, 2.0f, -0.9f, 2.0f, -2.0f)
                lineTo(21.0f, 5.0f)
                curveToRelative(0.0f, -1.1f, -0.9f, -2.0f, -2.0f, -2.0f)
                horizontalLineToRelative(-1.0f)
                lineTo(18.0f, 1.0f)
                horizontalLineToRelative(-2.0f)
                close()
                moveTo(19.0f, 19.0f)
                lineTo(5.0f, 19.0f)
                lineTo(5.0f, 8.0f)
                horizontalLineToRelative(14.0f)
                verticalLineToRelative(11.0f)
                close()
            }
        }
        return _event!!
    }

private var _event: ImageVector? = null

public val Icons.Filled.ExpandLess: ImageVector
    get() {
        if (_expandLess != null) {
            return _expandLess!!
        }
        _expandLess = materialIcon(name = "Filled.ExpandLess") {
            materialPath {
                moveTo(12.0f, 8.0f)
                lineToRelative(-6.0f, 6.0f)
                lineToRelative(1.41f, 1.41f)
                lineTo(12.0f, 10.83f)
                lineToRelative(4.59f, 4.58f)
                lineTo(18.0f, 14.0f)
                close()
            }
        }
        return _expandLess!!
    }

private var _expandLess: ImageVector? = null

public val Icons.Filled.ExpandMore: ImageVector
    get() {
        if (_expandMore != null) {
            return _expandMore!!
        }
        _expandMore = materialIcon(name = "Filled.ExpandMore") {
            materialPath {
                moveTo(16.59f, 8.59f)
                lineTo(12.0f, 13.17f)
                lineTo(7.41f, 8.59f)
                lineTo(6.0f, 10.0f)
                lineToRelative(6.0f, 6.0f)
                lineToRelative(6.0f, -6.0f)
                close()
            }
        }
        return _expandMore!!
    }

private var _expandMore: ImageVector? = null

public val Icons.Filled.Favorite: ImageVector
    get() {
        if (_favorite != null) {
            return _favorite!!
        }
        _favorite = materialIcon(name = "Filled.Favorite") {
            materialPath {
                moveTo(12.0f, 21.35f)
                lineToRelative(-1.45f, -1.32f)
                curveTo(5.4f, 15.36f, 2.0f, 12.28f, 2.0f, 8.5f)
                curveTo(2.0f, 5.42f, 4.42f, 3.0f, 7.5f, 3.0f)
                curveToRelative(1.74f, 0.0f, 3.41f, 0.81f, 4.5f, 2.09f)
                curveTo(13.09f, 3.81f, 14.76f, 3.0f, 16.5f, 3.0f)
                curveTo(19.58f, 3.0f, 22.0f, 5.42f, 22.0f, 8.5f)
                curveToRelative(0.0f, 3.78f, -3.4f, 6.86f, -8.55f, 11.54f)
                lineTo(12.0f, 21.35f)
                close()
            }
        }
        return _favorite!!
    }

private var _favorite: ImageVector? = null

public val Icons.Filled.FilterList: ImageVector
    get() {
        if (_filterList != null) {
            return _filterList!!
        }
        _filterList = materialIcon(name = "Filled.FilterList") {
            materialPath {
                moveTo(10.0f, 18.0f)
                horizontalLineToRelative(4.0f)
                verticalLineToRelative(-2.0f)
                horizontalLineToRelative(-4.0f)
                verticalLineToRelative(2.0f)
                close()
                moveTo(3.0f, 6.0f)
                verticalLineToRelative(2.0f)
                horizontalLineToRelative(18.0f)
                lineTo(21.0f, 6.0f)
                lineTo(3.0f, 6.0f)
                close()
                moveTo(6.0f, 13.0f)
                horizontalLineToRelative(12.0f)
                verticalLineToRelative(-2.0f)
                lineTo(6.0f, 11.0f)
                verticalLineToRelative(2.0f)
                close()
            }
        }
        return _filterList!!
    }

private var _filterList: ImageVector? = null
