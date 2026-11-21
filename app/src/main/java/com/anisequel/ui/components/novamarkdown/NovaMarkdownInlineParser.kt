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

        if (char == '`') {
            val runEnd = i
            var ticks = 0
            while (runEnd + ticks < text.length && text[runEnd + ticks] == '`') ticks++
            val close = text.indexOf("`".repeat(ticks), i + ticks)
            if (close > i + ticks) {
                flushPlain()
                tokens += NovaInline.Code(text.substring(i + ticks, close))
                i = close + ticks
                continue
            }
            plain.append(char)
            i++
            continue
        }

        if (char == '!' && i + 1 < text.length && text[i + 1] == '[') {
            val image = readNovaInlineImage(text, i)
            if (image != null) {
                flushPlain()
                tokens += image.first
                i = image.second
                continue
            }
            plain.append(char)
            i++
            continue
        }

        if (char == '[') {
            val link = readNovaInlineLink(text, i, depth, spoilerDepth)
            if (link != null) {
                flushPlain()
                tokens += link.first
                i = link.second
                continue
            }
            plain.append(char)
            i++
            continue
        }

        if (char == '<') {
            val angle = readNovaAngle(text, i, depth, spoilerDepth)
            if (angle != null) {
                flushPlain()
                angle.first?.let(tokens::add)
                i = angle.second
                continue
            }
            plain.append(char)
            i++
            continue
        }

        if (char == '~' && i + 1 < text.length && text[i + 1] == '~') {
            val close = text.indexOf("~~", i + 2)
            if (close > i + 2) {
                flushPlain()
                tokens += NovaInline.Strike(
                    parseNovaInlinesAt(text.substring(i + 2, close), depth + 1, spoilerDepth)
                )
                i = close + 2
                continue
            }
            plain.append(char)
            i++
            continue
        }

        if (char == '~' && i + 1 < text.length && text[i + 1] == '!' &&
            spoilerDepth < MAX_SPOILER_DEPTH
        ) {
            val close = text.indexOf("!~", i + 2)
            if (close > i + 2) {
                flushPlain()
                val source = text.substring(i + 2, close)
                val children = parseNovaInlinesAt(source, depth + 1, spoilerDepth + 1)
                tokens += NovaInline.Spoiler(
                    text = children.novaPlainText(),
                    children = children
                )
                i = close + 2
                continue
            }
            plain.append(char)
            i++
            continue
        }

        if (char == '@' && (i == 0 || !text[i - 1].isLetterOrDigit())) {
            var end = i + 1
            while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '-')) {
                end++
            }
            if (end > i + 1) {
                flushPlain()
                tokens += NovaInline.Mention(text.substring(i + 1, end))
                i = end
                continue
            }
            plain.append(char)
            i++
            continue
        }

        if (char == '*' || char == '_') {
            val emphasis = readNovaEmphasis(text, i, depth, spoilerDepth)
            if (emphasis != null) {
                flushPlain()
                tokens += emphasis.first
                i = emphasis.second
                continue
            }
            plain.append(char)
            i++
            continue
        }

        val startsBareUrl = char == 'h' &&
            (text.startsWith("https://", i, true) || text.startsWith("http://", i, true)) ||
            char == 'a' && text.startsWith("anilist.co/", i, true) ||
            char == 'w' && text.startsWith("www.anilist.co/", i, true)
        if (startsBareUrl && (i == 0 || !text[i - 1].isLetterOrDigit())) {
            val bare = readNovaBareUrl(text, i)
            if (bare != null) {
                flushPlain()
                tokens += bare.first
                i = bare.second
                continue
            }
        }

        plain.append(char)
        i++
    }

    flushPlain()
    return mergeNovaText(tokens)
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
    var i = openParen + 1
    while (i < text.length && text[i].isWhitespace()) i++
    if (i >= text.length) return null

    val url = StringBuilder()
    if (text[i] == '<') {
        i++
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
        i++
    } else {
        var depth = 0
        while (i < text.length) {
            when (val char = text[i]) {
                '\\' -> {
                    if (i + 1 < text.length) {
                        url.append(text[i + 1])
                        i += 2
                    } else {
                        i++
                    }
                }

                '(' -> {
                    depth++
                    url.append(char)
                    i++
                }

                ')' -> {
                    if (depth == 0) break
                    depth--
                    url.append(char)
                    i++
                }

                else -> {
                    if (char.isWhitespace() && depth == 0) break
                    url.append(char)
                    i++
                }
            }
        }
    }

    while (i < text.length && text[i].isWhitespace()) i++
    if (i < text.length && (text[i] == '"' || text[i] == '\'' || text[i] == '(')) {
        val quote = if (text[i] == '(') ')' else text[i]
        i++
        while (i < text.length && text[i] != quote) {
            if (text[i] == '\\' && i + 1 < text.length) i++
            i++
        }
        if (i >= text.length) return null
        i++
        while (i < text.length && text[i].isWhitespace()) i++
    }

    if (i >= text.length || text[i] != ')') return null
    return url.toString().trim() to i + 1
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
        return null to if (end >= 0) end + 3 else text.length
    }

    val autoLink = raw.trim()
    val autoLinkIsHttp = autoLink.startsWith("http://", true) || autoLink.startsWith("https://", true)
    val autoLinkIsBare = autoLink.startsWith("anilist.co/", true) ||
        autoLink.startsWith("www.anilist.co/", true)
    if (autoLinkIsHttp || autoLinkIsBare) {
        val url = if (autoLinkIsHttp) novaAbsoluteUrl(autoLink) else "https://$autoLink"
        val entity = parseNovaEntityUrl(url)
        val token: NovaInline = if (entity != null) {
            NovaInline.MediaLink(
                label = novaTitleFromSlug(entity.second, entity.first, entity.third),
                url = url,
                type = entity.first,
                id = entity.third
            )
        } else {
            NovaInline.Link(url, listOf(NovaInline.Text(autoLink)))
        }
        return token to close + 1
    }

    if (raw.startsWith("/")) return null to close + 1

    val nameMatch = Regex("""^([A-Za-z][A-Za-z0-9]*)\b(.*)$""", RegexOption.DOT_MATCHES_ALL)
        .matchEntire(raw) ?: return null to close + 1
    val name = nameMatch.groupValues[1].lowercase(Locale.ROOT)
    val attrs = nameMatch.groupValues[2]

    return when (name) {
        "br" -> NovaInline.Text("\n") to close + 1

        "img" -> {
            val image = htmlNovaImage(raw)
            (image ?: NovaInline.Text(text.substring(start, close + 1))) to close + 1
        }

        "a" -> {
            val end = findClosingTag(text, "a", close + 1) ?: return null
            val children = parseNovaInlinesAt(
                text.substring(close + 1, end.first),
                depth + 1,
                spoilerDepth
            )
            val href = novaHtmlAttribute(attrs, "href")?.trim()
            val token: NovaInline = if (href.isNullOrBlank()) {
                // `<a>text</a>` with no href is AniList's coloured text, not a
                // link. Drawing it as a link would make tapping it do nothing.
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
            token to end.last + 1
        }

        "span", "div" -> {
            if (!isNovaSpoiler(attrs)) return null to close + 1
            // The cap is spent on markup as well as on the `~!` spelling; a
            // hand-edited pile of nested spans must degrade to text, not recurse.
            if (spoilerDepth >= MAX_SPOILER_DEPTH) return null to close + 1
            val end = findClosingTag(text, name, close + 1) ?: return null to close + 1
            val children = parseNovaInlinesAt(
                text.substring(close + 1, end.first),
                depth + 1,
                spoilerDepth + 1
            )
            NovaInline.Spoiler(
                text = children.novaPlainText(),
                children = children,
                label = novaSpoilerLabel(attrs)
            ) to end.last + 1
        }

        "b", "strong", "i", "em", "u", "s", "del", "strike", "code" -> {
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
            token to end.last + 1
        }

        else -> null to close + 1
    }
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

        val content = text.substring(start + size, closeAt)
        if (content.isEmpty()) return null
        val children = parseNovaInlinesAt(content, depth + 1, spoilerDepth)
        val token = when (size) {
            1 -> NovaInline.Italic(children)
            2 -> NovaInline.Bold(children)
            else -> NovaInline.BoldItalic(children)
        }
        return token to closeAt + 1
    }
    return null
}

private fun readNovaBareUrl(text: String, start: Int): Pair<NovaInline, Int>? {
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

    var url = text.substring(start, i)
    var trailing = 0
    while (url.isNotEmpty() && url.last() in ".,;:!?") {
        url = url.dropLast(1)
        trailing++
    }
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
    return token to i - trailing
}
