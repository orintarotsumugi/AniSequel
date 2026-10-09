package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel

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
    sequel: MissedSequel,
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
            trailerUrl = sequel.trailerUrl
        )

        // All-Time & Seasonal Community Rankings
        AnimeRankingsSection(
            sequel = sequel
        )

        // Community Scores & Popularity Banner
        AnimeRatingsSection(
            sequel = sequel
        )

        // Production, Schedule & Native Transliterations Grid
        AnimeSpecsGrid(
            sequel = sequel,
            isDetailLoading = isDetailLoading
        )

        // Thematic Tags with Weights & Alternative Titles
        AnimeThematicTags(
            sequel = sequel
        )
    }
}
