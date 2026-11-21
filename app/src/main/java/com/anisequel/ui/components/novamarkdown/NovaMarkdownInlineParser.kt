package com.anisequel.ui.components.novamarkdown

import java.util.Locale

/**
 * The inline scanner: emphasis, links, images, mentions, HTML runs and inline
 * spoilers, left to right with an explicit depth cap.
 *
 * Every loop advances, which is the property that matters for a public bio - a
 * parse runs on the Compose main thread, so a non-advancing branch is an ANR.
 */


// ---------------------------------------------------------------------------
// Inline parser
// ---------------------------------------------------------------------------

internal fun parseNovaInlineLine(source: String): List<NovaInline> =
    mergeNovaText(parseNovaInlinesAt(normalizeNovaSource(source), 0, 0))

private fun parseNovaInlinesAt(
    text: String,
    depth: Int,
    spoilerDepth: Int
): List<NovaInline> {
    if (text.isEmpty()) return emptyList()
    // Past the cap the run is readable text. AniList bios nest links in bold in
    // spoilers, but nothing legitimate nests six deep, and a stack overflow on a
    // public bio is not a render.
    if (depth > MAX_INLINE_DEPTH) return listOf(NovaInline.Text(text))
    val tokens = mutableListOf<NovaInline>()
    val plain = StringBuilder()
    var i = 0

    fun flushPlain() {
        if (plain.isNotEmpty()) {
            tokens += NovaInline.Text(plain.toString())
            plain.clear()
        }
    }

    while (i < text.length) {
        val char = text[i]

        // Escaped markdown is literal text. This check has to beat every form
        // below, or `\*not italic\*` loses its backslashes and becomes italic.
        if (char == '\\' && i + 1 < text.length) {
            plain.append(text[i + 1])
            i += 2
            continue
        }

        val token = readNovaInlineToken(text, i, depth, spoilerDepth)
        if (token != null) {
            flushPlain()
            token.first?.let(tokens::add)
            i = token.second
            continue
        }

        plain.append(char)
        i++
    }

    flushPlain()
    return mergeNovaText(tokens)
}

