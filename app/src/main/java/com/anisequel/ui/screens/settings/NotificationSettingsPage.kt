package com.anisequel.ui.screens.settings

import android.Manifest
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.notifications.SystemNotifications
import com.anisequel.data.repository.NotificationPreferencesStore
import com.anisequel.data.repository.NotificationReadStore
import com.anisequel.domain.notifications.NotificationPreferences
import com.anisequel.ui.components.AppVectorIcons
import kotlinx.coroutines.launch

/**
 * Notification preferences, inside the existing Settings shelf.
 *
 * The three groups are deliberately separate: Inbox is about this app local
 * read tracking, Activity about the social inbox, Media about title events. The
 * switches hide rows from the notification centre; they do not dismiss anything
 * on AniList, and they are not Android notification permission.
 */
@Composable
internal fun NotificationSettingsPage(
    preferencesStore: NotificationPreferencesStore,
    readStore: NotificationReadStore,
    viewer: ViewerProfile?,
    /** Opens the inbox itself, the way back when its top-bar bell is hidden. */
    onOpenInbox: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val preferences by preferencesStore.preferences.collectAsStateWithLifecycle(
        initialValue = NotificationPreferences()
    )
    val scope = rememberCoroutineScope()
    // Keyed by account: a confirmation must not follow the reader into a
    // different profile after switching accounts.
    var confirmReset by rememberSaveable(viewer?.id) { mutableStateOf(false) }
    var didReset by rememberSaveable(viewer?.id) { mutableStateOf(false) }

    SettingsScrollColumn(modifier = modifier) {
        NotificationPermissionSection()

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
                subtitle = "Opening a card, its avatar or its title clears the unread dot for the whole folded group.",
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
            title = "Activity",
            icon = AppVectorIcons.ProfileSocial,
            subtitle = "Which social events appear in the notification centre."
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
        }

        SectionCard(
            title = "Media",
            icon = AppVectorIcons.Tv,
            subtitle = "Title events, separate from social activity."
) {
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

        SectionCard(
            title = "Inbox refresh",
            icon = AppVectorIcons.MessageReceived,
            subtitle = "How the inbox decides between the answer it already has and a new request."
        ) {
            Text(
                text = "Opening the inbox reuses the last answer while it is still fresh, then " +
                    "updates it in place without dropping pages you already loaded. Pull down, or " +
                    "use Refresh, to ask AniList now: that starts a new snapshot from page one, so " +
                    "older pages are fetched again as you scroll.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "There is no background worker and no push service. Android alerts are " +
                    "optional and separate from these inbox filters.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
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
 * The Android permission, its current state, and the one test post.
 *
 * This is deliberately not a switch: turning a notification switch off in the
 * app cannot revoke a permission Android owns, and a switch that flips back on
 * the next frame is worse than a button that opens the screen which can change
 * the answer. The runtime dialog is offered only when Android 13+ still allows
 * it; the settings route is always available so a permanent denial has a
 * recovery path.
 */
@Composable
private fun NotificationPermissionSection() {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SystemNotifications.isEnabled(context)) }
    var hasPermission by remember {
        mutableStateOf(SystemNotifications.hasRuntimePermission(context))
    }
    val canRequest = SystemNotifications.canRequestRuntimePermission(context)

    // Coming back from the system page is the only way this app learns that the
    // reader changed the answer there. There is no callback for another app's
    // settings screen.
    LifecycleResumeEffect(Unit) {
        enabled = SystemNotifications.isEnabled(context)
        hasPermission = SystemNotifications.hasRuntimePermission(context)
        onPauseOrDispose { }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = SystemNotifications.hasRuntimePermission(context)
        enabled = SystemNotifications.isEnabled(context)
        if (!granted) {
            Toast.makeText(
                context,
                "Notifications stay off. You can allow them later in system settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    SectionCard(
        title = "Android notifications",
        icon = AppVectorIcons.Notifications,
        subtitle = "Optional system alerts. The inbox keeps working without them.",
        badge = {
            Surface(
                shape = CircleShape,
                color = if (enabled) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
            ) {
                Text(
                    text = if (enabled) "Allowed" else "Off",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (enabled) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }
        }
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when {
                        enabled -> "Android can show AniSequel notifications."
                        !hasPermission -> "The permission has not been granted yet."
                        else -> "Android is blocking AniSequel notifications."
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = when {
                        enabled ->
                            "The channel is on. You can still silence it from the system page; " +
                                "the inbox itself has no background polling."
                        !hasPermission -> if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            "Android 13 and later requires this runtime permission. Ask here, " +
                                "or use the system page if the dialog no longer appears."
                        } else {
                            "This Android version does not use a runtime permission. Allow " +
                                "AniSequel in the system page instead."
                        }
                        else ->
                            "The runtime permission is granted, but the app switch or this " +
                                "channel is off. Open system settings and turn them back on."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (canRequest) {
                Button(
                    onClick = { permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) },
                    modifier = Modifier.testTag("notification_permission_allow")
                ) {
                    Text("Allow notifications")
                }
            }
            if (enabled) {
                Button(
                    onClick = {
                        val sent = SystemNotifications.postTest(context)
                        Toast.makeText(
                            context,
                            if (sent) {
                                "Test notification sent."
                            } else {
                                "Android is still blocking AniSequel notifications."
                            },
                            Toast.LENGTH_LONG
                        ).show()
                    },
                    modifier = Modifier.testTag("notification_permission_test")
                ) {
                    Text("Send test")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (!SystemNotifications.openSystemSettings(context)) {
                    Toast.makeText(context, "This device has no notification settings page.", Toast.LENGTH_LONG)
                        .show()
                }
            },
            modifier = Modifier.testTag("notification_permission_settings")
        ) {
            Text("Open system settings")
        }
    }
}
