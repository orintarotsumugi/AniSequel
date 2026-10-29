package com.anisequel

import com.anisequel.domain.usecase.SocialChipAction
import com.anisequel.domain.usecase.shouldStartSocialFetch
import com.anisequel.domain.usecase.socialChipAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * When the Social tab spends a request, and when it must not.
 *
 * Both of these functions exist because the tab's trigger fires more often than the
 * API can pay for: AniList holds the app at 30 requests a minute, and one visit to
 * this tab is *two* round trips (followers and following, because `Page` accepts one
 * data field). The failures on either side of that are the ones these pin:
 *
 *  - A one-shot entry effect that only spoke from `Idle` left the tab on a spinner
 *    forever after a cancelled job or an unresolved profile id, because the one
 *    frame allowed to ask had already come and gone.
 *  - The obvious fix - ask whenever the tab composes - is one follower *and* one
 *    following request per recomposition, which rate-limits every other screen in
 *    the app.
 *  - A chip tap that restarted the job cancelled the answer to the list the reader
 *    was *not* switching to, then re-issued both requests to show a grid they were
 *    already looking at.
 *
 * So: rows on screen and a live job both mean "nothing to start", and only a
 * deliberate retry - the tab's own button, or a tap on the half that failed - means
 * "ask again".
 */
class SocialFetchPolicyTest {

    @Test
    fun `a tap on a list whose rows are in memory switches rather than fetches`() {
        assertEquals(
            SocialChipAction.Show,
            socialChipAction(hasRows = true, fetchInFlight = false, isFailedList = false)
        )
    }

    @Test
    fun `a tap during a fetch moves the spinner instead of restarting the job`() {
        // `Fetch` here is the bug: the running job answers both halves, so
        // cancelling it to re-issue both requests buys a spinner for a list that
        // was already coming.
        assertEquals(
            SocialChipAction.Retarget,
            socialChipAction(hasRows = false, fetchInFlight = true, isFailedList = false)
        )
    }

    @Test
    fun `a tap with nothing on screen and nothing coming asks`() {
        assertEquals(
            SocialChipAction.Fetch,
            socialChipAction(hasRows = false, fetchInFlight = false, isFailedList = false)
        )
    }

    @Test
    fun `the failed half is re-asked even while the state still holds rows`() {
        // The rows a failed list carries are the ones from *before* it failed.
        // Switching to them would present stale - or empty - rows as this tap's
        // answer, which is exactly what the reader tapped to find out.
        assertEquals(
            SocialChipAction.Fetch,
            socialChipAction(hasRows = true, fetchInFlight = false, isFailedList = true)
        )
    }

    @Test
    fun `the failed half beats an in-flight job too`() {
        // A job already running is answering the *other* half; this one is not on
        // its way anywhere.
        assertEquals(
            SocialChipAction.Fetch,
            socialChipAction(hasRows = true, fetchInFlight = true, isFailedList = true)
        )
    }

    @Test
    fun `entering an empty tab for a resolved profile asks once`() {
        assertTrue(
            shouldStartSocialFetch(
                hasSomeoneToFetch = true,
                hasRowsOnScreen = false,
                isFetchInFlight = false,
                isShowingFailure = false
            )
        )
    }

    @Test
    fun `a tab already showing people is not re-asked as it recomposes`() {
        assertFalse(
            shouldStartSocialFetch(
                hasSomeoneToFetch = true,
                hasRowsOnScreen = true,
                isFetchInFlight = false,
                isShowingFailure = false
            )
        )
    }

    @Test
    fun `a tab with a job on the wire is not asked again`() {
        assertFalse(
            shouldStartSocialFetch(
                hasSomeoneToFetch = true,
                hasRowsOnScreen = false,
                isFetchInFlight = true,
                isShowingFailure = false
            )
        )
    }

    @Test
    fun `a failure is left to its own retry button`() {
        // Auto-retrying on entry spends a request per visit to say the same
        // sentence, and for a dead session says it forever.
        assertFalse(
            shouldStartSocialFetch(
                hasSomeoneToFetch = true,
                hasRowsOnScreen = false,
                isFetchInFlight = false,
                isShowingFailure = true
            )
        )
    }

    @Test
    fun `a profile with no id starts nothing`() {
        // This is the half that made the tab's spinner permanent: the refused
        // request is the only thing that ever clears that state, and with nobody
        // to fetch there is no request to make. The tab reports it instead.
        assertFalse(
            shouldStartSocialFetch(
                hasSomeoneToFetch = false,
                hasRowsOnScreen = false,
                isFetchInFlight = false,
                isShowingFailure = false
            )
        )
    }
}
