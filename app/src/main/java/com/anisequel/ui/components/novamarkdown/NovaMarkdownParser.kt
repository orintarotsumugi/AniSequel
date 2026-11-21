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
    if (FENCE.matches(trimmed) || ATX_HEADING.matches(trimmed)) return true
    if (HORIZONTAL_RULE.matches(trimmed) || HTML_HR.matches(trimmed)) return true
    if (trimmed.startsWith(">")) return true
    if (BULLET_ITEM.matches(raw) || ORDERED_ITEM.matches(raw)) return true
    if (trimmed.equals(TILDE_CENTER, true) || trimmed.equals("~~~center", true)) return true
    if (SINGLE_TILDE_CENTER.matches(trimmed) || CENTER_OPEN.matches(trimmed)) return true
    if (ALIGN_OPEN.matches(trimmed)) return true
    if (HTML_QUOTE.matches(trimmed) || HTML_PRE.matches(trimmed) || HTML_LIST.matches(trimmed)) return true
    if (HTML_IMAGE_LINE.matches(trimmed)) return true
    if (isLegacySpoilerStart(trimmed)) return true
    if (looksLikeHtmlSpoiler(trimmed)) return true
    if (standaloneNovaImage(trimmed) != null) return true
    if (parseNovaVideo(trimmed) != null) return true
    if (looksLikeAniListEntity(trimmed)) return true
    if (looksLikeNovaTable(lines, index)) return true
    return false
}

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
        val rawLine = lines[i]
        val line = rawLine.trimEnd()
        val trimmed = line.trim()

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

        // ---- ~ ~ ~ centring -------------------------------------------------
        if (trimmed.equals(TILDE_CENTER, true) || trimmed.equals("~~~center", true)) {
            val (body, next) = collectUntilToken(lines, i + 1, TILDE_CENTER)
            out += parseAligned(body, NovaAlign.Center, depth + 1)
            i = next
            continue
        }

        val singleTilde = SINGLE_TILDE_CENTER.matchEntire(trimmed)
        if (singleTilde != null) {
            out += parseAligned(singleTilde.groupValues[1].trim(), NovaAlign.Center, depth + 1)
            i++
            continue
        }

        // ---- <center> ------------------------------------------------------
        val centerOpen = CENTER_OPEN.matchEntire(trimmed)
        if (centerOpen != null) {
            val first = centerOpen.groupValues[1]
            val inlineClose = first.indexOf(CENTER_CLOSE, ignoreCase = true)
            if (inlineClose >= 0) {
                out += parseAligned(first.substring(0, inlineClose), NovaAlign.Center, depth + 1)
                val after = first.substring(inlineClose + CENTER_CLOSE.length).trim()
                if (after.isNotEmpty()) {
                    out += parseAligned(after, NovaAlign.Center, depth + 1)
                }
                i++
            } else {
                val (body, next) = collectUntilToken(lines, i + 1, CENTER_CLOSE)
                val content = listOf(first).filter { it.isNotBlank() } + body.lines()
                out += parseAligned(content.joinToString("\n"), NovaAlign.Center, depth + 1)
                i = next
            }
            continue
        }

        // ---- fenced code ---------------------------------------------------
        val fence = FENCE.matchEntire(line)
        if (fence != null) {
            val marker = fence.groupValues[1]
            val language = fence.groupValues[2].trim().takeIf { it.isNotEmpty() }
            val code = mutableListOf<String>()
            var j = i + 1
            while (j < lines.size && !lines[j].trimStart().startsWith(marker)) {
                code += lines[j]
                j++
            }
            out += NovaBlock.Code(language, code.joinToString("\n"), align)
            i = if (j < lines.size) j + 1 else j
            continue
        }

        // ---- setext heading ------------------------------------------------
        val underlined = lines.getOrNull(i + 1)?.trim()
        val isTitleLine = !startsNovaBlock(lines, i) && !HORIZONTAL_RULE.matches(trimmed)
        if (underlined != null && isTitleLine && SETEXT_H1.matches(underlined)) {
            out += NovaBlock.Heading(1, parseNovaInlineLine(trimmed), align)
            i += 2
            continue
        }
        if (underlined != null && isTitleLine && SETEXT_H2.matches(underlined)) {
            out += NovaBlock.Heading(2, parseNovaInlineLine(trimmed), align)
            i += 2
            continue
        }

        // ---- ATX heading ---------------------------------------------------
        val atx = ATX_HEADING.matchEntire(trimmed)
        if (atx != null) {
            val level = atx.groupValues[1].length.coerceAtMost(6)
            out += NovaBlock.Heading(level, parseNovaInlineLine(atx.groupValues[2]), align)
            i++
            continue
        }

        // ---- rules ---------------------------------------------------------
        if (HORIZONTAL_RULE.matches(trimmed) || HTML_HR.matches(trimmed)) {
            out += NovaBlock.Rule
            i++
            continue
        }

        // ---- HTML spoilers -------------------------------------------------
        val htmlSpoiler = readHtmlSpoiler(lines, i, align, depth)
        if (htmlSpoiler != null) {
            out += htmlSpoiler.first
            i = htmlSpoiler.second
            continue
        }

        // ---- ~! legacy spoiler ---------------------------------------------
        if (isLegacySpoilerStart(trimmed)) {
            val legacy = readLegacySpoiler(lines, i, align, depth)
            out += legacy.first
            i = legacy.second
            continue
        }

        // ---- <blockquote> --------------------------------------------------
        val htmlQuote = HTML_QUOTE.matchEntire(trimmed)
        if (htmlQuote != null) {
            val first = htmlQuote.groupValues[1]
            if (first.contains("</blockquote", ignoreCase = true)) {
                val inner = first.substringBefore("</blockquote", "")
                out += NovaBlock.Quote(parseNovaBlocks(inner.lines(), align, depth + 1), align)
                i++
            } else {
                val (body, next) = collectUntilToken(lines, i + 1, "</blockquote>")
                val content = listOf(first).filter { it.isNotBlank() } + body.lines()
                out += NovaBlock.Quote(
                    parseNovaBlocks(content.joinToString("\n").lines(), align, depth + 1),
                    align
                )
                i = next
            }
            continue
        }

        // ---- <pre> ---------------------------------------------------------
        val htmlPre = HTML_PRE.matchEntire(trimmed)
        if (htmlPre != null) {
            val first = htmlPre.groupValues[1]
            if (first.contains("</pre", ignoreCase = true)) {
                out += NovaBlock.Code(
                    null,
                    HTML_TAG.replace(first.substringBefore("</pre"), ""),
                    align
                )
                i++
            } else {
                val (body, next) = collectUntilToken(lines, i + 1, "</pre>")
                val content = (listOf(first) + body.lines()).joinToString("\n")
                out += NovaBlock.Code(null, HTML_TAG.replace(content, ""), align)
                i = next
            }
            continue
        }

        // ---- HTML lists ----------------------------------------------------
        val htmlList = HTML_LIST.matchEntire(trimmed)
        if (htmlList != null) {
            val ordered = htmlList.groupValues[1].equals("ol", ignoreCase = true)
            val closeToken = if (ordered) "</ol>" else "</ul>"
            val openingHasClose = trimmed.contains(closeToken, ignoreCase = true)
            val (body, next) = if (openingHasClose) {
                "" to (i + 1)
            } else {
                collectUntilToken(lines, i + 1, closeToken)
            }
            val source = (listOf(trimmed) + body.lines()).joinToString("\n")
            val items = HTML_LI.findAll(source).map { item ->
                NovaListItem(
                    listOf(NovaBlock.Paragraph(parseNovaInlineLine(item.groupValues[1].trim())))
                )
            }.toList()
            out += NovaBlock.BulletList(ordered, 1, items, align)
            i = next
            continue
        }

        // ---- <div>/<p> containers ------------------------------------------
        val aligned = ALIGN_OPEN.matchEntire(trimmed)
        if (aligned != null) {
            val tag = aligned.groupValues[1].lowercase(Locale.ROOT)
            val attrs = aligned.groupValues[2]
            val innerAlign = novaAlignFrom(attrs) ?: align
            val first = aligned.groupValues[3]
            val close = "</$tag>"
            val inlineClose = first.indexOf(close, ignoreCase = true)
            if (inlineClose >= 0) {
                out += parseAligned(first.substring(0, inlineClose), innerAlign, depth + 1)
                i++
            } else {
                val (body, next) = collectUntilToken(lines, i + 1, close)
                val content = listOf(first).filter { it.isNotBlank() } + body.lines()
                out += parseAligned(content.joinToString("\n"), innerAlign, depth + 1)
                i = next
            }
            continue
        }

        // A stray close is a wrapper's remainder, not a sentence.
        if (trimmed.startsWith("</") && trimmed.endsWith(">")) {
            i++
            continue
        }

        // ---- > quotes ------------------------------------------------------
        if (trimmed.startsWith(">")) {
            val quoteLines = mutableListOf<String>()
            var j = i
            while (j < lines.size && lines[j].trimStart().startsWith(">")) {
                quoteLines += lines[j].trimStart().removePrefix(">").removePrefix(" ")
                j++
            }
            out += NovaBlock.Quote(parseNovaBlocks(quoteLines, align, depth + 1), align)
            i = j
            continue
        }

        // ---- lists ---------------------------------------------------------
        val bullet = BULLET_ITEM.matchEntire(line)
        if (bullet != null) {
            val (list, next) = readNovaList(
                lines = lines,
                start = i,
                ordered = false,
                baseIndent = bullet.groupValues[1].length,
                align = align,
                depth = depth
            )
            out += list
            i = next
            continue
        }
        val numbered = ORDERED_ITEM.matchEntire(line)
        if (numbered != null) {
            val (list, next) = readNovaList(
                lines = lines,
                start = i,
                ordered = true,
                baseIndent = numbered.groupValues[1].length,
                align = align,
                depth = depth
            )
            out += list
            i = next
            continue
        }

        // ---- tables --------------------------------------------------------
        val table = parseNovaTable(lines, i) { parseNovaInlineLine(it) }
        if (table != null) {
            out += table.first.copy(align = align)
            i = table.second
            continue
        }

        // ---- whole-line embeds --------------------------------------------
        val image = standaloneNovaImage(trimmed)
        if (image != null) {
            out += image.copy(align = align)
            i++
            continue
        }

        val video = parseNovaVideo(trimmed)
        if (video != null) {
            out += video.copy(align = align)
            i++
            continue
        }

        val bareEntity = trimmed.removeSurrounding("<", ">").trim()
        val entity = parseNovaEntityUrl(bareEntity)
        if (entity != null) {
            out += NovaBlock.Preview(
                url = novaEntityAbsoluteUrl(bareEntity),
                type = entity.first,
                id = entity.third,
                title = novaTitleFromSlug(entity.second, entity.first, entity.third),
                align = align
            )
            i++
            continue
        }

        // ---- paragraph -----------------------------------------------------
        val paragraphLines = mutableListOf<String>()
        while (i < lines.size && !startsNovaBlock(lines, i)) {
            paragraphLines += lines[i].trim()
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
            return body.joinToString("\n") to (i + 1)
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
