package com.anisequel.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.card.CardSize
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.DpiMode
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.InfoPageStyle
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.domain.ui.NavBarLift
import com.anisequel.domain.ui.PosterOverlays
import com.anisequel.domain.ui.PosterSource
import com.anisequel.domain.ui.ProgressBarStyle
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

private val Context.interfaceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "anisequel_interface_prefs"
)

/**
 * How the app draws, as distinct from what colour it is drawn in.
 *
 * Its own store rather than a key on [LibraryPreferences] or [ThemePreferences],
 * because the card style is read by two surfaces that share neither of those
 * owners: the Library grid belongs to the reader's own list, and the dashboard's
 * sequel rows belong to the recommendations. A style stored under the Library would
 * mean the dashboard has to reach into somebody else's preferences to find out how
 * to draw itself - and the Library store is deliberately about *which* entries are
 * shown, not about the shape they are shown in.
 *
 * ## The layout choice lives here too
 *
 * [settings] is the one read every screen depends on: which presentation the app
 * draws, how dense the new Library is, whether Home opens on the Continue
 * Watching rail, and how far the main bar floats above the bottom edge. They are
 * one `Flow` because they are one answer to "how should this app look" - and
 * because the app root collecting three separate flows would rebuild the
 * navigation graph three times for one tap.
 *
 * Every value is the enum's `storageValue`, never its ordinal: a reordering of the
 * entries would otherwise silently repaint every card in the app, and a style a
 * newer build removed reads back as the plain one rather than as a crash.
 */
class InterfacePreferences(private val context: Context) {

    private object Keys {
        val CARD_STYLE = stringPreferencesKey("media_card_style")
        val APP_LAYOUT = stringPreferencesKey("app_layout")

        /**
         * Which title page the detail sheet draws.
         *
         * Its own key rather than a third value on [APP_LAYOUT]: the two are
         * different questions, and folding the title page into the Home layout
         * would make "I like the new Home but not the new title page" unexpressible.
         */
        val INFO_PAGE_STYLE = stringPreferencesKey("info_page_style")

        /**
         * How the Info page's progress card draws its bar.
         *
         * Its own key because the style outlives one title: the reader picks it
         * once, on whichever card they held, and every title they open afterwards
         * uses it. A per-title value would be another entry field AniList does not
         * have and would reset on every relation walk.
         */
        val PROGRESS_BAR_STYLE = stringPreferencesKey("progress_bar_style")
        val LIBRARY_VIEW = stringPreferencesKey("library_view_mode")
        val SHOW_CONTINUE_RAIL = booleanPreferencesKey("show_continue_rail")
        val HOME_SEQUEL_STYLE = stringPreferencesKey("home_sequel_style")
        val MAIN_BAR_LIFT = intPreferencesKey("main_bar_lift_dp")

        /**
         * The card-design block. Five keys rather than one serialised string: a
         * preferences file is readable and editable, and a blob that fails to parse
         * loses every value in it - a broken title size would also reset bold titles.
         * Separate keys also let a future build migrate one of them without knowing
         * the format of the other four.
         */
        val CARD_TITLE_SCALE = floatPreferencesKey("card_title_scale")
        val CARD_RATING_SCALE = floatPreferencesKey("card_rating_scale")
        val CARD_SIZE = stringPreferencesKey("card_size")
        val CARD_BOLD_TITLES = booleanPreferencesKey("card_bold_titles")
        val CARD_SCROLL_TITLES = booleanPreferencesKey("card_scroll_titles")

        /**
         * Whether a card prints its own name and score.
         *
         * Default true, which is what every card drew before these keys existed -
         * a reader who has never opened Settings sees no change on upgrading.
         */
        val CARD_SHOW_TITLES = booleanPreferencesKey("card_show_titles")
        val CARD_SHOW_SCORES = booleanPreferencesKey("card_show_scores")
        val CARD_CORNER_RADIUS = floatPreferencesKey("card_corner_radius")
        val SHOW_MY_RATINGS = booleanPreferencesKey("show_my_ratings")
        val SHOW_THEIR_RATINGS = booleanPreferencesKey("show_their_ratings")

