package com.anisequel.ui.components.novamarkdown

import java.util.Locale

/**
 * The AniList Markdown parser for NovaMarkdown.
 *
 * ## What this is written against
 *
 * AniList's own editor output and the community's AniFlavored Markdown guide
 * (`files.kiniro.uk/anilist-flavored-markdown-v1.md`), not GitHub Flavored
 * Markdown. The differences that matter are all here:
 *
 *  - a single newline is a line break, so paragraph lines are kept rather than
 *    joined into one line;
 *  - `~~~…~~~` centres, and so does `<center>` plus `align="…"` on `<p>`/`<div>`;
 *  - `img420(url)` and `img50%(url)` size an image;
 *  - `~!…!~` is a spoiler, as are `<span class="markdown-spoiler">` and
 *    `<div rel="spoiler">`;
 *  - `youtube(id)` and `webm(url)` are embeds;
 *  - `__text__` is bold (not underlined), and `<u>` is not allowed on the site;
 *  - AniList entity links become preview cards.
 *
 * ## Why the parser is hand-written
 *
 * Every regex-first implementation of this dialect hits the same two walls: a
 * link label may itself contain a bold run or an image, and an emphasis run may
 * contain a link. The parser below is a small recursive-descent scanner: blocks
 * first, then inline tokens left to right with an explicit nesting cap. Every
 * loop advances, which is the property that matters on a public bio - a parse
 * runs on the Compose main thread, so a non-advancing branch is an ANR.
 */


internal const val MAX_INLINE_DEPTH = 6
internal const val MAX_BLOCK_DEPTH = 8
internal const val MAX_SPOILER_DEPTH = 4

internal fun parseNovaMarkdown(source: String): List<NovaBlock> {
    if (source.isBlank()) return emptyList()
    val normalized = normalizeNovaSource(source)
    return parseNovaBlocks(normalized.lines(), NovaAlign.Start, 0)
}

// ---------------------------------------------------------------------------
// Normalisation
// ---------------------------------------------------------------------------

internal val HTML_COMMENT = Regex("""<!--[\s\S]*?-->""")
internal val HTML_SCRIPT = Regex("""<script\b[\s\S]*?</script\s*>""", RegexOption.IGNORE_CASE)
internal val HTML_STYLE = Regex("""<style\b[\s\S]*?</style\s*>""", RegexOption.IGNORE_CASE)
internal val HTML_BREAK = Regex("""<br\s*/?>""", RegexOption.IGNORE_CASE)
internal val HTML_ENTITY = Regex("""&(#x?[0-9A-Fa-f]+|[A-Za-z][A-Za-z0-9]+);""")

internal val NAMED_ENTITIES = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'",
    "nbsp" to " ", "ensp" to " ", "emsp" to " ", "thinsp" to " ",
    "lpar" to "(", "rpar" to ")", "lsqb" to "[", "rsqb" to "]",
    "lcub" to "{", "rcub" to "}", "verbar" to "|", "vert" to "|",
    "tilde" to "~", "excl" to "!", "num" to "#", "dollar" to "$",
    "percnt" to "%", "ast" to "*", "plus" to "+", "comma" to ",",
    "period" to ".", "sol" to "/", "colon" to ":", "semi" to ";",
    "equals" to "=", "quest" to "?", "commat" to "@", "lowbar" to "_",
    "hyphen" to "-", "dash" to "-", "ndash" to "–", "mdash" to "—",
    "hellip" to "…", "copy" to "©", "reg" to "®", "trade" to "™",
    "deg" to "°", "middot" to "·", "bull" to "•", "laquo" to "«",
    "raquo" to "»", "ldquo" to "“", "rdquo" to "”", "lsquo" to "‘",
    "rsquo" to "’", "hearts" to "♥", "star" to "☆", "check" to "✓",
)

/**
 * One pass over the raw document.
 *
 * HTML that has no block meaning is removed here rather than in the inline
 * walk because a bio may carry a `<script>` or a `<style>` from a pasted widget,
 * and neither should ever reach a renderer. `<br>` becomes a newline, which is
 * what AniList itself does with it. Entities are decoded once, here, so a URL
 * captured later (`&amp;` in a query string) is the URL the author wrote.
 */
