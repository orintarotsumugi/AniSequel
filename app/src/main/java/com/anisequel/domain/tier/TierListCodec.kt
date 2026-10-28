package com.anisequel.domain.tier

/**
 * The tier list's on-disk format, as pure functions.
 *
 * ## Why this moved out of the repository
 *
 * It used to be a `companion object` inside `TierListPreferences`, which is an
 * Android class: `Context`, `DataStore`, `androidx.datastore.preferences`. The
 * tests could call the companion without a device, but only because the JVM unit
 * tests carry a stubbed `android.jar` and the companion's functions happen not to
 * touch it - so the one part of the format that silently loses somebody's work was
 * tested *through* a class that has nothing to do with it. Here it is pure Kotlin
 * with no Android import at all, which is the layer rule the rest of `domain/tier`
 * already follows.
 *
 * ## The format is line-based, and it stays that way
 *
 * Not JSON, not a serialization library. A list is one line of
 * `label|colour|id|floor` fields for the tiers and one line of `mediaId:tierId`
 * pairs for the placements, and a document is six of those lines. Every reader
 * here skips what it cannot parse and keeps what it can, because the failure mode
 * of a strict parse is not a crash: it is a *successful load of an empty list*,
 * which looks exactly like the person's work having been deleted, with nothing on
 * screen that says why.
 *
 * The fourth tier field and the fifth document line were added by the board
 * redesign (editable rating bands, title placement, zoom). The sixth names the
 * media type now that a list can rank manga. Every reader accepts the older,
 * shorter form: a tier with no floor decodes with `scoreFloor = null`, a document
 * with no display line gets [TierDisplay]'s defaults, and one with no media type
 * is an anime list. That is what lets an install upgrade without its list changing
 * shape.
 *
 * ## Why the tiers' text is escaped and the placements' is not
 *
 * A tier's **label is free text** - it is whatever the person typed when they
 * renamed a row - so it can contain the format's own separators. It did: renaming a
 * tier to `S, best of` produced two chunks that both failed to parse, and the row
 * came back renamed with the rest of the list shifted around it. Escaping `\`, `,`,
 * `|`, `;` and newlines is what lets a label say whatever it likes. Ids and media
 * ids are numbers and generated strings that contain none of those characters, so
 * escaping them would be machinery guarding a case that cannot happen.
 */

private const val FIELD_SEPARATOR = ','
private const val PART_SEPARATOR = '|'
private const val PAIR_SEPARATOR = ';'
private const val ID_SEPARATOR = ':'
private const val DOCUMENT_LINE_SEPARATOR = '\n'

// ---------------------------------------------------------------------------
// Tiers
// ---------------------------------------------------------------------------

/**
 * `label|colour|id|floor`, comma-separated, in the order given.
 *
 * The id stays before the floor so that the fields a reader written for the old
 * layout understands - label, colour, id - are still in the positions it expects;
 * that reader rejects the extra part rather than misreading one, which is the
 * honest failure. A tier with no band writes an empty fourth part, so the shape of
 * the line does not change depending on whether a band happens to be set.
 */
fun encodeTiers(tiers: List<TierDefinition>): String =
    tiers.joinToString(FIELD_SEPARATOR.toString()) {
        listOf(
            escape(it.label),
            it.colorArgb.toString(),
            escape(it.id),
            it.scoreFloor?.toString().orEmpty()
        ).joinToString(PART_SEPARATOR.toString())
    }

/**
 * The tiers [encodeTiers] wrote, or [StandardTiers] when there is nothing readable.
 *
 * Falling back to the template rather than to an empty list is deliberate: a list
 * with no rows has nowhere to drag anything and no way back, while the standard
 * six are a working list the person can rename. A person who has lost their
 * colours has lost much less than one who has lost the ability to file a title.
 */
