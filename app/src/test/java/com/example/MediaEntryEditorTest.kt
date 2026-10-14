package com.example

import com.example.data.model.FuzzyDate
import com.example.data.model.GraphQLResponse
import com.example.data.model.MediaEntryEdit
import com.example.data.model.MediaListEntryData
import com.example.data.model.SimpleMediaListEntry
import com.example.data.network.GraphQLQueries
import com.example.data.repository.toSaveVariables
import com.example.ui.components.detail.formatScore
import com.example.ui.components.detail.statusLabel
import com.example.ui.components.detail.toDraft
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the entry editor against the two ways it can quietly damage a real list.
 *
 * ## Why this needs its own test
 *
 * An editor is the one screen whose bugs are *writes*. Everything else in the app
 * can be wrong on screen and recover; a form that sends the wrong score scale or
 * drops a field it does not draw has rewritten the user's AniList entry, and the
 * next build cannot tell that the value it reads back was not theirs.
 *
 * So this asserts the two things that would do that, and that the mutations are
 * classified as writes so the request coalescer never drops one.
 */
class MediaEntryEditorTest {

    private val moshi = Moshi.Builder().build()

    private fun parse(json: String): MediaListEntryData {
        val responseType = Types.newParameterizedType(GraphQLResponse::class.java, MediaListEntryData::class.java)
        val adapter = moshi.adapter<GraphQLResponse<MediaListEntryData>>(responseType)
        return adapter.fromJson(json)!!.data!!
    }

    @Test
    fun `parses every field the editor can change`() {
        val json = """
            {
              "data": {
                "MediaList": {
                  "id": 456,
                  "mediaId": 101922,
                  "status": "CURRENT",
                  "progress": 2,
                  "progressVolumes": 3,
                  "score": 85,
                  "private": true,
                  "startedAt": { "year": 2026, "month": 10, "day": 6 },
                  "completedAt": null
                }
              }
            }
        """.trimIndent()

        val entry = parse(json).entry!!

        assertEquals(456, entry.id)
        assertEquals("CURRENT", entry.status)
        assertEquals(2, entry.progress)
        assertEquals(3, entry.progressVolumes)
        assertEquals(85.0, entry.score!!, 0.0)
        // `private` is a Kotlin keyword, so the wire name is pinned by annotation.
        // A missed `@Json(name = "private")` reads null here and never fails a build.
        assertEquals(true, entry.isPrivate)
        assertEquals(FuzzyDate(2026, 10, 6), entry.startedAt)
        assertEquals(null, entry.completedAt)
    }

    @Test
    fun `a save mutation is never treated as a read`() {
        // The repository's `isReadOnly` collects *reads* into the request
        // coalescer. A mutation that matched the query pattern would be collapsed
        // with a concurrent identical one, silently dropping the second write.
        assertTrue(
            "a save must start with `mutation`",
            GraphQLQueries.SAVE_MEDIA_LIST_ENTRY.trimStart().startsWith("mutation")
        )
        assertTrue(
            "a delete must start with `mutation`",
            GraphQLQueries.DELETE_MEDIA_LIST_ENTRY.trimStart().startsWith("mutation")
        )
        assertTrue(
            "loading an entry is a read and may be coalesced",
            GraphQLQueries.GET_MEDIA_LIST_ENTRY.trimStart().startsWith("query")
        )
    }

    @Test
    fun `the save mutation sends the fields AniList actually has`() {
        val query = GraphQLQueries.SAVE_MEDIA_LIST_ENTRY

        // AniList names this `completedAt`; there is no `finishedAt` argument, and
        // a document that sent one would be rejected outright with no partial save.
        assertTrue(query.contains("completedAt:"))
        assertFalse("AniList has no finishedAt argument", query.contains("finishedAt"))
        // The whole-snapshot save depends on every editable field being declared,
        // or a cleared date would leave the old one on the server.
        listOf("startedAt:", "progressVolumes:", "score:", "private:").forEach { field ->
            assertTrue("the save is missing $field", query.contains(field))
        }
    }

    @Test
    fun `loading asks for the fields the form draws`() {
        val query = GraphQLQueries.GET_MEDIA_LIST_ENTRY
        listOf("status", "progress", "progressVolumes", "score", "private", "startedAt", "completedAt")
            .forEach { field ->
                assertTrue("the load is missing $field", query.contains(field))
            }
    }

    /**
     * The score is the one value that must survive the round trip untouched.
     *
     * AniList holds it in whichever format the account chose, so rescaling an
     * 85/100 score to "out of ten" here would save it back as 8 - a value the user
     * never entered, on the field they are most sensitive about.
     */
    @Test
    fun `a hundred-point score is not rescaled and opens on a hundred-point scale`() {
        val draft = SimpleMediaListEntry(status = "CURRENT", progress = 2, score = 85.0).toDraft()

        assertEquals(85.0, draft.score, 0.0)
        assertEquals(100, draft.scoreMax)
    }