internal fun normalizeNovaSource(source: String): String {
    var text = source.replace("\r\n", "\n").replace('\r', '\n')
    text = HTML_COMMENT.replace(text, "")
    text = HTML_SCRIPT.replace(text, "")
    text = HTML_STYLE.replace(text, "")
    text = decodeNovaEntities(text)
    // Decoding has to happen before the second pass: `&lt;script&gt;` is still
    // script once the browser-shaped text is decoded, and stripping only the
    // literal tag leaves the payload for the inline scanner to read as prose.
    text = HTML_COMMENT.replace(text, "")
    text = HTML_SCRIPT.replace(text, "")
    text = HTML_STYLE.replace(text, "")
    text = HTML_BREAK.replace(text, "\n")
    // Three or more blank lines are one paragraph break; two is already one.
    text = text.replace(Regex("""\n{4,}"""), "\n\n\n")
    return text
}

private fun decodeNovaEntities(text: String): String {
    if (!text.contains('&')) return text
    return HTML_ENTITY.replace(text) { match ->
        val body = match.groupValues[1]
        if (body.startsWith("#")) {
            val code = if (body.startsWith("#x") || body.startsWith("#X")) {
                body.drop(2).toIntOrNull(16)
            } else {
                body.drop(1).toIntOrNull()
            }
            if (code == null || code <= 0 || code > Character.MAX_CODE_POINT) {
                match.value
            } else {
                String(Character.toChars(code))
            }
        } else {
            NAMED_ENTITIES[body.lowercase(Locale.ROOT)] ?: match.value
        }
    }
}

// ---------------------------------------------------------------------------
// Block parser
// ---------------------------------------------------------------------------

/**
 * Whether the line at [index] begins a block, which ends any paragraph above it.
 *
 * Kept separate from the block handlers so the paragraph loop and the setext
 * look-ahead ask the same question. A handler that consumes a line the paragraph
 * would also have consumed is how a document loses text; a line the paragraph
 * refuses that no handler accepts is how a parser hangs. Every branch here is a
 * line a handler below owns or a line the loop skips.
 */
private fun startsNovaBlock(lines: List<String>, index: Int): Boolean {
    val raw = lines[index]
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return true
    return startsNovaTextBlock(raw, trimmed) ||
        startsNovaHtmlBlock(trimmed) ||
        startsNovaMediaBlock(lines, index, trimmed)
}

/**
 * Net anchors opened by one line.
 *
 * AniList authors routinely put an opening `<a>` on its own line, the image on
 * the next and `</a>` after that. The block walk below would otherwise end the
 * paragraph at the standalone image, leaving the opening tag as literal text -
 * which is exactly the `<a href=…>` a bio reader sees at the bottom of a profile.
 */
private fun novaAnchorDelta(line: String): Int =
    ANCHOR_OPEN_TAG.findAll(line).count() - ANCHOR_CLOSE_TAG.findAll(line).count()

/**
 * A block starter that may be swallowed into a paragraph while an anchor is open.
 *
 * AniList routinely writes `<a>`, then a picture, then `</a>` on three lines. The
 * picture looks like a media block to [startsNovaBlock], so it has to stay in the
 * link. Nothing else may cross that boundary: an unclosed `<a>` followed by a
 * heading used to turn the entire rest of the bio into one literal paragraph.
 */
private fun continuesNovaAnchor(trimmed: String): Boolean =
    standaloneNovaImage(trimmed) != null || parseNovaVideo(trimmed) != null

private fun startsNovaTextBlock(raw: String, trimmed: String): Boolean =
    FENCE.matches(trimmed) ||
        ATX_HEADING.matches(trimmed) ||
        HORIZONTAL_RULE.matches(trimmed) ||
        HTML_HR.matches(trimmed) ||
        trimmed.startsWith(">") ||
        BULLET_ITEM.matches(raw) ||
        ORDERED_ITEM.matches(raw) ||
        trimmed.equals(TILDE_CENTER, true) ||
        trimmed.equals("~~~center", true) ||
        SINGLE_TILDE_CENTER.matches(trimmed) ||
        CENTER_OPEN.matches(trimmed) ||
        ALIGN_OPEN.matches(trimmed)