fun decodeTiers(raw: String?): List<TierDefinition> {
    if (raw.isNullOrBlank()) return StandardTiers
    val parsed = splitEscaped(raw, FIELD_SEPARATOR).mapNotNull { field ->
        val parts = splitEscaped(field, PART_SEPARATOR)
        // Three parts is a tier written before bands existed; four is current.
        // Anything else is a field this reader does not understand, and guessing
        // where its separators were is how a label ends up in the colour column.
        if (parts.size !in 3..4) return@mapNotNull null
        val color = unescape(parts[1]).toLongOrNull() ?: return@mapNotNull null
        val label = unescape(parts[0]).trim()
        val id = unescape(parts[2]).trim()
        if (label.isEmpty() || id.isEmpty()) {
            null
        } else {
            // A fourth part that is present but not a number loses the *band*,
            // not the row. The row is where the placements live; a band is one tap
            // to set again, and dropping the row would orphan everything filed
            // under it.
            val floor = parts.getOrNull(3)?.trim()?.takeIf { it.isNotEmpty() }?.toIntOrNull()
            TierDefinition(id, label, color, scoreFloor = floor)
        }
    }
    return parsed.ifEmpty { StandardTiers }
}

// ---------------------------------------------------------------------------
// Placements
// ---------------------------------------------------------------------------

/**
 * The placements as `mediaId:tierId` pairs, in the map's own order.
 *
 * The order is the list: [`withPlacement`] rebuilds this map to move an entry, and
 * both rows on the board and the exported file read the members of a tier out of
 * it in this sequence. There is no separate position field to fall out of step.
 */
fun encodeAssignment(assignment: TierAssignment): String =
    assignment.tierIdByMediaId.entries.joinToString(PAIR_SEPARATOR.toString()) {
        "${it.key}$ID_SEPARATOR${it.value}"
    }

/**
 * The placements [encodeAssignment] wrote. Unparseable pairs are skipped.
 *
 * A dangling `30013` with no tier is dropped rather than guessed at: guessing files
 * a title under a judgement nobody made, which is the same rule seeding follows for
 * unrated titles.
 */
fun decodeAssignment(raw: String?): TierAssignment {
    if (raw.isNullOrBlank()) return TierAssignment()
    val map = LinkedHashMap<Int, String>()
    raw.split(PAIR_SEPARATOR).forEach { pair ->
        val separator = pair.indexOf(ID_SEPARATOR)
        if (separator <= 0) return@forEach
        val mediaId = pair.substring(0, separator).trim().toIntOrNull() ?: return@forEach
        val tierId = pair.substring(separator + 1).trim()
        if (tierId.isNotEmpty()) map[mediaId] = tierId
    }
    return TierAssignment(map)
}

// ---------------------------------------------------------------------------
// Documents
// ---------------------------------------------------------------------------

/**
 * The drawing preferences as one comma-separated line.
 *
 * `showTitles,position,zoom,length`, in that order, with both enums stored by
 * name so an added value reads as a token rather than as an ordinal that silently
 * means a different one. Kept public so the round trip can be tested without a
 * whole document.
 *
 * The length is the fourth field rather than a replacement for the zoom, so a
 * document written by a build that predates it still reads exactly as it did: an
 * absent fourth part falls back to the standard length.
 */
fun encodeDisplay(display: TierDisplay): String = listOf(
    if (display.showTitles) "1" else "0",
    display.titlePosition.name,
    display.zoom.toString(),
    display.titleLength.name
).joinToString(FIELD_SEPARATOR.toString())

/**
 * The display preferences [encodeDisplay] wrote, or the defaults.
 *
 * Every field falls back on its own: a build that learns to store the zoom but
 * not the title position still gets its zoom back. A non-finite zoom is treated
 * as absent rather than passed on - it is the one value here that can make the
 * board's layout maths produce NaN and draw nothing at all.
 */
fun decodeDisplay(raw: String?): TierDisplay {
    if (raw.isNullOrBlank()) return TierDisplay()
    val parts = raw.split(FIELD_SEPARATOR)
    val showTitles = when (parts.getOrNull(0)?.trim()?.lowercase()) {
        "0", "false" -> false
        "1", "true" -> true
        else -> true
    }
    val position = when (parts.getOrNull(1)?.trim()) {
        TierTitlePosition.ON_COVER.name -> TierTitlePosition.ON_COVER
        else -> TierTitlePosition.UNDER_COVER
    }
    val zoom = parts.getOrNull(2)?.trim()?.toFloatOrNull()?.takeIf { it.isFinite() } ?: 1f
    val length = when (parts.getOrNull(3)?.trim()) {
        TierTitleLength.SHORT.name -> TierTitleLength.SHORT
        TierTitleLength.FULL.name -> TierTitleLength.FULL
        else -> TierTitleLength.STANDARD
    }
    return TierDisplay(
        showTitles = showTitles,
        titlePosition = position,
        titleLength = length,
        zoom = zoom
    )
}

