package com.anisequel.ui.components.markdown

import java.util.Locale

/**
 * Tokenizer and AST parser for AniList-flavored Markdown.
 *
 * Handles:
 *  - Headers (`# `, `## `, `### `, `#### `, etc.)
 *  - Blockquotes (`> `)
 *  - Code blocks (```` ````) and inline code (`` `code` ``)
 *  - Checklists (`- [ ]`, `- [x]`) and lists (`- `, `* `, `1. `)
 *  - AniList image embeds (`img(url)`, `img220(url)`, `![alt](url)`, linked images)
 *  - Bare image/GIF URLs on their own line (`https://.../x.gif`) as an embed
 *  - Inline images and GIFs inside a paragraph (`Text ![alt](x.gif) more`)
 *  - Videos (`youtube(id/url)`, `webm(url)`)
 *  - Centered blocks (`~~~center`, `<center>`, `~~~`) and centred lines (`~~~text~~~`)
 *  - Horizontal dividers (`---`, `***`, `___`)
 *  - Setext headings (a line underlined with `===`)
 *  - Spoilers (`~!spoiler!~`)
 *  - Links (`[label](url)`), raw auto-URLs, and user mentions (`@username`)
 *  - Bold (`**`, `__`, `<b>`), Italic (`*`, `_`, `<i>`), Underline (`<u>`), Strikethrough (`~~`, `<s>`)
 */
object AniListMarkdownParser {

    /**
     * The HTML escapes AniList's editor leaves in a bio, and their numeric forms.
     *
     * A bio is authored in a rich-text editor and stored with its escapes intact:
     * `&amp;` for an ampersand, `&quot;` for a double quote, `&#39;` for an
     * apostrophe, `&#123;` / `&#x1F600;` for anything else. Nothing decoded them,
     * so the characters reached the reader as the escape sequences - an
     * apostrophe printed as the five characters `&#39;` - and, the one that cost
     * the most, an image URL whose query string carried `&amp;` was *requested*
     * with the literal escape still in it and 404ed, so the artwork stayed blank
     * over markup that looked perfectly correct.
     *
     * ## Why one pattern and not a chain of `replace` calls
     *
     * A chain decodes twice. `&amp;lt;` is how an author's literal `&lt;` text is
     * stored, and it has to come out as those four characters - but
     * `replace("&amp;", "&")` and `replace("&lt;", "<")`, in either order, turn
     * it into `<`. One pass over the input, whose replacement text is never
     * re-scanned, is what keeps every escape decoded exactly once.
     */
    private val HTML_ENTITY_PATTERN = Regex("""&(amp|lt|gt|quot|apos|nbsp|#[0-9]+|#[xX][0-9a-fA-F]+);""")

    /**
     * The named half of [HTML_ENTITY_PATTERN].
     *
     * `&nbsp;` decodes to U+00A0 rather than to a plain space: the two look
     * identical in a text run and break differently, and the author who asked
     * for a non-breaking space asked for that difference.
     */
    private val NAMED_HTML_ENTITIES = mapOf(
        "amp" to "&",
        "lt" to "<",
        "gt" to ">",
        "quot" to "\"",
        "apos" to "'",
        "nbsp" to "\u00A0"
    )

    /**
     * The parse cache, keyed on the raw source.
     *
     * ## Why a cache rather than `remember` at each call site
     *
     * The same bio is parsed by the profile card, the detail sheet and the
     * comment thread, and each of those re-enters composition as its LazyColumn
     * recycles rows. `remember(source)` is lost when the row leaves the viewport,
     * so scrolling back up re-tokenized the whole document - and replayed the
     * `animateContentSize` wiggle the first parse triggered. A process-wide cache
     * keyed on the source makes the second parse free wherever it happens.
     *
     * Bounded at [PARSE_CACHE_ENTRIES] and access-ordered: the bios a reader
     * actually revisits stay, and a scroll through a thousand one-line comments
     * cannot grow the cache without limit. Synchronised because Coil and Compose
     * can call in from different threads; the parser itself is pure.
     */
    private const val PARSE_CACHE_ENTRIES = 200
    private val parseCacheLock = Any()
    private val parseCache = LinkedHashMap<String, List<MarkdownNode>>(64, 0.75f, true)

    private fun cachedParse(raw: String): List<MarkdownNode>? =
        synchronized(parseCacheLock) { parseCache[raw] }

    private fun cacheParse(raw: String, nodes: List<MarkdownNode>) {
        synchronized(parseCacheLock) {
            parseCache[raw] = nodes
            while (parseCache.size > PARSE_CACHE_ENTRIES) {
                val oldest = parseCache.entries.iterator()
                if (oldest.hasNext()) {
                    oldest.next()
                    oldest.remove()
                } else {
                    break
                }
            }
        }
    }

    private val HEADER_PATTERN = Regex("""^(#{1,6})\s+(.+)$""")
    private val CHECKLIST_PATTERN = Regex("""^[-*+]\s+\[([ xX])]\s*(.+)$""")
    private val UNORDERED_LIST_PATTERN = Regex("""^[-*+]\s+(.+)$""")
    private val ORDERED_LIST_PATTERN = Regex("""^\d+\.\s+(.+)$""")
    private val HORIZONTAL_RULE_PATTERN = Regex("""^(\*{3,}|-{3,}|_{3,})$""")
    /**
     * The four whole-line image forms, and the rules they now share.
     *
     * ## The scheme is optional
     *
     * AniList serves artwork from protocol-relative addresses
     * (`//s4.anilist.co/...`) in some documents, and every one of these patterns
     * required `https?://`, so those images matched nothing: the line fell
     * through to the paragraph path and printed as a link or as raw text.
     * `(?:https?:)?//` matches both spellings, and [absoluteImageUrl] puts the
     * scheme back on whatever was captured, because Coil cannot resolve `//`
     * against anything and would request a host of its own.
     *
     * ## The title is optional, and dropped
     *
     * `![alt](https://x/y.png "a title")` is valid Markdown. The ` "a title"`
     * suffix used to stop every markdown-image pattern from matching at all -
     * the image silently degraded to a link or to the literal markup - so the
     * title is now consumed and discarded: nothing in this renderer can show a
     * title, and splitting it off the URL is the whole point.
     *
     * ## The size is captured where AniList has one
     *
     * `img220(url)` / `img50%(url)` and their linked spelling carry a size that
     * was matched and then dropped, so `ImageBlock.width` was always null and
     * `RenderImage` had nothing to honour. It is the only thing this syntax adds
     * over `img(url)`, so it is now captured - see `imageWidthModifier`.
     */
    private val LINKED_MD_IMG_PATTERN = Regex(
        """^\[!\[([^\]]*)]\(((?:https?:)?//[^\s)]+)(?:\s+["'][^"']*["'])?\s*\)]\((https?://[^\s)]+)\)$"""
    )
    private val LINKED_ANILIST_IMG_PATTERN = Regex(
        """^\[img(\d+%|\d+)?\(((?:https?:)?//[^)]+)\)]\((https?://[^\s)]+)\)$""",
        RegexOption.IGNORE_CASE
    )
    private val ANILIST_IMG_PATTERN = Regex(
        """^img(\d+%|\d+)?\(((?:https?:)?//[^)]+)\)$|^image\(((?:https?:)?//[^)]+)\)$""",
        RegexOption.IGNORE_CASE
    )
    private val MD_IMG_PATTERN = Regex("""^!\[([^\]]*)]\(((?:https?:)?//[^\s)]+)(?:\s+["'][^"']*["'])?\s*\)$""")
    /**
     * A bare image/GIF URL alone on its line.
     *
     * AniList renders one of these as an embedded image, but this parser used to
     * fall through to the paragraph/auto-URL path and produce a *link*, so a GIF
     * posted on its own line was a blue underline rather than the GIF. The
     * extension list is the whole test - there is no content-type to consult
     * before the request is made - and it is matched case-insensitively because
     * hosts are not consistent about the casing. The scheme is optional, for the
     * protocol-relative addresses described above.
     */
    private val BARE_IMAGE_URL_PATTERN = Regex(
        """^(?:https?:)?//\S+\.(?:gif|png|jpe?g|webp|bmp|avif)(?:\?\S*)?$""",
        RegexOption.IGNORE_CASE
    )

