package com.anisequel.ui.components

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.update.ReleaseNote

/**
 * Release notes, drawn the same way wherever they appear.
 *
 * ## One renderer, two sources
 *
 * Settings > What's new reads the bundled `assets/changelog.json`; the update
 * prompt reads the `notes` field of the published manifest, which the release
 * workflow writes *from that same file*. They are the same sentences, and before
 * this composable they were two different screens: the card was a list of small
 * bulleted surfaces and the prompt was a single line and a link. Somebody deciding
 * whether to update was shown less than somebody reading the settings page after
 * updating - the wrong way round.
 *
 * The entries arrive as [ReleaseNote] rather than either source's own model, so
 * this file depends on neither the changelog parser nor the manifest. See
 * `parseReleaseNotes` for the manifest side.
 *
 * ## Scrolling belongs to the caller
 *
 * This draws the list at its natural height. The update prompt puts it in a
 * bounded scrolling box because a dialog cannot grow; Settings puts it in the
 * page's own scroll, because a second scrollable region inside a `LazyColumn`
 * makes the gesture ambiguous about which one it is moving. A composable that
 * chose one of those would be wrong in the other place.
 */
@Composable
fun ReleaseNotes(
    notes: List<ReleaseNote>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        notes.forEachIndexed { index, note ->
            if (index > 0) {
                Spacer(modifier = Modifier.height(10.dp))
            }
            ReleaseNoteCard(note)
        }
    }
}

@Composable
private fun ReleaseNoteCard(note: ReleaseNote) {
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
                    // Nudged down to sit on the title's first line rather than
                    // above it: the dot is a bullet, not an icon.
                    .padding(top = 6.dp, end = 10.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
            Column {
                Text(
                    text = note.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold
                )
                note.detail?.takeIf { it.isNotBlank() }?.let { detail ->
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
