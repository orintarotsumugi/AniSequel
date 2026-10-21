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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.BuildConfig
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.changelog.ChangelogVersion
import com.anisequel.data.model.ViewerProfile
import com.anisequel.data.network.AniListOAuth
import com.anisequel.data.network.ApiReachability
import com.anisequel.data.network.ApiStatusSnapshot
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.StatsBanner
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.viewmodel.rememberApiStatusProbe

@Composable
fun InfoSettingsTab(
    viewer: ViewerProfile?,
    onNavigateToLogin: () -> Unit,
    onSignOut: () -> Unit = {},
    totalWatchedCount: Int? = null,
    totalMissedCount: Int? = null,
    changelogRepository: ChangelogRepository? = null,
    /**
     * Asks AniList whether it is answering. Defaults to UNKNOWN so a preview or a
     * demo build shows "not checked" rather than a fabricated verdict.
     */
    checkApiReachability: suspend () -> ApiStatusSnapshot = {
        ApiStatusSnapshot(ApiReachability.UNKNOWN)
    },
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

        // The connection card sits above About because it is the one diagnostic a
        // reader can actually use: "is AniList awake, or is it my phone?" is the
        // question every failed screen leaves them asking.
        ConnectionSection(checkApiReachability = checkApiReachability)

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
            Spacer(modifier = Modifier.height(8.dp))
            // The app talks to exactly two hosts. Saying so is the privacy policy:
            // there is no analytics SDK to describe, and "no telemetry" is only
            // credible if the reader can see what *is* contacted.
            Text(
                text = "It talks only to AniList and GitHub Releases. There is no analytics, no telemetry, and no third-party tracker.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        UpdateSectionCard()

        ChangelogSectionCard(changelog = changelog)

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

/**
 * The "AniList connection" card and the dialog it opens.
 *
 * Extracted from [InfoSettingsTab] so the tab stays a layout. One probe is shared
 * by the row and the dialog - two would let a Check tap in the dialog disagree
 * with the row that opened it - and that state belongs with the card, not with
 * every other section in the tab.
 */
@Composable
private fun ConnectionSection(checkApiReachability: suspend () -> ApiStatusSnapshot) {
    val apiProbe = rememberApiStatusProbe(checkApiReachability)
    var showConnectionCheck by rememberSaveable { mutableStateOf(false) }

    SectionCard(
        title = "AniList connection",
        icon = AppVectorIcons.BrokenHeart,
        subtitle = "Check whether the API is answering"
    ) {
        Text(
            text = "If loading feels stuck, ask AniList directly. The check tells you " +
                "whether the problem is this device or the API - and the heart breaks " +
                "or mends accordingly.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(12.dp))
        SettingsButton(
            text = "Check AniList now",
            onClick = { showConnectionCheck = true },
            icon = AppVectorIcons.HeartMended,
            variant = SettingsButtonVariant.Tonal,
            fillWidth = true,
            testTag = "api_status_button"
        )
    }

    if (showConnectionCheck) {
        ConnectionCheckDialog(
            probe = apiProbe,
            onDismiss = { showConnectionCheck = false }
        )
    }
}

/**
 * "What's new", rendered from the newest changelog block.
 *
 * The empty case is not an error: a build between releases can have notes for a
 * version it has not shipped yet, and the card must still say something useful
 * rather than render a blank section.
 */
@Composable
private fun ChangelogSectionCard(changelog: ChangelogVersion?) {
    val entries = changelog?.entries
        ?.filterNot { it.title.isNullOrBlank() }
        .orEmpty()

    SectionCard(
        title = "What's new",
        icon = AppVectorIcons.NewReleases,
        subtitle = changelog?.version?.let { "Release notes for $it" }
            ?: BuildConfig.VERSION_NAME
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "No release notes for this version yet. They are on the download page in the meantime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            entries.forEachIndexed { index, entry ->
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
}
