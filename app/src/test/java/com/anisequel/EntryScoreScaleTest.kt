package com.anisequel

import com.anisequel.data.model.GraphQLResponse
import com.anisequel.data.model.MediaListEntryData
import com.anisequel.data.model.SimpleMediaListEntry
import com.anisequel.data.network.GraphQLQueries
import com.anisequel.ui.components.detail.ScoreFormat
import com.anisequel.ui.components.detail.ScoreScale
import com.anisequel.ui.components.detail.scoreScaleFor
import com.anisequel.ui.components.detail.toDraft
import com.squareup.moshi.Moshi
import com.squareup.moshi.Types
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The score control's scale, which used to be guessed from the number.
 *
 * ## The two failures this pins
 *
 * `SaveMediaListEntry` takes the score in the account's own format, and the
 * editor has always sent it back unscaled. What it did not know was the format
 * itself, so:
 *
 *  - a **hundred-point** account whose entry held 8 or 10 opened on a 0-10
 *    slider, because the scale was inferred with `score > 10`, and the score it
 *    actually wanted was unreachable;
 *  - a **five-** or **three-point** account opened on a 0-10 slider too, which
 *    offers values AniList rejects outright;
 *  - a **decimal ten-point** account could not enter the `8.5` the row above the
 *    slider was already printing, because every scale got one tick per whole
 *    point.
 *
 * The format now comes from `Viewer.mediaListOptions.scoreFormat` in the same
 * request as the entry, and the ticks follow it. The old inference survives only
 * as the fallback for a payload with no `Viewer` - the save response.
 */
class EntryScoreScaleTest {

    private val moshi = Moshi.Builder().build()

    private fun loadedDraft(score: Double?, format: String? = null) =
        SimpleMediaListEntry(
            status = "CURRENT",
            progress = 0,
            score = score,
            scoreFormat = format
        ).toDraft()

    @Test
    fun `a hundred-point account keeps its scale when the score is below ten`() {
        // The bug: `score > 10` read this as a ten-point entry, so the slider
        // topped out at 10 and a 47 could not be entered at all.
        val draft = loadedDraft(score = 8.0, format = "POINT_100")

        assertEquals(100, draft.scoreMax)
        assertEquals(1.0, draft.scoreStep, 0.0)
        assertEquals(8.0, draft.score, 0.0)
        // A hundred ticks would be a dotted rule, so the slider is continuous and
        // the quantiser keeps it on whole points.
        assertEquals(0, ScoreScale(draft.scoreMax, draft.scoreStep).steps)
    }

    @Test
    fun `a decimal account can set the half point the row prints`() {
        val draft = loadedDraft(score = 8.5, format = "POINT_10_DECIMAL")
        val scale = ScoreScale(draft.scoreMax, draft.scoreStep)

        assertEquals(10, draft.scoreMax)
        // 100 ticks do not fit on a phone, so this is a continuous slider whose
        // values are snapped to the format's tenths.
        assertEquals(0, scale.steps)
        assertEquals(8.5, scale.quantise(8.5), 0.0)
        assertEquals(8.3, scale.quantise(8.31), 0.0)
        // The loaded value must survive untouched: only a drag snaps it.
        assertEquals(8.3, loadedDraft(score = 8.3, format = "POINT_10_DECIMAL").score, 0.0)
    }

    @Test
    fun `a five-point account cannot reach a score its format rejects`() {
        val draft = loadedDraft(score = 4.0, format = "POINT_5")
        val scale = ScoreScale(draft.scoreMax, draft.scoreStep)

        assertEquals(5, draft.scoreMax)
        assertEquals(4, scale.steps)
        assertEquals(5.0, scale.quantise(9.0), 0.0)
    }

    @Test
    fun `a three-point account is drawn on three points`() {
        val draft = loadedDraft(score = 2.0, format = "POINT_3")

        assertEquals(3, draft.scoreMax)
        assertEquals(2, ScoreScale(draft.scoreMax, draft.scoreStep).steps)
    }

