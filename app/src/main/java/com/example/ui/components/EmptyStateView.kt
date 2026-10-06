package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.expressive.ExpressiveEmptyOrb
import com.example.ui.components.expressive.ExpressivePrimaryButton
import com.example.ui.theme.AniSequelTheme

@Composable
fun EmptyStateView(
    isSearching: Boolean,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 40.dp)
            .testTag("empty_state_view"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // A Material 3 Expressive polygon rather than a plain circle. It is the one
        // moment the screen has nothing to show, so it is the one place a
        // non-circular silhouette earns its keep.
        ExpressiveEmptyOrb(
            icon = if (isSearching) AppVectorIcons.Search else AppVectorIcons.AnimeSparkle,
            modifier = Modifier,
            containerColor = if (isSearching) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                AniSequelTheme.statusColors.successContainer
            },
            iconTint = if (isSearching) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                AniSequelTheme.statusColors.success
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearching) "Nothing matches those filters" else "All caught up!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearching) {
                "Try a different search term, or reset the filters to see everything you are missing."
            } else {
                "None of your completed anime has a missed sequel. You are completely caught up across all franchises!"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (isSearching) {
            Spacer(modifier = Modifier.height(20.dp))
            ExpressivePrimaryButton(
                text = "Reset filters",
                onClick = onResetFilters,
                icon = AppVectorIcons.Close,
                modifier = Modifier.testTag("reset_filters_button")
            )
        }
    }
}