package com.example.ui.components.markdown

/**
 * Tokenizer and AST parser for AniList-flavored Markdown.
 *
 * Handles:
 *  - Headers (`# `, `## `, `### `, `#### `, etc.)
 *  - Blockquotes (`> `)
 *  - Code blocks (```` ````) and inline code (`` `code` ``)
 *  - Checklists (`- [ ]`, `- [x]`) and lists (`- `, `* `, `1. `)
 *  - AniList image embeds (`img(url)`, `img220(url)`, `![alt](url)`, linked images)
 *  - Videos (`youtube(id/url)`, `webm(url)`)
 *  - Centered blocks (`~~~center`, `<center>`, `~~~`)
 *  - Horizontal dividers (`---`, `***`, `___`)
 *  - Spoilers (`~!spoiler!~`)
 *  - Links (`[label](url)`), raw auto-URLs, and user mentions (`@username`)
 *  - Bold (`**`, `__`, `<b>`), Italic (`*`, `_`, `<i>`), Underline (`<u>`), Strikethrough (`~~`, `<s>`)
 */
object AniListMarkdownParser {

    private val HEADER_PATTERN = Regex("""^(#{1,6})\s+(.+)$""")
    private val CHECKLIST_PATTERN = Regex("""^[-*+]\s+\[([ xX])]\s*(.+)$""")
    private val UNORDERED_LIST_PATTERN = Regex("""^[-*+]\s+(.+)$""")
    private val ORDERED_LIST_PATTERN = Regex("""^\d+\.\s+(.+)$""")
    private val HORIZONTAL_RULE_PATTERN = Regex("""^(\*{3,}|-{3,}|_{3,})$""")
    private val LINKED_MD_IMG_PATTERN = Regex("""^\[!\[([^\]]*)]\((https?://[^)]+)\)]\((https?://[^\s)]+)\)$""")
    private val LINKED_ANILIST_IMG_PATTERN = Regex("""^\[img(?:\d+%|\d+)?\((https?://[^)]+)\)]\((https?://[^\s)]+)\)$""", RegexOption.IGNORE_CASE)
    private val ANILIST_IMG_PATTERN = Regex("""^img(?:\d+%|\d+)?\((https?://[^)]+)\)$|^image\((https?://[^)]+)\)$""", RegexOption.IGNORE_CASE)
    private val MD_IMG_PATTERN = Regex("""^!\[([^\]]*)]\((https?://[^)]+)\)$""")
    private val YOUTUBE_PATTERN = Regex("""^youtube\(([^)]+)\)$""", RegexOption.IGNORE_CASE)
    private val WEBM_PATTERN = Regex("""^webm\(([^)]+)\)$""", RegexOption.IGNORE_CASE)
    private val ANILIST_SPOILER_BLOCK = Regex("""^~!\s*([\s\S]*?)\s*!~$""")
    private val ANILIST_PREVIEW_PATTERN = Regex("""^https?://anilist\.co/(anime|manga|character|staff|studio|user)/(\d+|[a-zA-Z0-9_-]+)(?:/([^\s)]+))?$""", RegexOption.IGNORE_CASE)

    // Inline regex patterns
    private val INLINE_LINKED_IMG = Regex("""\[!\[([^\]]*)]\((https?://[^)]+)\)]\((https?://[^\s)]+)\)""")
    private val INLINE_LINK = Regex("""\[([^\]]+)]\((https?://[^\s)]+)\)""")
    private val INLINE_AUTO_URL = Regex("""https?://[^\s<>"'()\[\]{}]+""")
    private val INLINE_SPOILER = Regex("""~!([\s\S]*?)!~""")
    private val INLINE_CODE = Regex("""`([^`]+)`""")
    private val INLINE_BOLD_ITALIC = Regex("""(?:\*\*\*|___)(.+?)(?:\*\*\*|___)""")
    private val INLINE_BOLD = Regex("""(?:\*\*|__)(.+?)(?:\*\*|__)|<b>(.+?)</b>|<strong>(.+?)</strong>""", RegexOption.IGNORE_CASE)
    private val INLINE_ITALIC = Regex("""(?:\*|_)(.+?)(?:\*|_)|<i>(.+?)</i>|<em>(.+?)</em>""", RegexOption.IGNORE_CASE)
    private val INLINE_UNDERLINE = Regex("""<u>(.+?)</u>""", RegexOption.IGNORE_CASE)
    private val INLINE_STRIKETHROUGH = Regex("""~~(.+?)~~|<s>(.+?)</s>|<del>(.+?)</del>""", RegexOption.IGNORE_CASE)
    private val INLINE_MENTION = Regex("""@([a-zA-Z0-9_-]+)""")

