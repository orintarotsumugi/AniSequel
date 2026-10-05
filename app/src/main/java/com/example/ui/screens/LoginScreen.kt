package com.example.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveTabBar
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.screens.login.ClientIdDialog
import com.example.ui.screens.login.LoginHero
import com.example.ui.screens.login.ManualTokenDialog
import com.example.ui.screens.login.OAuthCard
import com.example.ui.screens.login.UsernameScanCard
import com.example.ui.viewmodel.AuthUiState
import com.example.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    authState: AuthUiState,
    onStartDemo: () -> Unit,
    onScanUsername: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var showManualTokenDialog by remember { mutableStateOf(false) }
    val currentClientId by authViewModel.clientId.collectAsState()
    val haptic = LocalHapticFeedback.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("login_screen")
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.background
                        )
                    )
                )
                .padding(paddingValues)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(modifier = Modifier.height(16.dp))

                LoginHero()

                Spacer(modifier = Modifier.height(24.dp))

                ExpressiveTabBar(
                    tabs = listOf("Sign In", "Username Scan"),
                    selectedIndex = selectedTab,
                    onSelect = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        selectedTab = it
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(20.dp))

                AnimatedContent(
                    targetState = selectedTab,
                    transitionSpec = {
                        fadeIn(ExpressiveMotion.FastEffects) togetherWith
                            fadeOut(ExpressiveMotion.FastEffects)
                    },
                    label = "login_tab_content"
                ) { tab ->
                    if (tab == 0) {
                        OAuthCard(
                            authState = authState,
                            authUrl = authViewModel.getAuthorizationUrl(),
                            onRetry = authViewModel::dismissAuthError,
                            onShowManualToken = { showManualTokenDialog = true },
                            onShowClientId = { showClientIdDialog = true }
                        )
                    } else {
                        UsernameScanCard(
                            onScanUsername = onScanUsername
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Instant Demo Preview Button
                OutlinedButton(
                    onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onStartDemo()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("explore_demo_button")
                        .bouncyPress(pressedScale = 0.97f),
                    shape = MaterialTheme.shapes.small
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explore with sample data", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }

    if (showClientIdDialog) {
        ClientIdDialog(
            currentClientId = currentClientId,
            onSave = { authViewModel.updateClientId(it) },
            onDismiss = { showClientIdDialog = false }
        )
    }

    if (showManualTokenDialog) {
        ManualTokenDialog(
            onSave = { authViewModel.saveToken(it) },
            onDismiss = { showManualTokenDialog = false }
        )
    }
}
