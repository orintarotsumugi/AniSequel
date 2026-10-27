package com.anisequel.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.BuildConfig
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.data.repository.AppLockPreferences
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.viewmodel.AuthViewModel
import com.anisequel.ui.viewmodel.rememberApiStatusProbe

/**
 * The pages behind the Settings hub.
 *
 * They are an enum rather than a nested navigation graph because they are not
 * destinations a link or a back stack should be able to reach on their own: the
 * hub is the only entrance, and `BackHandler` stepping out of a page is the whole
 * navigation contract.
 */
private enum class SettingsPage(val title: String, val subtitle: String) {
    APPEARANCE("Appearance", "Theme, background, accent and motion"),
    JOKES("Jokes", "Ani-chan's timing, tone and saved lines"),
    LIBRARY("Library & dashboard", "Refresh, arriving rows and quick add"),
    ACCOUNT("AniList account", "Client ID, session and app lock"),
    // "and links" is not padding: the page carries the repository and the issue
    // tracker, which is where a bug report actually goes. A subtitle that stops at
    // "privacy" describes four of its six cards.
    ABOUT("About & support", "Build, updates, privacy and links")
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
 * ## Why `AnimatedContent` still uses `using null`
 *
 * The pages are scrollable columns, and the previous tab implementation carried
 * a comment about a crash from the default `SizeTransform` animating the
 * container's height while the outgoing page settled its scroll. That reasoning
 * still holds: the height snaps, the fades and scales are draw-phase and stay.
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
    changelogRepository: ChangelogRepository,
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
    modifier: Modifier = Modifier
) {
    var page by rememberSaveable { mutableStateOf<SettingsPage?>(null) }
    var showConnectionCheck by rememberSaveable { mutableStateOf(false) }
    // One probe, for the one place that checks: the shelf's "AniList connection"
    // row and the dialog it opens share it, so a Check tap cannot disagree with
    // the row that opened it. The About page used to raise a second, independent
    // probe for its own check button; that card is gone because the row here
    // already opens the same dialog.
    val apiProbe = rememberApiStatusProbe(checkApiReachability)

    BackHandler {
        if (page != null) page = null else onNavigateBack()
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
                transitionSpec = {
                    (
                        fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                            scaleIn(
                                animationSpec = ExpressiveMotion.FastSpatial,
                                initialScale = 0.96f
                            ) togetherWith
                            fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                            scaleOut(
                                animationSpec = ExpressiveMotion.FastSpatial,
                                targetScale = 1.03f
                            )
                        ) using null
                },
                label = "settings_page_transition",
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("settings_tab_content")
            ) { destination ->
                if (destination == null) {
                    SettingsRoot(
                        onClose = onNavigateBack,
                        onOpen = { page = it },
                        onCheckConnection = { showConnectionCheck = true }
                    )
                } else {
                    Column(modifier = Modifier.fillMaxSize()) {
                        SettingsSubHeader(
                            title = destination.title,
                            subtitle = destination.subtitle,
                            onBack = { page = null }
                        )
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
                                changelogRepository = changelogRepository,
                                viewer = viewer,
                                onNavigateToLogin = onNavigateToLogin,
                                totalWatchedCount = totalWatchedCount,
                                totalMissedCount = totalMissedCount
                            )
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
 * who it is signed in as, whether the network is awake, and what changed. Each
 * row is its own card with a 10dp gap, so a tap that lands between two rows hits
 * the background instead of the wrong setting.
 */
@Composable
private fun SettingsRoot(
    onClose: () -> Unit,
    onOpen: (SettingsPage) -> Unit,
    onCheckConnection: () -> Unit
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

            SettingsSectionLabel("Personalisation")
            SettingsRows {
                SettingsRow(
                    page = SettingsPage.APPEARANCE,
                    icon = AppVectorIcons.SectionAppearance,
                    testTag = "settings_row_appearance",
                    onOpen = onOpen
                )
                SettingsRow(
                    page = SettingsPage.JOKES,
                    icon = AppVectorIcons.AnimeSparkle,
                    testTag = "settings_row_jokes",
                    onOpen = onOpen
                )
            }

            SettingsSectionLabel("Library")
            SettingsRows {
                SettingsRow(
                    page = SettingsPage.LIBRARY,
                    icon = AppVectorIcons.Tune,
                    testTag = "settings_row_library",
                    onOpen = onOpen
                )
            }

            // The connection check used to sit under a "Network" label of its own,
            // which is a whole section heading for one row - and a heading that
            // names the transport rather than the thing being checked. It is the
            // same AniList the account row is about, so it lives here, second.
            SettingsSectionLabel("AniList")
            SettingsRows {
                SettingsRow(
                    page = SettingsPage.ACCOUNT,
                    icon = AppVectorIcons.SectionAniList,
                    testTag = "settings_row_account",
                    onOpen = onOpen
                )
                SettingsHubRow(
                    // `SignalOn`, not `BrokenHeart`: this row asks a question. A
                    // broken heart beside "connection" claims the answer before the
                    // check has run, on a screen where the answer is usually fine.
                    icon = AppVectorIcons.SignalOn,
                    title = "Connection check",
                    subtitle = "Ask AniList whether it is reachable",
                    onClick = onCheckConnection,
                    testTag = "settings_row_connection"
                )
            }

            SettingsSectionLabel("About")
            SettingsRows {
                SettingsRow(
                    page = SettingsPage.ABOUT,
                    icon = AppVectorIcons.SectionAbout,
                    testTag = "settings_row_about",
                    onOpen = onOpen
                )
            }

            Box(modifier = Modifier.padding(horizontal = 20.dp)) {
                SettingsVersionFooter(
                    versionName = BuildConfig.VERSION_NAME,
                    versionCode = BuildConfig.VERSION_CODE
                )
            }
        }
    }
}

/** The 20dp gutter and 10dp gaps every group of rows shares. */
@Composable
private fun SettingsRows(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        content()
    }
}

