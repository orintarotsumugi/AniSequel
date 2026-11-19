package com.anisequel

import com.anisequel.data.model.ArrivingEntry
import com.anisequel.ui.screens.dashboard.arrivalMeta
import org.junit.Assert.assertEquals
import org.junit.Test

class ArrivingMetaFormattingTest {

    private fun airing(episodes: Int?): ArrivingEntry = ArrivingEntry(
        mediaId = 1,
        mediaType = "ANIME",
        title = "Show",
        coverUrl = null,
        coverColor = null,
        status = "RELEASING",
        startDate = null,
        nextAiringAt = null,
        nextEpisodeNumber = null,
        episodes = episodes,
        progress = null
    )

    @Test
    fun `a single-episode airing count uses singular form`() {
        assertEquals("Airing · 1 ep", arrivalMeta(airing(episodes = 1)))
        assertEquals("Airing · 12 eps", arrivalMeta(airing(episodes = 12)))
    }
}
