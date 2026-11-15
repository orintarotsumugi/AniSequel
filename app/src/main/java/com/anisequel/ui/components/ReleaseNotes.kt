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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.anisequel.data.update.ReleaseNote
import com.anisequel.data.update.ReleaseNoteTone
import com.anisequel.data.update.releaseNoteTone

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
 * ## Colour, without inventing categories
 *
 * Each note carries a tone inferred from its title (`releaseNoteTone`) and a dot
 * plus a one-word label in that tone's colour. This is the update's only
 * at-a-glance answer to "what kind of release is this": six paragraphs of
 * `bodySmall` in a dialog all look identical, and a reader who only cares about a
 * crash fix should not have to read six titles to find it. The categories are the
 * app's own outcomes - New, Fix, Faster, Changed, Note - rather than a taxonomy
 * the changelog does not actually record.
 *
 * ## Markup
 *
 * The changelog is written by hand and contains inline code (the `<escape>` tag
 * the bio parser strips) and, occasionally, `**emphasis**`. Those used to print
 * their backticks and asterisks literally in the one surface that explains a
 * release. They are rendered here: code in the theme's primary with a monospace
 * face, emphasis in the font's real Bold - not ExtraBold, which many imported
 * fonts do not have and Compose then synthesises.
 *
 * ## Scrolling belongs to the caller
 *
 * This draws the list at its natural height. The update prompt puts it in a
 * bounded scrolling box because a dialog cannot grow; Settings puts it in the
 * page's own scroll, because a second scrollable region inside a `LazyColumn`
 * makes the gesture ambiguous about which one it is moving.
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
    val tone = remember(note.title) { releaseNoteTone(note.title) }
    val toneColor = toneColor(tone)

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            // The dot stays a bullet, not an icon: at 7dp the colour is the
            // signal and a glyph would be a smudge. Its tone colour is what
            // makes a six-note dialog scannable.
            Box(
                modifier = Modifier
                    .padding(top = 6.dp, end = 10.dp)
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(toneColor)
            )
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = noteMarkup(note.title),
                        // The title is the only line a reader scanning six notes
                        // actually reads. SemiBold rendered as the body's own
                        // medium face under several of the imported fonts, which
                        // is why every heading in this dialog looked like a
                        // paragraph. Bold is a real face here, not ExtraBold.
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        text = tone.label,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = toneColor
                    )
                }
                note.detail?.takeIf { it.isNotBlank() }?.let { detail ->
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = noteMarkup(detail),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun toneColor(tone: ReleaseNoteTone): Color = when (tone) {
    ReleaseNoteTone.NEW -> MaterialTheme.colorScheme.tertiary
    ReleaseNoteTone.FIX -> MaterialTheme.colorScheme.primary
    ReleaseNoteTone.IMPROVE -> MaterialTheme.colorScheme.secondary
    ReleaseNoteTone.CHANGE -> MaterialTheme.colorScheme.error
    ReleaseNoteTone.NOTE -> MaterialTheme.colorScheme.onSurfaceVariant
}

/**
 * `code` and `**emphasis**`, rendered; everything else stays literal.
 *
 * Deliberately small: this is release-note copy, not AniList markdown. Links,
 * images and spoilers would be a second parser for a surface that never has them.
 * An unmatched marker falls through as text, which is the honest failure - a
 * stray backtick should look like a typo, not swallow the rest of the sentence.
 */
@Composable
private fun noteMarkup(text: String): AnnotatedString {
    val codeColor = MaterialTheme.colorScheme.primary
    val codeBackground = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    return remember(text, codeColor, codeBackground) {
        buildAnnotatedString {
            var cursor = 0
            markupPattern.findAll(text).forEach { match ->
                append(text.substring(cursor, match.range.first))
                val token = match.value
                if (token.startsWith("`") && token.length > 2) {
                    withStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            color = codeColor,
                            background = codeBackground
                        )
                    ) {
                        append(token.substring(1, token.length - 1))
                    }
                } else if (token.startsWith("**") && token.length > 4) {
                    withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                        append(token.substring(2, token.length - 2))
                    }
                } else {
                    append(token)
                }
                cursor = match.range.last + 1
            }
            append(text.substring(cursor))
        }
    }
}

private val markupPattern = Regex("(`[^`]+`|\\*\\*[^*]+\\*\\*)")
