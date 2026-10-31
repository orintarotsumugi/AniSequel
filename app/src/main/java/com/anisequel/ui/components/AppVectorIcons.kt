package com.anisequel.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdd
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CalendarViewMonth
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.EmojiPeople
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material.icons.outlined.Api
import androidx.compose.material.icons.outlined.ColorLens
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.NewReleases
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.Tune
import com.anisequel.ui.components.icons.AppCustomVectors
import com.anisequel.ui.components.icons.AppExtraVectors

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

    // The Library destination. A shelf of books, not the Stats tab's stopwatch:
    // "the list I keep" and "time spent watching" shared one glyph, which made two
    // unrelated destinations look like the same page at 22dp.
    val Library = AppExtraVectors.LibraryShelf
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

    // The title page's one-tap "copy the AniList link" action. Named for the thing
    // the control does rather than for the glyph, so it survives a redraw; a chain
    // link is the one pictogram that means "this is a URL" without a word.
    val CopyLink = Icons.Default.Link
    val Visibility = Icons.Default.Visibility

    // GitHub's own mark, for the link to this app's source. The one glyph here
    // that is a brand rather than a drawing of an idea: a link to GitHub wearing
    // a generic "code" or "link" icon is a link the reader has to read.
    val GitHub = AppExtraVectors.GitHub

    // The connection status strip's two faces. SignalOn is the same glyph the
    // system Wi-Fi picker uses, so "can I reach AniList" reads without a label.
    val SignalOn = Icons.Default.Wifi
    val SignalOff = Icons.Default.WifiOff

    // The heartbreak/recovery family. Both hearts are hand-authored halves in
    // AppCustomVectors so the scene can animate them apart and back together;
    // these two are the single-glyph versions for buttons and section headers.
    val BrokenHeart = AppCustomVectors.BrokenHeart
    val HeartMended = AppCustomVectors.HeartMended
    val Grass = AppExtraVectors.GrassTuft
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

    // The library's "make one" action. Named for what it makes, not for the shape
    // it is drawn with, so the day it is redrawn nobody has to rename a screen.
    val NewList = Icons.Default.Add
    val ExpandMore = Icons.Default.ExpandMore
    val ExpandLess = Icons.Default.ExpandLess

    // The entry editor's own controls. The stepper pair is named for the
    // direction it moves a number rather than for the glyph, so a redraw cannot
    // silently swap them.
    val StepUp = Icons.Default.Add
    val StepDown = Icons.Default.Remove
    val Privacy = Icons.Default.Lock
    val LockOpen = Icons.Default.LockOpen
    val Export = Icons.Default.Image
    val EditEntry = Icons.Default.Edit

    // The profile screen's four destinations. Every one is named for what the
    // destination *is*, and each of the four was picked because it cannot be
    // mistaken for one of the others at 22dp.
    //
    // `Insights` rather than `BarChart`: [StatScore] already uses `BarChart`
    // for the score-distribution chart on the Stats tab, and a destination tab
    // wearing the glyph of a chart inside the destination one tap away reads as
    // a shortcut to that chart rather than as the tab's own page. `Insights`
    // reads as analytics without being the same drawing, so the two sit on one
    // screen without being mistaken for each other.
    val ProfileHome = Icons.Default.Home
    val ProfileActivity = Icons.AutoMirrored.Filled.Comment
    val ProfileStats = Icons.Default.Insights
    val ProfileSocial = Icons.Default.Group
    // The Activity tab's Messages segment: site messages rather than a feed. An
    // envelope rather than a chat bubble, which is already [ProfileActivity] -
    // the two sit side by side in the segment row and must not read as one icon.
    //
    // `Mail`, not `Email`: 1.7.8 has no `Email` icon at all - the Material name
    // changed - so `Icons.Default.Email` is an unresolved reference discovered in
    // CI, not at a glance. Checked against `material-icons-extended-android`'s own
    // class list rather than assumed, the same way [ProfileStats] was.
    val ProfileMessages = Icons.Default.Mail
    // The same distinction inside a message card: an envelope arriving, against
    // [ActivitySend] leaving.
    val MessageReceived = Icons.Default.Inbox
    // The main bar's Profile tab falls back to this while the viewer's avatar is unknown.
    val ProfilePerson = Icons.Default.AccountCircle

    // The Social tab's view switch, which shows the mode currently on screen: a
    // grid of faces, or the same people as name rows. Paired with [List], which is
    // already the app's list glyph, so the two states cannot drift apart.
    val ViewGrid = Icons.Default.GridView

    // Section headers on the profile screen.
    val FavouriteAnime = Icons.Default.Movie
    val FavouriteManga = Icons.AutoMirrored.Filled.MenuBook
    val FavouriteCharacters = Icons.Default.EmojiPeople
    val FavouriteStaff = Icons.Default.Badge
    val FavouriteStudios = AppCustomVectors.StudioBuilding
    val ProfileAbout = Icons.Default.FormatQuote
    val ProfileJoined = Icons.Default.CalendarMonth
    val ProfileUpdated = Icons.Default.Schedule

    // The person sheet's "Voiced by" heading. A microphone is the one glyph that
    // means "a voice rather than a face", which is what the section lists: the people
    // who perform the character the page is about.
    val Mic = Icons.Default.Mic

    // One per Stats-tab chart, so the card and its meaning are never a guess.
    val StatSummary = Icons.Default.Insights
    val StatStatus = Icons.Default.RadioButtonChecked
    // `Category`, not `Smartphone`: the Format card breaks a list down by TV,
    // Movie, OVA, ONA and Special. A phone stood for none of those - it read as
    // "the device this app is on" on a card that is about what was watched.
    val StatFormat = Icons.Default.Category
    val StatCountry = Icons.Default.Public
    val StatScore = Icons.Default.BarChart
    // `FormatListNumbered`, not `Timer`: the card counts episodes per bucket -
    // 1-12, 13-24 - and a stopwatch says "time spent", which is the summary
    // card above it, not this one.
    val StatEpisodeCount = Icons.Default.FormatListNumbered
    val StatReleaseYear = Icons.Default.Event
    val StatWatchYear = Icons.Default.CalendarViewMonth
    val StatGenre = Icons.Default.LocalOffer
    val ActivityReplies = Icons.AutoMirrored.Filled.Comment

    // Activity engagement. Filled and outlined hearts are two states of one
    // control, so they are named for the state rather than the shape.
    val ActivityLiked = Icons.Default.Favorite
    val ActivityNotLiked = Icons.Outlined.FavoriteBorder
    val ActivitySend = Icons.AutoMirrored.Filled.Send
    // The overflow on the viewer's own comment. Horizontal dots rather than
    // vertical: the control sits beside a name and a timestamp on one line, and
    // the glyph reads as "more of this row" rather than as another line of it.
    val ActivityManage = Icons.Default.MoreHoriz

    // The comment composer's format bar. All four are hand-authored vectors in
    // `AppCustomVectors`, so one toolbar is not half a font glyph and half paths
    // that scale with the icon font loading or not.
    val TextBold = AppCustomVectors.TextBold
    val TextItalic = AppCustomVectors.TextItalic
    val TextSpoiler = AppCustomVectors.TextSpoiler
    val AttachImage = AppCustomVectors.AttachImage

    // Settings section headers. All from the extended set, which R8 prunes to
    // whatever is actually reachable - adding names here costs nothing in the
    // release APK, and it keeps every Settings header in one reviewable list.
    val SectionAppearance = Icons.Outlined.ColorLens
    val SectionAniList = Icons.Outlined.Api
    val SectionAbout = Icons.Outlined.Contrast
    val SectionUpdates = Icons.Default.SystemUpdate
    val SectionHelp = Icons.AutoMirrored.Filled.MenuBook
    val SectionBackground = AppCustomVectors.SurfaceTones
    val SectionMotion = AppCustomVectors.MotionWaves
    val ThemeLight = Icons.Outlined.LightMode
    val ThemeDark = Icons.Outlined.DarkMode
    val Restore = Icons.Outlined.RestartAlt
    val Tune = Icons.Outlined.Tune

    // The tier board's undo/redo pair. Auto-mirrored: in a right-to-left locale
    // the arrows have to point the other way, and a hand-flipped glyph here would
    // be the one control on the screen pointing backwards.
    val Undo = Icons.AutoMirrored.Filled.Undo
    val Redo = Icons.AutoMirrored.Filled.Redo
    val NewReleases = Icons.Outlined.NewReleases
    val FullscreenExpand = AppCustomVectors.FullscreenExpand

    // The three motion styles on the Theme tab, each named for what the style
    // *is*: no motion, calm motion, everything-bounces motion. They sit side
    // by side in one segment bar, so their glyphs must read as different.
    val MotionInstant = Icons.Outlined.RestartAlt
    val MotionSmooth = AppCustomVectors.MotionWaves
    val MotionChill = AppCustomVectors.MotionBolt
}
