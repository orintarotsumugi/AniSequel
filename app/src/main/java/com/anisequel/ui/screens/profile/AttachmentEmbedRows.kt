package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.domain.usecase.MediaAttachment
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * How the next attached image lands in the draft: the size AniList is asked to draw
 * it at, and the framing around it.
 *
 * Both are markup the site's editor writes - `img220(url)`, `~~~` fences, `~!…!~` -
 * and both are decisions about the *embed*, not about the text around it, which is
 * why they are one type rather than two parameters drifting apart at each call site.
 *
 * [width] is the empty string for "Original", because this is carried in a saveable
 * `String` and [MediaAttachment.embedMarkdown] reads a value it cannot parse - the
 * empty one included - as "no width", which is exactly the bare `img(url)` the site
 * writes.
 */
internal data class EmbedStyle(
    val width: String = "",
    val framing: MediaAttachment.EmbedFraming = MediaAttachment.EmbedFraming.Plain
)

/**
 * Whether the photo's two command rows have a file to describe.
 *
 * They used to be drawn on every Write pane, attached or not, and the row read as a
 * photo already being there - and as the composer asking what size to make a file the
 * reader has not picked. So the rule is the file itself: while an upload is in flight
 * the row appears with it, and afterwards only while the URL the upload wrote is
 * still in the draft.
 *
 * The second half is what makes it a rule rather than a flag. The reader can delete
 * the `img220(…)` the upload inserted, and the chips would then describe a file that
 * is no longer in the text; a posted comment clears the field, and clears them with
 * it, without the editor having to remember to reset anything.
 *
 * The test is [MediaAttachment.embedSpanFor] rather than `contains`: an address that
 * survives only as a link, or inside `imgwibble(…)`, is not the picture these chips
 * describe, and a substring check kept the row up for markup the parser will not
 * render.
 */
internal fun showsPhotoEmbedRows(draftText: String, attachedUrl: String?, isUploading: Boolean): Boolean =
    isUploading ||
        (attachedUrl != null && MediaAttachment.embedSpanFor(draftText, attachedUrl) != null)

/**
 * The size and framing commands for an attached image, as two chip rows.
 *
 * ## Why this is not in the format bar
 *
 * The format bar marks up text the reader has already written. These two describe the
 * file that is *about to* go in, and the alternative - insert the image and then wrap
 * the line by hand in `~!…!~`, or retype `img220(` around a URL nobody types by hand
 * on a phone - is not a thing readers do. So the choice sits beside the control that
 * attaches the file.
 *
 * ## Why photos get it too
 *
 * This row used to exist only in the GIF pane, which meant a picked screenshot was
 * always embedded at the site's full width with no way to ask for less - while the
 * same composer could put a GIF in at 220. A screenshot of a spoilery title card is
 * exactly the image a reader wants small and behind a lid, and the markup for it
 * already existed on the other pane.
 *
 * [caption] names which of the two the row is controlling, because a composer can
 * hold a GIF choice and a photo choice at once and the chips must say which draft
 * they belong to.
 */
@Composable
internal fun AttachmentEmbedRows(
    style: EmbedStyle,
    onStyleChange: (EmbedStyle) -> Unit,
    caption: String,
    tagPrefix: String,
    /**
     * The file these two rows describe, drawn beside the caption.
     *
     * Null for the GIF pane, where the row is a choice about a GIF that has not been
     * inserted yet and the results grid above is the thing being looked at. Set it
     * for an attached photo and the caption stops being a label floating over the
     * field: the picture is right there, and the chips underneath plainly belong to
     * it.
     */
    thumbnailUrl: String? = null,
    /**
     * The file is on its way to AniList right now.
     *
     * [thumbnailUrl] is null for exactly this stretch - the URL the thumbnail needs
     * is the thing the upload has not answered with yet - so without this the row
     * said "Uploading a photo…" in the same voice it uses for everything else and
     * the reader had no way to tell a working composer from a stopped one.
     */
    isBusy: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(start = 20.dp, top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (thumbnailUrl != null) {
                AsyncImage(
                    model = thumbnailUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(30.dp)
                        // The theme's extra-small slot. An 8dp literal was the one
                        // corner in this row that did not belong to the app's scale,
                        // and at thumbnail size it read as a different family from
                        // the chips below it.
                        .clip(ExpressiveShapes.extraSmall)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                    contentScale = ContentScale.Crop
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("${tagPrefix}_caption")
            )
            if (isBusy) {
                Spacer(modifier = Modifier.width(8.dp))
                ExpressiveLoadingIndicator(modifier = Modifier.size(18.dp))
            }
        }
        EmbedChipRow(
            // "Original" first, as it always was: some files *are* their size, and a
            // 60px sprite row blown up to 220 is a blur. First in the list, but not the
            // default - the default is the site's own strip width.
            options = listOf("Original" to "") + MediaAttachment.EMBED_WIDTHS.map { it to it },
            selected = style.width,
            onClickLabel = { "Insert at $it width" },
            tagPrefix = "${tagPrefix}_width",
            onSelect = { onStyleChange(style.copy(width = it)) }
        )
        EmbedChipRow(
            options = MediaAttachment.EmbedFraming.entries.map { framing ->
                framingLabel(framing) to framing.name
            },
            selected = style.framing.name,
            onClickLabel = { "Insert as $it" },
            tagPrefix = "${tagPrefix}_framing",
            onSelect = { name ->
                val framing = MediaAttachment.EmbedFraming.entries.firstOrNull { it.name == name }
                    ?: MediaAttachment.EmbedFraming.Plain
                onStyleChange(style.copy(framing = framing))
            }
        )
    }
}

/** The one word a framing step is labelled with in the composer. */
private fun framingLabel(framing: MediaAttachment.EmbedFraming): String = when (framing) {
    MediaAttachment.EmbedFraming.Plain -> "Normal"
    MediaAttachment.EmbedFraming.Centred -> "Centred"
    MediaAttachment.EmbedFraming.Spoiler -> "Spoiler"
}

/**
 * One row of chips: each shows [label], selects on [value], and is written so the
 * chip whose value equals [selected] is the one that reads as chosen.
 *
 * The pair is `(label, value)` rather than the value alone because the two differ in
 * both directions this row needs: "Original" is the label for the empty string, and
 * "Normal" is the label for `Plain`. Comparing the label instead of the value is how
 * picking Original once left every chip in the row looking unchosen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmbedChipRow(
    options: List<Pair<String, String>>,
    selected: String,
    onClickLabel: (String) -> String,
    tagPrefix: String,
    onSelect: (String) -> Unit
) {
    // A wrapping row rather than a horizontally scrolling one, for the reason
    // `MotionSection` gives for the same change: the scrolling row put "100%" off
    // the right edge on a phone, which reads as "there is no full-width option" to
    // anyone who did not think to swipe a control that looks static. All five widths
    // are visible at once, two rows at worst.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        options.forEach { (label, value) ->
            // One source for the press spring and the click, shared between them:
            // `bouncyPress` without one installs its own fallback pointer loop on the
            // element `clickable` is already driving, and the two compete for the tap.
            val interactionSource = remember(value) { MutableInteractionSource() }
            val isSelected = value == selected
            Box(
                modifier = Modifier
                    .bouncyPress(interactionSource = interactionSource)
                    // The shared capsule token rather than a literal: the width and
                    // framing rows are the same control as every other chip in the
                    // app, and a percent spelled here can drift from [ExpressiveShapes].
                    .clip(ExpressiveShapes.pill)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClickLabel = onClickLabel(label)
                    ) { onSelect(value) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("${tagPrefix}_$label")
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}
