package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveShapes

/**
 * Thematic tags with percentage weights and alternative franchise titles.
 * Formatted with capsule shapes and Material 3 Expressive chip styles.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnimeThematicTags(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val mediaTags = sequel.sequelMedia.tags
        ?.filter { it.isMediaSpoiler != true && !it.name.isNullOrBlank() }
        ?.sortedByDescending { it.rank ?: 0 }
        ?.take(8)
    val fallbackTags = sequel.topTags
    val synonyms = sequel.synonyms

    val hasTags = !mediaTags.isNullOrEmpty() || fallbackTags.isNotEmpty()
    val hasSynonyms = synonyms.isNotEmpty()

    if (!hasTags && !hasSynonyms) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spec_tags_section"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Thematic Tags
        if (hasTags) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.Tag,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Thematic Tags",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (!mediaTags.isNullOrEmpty()) {
                    mediaTags.forEach { tag ->
                        Surface(
                            shape = ExpressiveShapes.pill,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = tag.name ?: "",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (tag.rank != null && tag.rank > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = "${tag.rank}%",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                } else {
                    fallbackTags.forEach { tag ->
                        Surface(
                            shape = ExpressiveShapes.pill,
                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }

        // Alternative Titles
        if (hasSynonyms) {
            Text(
                text = "Alternative Titles & Aliases",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                synonyms.take(6).forEach { synonym ->
                    Surface(
                        shape = ExpressiveShapes.pill,
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Text(
                            text = synonym,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }
        }
    }
}
