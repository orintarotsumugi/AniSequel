package com.anisequel.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveTabBar
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.screens.login.ClientIdDialog
import com.anisequel.ui.screens.login.LoginHero
import com.anisequel.ui.screens.login.ManualTokenDialog
import com.anisequel.ui.screens.login.OAuthCard
import com.anisequel.ui.screens.login.UsernameScanCard
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.viewmodel.AuthViewModel

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    authState: AuthUiState,
    onStartDemo: () -> Unit,
    onScanUsername: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // `rememberSaveable` for the tab, matching `SettingsScreen` - the one other
    // tabbed screen in the app. This one used a plain `remember`, so rotating the
    // phone mid-setup silently dropped the user back on the Sign In tab from
    // Scan, which is the tab a first-time user is most likely to be reading.
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showClientIdDialog by remember { mutableStateOf(false) }
    var showManualTokenDialog by remember { mutableStateOf(false) }
    // Hoisted out of `UsernameScanCard` so a typed username survives a tab
    // switch. The tabs live in an `AnimatedContent`, which disposes the tab it
    // animates away from - so the field was cleared the moment the user looked
    // at Sign In and came back. `rememberSaveable` also carries it across a
    // rotation.
    var scanUsername by rememberSaveable { mutableStateOf("") }
    val currentClientId by authViewModel.clientId.collectAsState()
    // Built once per client id rather than on every recomposition of the tab
    // content, which re-runs on every crossfade.
    val authUrl = remember(currentClientId) { authViewModel.getAuthorizationUrl() }
    val haptic = LocalHapticFeedback.current
    // Shared with `bouncyPress` below, so the press comes from the button's own
    // `clickable` - which cancels the moment the gesture is claimed as a scroll -
    // instead of from `bouncyPress`'s fallback pointer loop. The fallback springs
    // the button down on the first pointer-down and holds it there for the whole
    // drag, so starting a scroll with a finger on this button squashed it until
    // the finger lifted. This screen scrolls, so that was reachable on every
    // launch.
    val demoButtonSource = remember { MutableInteractionSource() }

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
                // The keyboard is the reason the Scan tab is usable at all, and
                // this column was laid out behind it: with no IME inset the field
                // the user just tapped stayed at its old y, the keyboard covered
                // it, and the Scan button underneath it fell off the scrollable
                // area's bottom - so tapping the field and typing left nothing to
                // submit with but the keyboard's own Enter key, which did
                // nothing. Applied after `paddingValues` rather than before so the
                // gradient still paints behind the status bar, and the already
                // paid navigation inset is consumed first so the IME does not add
                // its height on top of it.
                .consumeWindowInsets(paddingValues)
                .imePadding()
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
                    // Two tabs split evenly, so each gets about half the width -
                    // enough for "Sign In" but not quite for "Username Scan",
                    // which ellipsized. "Scan" says the same thing: this tab takes
                    // somebody else's public profile rather than an account.
                    tabs = listOf("Sign In", "Scan"),
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
                            authUrl = authUrl,
                            onRetry = authViewModel::dismissAuthError,
                            onShowManualToken = { showManualTokenDialog = true },
                            onShowClientId = { showClientIdDialog = true }
                        )
                    } else {
                        UsernameScanCard(
                            username = scanUsername,
                            onUsernameChange = { scanUsername = it },
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
                        .bouncyPress(pressedScale = 0.97f, interactionSource = demoButtonSource),
                    shape = MaterialTheme.shapes.small,
                    interactionSource = demoButtonSource
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Explore with sample data", fontWeight = FontWeight.SemiBold)
                }

                // What the demo button actually opens. "Sample data" on its own
                // reads like a test fixture, and the button used to be the last
                // thing on the screen with nothing under it - so a first-time
                // visitor had no way to tell whether it was a preview, a tutorial
                // or something that would sign them in somewhere.
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "No AniList account needed - it opens a sample profile, " +
                        "activity feed, followers and statistics so you can look around.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("explore_demo_caption")
                )
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
