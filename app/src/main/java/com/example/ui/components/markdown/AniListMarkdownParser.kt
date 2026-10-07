package com.example.ui.components.markdown

/**
 * Tokenizer and AST parser for AniList-flavored Markdown.
 *
 * Handles:
 *  - Headers (`# `, `## `, `### `, `#### `)
 *  - Blockquotes (`> `)
 *  - Code blocks (```` ````) and inline code (`` `code` ``)
 *  - Unordered (`- `, `* `) and ordered (`1. `) lists
 *  - AniList image embeds (`img(url)`, `img220(url)`, `![alt](url)`)
 *  - Horizontal dividers (`---`, `***`)
 *  - Spoilers (`~!spoiler!~`)
 *  - Links (`[label](url)`)
 *  - Bold (`**`, `__`), Italic (`*`, `_`), Strikethrough (`~~`)
 */
object AniListMarkdownParser {

    private val CODE_BLOCK_PATTERN = Regex("""^```([a-zA-Z0-9_-]*)\n([\s\S]*?)\n```""", RegexOption.MULTILINE)
    private val HEADER_PATTERN = Regex("""^(#{1,6})\s+(.+)$""")
    private val BLOCKQUOTE_PATTERN = Regex("""^>\s*(.*)$""")
    private val UNORDERED_LIST_PATTERN = Regex("""^[-*+]\s+(.+)$""")
    private val ORDERED_LIST_PATTERN = Regex("""^\d+\.\s+(.+)$""")
    private val HORIZONTAL_RULE_PATTERN = Regex("""^(\*{3,}|-{3,}|_{3,})$""")
    private val ANILIST_IMG_PATTERN = Regex("""^img\d*\((https?://[^)]+)\)$""", RegexOption.IGNORE_CASE)
    private val MD_IMG_PATTERN = Regex("""^!\[([^\]]*)]\((https?://[^)]+)\)$""")
    private val ANILIST_SPOILER_BLOCK = Regex("""^~!\s*([\s\S]*?)\s*!~$""")

    // Inline regex patterns
    private val INLINE_LINK = Regex("""\[([^\]]+)]\((https?://[^\s)]+)\)""")
    private val INLINE_SPOILER = Regex("""~!([\s\S]*?)!~""")
    private val INLINE_CODE = Regex("""`([^`]+)`""")
    private val INLINE_BOLD_ITALIC = Regex("""(?:\*\*\*|___)(.+?)(?:\*\*\*|___)""")
    private val INLINE_BOLD = Regex("""(?:\*\*|__)(.+?)(?:\*\*|__)""")
    private val INLINE_ITALIC = Regex("""(?:\*|_)(.+?)(?:\*|_)""")
    private val INLINE_STRIKETHROUGH = Regex("""~~(.+?)~~""")
    private val ANILIST_INLINE_IMG = Regex("""img\d*\((https?://[^)]+)\)""", RegexOption.IGNORE_CASE)

    fun parse(raw: String): List<MarkdownNode> {
        val clean = raw.replace("\r\n", "\n").replace("\r", "\n").trim()
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

            // Code block check
            if (line.startsWith("```")) {
                val lang = line.removePrefix("```").trim().takeIf { it.isNotEmpty() }
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trimEnd().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // skip closing ```
                nodes.add(MarkdownNode.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            // Horizontal rule
            if (HORIZONTAL_RULE_PATTERN.matches(line.trim())) {
                nodes.add(MarkdownNode.Divider)
                i++
                continue
            }

            // Headers
            val headerMatch = HEADER_PATTERN.matchEntire(line.trim())
            if (headerMatch != null) {
                val level = headerMatch.groupValues[1].length
                val content = headerMatch.groupValues[2].trim()
                nodes.add(MarkdownNode.Header(level, parseInlines(content)))
                i++
                continue
            }

            // AniList / Markdown images
            val anilistImgMatch = ANILIST_IMG_PATTERN.matchEntire(line.trim())
            if (anilistImgMatch != null) {
                nodes.add(MarkdownNode.ImageBlock(anilistImgMatch.groupValues[1], null))
                i++
                continue
            }

            val mdImgMatch = MD_IMG_PATTERN.matchEntire(line.trim())
            if (mdImgMatch != null) {
                val alt = mdImgMatch.groupValues[1].takeIf { it.isNotBlank() }
                nodes.add(MarkdownNode.ImageBlock(mdImgMatch.groupValues[2], alt))
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

            // Unordered list
            if (UNORDERED_LIST_PATTERN.matches(line.trim())) {
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
            if (ORDERED_LIST_PATTERN.matches(line.trim())) {
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
            val spoilerMatch = ANILIST_SPOILER_BLOCK.matchEntire(line.trim())
            if (spoilerMatch != null) {
                val content = spoilerMatch.groupValues[1].trim()
                nodes.add(MarkdownNode.SpoilerBlock(parseInlines(content)))
                i++
                continue
            }

            // General Paragraph (accumulate continuous non-empty lines)
            val paragraphLines = mutableListOf<String>()
            while (i < lines.size && lines[i].isNotBlank() &&
                !lines[i].startsWith("```") &&
                !HEADER_PATTERN.matches(lines[i].trim()) &&
                !lines[i].trimStart().startsWith(">") &&
                !UNORDERED_LIST_PATTERN.matches(lines[i].trim()) &&
                !ORDERED_LIST_PATTERN.matches(lines[i].trim()) &&
                !HORIZONTAL_RULE_PATTERN.matches(lines[i].trim())
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
     * Parses inline tokens: links, spoilers, bold/italic, code, strikethrough, and plain text.
     */
    fun parseInlines(text: String): List<InlineToken> {
        if (text.isEmpty()) return emptyList()

        val tokens = mutableListOf<InlineToken>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            // Find closest match across inline patterns
            val linkMatch = INLINE_LINK.find(remaining)
            val spoilerMatch = INLINE_SPOILER.find(remaining)
            val codeMatch = INLINE_CODE.find(remaining)
            val boldItalicMatch = INLINE_BOLD_ITALIC.find(remaining)
            val boldMatch = INLINE_BOLD.find(remaining)
            val italicMatch = INLINE_ITALIC.find(remaining)
            val strikeMatch = INLINE_STRIKETHROUGH.find(remaining)

            val matches = listOfNotNull(
                linkMatch?.let { it to "LINK" },
                spoilerMatch?.let { it to "SPOILER" },
                codeMatch?.let { it to "CODE" },
                boldItalicMatch?.let { it to "BOLD_ITALIC" },
                boldMatch?.let { it to "BOLD" },
                italicMatch?.let { it to "ITALIC" },
                strikeMatch?.let { it to "STRIKE" }
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
                    tokens.add(InlineToken.Bold(firstMatch.groupValues[1]))
                }
                "ITALIC" -> {
                    tokens.add(InlineToken.Italic(firstMatch.groupValues[1]))
                }
                "STRIKE" -> {
                    tokens.add(InlineToken.Strikethrough(firstMatch.groupValues[1]))
                }
            }

            remaining = remaining.substring(firstMatch.range.last + 1)
        }

        return tokens
    }
}
