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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.BuildConfig
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.changelog.ChangelogVersion
import com.anisequel.data.network.AniListOAuth
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.openExternalUrl

/**
 * The About & support page: what this build is, and what it does with your data.
 *
 * ## What moved out, and why
 *
 * This page used to be the old "Info" tab under a new name, which meant it still
 * carried the profile card, the demo sign-in prompt, the list snapshot, the
 * AniList connection check and the sign-out button - all of them a second copy
 * of something the AniList account page or the connection row on the shelf
 * already offered. Two sign-out buttons in one Settings screen is the clearest
 * version of the problem (both called `logout()`; a reader could reasonably
 * wonder if they differed), but the duplication was wider than that: the page
 * whose subtitle promises "release notes, updates and privacy" was mostly
 * account and diagnostics, and the account page was mostly a form.
 *
 * Everything account-shaped now lives on [EditSettingsTab], where the session
 * already was, and the connection check lives on the shelf row that opens it.
 * What is left is one subject per card: the build, the privacy promise, the
 * updater, the notes, and the one external link the app needs.
 */
@Composable
fun InfoSettingsTab(
    changelogRepository: ChangelogRepository? = null,
    modifier: Modifier = Modifier
) {
    // Newest block in the changelog file, not a lookup by BuildConfig.VERSION_NAME:
    // the release workflow advances the patch number past the newest published
    // release, so the version a build reports is not knowable when the notes are
    // written and an exact match would always miss. See ChangelogRepository.
    val changelog = remember(changelogRepository) {
        changelogRepository?.latestWithNotes()
    }

    SettingsScrollColumn(modifier) {
        BuildCard()
        PrivacyCard()
        UpdateSectionCard()
        ChangelogSectionCard(changelog = changelog)
        HelpLinksCard()
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The build, stated plainly.
 *
 * Version and package are the two facts a bug report needs, so they stay rows
 * rather than prose. The application id is shown because this app is installed
 * from GitHub rather than a store, which makes "which AniSequel is this?"
 * a question people actually ask.
 */
@Composable
private fun BuildCard() {
    SectionCard(
        title = "About AniSequel",
        icon = AppVectorIcons.SectionAbout,
        subtitle = "Build and data source"
    ) {
        Text(
            text = "AniSequel reads your completed list, follows franchise relations, " +
                "and finds sequels you haven't started.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(14.dp))
        InfoRow(
            label = "Version",
            value = "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
        )
        Spacer(modifier = Modifier.height(10.dp))
        InfoRow(label = "Package", value = BuildConfig.APPLICATION_ID)
        Spacer(modifier = Modifier.height(10.dp))
        InfoRow(label = "Data source", value = "AniList GraphQL API")
    }
}

/**
 * The privacy promise, as three separate sentences.
 *
 * It was a wall of body text under the version rows, which is where a promise
 * goes to be skipped. Each line now answers one question - what is sent, what it
 * may write, what else it contacts - and the card has a heading of its own so it
 * can be found by somebody looking for it rather than read past on the way to
 * the version number.
 */
@Composable
private fun PrivacyCard() {
    SectionCard(
        title = "Privacy",
        icon = AppVectorIcons.Privacy,
        subtitle = "What leaves this device"
    ) {
        AboutPoint(
            "Your AniList token is stored on this device and never leaves it. " +
                "There is no AniSequel server to send it to."
        )
        Spacer(modifier = Modifier.height(10.dp))
        AboutPoint(
            "AniSequel asks for read access to your lists, and write access only " +
                "when you tap Add to Planning."
        )
        Spacer(modifier = Modifier.height(10.dp))
        AboutPoint(
            "It talks only to AniList and GitHub Releases. There is no analytics, " +
                "no telemetry, and no third-party tracker."
        )
    }
}

/** One bulleted fact. The dot is the same one the release notes use. */
@Composable
private fun AboutPoint(text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp, end = 10.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The one link the app needs: AniList's own API documentation. */
@Composable
private fun HelpLinksCard() {
    val context = LocalContext.current

    SectionCard(
        title = "Help & links",
        icon = AppVectorIcons.SectionHelp,
        subtitle = "AniList's own documentation"
    ) {
        SettingsButton(
            text = "AniList API documentation",
            onClick = { openExternalUrl(context, AniListOAuth.DEVELOPER_SETTINGS_URL) },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            variant = SettingsButtonVariant.Text,
            fillWidth = true
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
