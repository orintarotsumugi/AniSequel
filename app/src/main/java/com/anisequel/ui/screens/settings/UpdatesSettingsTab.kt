package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.anisequel.data.changelog.ChangelogRepository
import com.anisequel.data.changelog.ChangelogVersion
import com.anisequel.data.update.ReleaseNote
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ReleaseNotes

/**
 * The Updates page: whether this build is current, and what changed.
 *
 * ## Why this left the About page
 *
 * About carried the build, the updater, the changelog, the crash report, privacy
 * and the help links in one scroll. The updater and the notes are the two cards a
 * reader comes *back* to - "is there a new version" and "what did it change" -
 * while the rest is read once. Splitting them means the check and its notes are
 * one tap from the shelf instead of two-thirds of the way down the page whose
 * subtitle used to promise them.
 *
 * The build number stays on About, where a bug report looks for it.
 */
@Composable
fun UpdatesSettingsTab(
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
        UpdateSectionCard()
        ChangelogSectionCard(changelog = changelog)
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ChangelogSectionCard(changelog: ChangelogVersion?) {
    val entries = changelog?.entries
        ?.filterNot { it.title.isNullOrBlank() }
        .orEmpty()

    SectionCard(
        title = "What's new",
        icon = AppVectorIcons.NewReleases,
        // A bare version number is not a subtitle - it is the answer to a question
        // nobody asked, under a heading that already says these are release notes.
        // The version is only worth naming when it is the version these notes are
        // for, and the fallback says what the card is instead.
        subtitle = changelog?.version?.let { "Release notes for $it" } ?: "Release notes"
    ) {
        if (entries.isEmpty()) {
            Text(
                text = "No release notes for this version yet. They are on the download page in the meantime.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            // The same renderer the update prompt uses: the workflow writes that
            // manifest's notes from this file, so the two are the same sentences
            // and had no business looking different.
            ReleaseNotes(
                notes = entries.map { ReleaseNote(title = it.title.orEmpty(), detail = it.detail) }
            )
        }
    }
}
