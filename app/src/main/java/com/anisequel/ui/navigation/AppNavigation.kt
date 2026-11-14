package com.anisequel.ui.navigation

import android.content.Context
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.ui.components.cards.LocalMediaCardStyle
import com.anisequel.ui.components.detail.RepositoryEntryEditor
import com.anisequel.ui.components.markdown.ProvideMediaCovers
import com.anisequel.data.network.NetworkClient
import com.anisequel.data.repository.AniListRepositoryImpl
import com.anisequel.data.repository.AniListSnapshots
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.AuthRepository
import com.anisequel.data.repository.HiddenSequelsPreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.OnboardingPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.DevicePreferences
import com.anisequel.data.repository.SeasonalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.data.repository.TierListPreferences
import com.anisequel.domain.ui.NavBarLift
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.seasonal.SeasonalParticleHost
import com.anisequel.ui.screens.login.FirstRunSetupHost
import com.anisequel.ui.components.seasonal.rememberSeasonalAmbientModifier
import com.anisequel.ui.theme.LocalAppLayout
import com.anisequel.ui.theme.LocalConfirmPlanningAdd
import com.anisequel.ui.theme.LocalHomeSequelStyle
import com.anisequel.ui.theme.LocalLibraryViewMode
import com.anisequel.ui.theme.LocalMainBarLift
import com.anisequel.ui.theme.LocalShowContinueRail
import com.anisequel.ui.theme.LocalSearchAnimeEnabled
import com.anisequel.ui.theme.LocalSearchMangaEnabled
import com.anisequel.ui.theme.LocalSearchTopBarShortcut
import com.anisequel.ui.theme.LocalTierTopBarShortcut
import com.anisequel.ui.screens.profile.rememberLibrarySelection
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.AuthViewModel
import com.anisequel.ui.viewmodel.DashboardUiState
import com.anisequel.ui.viewmodel.DashboardViewModel
import com.anisequel.ui.viewmodel.rememberMediaDetailHost
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

object AppRoutes {
    const val LOGIN = "login"
    const val DASHBOARD = "dashboard"
    const val DASHBOARD_DEMO = "dashboard_demo"
    const val DASHBOARD_USER = "dashboard_user/{username}"
    const val LIBRARY = "library"

    /** The catalogue search, one of the bar's pages when its tab is enabled. */
    const val SEARCH = "search"
    const val SETTINGS = "settings"
    const val TIER_LIST = "tier_list"

    /** The new look's feature hub. Hidden from the classic bar by design. */
    const val OTHER = "other"

    /** Airing and announced titles from the viewer's list, opened from Other. */
    const val SCHEDULE = "schedule"

    /**
     * The path is encoded, not interpolated raw.
     *
     * A `composable()` route is matched exactly, so a name carrying `/`, `#`, `?`
     * or `%` produced a URI that matched no destination and the tap did nothing
     * with no error anywhere - the same silent failure [ProfileRoutes.byId]
     * documents for the query form. Navigation decodes the segment back for
     * `NavType.StringType`, so the screen still receives the name as typed.
     */
    fun userDashboard(username: String) = "dashboard_user/${Uri.encode(username)}"
}

/** A state that never emits a real dashboard, for when there is no session VM. */
private val EmptyDashboardState = MutableStateFlow<DashboardUiState?>(null)

/**
 * Whether the top bar may offer catalogue search.
 *
 * The shortcut needs both answers, not just its own switch: a reader who leaves
 * the shortcut on but turns both catalogue halves off would otherwise get a
 * magnifier into an empty page. Kept out of [AppNavigation] so the host's own
 * composition stays a routing function rather than one more place a pair of
 * booleans is combined by hand.
 */
private fun catalogueShortcutOffered(
    shortcutEnabled: Boolean,
    searchAnimeEnabled: Boolean,
    searchMangaEnabled: Boolean
): Boolean = shortcutEnabled && (searchAnimeEnabled || searchMangaEnabled)

