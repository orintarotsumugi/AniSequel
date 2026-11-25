package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.ProfileActivity
import com.anisequel.ui.screens.profile.activityDetail
import com.anisequel.ui.screens.profile.stripProgressMarker
import org.junit.Assert.assertEquals
import org.junit.Test

class ActivityDetailFormattingTest {

    @Test
    fun `an unknown episode total is not rendered as zero`() {
        val activity = ProfileActivity(
            id = 1,
            progress = "4",
            media = MediaNode(
                id = 1,
                type = "ANIME",
                episodes = 0,
                title = MediaTitle(userPreferred = "Show")
            )
        )

        assertEquals("Episode 4", activityDetail(activity))
    }

    @Test
    fun `an unknown chapter total is not rendered as zero`() {
        val activity = ProfileActivity(
            id = 2,
            progress = "4",
            media = MediaNode(
                id = 2,
                type = "MANGA",
                chapters = 0,
                title = MediaTitle(userPreferred = "Book")
            )
        )

        assertEquals("Chapter 4", activityDetail(activity))
    }

    /**
     * AniList's `progress` is free text, and clients fill it with their own
     * abbreviation. The card supplies the medium's word, so the marker that is
     * already there has to come off first - the old `"ep"` -> `"ch"` rewrite
     * turned "Ep 7" into "Ch 7" and then prefixed it again, which is where
     * "Chapter Ch 7" came from.
     */
    @Test
    fun `a progress marker is stripped once, not prefixed twice`() {
        assertEquals("7", stripProgressMarker("7"))
        assertEquals("7", stripProgressMarker("Episode 7"))
        assertEquals("7", stripProgressMarker("Ep. 7"))
        assertEquals("12", stripProgressMarker("Ch 12"))
        assertEquals("7 of 24", stripProgressMarker("Episode 7 of 24"))
    }

    @Test
    fun `an abbreviated line reads as one episode or chapter`() {
        val anime = ProfileActivity(
            id = 3,
            progress = "Ep. 7",
            media = MediaNode(
                id = 3,
                type = "ANIME",
                episodes = 24,
                title = MediaTitle(userPreferred = "Show")
            )
        )
        val manga = ProfileActivity(
            id = 4,
            progress = "Ch 12",
            media = MediaNode(
                id = 4,
                type = "MANGA",
                chapters = 0,
                title = MediaTitle(userPreferred = "Book")
            )
        )

        assertEquals("Episode 7 of 24", activityDetail(anime))
        assertEquals("Chapter 12", activityDetail(manga))
    }
}