/**
 * A shelf row built from the [SettingsPage] it opens.
 *
 * The row and the page header used to spell the title and the subtitle out twice,
 * and they had already drifted: the Jokes row promised "timing and placement"
 * while the page it opened said "timing, tone and saved lines". A row that
 * describes a different page than the one it opens is a small lie on the way in.
 * Deriving both from the enum makes the drift impossible rather than unlikely.
 */
@Composable
private fun SettingsRow(
    page: SettingsPage,
    icon: ImageVector,
    testTag: String,
    onOpen: (SettingsPage) -> Unit
) {
    SettingsHubRow(
        icon = icon,
        title = page.title,
        subtitle = page.subtitle,
        onClick = { onOpen(page) },
        testTag = testTag
    )
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
    changelogRepository: ChangelogRepository,
    viewer: ViewerProfile?,
    onNavigateToLogin: () -> Unit,
    totalWatchedCount: Int?,
    totalMissedCount: Int?
) {
    when (page) {
        SettingsPage.APPEARANCE -> ThemeTab(themePreferences = themePreferences)
        SettingsPage.JOKES -> JokeSettingsTab(jokePreferences = jokePreferences)
        SettingsPage.LIBRARY -> LibrarySettingsTab(
            refreshIntervalPreferences = refreshIntervalPreferences,
            arrivingPreferences = arrivingPreferences,
            quickAddPreferences = quickAddPreferences
        )
        SettingsPage.ACCOUNT -> EditSettingsTab(
            authViewModel = authViewModel,
            appLockPreferences = appLockPreferences,
            onNavigateToLogin = onNavigateToLogin,
            viewer = viewer,
            totalWatchedCount = totalWatchedCount,
            totalMissedCount = totalMissedCount
        )
        SettingsPage.ABOUT -> InfoSettingsTab(changelogRepository = changelogRepository)
    }
}
