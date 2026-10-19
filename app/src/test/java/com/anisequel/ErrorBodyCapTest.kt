package com.anisequel

import com.anisequel.data.network.MAX_ERROR_BODY_BYTES
import com.anisequel.data.network.readCapped
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cap on how much of a failed response's body is ever read.
 *
 * AniList's own error envelope is tiny. A proxy's HTML error page, a
 * misconfigured CDN response, or a hostile body is not, and
 * `ResponseBody.string()` reads all of it before the classifier can decide it is
 * not JSON. On a device already low on memory during a failed request, that is
 * how an error becomes a crash.
 */
class ErrorBodyCapTest {

    @Test
    fun `an oversized response body is truncated to the cap`() {
        val body = "x".repeat(MAX_ERROR_BODY_BYTES * 3).toResponseBody()

        val text = body.readCapped()

        assertEquals(
            "the reader must never hold more than the cap",
            MAX_ERROR_BODY_BYTES,
            text?.length
        )
        assertTrue(
            "the cap is a memory-safety bound, not a suggestion",
            MAX_ERROR_BODY_BYTES <= 64 * 1024
        )
    }

    @Test
    fun `a short body is read in full`() {
        val json = """{"errors":[{"message":"Invalid token","status":400}]}"""

        assertEquals(json, json.toResponseBody().readCapped())
    }
}
