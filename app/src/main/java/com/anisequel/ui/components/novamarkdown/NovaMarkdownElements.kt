package com.anisequel.ui.components.novamarkdown

import java.util.Locale

/**
 * The larger AniList constructs: spoilers, lists, tables, whole-line media and
 * the HTML attribute reader they share.
 *
 * Kept apart from `parseNovaBlocks` so each file has one job: this one knows how
 * a construct is shaped and where it ends, while the block parser only decides
 * which construct owns a line.
 */


// ---------------------------------------------------------------------------
// Spoilers
// ---------------------------------------------------------------------------

internal fun isLegacySpoilerStart(trimmed: String): Boolean =
    trimmed.startsWith("~!") && !trimmed.startsWith("~!!") && !trimmed.startsWith("~!~")

internal fun readLegacySpoiler(
    lines: List<String>,
    start: Int,
    align: NovaAlign,
    depth: Int
): Pair<NovaBlock.Spoiler, Int> {
    val first = lines[start].trim().removePrefix("~!")
    val sameLine = first.indexOf("!~")
    val content: String
    val next: Int
    if (sameLine >= 0) {
        content = first.substring(0, sameLine)
        next = start + 1
    } else {
        val body = mutableListOf(first)
        var i = start + 1
        var closed = false
        while (i < lines.size) {
            val at = lines[i].indexOf("!~")
            if (at >= 0) {
                body += lines[i].substring(0, at)
                closed = true
                i++
                break
            }
            body += lines[i]
            i++
        }
        content = body.joinToString("\n")
        next = if (closed) i else lines.size
    }
    return NovaBlock.Spoiler(
        label = null,
        blocks = parseNovaBlocks(content.trim().lines(), align, depth + 1),
        align = align
    ) to next
}

internal fun readHtmlSpoiler(
    lines: List<String>,
    start: Int,
    align: NovaAlign,
    depth: Int
): Pair<NovaBlock.Spoiler, Int>? {
    val line = lines[start].trim()
    val open = SPOILER_OPEN.find(line) ?: return null
    if (open.range.first != 0) return null
    val tag = open.groupValues[1].lowercase(Locale.ROOT)
    val attrs = open.groupValues[2]
    if (!isNovaSpoiler(attrs)) return null

    val close = "</$tag>"
    val rest = line.substring(open.range.last + 1)
    val inlineClose = rest.indexOf(close, ignoreCase = true)

    // A one-line span inside a sentence is inline. A div is always a block,
    // even one line, because that is how the site's own editor writes it.
    if (tag == "span" && inlineClose >= 0) return null

    val label = novaSpoilerLabel(attrs)
    if (inlineClose >= 0) {
        return NovaBlock.Spoiler(
            label = label,
            blocks = parseNovaBlocks(rest.substring(0, inlineClose).lines(), align, depth + 1),
            align = align
        ) to start + 1
    }

    val (body, next) = collectUntilToken(lines, start + 1, close)
    val content = (listOf(rest).filter { it.isNotBlank() } + body.lines()).joinToString("\n")
    return NovaBlock.Spoiler(
        label = label,
        blocks = parseNovaBlocks(content.trim().lines(), align, depth + 1),
        align = align
    ) to next
}

internal fun isNovaSpoiler(attrs: String): Boolean {
    val classes = CLASS_ATTR.find(attrs)?.let { match ->
        match.groupValues[2].ifEmpty {
            match.groupValues[3].ifEmpty { match.groupValues[4] }
        }
    }.orEmpty()
    if (classes.split(Regex("""\s+""")).any {
            it.equals("markdown-spoiler", true) || it.equals("markdown_spoiler", true)
        }
    ) {
        return true
    }
    val rel = REL_ATTR.find(attrs)?.let { match ->
        match.groupValues[2].ifEmpty {
            match.groupValues[3].ifEmpty { match.groupValues[4] }
        }
    }
    return rel?.equals("spoiler", ignoreCase = true) == true
}

/**
 * A spoiler label is a hint, not prose.
 *
 * The characters that could close the tag it sits in or start markup of its own
 * are refused whole rather than escaped: a label with half a `<b>` in it is a
 * parsing bug waiting for the next renderer, while a spoiler without a label is
 * still a spoiler.
 */