private fun startsNovaHtmlBlock(trimmed: String): Boolean =
    HTML_QUOTE.matches(trimmed) ||
        HTML_PRE.matches(trimmed) ||
        HTML_LIST.matches(trimmed) ||
        HTML_IMAGE_LINE.matches(trimmed) ||
        ESCAPE_OPEN.matches(trimmed) ||
        isLegacySpoilerStart(trimmed) ||
        looksLikeHtmlSpoiler(trimmed)

private fun startsNovaMediaBlock(lines: List<String>, index: Int, trimmed: String): Boolean =
    standaloneNovaImage(trimmed) != null ||
        parseNovaVideo(trimmed) != null ||
        looksLikeAniListEntity(trimmed) ||
        looksLikeNovaTable(lines, index)

private fun looksLikeHtmlSpoiler(trimmed: String): Boolean {
    val open = SPOILER_OPEN.find(trimmed) ?: return false
    if (open.range.first != 0 || !isNovaSpoiler(open.groupValues[2])) return false
    // A one-line `<span class="markdown-spoiler">…</span>` sits inside a sentence,
    // not between blocks. The block reader declines it (see `readHtmlSpoiler`), so
    // treating it as a block start would strand the line as unparsed text.
    if (open.groupValues[1].equals("span", ignoreCase = true)) {
        val rest = trimmed.substring(open.range.last + 1)
        if (rest.contains("</span>", ignoreCase = true)) return false
    }
    return true
}

private fun looksLikeAniListEntity(trimmed: String): Boolean =
    parseNovaEntityUrl(trimmed.removeSurrounding("<", ">").trim()) != null

/** A cheap table check for the paragraph loop; the real parser builds the cells. */
private fun looksLikeNovaTable(lines: List<String>, index: Int): Boolean {
    if (index + 1 >= lines.size) return false
    val header = lines[index].trim()
    val separator = lines[index + 1].trim()
    if (!header.contains('|') || !separator.contains('|')) return false
    val cells = splitNovaTableRow(separator)
    return cells.isNotEmpty() && cells.all { SEPARATOR_CELL.matches(it.trim()) }
}

internal fun parseNovaBlocks(
    lines: List<String>,
    align: NovaAlign,
    depth: Int
): List<NovaBlock> {
    val out = mutableListOf<NovaBlock>()
    var i = 0

    while (i < lines.size) {
        val trimmed = lines[i].trimEnd().trim()

        if (trimmed.isEmpty()) {
            i++
            continue
        }

        // Past the nesting cap the rest is readable text. A bio that nests
        // deeper than this is malformed, and a stack overflow is not a render.
        if (depth > MAX_BLOCK_DEPTH) {
            out += NovaBlock.Paragraph(listOf(NovaInline.Text(trimmed)), align)
            i++
            continue
        }

        val block = parseNovaHeadingBlock(lines, i, align, depth)
            ?: parseNovaHtmlBlock(lines, i, align, depth)
            ?: parseNovaStructuredBlock(lines, i, align, depth)
        if (block != null) {
            out += block.first
            i = block.second
            continue
        }

        // ---- paragraph -----------------------------------------------------
        // A paragraph normally stops at the next block. An open anchor keeps it
        // going across a picture line, because a picture between `<a>` and
        // `</a>` belongs to the link. That is the only block it may cross: a
        // stray opening tag must not eat the headings and spoilers after it.
        val paragraphLines = mutableListOf<String>()
        var openAnchors = 0
        while (i < lines.size) {
            val line = lines[i].trim()
            if (startsNovaBlock(lines, i) &&
                (openAnchors == 0 || !continuesNovaAnchor(line))
            ) {
                break
            }
            paragraphLines += line
            openAnchors = (openAnchors + novaAnchorDelta(line)).coerceAtLeast(0)
            i++
        }

        if (paragraphLines.isEmpty()) {
            // A block starter that also refuses to consume its own line is the
            // one shape that can hang this loop. Emitting it as text guarantees
            // progress and keeps the author's line visible.
            out += NovaBlock.Paragraph(listOf(NovaInline.Text(trimmed)), align)
            i++
        } else {
            out += paragraphBlocks(paragraphLines.joinToString("\n"), align)
        }
    }

    return out
}

