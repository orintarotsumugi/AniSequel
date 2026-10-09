package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.bouncyPress

/**
 * Grid of core anime production, schedule, and title transliteration specifications.
 * Employs clean 2x2 cards with vector icons and tactile press feedback.
 */
@Composable
fun AnimeSpecsGrid(
    sequel: MissedSequel,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spec_specs_grid"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Row 1: Studio & Source
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecCard(
                icon = AppVectorIcons.Studio,
                title = "Animation Studio",
                value = sequel.studioName ?: if (isDetailLoading) "Loading..." else "TBA",
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                icon = AppVectorIcons.SourceBook,
                title = "Source Material",
                value = sequel.source ?: if (isDetailLoading) "Loading..." else "Original / Other",
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Duration & Season/Year
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            SpecCard(
                icon = AppVectorIcons.Schedule,
                title = "Duration / Format",
                value = buildDurationFormatString(sequel, isDetailLoading),
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                icon = AppVectorIcons.Calendar,
                title = "Season & Year",
                value = sequel.airingSeason ?: "TBA",
                modifier = Modifier.weight(1f)
            )
        }

        // Titles breakdown (Native, Romaji, English)
        val native = sequel.nativeTitle
        val romaji = sequel.romajiTitle
        val english = sequel.englishTitle

        if (!native.isNullOrBlank() || !romaji.isNullOrBlank() || !english.isNullOrBlank()) {
            TitlesCard(
                native = native,
                romaji = romaji,
                english = english
            )
        }
    }
}

@Composable
private fun TitlesCard(
    native: String?,
    romaji: String?,
    english: String?,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Titles & Transliterations",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            if (!native.isNullOrBlank()) {
                TitleRow(label = "JP", value = native, isPrimary = true)
            }
            if (!romaji.isNullOrBlank()) {
                TitleRow(label = "ROMAJI", value = romaji)
            }
            if (!english.isNullOrBlank()) {
                TitleRow(label = "EN", value = english)
            }
        }
    }
}

@Composable
private fun TitleRow(
    label: String,
    value: String,
    isPrimary: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.extraSmall,
            color = if (isPrimary) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                MaterialTheme.colorScheme.surfaceContainerHigh
            }
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = if (isPrimary) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (isPrimary) FontWeight.SemiBold else FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SpecCard(
    icon: ImageVector,
    title: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.bouncyPress(pressedScale = 0.98f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildDurationFormatString(sequel: MissedSequel, isDetailLoading: Boolean): String {
    val duration = sequel.duration
    val format = sequel.format
    return when {
        duration != null -> "$format · $duration"
        isDetailLoading -> "Loading..."
        else -> format
    }
}