internal fun novaSpoilerLabel(attrs: String): String? {
    val raw = LABEL_ATTR.find(attrs)?.let { match ->
        match.groupValues[2].ifEmpty { match.groupValues[3] }
    } ?: return null
    val label = raw.trim()
    if (label.isEmpty() || label.length > 80) return null
    if (label.any { it == '<' || it == '>' || it == '{' || it == '}' || it == '"' || it == '\'' }) {
        return null
    }
    return label
}

// ---------------------------------------------------------------------------
// Lists and tables
// ---------------------------------------------------------------------------

internal fun readNovaList(
    lines: List<String>,
    start: Int,
    ordered: Boolean,
    baseIndent: Int,
    align: NovaAlign,
    depth: Int
): Pair<NovaBlock.BulletList, Int> {
    val items = mutableListOf<NovaListItem>()
    var i = start
    var listStart = 1
    var first = true

    while (i < lines.size) {
        val match = novaListItemMatch(lines[i], ordered) ?: break
        val indent = match.groupValues[1].length
        if (indent != baseIndent) break

        val number = if (ordered) match.groupValues[2].toIntOrNull() ?: 1 else 1
        val content = if (ordered) match.groupValues[3] else match.groupValues[2]
        if (first) {
            listStart = number
            first = false
        }

        val (item, next) = readNovaListItem(
            lines = lines,
            contentStart = i + 1,
            content = content,
            ordered = ordered,
            baseIndent = baseIndent,
            align = align,
            depth = depth
        )
        items += item
        i = next
    }

    return NovaBlock.BulletList(ordered, listStart, items, align) to i
}

private fun novaListItemMatch(line: String, ordered: Boolean): MatchResult? =
    if (ordered) ORDERED_ITEM.matchEntire(line) else BULLET_ITEM.matchEntire(line)

private fun readNovaListItem(
    lines: List<String>,
    contentStart: Int,
    content: String,
    ordered: Boolean,
    baseIndent: Int,
    align: NovaAlign,
    depth: Int
): Pair<NovaListItem, Int> {
    val (itemLines, next) = collectNovaListItemLines(
        lines = lines,
        contentStart = contentStart,
        content = content,
        ordered = ordered,
        baseIndent = baseIndent
    )
    val task = TASK_ITEM.matchEntire(itemLines.first().trim())
    val checked = task?.let { it.groupValues[1].equals("x", ignoreCase = true) }
    val textLines = if (task != null) {
        listOf(task.groupValues[2]) + itemLines.drop(1)
    } else {
        itemLines
    }
    return NovaListItem(
        blocks = parseNovaBlocks(textLines, align, depth + 1),
        checked = checked
    ) to next
}

private fun collectNovaListItemLines(
    lines: List<String>,
    contentStart: Int,
    content: String,
    ordered: Boolean,
    baseIndent: Int
): Pair<List<String>, Int> {
    val itemLines = mutableListOf(content)
    var j = contentStart
    while (j < lines.size) {
        val nextLine = lines[j]
        if (nextLine.isBlank()) {
            val after = lines.getOrNull(j + 1)
            if (after != null && leadingSpaces(after) > baseIndent) {
                itemLines += ""
                j++
                continue
            }
            // A blank line between two items of the same list does not split
            // the list in AniList's renderer.
            if (after != null && sameListMarker(after, ordered, baseIndent)) {
                j++
            }
            break
        }
        val nextIndent = leadingSpaces(nextLine)
        if (nextIndent <= baseIndent) break
        val strip = minOf(nextIndent, baseIndent + 2)
        itemLines += nextLine.drop(strip.coerceAtMost(nextLine.length))
        j++
    }
    return itemLines to j
}

private fun leadingSpaces(line: String): Int {
    var count = 0
    while (count < line.length && (line[count] == ' ' || line[count] == '\t')) count++
    return count
}

private fun sameListMarker(line: String, ordered: Boolean, indent: Int): Boolean {
    val match = (
        if (ordered) ORDERED_ITEM.matchEntire(line) else BULLET_ITEM.matchEntire(line)
    ) ?: return false
    return match.groupValues[1].length == indent
}

/**
 * A GFM table, which AniList reviews and some bios use.
 *
 * Split out and taking an inline parser so the parser and the regression test
 * agree about which lines are a table. The lower-bound check is not decorative:
 * an out-of-range index here is an `IndexOutOfBoundsException`, and this function
 * is called from the main-thread block walk.
 */
