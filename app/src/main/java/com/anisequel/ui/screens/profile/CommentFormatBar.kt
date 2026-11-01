package com.anisequel.ui.screens.profile

/**
 * The comment composer's format bar, and the one dialog it needs.
 *
 * Split out of `CommentComposer.kt` because the bar has its own reason to change:
 * it is a catalogue of what AniList's comment markup can carry, and it grows when a
 * marker is added to the parser - which is a different event from the editor's
 * layout changing. The split is also what kept the pair under the file-size ceiling
 * this app enforces; the editor keeps the panes and the draft plumbing, and this
 * file keeps the seventeen controls, their groups and their ordering rules.
 *
 * The bar is one scrolling row divided into groups rather than two rows or an
 * overflow menu. A menu hides the markups a reader cannot name - they can recognise
 * a centred line when they see one, and "More markup ▸ Centre" is two decisions per
 * tap - while a single row of grouped targets is one scroll and one tap, and the
 * groups read as *kinds* of thing: media first, then emphasis around a selection,
 * then whole-line blocks, then the embeds that need an address.
 */

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.domain.usecase.ContentLimits
import com.anisequel.domain.usecase.MediaAttachment
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers

/**
 * The reason the send button is off, the format controls, the counter and Post.
 *
 * The reason is shown beside the button rather than as a tooltip because a disabled
 * control with no sentence next to it is indistinguishable from one that is broken.
 * The controls scroll horizontally: a phone will not fit seventeen 48dp targets, a
 * counter and a pill in one line.
 */
