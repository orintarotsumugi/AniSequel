package com.anisequel.ui.screens.settings

import androidx.compose.ui.graphics.vector.ImageVector
import com.anisequel.ui.components.AppVectorIcons
import java.text.Normalizer
import java.util.Locale

/**
 * The Settings search: one index, one ranking, one highlighter.
 *
 * ## Why the index is not just the hub's rows
 *
 * The shelf has ten rows, and a search that answers with ten rows is a table of
 * contents with a filter. A reader looking for "OLED" or "app lock" wants the card
 * that holds the switch, not the page that holds the card. The index therefore
 * contains the shelf destinations *and* the named parts inside them, each carrying
 * the page and sub-section that must open to reach it. The fields that cannot be
 * reached directly - a switch inside a card - still resolve to their page; the
 * result is honest about the level it can land on.
 *
 * ## Why the matching is written here instead of in the UI
 *
 * Ranking, normalisation and fuzzy fallback are pure functions. Keeping them out of
 * the composable is what lets `SettingsSearchTest` pin the answers without a Compose
 * harness, and it keeps the search field from carrying a second implementation of
 * "what counts as a match".
 *
 * ## Why every result carries a page
 *
 * A search result that only knows a label is a suggestion the app cannot honour.
 * [SettingsSearchResult.page] plus the optional [interfaceSection] or
 * [themeSection] is the complete instruction [SettingsScreen] needs, so a typo in
 * the index shows up as a dead result instead of a wrong page.
 */
internal data class SettingsSearchResult(
    val id: String,
    val page: SettingsPage,
    val title: String,
    val subtitle: String,
    /** The page (and part) the result belongs to, printed above the title. */
    val eyebrow: String,
    val icon: ImageVector,
    /** Extra words a reader may type that the visible copy does not contain. */
    val keywords: String = "",
    val interfaceSection: InterfaceSection? = null,
    val themeSection: ThemeSection? = null,
    /**
     * The shelf part a result belongs to inside a page that has parts, as the
     * section enum's name.
     *
     * A name rather than a typed field per page: `SettingsScreen` carries one
     * `rememberSaveable` target and each shelf resolves the name against its own
     * enum. The typed `interfaceSection`/`themeSection` above predate that and are
     * kept because their two pages' handoffs are typed; every newer shelf uses this.
     */
    val sectionName: String? = null,
    /** A small ranking bias for the settings a reader changes most often. */
    val priority: Int = 0,
    /** True only for a result found by the typo fallback. */
    val fuzzy: Boolean = false
)

/**
 * The searchable Settings index.
 *
 * The order of [allResults] is not the answer order; it is only the order equal
 * scores fall back to, which is why the hand-picked suggestions are looked up by id
 * rather than by position.
 */
internal object SettingsSearch {

    private const val MAX_RESULTS = 24

    /** The five settings offered before the reader has typed anything. */
    private val suggestionIds = listOf(
        "info_page",
        "theme_mode",
        "app_lock",
        "interface_motion",
        "whats_new"
    )

    private val allResults: List<SettingsSearchResult> by lazy { buildIndex() }

    /** Empty for an empty query; the caller draws the suggestions instead. */
    fun search(query: String): List<SettingsSearchResult> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val normalizedQuery = tokens.joinToString(" ")

