package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anisequel.data.discord.DiscordPresenceCommands
import com.anisequel.data.discord.DiscordPresenceRuntime
import com.anisequel.data.discord.DiscordRpcClient
import com.anisequel.data.discord.DiscordRuntimeState
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.repository.DiscordPreferences
import com.anisequel.domain.discord.DiscordActivity
import com.anisequel.domain.discord.DiscordActivityKind
import com.anisequel.domain.discord.DiscordPresence
import com.anisequel.domain.discord.DiscordPresenceComposer
import com.anisequel.domain.discord.DiscordSettings
import com.anisequel.domain.discord.DiscordStats
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.discord.DiscordActivityBus
import com.anisequel.ui.components.discord.DiscordPresenceCard
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DiscordRpcTab(
    preferences: DiscordPreferences,
    stats: DiscordStats,
    previewEntries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    // One collection, not two: the page used to open a second reader just to learn
    // that the first had emitted, so a single preference had two DataStore
    // subscribers and the loader could disagree with the content by a frame.
    val storedSettings by preferences.settings.collectAsState(initial = null)
    val loaded = storedSettings != null
    val settings = storedSettings ?: DiscordSettings()
    val runtime by DiscordPresenceRuntime.state.collectAsState()
    val enginePresence by DiscordPresenceRuntime.presence.collectAsState()
    val liveActivity by DiscordActivityBus.activity.collectAsState()
    var previewOverride by remember { mutableStateOf<DiscordActivity?>(null) }
    // The dock's own height, measured. The clearance under the scroll used to be a
    // guessed 320dp, which was short as soon as a long state line or two buttons
    // made the card taller - the same class of bug the Info page had, where the
    // last card could not be scrolled out from under a floating bar.
    var dockHeightPx by remember { mutableStateOf(0) }
    val dockHeight = with(LocalDensity.current) { dockHeightPx.toDp() }
    var editingKind by remember { mutableStateOf<DiscordActivityKind?>(null) }
    var showLogin by remember { mutableStateOf(false) }
    var showAddJoke by remember { mutableStateOf(false) }

    // Shared with the app-root publisher, so "Log out" below closes the socket
    // that is actually carrying the presence. A client built here used to be
    // disconnected while the publisher's own session kept running.
    val client = DiscordRpcClient.shared
    val previewActivity = previewOverride ?: liveActivity
    val previewPresence = remember(settings, previewActivity, stats, enginePresence?.joke) {
        DiscordPresenceComposer.compose(
            settings = settings,
            activity = previewActivity,
            stats = stats,
            joke = enginePresence?.joke
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (!loaded) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 3.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Loading Discord settings…",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Box
        }

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
                    .padding(horizontal = 20.dp)
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DiscordAccountCard(
                    settings = settings,
                    runtime = runtime,
                    onToggle = { enabled -> scope.launch { preferences.setEnabled(enabled) } },
                    onLogin = { showLogin = true },
                    onLogout = {
                        scope.launch {
                            preferences.unlink()
                            client.disconnect()
                        }
                    }
                )

                GeneralAndPrivacyCard(
                    settings = settings,
                    scope = scope,
                    preferences = preferences
                )

                DiscordCustomizationCard(
                    settings = settings,
                    scope = scope,
                    preferences = preferences
                )

                QuickActionsCard(
                    settings = settings,
                    previewEntries = previewEntries,
                    previewOverride = previewOverride,
                    onPreview = { previewOverride = it },
                    onNewJoke = { DiscordPresenceCommands.requestNewJoke() },
                    onToggleEnabled = { enabled -> scope.launch { preferences.setEnabled(enabled) } }
                )

                DiscordPhrasingCard(
                    settings = settings,
                    onEdit = { editingKind = it }
                )

                DiscordJokesCard(
                    settings = settings,
                    currentJoke = enginePresence?.joke,
                    scope = scope,
                    preferences = preferences,
                    onAdd = { showAddJoke = true }
                )

                DiscordConnectionCard(
                    settings = settings,
                    runtime = runtime,
                    onLogin = { showLogin = true },
                    onLogout = {
                        scope.launch {
                            preferences.unlink()
                            client.disconnect()
                        }
                    }
                )

                if (!settings.previewPinned) {
                    SettingsButton(
                        text = "Show the live preview",
                        onClick = { scope.launch { preferences.setPreviewPinned(true) } },
                        fillWidth = true,
                        icon = AppVectorIcons.Visibility
                    )
                }

                Spacer(
                    modifier = Modifier.height(
                        if (settings.previewPinned) {
                            if (dockHeightPx > 0) dockHeight + 24.dp else 320.dp
                        } else {
                            24.dp
                        }
                    )
                )
            }
        }

        AnimatedVisibility(
            visible = settings.previewPinned,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            DiscordPreviewDock(
                presence = previewPresence,
                settings = settings,
                runtime = runtime,
                previewing = previewOverride != null,
                previewActivity = previewActivity,
                onHide = { scope.launch { preferences.setPreviewPinned(false) } },
                modifier = Modifier.onSizeChanged { dockHeightPx = it.height }
            )
        }
    }

    editingKind?.let { kind ->
        DiscordPhrasingDialog(
            kind = kind,
            phrasing = settings.phrasingFor(kind),
            onDismiss = { editingKind = null },
            onSave = { phrasing ->
                scope.launch { preferences.setTemplate(kind, phrasing) }
                editingKind = null
            },
            onReset = {
                scope.launch { preferences.resetTemplate(kind) }
                editingKind = null
            }
        )
    }

    if (showAddJoke) {
        DiscordAddJokeDialog(
            onDismiss = { showAddJoke = false },
            onAdd = { line ->
                scope.launch { preferences.addCustomJoke(line) }
                showAddJoke = false
            }
        )
    }

    if (showLogin) {
        DiscordLoginDialog(
            settings = settings,
            client = client,
            onDismiss = { showLogin = false },
            onLinked = { token, account ->
                scope.launch {
                    preferences.link(token, account.username, account.userId, account.avatarUrl)
                    if (!settings.enabled) preferences.setEnabled(true)
                    showLogin = false
                }
            }
        )
    }
}

