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
        title = "Hidden list is back",
        detail = "Hiding a sequel is reversible now - find them under Filters, Hidden."
    ),
    ChangelogEntry(
        title = "Redesigned Material 3 Expressive UI",
        detail = "Spring physics, morphing shapes, and responsive controls throughout."
    ),
    ChangelogEntry(
        title = "Faster, steadier list loads",
        detail = "Optimised GraphQL batching and season mapping prevent connection failures."
    ),
    ChangelogEntry(
        title = "Planning syncs straight to AniList",
        detail = "Every addition lands on your account with instant confirmation."
    ),
    ChangelogEntry(
        title = "In-app updates",
        detail = "Checks GitHub releases and installs them in two taps."
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
            SectionCard(
                title = "Not signed in",
                icon = AppVectorIcons.Login,
                subtitle = "Demo mode"
            ) {
                Text(
                    text = "You are browsing in read-only demo mode. Sign in with your AniList account to scan your completed list and plan sequels.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        SectionCard(
            title = "About",
            icon = AppVectorIcons.SectionAbout,
            subtitle = "Build and data source"
        ) {
            InfoRow(label = "Version", value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
            Spacer(modifier = Modifier.height(10.dp))
            InfoRow(label = "Data source", value = "AniList GraphQL API")

            Spacer(modifier = Modifier.height(12.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "AniSequel reads your completed list, follows franchise relations, and finds sequels you haven't started. Your credentials never leave this device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UpdateSectionCard()

        SectionCard(
            title = "What's new",
            icon = AppVectorIcons.NewReleases,
            subtitle = BuildConfig.VERSION_NAME
        ) {
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
                            .background(MaterialTheme.colorScheme.secondary)
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

        SectionCard(
            title = "Help & links",
            icon = AppVectorIcons.SectionHelp,
            subtitle = "Docs and support"
        ) {
            Text(
                text = "AniSequel asks for read access to your lists, and write access only when you tap Add to Planning.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsButton(
                text = "AniList API documentation",
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
