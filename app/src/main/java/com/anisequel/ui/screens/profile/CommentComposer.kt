package com.anisequel.ui.screens.profile

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.domain.usecase.ContentLimits
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView

/**
 * The comment editor, lifted out of the thread sheet.
 *
 * ## The shape, and where it comes from
 *
 * This is AniList's own composer: a Cancel / title / Preview header, the reply
 * target as a chip above the field, a full-height box rather than a two-line
 * strip, and one bottom bar holding the format controls, the character counter
 * and the Post button. The point of copying it is that it is the editor people
 * already know: the field is big enough to see a paragraph, the Preview step lets
 * them check their markdown before it lands, and the counter says how much of the
 * 8,000 they have used instead of refusing on send.
 *
 * ## Why the draft is not owned here
 *
 * [draft] and [replyToName] live in the sheet above. A successful post clears
 * them, and Cancel closes this pane while keeping the text - both are decisions
 * about the whole thread, not about the editor - and a draft recalled from
 * `rememberSaveable` after the pane was disposed is what makes Cancel safe.
 *
 * Preview and the attach dialog are local because they mean nothing outside an
 * open editor.
 */
@Composable
internal fun CommentComposer(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    replyToName: String?,
    onClearReplyTo: () -> Unit,
    isPosting: Boolean,
    postError: String?,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var previewing by rememberSaveable { mutableStateOf(false) }
    var attaching by rememberSaveable { mutableStateOf(false) }

    val verdict = ContentLimits.checkReply(draft.text)
    val canSend = verdict == ContentLimits.ReplyVerdict.Valid && !isPosting

    fun apply(edit: DraftEdit) = onDraftChange(draft.withDraftEdit(edit))

    Column(modifier = modifier.fillMaxWidth()) {
        ComposerHeader(
            replyToName = replyToName,
            previewing = previewing,
            isPosting = isPosting,
            onCancel = onCancel,
            onTogglePreview = { previewing = !previewing }
        )

        ComposerBody(
            previewing = previewing,
            replyToName = replyToName,
            draft = draft,
            isPosting = isPosting,
            onDraftChange = onDraftChange,
            onClearReplyTo = onClearReplyTo
        )

        ComposerBottomBar(
            draft = draft,
            verdict = verdict,
            canSend = canSend,
            isPosting = isPosting,
            postError = postError,
            onEdit = { apply(it) },
            onAttach = { attaching = true },
            onSubmit = onSubmit
        )
    }

    if (attaching) {
        AttachImageDialog(
            onDismiss = { attaching = false },
            onInsert = { url ->
                apply(
                    insertSnippet(
                        draft.text,
                        draft.selection.start,
                        draft.selection.end,
                        "![image]($url)"
                    )
                )
                attaching = false
            }
        )
    }
}

/**
 * The Cancel / title / Preview header.
 *
 * The title says Reply rather than Comment when the editor was opened against a
 * comment, because the reader should not have to remember which one they tapped.
 * Preview toggles rather than being a separate pane: the field is the same box,
 * and a toggle keeps the draft in place.
 */
@Composable
private fun ComposerHeader(
    replyToName: String?,
    previewing: Boolean,
    isPosting: Boolean,
    onCancel: () -> Unit,
    onTogglePreview: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TextButton(onClick = onCancel, enabled = !isPosting) {
            Text("Cancel")
        }
        Text(
            text = if (replyToName != null) "Write a Reply" else "Write a Comment",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        TextButton(onClick = onTogglePreview, enabled = !isPosting) {
            Text(if (previewing) "Write" else "Preview")
        }
    }
}

/**
 * The reply target chip and the editable pane: the rendered preview when the
 * reader is checking, the field when they are writing.
 */
@Composable
private fun ColumnScope.ComposerBody(
    previewing: Boolean,
    replyToName: String?,
    draft: TextFieldValue,
    isPosting: Boolean,
    onDraftChange: (TextFieldValue) -> Unit,
    onClearReplyTo: () -> Unit
) {
    if (replyToName != null) {
        ReplyTargetChip(name = replyToName, onClear = onClearReplyTo)
    }

    if (previewing) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            if (draft.text.isBlank()) {
                Text(
                    text = "Nothing to preview yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // The same parser the posted comment goes through, so Preview
                // shows what readers will get rather than an approximation.
                val nodes = remember(draft.text) { AniListMarkdownParser.parse(draft.text) }
                AniListMarkdownView(nodes = nodes)
            }
        }
    } else {
        OutlinedTextField(
            value = draft,
            onValueChange = onDraftChange,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .testTag("activity_comment_field"),
            placeholder = {
                Text(if (replyToName != null) "Write a reply..." else "Add a comment")
            },
            textStyle = MaterialTheme.typography.bodyLarge,
            shape = MaterialTheme.shapes.large,
            enabled = !isPosting
        )
    }
}

/**
 * The reason the send button is off, the error from the last post, and the
 * format controls, counter and Post button.
 *
 * The reason is shown beside the button rather than as a tooltip because a
 * disabled control with no sentence next to it is indistinguishable from one
 * that is broken. The controls scroll horizontally: four 40dp controls, a
 * counter and a pill do not fit a 360dp phone.
 */
