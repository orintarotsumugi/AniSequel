package com.anisequel

import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaTitle
import com.anisequel.data.model.ProfileActivity
import com.anisequel.ui.screens.profile.activityDetail
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
}
