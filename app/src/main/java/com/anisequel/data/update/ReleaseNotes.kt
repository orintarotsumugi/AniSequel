package com.anisequel.data.update

/**
 * One "what changed" line, as both the update prompt and the Settings card draw it.
 *
 * A neutral value rather than either of the two models that produce one: the
 * bundled changelog has its own Moshi type and the manifest's notes are a flat
 * string, so sharing a *renderer* means sharing a shape, not a parser or a data
 * class from the other layer. See `ui/components/ReleaseNotes.kt`.
 */
data class ReleaseNote(val title: String, val detail: String? = null)

/** The prefix the release workflow writes before every entry. */
private const val BULLET = "- "

/** The separator between an entry's title and its detail, as the workflow writes it. */
private const val DETAIL_SEPARATOR = ": "

/**
 * The entries in an `update.json` `notes` field.
 *
 * ## The format, and why parsing it is worth doing
 *
 * The workflow writes `notes` from `assets/changelog.json` - one
 * `- Title: detail` line per entry, then a blank line and a
 * `Build <run> from <sha>.` footer. Before that it wrote the GitHub release body,
 * which was the same "Find the anime sequels you finished..." paragraph in every
 * release: a description of the app, not of what changed. The dialog used to skip
 * the field entirely for that reason and offer a link to the release page instead,
 * which meant the one moment a person decides whether to update told them nothing.
 *
 * ## It is lenient on purpose
 *
 * `notes` is a file on a public URL, written by a shell script and capped at 1200
 * bytes with `head -c`, which can cut the last line in half. So:
 *
 *  - anything that is not a bullet is ignored (the footer, a stray blank line, an
 *    older manifest whose notes were markdown);
 *  - a bullet with no title is dropped rather than drawn as an empty card;
 *  - a truncated final bullet is kept, because half a sentence about what changed
 *    is still more than the link it would otherwise be replaced with.
 *
 * An empty result is a real answer, not an error: it means this manifest cannot
 * describe itself, and the caller falls back to the release page.
 */
fun parseReleaseNotes(raw: String?): List<ReleaseNote> {
    if (raw.isNullOrBlank()) return emptyList()

    return raw.lineSequence()
        .map { it.trim() }
        .filter { it.startsWith(BULLET) }
        .mapNotNull { line ->
            val body = line.removePrefix(BULLET).trim()
            if (body.isEmpty()) return@mapNotNull null

            val separator = body.indexOf(DETAIL_SEPARATOR)
            val title = if (separator < 0) body else body.take(separator).trim()
            val detail = if (separator < 0) {
                null
            } else {
                body.substring(separator + DETAIL_SEPARATOR.length).trim()
            }
            title.takeIf { it.isNotEmpty() }?.let {
                ReleaseNote(title = it, detail = detail?.takeIf { text -> text.isNotEmpty() })
            }
        }
        .toList()
}