    @Test
    fun `an integer ten-point account snaps to whole points`() {
        // POINT_10 is the integer format; POINT_10_DECIMAL exists precisely
        // because of that. A slider that offered 8.3 here would be offering a
        // value the server refuses.
        val scale = ScoreScale(10, ScoreFormat.POINT_10.step)

        assertEquals(0, scale.steps)
        assertEquals(9.0, scale.quantise(8.5), 0.0)
    }

    @Test
    fun `an unknown format keeps the old inference, with halves reachable`() {
        // A save response has no `Viewer`, and a caller that never went through
        // the repository has no format either.
        assertEquals(100, loadedDraft(score = 85.0).scoreMax)
        assertEquals(ScoreScale(100, 1.0), scoreScaleFor(null, 85.0))

        val tenPoint = scoreScaleFor(null, 8.5)
        assertEquals(10, tenPoint.max)
        assertEquals(0.5, tenPoint.step, 0.0)
        assertEquals(19, tenPoint.steps)
        assertEquals(8.5, tenPoint.quantise(8.5), 0.0)
    }

    @Test
    fun `a title with no entry yet still carries the account's scale`() {
        // The first-time case is the one that cannot be inferred from a value at
        // all: there is no score, so the old code always chose ten.
        val draft = loadedDraft(score = null, format = "POINT_100")

        assertEquals(0.0, draft.score, 0.0)
        assertEquals(100, draft.scoreMax)
        assertEquals(ScoreFormat.POINT_100, draft.scoreFormat)
    }

    @Test
    fun `a format this build has never heard of falls back rather than crashing`() {
        assertNull(ScoreFormat.fromApi("SOMETHINGNEW"))
        assertNull(ScoreFormat.fromApi(null))
        // Case is the server's, not ours.
        assertEquals(ScoreFormat.POINT_100, ScoreFormat.fromApi("point_100"))
        // Unknown format, a score below ten: the old inference, still usable.
        assertEquals(10, loadedDraft(score = 8.0, format = "SOMETHINGNEW").scoreMax)
    }

    @Test
    fun `a quantised score can never leave the scale and keeps its decimals clean`() {
        val half = ScoreScale(10, 0.5)
        val tenth = ScoreScale(10, 0.1)

        assertEquals(0.0, half.quantise(-4.0), 0.0)
        assertEquals(10.0, half.quantise(11.0), 0.0)
        assertEquals(10.0, half.quantise(10.0), 0.0)
        assertEquals(9.5, half.quantise(9.4), 0.0)
        // Floating point debris would be printed by the row above the slider.
        assertEquals("8.3", tenth.quantise(8.3).toString())
    }

    @Test
    fun `the entry query asks for the account's score format`() {
        val query = GraphQLQueries.GET_MEDIA_LIST_ENTRY

        assertTrue(
            "the score slider cannot pick a scale without Viewer.mediaListOptions.scoreFormat",
            query.contains("mediaListOptions") && query.contains("scoreFormat")
        )
        assertTrue("loading an entry is still a read", query.trimStart().startsWith("query"))
    }

    @Test
    fun `the score format arrives on the entry the editor loads`() {
        val responseType = Types.newParameterizedType(
            GraphQLResponse::class.java,
            MediaListEntryData::class.java
        )
        val data = moshi
            .adapter<GraphQLResponse<MediaListEntryData>>(responseType)
            .fromJson(
                """
                {
                  "data": {
                    "Viewer": { "mediaListOptions": { "scoreFormat": "POINT_100" } },
                    "MediaList": { "id": 4, "mediaId": 21, "status": "CURRENT", "score": 8 }
                  }
                }
                """.trimIndent()
            )!!
            .data!!

        assertEquals("POINT_100", data.viewer!!.mediaListOptions!!.scoreFormat)

        val entry = data.entry!!.copy(scoreFormat = data.viewer!!.mediaListOptions!!.scoreFormat)
        assertEquals(100, entry.toDraft().scoreMax)
    }
}
