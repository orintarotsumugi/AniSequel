package com.anisequel.domain.usecase

/**
 * What the app will attach to a comment, and what it becomes once uploaded.
 *
 * ## Why an upload needs policy in the domain at all
 *
 * AniList stores a comment as text, so an "attachment" is a file handed to a
 * third-party host and the URL that comes back written into the comment. Two
 * things can then go wrong in places the reader cannot see: the file is one the
 * host refuses (a 40 MB HEIC, an `.exe` renamed to `.jpg`), or the host takes it
 * and hands back something that is not a URL. Both are cheap to decide about up
 * front and expensive to discover after a spinner has run for ten seconds, so the
 * decisions are arithmetic on a mime type and a byte count - no Android, no
 * socket, and testable in a JVM run.
 *
 * ## The limits are the app's, not the hosts'
 *
 * The hosts' ceilings differ - catbox.moe takes up to 200 MB, pomf.lain.la up to
 * 1 GiB and imgbb.com 32 MB - but none of them is the wall here. This app hands
 * the bytes to the host from memory: the file is already a `ByteArray` and
 * multipart adds its own copy, so a limit anywhere near catbox's or pomf's is an
 * `OutOfMemoryError` on a phone, not an upload. 60 MB is the most that path has
 * been measured to survive. A file over imgbb's 32 MB ceiling still uploads as
 * long as one of the two hosts tried before it takes it; imgbb is the last
 * attempt, not a guarantee that every accepted file fits every host.
 *
 * The GIF ceiling is lower for a different reason: an animated GIF is decoded at
 * full size wherever it is shown, so a 30 MB one is also the thing that makes a
 * comment thread stutter on a mid-range device. The host itself accepts it
 * ("GIF format sucks - use webm if you need an animation bigger than 20 MB"), and
 * the app has no converter, so the refusal is a sentence about the file.
 */
object MediaAttachment {

    /** The app's own ceiling for any one file, sized for its memory, not a host's. */
    const val MAX_FILE_BYTES: Long = 60L * 1024 * 1024

    /**
     * The ceiling for an animated GIF, which is lower than the general one.
     *
     * A GIF this size still renders, but it decodes at its intrinsic size in every
     * comment thread that shows it; the host would take it, and the app refuses it
     * so one reader's reaction does not make the app stutter for everybody.
     */
    const val MAX_GIF_BYTES: Long = 20L * 1024 * 1024

    /**
     * Types the app will offer, because every reader who sees the comment can
     * display them.
     *
     * HEIC and HEIF are deliberately absent even though Android's camera writes
     * them: AniList shows the comment to other people, and a phone format that no
     * browser renders is a broken image on the site. Those two are re-encoded to
     * JPEG by [com.anisequel.data.repository.PickedImageReader] before they get
     * here, so an iPhone photo still attaches.
     */
    val SUPPORTED_TYPES: Set<String> = setOf(
        "image/jpeg",
        "image/pjpeg",
        "image/png",
        "image/webp",
        "image/gif",
        "image/avif",
        "image/bmp"
    )

    /** Why an attachment was refused, or that it was not. */
    enum class Verdict {
        Accept,

        /** Nothing to upload - an empty stream, or a provider that lied about size. */
        Empty,

        /** A type outside [SUPPORTED_TYPES]: audio, video, a document, a bare extension. */
        UnsupportedType,

        /** Over the app's limit for its kind. */
        TooLarge
    }

    /**
     * Whether [sizeBytes] of [mimeType] may be handed to the host.
     *
     * A null or blank mime type is refused rather than guessed at. A content
     * provider that does not report a type is common enough (a file manager, a
     * cloud document), but "unknown" is also exactly what a renamed executable
     * looks like, and the app should not find out which one it was by uploading it.
     */
    fun verdictFor(mimeType: String?, sizeBytes: Long): Verdict {
        if (sizeBytes <= 0L) return Verdict.Empty
        val normalised = mimeType?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
            ?: return Verdict.UnsupportedType
        // A type this app re-encodes before uploading is judged by the size it
        // arrives at, not by whether the host would accept it as it is. HEIC is
        // deliberately absent from SUPPORTED_TYPES because no browser renders it,
        // but checking that set first made the recode branch unreachable: a phone
        // photo was refused as "only images can be attached" before
        // PickedImageReader ever got to convert it, which is exactly the file an
        // Android camera writes.
        if (normalised in RECODE_REQUIRED_TYPES) {
            return if (sizeBytes > MAX_FILE_BYTES) Verdict.TooLarge else Verdict.Accept
        }
        if (normalised !in SUPPORTED_TYPES) return Verdict.UnsupportedType
        val ceiling = if (normalised == "image/gif") MAX_GIF_BYTES else MAX_FILE_BYTES
        return if (sizeBytes > ceiling) Verdict.TooLarge else Verdict.Accept
    }

    /** The sentence to show for [verdict]. */
    fun reasonFor(verdict: Verdict): String = when (verdict) {
        Verdict.Accept -> ""
        Verdict.Empty -> "That file was empty."
        Verdict.UnsupportedType ->
            "Only images can be attached - jpeg, png, webp, gif, avif or bmp."
        Verdict.TooLarge ->
            "That file is too large. Animations up to ${MAX_GIF_BYTES / (1024 * 1024)} MB " +
                "and other images up to ${MAX_FILE_BYTES / (1024 * 1024)} MB."
    }

