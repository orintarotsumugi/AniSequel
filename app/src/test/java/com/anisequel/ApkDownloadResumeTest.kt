package com.anisequel

import com.anisequel.data.update.planResume
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a response is lined up with a partial download already on disk.
 *
 * ## Why this is pure logic worth pinning
 *
 * Continuing a download means asking for bytes the app already has, and the
 * answer comes back in a header nobody can see at the call site. Three outcomes
 * have to be told apart, and getting any of them wrong produces a file that is
 * still a valid zip:
 *
 *  - **206.** What was asked for. Append, and the bar starts from where it is.
 *  - **200.** The server ignored the range and is sending the whole file again.
 *    Appending would put 1 MB of the previous attempt on the front of a complete
 *    download - a file that is the right size-ish, opens as a zip, and fails at
 *    the package installer with nothing on screen to explain it.
 *  - **No partial file at all.** The ordinary first attempt, where the bytes
 *    already on disk are irrelevant.
 *
 * The third case is also why "resume when there is something to resume" cannot
 * be decided by the caller: it depends on the response as well as the disk.
 *
 * The transfer this drives lives in `com.anisequel.data.update.ApkDownloader`.
 */
class ApkDownloadResumeTest {

    @Test
    fun `a partial response continues from the bytes already on disk`() {
        val plan = planResume(requestedFrom = 1_048_576, responseCode = 206)

        assertEquals(
            "a 206 answering a range request continues at the requested offset",
            1_048_576L,
            plan.baseOffset
        )
        assertTrue("a 206 must be appended, not overwrite what is there", plan.append)
        assertFalse("a 206 means the partial file is good", plan.discardPart)
    }

    @Test
    fun `a server that ignores the range makes the partial file be thrown away`() {
        // The regression. Appending here produces a file with the same first
        // megabyte twice, which is a valid-looking zip of the wrong thing.
        val plan = planResume(requestedFrom = 1_048_576, responseCode = 200)

        assertEquals("a 200 body starts at byte zero", 0L, plan.baseOffset)
        assertFalse("the body must not be appended to the partial file", plan.append)
        assertTrue(
            "a 200 for a ranged request means the partial file does not belong " +
                "to this body, so it has to go",
            plan.discardPart
        )
    }

    @Test
    fun `a first download starts from nothing`() {
        val plan = planResume(requestedFrom = 0, responseCode = 200)

        assertEquals(0L, plan.baseOffset)
        assertFalse(plan.append)
        assertFalse(
            "with nothing on disk there is nothing to discard",
            plan.discardPart
        )
    }

    @Test
    fun `a first download served as partial is still read from the start`() {
        // 206 with no offset requested is a server answering oddly. Reading it
        // from zero is still correct; treating the empty file as a resume point
        // and appending is not.
        val plan = planResume(requestedFrom = 0, responseCode = 206)

        assertEquals(0L, plan.baseOffset)
        assertFalse(plan.append)
        assertFalse(plan.discardPart)
    }

    @Test
    fun `any other success is treated as the whole file again`() {
        // 204 and friends have no body at all; whatever the status, a body that
        // is not a 206 cannot be appended to a partial file.
        listOf(200, 203, 204).forEach { code ->
            val plan = planResume(requestedFrom = 512, responseCode = code)

            assertTrue(
                "response $code must not be appended to a partial file",
                !plan.append
            )
            assertTrue(
                "response $code means the partial file is not a prefix of this body",
                plan.discardPart
            )
        }
    }
}