@Composable
fun AppNavigation(
    authRepository: AuthRepository,
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    /**
     * The app-lock choice. Settings writes it; `MainActivity` reads the same
     * DataStore to decide whether to draw the gate, so the switch and the lock
     * cannot disagree about whether the feature is on.
     */
    appLockPreferences: AppLockPreferences,
    /**
     * A tap on the home-screen widget asked for the profile's Activity tab.
     *
     * Passed in rather than read from the Activity's intent here so this stays a
     * pure function of its arguments - the widget's request arrives as an Intent
     * extra, which only `MainActivity` can see, and routing it through here is
     * what keeps the navigation graph from having to know about `Intent`s.
     */
    openProfileActivityOnLaunch: Boolean = false,
    /** Called once the request has been navigated to, so it is not replayed. */
    onProfileActivityOpened: () -> Unit = {},
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val authState by authViewModel.uiState.collectAsState()

    // Application-scoped: the hidden list is a property of the person, not of a
    // screen, and it has to survive the Settings screen and the detail sheet
    // being torn down. Without this the dashboards are built with a null store
    // and "Not interested" silently does nothing.
    val context = LocalContext.current
    val hiddenSequelsPreferences = remember(context) {
        HiddenSequelsPreferences(context.applicationContext)
    }

    // Application-scoped for the same reason: how often the list is re-fetched is
    // a property of the person, and the repository that caches the list outlives
    // any one screen. Two instances would mean two DataStore readers disagreeing
    // about the same preference.
    val refreshIntervalPreferences = remember(context) {
        RefreshIntervalPreferences(context.applicationContext)
    }

    // Application-scoped so the dashboard and Settings agree in one session:
    // the dashboard mirrors both flags and Settings writes them, and two
    // instances would read two copies of the same preference.
    val arrivingPreferences = remember(context) {
        ArrivingPreferences(context.applicationContext)
    }

    val quickAddPreferences = remember(context) {
        com.anisequel.data.repository.QuickAddPreferences(context.applicationContext)
    }

    // Application-scoped for the same reason as the rest: the toast host here and
    // the joke card in Settings are two readers of one preference. Two DataStore
    // instances would let the preview describe an edge the toast never uses.
    val jokePreferences = remember(context) {
        JokePreferences(context.applicationContext)
    }
    val jokeSettings by jokePreferences.settings.collectAsState(initial = JokeSettings())
    // A separate flow because saved lines are the reader's content rather than a
    // switch; the host needs them to draw the heart filled in the pill.
    val jokeFavourites by jokePreferences.favourites.collectAsState(initial = emptySet())

    // The Library page's remembered media type. Application-scoped and built here
    // for the same reason as the rest: one DataStore reader per process, so the
    // tab cannot disagree with itself about what was last chosen.
    val libraryPreferences = remember(context) {
        com.anisequel.data.repository.LibraryPreferences(context.applicationContext)
    }

    // Application-scoped for the same reason as the rest: the tier list is the
    // reader's own saved work, and a tier list built one visit and thrown away on
    // the next is worse than no feature at all. `remember` keyed on `context` is
    // what keeps one DataStore reader per process, so a second destination cannot
    // disagree with this one about what is filed where.
    val tierListPreferences = remember(context) {
        TierListPreferences(context.applicationContext)
    }

    // The seasonal overlay's shape, density, size and speed. Application-scoped
    // for the reason every other preference here is: the host mounted below and
    // the preview in Settings are two readers of one DataStore, so the page cannot
    // describe a sky the app is not drawing.
    val seasonalPreferences = remember(context) {
        SeasonalPreferences(context.applicationContext)
    }

    // Device facts the Seasonal page reads and writes: the cover-cache ceiling, whose
    // saved value the image loader is built from at process start. Application-scoped
    // like the rest, so the page's pill and the pipeline's ceiling are one fact.
    val devicePreferences = remember(context) {
        DevicePreferences(context.applicationContext)
    }

    // Which pages the bar offers. Application-scoped like the rest, because the
    // host draws the bar and Settings writes the switch: two DataStore readers
    // would let the switch describe a bar the host is not drawing.
    val mainTabPreferences = remember(context) {
        MainTabPreferences(context.applicationContext)
    }
    val tierListTab by mainTabPreferences.tierListTab.collectAsState(
        initial = MainTabPreferences.DEFAULT_TIER_LIST_TAB
    )
    val searchTab by mainTabPreferences.searchTab.collectAsState(
        initial = MainTabPreferences.DEFAULT_SEARCH_TAB
    )
    val searchAnimeEnabled by mainTabPreferences.searchAnimeEnabled.collectAsState(
        initial = MainTabPreferences.DEFAULT_SEARCH_ANIME
    )
    val searchMangaEnabled by mainTabPreferences.searchMangaEnabled.collectAsState(
        initial = MainTabPreferences.DEFAULT_SEARCH_MANGA
    )
    val searchTopBar by mainTabPreferences.searchTopBar.collectAsState(
        initial = MainTabPreferences.DEFAULT_SEARCH_TOP_BAR
    )
    val tierTopBar by mainTabPreferences.tierTopBar.collectAsState(
        initial = MainTabPreferences.DEFAULT_TIER_TOP_BAR
    )

    val onboardingPreferences = remember(context) {
        OnboardingPreferences(context.applicationContext)
    }
    val onboardingCompleted by onboardingPreferences.completed.collectAsState(initial = true)

    // How the media cards are drawn. Read here and handed down as a composition
    // local rather than as another parameter on every destination, because the
    // Library grid, the dashboard's sequel rows and the picker's own preview are
    // three readers of one fact - and a style passed to two of them would let the
    // preview describe a card the app does not build.
    val interfacePreferences = remember(context) {
        InterfacePreferences(context.applicationContext)
    }
    val cardStyle by interfacePreferences.cardStyle.collectAsState(
        initial = MediaCardStyle.DEFAULT
    )

    // The rest of the interface's answer - which layout, how dense the new
    // Library is, whether Home shows the Continue rail - in one emission. Read
    // here and provided as composition locals rather than threaded through every
    // destination: the dashboard, the Library, the bar and the Settings preview
    // have to agree, and a parameter is one more place they can disagree.
    // The bar's lift is the one preference that moves something the reader is
    // already looking at. Writing the preference on release used to snap the bar
    // and every list's bottom clearance in one frame - a jarring jump for a
    // control whose whole point is a visible amount of daylight - so the stored
    // value is interpolated once here, at the app root, and every reader of
    // `LocalMainBarLift` (the bar and the lists that reserve clearance) follows
    // the same spring. The Settings page keeps its own live draft for the thumb.
    // Clamped again on this side: DataStore can hold a value written by an older
    // build, and a lift outside 0..24 is not a layout this app draws.
    val interfaceSettings by interfacePreferences.settings.collectAsState(
        initial = InterfacePreferences.InterfaceSettings()
    )
    val animatedBarLift by animateIntAsState(
        targetValue = NavBarLift.clamp(interfaceSettings.mainBarLift),
        animationSpec = ExpressiveMotion.FastSpatialInt,
        label = "main_bar_lift"
    )

    // The planning confirmation, read once at the root for the same reason. The
    // dashboard still collects this flow for its hold/swipe settings; this read is
    // only the tick/cross switch, and it is what makes the behaviour follow the
    // reader from card to detail sheet without every host being handed it.
    val quickAddSettings by quickAddPreferences.settings.collectAsState(
        initial = com.anisequel.data.repository.QuickAddPreferences.QuickAddSettings()
    )
    val appScope = rememberCoroutineScope()

    // Derived from the enum, filtered by the setting - see [enabledMainTabs].
    // Remembered on the flag so the bar's list is the same instance across
    // recompositions; it is an index table for `MainBar`, not state.
    val mainTabs = remember(tierListTab, searchTab, searchAnimeEnabled, searchMangaEnabled) {
        enabledMainTabs(
            tierListTab = tierListTab,
            searchTab = searchTab,
            searchAnimeEnabled = searchAnimeEnabled,
            searchMangaEnabled = searchMangaEnabled
        )
    }

    // Whether the tier list has a board open, which is the one thing that hides the
    // bar on a page that is otherwise one of its own. It lives here because the bar
    // is drawn here and the board is inside a destination: the screen reports the
    // change, the host owns the answer. Not `rememberSaveable` - a board is
    // restored from the ViewModel, which reports itself open again on the first
    // composition, and a stale `true` surviving process death would hide the bar
    // over the library.
    var tierBoardOpen by remember { mutableStateOf(false) }

    // The disk tier under the repository's caches. Process-wide rather than
    // per-screen because the auth ViewModel's repository writes and clears the
    // same files - see [AniListSnapshots] - and it lives in the cache directory
    // because everything in it can be re-fetched: Android emptying it under
    // storage pressure, or the user clearing the app's cache, costs a request
    // and never a screen. Sign-out deletes it explicitly rather than trusting
    // the OS to ever do so.
    val aniListSnapshots = remember(context) { AniListSnapshots.of(context) }

    // The cache window is a supplier rather than a value because the user can
    // change it from Settings while the app is open: reading it per lookup is
    // what makes "15 min" mean fifteen minutes from the moment it is picked,
    // rather than from the moment this repository was built.
    val aniListRepository = remember(authRepository) {
        AniListRepositoryImpl(
            apiService = NetworkClient.createApiService(authRepository),
            listCacheTtlMillis = refreshIntervalPreferences::currentStalenessMillis,
            snapshots = aniListSnapshots
        )
    }

    // Application-scoped for the same reason: the notes are a property of the
    // build, and the release workflow reads the same file to write the release
    // body. Two readers of two copies is how an app claims a feature the
    // download page does not mention.
    val changelogRepository = remember(context) {
        ChangelogRepository(context.applicationContext)
    }

    // Mirrors the stored interval into the plain value the repository reads,
    // since a list-cache lookup cannot suspend. Started here rather than in the
    // preferences object because that would mean the class owning a CoroutineScope
    // it has no business having.
    LaunchedEffect(refreshIntervalPreferences) {
        refreshIntervalPreferences.observeInterval()
    }

    // Nothing is loaded until the stored session has actually been read.
    //
    // Picking a start destination from an unresolved state is what produced a
    // login screen on every cold start, and - because the OAuth round trip hands
    // the app off to the browser long enough for the process to be killed - a
    // login screen every time the user came *back* from signing in. It read as
    // the app forgetting they had asked to sign in, then signing them in anyway
    // a second later.
    //
    // A splash for the few tens of milliseconds this takes is the honest thing
    // to draw while the answer is genuinely unknown. Guessing is not.
    if (authState is AuthUiState.Restoring) {
        RestoringPlaceholder(modifier)
        return
    }

    // The signed-in dashboard is scoped to the activity, not to its destination,
    // for two reasons: rotating the device used to throw it away and re-fetch the
    // user's entire list, and Settings needs the same viewer profile that the
    // dashboard already holds - it used to be passed through a plain `remember`,
    // which lost the profile on every configuration change.
    //
    // Built only when there is a session, and keyed on the token. Both halves are
    // load-bearing:
    //
    // `DashboardViewModel` loads in its `init`, so constructing it while
    // unauthenticated issued the viewer query with no `Authorization` header at
    // all. AniList answers that 401 "Unauthorized.", the repository classified it
    // as a dead session, and because the ViewModel is activity-scoped the screen
    // kept that error for good - so signing in landed the user on "Session
    // expired. Please re-authenticate." having just been told their sign-in
    // succeeded. Nothing re-ran the `init`, so no retry could clear it. It read
    // as the app refusing the login it had just accepted.
    //
    // Keying on the token means signing out and back in produces a fresh
    // ViewModel whose `init` runs against the token actually in force, instead of
    // re-showing whatever the previous session left behind.
    val authenticated = authState as? AuthUiState.Authenticated

    val mainDashboardViewModel: DashboardViewModel? = if (authenticated != null) {
        viewModel(
            key = "dashboard_${authenticated.token}",
            factory = DashboardViewModel.Factory(
                aniListRepository = aniListRepository,
                hiddenSequelsPreferences = hiddenSequelsPreferences,
                refreshIntervalPreferences = refreshIntervalPreferences,
                arrivingPreferences = arrivingPreferences
            )
        )
    } else {
        null
    }

    // Collected once here, beside the session, so the profile routes have the
    // viewer's name and id without each of them re-reading the dashboard's
    // state. The id is the load-bearing half: without it the profile screen
    // looks its list up by name, misses the cache the dashboard just filled, and
    // spends AniList's most expensive request re-downloading a list it already
    // holds. See `ProfileDestination`.
    //
    // The empty flow is the same one Settings uses, for the same reason: a
    // null-propagating `?.` would hide the `collectAsState` call on the frames
    // where there is no session, and re-entering this lambda with a different
    // call sequence crashes at runtime.
    val dashboardFlow = mainDashboardViewModel?.uiState ?: EmptyDashboardState
    val dashboardState by dashboardFlow.collectAsState()

    // Derived rather than read straight off the state, because `uiState` is
    // replaced on every dashboard recompute - which includes every character
    // typed into the dashboard's search box. A plain read here subscribed this
    // composable, the app's root, to all of them, so each keystroke recomposed the
    // `NavHost`, the bar and the toast host to discover that the viewer was still
    // the same person. `derivedStateOf` compares by equality, so it invalidates
    // only when the identity actually changes.
    //
    // Keyed on the flow because signing in or out swaps the ViewModel and with it
    // the state being read; a derived state built over the old one would keep
    // reporting the previous account's viewer.
    val signedInViewer by remember(dashboardFlow) {
        derivedStateOf { (dashboardState as? DashboardUiState.Success)?.viewer }
    }

    // The three dashboard values the Library and Settings graphs read.
    //
    // They used to be handed the whole state instead, which put a read of it in
    // the root composition: every publish - including one per keystroke in the
    // dashboard's search box - gave the graphs a new state instance and
    // recomposed the root, the bar and the toast host to discover the error and
    // the two counts were unchanged. Derived states compare by equality, so
    // these invalidate only on a real change.
    val dashboardError by remember(dashboardFlow) {
        derivedStateOf { dashboardState as? DashboardUiState.Error }
    }
    val totalWatchedCount by remember(dashboardFlow) {
        derivedStateOf { (dashboardState as? DashboardUiState.Success)?.totalWatchedCount }
    }
    val totalMissedCount by remember(dashboardFlow) {
        derivedStateOf { (dashboardState as? DashboardUiState.Success)?.totalMissedCount }
    }

    // The three entries Settings' card preview draws. Derived for the same reason
    // as the counts above: `uiState` is replaced on every recompute, and a plain
    // read here would subscribe the app root to all of them to discover that the
    // same three covers are still on screen. `cardPreviewEntries` is memoised on
    // the collection, so this only changes when the library actually changes.
    val cardPreview by remember(dashboardFlow) {
        derivedStateOf { (dashboardState as? DashboardUiState.Success)?.cardPreview.orEmpty() }
    }

    // The Library page's remembered filters and grid position, resolved beside the
    // other app-scoped stores rather than inside the route: the route is disposed
    // when the reader switches tabs, and re-asking DataStore - or re-remembering the
    // grid - on every return is the failure this page was fixed for when it was the
    // profile's fourth tab (see `RememberedLibraryView`). One DataStore reader per
    // process, as everywhere.
    val librarySelection = rememberLibrarySelection(libraryPreferences)
    val libraryGridState = rememberLazyGridState()
    // The detail sheet's on-demand fetches, held here for the whole app rather than
    // per route: the Library, the Profile and a studio page all open the same
    // `SequelDetailSheet`, and each used to own a store of its own. That is why the
    // same title opened from two tabs was two requests and two caches, and why a
    // title the Library had already loaded could still arrive at a profile without
    // its data. One host makes the sheet the same page wherever the tap came from;
    // leaving a tab and coming back no longer re-fetches a synopsis it already has.
    //
    // The dashboard is the one exception, and it is not a second rule: its store
    // also folds each answer into the recommendation cards behind the sheet, which
    // is a dashboard-only write. See `MediaDetailHost`.
    //
    // Deliberately not gated on `authenticated`: this host now serves the studio
    // page too, whose titles are fetched live even in the demo, so a "do not
    // fetch" flag here would leave a real studio title's sheet half empty. The
    // demo's own summaries are complete already, so the fetch they trigger is
    // redundant rather than wrong.
    val detailHost = rememberMediaDetailHost(aniListRepository)

    // The one entry editor the non-dashboard hosts share: the Library and the
    // studio page open the same `SequelDetailSheet` the dashboard does, and a
    // sheet that has the "My Entry" tab in one place but not in another is two
    // different sheets over the same title. `authenticated` is the gate rather
    // than `signedInViewer`, which stays null until the dashboard answers: the
    // editor's load omits the user id when it has none and AniList resolves the
    // entry under the session, so a known id is a refinement, not a requirement.
    // A demo session has no session to write with, so it gets null and the tab is
    // left out entirely.
    val sharedEntryEditor = remember(authenticated != null, signedInViewer?.id) {
        if (authenticated == null) {
            null
        } else {
            RepositoryEntryEditor(aniListRepository, signedInViewer?.id)
        }
    }

    // React to auth state changes to navigate automatically.
    LaunchedEffect(authState) {
        when (authState) {
            is AuthUiState.Authenticated -> {
                val currentRoute = navController.currentDestination?.route
                // The Library and the viewer's own profile join Settings here:
                // an auth emission that lands while the reader is reading either
                // of them is not a reason to yank them off it. The bar already
                // shows the page as current, and Home is one tap away - the
                // emission is about the session, not about where they were.
                if (currentRoute != AppRoutes.DASHBOARD &&
                    currentRoute != AppRoutes.LIBRARY &&
                    // Every one of the bar's pages belongs here, including the
                    // optional ones: an auth emission is not a reason to pull a
                    // reader off a page the bar is currently showing as theirs.
                    currentRoute != AppRoutes.SEARCH &&
                    currentRoute != AppRoutes.TIER_LIST &&
                    currentRoute != ProfileRoutes.OWN_ROUTE &&
                    currentRoute != AppRoutes.SETTINGS
                ) {
                    navController.navigate(AppRoutes.DASHBOARD) {
                        popUpTo(AppRoutes.LOGIN) { inclusive = true }
                    }
                }
            }
            is AuthUiState.Unauthenticated -> {
                val currentRoute = navController.currentDestination?.route
                // The tier list is named here for the same reason the other two
                // are. It reads one person's saved work and needs no session of
                // its own, so nothing would ever move the user off it - which is
                // precisely the failure: an AniList 401 that lands while the list
                // is open would drop the session and leave the ranking on screen
                // with no dashboard, no sign-in and no way forward but Back.
                //
                // The Library and the viewer's own profile are the same failure
                // now that the bar made them pages: both fall back to the demo
                // fixture when the session goes (`isDemo`), so nothing would move
                // the reader off them either - and a dropped session would leave
                // them looking at demo data with no dashboard, no sign-in and no
                // way forward but Back.
                if (currentRoute == AppRoutes.DASHBOARD ||
                    currentRoute == AppRoutes.SETTINGS ||
                    currentRoute == AppRoutes.TIER_LIST ||
                    currentRoute == AppRoutes.LIBRARY ||
                    currentRoute == AppRoutes.SEARCH ||
                    currentRoute == ProfileRoutes.OWN_ROUTE
                ) {
                    // `popUpTo(0)` is not a destination - it popped nothing and
                    // left the dashboard underneath the login screen, so signing
                    // out and back in replayed the old dashboard state.
                    navController.navigate(AppRoutes.LOGIN) {
                        popUpTo(navController.graph.id) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }

    // The widget's tap, honoured once the app knows who is signed in.
    //
    // Deferred rather than fired on arrival because a profile needs an identity:
    // the widget's `PendingIntent` can only start the Activity, and at that moment
    // the stored session may still be being restored - or may not exist, in which
    // case the request has to stay pending until a sign-in resolves it instead of
    // being spent on a login screen.
    //
    // `Authenticated` is not yet an identity: it is the token, and the viewer id
    // and name arrive one query later. Firing on the token alone put the profile
    // route through its demo fallback, so the reader landed on "OtakuExplorer"'s
    // fixture - somebody else's invented list - for the length of a round trip,
    // and for good if that query failed.
    LaunchedEffect(openProfileActivityOnLaunch, authState, signedInViewer) {
        if (openProfileActivityOnLaunch &&
            authState is AuthUiState.Authenticated &&
            signedInViewer != null
        ) {
            navController.navigateSingle(ProfileRoutes.OWN_ACTIVITY)
            onProfileActivityOpened()
        }
    }

    // Keeps the home-screen widgets in step with the live dashboard counts.
    //
    // Called rather than inlined because nothing in this body may read the
    // dashboard state: `uiState` is replaced on every filter keystroke, and a
    // read here invalidated the app's root - the `NavHost`, the bar and the toast
    // host - once per character typed into the dashboard's search box. Hoisting
    // the sync into its own composable confines that invalidation to a scope that
    // draws nothing.
    WidgetSync(dashboardFlow = dashboardFlow, authState = authState, context = context)

    // One provider for every markdown surface in the app: a person's bio, a
    // comment, a draft preview and a title's synopsis all draw AniList link
    // cards, and each of them used to need its own cover plumbing - which is
    // how the Creator page ended up drawing badges with no posters while the
    // profile's About card beside it drew them correctly. Provided here, at
    // the app's only composition root, a surface gets covers by rendering its
    // document with `AniListMarkdownWithCovers` and nothing else.
    // Scrolls and taps from every page are observed at the root and become wind
    // for the seasonal sky. This is a *parent* observer: it never consumes the
    // gesture, so every control under it still gets the tap it always did. It is
    // installed even while the effect is off because the observers cannot be
    // added retroactively to a gesture in progress. See SeasonalWind.
    val seasonalAmbient = rememberSeasonalAmbientModifier()

    ProvideMediaCovers(aniListRepository) {
        CompositionLocalProvider(
            LocalMediaCardStyle provides cardStyle,
            LocalAppLayout provides interfaceSettings.layout,
            LocalLibraryViewMode provides interfaceSettings.libraryView,
            LocalShowContinueRail provides interfaceSettings.showContinueRail,
            LocalHomeSequelStyle provides interfaceSettings.homeSequelStyle,
            LocalMainBarLift provides animatedBarLift,
            LocalConfirmPlanningAdd provides quickAddSettings.confirmAdds,
            // Both halves off means the search page has no question left to ask,
            // so neither it nor its shortcut should still be offered. One half on
            // is enough for both: the page is still useful.
            LocalSearchTopBarShortcut provides catalogueShortcutOffered(
                shortcutEnabled = searchTopBar,
                searchAnimeEnabled = searchAnimeEnabled,
                searchMangaEnabled = searchMangaEnabled
            ),
            LocalTierTopBarShortcut provides tierTopBar,
            LocalSearchAnimeEnabled provides searchAnimeEnabled,
            LocalSearchMangaEnabled provides searchMangaEnabled
        ) {
            // Opaque, and that is the point.
            //
            // A page arrives scaled to 94% and fading up while the page it covers
            // fades out and settles to 98%: for a few frames neither of them is
            // full-size and neither is solid, so what shows through is whatever this
            // Box is painted. With no background here it was the Activity window - and
            // the platform default is black, so a fast double tap on the Settings icon
            // (or any chained navigation) flashed the whole screen black between two
            // screens that were both perfectly themed. The fade is the app's motion
            // language and stays; the surface behind it is what was missing.
            Box(
                modifier = modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
                    // Mounted once, above the graph, so every scrollable page
                    // reaches the bar's hide-on-scroll rule without a single
                    // screen having to know it exists. The connection never
                    // consumes, and it sits beside the seasonal wind rather
                    // than replacing it - both are observers of the same
                    // gesture.
                    .nestedScroll(MainBarAutoHide.connection)
                    .then(seasonalAmbient)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = if (authState is AuthUiState.Authenticated) AppRoutes.DASHBOARD else AppRoutes.LOGIN,
                    modifier = Modifier.fillMaxSize(),
                    enterTransition = {
                        enterPage(
                            from = initialState.destination.route,
                            to = targetState.destination.route
                        )
                    },
                    exitTransition = {
                        exitPage(
                            from = initialState.destination.route,
                            to = targetState.destination.route,
                            popped = false
                        )
                    },
                    popEnterTransition = {
                        popEnterPage(
                            from = initialState.destination.route,
                            to = targetState.destination.route
                        )
                    },
                    popExitTransition = {
                        exitPage(
                            from = initialState.destination.route,
                            to = targetState.destination.route,
                            popped = true
                        )
                    }
                ) {
                    authDestinations(
                        navController = navController,
                        authViewModel = authViewModel,
                        authState = authState
                    )

                    dashboardDestinations(
                        navController = navController,
                        aniListRepository = aniListRepository,
                        mainDashboardViewModel = mainDashboardViewModel,
                        quickAddPreferences = quickAddPreferences,
                        hiddenSequelsPreferences = hiddenSequelsPreferences,
                        refreshIntervalPreferences = refreshIntervalPreferences,
                        arrivingPreferences = arrivingPreferences,
                        // A list shortcut has to land on the half it named. Writing
                        // the remembered type before the tab switch is what makes
                        // "Manga List" open manga even on a first visit, when no
                        // page has chosen a type yet.
                        onOpenLibrary = { rememberedType ->
                            if (!rememberedType.isNullOrBlank()) {
                                appScope.launch {
                                    libraryPreferences.setType(rememberedType)
                                }
                            }
                            navController.navigateToMainTab(
                                MainTab.LIBRARY,
                                signedIn = authenticated != null
                            )
                        },
                        onSignInAgain = authViewModel::logout
                    )

                    profileDestinations(
                        navController = navController,
                        aniListRepository = aniListRepository,
                        detailHost = detailHost,
                        signedInViewer = signedInViewer,
                        onSignInAgain = authViewModel::logout
                    )

                    tierListDestinations(
                        navController = navController,
                        aniListRepository = aniListRepository,
                        tierListPreferences = tierListPreferences,
                        signedInViewer = signedInViewer,
                        authenticated = authenticated,
                        dashboardError = dashboardError,
                        mainDashboardViewModel = mainDashboardViewModel,
                        onBoardOpenChange = { tierBoardOpen = it },
                        onSignInAgain = authViewModel::logout
                    )

                    libraryDestinations(
                        navController = navController,
                        aniListRepository = aniListRepository,
                        detailHost = detailHost,
                        sharedEntryEditor = sharedEntryEditor,
                        mainDashboardViewModel = mainDashboardViewModel,
                        dashboardError = dashboardError,
                        signedInViewer = signedInViewer,
                        authenticated = authenticated,
                        librarySelection = librarySelection,
                        libraryGridState = libraryGridState,
                        onSignInAgain = authViewModel::logout
                    )

                    studioDestinations(
                        navController = navController,
                        aniListRepository = aniListRepository,
                        detailHost = detailHost,
                        sharedEntryEditor = sharedEntryEditor
                    )

                    otherDestinations(
                        navController = navController,
                        signedIn = authenticated != null,
                        aniListRepository = aniListRepository,
                        quickAddPreferences = quickAddPreferences,
                        dashboardViewModel = mainDashboardViewModel,
                        detailHost = detailHost,
                        entryEditor = sharedEntryEditor
                    )

                    settingsDestinations(
                        navController = navController,
                        authViewModel = authViewModel,
                        themePreferences = themePreferences,
                        appLockPreferences = appLockPreferences,
                        refreshIntervalPreferences = refreshIntervalPreferences,
                        arrivingPreferences = arrivingPreferences,
                        quickAddPreferences = quickAddPreferences,
                        libraryPreferences = libraryPreferences,
                        mainTabPreferences = mainTabPreferences,
                        jokePreferences = jokePreferences,
                        changelogRepository = changelogRepository,
                        seasonalPreferences = seasonalPreferences,
                        devicePreferences = devicePreferences,
                        interfacePreferences = interfacePreferences,
                        aniListRepository = aniListRepository,
                        totalWatchedCount = totalWatchedCount,
                        totalMissedCount = totalMissedCount,
                        cardPreview = cardPreview,
                        signedInViewer = signedInViewer
                    )
                }

                // The seasonal sky is painted between the page and the chrome: over
                // every screen, under the bar and the joke toast. It draws no pointer
                // input, so every tap falls through to the page it covers - the one
                // property a full-screen decoration must have.
                //
                // Mounted here rather than inside a route because a screen cannot draw
                // something that outlives it, and the effect is meant to be one sky
                // over the whole app rather than a different one per page.
                SeasonalParticleHost(preferences = seasonalPreferences)

                // The bar and the joke toast float over whichever page is on screen
                // rather than living inside a route: a screen cannot draw chrome that
                // outlives it, and the pages the bar switches between should not each
                // have to remember to draw it.
                //
                // Both hosts read the current route themselves instead of being handed
                // it from here. Reading it in this scope - the same one that calls
                // `NavHost` - meant every push, pop and tab switch invalidated the call
                // site of the graph and re-ran its destination table, to move two
                // overlays that were the only things on screen which actually changed.
                // The reads now live one scope down, in `MainChrome.kt`.
                MainBarHost(
                    navController = navController,
                    tabs = mainTabs,
                    // The largest the viewer's avatar has; the bar falls back to a glyph
                    // while it is still unknown.
                    profileAvatarUrl = signedInViewer?.avatar?.large ?: signedInViewer?.avatar?.medium,
                    signedIn = authenticated != null,
                    tierBoardOpen = tierBoardOpen,
                    onTierBoardOpenChange = { tierBoardOpen = it }
                )

                JokeHost(
                    navController = navController,
                    preferences = jokePreferences,
                    settings = jokeSettings,
                    favourites = jokeFavourites
                )

                FirstRunSetupHost(
                    completed = onboardingCompleted,
                    authState = authState,
                    onboardingPreferences = onboardingPreferences,
                    interfacePreferences = interfacePreferences,
                    themePreferences = themePreferences,
                    jokePreferences = jokePreferences,
                    mainTabPreferences = mainTabPreferences
                )
            }
        }
    }
}

/**
 * The demo profile's name.
 *
 * Deliberately the same name as [com.anisequel.data.repository.DemoDataProvider]
 * uses for its viewer, so the profile screen's own route and its fixture agree
 * about who "this" is.
 */
internal const val DEMO_USERNAME = "OtakuExplorer"