internal fun parseNovaTable(
    lines: List<String>,
    startIndex: Int,
    inline: (String) -> List<NovaInline>
): Pair<NovaBlock.Table, Int>? {
    if (startIndex < 0 || startIndex + 1 >= lines.size) return null
    val headerLine = lines[startIndex].trim()
    val separatorLine = lines[startIndex + 1].trim()
    if (!headerLine.contains('|') || !separatorLine.contains('|')) return null

    val separators = splitNovaTableRow(separatorLine)
    if (separators.isEmpty()) return null
    if (separators.any { !SEPARATOR_CELL.matches(it.trim()) }) return null

    val headers = splitNovaTableRow(headerLine)
    if (headers.all { it.isBlank() }) return null

    val alignments = separators.map { cell ->
        val trimmed = cell.trim()
        when {
            trimmed.startsWith(":") && trimmed.endsWith(":") -> NovaAlign.Center
            trimmed.endsWith(":") -> NovaAlign.End
            trimmed.startsWith(":") -> NovaAlign.Start
            else -> NovaAlign.Start
        }
    }

    val rows = mutableListOf<List<NovaTableCell>>()
    var i = startIndex + 2
    while (i < lines.size) {
        val rowLine = lines[i].trim()
        if (rowLine.isEmpty() || !rowLine.contains('|')) break
        val cells = splitNovaTableRow(rowLine)
        rows += headers.indices.map { index ->
            NovaTableCell(
                inlines = inline(cells.getOrElse(index) { "" }),
                align = alignments.getOrElse(index) { NovaAlign.Start }
            )
        }
        i++
    }

    val table = NovaBlock.Table(
        headers = headers.mapIndexed { index, cell ->
            NovaTableCell(
                inline(cell),
                alignments.getOrElse(index) { NovaAlign.Start }
            )
        },
        rows = rows
    )
    return table to i
}

/** One table row, respecting `\|` and dropping the outer pipes. */
internal fun splitNovaTableRow(line: String): List<String> {
    val cells = mutableListOf<String>()
    val current = StringBuilder()
    var i = 0
    while (i < line.length) {
        val char = line[i]
        when {
            char == '\\' && i + 1 < line.length && line[i + 1] == '|' -> {
                current.append('|')
                i += 2
            }

            char == '|' -> {
                cells += current.toString().trim()
                current.clear()
                i++
            }

            else -> {
                current.append(char)
                i++
            }
        }
    }
    cells += current.toString().trim()

    // A leading/trailing pipe produces an empty outer cell; it is a delimiter,
    // not an empty column.
    if (cells.isNotEmpty() && cells.first().isEmpty()) cells.removeAt(0)
    if (cells.isNotEmpty() && cells.last().isEmpty()) cells.removeAt(cells.lastIndex)
    return cells
}

// ---------------------------------------------------------------------------
// Whole-line images, videos and previews
// ---------------------------------------------------------------------------

internal fun standaloneNovaImage(trimmed: String): NovaBlock.Image? {
    MD_IMAGE_LINE.matchEntire(trimmed)?.let { match ->
        return NovaBlock.Image(
            url = novaAbsoluteUrl(match.groupValues[2]),
            alt = match.groupValues[1].takeIf { it.isNotBlank() }
        )
    }
    ANILIST_IMAGE_LINE.matchEntire(trimmed)?.let { match ->
        return NovaBlock.Image(
            url = novaAbsoluteUrl(match.groupValues[2]),
            width = normalizeNovaImageWidth(match.groupValues[1])
        )
    }
    HTML_IMAGE_LINE.matchEntire(trimmed)?.let { match ->
        return htmlNovaImage(match.groupValues[1])?.let { image ->
            NovaBlock.Image(
                url = image.url,
                alt = image.alt,
                width = image.width,
                height = image.height
            )
        }
    }
    val linked = LINKED_LINE.matchEntire(trimmed)
    if (linked != null) {
        val inner = linked.groupValues[1].trim()
        val target = novaAbsoluteUrl(linked.groupValues[2])
        val image = standaloneNovaImage(inner) ?: return null
        return image.copy(targetUrl = target)
    }
    PARENTHESISED_URL.matchEntire(trimmed)?.let { match ->
        val url = match.groupValues[1]
        if (IMAGE_EXTENSION.containsMatchIn(url)) {
            return NovaBlock.Image(novaAbsoluteUrl(url))
        }
    }
    if (IMAGE_URL.matches(trimmed)) {
        return NovaBlock.Image(novaAbsoluteUrl(trimmed))
    }
    return null
}