@Composable
internal fun ComposerBottomBar(
    draft: TextFieldValue,
    verdict: ContentLimits.ReplyVerdict,
    canSend: Boolean,
    isBusy: Boolean,
    postError: String?,
    uploadNote: String?,
    replyToName: String?,
    gifsOpen: Boolean,
    onEdit: (DraftEdit) -> Unit,
    onInsertBlock: (String) -> Unit,
    onAskKind: (InsertKind) -> Unit,
    onPickFromDevice: () -> Unit,
    onToggleGifs: () -> Unit,
    submitLabel: String?,
    busyLabel: String,
    onSubmit: () -> Unit
) {
    Column(
        // The sheet pads for `ime union navigationBars` once, above this pane.
        // Padding here again put a nav bar's height between the controls and the
        // keyboard while typing - a gap directly under the Post button.
        modifier = Modifier.fillMaxWidth()
    ) {
        val reason = when {
            verdict == ContentLimits.ReplyVerdict.TooShort && draft.text.isNotEmpty() ->
                "A comment needs at least ${ContentLimits.REPLY_MIN} characters."
            verdict == ContentLimits.ReplyVerdict.TooLong ->
                "A comment can be at most ${ContentLimits.REPLY_MAX} characters."
            else -> null
        }
        reason?.let { NoteLine(it, MaterialTheme.colorScheme.onSurfaceVariant, null) }
        // An upload is a round trip to a third party and it can fail for reasons the
        // reader can act on - a 30 MB GIF, a type the host refuses, a host that is
        // down. It gets its own line rather than the post error's, because "could
        // not upload" is not a complaint about the comment.
        uploadNote?.let {
            NoteLine(
                text = it,
                color = if (it.startsWith("Uploading")) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.error
                },
                tag = "activity_comment_upload_note"
            )
        }
        postError?.let { NoteLine(it, MaterialTheme.colorScheme.error, null) }

        // The controls get a row of their own.
        //
        // They used to share one Row with the counter and the Post button, and a
        // `weight(1f)` scroll strip beside a ~150dp button left room for three
        // 48dp controls on a phone: the bar appeared to have exactly three
        // buttons, with the other fourteen hidden behind a horizontal scroll
        // nobody could see was there. Post is still at the right edge, on a line
        // below, so it never scrolls away and the strip gets the full width.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
                FormatAction(
                    icon = AppVectorIcons.PhotoUpload,
                    label = "Attach a photo from the device",
                    tag = "comment_attach_photo",
                    enabled = !isBusy,
                    onClick = onPickFromDevice
                )
                FormatAction(
                    icon = AppVectorIcons.AnimatedGif,
                    label = "Search for a GIF",
                    tag = "comment_attach_gif",
                    enabled = !isBusy,
                    selected = gifsOpen,
                    onClick = onToggleGifs
                )
                FormatAction(
                    icon = AppVectorIcons.AttachImage,
                    label = "Insert an image by URL",
                    tag = "comment_format_image",
                    enabled = !isBusy,
                    onClick = { onAskKind(InsertKind.IMAGE) }
                )
                BarDivider()
                FormatAction(
                    icon = AppVectorIcons.TextBold,
                    label = "Bold",
                    tag = "comment_format_bold",
                    enabled = !isBusy,
                    onClick = { onEdit(wrap(draft, "**")) }
                )
                FormatAction(
                    icon = AppVectorIcons.TextItalic,
                    label = "Italic",
                    tag = "comment_format_italic",
                    enabled = !isBusy,
                    onClick = { onEdit(wrap(draft, "*")) }
                )
                FormatAction(
                    icon = AppVectorIcons.TextUnderline,
                    label = "Underline",
                    tag = "comment_format_underline",
                    enabled = !isBusy,
                    onClick = { onEdit(wrap(draft, "<u>", "</u>")) }
                )
                FormatAction(
                    icon = AppVectorIcons.TextStrikethrough,
                    label = "Strikethrough",
                    tag = "comment_format_strike",
                    enabled = !isBusy,
                    onClick = { onEdit(wrap(draft, "~~")) }
                )
                FormatAction(
                    icon = AppVectorIcons.TextSpoiler,
                    label = "Spoiler",
                    tag = "comment_format_spoiler",
                    enabled = !isBusy,
                    onClick = { onEdit(wrap(draft, "~!", "!~")) }
                )
                FormatAction(
                    icon = AppVectorIcons.TextCode,
                    label = "Code",
                    tag = "comment_format_code",
                    enabled = !isBusy,
                    onClick = { onEdit(wrap(draft, "`")) }
                )
                BarDivider()
                FormatAction(
                    icon = AppVectorIcons.TextHeader,
                    label = "Heading",
                    tag = "comment_format_header",
                    enabled = !isBusy,
                    onClick = { onEdit(prefix(draft, "# ")) }
                )
                FormatAction(
                    icon = AppVectorIcons.TextQuote,
                    label = "Block quote",
                    tag = "comment_format_quote",
                    enabled = !isBusy,
                    onClick = { onEdit(prefix(draft, "> ")) }
                )
                FormatAction(
                    icon = AppVectorIcons.List,
                    label = "Bulleted list",
                    tag = "comment_format_list",
                    enabled = !isBusy,
                    onClick = { onEdit(prefix(draft, "- ")) }
                )
                FormatAction(
                    icon = AppVectorIcons.TextCentered,
                    label = "Centre the selection",
                    tag = "comment_format_center",
                    enabled = !isBusy,
                    onClick = {
                        val chosen = draft.text.substring(
                            minOf(draft.selection.start, draft.selection.end).coerceIn(0, draft.text.length),
                            maxOf(draft.selection.start, draft.selection.end).coerceIn(0, draft.text.length)
                        )
                        if (chosen.isBlank()) {
                            // Empty markers, caret between them: pressing Centre and
                            // then typing is the order a reader means.
                            onInsertBlock("~~~\n\n~~~")
                        } else {
                            // A centred span is its own block on both sides, so the
                            // fences go on lines of their own rather than around the
                            // words - `~~~text~~~` on one line centres that line, but
                            // only until the next paragraph, which is not what a
                            // multi-line selection means.
                            onEdit(wrap(draft, "~~~\n", "\n~~~"))
                        }
                    }
                )
                BarDivider()
                FormatAction(
                    icon = AppVectorIcons.CopyLink,
                    label = "Link",
                    tag = "comment_format_link",
                    enabled = !isBusy,
                    onClick = { onAskKind(InsertKind.LINK) }
                )
                FormatAction(
                    icon = AppVectorIcons.YouTube,
                    label = "YouTube video",
                    tag = "comment_format_youtube",
                    enabled = !isBusy,
                    onClick = { onAskKind(InsertKind.YOUTUBE) }
                )
                FormatAction(
                    icon = AppVectorIcons.WebmVideo,
                    label = "WebM video",
                    tag = "comment_format_webm",
                    enabled = !isBusy,
                    onClick = { onAskKind(InsertKind.WEBM) }
                )
            }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                // The API's own ceiling, and the field's: they are the same
                // number because the field clips with `ContentLimits`. An embedded
                // URL counts against it, which is why a long host address is worth
                // seeing before the post is refused rather than after.
                text = "${draft.text.length} / ${ContentLimits.REPLY_MAX}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("activity_comment_counter")
            )

            Spacer(modifier = Modifier.weight(1f))

            Button(
                onClick = onSubmit,
                enabled = canSend,
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("activity_comment_send")
            ) {
                if (isBusy) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                } else {
                    Icon(
                        imageVector = AppVectorIcons.ActivitySend,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = when {
                        isBusy -> busyLabel
                        // The same word the header uses. A new comment used to read
                        // "Post Reply" here, which named a reply that was not being
                        // written.
                        submitLabel != null -> submitLabel
                        replyToName != null -> "Post Reply"
                        else -> "Post Comment"
                    }
                )
            }
        }
    }
}

