package com.anisequel.ui.screens.profile

/**
 * The composer's format bar, as pure text edits.
 *
 * ## Why this is not in the composable
 *
 * Every button on the bar does the same thing to a `String` and a selection, and
 * the mistakes are all off-by-one: a cursor left outside the markers, a reversed
 * selection producing an empty insertion, an index from before a truncation
 * pointing past the end. Those are the cases a JVM test can pin in milliseconds,
 * and a `TextFieldValue` inside a sheet is not. The composable keeps only the
 * mapping from this result to Compose's own value type.
 *
 * ## Markers
 *
 * These are AniList's own markdown spellings, and they are the same ones
 * [com.anisequel.ui.components.markdown.AniListMarkdownParser] renders - a
 * composer that writes a marker the reader's own parser does not understand is
 * a preview that disagrees with the posted comment.
 */
internal data class DraftEdit(
    val text: String,
    val selectionStart: Int,
    val selectionEnd: Int
)

/**
 * Wraps the selection in [prefix]/[suffix], or inserts the empty pair.
 *
 * With a selection the markers go outside it and the selection stays over the
 * text, so pressing Bold twice in a row is not a no-op on nothing - the second
 * press can still see what it is bolding. With a caret the pair is inserted and
 * the cursor is placed *between* the markers: pressing a format button and then
 * typing is the normal order, and a cursor left after both markers would put the
 * first keystroke outside the emphasis.
 *
 * A reversed selection (end before start) is accepted rather than rejected:
 * Android reports a drag from right to left that way, and dropping the selection
 * on the floor would make the button do nothing for half the gestures.
 */
internal fun wrapSelection(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    prefix: String,
    suffix: String = prefix
): DraftEdit {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val selected = text.substring(from, to)
    val replacement = prefix + selected + suffix
    val newText = text.substring(0, from) + replacement + text.substring(to)
    return if (from == to) {
        DraftEdit(newText, from + prefix.length, from + prefix.length)
    } else {
        DraftEdit(newText, from + prefix.length, from + prefix.length + selected.length)
    }
}

/**
 * Replaces the selection with [snippet] and puts the cursor after it.
 *
 * Used by the attach dialog, where the inserted text is already complete and
 * there is nothing left to type inside it.
 */
internal fun insertSnippet(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    snippet: String
): DraftEdit {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val newText = text.substring(0, from) + snippet + text.substring(to)
    val cursor = from + snippet.length
    return DraftEdit(newText, cursor, cursor)
}

/**
 * Prefixes a draft with `@name` for a reply to [name].
 *
 * AniList has no parent pointer on an activity reply: a reply is threaded by the
 * leading mention in its text, which is why the other clients' "Replying to X"
 * chip is backed by an `@X` the author can still delete. The mention goes in
 * once, and the cursor lands at the end so the reader keeps typing their comment
 * rather than having to step over the handle they did not write.
 *
 * A blank name -- a reply whose author AniList did not send -- leaves the draft
 * untouched: `@ ` is not a mention, it is a broken one.
 */
internal fun startReplyTo(text: String, name: String): DraftEdit {
    val handle = name.trim().removePrefix("@").trim()
    if (handle.isEmpty()) return DraftEdit(text, text.length, text.length)
    val mention = "@$handle"
    if (text == mention || text.startsWith("$mention ")) {
        return DraftEdit(text, text.length, text.length)
    }
    val newText = "$mention $text"
    return DraftEdit(newText, newText.length, newText.length)
}

/**
 * Whether an attachment field holds something the parser can render.
 *
 * Only `http(s)` is accepted. The comment is shown to other people and the
 * renderer only embeds an absolute URL: a relative path, a `data:` payload or
 * anything else the type-it-yourself field allows would be stored as literal
 * text in the comment, which is worse than refusing the insert.
 */
internal fun isAttachableImageUrl(raw: String): Boolean {
    val trimmed = raw.trim()
    val scheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> "https://"
        trimmed.startsWith("http://", ignoreCase = true) -> "http://"
        else -> return false
    }
    return trimmed.length > scheme.length && !trimmed.contains(' ')
}

/**
 * Prefixes every line the selection touches, for the block-level markup.
 *
 * A header, a quote and a list are all "this line, not this span": AniList's
 * parser matches them at the start of a line, so a control that wrapped the
 * selection in `# ` would put the hash in the middle of a sentence. The lines the
 * reader has actually selected get the prefix; with a bare caret the current line
 * is the target, because pressing Quote and then typing is the ordinary order.
 *
 * Removing a prefix is not offered. A toolbar that adds and removes on a toggle
 * has to remember what it did to each line, and a comment is plain text that the
 * author can edit by hand - the second press of Header making a second `#` is the
 * honest behaviour, and the one the reader can undo with Backspace.
 */