        /** Which art a card draws; see [PosterSource]. */
        val POSTER_SOURCE = stringPreferencesKey("poster_source")

        /**
         * What btttr composites onto a BetterPosters poster; see [PosterOverlays].
         *
         * Three booleans rather than one serialised value, for the same reason the
         * card design has five keys: a preferences file is readable, and a blob that
         * fails to parse loses every switch at once.
         */
        val POSTER_SHOW_RATING = booleanPreferencesKey("poster_show_rating")
        val POSTER_SHOW_GENRE = booleanPreferencesKey("poster_show_genre")
        val POSTER_SHOW_TREND_TAGS = booleanPreferencesKey("poster_show_trend_tags")

        /**
         * Whether a sequel season keeps its own AniList cover instead of repeating
         * the series poster btttr draws for every season.
         *
         * On by default: the repeated picture is the thing a reader notices in a
         * grid, and AniList's entry has the season-specific art and name.
         */
        val POSTER_UNIQUE_SEASONS = booleanPreferencesKey("poster_unique_seasons")

        /**
         * The display density the whole app draws at, in dpi.
         *
         * An int because it is a whole dpi: the slider's label prints it, the
         * preference stores it and [DisplayDpi.density] divides it once.
         */
        val DISPLAY_DPI = intPreferencesKey("display_dpi")

        /**
         * Whether [DISPLAY_DPI] is in force or the device's own density is.
         *
         * Its own key so switching to Automatic does not throw away the number the
         * reader chose: they can come back to Manual and find it waiting. Absent
         * means [DpiMode.DEFAULT], which is Manual - the mode every install drew at
         * before this key existed.
         */
        val DPI_MODE = stringPreferencesKey("dpi_mode")
    }

