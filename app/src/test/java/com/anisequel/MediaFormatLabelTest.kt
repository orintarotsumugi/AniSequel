package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.formatLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * Guards how AniList's `format` enum is shown to a reader.
 *
 * ## The failure
 *
 * `formatLabel` returned the field unchanged. The Info page printed it twice: as a
 * hero chip under the title, and in the Details grid beside "Episodes" and
 * "Season". So a TV special read **TV_SHORT** and a one-shot manga read
 * **ONE_SHOT** - AniList's raw GraphQL vocabulary, set in the same typeface as the
 * show's name.
 *
 * This is the same defect [MediaStatusLabelTest] records for `status`, and the two
 * were visible in the same card: status said "Finished" while format said
 * `TV_SHORT`, half-fixed and half-broken. `MediaStatusLabelTest`'s own KDoc claims
 * `formatLabel` "already solved this" - it had not. That comment was wrong, and it
 * is why nobody looked for this again.
 *
 * ## Why it survived
 *
 * Two other places in the app had already written a readable mapper for this field
 * (`ProfileActivityCard.formatLabel` and `ProfileComponents.favouriteCaption`),
 * both correct, both private. So the knowledge existed - just in three copies, none
 * of them the one the Info page used.
 *
 * ## The shape of the rule
 *
 * Same rule as `statusLabel`: acronyms stay upper (`TV`, `OVA`, `ONA`), the rest
 * are spelled as a person writes them (`TV Short`, `One Shot`), and an unrecognised
 * value is passed through rather than replaced. An unfamiliar word honestly
 * signals that it is unfamiliar; a confident wrong label does not.
 */
class MediaFormatLabelTest {

    private fun media(format: String?, type: String? = "ANIME") =
        MediaNode(id = 1, format = format, type = type)

    @Test
    fun `the underscore enum that appeared on screen is now spelled`() {
        assertEquals("TV Short", media("TV_SHORT").formatLabel)
        assertEquals("One Shot", media("ONE_SHOT", type = "MANGA").formatLabel)
    }

    /**
     * The exact shape that reached a reader: uppercase on a raw field. The chip
     * uppercases whatever this returns, so `TV Short` renders as `TV SHORT`, which
     * is a caps treatment and not an API token - the underscore is what gave it away.
     */
    @Test
    fun `no format renders with an underscore`() {
        listOf("TV", "TV_SHORT", "MOVIE", "SPECIAL", "OVA", "ONA", "MANGA", "NOVEL", "ONE_SHOT")
            .forEach { raw ->
                assertFalse(
                    "'${media(raw).formatLabel}' still carries the enum's underscore",
                    media(raw).formatLabel.contains('_')
                )
            }
    }

    /** Acronyms stay upper: they are acronyms on a shelf and in a sentence. */
    @Test
    fun `acronyms keep their case`() {
        assertEquals("TV", media("TV").formatLabel)
        assertEquals("OVA", media("OVA").formatLabel)
        assertEquals("ONA", media("ONA").formatLabel)
    }

    @Test
    fun `ordinary formats are spelled as a person writes them`() {
        assertEquals("Movie", media("MOVIE").formatLabel)
        assertEquals("Special", media("SPECIAL").formatLabel)
        assertEquals("Manga", media("MANGA", type = "MANGA").formatLabel)
        assertEquals("Novel", media("NOVEL", type = "MANGA").formatLabel)
    }

    /**
     * Case is AniList's, not ours. The parser stores what it sent, so a lowercase
     * value has to land on the same label rather than on an "unknown" fallback.
     */
    @Test
    fun `case does not change the answer`() {
        assertEquals(media("TV_SHORT").formatLabel, media("tv_short").formatLabel)
        assertEquals(media("MOVIE").formatLabel, media("Movie").formatLabel)
    }

    /**
     * A format AniList adds later reads as itself rather than being flattened, and
     * a lowercased unknown loses its underscores rather than being printed with them.
     */
    @Test
    fun `an unrecognised format passes through readably`() {
        // The API spells music videos `MUSIC`; `MUSIC_VIDEO` is kept as an alias
        // because it is the word the docs use and a fixture can carry it.
        assertEquals("Music Video", media("MUSIC").formatLabel)
        assertEquals("Music Video", media("MUSIC_VIDEO").formatLabel)
        // Not `Live action`: the fallback lowercases, title-cases the first
        // character and leaves the rest alone, so it reads "Live action". Pinning
        // it as written is the point - an unfamiliar value is passed through by
        // this rule, and a future "title-case every word" edit would silently
        // rewrite it.
        assertEquals("Live action", media("LIVE_ACTION").formatLabel)
    }

    /**
     * No format is not "ANIME": AniList answers null for entries it has little data
     * on, and `ANIME` is a category rather than a format - a manga with no format
     * is a manga, and an anime with no format is television, which is what the
     * reader would otherwise assume anyway.
     */
    @Test
    fun `a missing format falls back to the kind of thing, not to a category`() {
        assertEquals("TV", media(null).formatLabel)
        assertEquals("TV", media("  ").formatLabel)
        assertEquals("Manga", media(null, type = "MANGA").formatLabel)
    }
}