/**
 * The media type [encodeTierListDocument] wrote, or anime when the line is absent.
 *
 * Case-insensitive because the token is written by `name` and read by a build that
 * may spell it differently; an unrecognised value is anime rather than null because
 * a document that cannot name its pool is still somebody's ranking, and the only
 * pool that existed when this line was optional is anime.
 */
fun decodeMediaType(raw: String?): TierMediaType {
    val token = raw?.trim()?.uppercase() ?: return TierMediaType.ANIME
    return TierMediaType.entries.firstOrNull { it.name == token } ?: TierMediaType.ANIME
}

/**
 * One saved list as six lines: name, last write, tiers, placements, display,
 * media type.
 *
 * The id is not in there. It is the *key* the document is stored under, and a value
 * that appears both in a key and in the value is a value that can disagree with
 * itself - which is exactly what makes a document unopenable rather than merely
 * misnamed.
 *
 * The display line and the media type are last, in that order, so that a reader
 * written before they existed sees exactly the lines it knew and a reader from
 * this build sees the extra one when it is there. A five-line document is an
 * anime list: manga rankings did not exist when five was the whole format, and a
 * missing line must not make somebody's anime board unopenable.
 */
fun encodeTierListDocument(document: TierListDocument): String = listOf(
    escape(document.name.trim()),
    document.updatedAt.toString(),
    encodeTiers(document.tiers),
    encodeAssignment(document.assignment),
    encodeDisplay(document.display),
    document.mediaType.name
).joinToString(DOCUMENT_LINE_SEPARATOR.toString())

/**
 * The document [encodeTierListDocument] wrote, or null when there is nothing there.
 *
 * Null is reserved for "this key holds nothing", which is the one case where a list
 * genuinely does not exist. A document whose name line is blank keeps its
 * placements and gets [TIER_LIST_BASE_NAME]: the name is a label the person can set
 * again in one tap, and dropping the whole document over it would throw away the
 * rankings to fix the label.
 */
fun decodeTierListDocument(id: String, raw: String?): TierListDocument? {
    if (raw.isNullOrBlank() || id.isBlank()) return null
    val lines = raw.split(DOCUMENT_LINE_SEPARATOR)
    val name = lines.getOrNull(0)?.let { unescape(it).trim() }.orEmpty()
    return TierListDocument(
        id = id,
        name = name.ifEmpty { TIER_LIST_BASE_NAME },
        tiers = decodeTiers(lines.getOrNull(2)),
        assignment = decodeAssignment(lines.getOrNull(3)),
        // Absent on a document written before display options existed, which is
        // exactly the state whose defaults reproduce that board.
        display = decodeDisplay(lines.getOrNull(4)),
        // Absent on every document written before the tier list ranked manga, and
        // those are all anime lists by definition. An unknown token is treated the
        // same way rather than failing the parse: a build that learns a third media
        // type must not make this one read its documents as empty.
        mediaType = decodeMediaType(lines.getOrNull(5)),
        // A document from a build that wrote fewer lines than this one reads keeps
        // its content and reads as never-written, which sorts it to the bottom of
        // an "updated" sort rather than claiming it was touched now.
        updatedAt = lines.getOrNull(1)?.trim()?.toLongOrNull() ?: 0L
    )
}

// ---------------------------------------------------------------------------
// The index
// ---------------------------------------------------------------------------

