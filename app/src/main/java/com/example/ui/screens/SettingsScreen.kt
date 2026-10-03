package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import com.example.data.model.ViewerProfile
import com.example.data.repository.ThemePreferences
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveTabBar
import com.example.ui.screens.settings.EditSettingsTab
import com.example.ui.screens.settings.InfoSettingsTab
import com.example.ui.viewmodel.AuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    authViewModel: AuthViewModel,
    themePreferences: ThemePreferences,
    viewer: ViewerProfile?,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit = {},
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
                            modifier = Modifier.testTag("settings_back_button")
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
                    tabs = listOf("Info", "Edit"),
                    selectedIndex = selectedTab,
                    onSelect = { selectedTab = it },
                    // "Info"/"Edit" as bare words left the reader guessing which
                    // was which; the icons say it before the labels are read.
                    icons = listOf(AppVectorIcons.Info, AppVectorIcons.Tune),
                    modifier = Modifier.testTag("settings_tabs")
                )
            }
        }
    ) { paddingValues ->
        key(selectedTab) {
            when (selectedTab) {
                1 -> EditSettingsTab(
                    authViewModel = authViewModel,
                    themePreferences = themePreferences,
                    onNavigateBack = onNavigateBack,
                    onNavigateToLogin = onNavigateToLogin,
                    modifier = Modifier.padding(paddingValues)
                )
                else -> InfoSettingsTab(
                    viewer = viewer,
                    onNavigateBack = onNavigateBack,
                    onNavigateToLogin = onNavigateToLogin,
                    modifier = Modifier.padding(paddingValues)
                )
            }
        }
    }
}
