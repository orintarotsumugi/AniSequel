package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.model.FollowUser
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.ExpressiveNavigationBar
import com.example.ui.components.expressive.NavigationDestination
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.openExternalUrl
import com.example.ui.viewmodel.UserActivityViewModel
import com.example.ui.viewmodel.UserOverviewUiState
import com.example.ui.viewmodel.UserOverviewViewModel
import com.example.ui.viewmodel.UserSocialViewModel

/**
 * How much room a tab's list leaves for the floating navigation capsule.
 *
 * A shared constant because each tab would otherwise guess it, and a guess that
 * is slightly short is not visibly wrong - the last item just ends up behind the
 * bar until the user scrolls again. The capsule is 52dp of item plus 5dp of
 * padding either side plus 12dp of margin, so 96dp clears it with the gesture
 * inset on top.
 */
internal val NavigationBarClearance = 96.dp

/**
 * The padding every tab's scrollable content uses.
 *
 * The bottom is *not* the Scaffold's. The navigation capsule is meant to float
 * over the content rather than push it, so the Scaffold's bottom inset is
 * replaced with [NavigationBarClearance].
 */
internal fun profileListPadding(contentPadding: PaddingValues = PaddingValues()): PaddingValues = PaddingValues(
    top = contentPadding.calculateTopPadding() + 8.dp,
    bottom = NavigationBarClearance
)

/**
 * The four destinations, declared once so the bar and the `when` cannot disagree.
 */
private val ProfileTabs = listOf(
    NavigationDestination("Home", AppVectorIcons.ProfileHome, "profile_tab_home"),
    NavigationDestination("Activity", AppVectorIcons.ProfileActivity, "profile_tab_activity"),
    NavigationDestination("Stats", AppVectorIcons.ProfileStats, "profile_tab_stats"),
    NavigationDestination("Social", AppVectorIcons.ProfileSocial, "profile_tab_social")
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserProfileScreen(
    overviewViewModel: UserOverviewViewModel,
    activityViewModel: UserActivityViewModel,
    socialViewModel: UserSocialViewModel,
    onOpenUser: (FollowUser) -> Unit,
    onNavigateBack: () -> Unit,
    onSignInAgain: () -> Unit = {},
    /**
     * The tab the screen opens on.
     *
     * An argument rather than only screen state because the profile can be
     * opened *for* a tab: the home-screen widget opens the Activity feed, and a
     * screen that always started on Home would land the user one tap short of
     * what they asked for. Only the first composition reads it - after that the
     * user's own taps own the selection.
     */
    initialTab: Int = 0,
    modifier: Modifier = Modifier
) {
    val uiState by overviewViewModel.uiState.collectAsStateWithLifecycle()
    val activityState by activityViewModel.state.collectAsStateWithLifecycle()
    val socialState by socialViewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var selectedTab by rememberSaveable { mutableIntStateOf(initialTab) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("profile_screen"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = (uiState as? UserOverviewUiState.Success)
                            ?.username
                            ?.let { "@$it" }
                            ?: "AniList",
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onNavigateBack,
                        modifier = Modifier
                            .testTag("profile_back_button")
                            .bouncyPress(pressedScale = 0.9f)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                actions = {
                    val siteUrl = (uiState as? UserOverviewUiState.Success)?.siteUrl
                    if (siteUrl != null) {
                        IconButton(
                            onClick = { openExternalUrl(context, siteUrl) },
                            modifier = Modifier
                                .testTag("profile_open_anilist_button")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.OpenInNew,
                                contentDescription = "Open on AniList"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = paddingValues.calculateTopPadding())
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.TopCenter
        ) {
            when (val state = uiState) {
                is UserOverviewUiState.Loading -> ProfileLoadingView(message = state.message)
                is UserOverviewUiState.Error -> ProfileErrorView(
                    message = state.message,
                    canRetry = state.canRetry,
                    isAuthError = state.isAuthError,
                    onRetry = { overviewViewModel.load() },
                    onSignInAgain = onSignInAgain
                )
                is UserOverviewUiState.Success -> {
                    LaunchedEffect(state.userId) {
                        if (state.userId > 0) {
                            activityViewModel.updateUserId(state.userId)
                            socialViewModel.updateUserId(state.userId)
                        }
                    }
                    ProfileTabsContent(
                        state = state,
                        selectedTab = selectedTab,
                        activityState = activityState,
                        activityViewModel = activityViewModel,
                        socialState = socialState,
                        socialViewModel = socialViewModel,
                        onOpenUser = onOpenUser
                    )
                    ExpressiveNavigationBar(
                        destinations = ProfileTabs,
                        selectedIndex = selectedTab,
                        onSelect = { selectedTab = it },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .windowInsetsPadding(WindowInsets.navigationBars)
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileTabsContent(
    state: UserOverviewUiState.Success,
    selectedTab: Int,
    activityState: UserActivityViewModel.ActivityState,
    activityViewModel: UserActivityViewModel,
    socialState: UserSocialViewModel.SocialState,
    socialViewModel: UserSocialViewModel,
    onOpenUser: (FollowUser) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        if (state.isRefreshing) {
            ProfileRefreshBar()
        }

        // `key(selectedTab)` so each tab's scrollable content is a new one and
        // starts at the top.
        key(selectedTab) {
            val padding = PaddingValues(top = 0.dp)
            when (selectedTab) {
                1 -> ProfileActivityTab(
                    activityState = activityState,
                    onLoadFirstPage = { activityViewModel.loadFirstPage() },
                    onLoadNextPage = { activityViewModel.loadNextPage() },
                    onRetry = { activityViewModel.retry() },
                    contentPadding = padding,
                    calendar = state.activity
                )

                2 -> ProfileStatsTab(state = state, contentPadding = padding)

                3 -> ProfileSocialTab(
                    socialState = socialState,
                    onSelectList = { list ->
                        socialViewModel.select(list)
                    },
                    onLoad = { list ->
                        socialViewModel.load(list)
                    },
                    onOpenUser = onOpenUser,
                    contentPadding = padding
                )

                else -> ProfileHomeTab(state = state, contentPadding = padding)
            }
        }
    }
}

@Composable
private fun ProfileRefreshBar() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_refreshing"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(6.dp))
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(20.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(6.dp))
    }
}
