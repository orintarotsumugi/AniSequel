package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import com.anisequel.ui.components.expressive.ExpressiveShapes
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
    /**
     * This draft's own photo picker: the upload in flight, what it reported, and
     * the file it wrote into the text.
     *
     * Handed as one object rather than three arguments because the three belong to
     * each other: the size and framing rows appear only while the picker holds a
     * file that is still in the draft, and the note is the upload's answer about
     * that same file.
     */
    photo: PhotoAttachment,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
    /**
     * How a picked photo is written into the draft: its size and framing.
     *
     * Held by the sheet, like the draft itself, because the sheet owns the picker and
     * the upload - the composer can show the choice, but only the code that receives
     * the host's URL can use it. Two of these exist in the sheet (the new-comment
     * draft and the edit draft), and each gets its own style so an attachment made
     * while editing cannot land in the other field's shape.
     */
    photoStyle: EmbedStyle = EmbedStyle(),
    onPhotoStyleChange: (EmbedStyle) -> Unit = {},
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
    val canSend = verdict == ContentLimits.ReplyVerdict.Valid && !isPosting && !photo.isUploading

    fun apply(edit: DraftEdit) = onDraftChange(draft.withDraftEdit(edit))

    fun insertBlockAt(block: String) {
        apply(insertBlock(draft.text, draft.selection.start, draft.selection.end, block))
    }

    /**
     * Rewrites the attached photo's markup when one of its chips moves.
     *
     * This is what makes the row mean something now that it appears *with* the file
     * rather than before it: the upload wrote `img220(url)` with whatever was selected
     * at that moment, so a reader who taps 400 afterwards would be choosing the size of
     * the next photo while looking at this one. The embed is found by its URL and
     * replaced whole, wrapper included. The reader's selection is carried through
     * the rewrite rather than moved to the embed: a chip tap while typing below the
     * picture used to jump the caret out of the sentence.
     */
    fun restyleAttachedPhoto(url: String, style: EmbedStyle) {
        val span = MediaAttachment.embedSpanFor(draft.text, url) ?: return
        val markup = MediaAttachment.embedMarkdown(url, style.width, style.framing)
        apply(
            replaceEmbedSpan(
                text = draft.text,
                span = span,
                markup = markup,
                selectionStart = draft.selection.start,
                selectionEnd = draft.selection.end
            )
        )
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
                AttachmentEmbedRows(
                    style = EmbedStyle(gifWidth, gifFraming),
                    onStyleChange = { next ->
                        gifWidth = next.width
                        gifFramingName = next.framing.name
                    },
                    caption = "Insert this GIF",
                    tagPrefix = "comment_gif_embed"
                )
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

        // The two commands that describe the file the attach button is about to
        // pick - the size AniList draws it at and the framing around it. They sit on
        // the Write pane because that is where the button is, and the size has to be
        // chosen before the URL is written: a reader cannot be expected to retype
        // `img220(` around a link they did not type.
        //
        // They are *not* drawn when there is no file, which used to be the case on
        // every single Write pane: five chips and three framing chips above an empty
        // field, reading as a photo already being attached and as the editor asking
        // about a size before there was anything to size. See [showsPhotoEmbedRows].
        PhotoEmbedCommands(
            pane = pane,
            draftText = draft.text,
            photo = photo,
            style = photoStyle,
            onStyleChange = onPhotoStyleChange,
            onRestyle = { url, next -> restyleAttachedPhoto(url, next) }
        )

        ComposerBottomBar(
            draft = draft,
            verdict = verdict,
            canSend = canSend,
            isBusy = isPosting || photo.isUploading,
            postError = postError,
            uploadNote = photo.note,
            isUploading = photo.isUploading,
            replyToName = replyToName,
            gifsOpen = showingGifs,
            onEdit = { apply(it) },
            onInsertBlock = { block -> insertBlockAt(block) },
            onAskKind = { kind -> pendingInsert = kind },
            onPickFromDevice = photo.launch,
            onToggleGifs = { pane = if (showingGifs) PANE_WRITE else PANE_GIFS },
            submitLabel = submitLabel,
            busyLabel = busyLabel,
            onSubmit = onSubmit
        )
    }

    InsertMarkupHost(
        kind = pendingInsert,
        draft = draft,
        onDismiss = { pendingInsert = null },
        onEdit = { edit ->
            apply(edit)
            pendingInsert = null
        }
    )
}

/**
 * The photo's size and framing chips, or nothing when there is no photo to describe.
 *
 * Split out of [CommentComposer] because it is the one part of the editor whose
 * visibility rule is about a *file* rather than about a pane: the chips appear with
 * the upload's URL in the draft and disappear when the reader deletes the embed.
 * Keeping that rule here also keeps the restyle branch - the chip moved, so rewrite
 * the embed that is already in the text - out of the composer's own body.
 */
@Composable
private fun PhotoEmbedCommands(
    pane: Int,
    draftText: String,
    photo: PhotoAttachment,
    style: EmbedStyle,
    onStyleChange: (EmbedStyle) -> Unit,
    onRestyle: (String, EmbedStyle) -> Unit
) {
    val photoUrl = photo.attachedUrl
    if (pane != PANE_WRITE ||
        !showsPhotoEmbedRows(draftText, photoUrl, photo.isUploading)
    ) {
        return
    }
    AttachmentEmbedRows(
        style = style,
        onStyleChange = { next ->
            onStyleChange(next)
            // In flight there is no embed to rewrite yet: the URL exists only once
            // the upload answers, and rewriting it early would fight the writer.
            if (!photo.isUploading && photoUrl != null) onRestyle(photoUrl, next)
        },
        caption = if (photo.isUploading) "Uploading a photo…" else "Attached photo",
        isBusy = photo.isUploading,
        tagPrefix = "comment_photo_embed",
        thumbnailUrl = photoUrl.takeIf { !photo.isUploading }
    )
}

/**
 * The insert dialog, with the draft's selection read as the span to replace.
 *
 * A host of its own rather than an `?.let` in the composer: the dialog is a window
 * with its own lifetime, and the selection it acts on is a *range*, which is the
 * part that has to be normalised before it reaches any markup helper.
 */
@Composable
private fun InsertMarkupHost(
    kind: InsertKind?,
    draft: TextFieldValue,
    onDismiss: () -> Unit,
    onEdit: (DraftEdit) -> Unit
) {
    if (kind == null) return
    val from = minOf(draft.selection.start, draft.selection.end).coerceIn(0, draft.text.length)
    val to = maxOf(draft.selection.start, draft.selection.end).coerceIn(from, draft.text.length)
    InsertMarkupDialog(
        kind = kind,
        hasSelection = to > from,
        onDismiss = onDismiss,
        onInsert = { value ->
            onEdit(
                if (kind == InsertKind.LINK) {
                    linkMarkdown(draft.text, from, to, value)
                } else {
                    insertBlock(draft.text, from, to, embedMarkdown(kind, value))
                }
            )
        }
    )
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
            .clip(ExpressiveShapes.pill)
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