private fun readNovaInlineToken(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline?, Int>? =
    readNovaMarkupToken(text, start, depth, spoilerDepth)
        ?: readNovaTextToken(text, start, depth, spoilerDepth)

private fun readNovaMarkupToken(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline?, Int>? {
    val char = text[start]
    if (char == '`') return readNovaCodeToken(text, start)
    if (char == '!' && start + 1 < text.length && text[start + 1] == '[') {
        return readNovaInlineImage(text, start)
    }
    if (char == '[') return readNovaInlineLink(text, start, depth, spoilerDepth)
    if (char == '<') return readNovaAngle(text, start, depth, spoilerDepth)
    if (char == '~' && start + 1 < text.length && text[start + 1] == '~') {
        return readNovaStrikeToken(text, start, depth, spoilerDepth)
    }
    if (char == '~' && start + 1 < text.length && text[start + 1] == '!' &&
        spoilerDepth < MAX_SPOILER_DEPTH
    ) {
        return readNovaInlineSpoilerToken(text, start, depth, spoilerDepth)
    }
    return null
}

private fun readNovaTextToken(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline?, Int>? {
    val char = text[start]
    if (char == '@' && (start == 0 || !text[start - 1].isLetterOrDigit())) {
        return readNovaMentionToken(text, start)
    }
    if (char == '*' || char == '_') {
        return readNovaEmphasis(text, start, depth, spoilerDepth)
    }
    if (startsNovaBareUrl(text, start)) {
        return readNovaBareUrl(text, start)
    }
    return null
}

private fun readNovaCodeToken(text: String, start: Int): Pair<NovaInline, Int>? {
    var ticks = 0
    while (start + ticks < text.length && text[start + ticks] == '`') ticks++
    val close = text.indexOf("`".repeat(ticks), start + ticks)
    if (close <= start + ticks) return null
    return NovaInline.Code(text.substring(start + ticks, close)) to close + ticks
}

private fun readNovaStrikeToken(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline, Int>? {
    val close = text.indexOf("~~", start + 2)
    if (close <= start + 2) return null
    return NovaInline.Strike(
        parseNovaInlinesAt(text.substring(start + 2, close), depth + 1, spoilerDepth)
    ) to close + 2
}

private fun readNovaInlineSpoilerToken(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline, Int>? {
    val close = text.indexOf("!~", start + 2)
    if (close <= start + 2) return null
    val source = text.substring(start + 2, close)
    val children = parseNovaInlinesAt(source, depth + 1, spoilerDepth + 1)
    return NovaInline.Spoiler(
        text = children.novaPlainText(),
        children = children
    ) to close + 2
}

private fun readNovaMentionToken(text: String, start: Int): Pair<NovaInline, Int>? {
    var end = start + 1
    while (end < text.length &&
        (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '-')
    ) {
        end++
    }
    if (end <= start + 1) return null
    return NovaInline.Mention(text.substring(start + 1, end)) to end
}

private fun startsNovaBareUrl(text: String, start: Int): Boolean {
    if (start > 0 && text[start - 1].isLetterOrDigit()) return false
    val char = text[start]
    return char == 'h' &&
        (text.startsWith("https://", start, true) || text.startsWith("http://", start, true)) ||
        char == 'a' && text.startsWith("anilist.co/", start, true) ||
        char == 'w' && text.startsWith("www.anilist.co/", start, true)
}

private fun mergeNovaText(tokens: List<NovaInline>): List<NovaInline> {
    if (tokens.size < 2) return tokens
    val merged = mutableListOf<NovaInline>()
    tokens.forEach { token ->
        val previous = merged.lastOrNull()
        if (token is NovaInline.Text && previous is NovaInline.Text) {
            merged[merged.lastIndex] = NovaInline.Text(previous.text + token.text)
        } else {
            merged += token
        }
    }
    return merged
}

private fun findClosingBracket(text: String, open: Int): Int {
    var depth = 1
    var i = open + 1
    while (i < text.length) {
        when (text[i]) {
            '\\' -> i += 2
            '[' -> {
                depth++
                i++
            }

            ']' -> {
                depth--
                if (depth == 0) return i
                i++
            }

            '`' -> {
                val end = text.indexOf('`', i + 1)
                i = if (end > 0) end + 1 else i + 1
            }

            else -> i++
        }
    }
    return -1
}

private fun readNovaInlineImage(text: String, start: Int): Pair<NovaInline.Image, Int>? {
    val closeBracket = findClosingBracket(text, start + 1)
    if (closeBracket < 0 || closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') {
        return null
    }
    val destination = readNovaDestination(text, closeBracket + 1) ?: return null
    val url = novaAbsoluteUrl(destination.first)
    if (url.isBlank()) return null
    val alt = text.substring(start + 2, closeBracket).trim().takeIf { it.isNotEmpty() }
    return NovaInline.Image(url, alt) to destination.second
}

private fun readNovaInlineLink(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline, Int>? {
    val closeBracket = findClosingBracket(text, start)
    if (closeBracket < 0 || closeBracket + 1 >= text.length || text[closeBracket + 1] != '(') {
        return null
    }
    val destination = readNovaDestination(text, closeBracket + 1) ?: return null
    val url = novaAbsoluteUrl(destination.first.trim())
    if (url.isBlank()) return null

    val labelSource = text.substring(start + 1, closeBracket)
    val children = parseNovaInlinesAt(labelSource, depth + 1, spoilerDepth)
    val labelIsImage = labelSource.trimStart().let {
        it.startsWith("![") ||
            Regex("""^img\d*%?\(""", RegexOption.IGNORE_CASE).containsMatchIn(it)
    }

    val entity = parseNovaEntityUrl(url)
    if (entity != null && !labelIsImage) {
        val label = children.novaPlainText().trim()
            .ifEmpty { novaTitleFromSlug(entity.second, entity.first, entity.third) }
        return NovaInline.MediaLink(
            label,
            novaEntityAbsoluteUrl(url),
            entity.first,
            entity.third
        ) to destination.second
    }

    return NovaInline.Link(
        url = if (entity != null) novaEntityAbsoluteUrl(url) else url,
        children = children.ifEmpty { listOf(NovaInline.Text(url)) }
    ) to destination.second
}

/**
 * The destination of a `](…)`, including a title and parentheses in the URL.
 *
 * AniList URLs are ordinary, but Wikipedia-style destinations are not: `(foo)`
 * in a path is legal and a naive `indexOf(')')` truncates the link at the first
 * one. Balanced parentheses are tracked here, and `<…>` is accepted for a URL
 * that really does contain a space.
 */
private fun readNovaDestination(text: String, openParen: Int): Pair<String, Int>? {
    var i = skipNovaWhitespace(text, openParen + 1)
    if (i >= text.length) return null

    val (url, afterUrl) = if (text[i] == '<') {
        readNovaAngleDestination(text, i) ?: return null
    } else {
        readNovaBareDestination(text, i)
    }
    i = skipNovaWhitespace(text, afterUrl)
    i = skipNovaDestinationTitle(text, i) ?: return null
    i = skipNovaWhitespace(text, i)

    if (i >= text.length || text[i] != ')') return null
    return url.trim() to i + 1
}

private fun skipNovaWhitespace(text: String, start: Int): Int {
    var i = start
    while (i < text.length && text[i].isWhitespace()) i++
    return i
}

private fun readNovaAngleDestination(text: String, start: Int): Pair<String, Int>? {
    var i = start + 1
    val url = StringBuilder()
    while (i < text.length && text[i] != '>') {
        if (text[i] == '\\' && i + 1 < text.length) {
            url.append(text[i + 1])
            i += 2
        } else {
            url.append(text[i])
            i++
        }
    }
    if (i >= text.length) return null
    return url.toString() to i + 1
}

private fun readNovaBareDestination(text: String, start: Int): Pair<String, Int> {
    var i = start
    var depth = 0
    val url = StringBuilder()
    while (i < text.length) {
        val char = text[i]
        when {
            char == '\\' -> {
                if (i + 1 < text.length) {
                    url.append(text[i + 1])
                    i += 2
                } else {
                    i++
                }
            }

            char == '(' -> {
                depth++
                url.append(char)
                i++
            }

            char == ')' -> {
                if (depth == 0) break
                depth--
                url.append(char)
                i++
            }

            char.isWhitespace() && depth == 0 -> break
            else -> {
                url.append(char)
                i++
            }
        }
    }
    return url.toString() to i
}

/** Consumes an optional `"title"`, `'title'` or `(title)` and returns its end. */
private fun skipNovaDestinationTitle(text: String, start: Int): Int? {
    var i = start
    if (i >= text.length || (text[i] != '"' && text[i] != '\'' && text[i] != '(')) return i
    val quote = if (text[i] == '(') ')' else text[i]
    i++
    while (i < text.length && text[i] != quote) {
        if (text[i] == '\\' && i + 1 < text.length) i++
        i++
    }
    if (i >= text.length) return null
    return i + 1
}

private fun readNovaAngle(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline?, Int>? {
    val close = text.indexOf('>', start + 1)
    if (close < 0) return null
    val raw = text.substring(start + 1, close)

    if (raw.startsWith("!--")) {
        val end = text.indexOf("-->", start + 4)
        val next = if (end >= 0) end + 3 else text.length
        return null to next
    }

    val autoLink = readNovaAngleAutolink(raw)
    if (autoLink != null) return autoLink to close + 1
    if (raw.startsWith("/")) return null to close + 1

    val nameMatch = Regex("""^([A-Za-z][A-Za-z0-9]*)\b(.*)$""", RegexOption.DOT_MATCHES_ALL)
        .matchEntire(raw) ?: return null to close + 1
    val name = nameMatch.groupValues[1].lowercase(Locale.ROOT)
    val attrs = nameMatch.groupValues[2]

    return when (name) {
        "br" -> NovaInline.Text("\n") to close + 1
        "img" -> readNovaAngleImage(text, start, close, raw)
        // `<escape>…</escape>` is AniList's literal wrapper: the text inside is
        // shown as written, so a URL in it is not turned into a card and any
        // markup in it is not interpreted.
        "escape" -> readNovaAngleEscape(text, close)
        // An opening anchor with no closing tag is malformed, not visible prose.
        // Consuming the tag keeps the raw `<a href=…>` out of the bio; the block
        // walk above usually keeps the matching close in the same paragraph.
        "a" -> readNovaAngleAnchor(text, close, attrs, depth, spoilerDepth)
            ?: (null to close + 1)
        "span", "div" -> readNovaAngleSpoiler(
            text,
            close,
            name,
            attrs,
            depth,
            spoilerDepth
        )
        "b", "strong", "i", "em", "u", "s", "del", "strike", "code" ->
            readNovaAngleFormatting(text, close, name, depth, spoilerDepth)
        else -> null to close + 1
    }
}

private fun readNovaAngleAutolink(raw: String): NovaInline? {
    val autoLink = raw.trim()
    val isHttp = autoLink.startsWith("http://", true) ||
        autoLink.startsWith("https://", true)
    val isBare = autoLink.startsWith("anilist.co/", true) ||
        autoLink.startsWith("www.anilist.co/", true)
    if (!isHttp && !isBare) return null

    val url = if (isHttp) novaAbsoluteUrl(autoLink) else "https://$autoLink"
    val entity = parseNovaEntityUrl(url)
    return if (entity != null) {
        NovaInline.MediaLink(
            label = novaTitleFromSlug(entity.second, entity.first, entity.third),
            url = url,
            type = entity.first,
            id = entity.third
        )
    } else {
        NovaInline.Link(url, listOf(NovaInline.Text(autoLink)))
    }
}

private fun readNovaAngleImage(
    text: String,
    start: Int,
    close: Int,
    raw: String
): Pair<NovaInline, Int> {
    val image = htmlNovaImage(raw)
    return (image ?: NovaInline.Text(text.substring(start, close + 1))) to close + 1
}

private fun readNovaAngleEscape(text: String, close: Int): Pair<NovaInline?, Int> {
    // Search for the whole closing tag. `indexOf("</escape")` also matches
    // `</escaped>`, and a later `>` may belong to an unrelated tag; either can
    // truncate the literal run.
    val end = ESCAPE_CLOSE.find(text, close + 1) ?: return null to close + 1
    return NovaInline.Text(text.substring(close + 1, end.range.first)) to end.range.last + 1
}

private fun readNovaAngleAnchor(
    text: String,
    close: Int,
    attrs: String,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline, Int>? {
    val end = findClosingTag(text, "a", close + 1) ?: return null
    val children = parseNovaInlinesAt(
        text.substring(close + 1, end.first),
        depth + 1,
        spoilerDepth
    )
    val href = novaHtmlAttribute(attrs, "href")?.trim()
    val token: NovaInline = if (href.isNullOrBlank()) {
        // `<a>text</a>` with no href is AniList's coloured text, not a link.
        // Drawing it as a link would make tapping it do nothing.
        NovaInline.Accent(children)
    } else {
        val entity = parseNovaEntityUrl(href)
        if (entity != null) {
            NovaInline.MediaLink(
                label = children.novaPlainText().trim(),
                url = novaEntityAbsoluteUrl(href),
                type = entity.first,
                id = entity.third
            )
        } else {
            NovaInline.Link(novaAbsoluteUrl(href), children)
        }
    }
    return token to end.last + 1
}

private fun readNovaAngleSpoiler(
    text: String,
    close: Int,
    name: String,
    attrs: String,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline?, Int> {
    if (!isNovaSpoiler(attrs) || spoilerDepth >= MAX_SPOILER_DEPTH) {
        return null to close + 1
    }
    val end = findClosingTag(text, name, close + 1) ?: return null to close + 1
    val children = parseNovaInlinesAt(
        text.substring(close + 1, end.first),
        depth + 1,
        spoilerDepth + 1
    )
    return NovaInline.Spoiler(
        text = children.novaPlainText(),
        children = children,
        label = novaSpoilerLabel(attrs)
    ) to end.last + 1
}

private fun readNovaAngleFormatting(
    text: String,
    close: Int,
    name: String,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline?, Int> {
    val end = findClosingTag(text, name, close + 1) ?: return null to close + 1
    val children = parseNovaInlinesAt(
        text.substring(close + 1, end.first),
        depth + 1,
        spoilerDepth
    )
    val token: NovaInline = when (name) {
        "b", "strong" -> NovaInline.Bold(children)
        "i", "em" -> NovaInline.Italic(children)
        "u" -> NovaInline.Underline(children)
        else -> if (name == "code") NovaInline.Mono(children) else NovaInline.Strike(children)
    }
    return token to end.last + 1
}

private fun readNovaEmphasis(
    text: String,
    start: Int,
    depth: Int,
    spoilerDepth: Int
): Pair<NovaInline, Int>? {
    val marker = text[start]
    var run = 1
    while (start + run < text.length && text[start + run] == marker) run++

    val size = when {
        run >= 3 -> 3
        run == 2 -> 2
        else -> 1
    }

    // `_` inside a word is a literal underscore, not emphasis. `snake_case` is
    // the shape this protects, and it is common in usernames and titles.
    if (marker == '_' && start > 0 && text[start - 1].isLetterOrDigit()) return null
    if (start + size >= text.length) return null

    val closeAt = findNovaEmphasisClose(text, start, marker, size) ?: return null
    val content = text.substring(start + size, closeAt)
    if (content.isEmpty()) return null

    val children = parseNovaInlinesAt(content, depth + 1, spoilerDepth)
    val token = when (size) {
        1 -> NovaInline.Italic(children)
        2 -> NovaInline.Bold(children)
        else -> NovaInline.BoldItalic(children)
    }
    return token to closeAt + size
}

private fun findNovaEmphasisClose(
    text: String,
    start: Int,
    marker: Char,
    size: Int
): Int? {
    var search = start + size
    while (search < text.length) {
        val close = text.indexOf(marker, search)
        if (close < 0 || close == start + size) return null

        if (close > 0 && text[close - 1] == '\\') {
            search = close + 1
            continue
        }
        if (marker == '_' && close + 1 < text.length && text[close + 1].isLetterOrDigit()) {
            search = close + 1
            continue
        }

        var closeRun = 1
        while (close + closeRun < text.length && text[close + closeRun] == marker) closeRun++

        // A single delimiter closes at the *end* of an odd run, so the two
        // remaining stars of `***` can close a nested bold run. A run of two
        // does not close italic at all: it opens bold instead.
        val valid = if (size == 1) closeRun % 2 == 1 else closeRun >= size
        if (!valid) {
            search = close + closeRun
            continue
        }

        val closeAt = if (size == 1) close + closeRun - 1 else close
        if (closeAt <= start + size) return null
        return closeAt
    }
    return null
}

private fun readNovaBareUrl(text: String, start: Int): Pair<NovaInline, Int>? {
    val end = readNovaBareUrlEnd(text, start)
    val (url, trailing) = trimNovaBareUrl(text, start, end)
    if (url.isEmpty()) return null

    val absolute = if (url.startsWith("http://", true) || url.startsWith("https://", true)) {
        url
    } else {
        "https://$url"
    }
    val entity = parseNovaEntityUrl(absolute)
    val token: NovaInline = when {
        entity != null -> NovaInline.MediaLink(
            label = novaTitleFromSlug(entity.second, entity.first, entity.third),
            url = absolute,
            type = entity.first,
            id = entity.third
        )

        IMAGE_EXTENSION.containsMatchIn(absolute) -> NovaInline.Image(absolute)
        else -> NovaInline.Link(absolute, listOf(NovaInline.Text(url)))
    }
    return token to end - trailing
}

private fun readNovaBareUrlEnd(text: String, start: Int): Int {
    var i = start
    var depth = 0
    while (i < text.length) {
        val char = text[i]
        if (char.isWhitespace() || char == '<' || char == '>' || char == '"') break
        if (char == '(') depth++
        if (char == ')') {
            if (depth == 0) break
            depth--
        }
        i++
    }
    return i
}

private fun trimNovaBareUrl(text: String, start: Int, end: Int): Pair<String, Int> {
    var url = text.substring(start, end)
    var trailing = 0
    while (url.isNotEmpty() && url.last() in ".,;:!?") {
        url = url.dropLast(1)
        trailing++
    }
    return url to trailing
}
