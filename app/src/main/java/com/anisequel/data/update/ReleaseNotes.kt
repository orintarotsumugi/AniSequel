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

/**
 * What kind of change a note describes, inferred from its title.
 *
 * The changelog has no machine-readable category field and adding one would mean
 * editing every historical release block. Titles are written in the app's own
 * voice and already start with the verb - "Adds", "Fixes", "The bar-lift crash is
 * fixed" - so the classification is a keyword pass over the title only. It is a
 * *presentation* hint: an unrecognised title is [NOTE], not an error, and the
 * detail text is never used to guess because a fix often mentions the feature it
 * fixed.
 *
 * This exists so the update prompt can colour-code what changed. The categories
 * are deliberately outcome-shaped rather than conventional-commit-shaped: a reader
 * deciding whether to update cares whether it is new, fixed, faster or different,
 * not whether it was a `feat` or a `refactor`.
 */
enum class ReleaseNoteTone(val label: String) {
    NEW("New"),
    FIX("Fix"),
    IMPROVE("Faster"),
    CHANGE("Changed"),
    NOTE("Note")
}

fun releaseNoteTone(title: String): ReleaseNoteTone {
    val text = title.lowercase()
    return when {
        listOf("fix", "bug", "crash", "restore", "repair", "no longer", "stops")
            .any { text.contains(it) } -> ReleaseNoteTone.FIX
        listOf("new", "add", "introduc", "now has", "can be", "gains")
            .any { text.contains(it) } -> ReleaseNoteTone.NEW
        listOf("faster", "speed", "improv", "optimi", "reduc", "lighter", "smooth")
            .any { text.contains(it) } -> ReleaseNoteTone.IMPROVE
        listOf("change", "remov", "replac", "rename", "moves", "move ")
            .any { text.contains(it) } -> ReleaseNoteTone.CHANGE
        else -> ReleaseNoteTone.NOTE
    }
}

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
