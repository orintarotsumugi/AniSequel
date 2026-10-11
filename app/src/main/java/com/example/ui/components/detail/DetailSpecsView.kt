package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaNode
import com.example.data.model.trailerYoutubeUrl

/**
 * Material 3 Expressive Anime Info & Specs tab.
 *
 * Coordinates modular anime information panels:
 *  - [AnimeTrailerBanner]: Tactile video play banner with haptic feedback
 *  - [AnimeRankingsSection]: AniList trophy rankings and all-time badges
 *  - [AnimeRatingsSection]: Community score gauge, mean ratings, and popularity
 *  - [AnimeSpecsGrid]: Studio, source, format, duration, season, and native titles
 *  - [AnimeThematicTags]: Thematic tags with weights and alias titles
 */
@Composable
fun DetailSpecsView(
    media: MediaNode,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("detail_specs_view"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Official Trailer preview if available
        AnimeTrailerBanner(
            trailerUrl = media.trailerYoutubeUrl
        )

        // All-Time & Seasonal Community Rankings
        AnimeRankingsSection(
            media = media
        )

        // Community Scores & Popularity Banner
        AnimeRatingsSection(
            media = media
        )

        // Production, Schedule & Native Transliterations Grid
        AnimeSpecsGrid(
            media = media,
            isDetailLoading = isDetailLoading
        )

        // Thematic Tags with Weights & Alternative Titles
        AnimeThematicTags(
            media = media
        )
    }
}