@Composable
private fun DiscordAccountCard(
    settings: DiscordSettings,
    runtime: DiscordRuntimeState,
    onToggle: (Boolean) -> Unit,
    onLogin: () -> Unit,
    onLogout: () -> Unit
) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF2B2D31), Color(0xFF1B1C20))))
            .padding(18.dp)
            .testTag("discord_account_card")
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(Brush.radialGradient(listOf(Color(0xFF5865F2), Color(0xFF3A44C9)))),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppVectorIcons.Discord,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(30.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = settings.username.takeIf { it.isNotBlank() } ?: "Discord",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(discordStatusColor(settings, runtime))
                    )
                    Spacer(modifier = Modifier.width(7.dp))
                    Text(
                        text = settings.connectionLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFC7CBD1),
                        maxLines = 2
                    )
                }
            }
            IconButton(
                onClick = { onToggle(!settings.enabled) },
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .testTag("discord_power")
            ) {
                Icon(
                    imageVector = Icons.Default.PowerSettingsNew,
                    contentDescription = if (settings.enabled) "Turn Discord Rich Presence off" else "Turn Discord Rich Presence on",
                    tint = if (settings.enabled) Color(0xFF57F287) else Color(0xFFFFB4AB)
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Connect with your own Discord token. Your presence is sent straight to Discord with no relay server.",
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF9FA4AC)
        )
        if (runtime is DiscordRuntimeState.Failed) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = runtime.message,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFFFB4AB)
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (settings.linked) {
                androidx.compose.material3.TextButton(onClick = onLogout) { Text("Log out") }
            }
            androidx.compose.material3.TextButton(
                onClick = onLogin,
                modifier = Modifier.testTag("discord_login_button")
            ) {
                Text(if (settings.linked) "Change account" else "Log in")
            }
        }
    }
}

