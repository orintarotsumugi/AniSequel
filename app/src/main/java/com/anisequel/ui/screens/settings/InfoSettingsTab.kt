package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.BuildConfig
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListOAuth
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.StatsBanner
import com.anisequel.ui.components.openExternalUrl

@Composable
fun InfoSettingsTab(
    viewer: ViewerProfile?,
    onNavigateToLogin: () -> Unit,
    onSignOut: () -> Unit = {},
    totalWatchedCount: Int? = null,
    totalMissedCount: Int? = null,
    changelogRepository: ChangelogRepository? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // Newest block in the changelog file, not a lookup by BuildConfig.VERSION_NAME:
    // the release workflow advances the patch number past the newest published
    // release, so the version a build reports is not knowable when the notes are
    // written and an exact match would always miss. See ChangelogRepository.
    val changelog = remember(changelogRepository) {
        changelogRepository?.latestWithNotes()
    }
    val changelogEntries = changelog?.entries
        ?.filterNot { it.title.isNullOrBlank() }
        .orEmpty()

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
                subtitle = "Demo mode active",
                badge = {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.tertiaryContainer
                    ) {
                        Text(
                            text = "DEMO",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                }
            ) {
                Text(
                    text = "You are browsing in read-only demo mode. Sign in with your AniList account to scan your completed list, follow franchise trees, and add sequels to Planning in one tap.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                SettingsButton(
                    text = "Sign in with AniList",
                    onClick = onNavigateToLogin,
                    icon = AppVectorIcons.Login,
                    variant = SettingsButtonVariant.Filled,
                    fillWidth = true
                )
            }
        }

        if (totalWatchedCount != null && totalMissedCount != null) {
            SectionCard(
                title = "Your list",
                icon = AppVectorIcons.List,
                subtitle = "Snapshot of the current scan"
            ) {
                StatsBanner(
                    totalWatched = totalWatchedCount,
                    missedCount = totalMissedCount
                )
                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(10.dp))
                InfoRow(
                    label = "Planning sync",
                    value = if (viewer != null) "Write-enabled" else "Read-only on public profiles"
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "AniSequel asks for read access to your lists, and write access only when you tap Add to Planning.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UpdateSectionCard()

        SectionCard(
            title = "What's new",
            icon = AppVectorIcons.NewReleases,
            subtitle = changelog?.version?.let { "Release notes for $it" }
                ?: BuildConfig.VERSION_NAME
        ) {
            if (changelogEntries.isEmpty()) {
                Text(
                    text = "No release notes for this version yet. They are on the download page in the meantime.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                changelogEntries.forEachIndexed { index, entry ->
                    if (index > 0) {
                        Spacer(modifier = Modifier.height(10.dp))
                    }
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top
                        ) {
                            Box(
                                modifier = Modifier
                                    .padding(top = 6.dp, end = 10.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                            Column {
                                Text(
                                    text = entry.title.orEmpty(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                entry.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = detail,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        SectionCard(
            title = "Help & links",
            icon = AppVectorIcons.SectionHelp,
            subtitle = "Docs and support"
        ) {
            SettingsButton(
                text = "AniList API documentation",
                onClick = { openExternalUrl(context, AniListOAuth.DEVELOPER_SETTINGS_URL) },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                variant = SettingsButtonVariant.Text,
                fillWidth = true
            )
        }

        SettingsButton(
            text = if (viewer != null) "Sign out" else "Sign in",
            onClick = {
                if (viewer != null) {
                    onSignOut()
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