    /** A line that is nothing but a URL, which AniList may play or embed. */
    private val BARE_URL_PATTERN = Regex("""^https?://\S+$""")
    private val YOUTUBE_PATTERN = Regex("""^youtube\(([^)]+)\)$""", RegexOption.IGNORE_CASE)
    private val WEBM_PATTERN = Regex("""^webm\(([^)]+)\)$""", RegexOption.IGNORE_CASE)

    /**
     * A parenthesised URL on a line of its own, which is AniList's embed form.
     *
     * This is the shape the community's AniList-Flavored Markdown guide documents
     * for video: the URL in round brackets, converted in place, with the video id
     * on its own being enough for YouTube. The parser only understood the
     * `youtube(id)` / `webm(url)` spellings, which AniList does not render - so a
     * bio written the way the site itself documents came through as a string of
     * parentheses and a bare link.
     */
    private val PARENTHESISED_URL_PATTERN = Regex("""^\((https?://[^\s)]+)\)$""", RegexOption.IGNORE_CASE)

    /**
     * A video file AniList would play in place, by extension.
     *
     * The extension is the whole test, exactly as with [BARE_IMAGE_URL_PATTERN]:
     * there is no content type to consult before the request is made.
     */
    private val VIDEO_EXTENSION = Regex(
        """\.(?:webm|mp4|m4v|mov|gifv)(?:\?\S*)?$""",
        RegexOption.IGNORE_CASE
    )

    /**
     * A line of `=` under a line of text, which AniList renders as a top-level
     * heading.
     *
     * `---` is deliberately *not* read the same way even though Markdown allows it:
     * it is already the horizontal rule above, and the community guide warns that
     * a rule and a setext heading are written identically. Between the two, the
     * rule is the one people mean - a bio is far more likely to divide itself than
     * to set a heading that way.
     */
    private val SETEXT_H1_PATTERN = Regex("""^=+\s*$""")

    /**
     * `~~~text~~~` on one line - the other way AniList centers.
     *
     * Distinct from the three-tilde *block* below, which opens on a line that is
     * nothing but `~~~` and closes on the next one. A single line that wraps its
     * own closing tildes is a complete centred line, and it used to reach the
     * paragraph path and print its tildes.
     */
    private val TILDE_CENTER_PATTERN = Regex("""^~~~(?!~)(.+?)~~~$""")
    private val ANILIST_SPOILER_BLOCK = Regex("""^~!\s*([\s\S]*?)\s*!~$""")
    /**
     * An AniList entity address, which a bio link may carry in its full form.
     *
     * ## Why the query string and fragment are allowed
     *
     * The pattern used to be anchored at `$` with nothing after the path, so
     * `anilist.co/anime/1?utm_source=discord` matched nothing. It then fell
     * through to the ordinary-link path and rendered as a blue underlined title
     * where every *other* entry in the same hall-of-fame bio drew a card, and
     * `mediaCoverKeysIn` never saw it, so its poster was never even requested.
     * Sharing a bio is the mechanism by which these links spread, and the share
     * text carries the query string.
     *
     * ## Why the slug is matched lazily
     *
     * Group three was `[^\s)]+`, which includes `/` - so it swallowed the trailing
     * slash and the card's title came out as `Cowboy Bebop/`. Lazy plus the
     * explicit `/?` leaves the slash to its own group, where the anchor can match
     * it, and the slug arrives clean. The `?` and `#` follow the same argument:
     * a slug that swallowed `?ref=share` produced a title of
     * `Cowboy Bebop?ref=share`, which is the same failure one character later.
     */
    private val ANILIST_PREVIEW_PATTERN = Regex(
        """^https?://(?:www\.)?anilist\.co/(anime|manga|character|staff|studio|user)/""" +
            """(\d+|[a-zA-Z0-9_-]+)(?:/([^\s?#)]+?))?/?(?:\?[^\s]*)?(?:#\S*)?$""",
        RegexOption.IGNORE_CASE
    )

    // Inline regex patterns
    /**
     * An image that is itself a link: `[![Alt](coverUrl)](targetUrl)`.
     *
     * ## Why this has to win over [INLINE_LINK]
     *
     * Both patterns match this text at the same offset, and the winner is whichever
     * the match list puts first. `INLINE_LINK` alone would take the *image* URL as
     * its target and leave `![cover` as the label - so a reader tapping the cover
     * was taken to the artwork instead of the title, and the title the author linked
     * to was dropped entirely. Both patterns still exist because the bracket nesting
     * is a different shape, and because the outer URL is what the reader follows.
     *
     * Listed before `linkMatch` in `parseInlines` so the sort, which is stable, keeps
     * it ahead on a tied offset.
     */
    private val INLINE_LINKED_IMG = Regex(
        """\[!\[([^\]]*)]\(((?:https?:)?//[^\s)]+)(?:\s+["'][^"']*["'])?\s*\)]\((https?://(?:[^\s()]|\([^\s()]*\))+)\)"""
    )
    /**
     * `![alt](url)` in the middle of a paragraph.
     *
     * Checked ahead of [INLINE_LINK] because that pattern matches the `[alt](url)`
     * half of it and leaves the leading `!` as a stray `Plain("!")` token, which
     * is exactly what the bio used to print for an inline image.
     *
     * The scheme may be missing (protocol-relative artwork) and a ` "title"`
     * suffix may follow the URL; both are consumed, and the title is discarded
     * because nothing here can show one. The title has to come *after* a URL
     * that cannot contain a space, or the URL group would swallow it whole and
     * the image would be requested under a name that includes its own title.
     */
    private val INLINE_MD_IMG = Regex(
        """!\[([^\]]*)]\(((?:https?:)?//[^)\s]+)(?:\s+["'][^"']*["'])?\s*\)"""
    )
    /**
     * A raw `<img ...>` tag anywhere in a paragraph.
     *
     * AniList descriptions can carry these. Nothing recognised one, so the
     * literal markup printed in the bio. The tag stops at its first `>`, which
     * keeps it from swallowing the text around it: a tag that matches is exactly
     * one tag, whether it is alone on its line or between two words. Attribute
     * values may be single- or double-quoted, and a trailing `/` before the `>`
     * of a self-closing tag is simply part of the match.
     */
    private val HTML_IMG_TAG_PATTERN = Regex("""<img\b[^>]*>""", RegexOption.IGNORE_CASE)

