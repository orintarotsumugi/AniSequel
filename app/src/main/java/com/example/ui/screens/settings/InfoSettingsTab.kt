package com.example.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.data.model.ViewerProfile
import com.example.data.network.AniListOAuth
import com.example.ui.components.AppVectorIcons

private data class ChangelogEntry(val title: String, val detail: String)

private val changelog = listOf(
    ChangelogEntry(
        title = "Redesigned Material 3 Expressive UI",
        detail = "Enjoy fluid spring physics, dynamic morphing shapes, and tactile responsive controls."
    ),
    ChangelogEntry(
        title = "Modern Anime Card Visuals",
        detail = "Featuring high-resolution posters, glowing status tags, sinusoidal watch progress, and 1-tap planning."
    ),
    ChangelogEntry(
        title = "Your anime list loads quickly & reliably",
        detail = "Optimized GraphQL coalescing and smart season mapping prevent connection failures."
    ),
    ChangelogEntry(
        title = "Seamless Planning Sync",
        detail = "Every addition to Planning syncs directly to your AniList account with real-time feedback."
    ),
    ChangelogEntry(
        title = "Automatic In-App Updates",
        detail = "Checks for official GitHub releases and updates your app safely with two taps."
    )
)

@Composable
fun InfoSettingsTab(
    viewer: ViewerProfile?,
    onNavigateBack: () -> Unit,
    onNavigateToLogin: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    SettingsScrollColumn(modifier) {
        if (viewer != null) {
            ProfileCard(
                viewer = viewer,
                onOpenProfile = {
                    openExternalUrl(context, "https://anilist.co/user/${viewer.name}")
                }
            )
        } else {
            SectionCard(title = "Not signed in") {
                Text(
                    text = "You are currently browsing AniSequel in read-only / demo mode. " +
                            "Sign in with your AniList account to scan your completed lists and add sequels to your Planning list.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(title = "About AniSequel") {
            InfoRow(label = "Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(label = "Data source", value = "AniList GraphQL API")

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AniSequel scans franchise relations from your completed anime list and discovers sequels you haven't started yet. Your credentials and data stay directly on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UpdateSectionCard()

        SectionCard(title = "What's new in ${BuildConfig.VERSION_NAME}") {
            Text(
                text = "Recent improvements, performance fixes, and enhancements:",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))

            changelog.forEachIndexed { index, entry ->
                if (index > 0) {
                    Spacer(modifier = Modifier.height(12.dp))
                }
                Row(verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .padding(top = 6.dp, end = 10.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Column {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = entry.detail,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        SectionCard(title = "Help & Links") {
            Text(
                text = "AniSequel requires read permissions to check your lists and write permission only when you tap 'Add to Planning'.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsButton(
                text = "AniList API Documentation",
                onClick = { openExternalUrl(context, AniListOAuth.DEVELOPER_SETTINGS_URL) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                variant = SettingsButtonVariant.Text,
                fillWidth = true
            )
        }

        SettingsButton(
            text = if (viewer != null) "Sign out" else "Back to sign in",
            onClick = {
                if (viewer != null) {
                    onNavigateBack()
                } else {
                    onNavigateToLogin()
                }
            },
            icon = if (viewer != null) AppVectorIcons.Logout else AppVectorIcons.Login,
            variant = SettingsButtonVariant.Outlined,
            fillWidth = true,
            testTag = "logout_button"
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}