internal fun prefixLines(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    prefix: String
): DraftEdit {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (from - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }
    val block = text.substring(lineStart, lineEnd)
    if (block.isEmpty()) {
        val inserted = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        val caret = lineStart + prefix.length
        return DraftEdit(inserted, caret, caret)
    }

    // A blank line inside the selection stays blank: a `> ` on an empty line is a
    // quote mark that quotes nothing, and it shows up in the posted comment.
    val lines = block.split("\n")
    val mapped = lines.map { line -> if (line.isBlank()) line else prefix + line }
    val newText = text.substring(0, lineStart) + mapped.joinToString("\n") + text.substring(lineEnd)

    /**
     * Where an index in the block lands once each line has been rewritten.
     *
     * Each line can gain a prefix or gain nothing, so the shift depends on which
     * line the index is in - and walking it is the only way to get the *start* of
     // selection right. Adding the total number of characters inserted (the old
     // behaviour) pushed a selection that began mid-block past its own beginning:
     // quoting two lines of a three-line paragraph marked the wrong text, and the
     // next press of the button acted on that.
     */
    fun shift(index: Int): Int {
        var remaining = index
        var rebuilt = lineStart
        for (line in lines.indices) {
            val oldLength = lines[line].length
            if (remaining < oldLength) return rebuilt + remaining
            if (remaining == oldLength) return rebuilt + mapped[line].length
            remaining -= oldLength + 1
            rebuilt += mapped[line].length + 1
        }
        return rebuilt
    }

    return DraftEdit(newText, shift(from - lineStart), shift(to - lineStart))
}

/**
 * Replaces the selection with [snippet] and puts the cursor after it.
 *
 * Used by the attach dialog, where the inserted text is already complete and
 * there is nothing left to type inside it.
 */
internal fun insertSnippet(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    snippet: String
): DraftEdit {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val newText = text.substring(0, from) + snippet + text.substring(to)
    val cursor = from + snippet.length
    return DraftEdit(newText, cursor, cursor)
}

/**
 * Prefixes a draft with `@name` for a reply to [name].
 *
 * AniList has no parent pointer on an activity reply: a reply is threaded by the
 * leading mention in its text, which is why the other clients' "Replying to X"
 * chip is backed by an `@X` the author can still delete. The mention goes in
 * once, and the cursor lands at the end so the reader keeps typing their comment
 * rather than having to step over the handle they did not write.
 *
 * A blank name -- a reply whose author AniList did not send -- leaves the draft
 * untouched: `@ ` is not a mention, it is a broken one.
 */
internal fun startReplyTo(text: String, name: String): DraftEdit {
    val handle = name.trim().removePrefix("@").trim()
    if (handle.isEmpty()) return DraftEdit(text, text.length, text.length)
    val mention = "@$handle"
    if (text == mention || text.startsWith("$mention ")) {
        return DraftEdit(text, text.length, text.length)
    }
    val newText = "$mention $text"
    return DraftEdit(newText, newText.length, newText.length)
}

/**
 * Whether an attachment field holds something the parser can render.
 *
 * Only `http(s)` is accepted. The comment is shown to other people and the
 * renderer only embeds an absolute URL: a relative path, a `data:` payload or
 * anything else the type-it-yourself field allows would be stored as literal
 * text in the comment, which is worse than refusing the insert.
 */
internal fun isAttachableImageUrl(raw: String): Boolean {
    val trimmed = raw.trim()
    val scheme = when {
        trimmed.startsWith("https://", ignoreCase = true) -> "https://"
        trimmed.startsWith("http://", ignoreCase = true) -> "http://"
        else -> return false
    }
    return trimmed.length > scheme.length && !trimmed.contains(' ')
}

/**
 * Prefixes every line the selection touches, for the block-level markup.
 *
 * A header, a quote and a list are all "this line, not this span": AniList's
 * parser matches them at the start of a line, so a control that wrapped the
 * selection in `# ` would put the hash in the middle of a sentence. The lines the
 * reader has actually selected get the prefix; with a bare caret the current line
 * is the target, because pressing Quote and then typing is the ordinary order.
 *
 * Removing a prefix is not offered. A toolbar that adds and removes on a toggle
 * has to remember what it did to each line, and a comment is plain text that the
 * author can edit by hand - the second press of Header making a second `#` is the
 * honest behaviour, and the one the reader can undo with Backspace.
 */
internal fun prefixLines(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    prefix: String
): DraftEdit {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val lineStart = text.lastIndexOf('\n', (from - 1).coerceAtLeast(0))
        .let { if (it < 0) 0 else it + 1 }
    val lineEnd = text.indexOf('\n', to).let { if (it < 0) text.length else it }
    val block = text.substring(lineStart, lineEnd)
    if (block.isEmpty()) {
        val inserted = text.substring(0, lineStart) + prefix + text.substring(lineStart)
        return DraftEdit(inserted, lineStart + prefix.length, lineStart + prefix.length)
    }
    val prefixed = block.lines().joinToString("\n") { line ->
        // A blank line inside the selection stays blank: a `> ` on an empty line
        // is a quote mark that quotes nothing, and it shows up in the posted
        // comment.
        if (line.isBlank()) line else prefix + line
    }
    val newText = text.substring(0, lineStart) + prefixed + text.substring(lineEnd)
    val added = prefixed.length - block.length
    return DraftEdit(newText, from + added, to + added)
}

