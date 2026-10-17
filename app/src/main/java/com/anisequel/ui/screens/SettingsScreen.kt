package com.anisequel.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.repository.ArrivingPreferences
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveTabBar
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.screens.settings.EditSettingsTab
import com.anisequel.ui.screens.settings.InfoSettingsTab
import com.anisequel.ui.screens.settings.ThemeTab
import com.anisequel.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences,
    arrivingPreferences: ArrivingPreferences,
    quickAddPreferences: com.anisequel.data.repository.QuickAddPreferences,
    changelogRepository: ChangelogRepository,
    viewer: ViewerProfile?,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
    totalWatchedCount: Int? = null,
    totalMissedCount: Int? = null,
    modifier: Modifier = Modifier
) {
    BackHandler { onNavigateBack() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Scaffold(
        modifier = modifier.fillMaxSize().testTag("settings_screen"),
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Settings", fontWeight = FontWeight.SemiBold) },
                    navigationIcon = {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier
                                .testTag("settings_back_button")
                                .bouncyPress(pressedScale = 0.9f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )

                HorizontalDivider()
                ExpressiveTabBar(
                    tabs = listOf("Info", "Theme", "Edit"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    // "Info"/"Edit" as bare words left the reader guessing which
                    // was which; the icons say it before the labels are read.
                    icons = listOf(AppVectorIcons.Info, AppVectorIcons.SectionAppearance, AppVectorIcons.Tune),
                    modifier = Modifier.testTag("settings_tabs")
                )
            }
        }
    ) { paddingValues ->
        // The tab body used to be a bare `when` inside `key(selectedTab)`: the old tab
        // was gone and the new one was fully drawn on the same frame, so a tap on the
        // tab bar appeared to do nothing until the eye caught the different heading. All
        // three tabs now cross over on the shared spring tokens.
        //
        // Fade *and scale*, not a horizontal slide: a slide would run a layout-phase
        // offset on two scrollable columns for every frame of the transition, and these
        // three tabs are siblings rather than pages of a sequence, so a direction would
        // be inventing an order the tab bar does not have. `scaleIn`/`scaleOut` are
        // `graphicsLayer` transforms, which is the draw phase the motion rules ask for.
        //
        // `AnimatedContent` keys its composition per target, which is what `key()` was
        // doing: switching tabs must not carry one tab's remembered state into another.
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = {
                // No parentheses around the two halves: `+` binds tighter than the
                // `togetherWith` infix function, so this parses as
                // `(fadeIn + scaleIn) togetherWith (fadeOut + scaleOut)` and detekt
                // flags the brackets as noise (which is what CodeFactor caught here).
                fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleIn(
                        animationSpec = ExpressiveMotion.FastSpatial,
                        initialScale = 0.94f
                    ) togetherWith
                    fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                    scaleOut(
                        animationSpec = ExpressiveMotion.FastSpatial,
                        targetScale = 1.04f
                    )
            },
            label = "settings_tab_transition",
            modifier = Modifier
                .fillMaxSize()
                .testTag("settings_tab_content")
        ) { tab ->
            when (tab) {
                2 -> EditSettingsTab(
                    authViewModel = authViewModel,
                    refreshIntervalPreferences = refreshIntervalPreferences,
                    arrivingPreferences = arrivingPreferences,
                    quickAddPreferences = quickAddPreferences,
                    onNavigateToLogin = onNavigateToLogin,
                    modifier = Modifier.padding(paddingValues)
                )
                1 -> ThemeTab(
                    themePreferences = themePreferences,
                    modifier = Modifier.padding(paddingValues)
                )
                else -> InfoSettingsTab(
                    viewer = viewer,
                    onNavigateToLogin = onNavigateToLogin,
                    onSignOut = authViewModel::logout,
                    totalWatchedCount = totalWatchedCount,
                    totalMissedCount = totalMissedCount,
                    changelogRepository = changelogRepository,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