/** Tilde/centre wrappers, fences, headings and rules. */
private fun parseNovaHeadingBlock(
    lines: List<String>,
    start: Int,
    align: NovaAlign,
    depth: Int
): Pair<List<NovaBlock>, Int>? {
    val line = lines[start].trimEnd()
    val trimmed = line.trim()

    if (trimmed.equals(TILDE_CENTER, true) || trimmed.equals("~~~center", true)) {
        val (body, next) = collectUntilToken(lines, start + 1, TILDE_CENTER)
        return parseAligned(body, NovaAlign.Center, depth + 1) to next
    }

    val singleTilde = SINGLE_TILDE_CENTER.matchEntire(trimmed)
    if (singleTilde != null) {
        return parseAligned(
            singleTilde.groupValues[1].trim(),
            NovaAlign.Center,
            depth + 1
        ) to start + 1
    }

    val center = readNovaCenterBlock(lines, start, trimmed, depth)
    if (center != null) return center
    val fence = readNovaFenceBlock(lines, start, line, align)
    if (fence != null) return fence
    val setext = readNovaSetextBlock(lines, start, trimmed, align)
    if (setext != null) return setext

    val atx = ATX_HEADING.matchEntire(trimmed)
    if (atx != null) {
        val level = atx.groupValues[1].length.coerceAtMost(6)
        return listOf(
            NovaBlock.Heading(level, parseNovaInlineLine(atx.groupValues[2]), align)
        ) to start + 1
    }

    if (HORIZONTAL_RULE.matches(trimmed) || HTML_HR.matches(trimmed)) {
        return listOf(NovaBlock.Rule) to start + 1
    }
    return null
}

private fun readNovaCenterBlock(
    lines: List<String>,
    start: Int,
    trimmed: String,
    depth: Int
): Pair<List<NovaBlock>, Int>? {
    val centerOpen = CENTER_OPEN.matchEntire(trimmed) ?: return null
    val first = centerOpen.groupValues[1]
    val inlineClose = first.indexOf(CENTER_CLOSE, ignoreCase = true)
    if (inlineClose >= 0) {
        val blocks = parseAligned(
            first.substring(0, inlineClose),
            NovaAlign.Center,
            depth + 1
        ).toMutableList()
        val after = first.substring(inlineClose + CENTER_CLOSE.length).trim()
        if (after.isNotEmpty()) {
            blocks += parseAligned(after, NovaAlign.Center, depth + 1)
        }
        return blocks to start + 1
    }
    val (body, next) = collectUntilToken(lines, start + 1, CENTER_CLOSE)
    val content = listOf(first).filter { it.isNotBlank() } + body.lines()
    return parseAligned(content.joinToString("\n"), NovaAlign.Center, depth + 1) to next
}

private fun readNovaFenceBlock(
    lines: List<String>,
    start: Int,
    line: String,
    align: NovaAlign
): Pair<List<NovaBlock>, Int>? {
    val fence = FENCE.matchEntire(line) ?: return null
    val marker = fence.groupValues[1]
    val language = fence.groupValues[2].trim().takeIf { it.isNotEmpty() }
    val code = mutableListOf<String>()
    var j = start + 1
    while (j < lines.size && !lines[j].trimStart().startsWith(marker)) {
        code += lines[j]
        j++
    }
    val next = if (j < lines.size) j + 1 else j
    return listOf(NovaBlock.Code(language, code.joinToString("\n"), align)) to next
}

private fun readNovaSetextBlock(
    lines: List<String>,
    start: Int,
    trimmed: String,
    align: NovaAlign
): Pair<List<NovaBlock>, Int>? {
    val underlined = lines.getOrNull(start + 1)?.trim() ?: return null
    val isTitleLine = !startsNovaBlock(lines, start) && !HORIZONTAL_RULE.matches(trimmed)
    if (!isTitleLine) return null
    if (SETEXT_H1.matches(underlined)) {
        return listOf(
            NovaBlock.Heading(1, parseNovaInlineLine(trimmed), align)
        ) to start + 2
    }
    if (SETEXT_H2.matches(underlined)) {
        return listOf(
            NovaBlock.Heading(2, parseNovaInlineLine(trimmed), align)
        ) to start + 2
    }
    return null
}