    /**
     * The `src` of an [HTML_IMG_TAG_PATTERN] match.
     *
     * `(?<![\w-])` is load-bearing: a bare `\b` before `src` also matches the
     * `src` inside `data-src`, and a lazily-loaded bio would then request the
     * placeholder instead of the artwork. The three alternatives are the three
     * ways HTML writes an attribute value; `groupValues` reads the first that
     * matched, and an unmatched group is the empty string.
     */
    private val HTML_IMG_SRC_PATTERN = Regex(
        """(?<![\w-])src\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""",
        RegexOption.IGNORE_CASE
    )

    /** The `alt` of an [HTML_IMG_TAG_PATTERN] match, under the same rules as `src`. */
    private val HTML_IMG_ALT_PATTERN = Regex(
        """(?<![\w-])alt\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s"'>]+))""",
        RegexOption.IGNORE_CASE
    )

    /**
     * A protocol-relative image URL between two words.
     *
     * [INLINE_AUTO_URL] requires a scheme, so the `https://` spelling of an
     * address embedded while the `//` spelling of the same address stayed
     * literal text. Widening the auto-URL rule would have been the wrong fix:
     * it would turn any `//word` in prose into a link. This pattern is confined
     * to images by its extension list, and the URL is matched lazily so that a
     * sentence's punctuation after the extension is left outside the match -
     * the same failure [INLINE_AUTO_URL] documents one step removed.
     *
     * The lookbehind keeps it from claiming the `//` of an `https://` URL that
     * `INLINE_AUTO_URL` is already going to match, or the second slash of a path
     * that happens to contain a doubled one.
     */
    private val INLINE_PROTOCOL_RELATIVE_IMAGE = Regex(
        """(?<![\w:])//\S+?\.(?:gif|png|jpe?g|webp|bmp|avif)(?:\?\S*)?""",
        RegexOption.IGNORE_CASE
    )
    /**
     * A labelled link, `[label](url)`.
     *
     * ## Why the URL is allowed one level of balanced parentheses
     *
     * It used to stop at `[^\s)]+`, which cut a URL at its first `)`. That is
     * wrong for the link shape AniList bios reach for most, because Wikipedia
     * titles carry parentheses: `https://en.wikipedia.org/wiki/Spike_Spiegel_(Cowboy_Bebop)`.
     * The reader got `.../Spike_Spiegel_(Cowboy_Bebop` - a 404 - and the closing
     * paren survived as stray plain text after the link.
     *
     * `(?:[^\s()]|\([^\s()]*\))+` accepts any run of non-space non-paren characters
     * with balanced single-level paren groups inside. One level is what real URLs
     * need; deeper nesting is not a shape AniList writes, and allowing it would make
     * the pattern able to swallow a following `)` that closes the *link* rather than
     * the URL.
     *
     * A trailing `)` that is not balanced is still excluded, so
     * `[Title](https://anilist.co/anime/1).` keeps the sentence's full stop as plain
     * text - the same rule the `AUTO_URL` branch documents.
     */
    private val INLINE_LINK = Regex("""\[([^\]]+)]\((https?://(?:[^\s()]|\([^\s()]*\))+)\)""")
    /**
     * A bare URL with no label.
     *
     * The same balanced-paren allowance as [INLINE_LINK], for the same reason: a
     * bare `https://en.wikipedia.org/wiki/Foo_(bar)` between two words used to link
     * to `https://en.wikipedia.org/wiki/Foo_` and leave `(bar)` as prose.
     */
    private val INLINE_AUTO_URL = Regex("""https?://(?:[^\s<>"'\[\]{}]|\([^\s<>"'\[\]{}]*\))+""")
    /** Whether an auto-linked URL points at an image, so it can be embedded. */
    private val IMAGE_EXTENSION = Regex("""\.(?:gif|png|jpe?g|webp|bmp|avif)(?:\?.*)?$""", RegexOption.IGNORE_CASE)
    private val INLINE_SPOILER = Regex("""~!([\s\S]*?)!~""")
    private val INLINE_CODE = Regex("""`([^`]+)`""")
    /**
     * Italic. `_` counts as a delimiter only at a word boundary.
     *
     * ## Why the pattern was split in two
     *
     * The single pattern used to accept `*` or `_` anywhere, so it treated the
     * underscore in `anilist_api_key` as an emphasis delimiter: the underscores
     * vanished and the middle run was italicised. Markdown requires a delimiter
     * run to be left- or right-flanking before it counts as one, which is what
     * the `(?<![\w])` / `(?![\w])` guards below express.
     *
     * The guards cannot simply be added to the old single pattern, because that
     * pattern's first alternative pairs a `*` with a `_` freely - `**bold**`
     * matches `*` + `*bold` + `*`. Adding a boundary test to the *opening* `_`
     * fixes `snake_case` and breaks `**bold**`, since the `*` that opens bold
     * satisfies no boundary requirement. So the two delimiters need different
     * rules and therefore different patterns; [parseInlines] tries both and takes
     * whichever starts earlier, which keeps `*x*` and `_x_` a single token each.
     *
     * `*` is left unguarded because AniList accepts `*italic*` mid-word and this
     * app has always done so.
     */
    private val INLINE_ITALIC = Regex(
        """\*(?=\S)(.+?)(?<=\S)\*|<i>(.+?)</i>|<em>(.+?)</em>""",
        RegexOption.IGNORE_CASE
    )

    /**
     * Bold and bold-italic, split by delimiter because the two behave differently.
     *
     * The single pattern used to be `(?:\*\*\*|___)|(?:\*\*|__)`, which matched
     * `___text_` - opening three underscores, closing one - and produced a
     * bold-italic whose trailing underscore was silently dropped from the bio.
     * Kept as two alternatives over their own delimiter pair so each spelling has
     * exactly one opening and one closing run.
     *
     * `__` bold is deliberately **not** guarded by a word boundary the way `_`
     * italic is: `snake__case` is vanishingly rare, while `**` is the delimiter
     * AniList users reach for, and guarding it would be a trade with no upside.
     */
    private val INLINE_BOLD = Regex(
        """\*\*(?=\S)(.+?)(?<=\S)\*\*|__(?=\S)(.+?)(?<=\S)__|<b>(.+?)</b>|<strong>(.+?)</strong>""",
        RegexOption.IGNORE_CASE
    )

    /**
     * `***text***` and `___text___`, one delimiter pair each.
     *
     * Was `(?:\*\*\*|___)(.+?)(?:\*\*\*|___)`, which also matched `___text***`.
     */
    private val INLINE_BOLD_ITALIC = Regex(
        """(?:\*\*\*|___)(?=\S)(.+?)(?<=\S)(?:\*\*\*|___)""",
        RegexOption.IGNORE_CASE
    )