    /** Everything about the interface that is not the colour palette. */
    data class InterfaceSettings(
        val layout: AppLayout = AppLayout.DEFAULT,
        /**
         * Which title page the Info destination draws.
         *
         * Part of this emission so the app root provides it once beside the layout
         * it belongs with; the detail sheet reads it through
         * [com.anisequel.ui.theme.LocalInfoPageStyle].
         * Defaults to Nova now; an explicit "classic" is still honoured, so an
         * install that chose the original page keeps it.
         */
        val infoPageStyle: InfoPageStyle = InfoPageStyle.DEFAULT,
        /** The bar style chosen by holding a progress card; see [ProgressBarStyle]. */
        val progressBarStyle: ProgressBarStyle = ProgressBarStyle.DEFAULT,
        val libraryView: LibraryViewMode = LibraryViewMode.DEFAULT,
        val showContinueRail: Boolean = true,
        val homeSequelStyle: HomeSequelStyle = HomeSequelStyle.DEFAULT,
        /**
         * How far the main bar floats above the bottom edge, in dp.
         *
         * Whole dp because the slider is discrete; see [NavBarLift]. The default
         * keeps the shipped bar on the device's navigation inset.
         */
        val mainBarLift: Int = NavBarLift.DEFAULT_DP,
        /**
         * How big and heavy a card's own text is, and how many fit a row.
         *
         * Part of this emission so the app root provides it once, beside the card
         * style it belongs with: the two settings describe the same card, and a
         * separate flow would rebuild the navigation graph a second time per tap.
         */
        val cardDesign: CardDesign = CardDesign.DEFAULT,
        /**
         * Whether a card prints the score *the reader* gave, on their own list.
         *
         * Off is not "hide the rating": it is "show the community average instead",
         * which is what every card drew before there was a choice. AniList keeps both
         * numbers, and which one a card should print is a reader's preference rather
         * than a fact about the entry.
         */
        val showMyRatings: Boolean = true,
        /**
         * Whether a card prints the score the *profile's owner* gave, on their list.
         *
         * Separate from [showMyRatings] because the two are read in different
         * contexts: on your own Library the personal number is a reminder of a verdict
         * you already made, and on somebody else's profile it is the thing you opened
         * the page for. A reader may want one without the other.
         */
        val showTheirRatings: Boolean = true,
        /**
         * Where cards get their cover art.
         *
         * Part of this emission for the same reason the card design is: it is a fact
         * about how a card is drawn, and the app root provides it once rather than
         * leaving each surface to read the store for itself.
         */
        val posterSource: PosterSource = PosterSource.DEFAULT,
        /**
         * What the chosen btttr poster has printed on the artwork itself.
         *
         * Part of this emission for the same reason the source is: it is a fact
         * about the picture a card draws, and the app root provides it once.
         */
        val posterOverlays: PosterOverlays = PosterOverlays.DEFAULT,
        /**
         * Keep a sequel season on its own cover rather than on btttr's one series
         * poster - see [com.anisequel.domain.ui.SequelSeason].
         */
        val posterUniqueSeasons: Boolean = true,
        /**
         * The density the whole app draws at, in dpi.
         *
         * Read at `MainActivity` as well as here - it is the one preference that has
         * to be in scope above the theme - and clamped on read because the slider is
         * not the only writer: a hand-edited file or an older build's value must land
         * inside a drawable range rather than throw inside `Density`.
         */
        val displayDpi: Int = DisplayDpi.DEFAULT_DPI,
        /**
         * Whether [displayDpi] is used or the panel's own density is followed.
         *
         * Read at `MainActivity` beside the number: the two are one answer, and a
         * second flow would let the mode change a frame after the number.
         */
        val dpiMode: DpiMode = DpiMode.DEFAULT,
        /**
         * Whether this is the stored answer or the defaults above.
         *
         * Read by the launch gate. The density - the number *and* the mode - is
         * applied above the theme, so a first frame drawn from the defaults is the
         * whole app laid out at the shipped 447dpi and then re-laid-out at the
         * reader's choice one frame later: the visible resize that seeding "the
         * shipped default rather than the device's dpi" was supposed to prevent,
         * arriving one frame late instead of on frame zero. `MainActivity` therefore
         * waits for this answer the way it already waits for the theme's, and that
         * wait has to be able to *end*: see the `.catch` on [settings].
         */
        val isLoaded: Boolean = false
    )

    /**
     * The chosen card style, defaulting to the plain layout.
     *
     * A [Flow] like every other preference here, so a tap on a style chip repaints
     * the grid behind the Settings page the moment the page is left - the reader sees
     * the choice land, which is the only proof a preview was worth drawing.
     */
    val cardStyle: Flow<MediaCardStyle> =
        context.interfaceDataStore.data.map { preferences ->
            MediaCardStyle.fromStorage(preferences[Keys.CARD_STYLE])
        }