/**
 * The list an install that predates the library was keeping, as a document.
 *
 * Before lists were documents there was exactly one, held across two keys
 * (`tiers_v1` and `assignment_v1`). Null means there was nothing to keep - both
 * keys absent - which is the state of a fresh install, and the reason this returns
 * null rather than an empty default list: migrating an install that never made a
 * list would greet the person with one empty list called [TIER_LIST_BASE_NAME]
 * that they did not create.
 *
 * A *decision* rather than a side effect, so it can be tested without a device and
 * without a DataStore, and so the store's migration is the two lines of plumbing it
 * looks like. Everything it decides is a rule worth being sure of: the placements
 * are the work and are kept even when the tiers are unreadable (they fall back to
 * the standard six, which is one tap to rename), and a value that is present but
 * blank still counts as a list having existed.
 *
 * The id is generated the same way a new list's is, because this document is the
 * first one in the library only from the migration's point of view.
 */
fun legacyTierListDocument(tiers: String?, assignment: String?): TierListDocument? {
    if (tiers == null && assignment == null) return null
    return TierListDocument(
        id = newTierListId(emptyList()),
        name = TIER_LIST_BASE_NAME,
        tiers = decodeTiers(tiers),
        assignment = decodeAssignment(assignment)
    )
}

/**
 * The ids of the saved lists, in the order they were created.
 *
 * An index rather than an enumeration of the store's keys, because DataStore's
 * `Preferences` is a map with no stable order: the library screen sorts by name or
 * by recency and would otherwise be re-sorted by whatever the preferences map
 * happened to iterate in, which changes as keys are added and removed. This is also
 * what makes a key that is not in the index (a document left behind by an
 * interrupted delete) invisible instead of appearing as a nameless list.
 */
fun encodeDocumentIds(ids: List<String>): String =
    ids.map { it.trim() }.filter { it.isNotEmpty() }.distinct()
        .joinToString(FIELD_SEPARATOR.toString())

fun decodeDocumentIds(raw: String?): List<String> {
    if (raw.isNullOrBlank()) return emptyList()
    val seen = LinkedHashSet<String>()
    raw.split(FIELD_SEPARATOR).forEach { candidate ->
        val id = candidate.trim()
        if (id.isNotEmpty()) seen += id
    }
    return seen.toList()
}

// ---------------------------------------------------------------------------
// Escaping
// ---------------------------------------------------------------------------

/**
 * Marks the format's own characters so a label can contain them.
 *
 * A backslash first, or escaping a comma would double the backslash that escaping
 * inserted and the reader would see one more of them every round trip.
 */
private fun escape(value: String): String {
    if (value.none { it == '\\' || it == ',' || it == '|' || it == ';' || it == '\n' }) {
        return value
    }
    val out = StringBuilder(value.length + 8)
    value.forEach { ch ->
        when (ch) {
            '\\' -> out.append("\\\\")
            ',' -> out.append("\\,")
            '|' -> out.append("\\|")
            ';' -> out.append("\\;")
            '\n' -> out.append("\\n")
            else -> out.append(ch)
        }
    }
    return out.toString()
}

/**
 * The text [escape] wrote.
 *
 * A backslash before a character this format does not escape is left as written
 * rather than dropped: a label is free text, and a person who typed `\d` should not
 * come back to `d`.
 */
private fun unescape(value: String): String {
    if ('\\' !in value) return value
    val out = StringBuilder(value.length)
    var index = 0
    while (index < value.length) {
        val ch = value[index]
        if (ch == '\\' && index + 1 < value.length) {
            index++
            out.append(if (value[index] == 'n') '\n' else value[index])
        } else {
            // A trailing lone backslash, or a character with nothing to escape.
            out.append(ch)
        }
        index++
    }
    return out.toString()
}

/**
 * Splits on [separator], ignoring separators that were escaped.
 *
 * The backslash is kept in the field rather than resolved here, so that [unescape]
 * is the single place that decides what an escape means and a split of a split
 * (fields, then parts) does not have to unescape twice in the same order in both
 * readers.
 */
private fun splitEscaped(raw: String, separator: Char): List<String> {
    val fields = mutableListOf<String>()
    val current = StringBuilder()
    var escaped = false
    raw.forEach { ch ->
        when {
            escaped -> {
                current.append('\\').append(ch)
                escaped = false
            }
            ch == '\\' -> escaped = true
            ch == separator -> {
                fields += current.toString()
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    if (escaped) current.append('\\')
    fields += current.toString()
    return fields
}
