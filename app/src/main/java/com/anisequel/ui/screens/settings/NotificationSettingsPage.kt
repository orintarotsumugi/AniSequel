package com.anisequel.ui.screens.settings

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.notifications.BatteryOptimisation
import com.anisequel.data.notifications.NotificationAlertStore
import com.anisequel.data.notifications.NotificationAlerts
import com.anisequel.data.notifications.NotificationAlertsScheduler
import com.anisequel.data.notifications.SystemNotifications
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.domain.notifications.NotificationAlertPreferences
import com.anisequel.domain.notifications.NotificationDeliveryState
import com.anisequel.domain.notifications.NotificationPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.screens.profile.formatRelativeSeconds
import java.time.Instant
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * Notification preferences, inside the existing Settings shelf.
 *
 * Three groups, kept deliberately separate because each one can be true while
 * the others are not:
 *
 *  1. **Inbox** - local read tracking and category visibility. These shape a
 *     screen the reader has already opened; they never post anything.
 *  2. **Background alerts** - the master switch, the categories Android may
 *     post, and the polling interval. This is what actually wakes the phone.
 *  3. **Android permission** - a platform state this app cannot change with a
 *     switch. It is a status plus buttons, never a toggle that flips back.
 *
 * The background feature is opt-in. Its switch starts off, and turning it on
 * asks Android for the runtime permission first so the switch cannot describe a
 * setting that will silently drop every post.
 */