/** HTML spoilers, block quotes, pre blocks, lists and align containers. */
private fun parseNovaHtmlBlock(
    lines: List<String>,
    start: Int,
    align: NovaAlign,
    depth: Int
): Pair<List<NovaBlock>, Int>? {
    val trimmed = lines[start].trimEnd().trim()

    val htmlSpoiler = readHtmlSpoiler(lines, start, align, depth)
    if (htmlSpoiler != null) {
        return listOf(htmlSpoiler.first) to htmlSpoiler.second
    }

    if (isLegacySpoilerStart(trimmed)) {
        val legacy = readLegacySpoiler(lines, start, align, depth)
        return listOf(legacy.first) to legacy.second
    }

    // `<escape>` is a block here, not just an inline span. A bare AniList URL or
    // an image on its own line inside it is a block starter, so the paragraph
    // walk would otherwise peel it off and draw a card the author asked not to
    // have. Collect the whole body as literal text before the inline scanner.
    val escapeOpen = ESCAPE_OPEN.matchEntire(trimmed)
    if (escapeOpen != null) {
        return readNovaEscapeBlock(lines, start, escapeOpen.groupValues[1], align)
    }

    return readNovaQuoteBlock(lines, start, trimmed, align, depth)
        ?: readNovaPreBlock(lines, start, trimmed, align)
        ?: readNovaHtmlListBlock(lines, start, trimmed, align)
        ?: readNovaAlignBlock(lines, start, trimmed, align, depth)
        ?: strayNovaCloseTag(start, trimmed)
}

/** A stray close is a wrapper's remainder, not a sentence. */
private fun strayNovaCloseTag(start: Int, trimmed: String): Pair<List<NovaBlock>, Int>? =
    if (trimmed.startsWith("</") && trimmed.endsWith(">")) {
        emptyList<NovaBlock>() to start + 1
    } else {
        null
    }

/** `<blockquote>` as a quote, inline or collected over the following lines. */
private fun readNovaQuoteBlock(
    lines: List<String>,
    start: Int,
    first: String,
    align: NovaAlign,
    depth: Int
): Pair<List<NovaBlock>, Int>? {
    val quote = HTML_QUOTE.matchEntire(first) ?: return null
    val inner = quote.groupValues[1]
    if (inner.contains("</blockquote", ignoreCase = true)) {
        return listOf(
            NovaBlock.Quote(
                parseNovaBlocks(inner.substringBefore("</blockquote", "").lines(), align, depth + 1),
                align
            )
        ) to start + 1
    }
    val (body, next) = collectUntilToken(lines, start + 1, "</blockquote>")
    val content = listOf(inner).filter { it.isNotBlank() } + body.lines()
    return listOf(
        NovaBlock.Quote(
            parseNovaBlocks(content.joinToString("\n").lines(), align, depth + 1),
            align
        )
    ) to next
}

/** `<pre>` as a plain code block, with the tags stripped from its body. */
private fun readNovaPreBlock(
    lines: List<String>,
    start: Int,
    first: String,
    align: NovaAlign
): Pair<List<NovaBlock>, Int>? {
    val pre = HTML_PRE.matchEntire(first) ?: return null
    val inner = pre.groupValues[1]
    if (inner.contains("</pre", ignoreCase = true)) {
        return listOf(
            NovaBlock.Code(null, HTML_TAG.replace(inner.substringBefore("</pre"), ""), align)
        ) to start + 1
    }
    val (body, next) = collectUntilToken(lines, start + 1, "</pre>")
    val content = (listOf(inner) + body.lines()).joinToString("\n")
    return listOf(NovaBlock.Code(null, HTML_TAG.replace(content, ""), align)) to next
}