    /**
     * The AniList markup for an uploaded image.
     *
     * `img(url)` is the site's own tag rather than markdown's `![](url)`: it is
     * what AniList's editor writes, what its renderer documents, and it is the
     * form that carries a width when one is wanted. It is also a *block* - the
     * parser matches it only when it is the whole line - which is why inserts go
     * through `insertBlock` rather than being dropped into a sentence.
     */
    /**
     * How the embed sits in the comment: on its own, inside a centred span, or
     * behind a spoiler's lid.
     *
     * All three are markup the site's own editor writes and this app's parser
     * renders, which is the rule every control in the composer is held to - a
     * marker the parser does not understand would post as a line of literal
     * markup in front of everybody.
     */
    enum class EmbedFraming { Plain, Centred, Spoiler }

    fun embedMarkdown(
        url: String,
        width: String? = null,
        framing: EmbedFraming = EmbedFraming.Plain
    ): String {
        val suffix = width?.takeIf { WIDTH_PATTERNS.any { pattern -> pattern.matches(it) } }
        val image = if (suffix == null) "img($url)" else "img$suffix($url)"
        return when (framing) {
            EmbedFraming.Plain -> image
            // The centred span is a block of its own, fences on lines of their own:
            // `~~~img(...)~~~` on one line is not what the parser matches, and the
            // Centre control on the format bar already writes it this way.
            EmbedFraming.Centred -> "~~~\n$image\n~~~"
            // The one-line form, because the block spoiler is matched against a
            // whole line - and the image inside it only renders at all since the
            // spoiler's payload started being re-parsed as blocks.
            EmbedFraming.Spoiler -> "~!$image!~"
        }
    }

    /**
     * The widths AniList's own editor writes.
     *
     * A bare number is pixels and a percent is a share of the column, which is why
     * both are accepted and anything else is dropped: the parser reads
     * `(\d+%|\d+)` before the parenthesis, and `imgwibble(url)` is not an image at
     * all - it is a line of text in somebody's comment.
     */
    val EMBED_WIDTHS: List<String> = listOf("220", "400", "50%", "100%")

    private val WIDTH_PATTERNS = listOf(Regex("""\d+"""), Regex("""\d+%"""))

    /**
     * The part name to send with the file.
     *
     * The host keeps the extension and serves the file under it, so an uploaded
     * JPEG named `photo` would come back as a URL with no extension and no
     * content type worth trusting. Spaces and shell-hostile characters go; the
     * extension comes from the mime type the policy already accepted, not from
     * whatever the picker called the file, because a `.gif` name on a PNG body is
     * how a comment ends up with a broken image.
     */
    fun storageFileName(mimeType: String?, fallback: String?): String {
        val extension = extensionFor(mimeType)
        val stem = fallback
            ?.substringBeforeLast('.', fallback)
            ?.map { if (it.isLetterOrDigit() || it == '-' || it == '_') it else '-' }
            ?.joinToString("")
            ?.trim('-', '.')
            ?.take(40)
            ?.takeIf { it.isNotEmpty() }
            ?: "attachment"
        return "$stem.$extension"
    }

    /** The extension the host should serve [mimeType] under. */
    fun extensionFor(mimeType: String?): String = when (mimeType?.trim()?.lowercase()) {
        "image/png" -> "png"
        "image/webp" -> "webp"
        "image/gif" -> "gif"
        "image/avif" -> "avif"
        "image/bmp" -> "bmp"
        else -> "jpg"
    }

    /**
     * The image type the bytes actually are, read from their magic numbers.
     *
     * Content providers lie routinely about this: a Drive or Nextcloud document
     * arrives as `application/octet-stream`, a gallery exports a PNG named
     * `.jpg`, and a screenshot from a messaging app reports no type at all. The
     * decision this feeds - upload it, re-encode it, or refuse it - changes what
     * every other reader sees in the comment, so it is taken from the file rather
     * than from a string the file's carrier happened to offer.
     *
     * Returns null for bytes that are not one of the recognised image types,
     * which the caller refuses; that is the point of the function, not a gap in it.
     */
    fun sniffMimeType(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        fun at(offset: Int, vararg wanted: Int): Boolean =
            wanted.indices.all { i -> bytes[offset + i].toInt() and 0xFF == wanted[i] }
        fun text(offset: Int, wanted: String): Boolean =
            wanted.indices.all { i -> bytes[offset + i].toInt().toChar() == wanted[i] }

        return when {
            at(0, 0xFF, 0xD8, 0xFF) -> "image/jpeg"
            at(0, 0x89, 0x50, 0x4E, 0x47) -> "image/png"
            text(0, "GIF87a") || text(0, "GIF89a") -> "image/gif"
            at(0, 0x42, 0x4D) -> "image/bmp"
            text(0, "RIFF") && text(8, "WEBP") -> "image/webp"
            // An `ftyp` box is the ISO-BMFF family: AVIF and HEIF/HEIC are the same
            // container with a different major brand four bytes later, and telling
            // them apart is the whole question here - one uploads as it is, the
            // other has to be re-encoded before anybody can view it.
            text(4, "ftyp") -> when {
                text(8, "avif") || text(8, "avis") -> "image/avif"
                text(8, "heic") || text(8, "heix") || text(8, "heim") ||
                    text(8, "heis") || text(8, "hevc") || text(8, "hevm") ||
                    text(8, "hevs") || text(8, "mif1") || text(8, "msf1") -> "image/heif"
                else -> null
            }
            else -> null
        }
    }

    /**
     * Types the app will not upload as they are, because most readers cannot open
     * them. Android's camera writes these; they become JPEG before they leave.
     */
    val RECODE_REQUIRED_TYPES: Set<String> = setOf("image/heif", "image/heic")

    /** Whether [mimeType] is a format that has to be re-encoded first. */
    fun needsRecode(mimeType: String?): Boolean =
        mimeType?.trim()?.lowercase() in RECODE_REQUIRED_TYPES
}