    /** The `_` form of [INLINE_ITALIC], with the flanking rule applied. */
    private val INLINE_UNDERSCORE_ITALIC = Regex(
        """(?<![\w])_(?=\S)(.+?)(?<=\S)_(?![\w])""",
        RegexOption.IGNORE_CASE
    )
    private val INLINE_UNDERLINE = Regex("""<u>(.+?)</u>""", RegexOption.IGNORE_CASE)
    private val INLINE_STRIKETHROUGH = Regex("""~~(.+?)~~|<s>(.+?)</s>|<del>(.+?)</del>""", RegexOption.IGNORE_CASE)
    /**
     * A username mention, which must not be the tail of an email address.
     *
     * Without the lookbehind, `someone@example.com` in a bio became a mention of
     * "example" - three words of somebody's contact address rendered as a
     * coloured username, with `.com` left dangling after it.
     */
    private val INLINE_MENTION = Regex("""(?<![\w.+\-])@([a-zA-Z0-9_-]+)""")

    /**
     * [text] with every escape in [HTML_ENTITY_PATTERN] replaced by the character it names.
     *
     * ## Why the escapes are decoded here and nowhere else
     *
     * Once per document, on the whole string, before it is split into lines: the
     * block patterns and the inline tokeniser then see the characters the author
     * typed rather than their storage form. Decoding at the point of use would
     * have to be repeated in every branch that can carry text, and one branch
     * forgotten is one class of bio that still prints `&amp;`. Decoding later
     * would run once per line with no memory of having already run, which is the
     * double-decode [HTML_ENTITY_PATTERN] exists to prevent.
     *
     * The `&` guard is not a micro-optimisation: a bio that contains no `&` at
     * all skips the scan, and most bios contain none.
     */
    private fun decodeHtmlEntities(text: String): String {
        if (!text.contains('&')) return text
        return HTML_ENTITY_PATTERN.replace(text) { match ->
            val body = match.groupValues[1]
            if (!body.startsWith("#")) {
                NAMED_HTML_ENTITIES[body] ?: match.value
            } else {
                val hex = body.startsWith("#x", ignoreCase = true)
                val code = body.substring(if (hex) 2 else 1).toIntOrNull(if (hex) 16 else 10)
                // A zero, a surrogate, or anything past Unicode's ceiling is not
                // a character a bio can mean. The escape is left as the author
                // wrote it, which reads as markup rather than as an empty gap or
                // a lone surrogate the text run cannot hold.
                if (code == null || code < 1 || code > 0x10FFFF || code in 0xD800..0xDFFF) {
                    match.value
                } else {
                    String(Character.toChars(code))
                }
            }
        }
    }

    fun parse(raw: String): List<MarkdownNode> {
        cachedParse(raw)?.let { return it }

        // `<br>` becomes a break *before* the escapes are decoded, so an author's
        // escaped literal `&lt;br&gt;` stays the visible text `<br>` instead of
        // becoming a line break. Entities are then decoded once, here, on the
        // whole document - see [decodeHtmlEntities] for why here and why once.
        val clean = raw
            .replace("\r\n", "\n")
            .replace("\r", "\n")
            .replace("<br>", "\n")
            .replace("<br/>", "\n")
            .replace("<br />", "\n")
            .let(::decodeHtmlEntities)
            .trim()
        // A parse that fails must not take the screen with it. This runs on the
        // main thread during composition over text nobody in this app wrote -
        // a stranger's comment, a synopsis, a bio - so an input this parser did
        // not anticipate used to close the app on the screen that was drawing it.
        // The fallback is the text the reader came for, as one plain paragraph:
        // less pretty than the markup deserved, still readable, and never a
        // force-stop. `StackOverflowError` is caught because the block walk
        // recurses through nested `~~~center` blocks, and a pathological run of
        // them can exhaust the stack rather than throw.
        val nodes = try {
            parseDecoded(clean)
        } catch (failure: StackOverflowError) {
            return plainFallback(clean)
        } catch (failure: Exception) {
            return plainFallback(clean)
        }
        return nodes.also { cacheParse(raw, it) }
    }

    /**
     * What a failed parse renders as: the text, unwrapped.
     *
     * Deliberately not cached as the *success* path is - this is a rare fallback,
     * and a cache entry would keep a broken document's plain version for the rest
     * of the session even after the bug behind it was fixed.
     */
    private fun plainFallback(text: String): List<MarkdownNode> =
        if (text.isBlank()) emptyList() else listOf(MarkdownNode.Paragraph(listOf(InlineToken.Plain(text))))