/** `<ul>`/`<ol>` as a bullet list. */
private fun readNovaHtmlListBlock(
    lines: List<String>,
    start: Int,
    first: String,
    align: NovaAlign
): Pair<List<NovaBlock>, Int>? {
    val list = HTML_LIST.matchEntire(first) ?: return null
    val ordered = list.groupValues[1].equals("ol", ignoreCase = true)
    val closeToken = if (ordered) "</ol>" else "</ul>"
    val (body, next) = if (first.contains(closeToken, ignoreCase = true)) {
        "" to start + 1
    } else {
        collectUntilToken(lines, start + 1, closeToken)
    }
    val source = (listOf(first) + body.lines()).joinToString("\n")
    val items = HTML_LI.findAll(source).map { item ->
        NovaListItem(
            listOf(NovaBlock.Paragraph(parseNovaInlineLine(item.groupValues[1].trim())))
        )
    }.toList()
    return listOf(NovaBlock.BulletList(ordered, 1, items, align)) to next
}

/** `<p>`/`<div>` with an alignment attribute, parsed as a sub-document. */
private fun readNovaAlignBlock(
    lines: List<String>,
    start: Int,
    first: String,
    align: NovaAlign,
    depth: Int
): Pair<List<NovaBlock>, Int>? {
    val aligned = ALIGN_OPEN.matchEntire(first) ?: return null
    val tag = aligned.groupValues[1].lowercase(Locale.ROOT)
    val innerAlign = novaAlignFrom(aligned.groupValues[2]) ?: align
    val inner = aligned.groupValues[3]
    val close = "</$tag>"
    val inlineClose = inner.indexOf(close, ignoreCase = true)
    if (inlineClose >= 0) {
        return parseAligned(inner.substring(0, inlineClose), innerAlign, depth + 1) to start + 1
    }
    val (body, next) = collectUntilToken(lines, start + 1, close)
    val content = listOf(inner).filter { it.isNotBlank() } + body.lines()
    return parseAligned(content.joinToString("\n"), innerAlign, depth + 1) to next
}

/** Reads `<escape>…</escape>` as one literal paragraph. */
private fun readNovaEscapeBlock(
    lines: List<String>,
    start: Int,
    afterOpen: String,
    align: NovaAlign
): Pair<List<NovaBlock>, Int> {
    val literal = StringBuilder()
    var rest = ""
    var next = start + 1

    val inlineClose = ESCAPE_CLOSE.find(afterOpen)
    if (inlineClose != null) {
        literal.append(afterOpen, 0, inlineClose.range.first)
        rest = afterOpen.substring(inlineClose.range.last + 1)
    } else {
        literal.append(afterOpen)
        while (next < lines.size) {
            val line = lines[next]
            val close = ESCAPE_CLOSE.find(line)
            if (close != null) {
                literal.append('\n').append(line, 0, close.range.first)
                rest = line.substring(close.range.last + 1)
                next++
                break
            }
            literal.append('\n').append(line)
            next++
        }
    }

    val inlines = mutableListOf<NovaInline>(NovaInline.Text(literal.toString().trim('\n')))
    if (rest.isNotBlank()) inlines += parseNovaInlineLine(rest.trim())
    return listOf(NovaBlock.Paragraph(inlines, align)) to next
}

