package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.model.ViewerProfile
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
    modifier: Modifier = Modifier
) {
    val preferences by preferencesStore.preferences.collectAsStateWithLifecycle(
        initialValue = NotificationPreferences()
    )
    val scope = rememberCoroutineScope()
    var reset by rememberSaveable { mutableStateOf(false) }

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
                        "Forget every read mark and baseline for this account. Rows will be treated as unread again, based on the server count at the next inbox load."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(
                    onClick = {
                        val account = viewer?.id ?: return@TextButton
                        scope.launch {
                            readStore.reset(account)
                            reset = true
                        }
                    },
                    enabled = viewer != null,
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("notification_reset_read")
                ) {
                    Text(if (reset) "Read history reset" else "Reset read history")
                }
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

        Text(
            text = "These are inbox filters, not Android notifications. The app does not poll in the background; the inbox refreshes when you open it or pull down.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
