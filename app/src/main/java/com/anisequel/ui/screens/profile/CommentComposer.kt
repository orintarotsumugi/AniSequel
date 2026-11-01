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
 * The comment editor.
 *
 * ## The shape, and where it comes from
 *
 * This is AniList's own composer, widened: a Cancel / title / Preview header, the
 * reply target as a chip above the field, a full-height box rather than a two-line
 * strip, and one bottom bar holding the format controls, the character counter and
 * the Post button. The point of copying it is that it is the editor people already
 * know - the field is big enough to see a paragraph, Preview shows what readers will
 * get rather than an approximation, and the counter says how much of the 8,000 has
 * been used instead of refusing on send.
 *
 * ## Why the bar is long
 *
 * AniList's comment markup is a dialect, not a subset: block quotes, headings,
 * centred spans, `img(url)`, `youtube(id)`, `webm(url)`, spoilers, `@mentions`. The
 * bar offers every one of them the app's own parser renders, because a composer
 * that writes a marker the reader's parser does not understand produces a preview
 * that disagrees with the posted comment - and a phone keyboard has no `~!` on it,
 * which is why a control has to exist rather than the documentation.
 *
 * The controls are in scrolling groups, divided by a hairline. Seventeen icons in
 * one undivided row is a strip to hunt through; four groups - media, emphasis,
 * blocks, embeds - is a strip you can find something in, and the order is by
 * frequency: the two things a phone is actually holding come first, where a thumb
 * starts.
 *
 * ## Why the draft is not owned here
 *
 * [draft] and [replyToName] live in the sheet above. A successful post clears them
 * and Cancel closes this pane while keeping the text - both are decisions about the
 * whole thread, not about the editor - and a draft recalled from `rememberSaveable`
 * after the pane was disposed is what makes Cancel safe.
 *
 * [gifPane] is a slot rather than a call into a store: this composable's job is the
 * draft and the controls around it. Who searches, what the results are and where
 * they came from belongs to the sheet, which hands in a function that is given the
 * "the reader chose this GIF" callback and inserts the result itself.
 */
