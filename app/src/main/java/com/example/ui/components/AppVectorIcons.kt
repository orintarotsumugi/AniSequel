package com.example.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
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
    val OverflowMenu = Icons.Default.MoreHoriz
    val ExpandMore = Icons.Default.ExpandMore
    val ExpandLess = Icons.Default.ExpandLess

    // The profile screen's four destinations. Every one is named for what the
    // destination *is*, and each of the four was picked because it cannot be
    // mistaken for one of the others at 22dp.
    //
    // `Insights` rather than `BarChart`: the material set has no `BarChart` at
    // all, and `Insights` is the icon that is actually a bar chart. Verified
    // against the material-icons-extended sources rather than assumed, because
    // a wrong import name here is a build failure discovered in CI.
    val ProfileHome = Icons.Default.Home
    val ProfileActivity = Icons.AutoMirrored.Filled.Comment
    val ProfileStats = Icons.Default.Insights
    val ProfileSocial = Icons.Default.Group

    // Section headers on the profile screen.
    val FavouriteAnime = Icons.Default.Movie
    val FavouriteManga = Icons.AutoMirrored.Filled.MenuBook
    val FavouriteCharacters = Icons.Default.EmojiPeople
    val FavouriteStaff = Icons.Default.Badge
    val FavouriteStudios = AppCustomVectors.StudioBuilding
    val ProfileAbout = Icons.Default.FormatQuote
    val ProfileJoined = Icons.Default.CalendarMonth
    val ProfileUpdated = Icons.Default.Schedule

    // One per Stats-tab chart, so the card and its meaning are never a guess.
    val StatSummary = Icons.Default.Insights
    val StatStatus = Icons.Default.RadioButtonChecked
    val StatFormat = Icons.Default.Smartphone
    val StatCountry = Icons.Default.Public
    val StatScore = Icons.Default.BarChart
    val StatEpisodeCount = Icons.Default.Timer
    val StatReleaseYear = Icons.Default.Event
    val StatWatchYear = Icons.Default.CalendarViewMonth
    val StatGenre = Icons.Default.LocalOffer
    val ActivityReplies = Icons.AutoMirrored.Filled.Comment

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