    fun parse(raw: String): List<MarkdownNode> {
        val clean = raw.replace("\r\n", "\n").replace("\r", "\n").replace("<br>", "\n").replace("<br/>", "\n").replace("<br />", "\n").trim()
        if (clean.isBlank()) return emptyList()

        val nodes = mutableListOf<MarkdownNode>()
        val lines = clean.lines()
        var i = 0

        while (i < lines.size) {
            val line = lines[i].trimEnd()

            if (line.isBlank()) {
                i++
                continue
            }

            // Centered block (~~~center, <center>, <div align="center">, etc.)
            val trimmedLine = line.trim()
            if (trimmedLine.equals("~~~center", ignoreCase = true) ||
                trimmedLine.equals("<center>", ignoreCase = true) ||
                trimmedLine.equals("<div align=\"center\">", ignoreCase = true) ||
                trimmedLine.equals("<div style=\"text-align: center;\">", ignoreCase = true) ||
                (trimmedLine == "~~~" && (i + 1 < lines.size))
            ) {
                val centerLines = mutableListOf<String>()
                i++
                while (i < lines.size &&
                    !lines[i].trim().equals("~~~", ignoreCase = true) &&
                    !lines[i].trim().equals("</center>", ignoreCase = true) &&
                    !lines[i].trim().equals("</div>", ignoreCase = true)
                ) {
                    centerLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing delimiter
                val centerContent = parse(centerLines.joinToString("\n"))
                nodes.add(MarkdownNode.CenteredBlock(centerContent))
                continue
            }

            // AniList entity preview card check
            val anilistPreviewMatch = ANILIST_PREVIEW_PATTERN.matchEntire(trimmedLine)
            if (anilistPreviewMatch != null) {
                val type = anilistPreviewMatch.groupValues[1].uppercase()
                val id = anilistPreviewMatch.groupValues[2]
                val rawSlug = anilistPreviewMatch.groupValues[3].ifEmpty { "$type #$id" }
                val title = rawSlug.replace('-', ' ').replace('_', ' ').split(' ').joinToString(" ") { word ->
                    word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                }
                nodes.add(MarkdownNode.AniListPreviewBlock(url = trimmedLine, type = type, id = id, title = title))
                i++
                continue
            }

            // Code block check
            if (line.startsWith("```")) {
                val lang = line.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++
                nodes.add(MarkdownNode.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            // Horizontal rule
            if (HORIZONTAL_RULE_PATTERN.matches(trimmedLine)) {
                nodes.add(MarkdownNode.Divider)
                i++
                continue
            }

            // Headers
            val headerMatch = HEADER_PATTERN.matchEntire(trimmedLine)
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val content = headerMatch.groupValues[2].trim()
                nodes.add(MarkdownNode.Header(level, parseInlines(content)))
                i++
                continue
            }

            // Linked images: [![Alt](imgUrl)](targetUrl) or [img(imgUrl)](targetUrl)
            val linkedMdImgMatch = LINKED_MD_IMG_PATTERN.matchEntire(trimmedLine)
            if (linkedMdImgMatch != null) {
                val alt = linkedMdImgMatch.groupValues[1].takeIf { it.isNotBlank() }
                val imgUrl = linkedMdImgMatch.groupValues[2]
                val targetUrl = linkedMdImgMatch.groupValues[3]
                nodes.add(MarkdownNode.ImageBlock(url = imgUrl, alt = alt, targetUrl = targetUrl))
                i++
                continue
            }

            val linkedAnilistImgMatch = LINKED_ANILIST_IMG_PATTERN.matchEntire(trimmedLine)
            if (linkedAnilistImgMatch != null) {
                val imgUrl = linkedAnilistImgMatch.groupValues[1]
                val targetUrl = linkedAnilistImgMatch.groupValues[2]
                nodes.add(MarkdownNode.ImageBlock(url = imgUrl, targetUrl = targetUrl))
                i++
                continue
            }

            // AniList / Markdown standalone images
            val anilistImgMatch = ANILIST_IMG_PATTERN.matchEntire(trimmedLine)
            if (anilistImgMatch != null) {
                val url = anilistImgMatch.groupValues[1].ifEmpty { anilistImgMatch.groupValues[2] }
                nodes.add(MarkdownNode.ImageBlock(url = url, alt = null))
                i++
                continue
            }

            val mdImgMatch = MD_IMG_PATTERN.matchEntire(trimmedLine)
            if (mdImgMatch != null) {
                val alt = mdImgMatch.groupValues[1].takeIf { it.isNotBlank() }
                nodes.add(MarkdownNode.ImageBlock(url = mdImgMatch.groupValues[2], alt = alt))
                i++
                continue
            }

            // Video embeds
            val ytMatch = YOUTUBE_PATTERN.matchEntire(trimmedLine)
            if (ytMatch != null) {
                val target = ytMatch.groupValues[1].trim()
                val url = if (target.startsWith("http")) target else "https://www.youtube.com/watch?v=$target"
                nodes.add(MarkdownNode.VideoBlock(url, isYoutube = true))
                i++
                continue
            }

            val webmMatch = WEBM_PATTERN.matchEntire(trimmedLine)
            if (webmMatch != null) {
                nodes.add(MarkdownNode.VideoBlock(webmMatch.groupValues[1].trim(), isYoutube = false))
                i++
                continue
            }

            // Blockquote
            if (line.trimStart().startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trimStart().startsWith(">")) {
                    quoteLines.add(lines[i].trimStart().removePrefix(">").trim())
                    i++
                }
                val text = quoteLines.joinToString(" ")
                nodes.add(MarkdownNode.Blockquote(parseInlines(text)))
                continue
            }

            // Checklist
            if (CHECKLIST_PATTERN.matches(trimmedLine)) {
                val items = mutableListOf<CheckListItem>()
                while (i < lines.size && CHECKLIST_PATTERN.matches(lines[i].trim())) {
                    val match = CHECKLIST_PATTERN.matchEntire(lines[i].trim())!!
                    val checked = match.groupValues[1].trim().equals("x", ignoreCase = true)
                    val text = match.groupValues[2]
                    items.add(CheckListItem(checked = checked, inlines = parseInlines(text)))
                    i++
                }
                nodes.add(MarkdownNode.CheckListBlock(items = items))
                continue
            }

            // Unordered list
            if (UNORDERED_LIST_PATTERN.matches(trimmedLine)) {
                val items = mutableListOf<List<InlineToken>>()
                while (i < lines.size && UNORDERED_LIST_PATTERN.matches(lines[i].trim())) {
                    val itemText = UNORDERED_LIST_PATTERN.matchEntire(lines[i].trim())!!.groupValues[1]
                    items.add(parseInlines(itemText))
                    i++
                }
                nodes.add(MarkdownNode.ListBlock(ordered = false, items = items))
                continue
            }

            // Ordered list
            if (ORDERED_LIST_PATTERN.matches(trimmedLine)) {
                val items = mutableListOf<List<InlineToken>>()
                while (i < lines.size && ORDERED_LIST_PATTERN.matches(lines[i].trim())) {
                    val itemText = ORDERED_LIST_PATTERN.matchEntire(lines[i].trim())!!.groupValues[1]
                    items.add(parseInlines(itemText))
                    i++
                }
                nodes.add(MarkdownNode.ListBlock(ordered = true, items = items))
                continue
            }

            // AniList Spoiler block ~!... !~
            val spoilerMatch = ANILIST_SPOILER_BLOCK.matchEntire(trimmedLine)
            if (spoilerMatch != null) {
                val content = spoilerMatch.groupValues[1].trim()
                nodes.add(MarkdownNode.SpoilerBlock(parseInlines(content)))
                i++
                continue
            }

            // Markdown Table check
            val tableResult = tryParseTable(lines, i) { parseInlines(it) }
            if (tableResult != null) {
                nodes.add(tableResult.first)
                i = tableResult.second
                continue
            }

            // General Paragraph
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank() &&
                !lines[i].startsWith("```") &&
                !HEADER_PATTERN.matches(lines[i].trim()) &&
                !lines[i].trimStart().startsWith(">") &&
                !lines[i].trimStart().startsWith("|") &&
                !CHECKLIST_PATTERN.matches(lines[i].trim()) &&
                !UNORDERED_LIST_PATTERN.matches(lines[i].trim()) &&
                !ORDERED_LIST_PATTERN.matches(lines[i].trim()) &&
                !HORIZONTAL_RULE_PATTERN.matches(lines[i].trim()) &&
                !lines[i].trim().equals("~~~center", ignoreCase = true) &&
                !lines[i].trim().equals("<center>", ignoreCase = true) &&
                !ANILIST_IMG_PATTERN.matches(lines[i].trim()) &&
                !MD_IMG_PATTERN.matches(lines[i].trim()) &&
                !LINKED_MD_IMG_PATTERN.matches(lines[i].trim()) &&
                !LINKED_ANILIST_IMG_PATTERN.matches(lines[i].trim()) &&
                !YOUTUBE_PATTERN.matches(lines[i].trim()) &&
                !WEBM_PATTERN.matches(lines[i].trim()) &&
                !ANILIST_SPOILER_BLOCK.matches(lines[i].trim())
            ) {
                paragraphLines.add(lines[i].trim())
                i++
            }
            if (paragraphLines.isNotEmpty()) {
                val fullText = paragraphLines.joinToString(" ")
                nodes.add(MarkdownNode.Paragraph(parseInlines(fullText)))
            }
        }

        return nodes
    }