    @Test
    fun `a ten-point score opens on a ten-point scale`() {
        val draft = SimpleMediaListEntry(status = "CURRENT", progress = 2, score = 8.5).toDraft()

        assertEquals(8.5, draft.score, 0.0)
        assertEquals(10, draft.scoreMax)
    }

    @Test
    fun `volumes read survive a save that does not show them`() {
        // The form has no volume control, but the save is a snapshot - so a value
        // loaded and not carried through is a value cleared on the server.
        val draft = SimpleMediaListEntry(status = "CURRENT", progressVolumes = 3).toDraft()

        assertEquals(3, draft.progressVolumes)
    }

    /**
     * The three numeric arguments may never be null, and the volume count may not
     * be present at all when the entry has none.
     *
     * ## Why this is the editor's worst bug and not a formatting detail
     *
     * AniList's input validator rejects an explicit null for all three and answers
     * **HTTP 400** with the whole write lost:
     *
     *     {"errors": [{"validation": {"progressVolumes":
     *                  ["The progress volumes must be an integer."]}}]}
     *
     * Verified against the live API. `progressVolumes: 0` validates, `: null` does
     * not, and leaving the argument off validates as well - so an anime, which has
     * no volume count at all, could not be saved. Every "Save changes" on a show
     * was a 400, and the form reported a save that never happened.
     */
    @Test
    fun `the save never sends a null for a numeric argument`() {
        // An anime as the editor would hold it: a status, and nothing else loaded.
        val anime = MediaEntryEdit(mediaId = 128893, status = "CURRENT").toSaveVariables()

        assertEquals(0, (anime["progress"] as Number).toInt())
        assertEquals(0.0, (anime["score"] as Number).toDouble(), 0.0)
        assertFalse(
            "a null progressVolumes is a 400, so the argument has to be absent",
            anime.containsKey("progressVolumes")
        )
        // The dates are the one pair that *must* go as nulls: that is how AniList
        // clears a date, and the row's X depends on it.
        assertTrue(anime.containsKey("startedAt"))
        assertEquals(null, anime["startedAt"])
        assertEquals(false, anime["private"] as Boolean?)
    }

    @Test
    fun `an entry with volumes sends them back untouched`() {
        val manga = MediaEntryEdit(
            mediaId = 1,
            status = "CURRENT",
            progress = 2,
            progressVolumes = 5,
            score = 85.0
        ).toSaveVariables()

        assertEquals(5, (manga["progressVolumes"] as Number).toInt())
        assertEquals(2, (manga["progress"] as Number).toInt())
        // Raw, in the account's own scale: an 85/100 score is not an 8/10 one.
        assertEquals(85.0, (manga["score"] as Number).toDouble(), 0.0)
    }

    @Test
    fun `a cleared date is sent as an explicit null, not dropped`() {
        // The form's X clears a date by setting it to null, and the snapshot save
        // has to carry that to the server - an omitted argument would leave the
        // old date in place, which reads as the X doing nothing.
        val variables = MediaEntryEdit(
            mediaId = 1,
            status = "COMPLETED",
            startedAt = null,
            completedAt = FuzzyDate(2026, 10, 6)
        ).toSaveVariables()

        assertTrue(variables.containsKey("startedAt"))
        assertEquals(null, variables["startedAt"])
        assertEquals(
            mapOf("year" to 2026, "month" to 10, "day" to 6),
            variables["completedAt"]
        )
    }

    @Test
    fun `a whole score reads without a decimal and a fractional one keeps it`() {
        assertEquals("8", formatScore(8.0))
        assertEquals("85", formatScore(85.0))
        assertEquals("8.5", formatScore(8.5))
    }

    @Test
    fun `status wording follows the medium`() {
        assertEquals("Watching", statusLabel("CURRENT", isManga = false))
        assertEquals("Reading", statusLabel("CURRENT", isManga = true))
        assertEquals("Rewatching", statusLabel("REPEATING", isManga = false))
        assertEquals("Rereading", statusLabel("REPEATING", isManga = true))
        // `null` is "no entry", which the editor can create - not a failure.
        assertEquals("Not on your list", statusLabel(null, isManga = false))
    }

    @Test
    fun `a status this build predates is shown rather than dropped`() {
        // AniList can add a status without telling us; the entry still has one and
        // the form must not render it as "Not on your list".
        assertEquals("Somethingnew", statusLabel("SOMETHINGNEW", isManga = false))
    }
}
