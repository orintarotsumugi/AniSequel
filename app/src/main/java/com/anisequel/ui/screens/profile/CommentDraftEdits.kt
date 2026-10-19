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
