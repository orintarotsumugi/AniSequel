package com.anisequel.ui.screens.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.BuildConfig
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.changelog.ChangelogVersion
import com.anisequel.data.diagnostics.CrashReports
import com.anisequel.data.network.AniListOAuth
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.detail.info.COPY_CONFIRMATION_MILLIS
import com.anisequel.ui.components.openExternalUrl
import kotlinx.coroutines.delay

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
        CrashReportCard()
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
 * The last force-stop, offered rather than hidden.
 *
 * ## Why this is in the app at all
 *
 * The app has no crash backend - that is deliberate, and the Privacy card above
 * promises it. But a crash with no trace is a bug nobody can fix, and the comment
 * sheet proved it: the report was "it closes when loading the comments", which is
 * true and unfixable, because reading the source can only rule a line out and never
 * confirm it. This card is the smallest thing that closes that gap: the trace is
 * captured on the device, stays on the device, and is one tap from the clipboard
 * when the reader is willing to send it.
 *
 * ## Why the card is absent when there is nothing to show
 *
 * A permanently empty "Diagnostics" section is noise on a page about the build, and
 * it invites the question "is it broken?" from every reader who has never crashed.
 * The card appears after the crash, which is the only moment it has anything to say.
 *
 * ## Why "Copy" confirms in the button rather than a toast
 *
 * Android only draws its own "Copied" toast from API 33, and this app's floor is 31
 * - the same reason the title page's copy row confirms in place. See `CopyLinkRow`.
 */
@Composable
private fun CrashReportCard() {
    val context = LocalContext.current
    // Read once per entry to the page: the file only changes when the process dies,
    // which is not a thing that happens while this screen is on top.
    var report by remember { mutableStateOf(CrashReports.read(context)) }
    var showing by remember { mutableStateOf(false) }
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(COPY_CONFIRMATION_MILLIS)
        copied = false
    }

    // Every `remember` above this line, so the two halves of the screen are not
    // conditionally composed in different slots - the rule `CommentEntryBar` states
    // for its own pair of states.
    val current = report ?: return

    SectionCard(
        title = "Diagnostics",
        icon = AppVectorIcons.Warning,
        subtitle = "The last time AniSequel closed unexpectedly"
    ) {
        Text(
            text = "This build keeps the last crash on this device so it can be " +
                "reported. It holds the error and your Android version, nothing " +
                "about your account, and it is never sent anywhere on its own.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        // The exception itself, which is the one line that says whether this is a
        // network failure, an out-of-memory, or something in the UI - the question
        // the whole card exists to answer.
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = CrashReports.headline(current),
                style = MaterialTheme.typography.bodySmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(12.dp)
            )
        }
        Spacer(modifier = Modifier.height(12.dp))
        SettingsButton(
            text = if (copied) "Report copied" else "Copy crash report",
            onClick = {
                copyToClipboard(context, current)
                copied = true
            },
            icon = if (copied) AppVectorIcons.CheckCircle else AppVectorIcons.CopyLink,
            variant = SettingsButtonVariant.Filled,
            fillWidth = true,
            testTag = "crash_report_copy"
        )
        Spacer(modifier = Modifier.height(4.dp))
        SettingsButton(
            text = "Show the whole report",
            onClick = { showing = true },
            variant = SettingsButtonVariant.Text,
            fillWidth = true,
            testTag = "crash_report_show"
        )
        SettingsButton(
            text = "Clear it",
            onClick = {
                // The card disappears with the report: `report` is the state the
                // early return above reads, so clearing it and forgetting the file
                // are the same act.
                CrashReports.clear(context)
                report = null
            },
            variant = SettingsButtonVariant.Text,
            fillWidth = true,
            testTag = "crash_report_clear"
        )
    }

    if (showing) {
        AlertDialog(
            onDismissRequest = { showing = false },
            title = { Text("Last crash report") },
            text = {
                // Selectable as well as copyable: a reader who would rather send the
                // two frames that matter than the whole trace can take them.
                SelectionContainer {
                    Text(
                        text = current,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .heightIn(max = 360.dp)
                            .verticalScroll(rememberScrollState())
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showing = false }) { Text("Close") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        copyToClipboard(context, current)
                        copied = true
                        showing = false
                    }
                ) { Text("Copy") }
            }
        )
    }
}

/** The platform clipboard, `as?` because a device can in principle not publish one. */
private fun copyToClipboard(context: Context, report: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("AniSequel crash report", report))
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