@Composable
private fun GeneralAndPrivacyCard(
    settings: DiscordSettings,
    scope: kotlinx.coroutines.CoroutineScope,
    preferences: DiscordPreferences
) {
    SectionCard(
        title = "General & privacy",
        icon = AppVectorIcons.Discord,
        subtitle = "What a friend sees and what leaves this phone",
        iconContainer = Color(0xFF5865F2),
        iconContent = Color.White
    ) {
        SwitchRow(
            title = "Enable Discord RPC",
            subtitle = "Broadcast your activity directly to Discord.",
            checked = settings.enabled,
            onCheckedChange = { scope.launch { preferences.setEnabled(it) } },
            modifier = Modifier.testTag("discord_enabled_switch")
        )
        SwitchRow(
            title = "Show the page or title I am on",
            subtitle = "Off leaves the stats and the joke, without naming what you're reading.",
            checked = settings.showActivity,
            onCheckedChange = { scope.launch { preferences.setShowActivity(it) } }
        )
        SwitchRow(
            title = "Show cover art",
            subtitle = "The artwork of the title you have open.",
            checked = settings.showCover,
            onCheckedChange = { scope.launch { preferences.setShowCover(it) } }
        )
        SwitchRow(
            title = "Show elapsed time",
            subtitle = "Display Discord's live timer for the current activity.",
            checked = settings.showElapsedTime,
            onCheckedChange = { scope.launch { preferences.setShowElapsedTime(it) } }
        )
        SwitchRow(
            title = "Show my stats",
            subtitle = "Completed anime, episodes, manga, chapters and sequels found.",
            checked = settings.showStats,
            onCheckedChange = { scope.launch { preferences.setShowStats(it) } }
        )
        SwitchRow(
            title = "Show buttons",
            subtitle = "Links on the presence card, including a title link when available.",
            checked = settings.showButtons,
            onCheckedChange = { scope.launch { preferences.setShowButtons(it) } }
        )
        SwitchRow(
            title = "Hide titles",
            subtitle = "Replace titles with a generic label and remove cover/title links.",
            checked = settings.hideTitles,
            onCheckedChange = { scope.launch { preferences.setHideTitles(it) } }
        )
        SwitchRow(
            title = "Pin the live preview",
            subtitle = "Keep the preview dock visible while you edit settings.",
            checked = settings.previewPinned,
            onCheckedChange = { scope.launch { preferences.setPreviewPinned(it) } }
        )
        SwitchRow(
            title = "Reduce network chatter",
            subtitle = "Debounce rapid edits for smoother, lighter presence updates.",
            checked = settings.reduceNetworkChatter,
            onCheckedChange = { scope.launch { preferences.setReduceNetworkChatter(it) } }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickActionsCard(
    settings: DiscordSettings,
    previewEntries: List<MediaListEntryItem>,
    previewOverride: DiscordActivity?,
    onPreview: (DiscordActivity?) -> Unit,
    onNewJoke: () -> Unit,
    onToggleEnabled: (Boolean) -> Unit
) {
    SectionCard(
        title = "Quick actions",
        icon = AppVectorIcons.Play,
        subtitle = "Try the card and control publishing quickly"
    ) {
        Text(
            text = "Preview as",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            QuickPreviewChip("Live", previewOverride == null, "discord_preview_live") { onPreview(null) }
            DiscordActivityKind.phrasingOrder
                .filter {
                    it != DiscordActivityKind.VIEWING_MANGA_DETAILS &&
                        it != DiscordActivityKind.READING_NOVEL &&
                        it != DiscordActivityKind.VIEWING_NOVEL_DETAILS
                }
                .forEach { kind ->
                    QuickPreviewChip(
                        label = when (kind) {
                            DiscordActivityKind.WATCHING_ANIME -> "Watching"
                            DiscordActivityKind.VIEWING_ANIME_DETAILS -> "Details"
                            DiscordActivityKind.READING_MANGA -> "Manga"
                            DiscordActivityKind.BROWSING -> "Idle"
                            else -> kind.displayName
                        },
                        selected = previewOverride?.kind == kind,
                        testTag = "discord_preview_${kind.storageValue}"
                    ) {
                        onPreview(sampleActivity(kind, System.currentTimeMillis(), previewEntries))
                    }
                }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = if (previewOverride == null) {
                "Showing your live activity."
            } else {
                "Previewing \"${previewOverride.kind.displayName}\". Your live activity can still publish while you test."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SettingsButton(
                text = "New joke",
                onClick = onNewJoke,
                icon = AppVectorIcons.Refresh,
                enabled = settings.jokesScheduled,
                modifier = Modifier.weight(1f),
                testTag = "discord_new_joke"
            )
            SettingsButton(
                text = if (settings.enabled) "Pause" else "Resume",
                onClick = { onToggleEnabled(!settings.enabled) },
                icon = if (settings.enabled) AppVectorIcons.Pause else AppVectorIcons.Play,
                modifier = Modifier.weight(1f),
                testTag = "discord_pause_resume"
            )
        }
    }
}

@Composable
private fun QuickPreviewChip(
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    JokeChoiceChip(label = label, selected = selected, enabled = true, onClick = onClick, testTag = testTag)
}

@Composable
private fun DiscordPreviewDock(
    presence: DiscordPresence,
    settings: DiscordSettings,
    runtime: DiscordRuntimeState,
    previewing: Boolean,
    previewActivity: DiscordActivity,
    onHide: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.background.copy(alpha = 0.97f),
        tonalElevation = 4.dp,
        shadowElevation = 10.dp
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = MaxSettingsContentWidth)
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "LIVE PREVIEW",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.4.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).testTag("discord_preview_label")
                )
                Text(
                    text = when {
                        previewing -> "Preview only"
                        runtime is DiscordRuntimeState.Live -> "Live"
                        runtime is DiscordRuntimeState.Connecting -> "Connecting…"
                        runtime is DiscordRuntimeState.Failed -> "Offline"
                        settings.enabled -> "Preview only"
                        else -> "Off"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = discordStatusColor(settings, runtime)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Hide",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onHide).padding(4.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            DiscordPresenceCard(
                presence = presence,
                compact = false,
                live = runtime is DiscordRuntimeState.Live && !previewing,
                connecting = runtime is DiscordRuntimeState.Connecting && !previewing
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = if (previewActivity.kind == DiscordActivityKind.BROWSING) {
                    "Browse activity · ${previewActivity.status.orEmpty()}"
                } else {
                    previewActivity.kind.displayName
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )
        }
    }
}