internal fun htmlNovaImage(attrs: String): NovaInline.Image? {
    val src = novaHtmlAttribute(attrs, "src")?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return NovaInline.Image(
        url = novaAbsoluteUrl(src),
        alt = novaHtmlAttribute(attrs, "alt")?.trim()?.takeIf { it.isNotEmpty() },
        width = novaImageAttributeLength(attrs, "width", STYLE_WIDTH, allowPercent = true),
        height = novaImageAttributeLength(attrs, "height", STYLE_HEIGHT, allowPercent = false)
    )
}

/**
 * The size an `width=`/`height=` attribute or an inline style asks for.
 *
 * A declared attribute that is unreadable (`width="auto"`, `width="50 %"`) is not
 * a size, so the style still gets a say. Taking the first *raw* value instead
 * threw the style away whenever the attribute was present but useless.
 */
private fun novaImageAttributeLength(
    attrs: String,
    attribute: String,
    stylePattern: Regex,
    allowPercent: Boolean
): String? =
    normalizeNovaImageLength(novaHtmlAttribute(attrs, attribute), allowPercent)
        ?: normalizeNovaImageLength(novaHtmlStyleLength(attrs, stylePattern), allowPercent)

/** A `width:`/`height:` value in the image's inline `style`, if it has one. */
private fun novaHtmlStyleLength(attrs: String, pattern: Regex): String? {
    val style = novaHtmlAttribute(attrs, "style") ?: return null
    return pattern.find(style)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }
}

/**
 * The size a `width` attribute asked for, in NovaMarkdown's spelling.
 *
 * `220`, `220px` and `50%` mean what they say; `auto` and anything unreadable
 * mean "the file's own size", which is the same as writing no width at all. A
 * zero or negative width is not a small image, it is an invisible one, so it
 * degrades to the default rather than to a blank line.
 */
internal fun normalizeNovaImageWidth(raw: String?): String? {
    return normalizeNovaImageLength(raw, allowPercent = true)
}

/**
 * The size an HTML `height` or `style="height:…"` asked for, in dp.
 *
 * A percentage height has no column to be a percentage of in this layout, so it
 * is refused rather than guessed at; the image keeps its own aspect ratio.
 */
internal fun normalizeNovaImageHeight(raw: String?): String? {
    return normalizeNovaImageLength(raw, allowPercent = false)
}

private fun normalizeNovaImageLength(raw: String?, allowPercent: Boolean): String? {
    val value = raw?.trim()?.lowercase(Locale.ROOT)
        ?.substringBefore('!') // `width: 50px !important`
        ?.trim()
        ?.removeSuffix("px")
        ?.takeIf { it.isNotEmpty() }
        ?: return null
    if (value == "auto") return null
    val percent = value.endsWith("%")
    if (percent && !allowPercent) return null
    val number = (if (percent) value.dropLast(1) else value).trim().toIntOrNull() ?: return null
    if (number <= 0) return null
    return if (percent) "$number%" else number.toString()
}

internal fun parseNovaVideo(trimmed: String): NovaBlock.Video? {
    YOUTUBE_MARK.matchEntire(trimmed)?.let { match ->
        val target = match.groupValues[1].trim()
        return NovaBlock.Video(youtubeUrl(target), youtube = true)
    }
    WEBM_MARK.matchEntire(trimmed)?.let { match ->
        return NovaBlock.Video(novaAbsoluteUrl(match.groupValues[1].trim()), youtube = false)
    }
    PARENTHESISED_URL.matchEntire(trimmed)?.let { match ->
        val url = match.groupValues[1]
        if (isNovaVideoUrl(url)) {
            return NovaBlock.Video(novaAbsoluteUrl(url), youtube = YOUTUBE_HOST.containsMatchIn(url))
        }
    }
    if (BARE_URL.matches(trimmed) && isNovaVideoUrl(trimmed)) {
        return NovaBlock.Video(
            novaAbsoluteUrl(trimmed),
            youtube = YOUTUBE_HOST.containsMatchIn(trimmed)
        )
    }
    return null
}