    /**
     * Parses inline tokens: links, spoilers, bold/italic, underline, code, strikethrough, mentions, auto URLs, and plain text.
     */
    fun parseInlines(text: String): List<InlineToken> {
        if (text.isEmpty()) return emptyList()

        val tokens = mutableListOf<InlineToken>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val linkMatch = INLINE_LINK.find(remaining)
            val spoilerMatch = INLINE_SPOILER.find(remaining)
            val codeMatch = INLINE_CODE.find(remaining)
            val boldItalicMatch = INLINE_BOLD_ITALIC.find(remaining)
            val boldMatch = INLINE_BOLD.find(remaining)
            val italicMatch = INLINE_ITALIC.find(remaining)
            val underlineMatch = INLINE_UNDERLINE.find(remaining)
            val strikeMatch = INLINE_STRIKETHROUGH.find(remaining)
            val mentionMatch = INLINE_MENTION.find(remaining)
            val autoUrlMatch = INLINE_AUTO_URL.find(remaining)

            val matches = listOfNotNull(
                linkMatch?.let { it to "LINK" },
                spoilerMatch?.let { it to "SPOILER" },
                codeMatch?.let { it to "CODE" },
                boldItalicMatch?.let { it to "BOLD_ITALIC" },
                boldMatch?.let { it to "BOLD" },
                italicMatch?.let { it to "ITALIC" },
                underlineMatch?.let { it to "UNDERLINE" },
                strikeMatch?.let { it to "STRIKE" },
                mentionMatch?.let { it to "MENTION" },
                autoUrlMatch?.let { it to "AUTO_URL" }
            ).sortedBy { it.first.range.first }

            if (matches.isEmpty()) {
                tokens.add(InlineToken.Plain(remaining))
                break
            }

            val (firstMatch, type) = matches.first()
            val start = firstMatch.range.first

            if (start > 0) {
                tokens.add(InlineToken.Plain(remaining.substring(0, start)))
            }

            when (type) {
                "LINK" -> {
                    val label = firstMatch.groupValues[1]
                    val url = firstMatch.groupValues[2]
                    tokens.add(InlineToken.Link(label, url))
                }
                "AUTO_URL" -> {
                    val url = firstMatch.value
                    tokens.add(InlineToken.Link(url, url))
                }
                "SPOILER" -> {
                    tokens.add(InlineToken.Spoiler(firstMatch.groupValues[1]))
                }
                "CODE" -> {
                    tokens.add(InlineToken.Code(firstMatch.groupValues[1]))
                }
                "BOLD_ITALIC" -> {
                    tokens.add(InlineToken.BoldItalic(firstMatch.groupValues[1]))
                }
                "BOLD" -> {
                    val content = firstMatch.groupValues[1].ifEmpty {
                        firstMatch.groupValues[2].ifEmpty { firstMatch.groupValues[3] }
                    }
                    tokens.add(InlineToken.Bold(content))
                }
                "ITALIC" -> {
                    val content = firstMatch.groupValues[1].ifEmpty {
                        firstMatch.groupValues[2].ifEmpty { firstMatch.groupValues[3] }
                    }
                    tokens.add(InlineToken.Italic(content))
                }
                "UNDERLINE" -> {
                    tokens.add(InlineToken.Underline(firstMatch.groupValues[1]))
                }
                "STRIKE" -> {
                    val content = firstMatch.groupValues[1].ifEmpty {
                        firstMatch.groupValues[2].ifEmpty { firstMatch.groupValues[3] }
                    }
                    tokens.add(InlineToken.Strikethrough(content))
                }
                "MENTION" -> {
                    val user = firstMatch.groupValues[1]
                    tokens.add(InlineToken.Mention(user))
                }
            }

            remaining = remaining.substring(firstMatch.range.last + 1)
        }

        return tokens
    }
}
