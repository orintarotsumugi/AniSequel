package com.anisequel.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.BuildConfig
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.DevicePreferences
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.LibraryPreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.viewmodel.AuthViewModel
import com.anisequel.ui.viewmodel.rememberApiStatusProbe
/** The key the [rememberSaveableStateHolder] files the shelf under. */
private const val HUB_SAVEABLE_KEY = "settings_hub"

/**
 * The motion of going one level deeper, and of coming back out.
 *
 * One definition rather than one per level: the Interface page opens its own
 * sub-pages (`InterfaceTab`), and a page that pushed sideways into a part which
 * then faded in place would read as two different navigations on the way in and
 * back out of the same settings screen.
 *
 * Branched on direction rather than by negating one pair: a push travels sideways, a
 * pop drops away. The old single pair made Back a second push that happened to run
 * the other way, which is not what the gesture means.
 */
internal fun AnimatedContentTransitionScope<*>.settingsLevelTransition(): ContentTransform {
    val opening = targetState != null
    return if (opening) {
        val enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            slideInHorizontally(
                animationSpec = ExpressiveMotion.FastSpatialOffset
            ) { width -> width / 4 } +
            scaleIn(
                initialScale = 0.98f,
                animationSpec = ExpressiveMotion.FastSpatial
            )
        val exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            slideOutHorizontally(
                animationSpec = ExpressiveMotion.FastSpatialOffset
            ) { width -> -width / 5 } +
            scaleOut(
                targetScale = 0.99f,
                animationSpec = ExpressiveMotion.FastSpatial
            )
        (enter togetherWith exit) using null
    } else {
        // Back mirrors the push: the shelf returns from the left while the current
        // page leaves to the right, so depth and direction stay clear. Height snaps
        // (`using null`) because a page settling its scroll while the container
        // animated its height caused crashes in the past.
        val enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            slideInHorizontally(
                animationSpec = ExpressiveMotion.FastSpatialOffset
            ) { width -> -width / 5 } +
            scaleIn(
                initialScale = 0.99f,
                animationSpec = ExpressiveMotion.FastSpatial
            )
        val exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            slideOutHorizontally(
                animationSpec = ExpressiveMotion.FastSpatialOffset
            ) { width -> width / 4 } +
            scaleOut(
                targetScale = 0.98f,
                animationSpec = ExpressiveMotion.FastSpatial
            )
        (enter togetherWith exit) using null
    }
}

/**
 * The pages behind the Settings hub.
 *
 * They are an enum rather than a nested navigation graph because they are not
 * destinations a link or a back stack should be able to reach on their own: the
 * hub is the only entrance, and `BackHandler` stepping out of a page is the whole
 * navigation contract.
 */