@Composable
private fun ComposerBottomBar(
    draft: TextFieldValue,
    verdict: ContentLimits.ReplyVerdict,
    canSend: Boolean,
    isPosting: Boolean,
    postError: String?,
    onEdit: (DraftEdit) -> Unit,
    onAttach: () -> Unit,
    onSubmit: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
    ) {
        // The reason, not just a greyed button. A disabled control with no
        // sentence next to it is indistinguishable from one that is broken.
        val reason = when {
            verdict == ContentLimits.ReplyVerdict.TooShort && draft.text.isNotEmpty() ->
                "A comment needs at least ${ContentLimits.REPLY_MIN} characters."
            verdict == ContentLimits.ReplyVerdict.TooLong ->
                "A comment can be at most ${ContentLimits.REPLY_MAX} characters."
            else -> null
        }
        if (reason != null) {
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }
        if (postError != null) {
            Text(
                text = postError,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 2.dp)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                FormatAction(
                    icon = AppVectorIcons.AttachImage,
                    label = "Insert an image",
                    tag = "comment_format_image",
                    enabled = !isPosting,
                    onClick = onAttach
                )
                FormatAction(
                    icon = AppVectorIcons.TextBold,
                    label = "Bold",
                    tag = "comment_format_bold",
                    enabled = !isPosting,
                    onClick = {
                        onEdit(
                            wrapSelection(
                                draft.text, draft.selection.start, draft.selection.end, "**"
                            )
                        )
                    }
                )
                FormatAction(
                    icon = AppVectorIcons.TextItalic,
                    label = "Italic",
                    tag = "comment_format_italic",
                    enabled = !isPosting,
                    onClick = {
                        onEdit(
                            wrapSelection(
                                draft.text, draft.selection.start, draft.selection.end, "*"
                            )
                        )
                    }
                )
                FormatAction(
                    icon = AppVectorIcons.TextSpoiler,
                    label = "Spoiler",
                    tag = "comment_format_spoiler",
                    enabled = !isPosting,
                    onClick = {
                        onEdit(
                            wrapSelection(
                                draft.text, draft.selection.start, draft.selection.end,
                                "~!", "!~"
                            )
                        )
                    }
                )
            }

            Text(
                // The API's own ceiling, and the field's: they are the same
                // number because the field clips with `ContentLimits`.
                text = "${draft.text.length} / ${ContentLimits.REPLY_MAX}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .testTag("activity_comment_counter")
            )

            Button(
                onClick = onSubmit,
                enabled = canSend,
                shape = RoundedCornerShape(percent = 50),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                modifier = Modifier.testTag("activity_comment_send")
            ) {
                if (isPosting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = AppVectorIcons.ActivitySend,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPosting) "Posting..." else "Post Reply")
            }
        }
    }
}

/**
 * A toolbar edit as the field's next value, with the caret clamped to the text.
 *
 * `TextFieldValue` *requires* its selection and composing region to fall inside
 * its text - see its `init` - so a value that truncates the text and keeps the
 * old caret throws from the constructor. Every programmatic edit goes through
 * here for that reason: the format bar, the insert dialog, and the `@mention`
 * the sheet adds when the reader taps Reply. A programmatic edit drops the IME's
 * composing range, which no longer describes anything after the text changed.
 */
internal fun TextFieldValue.withDraftEdit(edit: DraftEdit): TextFieldValue {
    val text = ContentLimits.clipReply(edit.text)
    val start = edit.selectionStart.coerceIn(0, text.length)
    val end = edit.selectionEnd.coerceIn(start, text.length)
    return TextFieldValue(text = text, selection = TextRange(start, end))
}

/**
 * The reply target, as the chip above the field.
 *
 * It states who the comment will be addressed to and gives a way to stop
 * addressing them. The `@name` itself is in the text, where the author can still
 * edit it - AniList threads replies only by that mention, so a chip that could
 * not be removed would be the app deciding who a comment is for.
 */
@Composable
private fun ReplyTargetChip(name: String, onClear: () -> Unit) {
    Row(
        modifier = Modifier
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(percent = 50))
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(start = 12.dp, end = 2.dp, top = 2.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Replying to $name",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer
        )
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .clickable(onClick = onClear)
                .testTag("activity_reply_target_clear"),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.Close,
                contentDescription = "Stop replying to $name",
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

/**
 * One format-bar control.
 *
 * A 40dp visual target inside a 48dp minimum from the bar's padding rather than
 * an [androidx.compose.material3.IconButton], because the bar's height is already
 * fixed by the Post button and an IconButton would add its own 48dp box to it.
 */
@Composable
private fun FormatAction(
    icon: ImageVector,
    label: String,
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val tint = if (enabled) {
        MaterialTheme.colorScheme.onSurfaceVariant
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
    }
    // 48dp, the platform's minimum touch target, rather than the 40dp box that
    // was sized around the glyph. These three sit above a text field where every
    // keystroke starts, so they are hit far more often than a toolbar normally is;
    // the icon inside stays the size it was.
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
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
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * The attach-image step.
 *
 * AniList has no upload endpoint, so "attach" can only mean "insert an image by
 * URL" - and the copy says so rather than opening a picker that would have
 * nowhere to send a file. The URL is checked before Insert is enabled, because a
 * relative path or a `data:` payload inserted into a comment is stored as literal
 * text that the renderer cannot embed: the author would post a broken image and
 * only find out with the comment already sent.
 */
@Composable
private fun AttachImageDialog(onDismiss: () -> Unit, onInsert: (String) -> Unit) {
    var url by rememberSaveable { mutableStateOf("") }
    val valid = isAttachableImageUrl(url)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Insert an image") },
        text = {
            Column {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    singleLine = true,
                    placeholder = { Text("https://...") },
                    isError = url.isNotBlank() && !valid,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("comment_image_url_field")
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "AniList has no upload, so the image is inserted by URL.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onInsert(url.trim()) },
                enabled = valid
            ) {
                Text("Insert")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