    /**
     * The block loop, over text whose line endings and `<br>`s are already
     * normalised and whose escapes are already decoded.
     *
     * Split out of [parse] because a centered block re-parses the slice of lines
     * it collected, and that slice has been through [decodeHtmlEntities] once
     * already. Calling [parse] from there decoded it a second time, so
     * `&amp;lt;` - which the reader should see as `&lt;` - came out as `<` if it
     * happened to sit inside `~~~center`.
     *
     * [depth] counts nested centered blocks, the only re-entry point, and is
     * capped at [MAX_BLOCK_DEPTH].
     */
    private fun parseDecoded(clean: String, depth: Int = 0): List<MarkdownNode> {
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

            val trimmedLine = line.trim()

            // A single centred line: `~~~text~~~`.
            val tildeCentered = TILDE_CENTER_PATTERN.matchEntire(trimmedLine)
            if (tildeCentered != null) {
                // Through the shared paragraph builder, so an AniList link inside
                // `~~~...~~~` becomes a card exactly as it does outside it. This
                // branch used to build the `Paragraph` itself and was therefore the
                // one paragraph path that never lifted media links: identical text
                // drew a card un-centred and a plain link centred.
                nodes.addAll(paragraphNodes(tildeCentered.groupValues[1].trim(), centered = true))
                i++
                continue
            }

            // Centered block (~~~center, <center>, <div align="center">, etc.)
            if (trimmedLine.equals("~~~center", ignoreCase = true) ||
                trimmedLine.equals("<center>", ignoreCase = true) ||
                trimmedLine.equals("<div align=\"center\">", ignoreCase = true) ||
                trimmedLine.equals("<div style=\"text-align: center;\">", ignoreCase = true) ||
                (trimmedLine == "~~~")
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
                // `parseDecoded`, not `parse`: these lines came out of the
                // already-decoded document, and decoding them again would make
                // an escaped `&lt;` inside a centred block print as `<`.
                //
                // Bounded because this is the one block that re-enters the walk:
                // a document of nested `~~~center` pairs would otherwise recurse
                // once per pair. Past [MAX_BLOCK_DEPTH] the remainder is kept as
                // plain text, which is what the reader can still read.
                val centerText = centerLines.joinToString("\n")
                val centerContent = if (depth >= MAX_BLOCK_DEPTH) {
                    listOf(MarkdownNode.Paragraph(listOf(InlineToken.Plain(centerText))))
                } else {
                    parseDecoded(centerText, depth + 1)
                }
                // Images inside the block are marked centred, because nothing else
                // can: the renderer's Box fills the width and aligns its child to
                // the start, so the column's own centring never reached the image.
                // `ImageBlock.centered` existed for this and was never set anywhere,
                // which left a `~~~center` embed - the standard layout for a
                // review's hall-of-fame strip - flush left inside a centred block.
                nodes.add(
                    MarkdownNode.CenteredBlock(
                        centerContent.map { child ->
                            if (child is MarkdownNode.ImageBlock) {
                                child.copy(centered = true)
                            } else {
                                child
                            }
                        }
                    )
                )
                continue
            }

            // AniList entity preview card check
            val anilistPreviewMatch = ANILIST_PREVIEW_PATTERN.matchEntire(trimmedLine)
            if (anilistPreviewMatch != null) {
                val type = anilistPreviewMatch.groupValues[1].uppercase()
                val id = anilistPreviewMatch.groupValues[2]
                nodes.add(
                    MarkdownNode.AniListPreviewBlock(
                        url = trimmedLine,
                        type = type,
                        id = id,
                        title = anilistTitleFromSlug(anilistPreviewMatch.groupValues[3], type, id)
                    )
                )
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
                nodes.add(MarkdownNode.ImageBlock(url = absoluteImageUrl(imgUrl), alt = alt, targetUrl = targetUrl))
                i++
                continue
            }

            val linkedAnilistImgMatch = LINKED_ANILIST_IMG_PATTERN.matchEntire(trimmedLine)
            if (linkedAnilistImgMatch != null) {
                // Group 1 is the size (`220` or `50%`), which used to be matched
                // and dropped; 2 is the image and 3 the target the cover opens.
                val imgUrl = linkedAnilistImgMatch.groupValues[2]
                val targetUrl = linkedAnilistImgMatch.groupValues[3]
                nodes.add(
                    MarkdownNode.ImageBlock(
                        url = absoluteImageUrl(imgUrl),
                        targetUrl = targetUrl,
                        width = linkedAnilistImgMatch.groupValues[1].takeIf { it.isNotEmpty() }
                    )
                )
                i++
                continue
            }

            // AniList / Markdown standalone images
            val anilistImgMatch = ANILIST_IMG_PATTERN.matchEntire(trimmedLine)
            if (anilistImgMatch != null) {
                val url = anilistImgMatch.groupValues[2].ifEmpty { anilistImgMatch.groupValues[3] }
                nodes.add(
                    MarkdownNode.ImageBlock(
                        url = absoluteImageUrl(url),
                        alt = null,
                        width = anilistImgMatch.groupValues[1].takeIf { it.isNotEmpty() }
                    )
                )
                i++
                continue
            }

            val mdImgMatch = MD_IMG_PATTERN.matchEntire(trimmedLine)
            if (mdImgMatch != null) {
                val alt = mdImgMatch.groupValues[1].takeIf { it.isNotBlank() }
                nodes.add(MarkdownNode.ImageBlock(url = absoluteImageUrl(mdImgMatch.groupValues[2]), alt = alt))
                i++
                continue
            }

            // A raw `<img src="...">` tag on a line of its own, which AniList
            // descriptions can carry and which used to print as literal markup.
            // The line has to be *nothing but* the tag - `matchEntire` - so a tag
            // inside a sentence is left to the inline path below rather than
            // having the sentence pulled into the image. A tag with no usable
            // `src` falls through to the paragraph, where the inline handler
            // keeps it as the text the author wrote.
            val htmlImgTag = HTML_IMG_TAG_PATTERN.matchEntire(trimmedLine)
            if (htmlImgTag != null) {
                val htmlImage = htmlTagImage(htmlImgTag.value)
                if (htmlImage != null) {
                    nodes.add(MarkdownNode.ImageBlock(url = htmlImage.first, alt = htmlImage.second))
                    i++
                    continue
                }
            }

            // A bare image/GIF URL on its own line, which AniList shows as the
            // image itself. Without this it fell through to the paragraph path and
            // rendered as a link.
            if (BARE_IMAGE_URL_PATTERN.matches(trimmedLine)) {
                nodes.add(MarkdownNode.ImageBlock(url = absoluteImageUrl(trimmedLine)))
                i++
                continue
            }

            // A bare video URL on its own line, which AniList plays in place.
            // It used to become an ordinary link, so a clip posted the way the
            // site accepts it arrived as a blue address instead of the video.
            val bareVideo = if (BARE_URL_PATTERN.matches(trimmedLine)) videoEmbedFor(trimmedLine) else null
            if (bareVideo != null) {
                nodes.add(bareVideo)
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

            // A parenthesised URL, which is AniList's own embed syntax. Only a
            // video or something that is unmistakably an image is lifted out; a
            // parenthesised *page* link falls through to the paragraph below so it
            // keeps its link and its parentheses rather than silently becoming a
            // card for something the author never offered as one.
            val parenthesised = PARENTHESISED_URL_PATTERN.matchEntire(trimmedLine)
            if (parenthesised != null) {
                val url = parenthesised.groupValues[1]
                val embed = videoEmbedFor(url)
                if (embed != null) {
                    nodes.add(embed)
                    i++
                    continue
                }
                if (IMAGE_EXTENSION.containsMatchIn(url)) {
                    nodes.add(MarkdownNode.ImageBlock(url = url))
                    i++
                    continue
                }
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
                // `!CHECKLIST_PATTERN.matches(...)` is load-bearing, not tidying.
                // `UNORDERED_LIST_PATTERN` also matches `- [x] done`, so a plain
                // bullet followed by a checkbox pulled the checkbox into this
                // list and printed the literal text `[x] done` as the bullet's own
                // content - the checkbox never rendered at all. A list of mixed
                // bullets and boxes is ordinary in a "currently watching" bio.
                while (i < lines.size &&
                    UNORDERED_LIST_PATTERN.matches(lines[i].trim()) &&
                    !CHECKLIST_PATTERN.matches(lines[i].trim())
                ) {
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

            // A heading written as a line of `=` under the title.
            val underline = lines.getOrNull(i + 1)?.trim()
            if (underline != null &&
                SETEXT_H1_PATTERN.matches(underline) &&
                !SETEXT_H1_PATTERN.matches(trimmedLine)
            ) {
                nodes.add(MarkdownNode.Header(1, parseInlines(trimmedLine)))
                i += 2
                continue
            }

            // Markdown Table check
            val tableResult = tryParseTable(lines, i) { parseInlines(it) }
            if (tableResult != null) {
                nodes.add(tableResult.first)
                i = tableResult.second
                continue
            }

            // Every handler above this point is reached only when it matches the
            // line at `i`, so each is guaranteed to advance. `tryParseTable` is
            // the exception: it declines a `|` line that has no `|---|` separator
            // under it, that has a separator carrying anything but `| : -`, or
            // that is the last line of the bio. The paragraph loop below refuses
            // to consume a `|` line (it exists so a paragraph stops and lets the
            // table handler have its turn), so on exactly that input the loop body
            // never runs, nothing is appended and `i` does not move.
            //
            // That is not a slow parse. `parse` is called from `AboutCard` inside
            // `remember(about)` on the **Compose main thread**, and again from the
            // ViewModel's dispatcher, so it spins the UI thread forever on a bio
            // containing a single stray pipe - an ANR, reachable by writing
            // `| Completed | 100 |` on two lines with no separator between them.
            //
            // So a `|` line that no handler claimed is emitted as its own
            // paragraph rather than dropped. Dropping it would lose the author's
            // text; keeping it as a paragraph means the pipe characters still
            // reach the reader as the literal line they wrote.
            val orphanPipeLine = trimmedLine.startsWith("|")
            if (orphanPipeLine) {
                nodes.add(MarkdownNode.Paragraph(parseInlines(trimmedLine)))
                i++
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
                // A bare `~~~`, because it opens a centered block and the block
                // handler above now takes it even as the bio's final line. It was
                // absent here, so a `~~~` in the middle of a paragraph was absorbed
                // as the literal text "~~~" - the very delimiter the same parser
                // honours when it happens to start a line.
                !lines[i].trim().equals("~~~") &&
                !ANILIST_IMG_PATTERN.matches(lines[i].trim()) &&
                !MD_IMG_PATTERN.matches(lines[i].trim()) &&
                // A line that is nothing but an `<img>` tag stops the paragraph
                // for the same reason `![...](...)` does: it is an image block
                // of its own, and absorbing it would draw it inline at the end
                // of the sentence above instead.
                !HTML_IMG_TAG_PATTERN.matches(lines[i].trim()) &&
                !BARE_IMAGE_URL_PATTERN.matches(lines[i].trim()) &&
                !(BARE_URL_PATTERN.matches(lines[i].trim()) && videoEmbedFor(lines[i].trim()) != null) &&
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
                nodes.addAll(paragraphNodes(paragraphLines.joinToString(" ")))
            } else {
                // Belt and braces on the ANR above. Every path into this branch is
                // supposed to have advanced `i` already, and the `|` case is now
                // handled explicitly; if a future handler is added that forgets to,
                // the parser degrades to one plain paragraph per stuck line rather
                // than hanging the main thread. An empty paragraph is never added,
                // so the visible cost of a bug here is a duplicate line, not a
                // freeze.
                nodes.add(MarkdownNode.Paragraph(parseInlines(lines[i].trim())))
                i++
            }
        }

        return nodes
    }

    /**
     * One paragraph's worth of nodes: the text, then a card per AniList link it
     * contained.
     *
     * Shared by every path that produces a paragraph, which is what keeps the
     * card treatment uniform. The block-level [ANILIST_PREVIEW_PATTERN] check
     * cannot see the shape bios actually use: it matches a line that is nothing
     * but the URL, while the standard "hall of fame" entry is a labelled link
     * inside a bold run (`**✨ [#1] [Title](https://anilist.co/anime/123)**`).
     * That rendered as plain text, which is the reported bug - the card existed
     * only for a bio nobody writes.
     *
     * The text paragraph comes first and the cards follow, in document order,
     * which is the reading order the reference clients use.
     */
    private fun paragraphNodes(text: String, centered: Boolean = false): List<MarkdownNode> {
        if (text.isBlank()) return emptyList()
        val (kept, mediaLinks) = splitMediaLinks(parseInlines(text))
        val nodes = mutableListOf<MarkdownNode>()
        if (kept.isNotEmpty()) {
            nodes += MarkdownNode.Paragraph(inlines = kept, centered = centered)
        }
        mediaLinks.forEach { link ->
            nodes += MarkdownNode.AniListPreviewBlock(
                url = link.url,
                type = link.type,
                id = link.id,
                title = link.label
            )
        }
        return nodes
    }

    /**
     * A readable title for an AniList URL, taken from its slug when it has one.
     *
     * `anilist.co/anime/128893/Hells-Paradise` carries the name;
     * `anilist.co/anime/128893` does not, so the fallback names the type and id
     * rather than leaving the card heading blank.
     *
     * ## Why the fallback is a label and not `ANIME #1`
     *
     * Because the fetched cover's real title replaces it on every card that
     * resolved - see `RenderAniListPreview` - so this text is only ever *read*
     * for a media whose request failed or came back with no cover: a rate limit,
     * a deleted entry, or a card that is still loading. And `#1` reads as a rank
     * to somebody scanning a numbered hall-of-fame list, which is the single most
     * common shape a bio has. `Anime #1` is a name; the type is already on the
     * card's badge, so shouting it in the title was never useful.
     */
    private fun anilistTitleFromSlug(slug: String, type: String, id: String): String {
        val prettyType = type.lowercase().replaceFirstChar { it.titlecase(Locale.ROOT) }
        val raw = slug.ifEmpty { "$prettyType #$id" }
        return raw.replace('-', ' ').replace('_', ' ')
            .split(' ')
            .joinToString(" ") { word ->
                word.replaceFirstChar {
                    if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString()
                }
            }
    }

    /**
     * The video a bare URL names, or null when it names anything else.
     *
     * A YouTube link is recognised by host because it has no useful extension -
     * `watch?v=` tells you nothing - and everything else by file extension, which
     * is the only signal available before the request is made. Shared by the
     * parenthesised embed above and the bare-URL path, because AniList plays both
     * and two copies of this test would eventually disagree about one of them.
     */
    private fun videoEmbedFor(url: String): MarkdownNode.VideoBlock? {
        val lower = url.lowercase()
        val isYoutube = lower.contains("youtube.com/") || lower.contains("youtu.be/")
        return when {
            isYoutube -> MarkdownNode.VideoBlock(url, isYoutube = true)
            VIDEO_EXTENSION.containsMatchIn(lower) -> MarkdownNode.VideoBlock(url, isYoutube = false)
            else -> null
        }
    }

    /**
     * [url] with a scheme, because a captured image URL may be protocol-relative.
     *
     * AniList serves artwork from `//s4.anilist.co/...`, and Coil resolves a
     * scheme-less address against nothing: the request went out with no host and
     * every one of those images stayed a blank placeholder. `https:` is the
     * scheme the site itself serves them over, so that is the one put back.
     * Applied to every captured image URL, in one place, so a future image path
     * cannot forget it and quietly request nothing.
     */
    private fun absoluteImageUrl(url: String): String =
        if (url.startsWith("//")) "https:$url" else url

    /**
     * The image an `<img ...>` tag names, as `src` to `alt`, or null when it
     * names no `src`.
     *
     * A tag without a usable `src` is not an image at all; the callers keep it
     * as text rather than dropping the author's line. [alt] is null when the tag
     * has none or when it is blank, because a blank `contentDescription` is not
     * the same thing as none at all to a screen reader.
     */
    private fun htmlTagImage(tag: String): Pair<String, String?>? {
        val src = HTML_IMG_SRC_PATTERN.find(tag)?.htmlAttributeValue()?.takeIf { it.isNotBlank() }
            ?: return null
        val alt = HTML_IMG_ALT_PATTERN.find(tag)?.htmlAttributeValue()?.takeIf { it.isNotBlank() }
        return absoluteImageUrl(src) to alt
    }

    /** The first of an attribute's quoted or unquoted spellings that matched. */
    private fun MatchResult.htmlAttributeValue(): String =
        groupValues[1].ifEmpty { groupValues[2].ifEmpty { groupValues[3] } }

    /**
     * An AniList entity link written *inside* a line, as a [InlineToken.MediaLink].
     *
     * Null when [url] is not an AniList entity address, so an ordinary link like
     * `[docs](https://example.com)` keeps its plain [InlineToken.Link] treatment and
     * is not turned into a card. The label wins over the URL's slug because a
     * labelled link is the title the author chose, and it is the shape every
     * "hall of fame" bio uses.
     *
     * The pattern is matched against the URL with any trailing sentence
     * punctuation removed first. A bio that ends a sentence with a link wrote
     * `[Title](https://anilist.co/anime/1).`, and `matchEntire` against that
     * failed - so the link fell back to a plain underlined title and no card was
     * drawn for it. The stop is the sentence's, not the URL's, and is restored as
     * plain text by the caller.
     */
    private fun anilistMediaLink(label: String, url: String): InlineToken.MediaLink? {
        // `INLINE_LINK` stops its URL at whitespace, `)` or a space, but *not* at
        // a full stop - so `[Title](https://anilist.co/anime/1).` arrives here with
        // the sentence's stop still attached and matches nothing. The stop belongs
        // to the sentence; the caller puts it back as plain text.
        val bareUrl = url.trimEnd('.', ',', ';', ':', '!', '?')
        val match = ANILIST_PREVIEW_PATTERN.matchEntire(bareUrl) ?: return null
        val type = match.groupValues[1].uppercase()
        val id = match.groupValues[2]
        val title = mediaLinkTitle(label, match.groupValues[3], type, id)
        return InlineToken.MediaLink(label = title, url = bareUrl, type = type, id = id)
    }

    /**
     * The card heading for a labelled AniList link.
     *
     * ## Why the label is not used raw
     *
     * [label] is the link's *source* text, never parsed. A bio writes
     * `[**#1 Cowboy Bebop**](https://anilist.co/anime/1)` whenever it wants the
     * entry emphasised, and the asterisks went straight into the card heading.
     * The emphasis inside a plain link's label is already parsed into `children`
     * (see the `Link` branch of `parseInlines`); this is that same flattening,
     * done for the one branch whose text ends up as a *heading* rather than as
     * styled prose.
     *
     * Only the run text survives. A link's own label is a title, not prose, so
     * nothing is lost by flattening it - and the alternative was a card that
     * read `**#1 Cowboy Bebop**` with visible markup in it.
     */
    private fun mediaLinkTitle(label: String, slug: String, type: String, id: String): String {
        val plain = parseInlines(label).flatText().trim()
        return plain.ifEmpty { anilistTitleFromSlug(slug, type, id) }
    }

    /**
     * This token's text with all emphasis flattened out.
     *
     * The nested expression is preferred over [InlineToken]'s flat `text` where
     * one exists, because the flat field still holds the markup source for a run
     * whose label was itself a link.
     *
     * ## Why every emphasis run has its own branch
     *
     * They all carry the same two fields, so the obvious grouping -
     * `is Bold, is Italic, ... ->` on one line - is what this was written as first,
     * and it does not compile. A multi-condition branch smart-casts only to the
     * common supertype of the listed types, which is `InlineToken`, so `children`
     * and `text` do not resolve on it. Five near-identical lines is the price of
     * the cast, and `nestedTokens()` and `childrenOrText()` in this file pay the
     * same one for the same reason.
     */
    private fun InlineToken.runText(): String = when (this) {
        is InlineToken.Plain -> text
        is InlineToken.Bold -> children.flatText().ifEmpty { text }
        is InlineToken.Italic -> children.flatText().ifEmpty { text }
        is InlineToken.BoldItalic -> children.flatText().ifEmpty { text }
        is InlineToken.Underline -> children.flatText().ifEmpty { text }
        is InlineToken.Strikethrough -> children.flatText().ifEmpty { text }
        is InlineToken.Link -> children.flatText().ifEmpty { label }
        is InlineToken.Code -> code
        is InlineToken.MediaLink -> label
        is InlineToken.Mention -> "@$username"
        is InlineToken.Spoiler -> text
        is InlineToken.Image -> alt.orEmpty()
    }

    /** The flattened text of a nested expression. */
    private fun List<InlineToken>.flatText(): String = joinToString("") { it.runText() }

    /**
     * Parses inline tokens: links, inline images/GIFs, spoilers, bold/italic,
     * underline, code, strikethrough, mentions, auto URLs, and plain text.
     */
    fun parseInlines(text: String): List<InlineToken> = parseInlines(text, depth = 0)

    /**
     * How deep nested emphasis and link-label parsing may descend before it stops.
     *
     * The cap is what keeps a malformed bio from recursing forever - a run whose
     * label parses back into the same run would otherwise spin. Real bios nest two
     * or three levels at most, so this is headroom rather than a limit anyone
     * reaches; past it the renderer falls back to the flat [InlineToken.Bold.text]
     * the token already carries.
     */
    private const val MAX_INLINE_DEPTH = 4

    /**
     * How deep nested centered blocks may nest before the rest is kept as text.
     *
     * `~~~center` is the only block that re-enters the block walk, and the walk is
     * recursive, so a document that opens the marker thousands of times is a
     * stack overflow rather than a render. Eight is far past anything a person
     * writes and still nowhere near the stack's limit.
     */
    private const val MAX_BLOCK_DEPTH = 8

    private fun parseInlines(text: String, depth: Int): List<InlineToken> {
        if (text.isEmpty()) return emptyList()

        val tokens = mutableListOf<InlineToken>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            val mdImgMatch = INLINE_MD_IMG.find(remaining)
            val htmlImgMatch = HTML_IMG_TAG_PATTERN.find(remaining)
            val protocolImageMatch = INLINE_PROTOCOL_RELATIVE_IMAGE.find(remaining)
            val linkedImgMatch = INLINE_LINKED_IMG.find(remaining)
            val linkMatch = INLINE_LINK.find(remaining)
            val spoilerMatch = INLINE_SPOILER.find(remaining)
            val codeMatch = INLINE_CODE.find(remaining)
            val boldItalicMatch = INLINE_BOLD_ITALIC.find(remaining)
            val boldMatch = INLINE_BOLD.find(remaining)
            val italicMatch = INLINE_ITALIC.find(remaining)
            // Tried as a separate pattern so the word-boundary rule applies to `_`
            // alone. Both `*italic*` and `_italic_` are matched here and the
            // earlier one wins by start offset, so `*x*` and `_x_` still produce a
            // single Italic token each.
            val underscoreItalicMatch = INLINE_UNDERSCORE_ITALIC.find(remaining)
            // The same reason, for the same reason as the italic split: the single
            // bold pattern paired `**` with `__` freely, so `___text_` matched with
            // an opening run of three and a closing run of one and the stray
            // underscore was dropped from the bio. Each pattern now spells out one
            // delimiter pair per alternative.
            //
            // `**bold**` is checked before italic and wins on the earlier start
            // offset, so bold is never stolen by the italic rule - the reason the
            // sort below exists at all.
            val underlineMatch = INLINE_UNDERLINE.find(remaining)
            val strikeMatch = INLINE_STRIKETHROUGH.find(remaining)
            val mentionMatch = INLINE_MENTION.find(remaining)
            val autoUrlMatch = INLINE_AUTO_URL.find(remaining)

            val matches = listOfNotNull(
                mdImgMatch?.let { it to "IMAGE" },
                htmlImgMatch?.let { it to "HTML_IMG" },
                protocolImageMatch?.let { it to "PROTOCOL_IMG" },
                linkedImgMatch?.let { it to "LINKED_IMG" },
                linkMatch?.let { it to "LINK" },
                spoilerMatch?.let { it to "SPOILER" },
                codeMatch?.let { it to "CODE" },
                boldItalicMatch?.let { it to "BOLD_ITALIC" },
                boldMatch?.let { it to "BOLD" },
                italicMatch?.let { it to "ITALIC" },
                underscoreItalicMatch?.let { it to "ITALIC" },
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
                "IMAGE" -> {
                    val alt = firstMatch.groupValues[1].takeIf { it.isNotBlank() }
                    tokens.add(InlineToken.Image(absoluteImageUrl(firstMatch.groupValues[2]), alt))
                }
                "HTML_IMG" -> {
                    val image = htmlTagImage(firstMatch.value)
                    if (image != null) {
                        tokens.add(InlineToken.Image(image.first, image.second))
                    } else {
                        // A tag with no `src` has nothing to draw, so it stays as
                        // the text the author wrote rather than vanishing.
                        tokens.add(InlineToken.Plain(firstMatch.value))
                    }
                }
                "PROTOCOL_IMG" -> {
                    tokens.add(InlineToken.Image(absoluteImageUrl(firstMatch.value)))
                }
                "LINKED_IMG" -> {
                    val alt = firstMatch.groupValues[1].takeIf { it.isNotBlank() }
                    val imgUrl = absoluteImageUrl(firstMatch.groupValues[2])
                    val targetUrl = firstMatch.groupValues[3]
                    // The cover is the label, the title is the target. `INLINE_LINK`
                    // alone would have taken the cover as the target and dropped the
                    // title, so this branch exists to keep both.
                    //
                    // A plain `Link` with the image nested, rather than a `MediaLink`:
                    // the outer URL is what the reader follows, and a `MediaLink` would
                    // replace the whole thing with a cover card and lose the artwork the
                    // author chose. If the target is an AniList entity the card is drawn
                    // for it by the view, which is the same thing the block-level
                    // `[![Alt](img)](target)` does.
                    tokens.add(
                        InlineToken.Link(
                            label = "",
                            url = targetUrl,
                            children = listOf(InlineToken.Image(imgUrl, alt))
                        )
                    )
                }
                "LINK" -> {
                    val label = firstMatch.groupValues[1]
                    val url = firstMatch.groupValues[2]
                    // An AniList entity link is kept as a media link so the renderer
                    // can draw a cover card for it; anything else stays a plain link.
                    val mediaLink = anilistMediaLink(label, url)
                    if (mediaLink != null) {
                        tokens.add(mediaLink)
                    } else {
                        // A label may itself be emphasised (`[**AniList**](url)`),
                        // which used to print the asterisks verbatim.
                        tokens.add(InlineToken.Link(label, url, nested(label, depth)))
                    }
                }
                "AUTO_URL" -> {
                    val matched = firstMatch.value
                    // A URL ending a sentence used to keep the sentence's full
                    // stop: `... anilist.co/anime/1.` linked to a path that does
                    // not exist, so the link the reader tapped was one character
                    // too long. The punctuation is put back as plain text behind
                    // it, because the sentence still needs its full stop.
                    val url = matched.trimEnd('.', ',', ';', ':', '!', '?')
                    val trailing = matched.substring(url.length)
                    val mediaLink = anilistMediaLink(label = "", url = url)
                    tokens.add(
                        when {
                            mediaLink != null -> mediaLink
                            // A bare URL that points at an image gets embedded rather
                            // than linked, so `https://.../cat.gif` between two words
                            // animates. This pattern requires a scheme, so the
                            // normalisation is a no-op here - it is applied anyway so
                            // that every `InlineToken.Image` in the file comes out of
                            // the same one place.
                            IMAGE_EXTENSION.containsMatchIn(url) -> InlineToken.Image(absoluteImageUrl(url))
                            else -> InlineToken.Link(url, url)
                        }
                    )
                    if (trailing.isNotEmpty()) tokens.add(InlineToken.Plain(trailing))
                }
                "SPOILER" -> {
                    tokens.add(InlineToken.Spoiler(firstMatch.groupValues[1]))
                }
                "CODE" -> {
                    tokens.add(InlineToken.Code(firstMatch.groupValues[1]))
                }
                "BOLD_ITALIC" -> {
                    val content = firstMatch.groupValues[1]
                    tokens.add(InlineToken.BoldItalic(content, nested(content, depth)))
                }
                "BOLD" -> {
                    // Groups 1 and 2 are `**` and `__`; 3 and 4 are `<b>` and
                    // `<strong>`. `groupValues` on an unmatched group is the empty
                    // string, which is why this reads as a chain of `ifEmpty`.
                    val content = firstMatch.groupValues[1].ifEmpty {
                        firstMatch.groupValues[2].ifEmpty {
                            firstMatch.groupValues[3].ifEmpty { firstMatch.groupValues[4] }
                        }
                    }
                    tokens.add(InlineToken.Bold(content, nested(content, depth)))
                }
                "ITALIC" -> {
                    // Group 1 for the `*x*` / `_x_` alternation, 2 and 3 for the
                    // `<i>` / `<em>` spellings. `groupValues` is safe on any of
                    // them: an unmatched group is the empty string, which is why
                    // `ifEmpty` chains read this way.
                    val content = firstMatch.groupValues[1].ifEmpty {
                        firstMatch.groupValues[2].ifEmpty { firstMatch.groupValues[3] }
                    }
                    tokens.add(InlineToken.Italic(content, nested(content, depth)))
                }
                "UNDERLINE" -> {
                    val content = firstMatch.groupValues[1]
                    tokens.add(InlineToken.Underline(content, nested(content, depth)))
                }
                "STRIKE" -> {
                    val content = firstMatch.groupValues[1].ifEmpty {
                        firstMatch.groupValues[2].ifEmpty { firstMatch.groupValues[3] }
                    }
                    tokens.add(InlineToken.Strikethrough(content, nested(content, depth)))
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

    /**
     * The inline expression nested inside an emphasis run or a link label.
     *
     * Empty once [MAX_INLINE_DEPTH] is reached or the text has nothing to parse,
     * which the renderer reads as "use the flat text this token already holds".
     */
    private fun nested(text: String, depth: Int): List<InlineToken> =
        if (depth >= MAX_INLINE_DEPTH || text.isBlank()) emptyList()
        else parseInlines(text, depth + 1)
}