internal enum class SettingsPage(
    val title: String,
    val subtitle: String,
    /** The tile the shelf draws, reused by the search result for this page. */
    val icon: ImageVector,
    /**
     * Whether the page draws its own header, including any header for a sub-screen.
     *
     * Theme and Interface are shelves with a level below them. Drawing this screen's
     * header *around* them meant a reader two levels in saw three titles stacked - the
     * page, the part, the part's part - with the content pushed under the third. Those
     * two pages now own the whole screen (`Column` + one header), and Back still steps
     * out one level at a time because their handlers are registered after this one.
     */
    val ownsHeader: Boolean = false
) {
    APPEARANCE(
        "Appearance",
        "Colours, cover-based themes and screen backgrounds",
        AppVectorIcons.SectionAppearance,
        ownsHeader = true
    ),
    // How the app is laid out, kept apart from what colour it is: the card style
    // changes the shape of every grid in the app, and a reader choosing it wants a
    // preview of the card, not a palette.
    // Its parts each have a sub-page of their own - see [InterfaceTab].
    UI("Interface", "Home, library, navigation, card layouts and gestures", AppVectorIcons.ViewGrid, ownsHeader = true),
    // Its own page rather than rows on Appearance: font, size and title motion
    // change how reading feels on every screen, and each one needs a sample to be
    // judged by - which is a page, not a switch.
    TYPE(
        "Type & reading",
        "Fonts, saved typefaces, text size and title behaviour",
        AppVectorIcons.TextBold
    ),
    JOKES("Jokes", "Ani-chan personality, jokes, triggers and animations", AppVectorIcons.AnimeSparkle),
    // The one page in Settings that draws something rather than switching it: the
    // shape, density, size and speed are only judgeable next to the sky they
    // produce, so it is a page with a preview and not four rows on Appearance.
    SEASONAL(
        "Seasonal effects",
        "Seasonal particles, their appearance and performance limits",
        AppVectorIcons.Seasonal
    ),
    LIBRARY(
        "Library & dashboard",
        "AniList refresh timing, dashboard sections and navigation bar",
        AppVectorIcons.Library
    ),
    // The four device-shaped choices were in four different pages because each was
    // written beside the feature that first needed it - the frame-rate ceiling under
    // the sky, full screen under the theme, the app lock under the session, the hold
    // duration under the cards it acts on. Asked together, they are one question.
    DEVICE(
        "Device Manager",
        "Display, app lock, performance limits and cover storage",
        AppVectorIcons.DevicePhone
    ),
    ACCOUNT("AniList account", "Connect AniList, manage the session and sign out", AppVectorIcons.SectionAniList),
    // Updates is its own page rather than the top of About: the update check and
    // the release notes are the two cards a reader comes back to, and they were
    // sharing a scroll with the crash report and the privacy policy.
    UPDATES(
        "Updates",
        "Installed version, app updates and diagnostics",
        AppVectorIcons.SystemUpdate
    ),
    // "and links" is not padding: the page carries the repository and the issue
    // tracker, which is where a bug report actually goes. A subtitle that stopped
    // at "privacy" would describe three of its four cards.
    ABOUT("About & support", "App details, privacy information and support links", AppVectorIcons.SectionAbout)
}