/**
 * Inserts block-level markup - an embed, a centred span - on its own line.
 *
 * AniList's `img(url)`, `youtube(id)` and `webm(url)` are matched only when the
 * whole line is the tag, so an insert that dropped them beside a word would store
 * literal markup in the comment. This pads the line breaks the parser needs and
 * leaves the caret after the block, ready for the next sentence.
 */
internal fun insertBlock(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    block: String
): DraftEdit {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val snippet = buildString {
        // Newlines are appended conditionally rather than concatenated around
        // parenthesised `if`s: `x + (if (c) "\n" else "") + y` only reads
        // correctly with its parentheses, and the version without them silently
        // folds the tail into the `else` branch.
        if (from > 0 && text[from - 1] != '\n') append('\n')
        append(block)
        if (to < text.length && text[to] != '\n') append('\n')
    }
    val newText = text.substring(0, from) + snippet + text.substring(to)
    val cursor = from + snippet.length
    return DraftEdit(newText, cursor, cursor)
}

/** `[selected](url)`, or `[label](url)` with nothing selected. */
internal fun linkMarkdown(
    text: String,
    selectionStart: Int,
    selectionEnd: Int,
    url: String
): DraftEdit {
    val from = minOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val to = maxOf(selectionStart, selectionEnd).coerceIn(0, text.length)
    val label = text.substring(from, to).ifBlank { url }
    return insertSnippet(text, from, to, "[$label]($url)")
}

/** What the composer's "more markup" dialog is being asked to produce. */
internal enum class InsertKind {
    /** `img(url)` - a block image, the site's own tag. */
    IMAGE,

    /** `youtube(id-or-url)` - the site plays it in place. */
    YOUTUBE,

    /** `webm(url)` - the site's video embed. */
    WEBM,

    /** `[label](url)` over the selection. */
    LINK;

    /** The label and the placeholder the dialog shows for this kind. */
    val label: String
        get() = when (this) {
            IMAGE -> "Image"
            YOUTUBE -> "YouTube"
            WEBM -> "WebM video"
            LINK -> "Link"
        }

    val placeholder: String
        get() = when (this) {
            IMAGE -> "https://.../picture.png"
            YOUTUBE -> "https://www.youtube.com/watch?v=..."
            WEBM -> "https://.../clip.webm"
            LINK -> "https://..."
        }

    /** Whether the value has to be a URL the app will accept. */
    val wantsUrl: Boolean
        get() = this != YOUTUBE
}

/**
 * The markup one insert kind produces from a value.
 *
 * A YouTube id is accepted as well as a link because that is what AniList itself
 * takes - `youtube(dQw4w9WgXcQ)` and `youtube(https://youtu.be/dQw4w9WgXcQ)` both
 * render - and a composer that insisted on the full URL would refuse the form the
 * site's own documentation leads with.
 */
internal fun embedMarkdown(kind: InsertKind, value: String): String = when (kind) {
    InsertKind.IMAGE -> "img(${value.trim()})"
    InsertKind.YOUTUBE -> "youtube(${value.trim().removeYouTubeWrapper()})"
    InsertKind.WEBM -> "webm(${value.trim()})"
    InsertKind.LINK -> value.trim()
}

/** Strips the watch-url wrapper down to the bare video id. */
private fun String.removeYouTubeWrapper(): String {
    val id = listOf("watch?v=", "youtu.be/", "embed/", "shorts/", "v=")
        .firstOrNull { contains(it, ignoreCase = true) }
        ?: return this
    return substringAfter(id, this).substringBefore('&').substringBefore('?')
}

/**
 * Whether a value the reader typed can become [kind]'s markup.
 *
 * A YouTube id or link goes through if it has any content at all, since both
 * forms are valid; every other kind needs an absolute http(s) URL for the same
 * reason [isAttachableImageUrl] does - a relative path or a `data:` payload is
 * stored as literal text the renderer cannot embed, and the author finds that out
 * after the comment is already public.
 */
internal fun isUsableInsert(kind: InsertKind, raw: String): Boolean {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return false
    if (kind == InsertKind.YOUTUBE) return true
    return isAttachableImageUrl(trimmed)
}

/**
 * The quote block a "Quote" action puts into the draft.
 *
 * Every line of the quoted comment goes behind `> `, and a blank line follows for
 * the answer - AniList renders a quoted block, and a reply that ran its own first
 * sentence onto the end of the quote is what the site's own quote button avoids by
 * inserting the break. The author's name leads the block only when the quoted text
 * does not already start with a mention, because quoting a reply into a thread that
 * was about to @-mention the same person should not say it twice.
 */
internal fun quoteBlock(text: String, authorName: String?): String {
    val body = text.trim().lines().joinToString("\n") { line -> "> $line" }
    val who = authorName?.trim()?.takeIf { it.isNotEmpty() }
    val credit = if (who != null && !text.trimStart().startsWith("@$who")) {
        "$who wrote\n"
    } else {
        ""
    }
    return "$credit$body\n\n"
}