    /**
     * The layout, density and Home-rail choice, in one emission.
     *
     * Read once at the app root and provided as [com.anisequel.ui.theme.LocalAppLayout],
     * [com.anisequel.ui.theme.LocalLibraryViewMode],
     * [com.anisequel.ui.theme.LocalShowContinueRail] and
     * [com.anisequel.ui.theme.LocalMainBarLift]. The defaults are the shipped
     * ones, so an install that has never touched Settings looks exactly as it did.
     */
    val settings: Flow<InterfaceSettings> =
        context.interfaceDataStore.data.map { preferences ->
            InterfaceSettings(
                layout = AppLayout.fromStorage(preferences[Keys.APP_LAYOUT]),
                infoPageStyle = InfoPageStyle.fromStorage(preferences[Keys.INFO_PAGE_STYLE]),
                progressBarStyle = ProgressBarStyle.fromStorage(
                    preferences[Keys.PROGRESS_BAR_STYLE]
                ),
                libraryView = LibraryViewMode.fromStorage(preferences[Keys.LIBRARY_VIEW]),
                showContinueRail = preferences[Keys.SHOW_CONTINUE_RAIL] ?: true,
                homeSequelStyle = HomeSequelStyle.fromStorage(
                    preferences[Keys.HOME_SEQUEL_STYLE]
                ),
                // Clamped here as well as in the setter: a value written before
                // `NavBarLift` gained its ceiling, or by hand, must not lift the bar
                // off the page.
                mainBarLift = NavBarLift.clamp(
                    preferences[Keys.MAIN_BAR_LIFT] ?: NavBarLift.DEFAULT_DP
                ),
                // Clamped on read for the same reason the lift is: a scale written
                // by an older build is still a number this app has to draw, and the
                // page clamps its slider but the grid does not know the slider.
                cardDesign = CardDesign(
                    titleScale = preferences[Keys.CARD_TITLE_SCALE]
                        ?: CardDesign.DEFAULT_TITLE_SCALE,
                    ratingScale = preferences[Keys.CARD_RATING_SCALE]
                        ?: CardDesign.DEFAULT_RATING_SCALE,
                    size = CardSize.fromStorage(preferences[Keys.CARD_SIZE]),
                    boldTitles = preferences[Keys.CARD_BOLD_TITLES] ?: false,
                    scrollTitles = preferences[Keys.CARD_SCROLL_TITLES] ?: true,
                    // `?: true`, matching both [CardDesign]'s default and what
                    // 2.3.1's release notes promised. The false that shipped here
                    // was a real defect: every grid lost its caption on upgrade,
                    // and the switch that would bring it back described itself as
                    // already on.
                    showTitles = preferences[Keys.CARD_SHOW_TITLES] ?: true,
                    showScores = preferences[Keys.CARD_SHOW_SCORES] ?: true,
                    // A radius written before this key existed is the shipped
                    // 12dp, so an upgrade does not change how every cover looks.
                    cornerRadius = preferences[Keys.CARD_CORNER_RADIUS]
                        ?: CardDesign.DEFAULT_CORNER_RADIUS
                ).clamped(),
                showMyRatings = preferences[Keys.SHOW_MY_RATINGS] ?: true,
                showTheirRatings = preferences[Keys.SHOW_THEIR_RATINGS] ?: true,
                posterSource = PosterSource.fromStorage(preferences[Keys.POSTER_SOURCE]),
                // Clean posters are the default: btttr's score, genre line and
                // trend strip are all opt-in, and "unset" has to mean off so an
                // upgrade from a build without these keys lands on the clean art.
                posterOverlays = PosterOverlays(
                    showRating = preferences[Keys.POSTER_SHOW_RATING] ?: false,
                    showGenre = preferences[Keys.POSTER_SHOW_GENRE] ?: false,
                    showTrendTags = preferences[Keys.POSTER_SHOW_TREND_TAGS] ?: false
                ),
                posterUniqueSeasons = preferences[Keys.POSTER_UNIQUE_SEASONS] ?: true,
                displayDpi = DisplayDpi.clamp(
                    preferences[Keys.DISPLAY_DPI] ?: DisplayDpi.DEFAULT_DPI
                ),
                dpiMode = DpiMode.fromStorage(preferences[Keys.DPI_MODE]),
                isLoaded = true
            )
        }
            // The same read failure as the theme's, and now the same consequence:
            // the launch gate waits for this emission before it draws anything, so
            // an `IOException` on a corrupt interface file used to be a splash with
            // no exit until the watchdog. Emitting the defaults is the recoverable
            // answer - the reader gets the app at the shipped density, and the
            // stored choices are still on disk if the read was merely transient.
            //
            // Only `IOException` is swallowed: a defaulted layout is not a fix for a
            // bug in the parser.
            .catch { error ->
                if (error is IOException) {
                    emit(InterfaceSettings(isLoaded = true))
                } else {
                    throw error
                }
            }

