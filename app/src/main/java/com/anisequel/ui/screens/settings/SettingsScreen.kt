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
    APPEARANCE("Appearance", "Colour, background and motion"),
    JOKES("Jokes", "Ani-chan's timing and placement"),
    LIBRARY("Library & dashboard", "Refresh, arriving rows and quick add"),
    ACCOUNT("AniList account", "Client ID, session and app lock"),
    ABOUT("About & support", "Release notes, updates and privacy")
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
    // One probe for the row's dialog. The Info page owns a separate probe for its
    // own check button; the two are independent requests, and sharing one would
    // make a check on one page spin the other.
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
                                checkApiReachability = checkApiReachability,
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

            SettingsSectionLabel("Personalization")
            SettingsRows {
                SettingsHubRow(
                    icon = AppVectorIcons.SectionAppearance,
                    title = "Appearance",
                    subtitle = "Colour, background and motion",
                    onClick = { onOpen(SettingsPage.APPEARANCE) },
                    testTag = "settings_row_appearance"
                )
                SettingsHubRow(
                    icon = AppVectorIcons.AnimeSparkle,
                    title = "Jokes",
                    subtitle = "Ani-chan's timing and placement",
                    onClick = { onOpen(SettingsPage.JOKES) },
                    testTag = "settings_row_jokes"
                )
            }

            SettingsSectionLabel("Library")
            SettingsRows {
                SettingsHubRow(
                    icon = AppVectorIcons.Tune,
                    title = "Library & dashboard",
                    subtitle = "Refresh, arriving rows and quick add",
                    onClick = { onOpen(SettingsPage.LIBRARY) },
                    testTag = "settings_row_library"
                )
            }

            SettingsSectionLabel("Network")
            SettingsRows {
                SettingsHubRow(
                    icon = AppVectorIcons.BrokenHeart,
                    title = "AniList connection",
                    subtitle = "Ask AniList whether it is awake",
                    onClick = onCheckConnection,
                    testTag = "settings_row_connection"
                )
            }

            SettingsSectionLabel("Accounts")
            SettingsRows {
                SettingsHubRow(
                    icon = AppVectorIcons.SectionAniList,
                    title = "AniList account",
                    subtitle = "Client ID, session and app lock",
                    onClick = { onOpen(SettingsPage.ACCOUNT) },
                    testTag = "settings_row_account"
                )
            }

            SettingsSectionLabel("About & Support")
            SettingsRows {
                SettingsHubRow(
                    icon = AppVectorIcons.SectionAbout,
                    title = "About & support",
                    subtitle = "Release notes, updates and privacy",
                    onClick = { onOpen(SettingsPage.ABOUT) },
                    testTag = "settings_row_about"
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
    checkApiReachability: suspend () -> ApiStatusSnapshot,
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
            onNavigateToLogin = onNavigateToLogin
        )
        SettingsPage.ABOUT -> InfoSettingsTab(
            viewer = viewer,
            onNavigateToLogin = onNavigateToLogin,
            onSignOut = authViewModel::logout,
            totalWatchedCount = totalWatchedCount,
            totalMissedCount = totalMissedCount,
            changelogRepository = changelogRepository,
            checkApiReachability = checkApiReachability
        )
    }
}