@Composable
internal fun CommentComposer(
    draft: TextFieldValue,
    onDraftChange: (TextFieldValue) -> Unit,
    replyToName: String?,
    onClearReplyTo: () -> Unit,
    isPosting: Boolean,
    postError: String?,
    uploadNote: String?,
    isUploading: Boolean,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    onPickFromDevice: () -> Unit,
    /**
     * The GIF picker, as a pane.
     *
     * Handed the "the reader chose this GIF" callback rather than a store: the
     * browser records the pick itself, because it is where the chosen result still
     * knows its own tile dimensions - and the strip that shows the pick back has to
     * draw the small copy, not the full animation.
     */
    gifPane: @Composable ColumnScope.((String) -> Unit) -> Unit,
    /**
     * Overrides the header's title.
     *
     * Null keeps the composer's own "Write a Comment" / "Write a Reply". The edit
     * pane passes "Edit Comment" so the same editor does not greet somebody
     * rewriting an existing comment with a prompt to write a new one.
     */
    title: String? = null,
    /** The send button's idle label; null keeps "Post Comment" / "Post Reply". */
    submitLabel: String? = null,
    /** The send button's label while a request is out. */
    busyLabel: String = "Posting...",
    modifier: Modifier = Modifier
) {
    var pane by rememberSaveable { mutableIntStateOf(PANE_WRITE) }
    // The width a picked GIF is inserted at. Defaulted to the site's own strip
    // width rather than "original": a full-size animation dropped into a comment is
    // the one embed that pushes the whole thread off the screen.
    //
    // `""` is Original's spelling, because this is a saveable String and
    // [MediaAttachment.embedMarkdown] reads any value it cannot parse - the empty
    // one included - as "no width", which is the bare `img(url)` the site writes.
    var gifWidth by rememberSaveable { mutableStateOf("220") }
    // Stored as the enum name because `rememberSaveable` will not carry an enum
    // through a Bundle without a custom saver, and a row that reset to "Normal"
    // after a rotation the reader did not ask for is worse than a String.
    var gifFramingName by rememberSaveable { mutableStateOf(MediaAttachment.EmbedFraming.Plain.name) }
    val gifFraming = MediaAttachment.EmbedFraming.entries
        .firstOrNull { it.name == gifFramingName } ?: MediaAttachment.EmbedFraming.Plain
    var pendingInsert by remember { mutableStateOf<InsertKind?>(null) }
    val showingGifs = pane == PANE_GIFS

    val verdict = ContentLimits.checkReply(draft.text)
    val canSend = verdict == ContentLimits.ReplyVerdict.Valid && !isPosting && !isUploading

    fun apply(edit: DraftEdit) = onDraftChange(draft.withDraftEdit(edit))

    fun insertBlockAt(block: String) {
        apply(insertBlock(draft.text, draft.selection.start, draft.selection.end, block))
    }

    fun selection(): Pair<Int, Int> {
        val from = minOf(draft.selection.start, draft.selection.end).coerceIn(0, draft.text.length)
        val to = maxOf(draft.selection.start, draft.selection.end).coerceIn(from, draft.text.length)
        return from to to
    }

    // Opening the editor is the reader saying "I am writing", so the field takes
    // focus and the keyboard comes with it. Without this the first tap needed to
    // find the field again inside a pane that had just replaced the list - the
    // tap that opened the editor only opened the editor.
    //
    // Replayed when Preview goes back to Write, because that disposes the field
    // and composes a new one with no focus. One frame is awaited first: on the
    // frame the pane appears the field is composed but not yet placed, and a
    // focus request against a node with no coordinates is dropped silently.
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(pane) {
        if (pane != PANE_WRITE) return@LaunchedEffect
        withFrameNanos { }
        // The pane can be dismissed in the same frame the request is made (a
        // fast Cancel, or the sheet closing on a back gesture), and requesting
        // focus from an unattached node throws.
        runCatching { focusRequester.requestFocus() }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        ComposerHeader(
            replyToName = replyToName,
            title = title,
            pane = pane,
            isPosting = isPosting,
            onCancel = onCancel,
            onPane = { target -> pane = if (pane == target) PANE_WRITE else target }
        )

        when {
            showingGifs -> {
                GifWidthRow(gifWidth) { gifWidth = it }
                GifFramingRow(gifFraming) { gifFramingName = it.name }
                gifPane { url ->
                    insertBlockAt(MediaAttachment.embedMarkdown(url, gifWidth, gifFraming))
                    pane = PANE_WRITE
                }
            }

            pane == PANE_PREVIEW -> DraftBody(
                draft = draft,
                replyToName = replyToName,
                onClearReplyTo = onClearReplyTo,
                previewing = true
            )

            else -> DraftBody(
                draft = draft,
                replyToName = replyToName,
                isPosting = isPosting,
                onDraftChange = onDraftChange,
                onClearReplyTo = onClearReplyTo,
                previewing = false,
                focusRequester = focusRequester
            )
        }

        ComposerBottomBar(
            draft = draft,
            verdict = verdict,
            canSend = canSend,
            isBusy = isPosting || isUploading,
            postError = postError,
            uploadNote = uploadNote,
            replyToName = replyToName,
            gifsOpen = showingGifs,
            onEdit = { apply(it) },
            onInsertBlock = { block -> insertBlockAt(block) },
            onAskKind = { kind -> pendingInsert = kind },
            onPickFromDevice = onPickFromDevice,
            onToggleGifs = { pane = if (showingGifs) PANE_WRITE else PANE_GIFS },
            submitLabel = submitLabel,
            busyLabel = busyLabel,
            onSubmit = onSubmit
        )
    }

    pendingInsert?.let { kind ->
        val (from, to) = selection()
        InsertMarkupDialog(
            kind = kind,
            hasSelection = to > from,
            onDismiss = { pendingInsert = null },
            onInsert = { value ->
                apply(
                    if (kind == InsertKind.LINK) {
                        linkMarkdown(draft.text, from, to, value)
                    } else {
                        insertBlock(draft.text, from, to, embedMarkdown(kind, value))
                    }
                )
                pendingInsert = null
            }
        )
    }
}

private const val PANE_WRITE = 0
private const val PANE_PREVIEW = 1
private const val PANE_GIFS = 2

/**
 * The Cancel / title / Preview header.
 *
 * The title says Reply rather than Comment when the editor was opened against a
 * comment, because the reader should not have to remember which one they tapped.
 * Preview toggles rather than being a separate pane: the field is the same box, and
 * a toggle keeps the draft in place. The GIF browser is a third pane, entered and
 * left from the bar - Preview is not offered while it is up, because "preview" of a
 * results grid is nothing a reader means.
 */