    suspend fun setCardStyle(style: MediaCardStyle) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.CARD_STYLE] = style.storageValue
        }
    }

    suspend fun setLayout(layout: AppLayout) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.APP_LAYOUT] = layout.storageValue
        }
    }

    /**
     * Which title page the Info destination draws.
     *
     * A separate setter from [setLayout] for the reason [InfoPageStyle] gives: the
     * two choices are independent, and one picker must not silently rewrite the
     * other.
     */
    suspend fun setInfoPageStyle(style: InfoPageStyle) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.INFO_PAGE_STYLE] = style.storageValue
        }
    }

    /**
     * The bar style the next Info page draws.
     *
     * Written from the picker a long hold opens on a progress card. The setter
     * takes the enum rather than a string so a typo cannot write a value every
     * read then has to fall back from.
     */
    suspend fun setProgressBarStyle(style: ProgressBarStyle) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.PROGRESS_BAR_STYLE] = style.storageValue
        }
    }

    suspend fun setLibraryView(view: LibraryViewMode) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.LIBRARY_VIEW] = view.storageValue
        }
    }

    suspend fun setShowContinueRail(show: Boolean) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.SHOW_CONTINUE_RAIL] = show
        }
    }

    /**
     * How the new Home draws its recommendation list.
     *
     * A setting rather than a second screen: both layouts read the same state and
     * call the same callbacks, so the reader switches the shape and keeps their
     * place - see [HomeSequelStyle].
     */
    suspend fun setHomeSequelStyle(style: HomeSequelStyle) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.HOME_SEQUEL_STYLE] = style.storageValue
        }
    }

    /**
     * How far the main bar floats above the bottom edge.
     *
     * Clamped, not rejected: the slider cannot ask for a value outside the range,
     * but the preference is public and a hand-edited or future value should land
     * at the nearest sensible position rather than make the bar disappear.
     */
    suspend fun setMainBarLift(liftDp: Int) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.MAIN_BAR_LIFT] = NavBarLift.clamp(liftDp)
        }
    }

    /**
     * The whole card design, written at once.
     *
     * One write rather than four setters because the page commits a drag on release:
     * the reader moved a title size, and everything that belongs to that title moved
     * with it. A per-field setter would still be here if a screen needed to change one
     * in isolation - none does.
     */
    suspend fun setCardDesign(design: CardDesign) {
        val clamped = design.clamped()
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.CARD_TITLE_SCALE] = clamped.titleScale
            preferences[Keys.CARD_RATING_SCALE] = clamped.ratingScale
            preferences[Keys.CARD_SIZE] = clamped.size.storageValue
            preferences[Keys.CARD_BOLD_TITLES] = clamped.boldTitles
            preferences[Keys.CARD_SCROLL_TITLES] = clamped.scrollTitles
            preferences[Keys.CARD_SHOW_TITLES] = clamped.showTitles
            preferences[Keys.CARD_SHOW_SCORES] = clamped.showScores
            preferences[Keys.CARD_CORNER_RADIUS] = clamped.cornerRadius
        }
    }

    /**
     * Whether the reader's own rating is preferred over the community average.
     *
     * Two setters rather than one pair-of-booleans call: the two switches sit under
     * one heading and are saved independently, so a single write would re-emit the
     * other half from a value the page happened to be holding.
     */
    suspend fun setShowMyRatings(show: Boolean) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.SHOW_MY_RATINGS] = show
        }
    }

    /** Whether the profile owner's rating is preferred over the community average. */
    suspend fun setShowTheirRatings(show: Boolean) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.SHOW_THEIR_RATINGS] = show
        }
    }

    /** Where cards draw their cover art from - AniList's own, or btttr's rendered art. */
    suspend fun setPosterSource(source: PosterSource) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.POSTER_SOURCE] = source.storageValue
        }
    }

    /**
     * Which of btttr's own overlays the poster keeps.
     *
     * One write for the three switches: they are one picture, and the URL builder
     * reads them together - a poster with the score removed but the genre kept is a
     * variant, not a half-applied set.
     */
    suspend fun setPosterOverlays(overlays: PosterOverlays) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.POSTER_SHOW_RATING] = overlays.showRating
            preferences[Keys.POSTER_SHOW_GENRE] = overlays.showGenre
            preferences[Keys.POSTER_SHOW_TREND_TAGS] = overlays.showTrendTags
        }
    }

    /**
     * Whether a sequel season keeps its own cover.
     *
     * See [com.anisequel.domain.ui.SequelSeason]: btttr has one series poster per
     * IMDb entry, so seasons 2 and up would otherwise share season one's picture.
     */
    suspend fun setPosterUniqueSeasons(unique: Boolean) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.POSTER_UNIQUE_SEASONS] = unique
        }
    }

    /**
     * The display density, clamped rather than rejected.
     *
     * The slider cannot ask for a value outside the range, but this is a public
     * setter over a plain integer: clamped so a bad write lands at the nearest
     * drawable density instead of throwing inside the theme's `Density`.
     */
    suspend fun setDisplayDpi(dpi: Int) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.DISPLAY_DPI] = DisplayDpi.clamp(dpi)
        }
    }

    /**
     * Automatic or Manual density.
     *
     * The chosen number is left alone either way: switching to Automatic is a
     * preview of the device's density, not a decision to forget the reader's own.
     */
    suspend fun setDpiMode(mode: DpiMode) {
        context.interfaceDataStore.edit { preferences ->
            preferences[Keys.DPI_MODE] = mode.storageValue
        }
    }

    /**
     * Back to the shipped layout, title page, progress bar, density, rail, card style, poster source and bar position.
     *
     * The progress bar style is cleared here for the same reason the title page is:
     * both are the shape of a page the layout choice draws, and leaving one of the
     * two behind made a reset restore everything except the bar under every title.
     * It was the one key declared in this file that the reset skipped.
     */
    suspend fun reset() {
        context.interfaceDataStore.edit { preferences ->
            preferences.remove(Keys.CARD_STYLE)
            preferences.remove(Keys.APP_LAYOUT)
            preferences.remove(Keys.INFO_PAGE_STYLE)
            preferences.remove(Keys.PROGRESS_BAR_STYLE)
            preferences.remove(Keys.LIBRARY_VIEW)
            preferences.remove(Keys.SHOW_CONTINUE_RAIL)
            preferences.remove(Keys.HOME_SEQUEL_STYLE)
            preferences.remove(Keys.MAIN_BAR_LIFT)
            preferences.remove(Keys.CARD_TITLE_SCALE)
            preferences.remove(Keys.CARD_RATING_SCALE)
            preferences.remove(Keys.CARD_SIZE)
            preferences.remove(Keys.CARD_BOLD_TITLES)
            preferences.remove(Keys.CARD_SCROLL_TITLES)
            preferences.remove(Keys.CARD_SHOW_TITLES)
            preferences.remove(Keys.CARD_SHOW_SCORES)
            preferences.remove(Keys.CARD_CORNER_RADIUS)
            preferences.remove(Keys.SHOW_MY_RATINGS)
            preferences.remove(Keys.SHOW_THEIR_RATINGS)
            preferences.remove(Keys.POSTER_SOURCE)
            preferences.remove(Keys.POSTER_SHOW_RATING)
            preferences.remove(Keys.POSTER_SHOW_GENRE)
            preferences.remove(Keys.POSTER_SHOW_TREND_TAGS)
            preferences.remove(Keys.POSTER_UNIQUE_SEASONS)
            preferences.remove(Keys.DISPLAY_DPI)
            preferences.remove(Keys.DPI_MODE)
        }
    }
}
