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
import com.anisequel.data.repository.DiagnosticsPreferences
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
 * Crash reports, and the last one if there is one.
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
 * ## Why it is off until the reader turns it on
 *
 * An app whose About page says there is no telemetry does not get to start writing
 * its own failures to disk because the code found it useful. The switch is the
 * permission, and it is the first thing on the card so nobody has to look for it.
 * The cost is honest: a reader who never opens this page sends no report, so the
 * comment sheet's crash has to be reproduced after the switch is flipped. That is
 * why the copy above the switch says so in as many words.
 *
 * ## Why the card is present even with nothing to show
 *
 * It has to be, now that it holds the switch: a card that only appears after a
 * crash is a card whose setting cannot be found before one. The *report* half is
 * what appears conditionally - the switch, the promise and the explanation are the
 * permanent part, and they are three lines rather than a section.
 *
 * ## Why turning it off deletes what is stored
 *
 * "Stop keeping this" and "keep the one you already have" are different requests,
 * and the second one is not what the switch says. Turning it off clears the file
 * so the setting means what a reader would assume it means; the Clear button stays
 * for the reader who has copied it and wants the file gone without changing the
 * setting.
 *
 * ## Why "Copy" confirms in the button rather than a toast
 *
 * Android only draws its own "Copied" toast from API 33, and this app's floor is 31
 * - the same reason the title page's copy row confirms in place. See `CopyLinkRow`.
 */
@Composable
private fun CrashReportCard() {
    val context = LocalContext.current
    val preferences = remember(context) { DiagnosticsPreferences(context) }
    var enabled by remember { mutableStateOf(preferences.crashReportsEnabled) }
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

    // Read once, then smart-cast: the card, its dialog and the copy button must all
    // draw the same string, and it only changes here when the reader clears it.
    val current = report

    SectionCard(
        title = "Diagnostics",
        icon = AppVectorIcons.Warning,
        subtitle = if (enabled) {
            "The last crash, kept on this device"
        } else {
            "Crash reports are off"
        }
    ) {
        Text(
            text = if (enabled) {
                "The next time AniSequel closes unexpectedly, the error and your " +
                    "Android version are saved here. Nothing about your account, " +
                    "and nothing is sent anywhere on its own."
            } else {
                "AniSequel keeps no record of a crash. Turn this on before " +
                    "reproducing a bug you want to report, then come back here to " +
                    "copy it out."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))
        SwitchRow(
            title = "Keep the last crash report",
            subtitle = "Stored on this device only, and never uploaded",
            checked = enabled,
            onCheckedChange = { next ->
                enabled = next
                preferences.crashReportsEnabled = next
                if (!next) {
                    // See the doc above: off means the file goes too.
                    CrashReports.clear(context)
                    report = null
                }
            }
        )

        // The report half. The switch, the promise and the explanation above are the
        // permanent part; this appears only when there is something to show.
        if (enabled && current != null) {
            Spacer(modifier = Modifier.height(12.dp))
            // The exception itself, which is the one line that says whether this is
            // a network failure, an out-of-memory, or something in the UI - the
            // question the whole card exists to answer.
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
                    // The block disappears with the report: `report` is the state
                    // read above, so clearing it and forgetting the file are the
                    // same act.
                    CrashReports.clear(context)
                    report = null
                },
                variant = SettingsButtonVariant.Text,
                fillWidth = true,
                testTag = "crash_report_clear"
            )
        }
    }

    // Read once, then smart-cast: the dialog and the card must draw the same string,
    // and the report can only change here by the reader clearing it.
    val current = report
    if (showing && current != null) {
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
        Spacer(modifier = Modifier.height(10.dp))
        // The Diagnostics card sits directly above this one and is the only thing
        // in the build that writes a record of a failure. Saying so here is the
        // difference between a promise and a promise with an asterisk.
        AboutPoint(
            "Crash reports are off unless you turn them on in Diagnostics above. " +
                "Even then they stay on this device until you copy one out."
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

/**
 * Where the app comes from, and where to say it is broken.
 *
 * AniSequel is installed from GitHub Releases rather than a store, so the
 * repository is not a "star us" link - it is the download page, the changelog and
 * the issue tracker for the build in the reader's hand. The crash report above is
 * only useful if there is somewhere to paste it, which is why the issues link
 * sits here next to it rather than being left to a search.
 */
@Composable
private fun HelpLinksCard() {
    val context = LocalContext.current

    SectionCard(
        title = "Help & links",
        icon = AppVectorIcons.SectionHelp,
        subtitle = "Source, issues and AniList's documentation"
    ) {
        SettingsButton(
            text = "AniSequel on GitHub",
            onClick = { openExternalUrl(context, GITHUB_REPOSITORY_URL) },
            icon = AppVectorIcons.GitHub,
            variant = SettingsButtonVariant.Text,
            fillWidth = true,
            testTag = "link_github_repository"
        )
        SettingsButton(
            text = "Report a problem",
            onClick = { openExternalUrl(context, GITHUB_ISSUES_URL) },
            icon = Icons.AutoMirrored.Filled.OpenInNew,
            variant = SettingsButtonVariant.Text,
            fillWidth = true,
            testTag = "link_github_issues"
        )
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
 * The project's own repository.
 *
 * This app is installed from its releases, so this is the download page as much
 * as the source: the reader who is on Settings > Info looking for "where does this
 * come from" wants the same place the updater reads `update.json` from.
 */
private const val GITHUB_REPOSITORY_URL = "https://github.com/orintarotsumugi/AniSequel"

/** Where a crash report goes once it is on the clipboard. */
private const val GITHUB_ISSUES_URL = "$GITHUB_REPOSITORY_URL/issues"

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
