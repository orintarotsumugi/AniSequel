package com.anisequel.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.theme.AniSequelTheme

@Composable
fun EmptyStateView(
    // Named for what actually decides the copy. It was `isSearching`, while
    // its only caller passed `filterCriteria.isNarrowing` - so a list emptied
    // by the sheet's switches told the reader to try a different search term
    // they had never typed.
    filtersActive: Boolean,
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
            icon = if (filtersActive) AppVectorIcons.Search else AppVectorIcons.AnimeSparkle,
            modifier = Modifier,
            containerColor = if (filtersActive) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                AniSequelTheme.statusColors.successContainer
            },
            iconTint = if (filtersActive) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                AniSequelTheme.statusColors.success
            }
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (filtersActive) "Nothing matches those filters" else "All caught up!",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (filtersActive) {
                "Adjust the search or the filters, or reset them to see everything you are missing."
            } else {
                "None of your completed anime has a missed sequel. You are completely caught up across all franchises!"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (filtersActive) {
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