        val strict = allResults.mapNotNull { result ->
            val score = score(result, tokens, normalizedQuery)
            if (score == 0) null else Scored(result, score)
        }
        if (strict.isNotEmpty()) {
            return strict
                .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.result.title })
                .take(MAX_RESULTS)
                .map { it.result }
        }

        // A one- or two-letter query has no useful typo answer, and every short
        // word would be "one edit" away from something. Only fall back where the
        // reader typed enough for a mistake to be recognisable.
        if (normalizedQuery.length < 4) return emptyList()
        val fuzzy = allResults.mapNotNull { result ->
            val score = fuzzyScore(result, tokens)
            if (score == 0) null else Scored(result.copy(fuzzy = true), score)
        }
        return fuzzy
            .sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.result.title })
            .take(MAX_RESULTS)
            .map { it.result }
    }

    /** The curated empty-query recommendation list, in the order a newcomer needs it. */
    fun suggestions(): List<SettingsSearchResult> {
        val byId = allResults.associateBy { it.id }
        return suggestionIds.mapNotNull { byId[it] }
    }

    /** The chips offered when a query matched nothing. */
    fun emptyStateQueries(): List<String> = listOf("Info page", "Dark mode", "App lock", "Updates")

    /**
     * The character ranges in [text] a search for [query] should highlight.
     *
     * Ranges are merged because two query tokens can land next to each other, and
     * an `AnnotatedString` with two overlapping styles renders the later one over
     * the earlier; a single merged range is the honest visual answer.
     */
    fun highlightRanges(text: String, query: String): List<IntRange> {
        val tokens = tokens(query)
        if (tokens.isEmpty()) return emptyList()
        val lower = text.lowercase(Locale.ROOT)
        val compact = StringBuilder(lower.length)
        val compactToOriginal = ArrayList<Int>(lower.length)
        lower.forEachIndexed { index, character ->
            if (character.isLetterOrDigit()) {
                compact.append(character)
                compactToOriginal.add(index)
            }
        }

        val ranges = mutableListOf<IntRange>()
        tokens.forEach { token ->
            var found = lower.indexOf(token)
            while (found >= 0) {
                ranges += found until found + token.length
                found = lower.indexOf(token, found + token.length)
            }
            // "fullscreen" is one word in the query and two in "Full-screen
            // mode", so the compact pass maps the match back through the letters
            // that survived punctuation removal.
            val compactToken = token.filter { it.isLetterOrDigit() }
            if (compactToken.isNotEmpty() && !lower.contains(token)) {
                var compactFound = compact.indexOf(compactToken)
                while (compactFound >= 0) {
                    val originalStart = compactToOriginal[compactFound]
                    val originalEnd = compactToOriginal[compactFound + compactToken.length - 1]
                    ranges += originalStart..originalEnd
                    compactFound = compact.indexOf(compactToken, compactFound + compactToken.length)
                }
            }
        }
        return mergeRanges(ranges)
    }

    private fun buildIndex(): List<SettingsSearchResult> = buildList {
        SettingsPage.entries.forEach { page ->
            add(
                SettingsSearchResult(
                    id = "page_${page.name.lowercase(Locale.ROOT)}",
                    page = page,
                    title = page.title,
                    subtitle = page.subtitle,
                    eyebrow = "Settings",
                    icon = page.icon,
                    keywords = settingsPageKeywords[page].orEmpty(),
                    priority = 20
                )
            )
        }

        InterfaceSection.entries.forEach { section ->
            add(
                SettingsSearchResult(
                    id = "interface_${section.name.lowercase(Locale.ROOT)}",
                    page = SettingsPage.UI,
                    title = section.title,
                    subtitle = section.subtitle,
                    eyebrow = "Interface",
                    icon = section.icon,
                    keywords = interfaceSectionKeywords[section].orEmpty(),
                    interfaceSection = section,
                    priority = 40
                )
            )
        }

        ThemeSection.entries.forEach { section ->
            add(
                SettingsSearchResult(
                    id = "appearance_${section.name.lowercase(Locale.ROOT)}",
                    page = SettingsPage.APPEARANCE,
                    title = section.title,
                    subtitle = section.subtitle,
                    eyebrow = "Appearance",
                    icon = section.icon,
                    keywords = appearanceSectionKeywords[section].orEmpty(),
                    themeSection = section,
                    priority = 40
                )
            )
        }

        // The pages that became shelves in the same rework that gave this screen
        // nine rows. Their parts are indexed from the enum itself rather than by
        // hand: the row the shelf draws and the result the search returns are one
        // title, so a part cannot be renamed into a dead result.
        addAll(
            sectionResults(
                sections = TypeSection.entries,
                page = SettingsPage.TYPE_AND_DISPLAY,
                eyebrow = "Type & display",
                keywords = typeSectionKeywords,
                idPrefix = "type",
                priority = 40
            )
        )
        addAll(
            sectionResults(
                sections = JokeSection.entries,
                page = SettingsPage.JOKES,
                eyebrow = "Jokes",
                keywords = jokeSectionKeywords,
                idPrefix = "joke_part",
                priority = 40
            )
        )
        addAll(
            sectionResults(
                sections = LibrarySection.entries,
                page = SettingsPage.LIBRARY,
                eyebrow = "Library & dashboard",
                keywords = librarySectionKeywords,
                idPrefix = "library_part",
                priority = 40
            )
        )
        addAll(
            sectionResults(
                sections = AboutSection.entries,
                page = SettingsPage.ABOUT,
                eyebrow = "About & support",
                keywords = aboutSectionKeywords,
                idPrefix = "about_part",
                priority = 40
            )
        )

        add(
            searchResult(
                id = "info_page",
                page = SettingsPage.UI,
                interfaceSection = InterfaceSection.LAYOUT,
                title = "Info page",
                subtitle = "Classic or Nova title-page layout",
                eyebrow = "Interface · App layout",
                icon = AppVectorIcons.SectionAppearance,
                keywords = "title page classic nova info page layout appearance skin",
                priority = 60
            )
        )
        add(
            searchResult(
                id = "bar_lift",
                page = SettingsPage.UI,
                interfaceSection = InterfaceSection.LAYOUT,
                title = "Bottom bar lift",
                subtitle = "Float the main bar above the screen edge",
                eyebrow = "Interface · App layout",
                icon = AppVectorIcons.Tune,
                keywords = "navigation bar float padding lift",
                priority = 20
            )
        )
        add(
            searchResult(
                id = "continue_watching",
                page = SettingsPage.UI,
                interfaceSection = InterfaceSection.SEQUELS,
                title = "Continue Watching rail",
                subtitle = "Show in-progress anime above the sequel feed",
                eyebrow = "Interface · Home sequels",
                icon = AppVectorIcons.Play,
                keywords = "continue watching home rail progress",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "feed_shape",
                page = SettingsPage.UI,
                interfaceSection = InterfaceSection.SEQUELS,
                title = "Feed shape",
                subtitle = "Rows, cards, banners or spotlight on the new Home",
                eyebrow = "Interface · Home sequels",
                icon = AppVectorIcons.HomeTab,
                keywords = "sequel feed home card row banner spotlight",
                priority = 20
            )
        )
        add(
            searchResult(
                id = "grid_and_rows",
                page = SettingsPage.UI,
                interfaceSection = InterfaceSection.LIBRARY,
                title = "Grid and rows",
                subtitle = "Poster grid, cover wall, shelf, list, grouped, table and spotlight",
                eyebrow = "Interface · Library view",
                icon = AppVectorIcons.ViewGrid,
                keywords = "library layout columns density posters spotlight",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "display",
                page = SettingsPage.DEVICE,
                title = "Full-screen mode",
                subtitle = "Hide the status and navigation bars",
                eyebrow = "Device Manager",
                icon = AppVectorIcons.DevicePhone,
                keywords = "immersive display bars edge to edge",
                priority = 10
            )
        )

        add(
            searchResult(
                id = "theme_mode",
                page = SettingsPage.APPEARANCE,
                title = "Theme mode",
                subtitle = "Light, dark, or follow the device",
                eyebrow = "Appearance",
                icon = AppVectorIcons.ThemeDark,
                keywords = "dark mode light system appearance night",
                priority = 60
            )
        )
        add(
            searchResult(
                id = "oled_mode",
                page = SettingsPage.APPEARANCE,
                themeSection = ThemeSection.BACKGROUND,
                title = "OLED mode",
                subtitle = "Pure black backgrounds drain less power",
                eyebrow = "Appearance · Background",
                icon = AppVectorIcons.SectionBackground,
                keywords = "black dark battery amoled true black",
                priority = 30
            )
        )
        add(
            searchResult(
                id = "home_blur",
                page = SettingsPage.APPEARANCE,
                themeSection = ThemeSection.BACKGROUND,
                title = "Home backdrop blur",
                subtitle = "Soften the glow behind the new Home",
                eyebrow = "Appearance · Background",
                icon = AppVectorIcons.SectionBackground,
                keywords = "blur background glow performance",
                priority = 15
            )
        )
        add(
            searchResult(
                id = "canvas_colour",
                page = SettingsPage.APPEARANCE,
                themeSection = ThemeSection.BACKGROUND,
                title = "Canvas colour",
                subtitle = "The background every surface is derived from",
                eyebrow = "Appearance · Background",
                icon = AppVectorIcons.SectionBackground,
                keywords = "background canvas colour color theme",
                priority = 20
            )
        )
        add(
            searchResult(
                id = "anime_colour",
                page = SettingsPage.APPEARANCE,
                themeSection = ThemeSection.ARTWORK,
                title = "Retain anime colour",
                subtitle = "Keep the colour of the title you opened",
                eyebrow = "Appearance · Poster colour",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "retain anime colour color cover tint keep title palette " +
                    "dynamic hue revert duration kept accent",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "info_backdrop",
                page = SettingsPage.APPEARANCE,
                themeSection = ThemeSection.PAGES,
                title = "Info page backdrop",
                subtitle = "Banner, cover or nothing behind a title's cards",
                eyebrow = "Appearance · Anime pages",
                icon = AppVectorIcons.SectionBackground,
                keywords = "backdrop banner cover off info title page blur soft tint " +
                    "artwork key visual background",
                priority = 25
            )
        )

        // No `sectionName`: these two cards are on the page itself now, so the
        // result opens the page and lands on the card instead of drilling into a
        // sub-page that no longer exists.
        add(
            searchResult(
                id = "list_refresh",
                page = SettingsPage.LIBRARY,
                title = "List refresh",
                subtitle = "How often the saved list asks AniList again",
                eyebrow = "Library & dashboard",
                icon = AppVectorIcons.Refresh,
                keywords = "refresh interval sync update cache list stale age always " +
                    "fifteen thirty minutes hourly daily weekly day week manual only " +
                    "data requests slider cadence",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "library_view",
                page = SettingsPage.LIBRARY,
                title = "Library view",
                subtitle = "What the Library reopens with, and forgetting it",
                eyebrow = "Library & dashboard",
                icon = AppVectorIcons.List,
                keywords = "library view remembered filter sort forget reset reopens " +
                    "browse",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "library_layout",
                page = SettingsPage.UI,
                interfaceSection = InterfaceSection.LIBRARY,
                title = "Library layout",
                subtitle = "Poster grid, cover wall, shelf, list, grouped, table and spotlight",
                eyebrow = "Interface · Library view",
                icon = AppVectorIcons.ViewGrid,
                keywords = "grid list table grouped covers shelf columns layout posters spotlight",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "arriving_rows",
                page = SettingsPage.LIBRARY,
                title = "Arriving rows",
                subtitle = "How the sequel feed is presented",
                eyebrow = "Library & dashboard",
                icon = AppVectorIcons.HomeTab,
                keywords = "arriving sequels feed home",
                priority = 15
            )
        )
        add(
            searchResult(
                id = "top_bar",
                sectionName = LibrarySection.NAVIGATION.name,
                page = SettingsPage.LIBRARY,
                title = "Top bar",
                subtitle = "Shortcuts beside Settings and Profile",
                eyebrow = "Library & dashboard",
                icon = AppVectorIcons.Tune,
                keywords = "tier list search shortcut top bar",
                priority = 15
            )
        )
        add(
            searchResult(
                id = "bottom_bar",
                sectionName = LibrarySection.NAVIGATION.name,
                page = SettingsPage.LIBRARY,
                title = "Bottom bar",
                subtitle = "Which pages the bar switches between",
                eyebrow = "Library & dashboard",
                icon = AppVectorIcons.Tune,
                keywords = "tabs navigation search tier list bar",
                priority = 20
            )
        )
        add(
            searchResult(
                id = "search_page",
                sectionName = LibrarySection.NAVIGATION.name,
                page = SettingsPage.LIBRARY,
                title = "Search page",
                subtitle = "What the catalogue search can find",
                eyebrow = "Library & dashboard",
                icon = AppVectorIcons.Search,
                keywords = "anime manga catalogue search results",
                priority = 15
            )
        )

        add(
            searchResult(
                id = "app_lock",
                page = SettingsPage.DEVICE,
                title = "App lock",
                subtitle = "Who can open AniSequel",
                eyebrow = "Device Manager",
                icon = AppVectorIcons.Privacy,
                keywords = "lock pin biometrics security privacy",
                priority = 35
            )
        )
        add(
            searchResult(
                id = "device_management",
                page = SettingsPage.DEVICE,
                title = "Device management",
                subtitle = "What this phone reports, and how hard the app may work",
                eyebrow = "Device Manager",
                icon = AppVectorIcons.DevicePhone,
                keywords = "performance cache frame rate memory diagnostics",
                priority = 15
            )
        )

        add(
            searchResult(
                id = "font",
                sectionName = TypeSection.TEXT.name,
                page = SettingsPage.TYPE_AND_DISPLAY,
                title = "Font",
                subtitle = "The face every screen is drawn in",
                eyebrow = "Type & display · Font & text",
                icon = AppVectorIcons.TextBold,
                keywords = "typeface google fonts download",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "text_size",
                sectionName = TypeSection.TEXT.name,
                page = SettingsPage.TYPE_AND_DISPLAY,
                title = "Text size",
                subtitle = "Scales every text style in the app",
                eyebrow = "Type & display · Font & text",
                icon = AppVectorIcons.TextBold,
                keywords = "font size accessibility large",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "display_size",
                sectionName = TypeSection.SCALE.name,
                page = SettingsPage.TYPE_AND_DISPLAY,
                title = "Display size",
                subtitle = "Automatic or manual app density",
                eyebrow = "Type & display · Display size",
                icon = AppVectorIcons.Tune,
                keywords = "dpi display density automatic manual scale ui size bigger " +
                    "smaller app zoom presets exact slider reset dpi size",
                priority = 35
            )
        )
        add(
            searchResult(
                id = "dpi_mode",
                sectionName = TypeSection.SCALE.name,
                page = SettingsPage.TYPE_AND_DISPLAY,
                title = "Automatic density",
                subtitle = "Follow the density your phone or tablet reports",
                eyebrow = "Type & display · Display size · Advanced",
                icon = AppVectorIcons.DevicePhone,
                keywords = "dpi automatic device density panel tablet foldable default " +
                    "follow phone screen wm density scale mode",
                priority = 30
            )
        )
        add(
            searchResult(
                id = "long_titles",
                sectionName = TypeSection.TEXT.name,
                page = SettingsPage.TYPE_AND_DISPLAY,
                title = "Long titles",
                subtitle = "Scroll a name that does not fit instead of cutting it",
                eyebrow = "Type & display · Font & text",
                icon = AppVectorIcons.TextBold,
                keywords = "marquee scroll title ellipsis",
                priority = 10
            )
        )

        // Three controls that are buttons rather than settings, and that a reader
        // looks for by name: "reset" and "preview" are things they want to *do*.
        // None of them carries a section, because none of them lives inside a part -
        // each sits on the page's own shelf, so opening the shelf puts the reader
        // next to the button instead of one level past it.
        add(
            searchResult(
                id = "reset_appearance",
                page = SettingsPage.APPEARANCE,
                title = "Reset appearance",
                subtitle = "Undo the colours and artwork choices you have changed",
                eyebrow = "Appearance",
                icon = AppVectorIcons.Refresh,
                keywords = "reset restore default appearance theme undo start over",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "reset_card_design",
                page = SettingsPage.UI,
                title = "Reset card design",
                subtitle = "Frame, type sizes and poster source, back to the default",
                eyebrow = "Interface · Cards",
                icon = AppVectorIcons.Refresh,
                keywords = "reset restore default card design shape text poster undo",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "joke_preview",
                page = SettingsPage.JOKES,
                title = "Preview a joke",
                subtitle = "See the toast with your own name, edge and line style",
                eyebrow = "Jokes",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "preview test try sample joke toast ani chan edge dwell",
                priority = 10
            )
        )

        add(
            searchResult(
                id = "ani_chan",
                sectionName = JokeSection.PERSONA.name,
                page = SettingsPage.JOKES,
                title = "Ani-chan",
                subtitle = "A line about anime, now and then",
                eyebrow = "Jokes",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "jokes pill anime chan",
                priority = 15
            )
        )
        add(
            searchResult(
                id = "joke_style",
                sectionName = JokeSection.TEXT_EFFECT.name,
                page = SettingsPage.JOKES,
                title = "Line style",
                subtitle = "How a joke arrives, and a sample to judge it by",
                eyebrow = "Jokes",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "compact pill speaker caption style",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "joke_pace",
                sectionName = JokeSection.PACE.name,
                page = SettingsPage.JOKES,
                title = "Pace and dwell",
                subtitle = "How often she speaks, and how long a line stays",
                eyebrow = "Jokes",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "frequency timer duration jokes",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "joke_placement",
                sectionName = JokeSection.PLACEMENT.name,
                page = SettingsPage.JOKES,
                title = "Where she appears",
                subtitle = "The edge of the screen, the pages, and the arrival tick",
                eyebrow = "Jokes",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "placement position pages vibration top bottom edge scope " +
                    "everywhere main pages where she appears",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "joke_topics",
                sectionName = JokeSection.TONES.name,
                page = SettingsPage.JOKES,
                title = "What she talks about",
                subtitle = "Switch off the kinds of line you would rather not read",
                eyebrow = "Jokes",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "joke topics rude profanity wholesome",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "joke_when",
                sectionName = JokeSection.MOMENTS.name,
                page = SettingsPage.JOKES,
                title = "When she appears",
                subtitle = "The timer is the baseline; these are extra invitations",
                eyebrow = "Jokes",
                icon = AppVectorIcons.AnimeSparkle,
                keywords = "joke trigger timer page arrival",
                priority = 5
            )
        )

        add(
            searchResult(
                id = "seasonal_effect",
                page = SettingsPage.SEASONAL,
                title = "Seasonal effect",
                subtitle = "Snow, petals and leaves over every page",
                eyebrow = "Seasonal effects",
                icon = AppVectorIcons.Seasonal,
                keywords = "snow petals leaves seasonal sky animation",
                priority = 25
            )
        )
        add(
            searchResult(
                id = "seasonal_shape",
                page = SettingsPage.SEASONAL,
                title = "Season and shape",
                subtitle = "Petals, leaves and snow, grouped by season",
                eyebrow = "Seasonal effects",
                icon = AppVectorIcons.Seasonal,
                keywords = "seasonal shape sprite particle",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "seasonal_wind",
                page = SettingsPage.SEASONAL,
                title = "Settling and wind",
                subtitle = "Let it gather, and let it move when you do",
                eyebrow = "Seasonal effects",
                icon = AppVectorIcons.Seasonal,
                keywords = "wind settle drift touch scroll",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "seasonal_density",
                page = SettingsPage.SEASONAL,
                title = "Density and size",
                subtitle = "How many, how big, and how fast the sky moves",
                eyebrow = "Seasonal effects",
                icon = AppVectorIcons.Seasonal,
                keywords = "density size speed particle performance",
                priority = 10
            )
        )

        add(
            searchResult(
                id = "anilist_client",
                page = SettingsPage.ACCOUNT,
                title = "AniList Client ID",
                subtitle = "The app identity AniList signs you in with",
                eyebrow = "AniList account",
                icon = AppVectorIcons.SectionAniList,
                keywords = "login sign in oauth client id account",
                priority = 30
            )
        )
        add(
            searchResult(
                id = "demo_mode",
                page = SettingsPage.ACCOUNT,
                title = "Demo mode",
                subtitle = "Read-only until you sign in",
                eyebrow = "AniList account",
                icon = AppVectorIcons.SectionAniList,
                keywords = "demo offline sign out account",
                priority = 15
            )
        )
        add(
            searchResult(
                id = "your_list",
                page = SettingsPage.ACCOUNT,
                title = "Your list",
                subtitle = "A snapshot of the current scan",
                eyebrow = "AniList account",
                icon = AppVectorIcons.Library,
                keywords = "list count entries watch progress",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "comments_attachments",
                page = SettingsPage.ACCOUNT,
                title = "Comments and attachments",
                subtitle = "Threads, uploads and the GIF search",
                eyebrow = "AniList account",
                icon = AppVectorIcons.ProfileActivity,
                keywords = "comments threads upload gif chat",
                priority = 10
            )
        )

        add(
            searchResult(
                id = "whats_new",
                page = SettingsPage.UPDATES,
                title = "What's new",
                subtitle = "Release notes for the build you are running",
                eyebrow = "Updates",
                icon = AppVectorIcons.SystemUpdate,
                keywords = "changelog release notes version update",
                priority = 45
            )
        )
        add(
            searchResult(
                id = "check_updates",
                page = SettingsPage.UPDATES,
                title = "Check for updates",
                subtitle = "Ask GitHub for a newer build",
                eyebrow = "Updates",
                icon = AppVectorIcons.SystemUpdate,
                keywords = "update download apk github version",
                priority = 35
            )
        )
        add(
            searchResult(
                id = "privacy",
                sectionName = AboutSection.PRIVACY.name,
                page = SettingsPage.ABOUT,
                title = "Privacy",
                subtitle = "What the app does and does not collect",
                eyebrow = "About & support",
                icon = AppVectorIcons.SectionAbout,
                keywords = "privacy policy data tracking",
                priority = 10
            )
        )
        add(
            searchResult(
                id = "links",
                sectionName = AboutSection.LINKS.name,
                page = SettingsPage.ABOUT,
                title = "Repository and issue tracker",
                subtitle = "Where the source lives and where bugs go",
                eyebrow = "About & support",
                icon = AppVectorIcons.SectionAbout,
                keywords = "github link issue bug report support",
                priority = 10
            )
        )
    }

    /**
     * One result per shelf part, taken from the enum the shelf itself draws.
     *
     * The alternative - a hand-written result per part - is what the rest of this
     * index does, and it is why renaming a row used to leave a stale answer behind.
     * A part can only be renamed in one place now.
     */
    private fun <T> sectionResults(
        sections: List<T>,
        page: SettingsPage,
        eyebrow: String,
        keywords: Map<T, String>,
        idPrefix: String,
        priority: Int
    ): List<SettingsSearchResult> where T : Enum<T>, T : SettingsShelfSection = sections.map { section ->
        SettingsSearchResult(
            id = "${idPrefix}_${section.name.lowercase(Locale.ROOT)}",
            page = page,
            title = section.title,
            subtitle = section.subtitle,
            eyebrow = eyebrow,
            icon = section.icon,
            keywords = keywords[section].orEmpty(),
            sectionName = section.name,
            priority = priority
        )
    }

    private fun searchResult(
        id: String,
        page: SettingsPage,
        title: String,
        subtitle: String,
        eyebrow: String,
        icon: ImageVector,
        keywords: String,
        priority: Int,
        interfaceSection: InterfaceSection? = null,
        themeSection: ThemeSection? = null,
        sectionName: String? = null
    ): SettingsSearchResult = SettingsSearchResult(
        id = id,
        page = page,
        title = title,
        subtitle = subtitle,
        eyebrow = eyebrow,
        icon = icon,
        keywords = keywords,
        interfaceSection = interfaceSection,
        themeSection = themeSection,
        sectionName = sectionName,
        priority = priority
    )

}
