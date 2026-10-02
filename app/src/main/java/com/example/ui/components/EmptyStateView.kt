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
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(
                    if (isSearching) {
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    } else {
                        AniSequelTheme.statusColors.successContainer
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isSearching) AppVectorIcons.Search else AppVectorIcons.CheckCircle,
                contentDescription = null,
                tint = if (isSearching) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    AniSequelTheme.statusColors.success
                },
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = if (isSearching) "Nothing matches those filters" else "All caught up",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = if (isSearching) {
                "Try a different search term, or reset the filters to see everything you are missing."
            } else {
                "Nothing in your completed lists has an unreleased sequel. That is genuinely rare - enjoy it."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        if (isSearching) {
            Spacer(modifier = Modifier.height(20.dp))
            Button(
                onClick = onResetFilters,
                modifier = Modifier.testTag("reset_filters_button")
            ) {
                Text("Clear search and filters")
            }
        }
    }
}