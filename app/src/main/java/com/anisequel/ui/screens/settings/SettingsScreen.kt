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
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.viewmodel.AuthViewModel
import com.anisequel.ui.viewmodel.rememberApiStatusProbe
import kotlin.enums.enumEntries
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
    TYPE_AND_DISPLAY(
        "Type & display",
        "Fonts, saved typefaces, text size and how large the app draws",
        AppVectorIcons.TextBold,
        ownsHeader = true
    ),
    JOKES(
        "Jokes",
        "Ani-chan's lines, timing, topics and where she appears",
        AppVectorIcons.AnimeSparkle,
        ownsHeader = true
    ),
    // The one page in Settings that draws something rather than switching it: the
    // shape, density, size and speed are only judgeable next to the sky they
    // produce, so it is a page with a preview and not four rows on Appearance.
    //
    // It stays one scroll rather than becoming a shelf for the same reason: its
    // sliders and its preview are one control. Moving the sliders a level down
    // would leave a preview with nothing to follow and sliders with nothing to
    // show - the failure its whole design exists to avoid.
    SEASONAL(
        "Seasonal effects",
        "Seasonal particles, their appearance and performance limits",
        AppVectorIcons.Seasonal
    ),
    LIBRARY(
        "Library & dashboard",
        "Navigation bars, the library view and how fresh the list is",
        AppVectorIcons.Library,
        ownsHeader = true
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
    NOTIFICATIONS(
        "Notifications",
        "Inbox read tracking and which activity, media and submission events appear",
        AppVectorIcons.MessageReceived
    ),
    ACCOUNT("AniList account", "Connect AniList, manage the session and sign out", AppVectorIcons.SectionAniList),
    // Updates is its own page rather than the top of About: the update check and
    // the release notes are the two cards a reader comes back to, and they were
    // sharing a scroll with the crash report and the privacy policy. Diagnostics
    // live on About; this page is the check and the notes.
    UPDATES(
        "Updates",
        "App updates and release notes",
        AppVectorIcons.SystemUpdate
    ),
    // "and links" is not padding: the page carries the repository and the issue
    // tracker, which is where a bug report actually goes. A subtitle that stopped
    // at "privacy" would describe three of its four cards.
    ABOUT(
        "About & support",
        "App details, privacy, support links and the people behind it",
        AppVectorIcons.SectionAbout,
        ownsHeader = true
    )
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
    notificationPreferencesStore: NotificationPreferencesStore,
    notificationReadStore: NotificationReadStore,
    /**
     * Opens directly on the Notifications page.
     *
     * A boolean rather than a page enum because [SettingsPage] is internal and this
     * screen is public. It exists for the bell in the app bar, whose one job is to
     * take a reader from the inbox to the switches that shape it in one tap.
     */
    startOnNotifications: Boolean = false,
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
    var page by rememberSaveable {
        mutableStateOf<SettingsPage?>(
            if (startOnNotifications) SettingsPage.NOTIFICATIONS else null
        )
    }
    var showConnectionCheck by rememberSaveable { mutableStateOf(false) }
    // Search is a mode of the shelf, not a destination of its own: the field sits
    // above the shelf, a non-empty query replaces the shelf with results, and Back
    // clears the query before it closes Settings. Keeping the query in this screen
    // rather than in `SettingsRoot` also lets it survive opening a result and
    // coming back to the same answer list.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    // A search result can point one level below a page. One string rather than one
    // typed state per shelf: the target is the section's enum name, resolved by the
    // page that owns that enum. It used to be two typed states (an `InterfaceSection`
    // and a `ThemeSection`) with a third needed for every new shelf - the plumbing,
    // not the feature, was what a new sub-page cost. Handed to the page once, then
    // consumed; without clearing it, reopening Interface from the normal row would
    // jump to the last searched part again.
    var targetSection by rememberSaveable { mutableStateOf<String?>(null) }
    // One probe, for the one place that checks: the account card's connection chip
    // and the dialog it opens share it, so a Check tap cannot disagree with the
    // state that opened it.
    val apiProbe = rememberApiStatusProbe(checkApiReachability)
    val apiUi by apiProbe.ui.collectAsStateWithLifecycle()
    // The row values, read from the same preference instances the pages write
    // through - see `rememberSettingsSummaries` for why they are assembled here and
    // not inside the hub.
    val summaries = rememberSettingsSummaries(
        themePreferences = themePreferences,
        interfacePreferences = interfacePreferences,
        refreshIntervalPreferences = refreshIntervalPreferences,
        jokePreferences = jokePreferences,
        seasonalPreferences = seasonalPreferences,
        appLockPreferences = appLockPreferences,
        notificationPreferencesStore = notificationPreferencesStore
    )
    val connectionLabel = when {
        apiUi.isChecking -> "checking"
        else -> apiUi.reachability.shortLabel()
    }

    // The shelf and the pages are separate compositions, and `AnimatedContent`
    // disposes the outgoing one when its slide finishes - so `rememberScrollState`
    // inside a page started at the top every time the reader came back to it, and
    // the shelf did too. The holder keys each destination so scroll positions and
    // page-local drafts survive the trip out and back, the same way the profile
    // tabs keep theirs.
    val pageStateHolder = rememberSaveableStateHolder()

    BackHandler {
        when {
            // The inbox opens this route directly on Notifications. Back there
            // should return to the inbox, not reveal a Settings hub the reader
            // never visited; a page they navigated to from that hub still steps
            // back to the hub as usual.
            page != null && !(startOnNotifications && page == SettingsPage.NOTIFICATIONS) -> {
                page = null
                targetSection = null
            }
            searchQuery.isNotBlank() -> searchQuery = ""
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
                            watchedCount = totalWatchedCount,
                            missedCount = totalMissedCount,
                            summaries = summaries,
                            connectionLabel = connectionLabel,
                            connectionHealthy = if (apiUi.isChecking) null else apiUi.reachability.health(),
                            onClose = onNavigateBack,
                            onOpen = {
                                targetSection = null
                                page = it
                            },
                            onOpenAccount = {
                                targetSection = null
                                page = SettingsPage.ACCOUNT
                            },
                            onOpenSearchResult = { result ->
                                // The typed interface/theme targets are kept so the
                                // two existing shelves need no change; every newer
                                // shelf arrives as a name and resolves it locally.
                                targetSection = result.interfaceSection?.name
                                    ?: result.themeSection?.name
                                    ?: result.sectionName
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
                                        // The inbox opened this route on
                                        // Notifications directly. Its own
                                        // back button must agree with system
                                        // Back and return there, not reveal
                                        // an unvisited Settings hub.
                                        if (startOnNotifications && destination == SettingsPage.NOTIFICATIONS) {
                                            onNavigateBack()
                                        } else {
                                            page = null
                                            targetSection = null
                                        }
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
                                    notificationPreferencesStore = notificationPreferencesStore,
                                    notificationReadStore = notificationReadStore,
                                    viewer = viewer,
                                    onNavigateToLogin = onNavigateToLogin,
                                    totalWatchedCount = totalWatchedCount,
                                    totalMissedCount = totalMissedCount,
                                    cardPreviewEntries = cardPreviewEntries,
                                    targetSection = targetSection,
                                    onTargetConsumed = { targetSection = null },
                                    onClosePage = {
                                        page = null
                                        targetSection = null
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
 * Three groups of three, with the account on its own card above them.
 *
 * ## Why three groups rather than five
 *
 * The shelf used to carry five labels, and two of them - "Library & dashboard"
 * and "This device" - headed exactly one row. A heading over a single row is a
 * hierarchy level that carries nothing: the reader parses a section with no
 * members, and it spends a label's worth of vertical space (26dp above, 10dp
 * below) to say so. Nine rows divide honestly into the three questions a reader
 * arrives with: what it looks like, what it shows, and what it runs on.
 *
 * ## Why the account is a card and not a row
 *
 * It used to be two rows - the account itself and a Connection check action -
 * and the action was the only non-page on the shelf, which made the screen's
 * contract ("every row opens a page") false. They are one subject, so they are
 * one card: identity, session state, and the connection chip that runs the same
 * check the row used to. See [SettingsAccountCard].
 *
 * ## Why the header and the search field do not scroll
 *
 * Header, field and nine rows is more than a screen, so the field used to leave
 * the screen exactly when a reader had scrolled past the rows it searches. Pinning
 * it costs about 120dp of viewport and keeps the one control that reaches every
 * setting available from every scroll position. Only the list below it scrolls,
 * and it still keeps its position across a page visit - see the
 * `rememberSaveableStateHolder` in `SettingsScreen`.
 *
 * ## Why every row now carries a value
 *
 * The subtitle says what is behind the row; the value says what is set. That is
 * the difference between a table of contents and a control panel, and it is what
 * spares the reader a page visit and a Back to answer "is OLED on?". See
 * [SettingsHubEntry.value] and [SettingsSummaries].
 */
@Composable
private fun SettingsRoot(
    viewer: ViewerProfile?,
    /** The dashboard's already-loaded counts, for the account card's second line. */
    watchedCount: Int?,
    missedCount: Int?,
    summaries: SettingsSummaries,
    connectionLabel: String,
    connectionHealthy: Boolean?,
    onClose: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onOpenAccount: () -> Unit,
    onOpenSearchResult: (SettingsSearchResult) -> Unit,
    onCheckConnection: () -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit
) {
    // The one scroll both modes share. Hoisted out of the `verticalScroll` call so
    // the mode swap below can put the reader back at the top of it: a reader who
    // scrolled to the bottom of the shelf and then typed got results drawn at that
    // offset, which on a short result list means the first answers are above the
    // viewport they were typed into.
    val hubScroll = rememberScrollState()
    val searching = searchQuery.isNotBlank()
    LaunchedEffect(searching, searchQuery) {
        // Animated, not `scrollTo`, when the mode changes: the list is moving out of
        // the way of the field the reader is typing in, and a spring reads as the
        // shelf getting out of the way rather than as a jump.
        //
        // A refined query is a different list, not a new mode, so it snaps instead.
        // Before this, only `searching` was a key: typing a second word while
        // scrolled through the first answer left the reader at that offset, with the
        // best matches for the new query drawn above the viewport they were typing
        // into - the same failure the mode change already fixed.
        if (hubScroll.value != 0) {
            if (searching) hubScroll.scrollTo(0) else hubScroll.animateScrollTo(0)
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // The pinned half: the screen's name and the field that can reach every
        // setting, available from any scroll position.
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
        }

        // The scrolling half. `weight` rather than `fillMaxSize` because the pinned
        // half above has already taken its height.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(hubScroll),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = MaxSettingsContentWidth)
                    .fillMaxWidth()
            ) {
                // A plain swap, not an `AnimatedContent`.
                //
                // 2.5.4 wrapped this pair in one, and every card on the shelf landed
                // on top of every other card. `AnimatedContent` places each *direct
                // child* of its content lambda at the same origin - its measure policy
                // is a Box with one position, not a Column - and the shelf branch
                // below emits the account card, three labels and three groups as
                // siblings. The last group drew on top, so the shelf showed three
                // rows ("Device Manager", "Updates", "About & support") and the six
                // above them sat underneath, unreadable and untappable. 2.5.3 was
                // fine because the swap was the `if` right here: the crossfade was
                // the regression, not a polish pass with a side effect.
                //
                // Not re-wrapped in a Column so the animation could come back safely.
                // A crossfade was the wrong motion for this in the first place: both
                // branches are lists the reader is about to read, and typing a query
                // replaces one with the other instead of moving between two places.
                if (searching) {
                    SettingsSearchResults(
                        query = searchQuery,
                        onQueryChange = onSearchQueryChange,
                        onOpen = onOpenSearchResult,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    // The account leads. It is the one answer that changes what every
                    // row below it is looking at - a real AniList list or the built-in
                    // demo - and it is now a card rather than a row, because it carries
                    // a second control (the connection check).
                    SettingsAccountCard(
                        name = viewer?.name,
                        // `large` first: the tile is 52dp, and AniList's medium
                        // avatar is the one sized for a 36dp row.
                        avatarUrl = viewer?.avatar?.let { it.large ?: it.medium },
                        subtitle = accountSubtitle(viewer, watchedCount, missedCount),
                        connectionLabel = connectionLabel,
                        connectionHealthy = connectionHealthy,
                        onOpenAccount = onOpenAccount,
                        onCheckConnection = onCheckConnection,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                    )

                    // One group per question, one tonal family per group, and no
                    // heading over a group that holds a single row.
                    SettingsSectionLabel("Look & layout")
                    SettingsHubGroup(
                        entries = listOf(
                            SettingsPage.APPEARANCE.entry(
                                "settings_row_appearance",
                                onOpen,
                                value = summaries.appearance
                            ),
                            SettingsPage.UI.entry(
                                "settings_row_ui",
                                onOpen,
                                value = summaries.interfaceStyle
                            ),
                            SettingsPage.TYPE_AND_DISPLAY.entry(
                                "settings_row_typedisplay",
                                onOpen,
                                value = summaries.type
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    SettingsSectionLabel("What it shows")
                    SettingsHubGroup(
                        entries = listOf(
                            SettingsPage.LIBRARY.entry(
                                "settings_row_library",
                                onOpen,
                                value = summaries.library
                            ),
                            SettingsPage.JOKES.entry(
                                "settings_row_jokes",
                                onOpen,
                                value = summaries.jokes
                            ),
                            SettingsPage.SEASONAL.entry(
                                "settings_row_seasonal",
                                onOpen,
                                value = summaries.seasonal
                            ),
                            SettingsPage.NOTIFICATIONS.entry(
                                "settings_row_notifications",
                                onOpen,
                                value = summaries.notifications
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )

                    SettingsSectionLabel("Device & app")
                    SettingsHubGroup(
                        entries = listOf(
                            SettingsPage.DEVICE.entry(
                                "settings_row_device",
                                onOpen,
                                value = summaries.device
                            ),
                            // No value: this app keeps no *last checked* answer, and a
                            // row that said "Up to date" without having asked would be
                            // the one lie on a screen of facts.
                            SettingsPage.UPDATES.entry(
                                "settings_row_updates",
                                onOpen
                            ),
                            SettingsPage.ABOUT.entry(
                                "settings_row_about",
                                onOpen,
                                value = summaries.about
                            )
                        ),
                        modifier = Modifier.padding(horizontal = 20.dp)
                    )
                }
                // A sibling of the shelf, never its parent. This was the last child
                // of a crossfade branch in 2.5.4, which put it inside a container
                // sized to the *other* mode - and the stamp could be placed over the
                // rows it is meant to sit under, the overlap a screenshot showed as
                // the version printed across "Device Manager". `!searching` keeps it
                // out of a result list.
                if (!searching) {
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
 * [value] is the page's current setting, read at the shelf - not a second
 * description of the page. It is the one thing on the row that changes while the
 * page behind it does not.
 */
private fun SettingsPage.entry(
    testTag: String,
    onOpen: (SettingsPage) -> Unit,
    value: String? = null
): SettingsHubEntry = SettingsHubEntry(
    icon = this.icon,
    title = title,
    subtitle = subtitle,
    testTag = testTag,
    onClick = { onOpen(this) },
    value = value
)

/**
 * The account card's second line.
 *
 * "Your list, session and sign-in" described the *page* the card opens, which the
 * chevron already promises. The counts describe the list the card is about, and
 * they are the numbers the dashboard has already loaded - a reader who came to
 * Settings to check "is this my account" gets an answer with evidence. Both are
 * absent until the list's first load answers, and an absent count draws no claim
 * rather than a zero.
 */
private fun accountSubtitle(viewer: ViewerProfile?, watched: Int?, missed: Int?): String {
    if (viewer == null) return "Browsing the demo list - connect AniList to see your own"
    val watchedLine = watched?.let { count ->
        if (count == 1) "1 title in your list" else "$count titles in your list"
    }
    val missedLine = missed?.takeIf { it > 0 }?.let { count ->
        if (count == 1) "1 sequel waiting" else "$count sequels waiting"
    }
    return listOfNotNull(watchedLine, missedLine).joinToString(" · ")
        .ifEmpty { "Your list, session and sign-in" }
}

/**
 * Resolves a search result's section name against the enum that owns it.
 *
 * The name rather than the enum instance so one `rememberSaveable` state can
 * carry a target for every shelf in Settings: an enum is not `Parcelable`, and one
 * typed state per shelf is the plumbing this replaces. An unknown name (a result
 * from a build that had a section this one dropped) resolves to null, which opens
 * the page at its shelf instead of crashing - the same honesty `SettingsSearch`
 * gives a result it cannot place.
 */
private inline fun <reified T : Enum<T>> String?.toSection(): T? =
    this?.let { name -> enumEntries<T>().firstOrNull { it.name == name } }

/** The chip's short verdict, because `ApiReachability.label` is a sentence. */
private fun ApiReachability.shortLabel(): String = when (this) {
    ApiReachability.ONLINE -> "Reachable"
    ApiReachability.SLOW -> "Slow"
    ApiReachability.RATE_LIMITED -> "Rate limited"
    ApiReachability.OFFLINE -> "Unreachable"
    ApiReachability.DOWN -> "Down"
    ApiReachability.NO_ANSWER -> "No answer"
    ApiReachability.UNKNOWN -> "Not checked"
}

/**
 * True answered, false did not, null no verdict yet.
 *
 * `RATE_LIMITED` is healthy: AniList answered, it just asked for fewer requests,
 * and painting the chip as a failure would send the reader looking for a network
 * problem that is not there.
 */
private fun ApiReachability.health(): Boolean? = when (this) {
    ApiReachability.ONLINE, ApiReachability.SLOW, ApiReachability.RATE_LIMITED -> true
    ApiReachability.OFFLINE, ApiReachability.DOWN, ApiReachability.NO_ANSWER -> false
    ApiReachability.UNKNOWN -> null
}

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
    notificationPreferencesStore: NotificationPreferencesStore,
    notificationReadStore: NotificationReadStore,
    viewer: ViewerProfile?,
    onNavigateToLogin: () -> Unit,
    totalWatchedCount: Int?,
    totalMissedCount: Int?,
    cardPreviewEntries: List<MediaListEntryItem>,
    /**
     * The shelf section a search result asked for, as its enum's name.
     *
     * One string for every shelf instead of a typed state per shelf: the section
     * resolves it against its own enum, so adding a sub-page no longer costs three
     * states and two callbacks in this screen.
     */
    targetSection: String?,
    onTargetConsumed: () -> Unit,
    /** Leaves the open page for the Settings shelf; used by the pages that own theirs. */
    onClosePage: () -> Unit
) {
    when (page) {
        // The account page is reached from the hero card rather than a shelf row, so
        // it does not own a header - `SettingsScreen` draws "AniList account" above
        // it like any other flat page.
        SettingsPage.ACCOUNT -> EditSettingsTab(
            authViewModel = authViewModel,
            onNavigateToLogin = onNavigateToLogin,
            viewer = viewer,
            totalWatchedCount = totalWatchedCount,
            totalMissedCount = totalMissedCount
        )
        SettingsPage.APPEARANCE -> ThemeTab(
            themePreferences = themePreferences,
            pageTitle = page.title,
            pageSubtitle = page.subtitle,
            onClosePage = onClosePage,
            initialSection = targetSection.toSection<ThemeSection>(),
            onInitialSectionConsumed = onTargetConsumed
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
            initialSection = targetSection.toSection<InterfaceSection>(),
            onInitialSectionConsumed = onTargetConsumed
        )
        SettingsPage.TYPE_AND_DISPLAY -> TypeDisplayTab(
            themePreferences = themePreferences,
            interfacePreferences = interfacePreferences,
            pageTitle = page.title,
            pageSubtitle = page.subtitle,
            onClosePage = onClosePage,
            initialSection = targetSection.toSection<TypeSection>(),
            onInitialSectionConsumed = onTargetConsumed
        )
        SettingsPage.LIBRARY -> LibrarySettingsTab(
            refreshIntervalPreferences = refreshIntervalPreferences,
            arrivingPreferences = arrivingPreferences,
            libraryPreferences = libraryPreferences,
            mainTabPreferences = mainTabPreferences,
            pageTitle = page.title,
            pageSubtitle = page.subtitle,
            onClosePage = onClosePage,
            initialSection = targetSection.toSection<LibrarySection>(),
            onInitialSectionConsumed = onTargetConsumed
        )
        SettingsPage.JOKES -> JokeSettingsTab(
            jokePreferences = jokePreferences,
            pageTitle = page.title,
            pageSubtitle = page.subtitle,
            onClosePage = onClosePage,
            initialSection = targetSection.toSection<JokeSection>(),
            onInitialSectionConsumed = onTargetConsumed
        )
        SettingsPage.SEASONAL -> SeasonalSettingsTab(
            seasonalPreferences = seasonalPreferences
        )
        SettingsPage.DEVICE -> DeviceManagerTab(
            seasonalPreferences = seasonalPreferences,
            devicePreferences = devicePreferences,
            themePreferences = themePreferences,
            appLockPreferences = appLockPreferences
        )
        SettingsPage.NOTIFICATIONS -> NotificationSettingsPage(
            preferencesStore = notificationPreferencesStore,
            readStore = notificationReadStore,
            viewer = viewer
        )
        SettingsPage.UPDATES -> UpdatesSettingsTab(changelogRepository = changelogRepository)
        SettingsPage.ABOUT -> InfoSettingsTab(
            pageTitle = page.title,
            pageSubtitle = page.subtitle,
            onClosePage = onClosePage,
            initialSection = targetSection.toSection<AboutSection>(),
            onInitialSectionConsumed = onTargetConsumed
        )
    }
}
