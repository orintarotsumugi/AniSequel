package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.hasNotAired
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Whether a title counts as unaired.
 *
 * ## Why this exists
 *
 * The entry editor asked "how many episodes?" of every title, including one that
 * has not aired. For a planner entry that is the worst case: the row opens on
 * the announced episode count, invites a tap, and writes the number to AniList -
 * for a show where no episode exists. The row is now hidden instead.
 *
 * The rule is AniList's own `status`, and the part worth pinning is what it is
 * *not* allowed to treat as unaired. Both cases below were reachable ways to
 * hide the control from somebody who could see it and wanted it.
 */
class UnreleasedMediaTest {

    private fun media(status: String?) = MediaNode(id = 1, status = status)

    @Test
    fun `a title AniList has not released has not aired`() {
        assertTrue(media("NOT_YET_RELEASED").hasNotAired)
        // AniList's value is uppercase, but the field is a String off the wire and
        // this app compares it case-insensitively everywhere else. A title that
        // slipped through in lower case must not get its row back.
        assertTrue(media("not_yet_released").hasNotAired)
    }

    @Test
    fun `a cancelled title is not going to air either`() {
        assertTrue(media("CANCELLED").hasNotAired)
    }

    @Test
    fun `a title that has started, or is airing, keeps its progress row`() {
        assertFalse(media("RELEASING").hasNotAired)
        assertFalse(media("FINISHED").hasNotAired)
        assertFalse(media("HIATUS").hasNotAired)
    }

    @Test
    fun `an unknown status is not treated as unaired`() {
        // This is the one that matters. `null` is what AniList returns for media
        // it has little data on - including titles that plainly have episodes out
        // - and a missing field is not evidence that nothing has aired.
        //
        // Guessing wrong here hides the one control a user with an unaired-in-
        // our-data but very real series opened the sheet to reach.
        assertFalse(media(null).hasNotAired)

        // Likewise a status this build has never heard of: AniList adds these
        // without warning, and the same reasoning as `statusLabel` applies - the
        // entry has *something*, so it is not "no episodes yet".
        assertFalse(media("SOMETHINGNEW").hasNotAired)
    }
}