@Composable
internal fun NotificationSettingsPage(
    preferencesStore: NotificationPreferencesStore,
    readStore: NotificationReadStore,
    alertStore: NotificationAlertStore,
    viewer: ViewerProfile?,
    /** Opens the inbox itself, the way back when its top-bar bell is hidden. */
    onOpenInbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val preferences by preferencesStore.preferences.collectAsStateWithLifecycle(
        initialValue = NotificationPreferences()
    )
    val alertPreferences by alertStore.preferences.collectAsStateWithLifecycle(
        initialValue = NotificationAlertPreferences()
    )
    val delivery by remember(viewer?.id) {
        viewer?.id?.let(alertStore::deliveryState) ?: flowOf(NotificationDeliveryState())
    }.collectAsStateWithLifecycle(initialValue = NotificationDeliveryState())

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var confirmReset by rememberSaveable(viewer?.id) { mutableStateOf(false) }
    var didReset by rememberSaveable(viewer?.id) { mutableStateOf(false) }

    val enabler = rememberAlertEnabler(
        context = context,
        alertStore = alertStore,
        enabled = alertPreferences.enabled,
        intervalMinutes = alertPreferences.intervalMinutes
    )
    val status by enabler.status

    // The platform owns this switch, so it is read again on every resume instead of
    // being remembered: the grant can arrive from the dialog *or* from a Settings
    // screen this app never sees, and a cached value would keep the card describing
    // a state the reader had already fixed.
    var batteryExempt by remember { mutableStateOf(BatteryOptimisation.isExempt(context)) }
    LifecycleResumeEffect(Unit) {
        batteryExempt = BatteryOptimisation.isExempt(context)
        onPauseOrDispose { }
    }

    SettingsScrollColumn(modifier = modifier) {
        SectionCard(
            title = "Inbox",
            icon = AppVectorIcons.MessageReceived,
            subtitle = "Unread dots are tracked on this device. AniList has no per-item read flag, so this is not a server read count."
        ) {
            SwitchRow(
                title = "Track unread locally",
                subtitle = "Show unread dots, the count pill and the New section. Turning this off keeps every row, without the unread state.",
                checked = preferences.trackingEnabled,
                onCheckedChange = { enabled -> scope.launch { preferencesStore.setTrackingEnabled(enabled) } },
                modifier = Modifier.testTag("notification_tracking_switch")
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Mark read when opened",
                subtitle = "Opening a card, its avatar or its title clears the unread dot for the whole folded group. A system alert tap follows this too.",
                checked = preferences.markReadOnOpen,
                enabled = preferences.trackingEnabled,
                onCheckedChange = { enabled -> scope.launch { preferencesStore.setMarkReadOnOpen(enabled) } },
                modifier = Modifier.testTag("notification_mark_read_switch")
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Reset local read history",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = if (viewer == null) {
                        "Sign in to reset your own read history. The demo has none to reset."
                    } else {
                        "Forget every read mark and baseline for this account. The next inbox load re-seeds which rows are new from the server count."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = { confirmReset = true },
                    enabled = viewer != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("notification_reset_read")
                ) {
                    Text("Reset read history")
                }
                if (didReset) {
                    Text(
                        text = "Read history reset. The next inbox load seeds a fresh baseline.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onOpenInbox,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("notification_open_inbox")
            ) {
                Text("Open notification centre")
            }
        }

        SectionCard(
            title = "Categories",
            icon = AppVectorIcons.Filter,
            subtitle = "Which events appear in the notification centre. These are local view filters; they do not change anything on AniList."
        ) {
            SwitchRow(
                title = "Likes",
                subtitle = "Likes on your activity, your replies and your thread comments.",
                checked = preferences.likes,
                onCheckedChange = { scope.launch { preferencesStore.setLikes(it) } }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Replies",
                subtitle = "Replies to your activity and to threads you follow.",
                checked = preferences.replies,
                onCheckedChange = { scope.launch { preferencesStore.setReplies(it) } }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Mentions",
                subtitle = "Activity posts and thread comments that name you.",
                checked = preferences.mentions,
                onCheckedChange = { scope.launch { preferencesStore.setMentions(it) } }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Messages",
                subtitle = "Messages AniList reports as notifications. The profile Messages tab stays the full message inbox.",
                checked = preferences.messages,
                onCheckedChange = { scope.launch { preferencesStore.setMessages(it) } }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Follows",
                subtitle = "People who start following you.",
                checked = preferences.follows,
                onCheckedChange = { scope.launch { preferencesStore.setFollows(it) } }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Airing",
                subtitle = "New episodes of anime you follow.",
                checked = preferences.airing,
                onCheckedChange = { scope.launch { preferencesStore.setAiring(it) } }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Media updates",
                subtitle = "Related titles, data changes, merges and deletions.",
                checked = preferences.mediaUpdates,
                onCheckedChange = { scope.launch { preferencesStore.setMediaUpdates(it) } }
            )
            Spacer(Modifier.height(8.dp))
            HorizontalDivider()
            Spacer(Modifier.height(8.dp))
            SwitchRow(
                title = "Submissions",
                subtitle = "Status updates on media submissions you sent to AniList.",
                checked = preferences.submissions,
                onCheckedChange = { scope.launch { preferencesStore.setSubmissions(it) } }
            )
        }

        BackgroundAlertsCard(
            enabled = alertPreferences.enabled,
            enabledForAccount = viewer != null,
            delivery = delivery,
            accountName = viewer?.name,
            intervalMinutes = alertPreferences.intervalMinutes,
            airing = alertPreferences.airing,
            likes = alertPreferences.likes,
            replies = alertPreferences.replies,
            mentions = alertPreferences.mentions,
            messages = alertPreferences.messages,
            follows = alertPreferences.follows,
            mediaUpdates = alertPreferences.mediaUpdates,
            submissions = alertPreferences.submissions,
            androidAllowed = status == SystemNotifications.ChannelStatus.ALLOWED,
            onToggleEnabled = { enabled ->
                if (enabled) {
                    enabler.enable()
                } else {
                    scope.launch {
                        alertStore.setEnabled(false)
                        // Stopping the work is not deleting the cursor: turning
                        // alerts back on resumes from the last delivered id.
                        NotificationAlertsScheduler.cancel(context)
                        NotificationAlerts.cancelAll(context)
                    }
                }
            },
            onInterval = { minutes -> scope.launch { alertStore.setIntervalMinutes(minutes) } },
            onAiring = { scope.launch { alertStore.setAiring(it) } },
            onLikes = { scope.launch { alertStore.setLikes(it) } },
            onReplies = { scope.launch { alertStore.setReplies(it) } },
            onMentions = { scope.launch { alertStore.setMentions(it) } },
            onMessages = { scope.launch { alertStore.setMessages(it) } },
            onFollows = { scope.launch { alertStore.setFollows(it) } },
            onMediaUpdates = { scope.launch { alertStore.setMediaUpdates(it) } },
            onSubmissions = { scope.launch { alertStore.setSubmissions(it) } },
            onCheckNow = {
                NotificationAlertsScheduler.checkNow(context)
                Toast.makeText(
                    context,
                    "Checking AniList now. New events will post as Android alerts.",
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        AndroidPermissionCard(
            status = status,
            canRequest = SystemNotifications.canRequestRuntimePermission(context),
            onAllow = enabler.allow,
            onOpenSettings = {
                if (!SystemNotifications.openSystemSettings(context)) {
                    Toast.makeText(
                        context,
                        "This device has no notification settings page.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onTest = {
                val sent = SystemNotifications.postTest(context)
                Toast.makeText(
                    context,
                    if (sent) {
                        "Test notification sent. This is a diagnostic, not a new event."
                    } else {
                        "Android is still blocking AniSequel notifications."
                    },
                    Toast.LENGTH_LONG
                ).show()
            }
        )

        BatteryOptimisationCard(
            exempt = batteryExempt,
            onRequest = {
                if (!BatteryOptimisation.request(context)) {
                    Toast.makeText(
                        context,
                        "This device has no battery settings page.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            },
            onOpenSettings = {
                if (!BatteryOptimisation.openBatterySettings(context)) {
                    Toast.makeText(
                        context,
                        "This device has no battery settings page.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        )
    }

    if (confirmReset && viewer != null) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset local read history?") },
            text = {
                Text(
                    "This clears every local read marker for ${viewer.name}. The next inbox " +
                        "load starts again from the server count. AniList itself is not changed."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmReset = false
                        scope.launch {
                            readStore.reset(viewer.id)
                            didReset = true
                        }
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * The permission side of the background-alert switch.
 *
 * Hoisted out of the page so the composable that draws the three cards is not
 * also the one that owns a launcher, a resume observer and a three-branch
 * permission decision. The status is returned as [State] rather than a value:
 * the caller reads it while composing the Android card, so a grant made in
 * system settings still recomposes the badge when the app returns.
 */
@Composable
private fun rememberAlertEnabler(
    context: Context,
    alertStore: NotificationAlertStore,
    enabled: Boolean,
    intervalMinutes: Int
): AlertEnablerState {
    val statusState = remember { mutableStateOf(SystemNotifications.channelStatus(context)) }
    var lastStatus by remember { mutableStateOf(statusState.value) }
    var enableAfterGrant by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val enableAlerts: () -> Unit = {
        scope.launch {
            alertStore.setEnabled(true)
            NotificationAlertsScheduler.ensureScheduled(context, intervalMinutes)
            // The first run establishes the silent baseline; it posts nothing,
            // which is what stops a history dump on enable.
            NotificationAlertsScheduler.checkNow(context)
        }
    }

    // Only an Activity result can tell us the dialog was answered; coming back
    // from the system settings page is an `onResume` with no callback at all.
    // Both paths refresh the badge from the platform, because the platform is
    // the only authority on a permission it owns.
    LifecycleResumeEffect(enabled) {
        val fresh = SystemNotifications.channelStatus(context)
        statusState.value = fresh
        if (lastStatus != SystemNotifications.ChannelStatus.ALLOWED &&
            fresh == SystemNotifications.ChannelStatus.ALLOWED &&
            enabled
        ) {
            // The reader granted the permission outside this screen after
            // enabling the feature. Catch up now rather than waiting a period.
            NotificationAlertsScheduler.checkNow(context)
        }
        lastStatus = fresh
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        statusState.value = SystemNotifications.channelStatus(context)
        if (granted) {
            if (enableAfterGrant) {
                enableAfterGrant = false
                enableAlerts()
            }
        } else {
            enableAfterGrant = false
            // Android reports `shouldShowRequestPermissionRationale` as false
            // both before the first request and after the reader has refused
            // twice. This callback only runs after a request, so false here is
            // the permanent state: launching the dialog again would do nothing,
            // and the old branch left the reader with a toast and no way in.
            val activity = context.findActivity()
            val canAskAgain = activity == null || ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.POST_NOTIFICATIONS
            )
            Toast.makeText(
                context,
                if (canAskAgain) {
                    "Notifications stay off. You can allow them later in system settings."
                } else {
                    "Android will not show the permission dialog again. Opening system settings."
                },
                Toast.LENGTH_LONG
            ).show()
            if (!canAskAgain) SystemNotifications.openSystemSettings(context)
        }
    }

    val enable: () -> Unit = {
        when (SystemNotifications.channelStatus(context)) {
            SystemNotifications.ChannelStatus.ALLOWED -> enableAlerts()

            SystemNotifications.ChannelStatus.PERMISSION_MISSING -> {
                // The status already says the permission is missing on API 33+;
                // there is no third state where a dialog must not be launched.
                // A permanently denied request is answered by the launcher's
                // callback below, which opens system settings instead.
                enableAfterGrant = true
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }

            else -> {
                Toast.makeText(
                    context,
                    "Android is blocking AniSequel alerts. Allow them in system settings first.",
                    Toast.LENGTH_LONG
                ).show()
                SystemNotifications.openSystemSettings(context)
            }
        }
    }

    return AlertEnablerState(
        status = statusState,
        enable = enable,
        allow = {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    )
}

private class AlertEnablerState(
    val status: State<SystemNotifications.ChannelStatus>,
    val enable: () -> Unit,
    val allow: () -> Unit
)

@Composable
private fun BackgroundAlertsCard(
    enabled: Boolean,
    enabledForAccount: Boolean,
    delivery: NotificationDeliveryState,
    accountName: String?,
    intervalMinutes: Int,
    airing: Boolean,
    likes: Boolean,
    replies: Boolean,
    mentions: Boolean,
    messages: Boolean,
    follows: Boolean,
    mediaUpdates: Boolean,
    submissions: Boolean,
    androidAllowed: Boolean,
    onToggleEnabled: (Boolean) -> Unit,
    onInterval: (Int) -> Unit,
    onAiring: (Boolean) -> Unit,
    onLikes: (Boolean) -> Unit,
    onReplies: (Boolean) -> Unit,
    onMentions: (Boolean) -> Unit,
    onMessages: (Boolean) -> Unit,
    onFollows: (Boolean) -> Unit,
    onMediaUpdates: (Boolean) -> Unit,
    onSubmissions: (Boolean) -> Unit,
    onCheckNow: () -> Unit
) {
    SectionCard(
        title = "Background alerts",
        icon = AppVectorIcons.Notifications,
        subtitle = "System notifications for new AniList events. Off by default; Android checks on a schedule while the app is closed.",
        badge = { StatusBadge(active = enabled, onLabel = "On", offLabel = "Off") }
    ) {
        SwitchRow(
            title = "Alert me in the background",
            subtitle = if (enabledForAccount) {
                "AniList has no push stream, so AniSequel asks for new activity periodically. Android may delay a check for battery saving, and a check needs a connection."
            } else {
                "Sign in to enable background alerts for your account."
            },
            checked = enabled,
            enabled = enabledForAccount,
            onCheckedChange = onToggleEnabled,
            modifier = Modifier.testTag("notification_background_master")
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Check every",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        if (enabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                NotificationAlertPreferences.INTERVAL_CHOICES.forEach { minutes ->
                    ExpressiveStateChip(
                        label = intervalLabel(minutes),
                        selected = intervalMinutes == minutes,
                        onClick = { onInterval(minutes) },
                        minTouchTargetHeight = 44.dp,
                        role = Role.RadioButton,
                        onClickLabel = "Check every ${intervalLabel(minutes)}"
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Android's minimum is 15 minutes. Longer intervals save battery and spend fewer AniList requests.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // The chips are hidden rather than drawn disabled: a control that
            // looks tappable and does nothing is the failure mode this page was
            // rewritten to remove. The interval is remembered either way.
            Text(
                text = "Turn background alerts on to choose a schedule. Android's minimum is 15 minutes.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        DeliveryStatusLine(
            enabled = enabled,
            accountName = accountName,
            delivery = delivery,
            androidAllowed = androidAllowed
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onCheckNow,
            enabled = enabled && enabledForAccount && androidAllowed,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notification_check_now")
        ) {
            Text("Check for new events now")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "This runs the same detection and delivery pipeline as the background check: " +
                "it establishes a silent baseline the first time, then posts only genuinely new events.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        HorizontalDivider()
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Events that may post",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Off categories never post, and their events still move the delivery cursor so turning one back on cannot unload a backlog.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Airing",
            subtitle = "A new episode of an anime you follow.",
            checked = airing,
            enabled = enabled,
            onCheckedChange = onAiring
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Activity likes",
            subtitle = "Likes on your activity, replies and thread comments.",
            checked = likes,
            enabled = enabled,
            onCheckedChange = onLikes
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Replies",
            subtitle = "Replies to your activity, or to a thread you follow.",
            checked = replies,
            enabled = enabled,
            onCheckedChange = onReplies
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Mentions",
            subtitle = "Activity posts and thread comments that name you.",
            checked = mentions,
            enabled = enabled,
            onCheckedChange = onMentions
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Messages",
            subtitle = "A message AniList reports as a notification.",
            checked = messages,
            enabled = enabled,
            onCheckedChange = onMessages
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Follows",
            subtitle = "A new follower.",
            checked = follows,
            enabled = enabled,
            onCheckedChange = onFollows
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Media updates",
            subtitle = "Related titles, data changes, merges and deletions.",
            checked = mediaUpdates,
            enabled = enabled,
            onCheckedChange = onMediaUpdates
        )
        Spacer(Modifier.height(8.dp))
        SwitchRow(
            title = "Submission updates",
            subtitle = "A result on a media submission you sent.",
            checked = submissions,
            enabled = enabled,
            onCheckedChange = onSubmissions
        )
    }
}

@Composable
private fun DeliveryStatusLine(
    enabled: Boolean,
    accountName: String?,
    delivery: NotificationDeliveryState,
    androidAllowed: Boolean
) {
    val text = when {
        !enabled -> "Background alerts are off."
        accountName == null -> "Sign in to start checking."
        !androidAllowed -> "Android is blocking alerts, so checks wait without advancing the cursor."
        !delivery.baselineEstablished -> "No check has finished yet. The first one starts a silent baseline."
        delivery.lastCheckedAtMillis == null -> "No check has finished yet for $accountName."
        else -> {
            val relative = formatRelativeSeconds(
                (delivery.lastCheckedAtMillis / 1000L).toInt(),
                Instant.now()
            )
            val alerts = when (delivery.lastAlertCount) {
                0 -> "no new alerts"
                1 -> "1 alert"
                else -> "${delivery.lastAlertCount} alerts"
            }
            "Last checked $relative on $accountName: $alerts."
        }
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.primary
    )
}

@Composable
private fun AndroidPermissionCard(
    status: SystemNotifications.ChannelStatus,
    canRequest: Boolean,
    onAllow: () -> Unit,
    onOpenSettings: () -> Unit,
    onTest: () -> Unit
) {
    val allowed = status == SystemNotifications.ChannelStatus.ALLOWED
    SectionCard(
        title = "Android notifications",
        icon = AppVectorIcons.Notifications,
        subtitle = "The platform permission and channel. Separate from the app switches above: Android owns these, so this card reports them instead of pretending to toggle them.",
        badge = { StatusBadge(active = allowed, onLabel = "Allowed", offLabel = "Off") }
    ) {
        Text(
            text = permissionTitle(status),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = permissionDetail(status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRequest) {
                Button(
                    onClick = onAllow,
                    modifier = Modifier.testTag("notification_permission_allow")
                ) {
                    Text("Allow notifications")
                }
            }
            if (allowed) {
                Button(
                    onClick = onTest,
                    modifier = Modifier.testTag("notification_permission_test")
                ) {
                    Text("Send test")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier.testTag("notification_permission_settings")
        ) {
            Text("Open system settings")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "The test post exists to prove the permission and channel work. It is not an event alert.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** A capsule status chip for a settings card's on/off state. */
@Composable
internal fun StatusBadge(active: Boolean, onLabel: String, offLabel: String) {
    Surface(
        shape = CircleShape,
        color = if (active) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Text(
            text = if (active) onLabel else offLabel,
            style = MaterialTheme.typography.labelMedium,
            color = if (active) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

private fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

private fun permissionTitle(status: SystemNotifications.ChannelStatus): String = when (status) {
    SystemNotifications.ChannelStatus.ALLOWED -> "Android can show AniSequel alerts."
    SystemNotifications.ChannelStatus.PERMISSION_MISSING -> "The notification permission has not been granted."
    SystemNotifications.ChannelStatus.APP_BLOCKED -> "Android is blocking AniSequel notifications."
    SystemNotifications.ChannelStatus.CHANNEL_BLOCKED -> "The AniSequel alert channel is silenced."
}

private fun permissionDetail(status: SystemNotifications.ChannelStatus): String = when (status) {
    SystemNotifications.ChannelStatus.ALLOWED ->
        "The channel is on. You can still silence it from the system page; background checks also need a connection and may be delayed by battery optimisation."
    SystemNotifications.ChannelStatus.PERMISSION_MISSING ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            "Android 13 and later requires this runtime permission. Ask here, or use the system page if the dialog no longer appears."
        } else {
            "This Android version does not use a runtime permission. Allow AniSequel in the system page instead."
        }
    SystemNotifications.ChannelStatus.APP_BLOCKED ->
        "The runtime permission may be granted, but the app-wide switch is off. Open system settings and turn AniSequel notifications back on."
    SystemNotifications.ChannelStatus.CHANNEL_BLOCKED ->
        "The app is allowed, but the \"AniSequel alerts\" channel has been set to Silent or Off. Open the system page and raise it back to Default."
}

private fun intervalLabel(minutes: Int): String = when {
    minutes < 60 -> "$minutes min"
    minutes % 60 == 0 -> "${minutes / 60} h"
    else -> "$minutes min"
}