/** One line of the three the bar can say above the controls. */
@Composable
private fun NoteLine(text: String, color: Color, tag: String?) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 2.dp)
            .then(tag?.let { Modifier.testTag(it) } ?: Modifier)
    )
}

/** The bar's two selection shortcuts, so seventeen controls do not repeat the clamp. */
private fun wrap(draft: TextFieldValue, prefix: String, suffix: String = prefix): DraftEdit =
    wrapSelection(draft.text, draft.selection.start, draft.selection.end, prefix, suffix)

private fun prefix(draft: TextFieldValue, marker: String): DraftEdit =
    prefixLines(draft.text, draft.selection.start, draft.selection.end, marker)

/** The hairline between two control groups. */
@Composable
private fun BarDivider() {
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .width(1.dp)
            .height(24.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * One format-bar control.
 *
 * A 48dp minimum touch target rather than the 40dp box that was originally sized
 * around the glyph: these sit above a text field where every keystroke starts, so
 * they are hit far more often than a toolbar normally is.
 *
 * [selected] is the only piece of state the bar shows - the GIF browser is open or
 * it is not, and the control that opened it is the control that closes it. A
 * filled container is the shortest way to say so without moving anything.
 */
@Composable
private fun FormatAction(
    icon: ImageVector,
    label: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
    selected: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(
                if (selected) {
                    Modifier.background(MaterialTheme.colorScheme.primaryContainer)
                } else {
                    Modifier
                }
            )
            .bouncyPress(pressedScale = 0.88f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                // The spring is the feedback; a ripple on a glyph this small
                // reads as a second, competing animation.
                indication = null,
                enabled = enabled,
                onClickLabel = label,
                onClick = onClick
            )
            .testTag(tag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            // No `size`: these were drawn at 20dp inside the 48dp target, which
            // made the 24dp vectors - and the 1.8-1.9f strokes most of them are
            // built from - visibly smaller than the Material `List` glyph that
            // sits in the same row. The glyph now uses the vector's own 24dp
            // default, the same correction the tier-list trophy needed.
        )
    }
}

/**
 * The one dialog the bar needs for anything that takes an address.
 *
 * The old composer had an "insert an image" dialog with a sentence in it explaining
 * that AniList has no upload. That is no longer true - a photo comes off the device
 * now - so what is left of it is the case where the reader already has a link: an
 * image somewhere else, a YouTube id, a WebM, or an address to wrap a selection in.
 *
 * It checks the value before Insert lights up because a relative path or a `data:`
 * payload inserted into a comment is stored as literal text the renderer cannot
 * embed: the author would post a broken image and only find out with the comment
 * already public.
 */
@Composable
internal fun InsertMarkupDialog(
    kind: InsertKind,
    hasSelection: Boolean,
    onDismiss: () -> Unit,
    onInsert: (String) -> Unit
) {
    var value by rememberSaveable { mutableStateOf("") }
    val valid = isUsableInsert(kind, value)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(kind.label) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                placeholder = { Text(kind.placeholder) },
                isError = value.isNotBlank() && !valid,
                supportingText = {
                    Text(
                        text = when {
                            kind == InsertKind.YOUTUBE -> "A link or the video id."
                            kind == InsertKind.LINK && hasSelection -> "Goes around the selected text."
                            !valid && value.isNotBlank() -> "An absolute http(s) address is needed."
                            else -> "It goes on its own line, which is how AniList embeds it."
                        },
                        style = MaterialTheme.typography.bodySmall
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("comment_insert_field")
            )
        },
        confirmButton = {
            TextButton(onClick = { onInsert(value.trim()) }, enabled = valid) {
                Text(if (kind == InsertKind.LINK) "Insert link" else "Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
