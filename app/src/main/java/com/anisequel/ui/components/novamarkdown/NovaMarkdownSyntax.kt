package com.anisequel.ui.components.novamarkdown

/**
 * Every pattern NovaMarkdown parses, kept in one place.
 *
 * The block parser, the inline scanner and the media readers all have to agree
 * about what a table separator, a spoiler class or an image extension is. One
 * declaration per shape is what stops two of them drifting apart.
 */

// ---------------------------------------------------------------------------
// Block patterns
// ---------------------------------------------------------------------------

internal val ATX_HEADING = Regex("""^ {0,3}(#{1,6})[ \t]+(.+?)[ \t]*#*[ \t]*$""")
internal val SETEXT_H1 = Regex("""^ {0,3}=+[ \t]*$""")
internal val SETEXT_H2 = Regex("""^ {0,3}-{2,}[ \t]*$""")
internal val HORIZONTAL_RULE =
    Regex("""^ {0,3}(?:(?:\*[ \t]*){3,}|(?:-[ \t]*){3,}|(?:_[ \t]*){3,})$""")
internal val FENCE = Regex("""^ {0,3}(`{3,})(.*)$""")
internal val BULLET_ITEM = Regex("""^(\s*)[-*+][ \t]+(.*)$""")
internal val ORDERED_ITEM = Regex("""^(\s*)(\d{1,9})[.)][ \t]+(.*)$""")
internal val TASK_ITEM = Regex("""^\[([ xX])\][ \t]+(.*)$""")
internal val SEPARATOR_CELL = Regex(""":?-{3,}:?""")

internal const val TILDE_CENTER = "~~~"

internal val SINGLE_TILDE_CENTER = Regex("""^~~~(?!~)(.*?)~~~$""")
internal val CENTER_OPEN = Regex("""^<center\b[^>]*>(.*)$""", RegexOption.IGNORE_CASE)
internal val CENTER_CLOSE = "</center>"

internal val ALIGN_OPEN = Regex("""^<(p|div)\b([^>]*)>(.*)$""", RegexOption.IGNORE_CASE)
internal val ALIGN_ATTR = Regex("""\balign\s*=\s*["']?(left|center|right|justify)""", RegexOption.IGNORE_CASE)
internal val STYLE_ALIGN = Regex("""\btext-align\s*:\s*(left|center|right|justify)""", RegexOption.IGNORE_CASE)

internal val SPOILER_OPEN = Regex("""^<(div|span)\b([^>]*)>""", RegexOption.IGNORE_CASE)
internal val CLASS_ATTR = Regex("""\bclass\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
internal val REL_ATTR = Regex("""\brel\s*=\s*("([^"]*)"|'([^']*)'|([^\s>]+))""", RegexOption.IGNORE_CASE)
internal val LABEL_ATTR = Regex("""\bdata-label\s*=\s*("([^"]*)"|'([^']*)')""", RegexOption.IGNORE_CASE)

internal val HTML_IMAGE_LINE = Regex("""^<img\b([^>]*)>\s*$""", RegexOption.IGNORE_CASE)
internal val HTML_HR = Regex("""^<hr\b[^>]*>\s*$""", RegexOption.IGNORE_CASE)
internal val HTML_PRE = Regex("""^<pre\b[^>]*>(.*)$""", RegexOption.IGNORE_CASE)
internal val HTML_QUOTE = Regex("""^<blockquote\b[^>]*>(.*)$""", RegexOption.IGNORE_CASE)
internal val HTML_LIST = Regex("""^<(ul|ol)\b([^>]*)>(.*)$""", RegexOption.IGNORE_CASE)
internal val HTML_LI = Regex("""(?is)<li\b[^>]*>(.*?)</li\s*>""")
internal val HTML_TAG = Regex("""<[^>]*>""")

internal val MD_IMAGE_LINE =
    Regex("""^!\[([^\]]*)]\(\s*((?:https?:)?//[^\s)]+)(?:\s+["'][^"']*["'])?\s*\)$""")
internal val ANILIST_IMAGE_LINE =
    Regex("""^img(\d*%?)\(\s*((?:https?:)?//[^\s)]+)\s*\)$""", RegexOption.IGNORE_CASE)
internal val LINKED_LINE = Regex("""^\[\s*(.+?)\s*]\(\s*((?:https?:)?//[^\s)]+)\s*\)$""")
internal val PARENTHESISED_URL = Regex("""^\(\s*((?:https?:)?//[^\s)]+)\s*\)$""")
internal val IMAGE_URL = Regex(
    """^(?:(?:https?:)?//)\S+\.(?:gif|png|jpe?g|webp|bmp|avif|svg)(?:[?#]\S*)?$""",
    RegexOption.IGNORE_CASE,
)
internal val IMAGE_EXTENSION = Regex("""\.(?:gif|png|jpe?g|webp|bmp|avif|svg)(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

internal val YOUTUBE_MARK = Regex("""^youtube\(\s*([^)]+?)\s*\)$""", RegexOption.IGNORE_CASE)
internal val WEBM_MARK = Regex("""^webm\(\s*([^)]+?)\s*\)$""", RegexOption.IGNORE_CASE)
internal val BARE_URL = Regex("""^(?:(?:https?:)?//)\S+$""")
internal val YOUTUBE_HOST = Regex("""(?:youtube\.com|youtu\.be)/""", RegexOption.IGNORE_CASE)
internal val VIDEO_EXTENSION =
    Regex("""\.(?:mp4|webm|ogv|mov|m4v|mp3|ogg|oga|wav)(?:[?#].*)?$""", RegexOption.IGNORE_CASE)

internal val ANILIST_ENTITY = Regex(
    """^(?:(?:https?:)?//)?(?:www\.)?anilist\.co/(anime|manga|character|staff|studio)/""" +
        """([A-Za-z0-9_-]+)(?:/([^/?#\s]+))?/?(?:[?#]\S*)?$""",
    RegexOption.IGNORE_CASE,
)