private fun isNovaVideoUrl(url: String): Boolean =
    YOUTUBE_HOST.containsMatchIn(url) || VIDEO_EXTENSION.containsMatchIn(url)

private fun youtubeUrl(value: String): String = when {
    value.startsWith("http", ignoreCase = true) -> value
    value.startsWith("//") -> "https:$value"
    else -> "https://www.youtube.com/watch?v=$value"
}

/** `(type, slug, id)` for an AniList entity address, or null. */
internal fun parseNovaEntityUrl(url: String): Triple<String, String, String>? {
    val match = ANILIST_ENTITY.matchEntire(url.trim()) ?: return null
    val type = match.groupValues[1].uppercase(Locale.ROOT)
    val id = match.groupValues[2]
    val slug = match.groupValues[3]
    return Triple(type, slug, id)
}

internal fun novaTitleFromSlug(slug: String, type: String, id: String): String {
    val prettyType = type.lowercase(Locale.ROOT).replaceFirstChar { it.titlecase(Locale.ROOT) }
    val raw = slug.ifBlank {
        id.takeIf { candidate -> candidate.any { !it.isDigit() } } ?: "$prettyType #$id"
    }
    return raw.replace('-', ' ').replace('_', ' ')
        .split(' ')
        .filter { it.isNotBlank() }
        .joinToString(" ") { word ->
            word.replaceFirstChar {
                if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
            }
        }
}

// ---------------------------------------------------------------------------
// HTML attributes
// ---------------------------------------------------------------------------

/**
 * One attribute of an HTML tag, scanning rather than searching.
 *
 * A regex over the whole tag cannot tell an attribute name from the same word
 * inside another attribute's value: `<img src="…?width=100" width="220">` has
 * two `width=` in it, and only the second is the author's size. Values are
 * consumed as values here, so a query string can never answer for an attribute.
 */
internal fun novaHtmlAttribute(tag: String, name: String): String? {
    var i = 0
    while (i < tag.length) {
        i = skipNovaHtmlAttributeSpace(tag, i)
        if (i >= tag.length) break

        val (attribute, afterName) = readNovaHtmlAttributeName(tag, i)
        i = afterName
        i = skipNovaHtmlWhitespace(tag, i)
        if (i >= tag.length || tag[i] != '=') continue

        i++
        i = skipNovaHtmlWhitespace(tag, i)
        val (value, next) = readNovaHtmlAttributeValue(tag, i)
        i = next
        if (attribute.equals(name, ignoreCase = true)) return value
    }
    return null
}

private fun skipNovaHtmlAttributeSpace(tag: String, start: Int): Int {
    var i = start
    while (i < tag.length && (tag[i].isWhitespace() || tag[i] == '/')) i++
    return i
}

private fun skipNovaHtmlWhitespace(tag: String, start: Int): Int {
    var i = start
    while (i < tag.length && tag[i].isWhitespace()) i++
    return i
}

private fun readNovaHtmlAttributeName(tag: String, start: Int): Pair<String, Int> {
    var i = start
    while (i < tag.length && tag[i] != '=' && !tag[i].isWhitespace()) i++
    return tag.substring(start, i) to i
}

private fun readNovaHtmlAttributeValue(tag: String, start: Int): Pair<String, Int> {
    var i = start
    val value = if (i < tag.length && (tag[i] == '"' || tag[i] == '\'')) {
        val quote = tag[i]
        i++
        val valueStart = i
        while (i < tag.length && tag[i] != quote) i++
        tag.substring(valueStart, i).also { if (i < tag.length) i++ }
    } else {
        val valueStart = i
        while (i < tag.length && !tag[i].isWhitespace() && tag[i] != '>') i++
        tag.substring(valueStart, i)
    }
    return value to i
}

internal fun findClosingTag(source: String, tag: String, from: Int): IntRange? {
    val pattern = Regex("""</?\s*$tag\b[^>]*>""", RegexOption.IGNORE_CASE)
    var depth = 1
    var cursor = from
    while (true) {
        val match = pattern.find(source, cursor) ?: return null
        if (match.value.startsWith("</")) depth-- else depth++
        if (depth == 0) return match.range
        cursor = match.range.last + 1
    }
}
