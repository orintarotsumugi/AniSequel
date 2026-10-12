package com.example.ui.components.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import com.example.data.model.MediaNode
import com.example.data.model.durationLabel
import com.example.data.model.englishName
import com.example.data.model.formatLabel
import com.example.data.model.isManga
import com.example.data.model.nativeName
import com.example.data.model.romajiName
import com.example.data.model.seasonLabel
import com.example.data.model.sourceLabel
import com.example.data.model.studioName
import com.example.ui.components.AppVectorIcons

/**
 * Grid of core anime production, schedule, and title transliteration specifications.
 * Employs clean 2x2 cards with vector icons and tactile press feedback.
 */
@Composable
fun AnimeSpecsGrid(
    media: MediaNode,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .testTag("spec_specs_grid"),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        val isManga = media.isManga

        // Row 1: Studio / Publisher & Source.
        //
        // `IntrinsicSize.Min` on both rows because two cards of equal weight are
        // not automatically equal height: each sizes to its own content, and a
        // longer studio name or a two-line value left one tile visibly taller
        // than its neighbour. The cards then `fillMaxHeight`, so each row reads as
        // one band rather than two stacked boxes.
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            SpecCard(
                icon = if (isManga) AppVectorIcons.FavouriteManga else AppVectorIcons.Studio,
                title = if (isManga) "Publisher / Serialization" else "Animation Studio",
                value = media.studioName ?: if (isDetailLoading) "Loading..." else if (isManga) "Manga Serialization" else "TBA",
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                icon = AppVectorIcons.SourceBook,
                title = "Source Material",
                value = media.sourceLabel ?: if (isDetailLoading) "Loading..." else if (isManga) "Original" else "Original / Other",
                modifier = Modifier.weight(1f)
            )
        }

        // Row 2: Duration / Chapters & Season/Year / Release Date
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.height(IntrinsicSize.Min)
        ) {
            SpecCard(
                icon = AppVectorIcons.Schedule,
                title = if (isManga) "Format & Chapters" else "Duration / Format",
                value = if (isManga) {
                    val count = listOfNotNull(
                        media.chapters?.let { "$it Chapters" },
                        media.volumes?.let { "$it Vols" }
                    ).joinToString(" · ")
                    if (count.isNotBlank()) "$count · ${media.formatLabel}" else media.formatLabel
                } else {
                    buildDurationFormatString(media, isDetailLoading)
                },
                modifier = Modifier.weight(1f)
            )
            SpecCard(
                icon = AppVectorIcons.Calendar,
                title = if (isManga) "Release Date" else "Season & Year",
                value = if (isManga) (media.startDate?.formatted ?: "TBA") else (media.seasonLabel ?: "TBA"),
                modifier = Modifier.weight(1f)
            )
        }

        // Titles breakdown (Native, Romaji, English)
        val native = media.nativeName
        val romaji = media.romajiName
        val english = media.englishName

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
    // No press animation, and it used to have one at 0.98f.
    //
    // Nothing here is clickable - a studio name, a format, a duration - so the
    // modifier was a promise the card never kept: touching it depressed it and
    // then sprang it back with no result. Worse on a scroll, because the old
    // press loop held the depression for as long as a finger stayed down, so
    // dragging past a spec tile made it pulse under the thumb. A surface that
    // cannot be pressed should look like one.
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxHeight()
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
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    // One line, so the two cards in a row keep matching heights and
                    // the label cannot wrap "Publisher / Serialization" into a
                    // second line that the neighbouring tile does not have.
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                // The value is the reason the tile exists, and it was the thing
                // being cut. Two lines covers "1200 Ch · 14 Vols · Manga" and a
                // long studio name at large font scales, which one line could not.
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun buildDurationFormatString(media: MediaNode, isDetailLoading: Boolean): String {
    val duration = media.durationLabel
    val format = media.formatLabel
    return when {
        duration != null -> "$format · $duration"
        isDetailLoading -> "Loading..."
        else -> format
    }
}