@Composable
private fun ComposerHeader(
    replyToName: String?,
    title: String?,
    pane: Int,
    isPosting: Boolean,
    onCancel: () -> Unit,
    onPane: (Int) -> Unit
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
            text = when {
                pane == PANE_GIFS -> "Choose a GIF"
                title != null -> title
                replyToName != null -> "Write a Reply"
                else -> "Write a Comment"
            },
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = { onPane(PANE_PREVIEW) },
            enabled = !isPosting && pane != PANE_GIFS
        ) {
            Text(if (pane == PANE_PREVIEW) "Write" else "Preview")
        }
    }
}

/**
 * The pane under the header: the field when writing, the rendered draft when
 * previewing. Both keep the reply chip, because the mention is part of the text
 * being previewed - it used to be drawn only above the editor, so Preview showed a
 * comment without the `@name` that threads it.
 */
@Composable
private fun ColumnScope.DraftBody(
    draft: TextFieldValue,
    replyToName: String?,
    onClearReplyTo: () -> Unit,
    previewing: Boolean,
    isPosting: Boolean = false,
    onDraftChange: (TextFieldValue) -> Unit = {},
    focusRequester: FocusRequester? = null
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
                .testTag("activity_comment_preview")
        ) {
            if (draft.text.isBlank()) {
                Text(
                    text = "Nothing to preview yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                // The same renderer the posted comment goes through, so Preview
                // shows what readers will get rather than an approximation -
                // including the poster on an AniList title the draft links, and the
                // photo or GIF that was just uploaded or chosen.
                AniListMarkdownWithCovers(markdown = draft.text)
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
                .then(focusRequester?.let { Modifier.focusRequester(it) } ?: Modifier)
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
 * The field's next value, with the caret clamped to the text.
 *
 * `TextFieldValue` *requires* its selection and composing region to fall inside
 * its text - see its `init` - so a value that truncates the text and keeps the
 * old caret throws from the constructor. Every programmatic edit goes through
 * here for that reason: the format bar, the insert dialog, the attachment the
 * upload just produced, and the `@mention` the sheet adds when the reader taps
 * Reply. A programmatic edit drops the IME's composing range, which no longer
 * describes anything after the text changed.
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
            .padding(start = 14.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Replying to $name",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        Spacer(modifier = Modifier.width(4.dp))
        // 40dp, not the 28dp box that was sized to the glyph. Clearing the target
        // is the chip's only action, and it was the smallest target in the
        // composer - smaller than the format bar's 48dp controls despite deciding
        // who the comment is addressed to. The glyph inside stays 14dp.
        Box(
            modifier = Modifier
                .size(40.dp)
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
 * Whether the picked GIF goes in on its own, centred, or behind a spoiler.
 *
 * Beside the width row rather than in the format bar, because it belongs to the
 * embed about to be inserted: a reader choosing a reaction GIF is often choosing
 * one that spoils something, and the alternative - inserting it and then wrapping
 * the line by hand in `~!...!~` on a phone keyboard - is not a thing anybody does.
 */
@Composable
private fun GifFramingRow(selected: MediaAttachment.EmbedFraming, onSelect: (MediaAttachment.EmbedFraming) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MediaAttachment.EmbedFraming.entries.forEach { framing ->
            val label = when (framing) {
                MediaAttachment.EmbedFraming.Plain -> "Normal"
                MediaAttachment.EmbedFraming.Centred -> "Centred"
                MediaAttachment.EmbedFraming.Spoiler -> "Spoiler"
            }
            val isSelected = framing == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .clickable(onClickLabel = "Insert as $label") { onSelect(framing) }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("comment_gif_framing_$label")
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


/**
 * The widths a picked GIF can be inserted at.
 *
 * AniList's own editor offers exactly this choice, and it matters more on a phone
 * than on a desktop: a comment thread is read on a 360dp screen, so an original
 * 720px animation is a wall that pushes the conversation away. "Original" stays
 * first in the list because some GIFs *are* their size - a 60px sprite row blown up
 * to 220 is a blur - but it is not the default.
 */
@Composable
private fun GifWidthRow(selected: String, onSelect: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        (listOf<String?>(null) + MediaAttachment.EMBED_WIDTHS).forEach { width ->
            val label = width ?: "Original"
            // Compared through the row's own spelling of Original: the selected value
            // is a `String` in which `""` means Original, and `null` is what this
            // list stands for it, so the two would never match and picking Original
            // left every chip in the row looking unchosen.
            val isSelected = (width ?: "") == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        }
                    )
                    .clickable(onClickLabel = "Insert at $label width") { onSelect(width ?: "") }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .testTag("comment_gif_width_$label")
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