/**
 * Settings, as a shelf of intentions instead of three filing tabs.
 *
 * ## What was wrong with the tabs
 *
 * "Info", "Theme" and "Edit" named the files the screen was built from, not the
 * things a reader wanted to change. The third tab carried refresh rate, quick
 * add, app lock, the Client ID and sign-out because they had nowhere else to go,
 * and the first one carried the profile, the diagnostic, the changelog and the
 * updater for the same reason. A tab that means "everything else" is a tab that
 * gets read top to bottom every time.
 *
 * The hub gives every intention a named row with a sentence under it, and the
 * detail a page of its own. The tab bodies were not thrown away - Theme and the
 * account, library and info pages were already good cards; they are now reached
 * from a row instead of a bar, and the old Edit tab was split by subject.
 *
 * ## Why a page slides sideways, and why the height still snaps
 *
 * The pages are scrollable columns, and the previous tab implementation carried
 * a comment about a crash from the default `SizeTransform` animating the
 * container's height while the outgoing page settled its scroll. That reasoning
 * still holds: the height snaps and only the draw-phase motion stays.
 *
 * The motion is a short horizontal push instead of a scale. A page that opens is
 * a level deeper, and a scale says "nearer" about both a page and its backdrop;
 * a push says which way the stack goes, so the back arrow and the animation now
 * tell the same story.
 *
 * Going back is deliberately *not* that push reversed. A pop drops the page off
 * the bottom edge as it fades while the shelf returns in place: the page is a
 * sheet being lifted away, not a second card sliding in from the opposite side.
 * Reversing the horizontal pair made Back look like another push that happened
 * to be going the other way, which is the one thing a back gesture must never
 * look like. The shelf still draws its own hover-free surface underneath, so
 * there is never a blank frame between the two.
 *
 * Both halves read the shared motion tokens, so the drop becomes `snap()` under
 * `MotionStyle.INSTANT` and loses its scale under `SMOOTH` without this file
 * needing a second switch.
 */
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    jokePreferences: JokePreferences,
    appLockPreferences: AppLockPreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    quickAddPreferences: QuickAddPreferences,
    /**
     * The Library page's remembered type, filter and sort.
     *
     * Read here only to offer the one action the Library cannot: forgetting them.
     * The Library writes these itself through the same instance, so this is one
     * DataStore reader per process rather than a second opinion about what was
     * last chosen.
     */
    libraryPreferences: LibraryPreferences,
    /**
     * Which pages the bottom bar offers.
     *
     * Settings owns the switch and the host owns the bar, so the same instance is
     * handed to both - two readers of one DataStore, so the switch cannot describe
     * a bar the app is not drawing.
     */
    mainTabPreferences: MainTabPreferences,
    changelogRepository: ChangelogRepository,
    /**
     * The seasonal overlay's shape, density, size, speed, settling and wind.
     *
     * The same instance the app root hands to `SeasonalParticleHost`, so the
     * preview on this page and the sky over the app cannot describe two different
     * settings.
     */
    seasonalPreferences: SeasonalPreferences,
    /**
     * The cover-cache ceiling the Device Manager's card reads and writes. See
     * [DeviceManagerTab].
     */
    devicePreferences: DevicePreferences,
    /**
     * The card style the Interface page writes. The same instance the app root
     * collects and hands to every card, so the picker cannot describe a layout the
     * Library grid is not drawing. See [InterfaceTab].
     */
    interfacePreferences: InterfacePreferences,
    /**
     * The repository's reachability probe, passed as a function so Settings does
     * not need the whole repository to ask one question.
     *
     * A default that reports UNKNOWN keeps every preview and every call site
     * that has no network - the demo route, screenshot tests - compiling and
     * honest rather than pretending the API is up.
     */
    checkApiReachability: suspend () -> ApiStatusSnapshot = {
        ApiStatusSnapshot(ApiReachability.UNKNOWN)
    },
    viewer: ViewerProfile?,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    totalWatchedCount: Int? = null,
    totalMissedCount: Int? = null,
    modifier: Modifier = Modifier,
    /**
     * Up to three of the reader's entries for the Interface page's card preview.
     * Empty is honest: demo mode has no list, and Settings can be opened before
     * Home's first load has answered. The preview falls back to its sample titles.
     */
    cardPreviewEntries: List<MediaListEntryItem> = emptyList()
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var showConnectionCheck by rememberSaveable { mutableStateOf(false) }
    // Search is a mode of the shelf, not a destination of its own: the field sits
    // above the shelf, a non-empty query replaces the shelf with results, and Back
    // clears the query before it closes Settings. Keeping the query in this screen
    // rather than in `SettingsRoot` also lets it survive opening a result and
    // coming back to the same answer list.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // A search result can point one level below a page. The target is handed to the
    // page's own shelf once, then consumed; without clearing it, reopening
    // Interface from the normal row would jump to the last searched part again.
    var interfaceTarget by rememberSaveable { mutableStateOf<InterfaceSection?>(null) }
    var themeTarget by rememberSaveable { mutableStateOf<ThemeSection?>(null) }
    // One probe, for the one place that checks: the shelf's "AniList connection"
    // row and the dialog it opens share it, so a Check tap cannot disagree with
    // the row that opened it. The About page used to raise a second, independent
    // probe for its own check button; that card is gone because the row here
    // already opens the same dialog.
    val apiProbe = rememberApiStatusProbe(checkApiReachability)

    // The shelf and the pages are separate compositions, and `AnimatedContent`
    // disposes the outgoing one when its slide finishes - so `rememberScrollState`
    // inside a page started at the top every time the reader came back to it, and
    // the shelf did too. The holder keys each destination so scroll positions and
    // page-local drafts survive the trip out and back, the same way the profile
    // tabs keep theirs.
    val pageStateHolder = rememberSaveableStateHolder()

    BackHandler {
        when {
            page != null -> {
                page = null
                interfaceTarget = null
                themeTarget = null
            }
            searchQuery.isNotEmpty() -> searchQuery = ""
            else -> onNavigateBack()
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("settings_screen"),
        containerColor = MaterialTheme.colorScheme.surface
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedContent(
                targetState = page,
                transitionSpec = { settingsLevelTransition() },
                label = "settings_page_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_tab_content")
            ) { destination ->
                pageStateHolder.SaveableStateProvider(
                    key = destination?.name ?: HUB_SAVEABLE_KEY
                ) {
                    if (destination == null) {
                        SettingsRoot(
                            viewer = viewer,
                            onClose = onNavigateBack,
                            onOpen = {
                                interfaceTarget = null
                                themeTarget = null
                                page = it
                            },
                            onOpenSearchResult = { result ->
                                interfaceTarget = result.interfaceSection
                                themeTarget = result.themeSection
                                page = result.page
                            },
                            onCheckConnection = { showConnectionCheck = true },
                            searchQuery = searchQuery,
                            onSearchQueryChange = { searchQuery = it }
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // A page that owns a nested stack draws the header for
                            // whatever level is open; drawing one here as well was
                            // the stacking: "Interface" above "Card design" above
                            // "Card shape", three rows of title before a control.
                            if (!destination.ownsHeader) {
                                SettingsSubHeader(
                                    title = destination.title,
                                    subtitle = destination.subtitle,
                                    onBack = {
                                        page = null
                                        interfaceTarget = null
                                        themeTarget = null
                                    }
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            ) {
                                SettingsPageContent(
                                    page = destination,
                                    authViewModel = authViewModel,
                                    themePreferences = themePreferences,
                                    jokePreferences = jokePreferences,
                                    appLockPreferences = appLockPreferences,
                                    refreshIntervalPreferences = refreshIntervalPreferences,
                                    arrivingPreferences = arrivingPreferences,
                                    quickAddPreferences = quickAddPreferences,
                                    libraryPreferences = libraryPreferences,
                                    mainTabPreferences = mainTabPreferences,
                                    changelogRepository = changelogRepository,
                                    seasonalPreferences = seasonalPreferences,
                                    devicePreferences = devicePreferences,
                                    interfacePreferences = interfacePreferences,
                                    viewer = viewer,
                                    onNavigateToLogin = onNavigateToLogin,
                                    totalWatchedCount = totalWatchedCount,
                                    totalMissedCount = totalMissedCount,
                                    cardPreviewEntries = cardPreviewEntries,
                                    interfaceTarget = interfaceTarget,
                                    onInterfaceTargetConsumed = { interfaceTarget = null },
                                    themeTarget = themeTarget,
                                    onThemeTargetConsumed = { themeTarget = null },
                                    onClosePage = {
                                        page = null
                                        interfaceTarget = null
                                        themeTarget = null
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (showConnectionCheck) {
                ConnectionCheckDialog(
                    probe = apiProbe,
                    onDismiss = { showConnectionCheck = false }
                )
            }
        }
    }
}

/**
 * The shelf.
 *
 * The sections are the reader's questions in order: how it looks, how it behaves,
 * who it is signed in as, whether the network is awake, and what changed. Rows in
 * one subject share a card and are separated by a hairline, so the gap between two
 * cards means "different subject" rather than "next row" - see `SettingsHubGroup`.
 */
@Composable
private fun SettingsRoot(
    viewer: ViewerProfile?,
    onClose: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onOpenSearchResult: (SettingsSearchResult) -> Unit,
    onCheckConnection: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxSettingsContentWidth)
                .fillMaxWidth()
        ) {
            SettingsHubHeader(onClose = onClose)
            SettingsSearchBar(
                query = searchQuery,
                onQueryChange = onSearchQueryChange,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
            )

            if (searchQuery.isNotBlank()) {
                SettingsSearchResults(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    onOpen = onOpenSearchResult,
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                // The account leads now. It is the one row whose answer changes what
                // every page below it is looking at - demo list or real AniList list -
                // and it used to sit fourth, under two sections a signed-out reader
                // would walk past on the way to "am I signed in?".
                SettingsSectionLabel("AniList")
                SettingsHubGroup(
                    entries = listOf(
                        SettingsPage.ACCOUNT.entry(
                            "settings_row_account",
                            onOpen,
                            // The one live fact on the shelf: whether this session is a
                            // real AniList account or the built-in demo list. Everything
                            // else the row could say is already on the page it opens.
                            subtitle = viewer?.let { "Signed in as ${it.name}" }
                                ?: "Not signed in - browsing the demo list"
                        ),
                        // `SignalOn`, not `BrokenHeart`: this row asks a question. A
                        // broken heart beside "connection" claims the answer before the
                        // check has run, on a screen where the answer is usually fine.
                        SettingsHubEntry(
                            icon = AppVectorIcons.SignalOn,
                            title = "Connection check",
                            subtitle = "Ask AniList whether it is reachable",
                            onClick = onCheckConnection,
                            testTag = "settings_row_connection"
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                SettingsSectionLabel("Personalisation")
                SettingsHubGroup(
                    entries = listOf(
                        SettingsPage.APPEARANCE.entry(
                            "settings_row_appearance",
                            onOpen
                        ),
                        SettingsPage.UI.entry(
                            "settings_row_ui",
                            onOpen
                        ),
                        SettingsPage.TYPE.entry(
                            "settings_row_type",
                            onOpen
                        ),
                        SettingsPage.JOKES.entry(
                            "settings_row_jokes",
                            onOpen
                        ),
                        SettingsPage.SEASONAL.entry(
                            "settings_row_seasonal",
                            onOpen
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                SettingsSectionLabel("Library & dashboard")
                SettingsHubGroup(
                    entries = listOf(
                        SettingsPage.LIBRARY.entry(
                            "settings_row_library",
                            onOpen
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                SettingsSectionLabel("This device")
                SettingsHubGroup(
                    entries = listOf(
                        SettingsPage.DEVICE.entry(
                            "settings_row_device",
                            onOpen
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                // The connection check used to sit under a "Network" label of its own,
                // which is a whole section heading for one row - and a heading that
                // names the transport rather than the thing being checked. It lives
                // with the account row at the top, because it is the same AniList.
                SettingsSectionLabel("About")
                SettingsHubGroup(
                    entries = listOf(
                        SettingsPage.UPDATES.entry(
                            "settings_row_updates",
                            onOpen
                        ),
                        SettingsPage.ABOUT.entry(
                            "settings_row_about",
                            onOpen
                        )
                    ),
                    modifier = Modifier.padding(horizontal = 20.dp)
                )

                Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                    SettingsVersionFooter(
                        versionName = BuildConfig.VERSION_NAME,
                        versionCode = BuildConfig.VERSION_CODE
                    )
                }
            }
        }
    }
}

/**
 * The shelf row for this page.
 *
 * The row and the page header used to spell the title and the subtitle out twice,
 * and they had already drifted: the Jokes row promised "timing and placement"
 * while the page it opened said "timing, tone and saved lines". A row that
 * describes a different page than the one it opens is a small lie on the way in.
 * Deriving both from the enum makes the drift impossible rather than unlikely.
 *
 * An entry rather than a composable, because a group has to know how many rows it
 * holds before it draws them: the divider goes *between* them, and a lambda per row
 * cannot tell the group where the last one is.
 *
 * The one exception is [subtitle], used only by the account row to state who is
 * signed in. That is not a second description of the page - it is a fact about
 * the session that changes while the page itself does not, and the shelf is
 * where a reader glances to ask whether they are signed in at all.
 */
private fun SettingsPage.entry(
    testTag: String,
    onOpen: (SettingsPage) -> Unit,
    subtitle: String? = null
): SettingsHubEntry = SettingsHubEntry(
    icon = this.icon,
    title = title,
    subtitle = subtitle ?: this.subtitle,
    testTag = testTag,
    onClick = { onOpen(this) }
)

/** The body of a hub page, with every control the old tabs had. */
@Composable
private fun SettingsPageContent(
    page: SettingsPage,
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    jokePreferences: JokePreferences,
    appLockPreferences: AppLockPreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    quickAddPreferences: QuickAddPreferences,
    libraryPreferences: LibraryPreferences,
    mainTabPreferences: MainTabPreferences,
    changelogRepository: ChangelogRepository,
    seasonalPreferences: SeasonalPreferences,
    devicePreferences: DevicePreferences,
    interfacePreferences: InterfacePreferences,
    viewer: ViewerProfile?,
    onNavigateToLogin: () -> Unit,
    totalWatchedCount: Int?,
    totalMissedCount: Int?,
    cardPreviewEntries: List<MediaListEntryItem>,
    /** An Interface part a search result asked for, consumed once by that page. */
    interfaceTarget: InterfaceSection?,
    onInterfaceTargetConsumed: () -> Unit,
    /** An Appearance part a search result asked for, consumed once by that page. */
    themeTarget: ThemeSection?,
    onThemeTargetConsumed: () -> Unit,
    /** Leaves the open page for the Settings shelf; used by the pages that own theirs. */
    onClosePage: () -> Unit
) {
    when (page) {
        SettingsPage.APPEARANCE -> ThemeTab(
            themePreferences = themePreferences,
            pageTitle = page.title,
            pageSubtitle = page.subtitle,
            onClosePage = onClosePage,
            initialSection = themeTarget,
            onInitialSectionConsumed = onThemeTargetConsumed
        )
        SettingsPage.UI -> InterfaceTab(
            themePreferences = themePreferences,
            interfacePreferences = interfacePreferences,
            quickAddPreferences = quickAddPreferences,
            pageTitle = page.title,
            pageSubtitle = page.subtitle,
            onClosePage = onClosePage,
            previewEntries = cardPreviewEntries,
            mainTabPreferences = mainTabPreferences,
            initialSection = interfaceTarget,
            onInitialSectionConsumed = onInterfaceTargetConsumed
        )
        SettingsPage.TYPE -> TypeSettingsTab(
            themePreferences = themePreferences,
            interfacePreferences = interfacePreferences
        )
        SettingsPage.JOKES -> JokeSettingsTab(jokePreferences = jokePreferences)
        SettingsPage.SEASONAL -> SeasonalSettingsTab(
            seasonalPreferences = seasonalPreferences
        )
        SettingsPage.LIBRARY -> LibrarySettingsTab(
            refreshIntervalPreferences = refreshIntervalPreferences,
            arrivingPreferences = arrivingPreferences,
            libraryPreferences = libraryPreferences,
            mainTabPreferences = mainTabPreferences
        )
        SettingsPage.DEVICE -> DeviceManagerTab(
            seasonalPreferences = seasonalPreferences,
            devicePreferences = devicePreferences,
            themePreferences = themePreferences,
            appLockPreferences = appLockPreferences
        )
        SettingsPage.ACCOUNT -> EditSettingsTab(
            authViewModel = authViewModel,
            onNavigateToLogin = onNavigateToLogin,
            viewer = viewer,
            totalWatchedCount = totalWatchedCount,
            totalMissedCount = totalMissedCount
        )
        SettingsPage.UPDATES -> UpdatesSettingsTab(changelogRepository = changelogRepository)
        SettingsPage.ABOUT -> InfoSettingsTab()
    }
}
