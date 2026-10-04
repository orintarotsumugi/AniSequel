package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import com.example.ui.components.icons.AppCustomVectors
import com.example.ui.components.icons.AppExtraVectors

/**
 * Central vector icon repository for AniSequel to avoid emoji usage
 * and enforce strict SVG/ImageVector compliance.
 */
object AppVectorIcons {
    val Search = Icons.Default.Search
    val Filter = Icons.Default.FilterList
    val Refresh = Icons.Default.Refresh
    val Settings = Icons.Default.Settings
    val Logout = Icons.AutoMirrored.Filled.Logout
    val Login = Icons.AutoMirrored.Filled.Login
    val Movie = Icons.Default.Movie
    val Tv = Icons.Default.Tv
    val SequelArrow = Icons.AutoMirrored.Filled.ArrowForward
    val SequelJump = AppCustomVectors.SequelJump
    val FranchiseBranch = AppExtraVectors.FranchiseBranch
    val CheckDouble = AppExtraVectors.CheckDouble
    val CalendarClock = AppExtraVectors.CalendarClock
    val BookmarkAdd = Icons.Default.BookmarkAdd
    val BookmarkDone = Icons.Default.Bookmark
    val CheckCircle = Icons.Default.CheckCircle
    val Star = Icons.Default.Star
    val AnimeSparkle = AppCustomVectors.AnimeSparkle
    val Calendar = Icons.Default.CalendarMonth
    val Warning = Icons.Default.Warning
    val Close = Icons.Default.Close
    val Done = Icons.Default.Done
    val List = Icons.AutoMirrored.Filled.List
    val OpenInBrowser = Icons.Default.OpenInBrowser
    val OpenInNew = Icons.AutoMirrored.Filled.OpenInNew
    val Visibility = Icons.Default.Visibility
    val Play = Icons.Default.PlayArrow
    val Trailer = AppCustomVectors.TrailerPlay
    val Info = Icons.Default.Info
    val Schedule = Icons.Default.Schedule
    val SourceBook = Icons.AutoMirrored.Filled.MenuBook
    val Trending = Icons.AutoMirrored.Filled.TrendingUp
    val Studio = AppCustomVectors.StudioBuilding
    val Trophy = AppCustomVectors.TrophyRank
    val Tag = AppCustomVectors.TagLabel
    val SystemUpdate = Icons.Default.SystemUpdate
    val Download = Icons.Default.Download
    val Pause = Icons.Default.PauseCircle

    // Settings section headers. All from the extended set, which R8 prunes to
    // whatever is actually reachable - adding names here costs nothing in the
    // release APK, and it keeps every Settings header in one reviewable list.
    val SectionAppearance = Icons.Outlined.ColorLens
    val SectionAniList = Icons.Outlined.Api
    val SectionAbout = Icons.Outlined.Contrast
    val SectionUpdates = Icons.Default.SystemUpdate
    val SectionHelp = Icons.AutoMirrored.Filled.MenuBook
    val ThemeLight = Icons.Outlined.LightMode
    val ThemeDark = Icons.Outlined.DarkMode
    val Restore = Icons.Outlined.RestartAlt
    val Tune = Icons.Outlined.Tune
    val NewReleases = Icons.Outlined.NewReleases
    val FullscreenExpand = AppCustomVectors.FullscreenExpand
}