/** Quotes, real lists, tables, media and entity cards. */
private fun parseNovaStructuredBlock(
    lines: List<String>,
    start: Int,
    align: NovaAlign,
    depth: Int
): Pair<List<NovaBlock>, Int>? {
    val line = lines[start].trimEnd()
    val trimmed = line.trim()

    if (trimmed.startsWith(">")) {
        val quoteLines = mutableListOf<String>()
        var j = start
        while (j < lines.size && lines[j].trimStart().startsWith(">")) {
            quoteLines += lines[j].trimStart().removePrefix(">").removePrefix(" ")
            j++
        }
        return listOf(
            NovaBlock.Quote(parseNovaBlocks(quoteLines, align, depth + 1), align)
        ) to j
    }

    val bullet = BULLET_ITEM.matchEntire(line)
    if (bullet != null) {
        val (list, next) = readNovaList(
            lines = lines,
            start = start,
            ordered = false,
            baseIndent = bullet.groupValues[1].length,
            align = align,
            depth = depth
        )
        return listOf(list) to next
    }

    val numbered = ORDERED_ITEM.matchEntire(line)
    if (numbered != null) {
        val (list, next) = readNovaList(
            lines = lines,
            start = start,
            ordered = true,
            baseIndent = numbered.groupValues[1].length,
            align = align,
            depth = depth
        )
        return listOf(list) to next
    }

    val table = parseNovaTable(lines, start) { parseNovaInlineLine(it) }
    if (table != null) {
        return listOf(table.first.copy(align = align)) to table.second
    }

    val image = standaloneNovaImage(trimmed)
    if (image != null) {
        return listOf(image.copy(align = align)) to start + 1
    }

    val video = parseNovaVideo(trimmed)
    if (video != null) {
        return listOf(video.copy(align = align)) to start + 1
    }

    val bareEntity = trimmed.removeSurrounding("<", ">").trim()
    val entity = parseNovaEntityUrl(bareEntity)
    if (entity != null) {
        return listOf(
            NovaBlock.Preview(
                url = novaEntityAbsoluteUrl(bareEntity),
                type = entity.first,
                id = entity.third,
                title = novaTitleFromSlug(entity.second, entity.first, entity.third),
                align = align
            )
        ) to start + 1
    }
    return null
}

/** Parses [source] as a centred/styled sub-document. */
private fun parseAligned(source: String, align: NovaAlign, depth: Int): List<NovaBlock> =
    parseNovaBlocks(source.lines(), align, depth).map { it.novaAligned(align) }

private fun NovaBlock.novaAligned(align: NovaAlign): NovaBlock = when (this) {
    is NovaBlock.Heading -> copy(align = align)
    is NovaBlock.Paragraph -> copy(align = align)
    is NovaBlock.Quote -> copy(align = align)
    is NovaBlock.Code -> copy(align = align)
    is NovaBlock.BulletList -> copy(align = align)
    is NovaBlock.Table -> copy(align = align)
    is NovaBlock.Image -> copy(align = align)
    is NovaBlock.Video -> copy(align = align)
    is NovaBlock.Spoiler -> copy(align = align)
    is NovaBlock.Preview -> copy(align = align)
    NovaBlock.Rule -> this
}

/**
 * A paragraph plus the preview cards for any AniList links it contained.
 *
 * The cards are separate blocks rather than inline widgets: a card is a
 * full-width surface with a poster, and AniList draws it after the sentence, not
 * inside it. Centring travels with both.
 */
private fun paragraphBlocks(text: String, align: NovaAlign): List<NovaBlock> {
    if (text.isBlank()) return emptyList()
    val (kept, links) = splitNovaMediaLinks(parseNovaInlineLine(text))
    val blocks = mutableListOf<NovaBlock>()
    if (kept.isNotEmpty()) {
        blocks += NovaBlock.Paragraph(kept, align)
    }
    links.forEach { link ->
        blocks += NovaBlock.Preview(link.url, link.type, link.id, link.label, align)
    }
    return blocks
}

/** The lines of a container up to [token], and the index just past it. */
internal fun collectUntilToken(
    lines: List<String>,
    start: Int,
    token: String
): Pair<String, Int> {
    val body = mutableListOf<String>()
    var i = start
    while (i < lines.size) {
        val line = lines[i]
        val at = line.indexOf(token, ignoreCase = true)
        if (at >= 0) {
            if (at > 0) body += line.substring(0, at)
            return body.joinToString("\n") to i + 1
        }
        body += line
        i++
    }
    return body.joinToString("\n") to i
}

private fun novaAlignFrom(attrs: String): NovaAlign? {
    val value = ALIGN_ATTR.find(attrs)?.groupValues?.get(1)
        ?: STYLE_ALIGN.find(attrs)?.groupValues?.get(1)
        ?: return null
    return when (value.lowercase(Locale.ROOT)) {
        "center" -> NovaAlign.Center
        "right" -> NovaAlign.End
        "justify" -> NovaAlign.Justify
        else -> NovaAlign.Start
    }
}
