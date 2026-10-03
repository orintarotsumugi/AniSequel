package com.example.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.theme.AniSequelTheme

/**
 * Modern poster component with dynamic background hue, format badge, and score indicator.
 */
@Composable
fun SequelPoster(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val coverColor = sequel.coverColor.toCoverColorOrNull()

    Box(
        modifier = modifier
            .width(104.dp)
            .height(152.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
    ) {
        // Underlying tinted container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight()
                .background(
                    if (coverColor != null) {
                        Brush.verticalGradient(listOf(coverColor, coverColor.copy(alpha = 0.45f)))
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.surfaceContainerHighest,
                                MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        )
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = AppVectorIcons.Movie,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.55f),
                modifier = Modifier.size(28.dp)
            )
        }

        // Cover artwork
        if (sequel.sequelCoverUrl != null) {
            AsyncImage(
                model = sequel.sequelCoverUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(),
                contentScale = ContentScale.Crop
            )
        }

        // Top gradient scrim for readable badge
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .fillMaxWidth(0.6f)
                .height(32.dp)
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.6f))
                    )
                )
        )

        // Format pill tag
        Surface(
            shape = RoundedCornerShape(bottomStart = 10.dp),
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.align(Alignment.TopEnd)
        ) {
            Text(
                text = sequel.format,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
            )
        }

        // High score badge
        val score = sequel.sequelMedia.averageScore
        if (score != null && score >= 75) {
            Surface(
                shape = RoundedCornerShape(topStart = 8.dp, bottomEnd = 6.dp),
                color = if (score >= 80) AniSequelTheme.statusColors.success else MaterialTheme.colorScheme.secondary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 4.dp, bottom = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = AppVectorIcons.AnimeSparkle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "$score%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

/**
 * Parses an AniList `#RRGGBB` cover colour, or null if it is missing or junk.
 *
 * `internal` rather than `private` so the parent-cover thumbnails in
 * [com.example.ui.components.cards.SequelCard] and the detail sheet can tint
 * their placeholders the same way instead of each re-implementing the parse.
 */
internal fun String?.toCoverColorOrNull(): Color? {
    val hex = this?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    if (!hex.all { it.isDigit() || it.lowercaseChar() in 'a'..'f' }) return null
    val value = hex.toLongOrNull(16) ?: return null
    return Color(0xFF000000 or value)
}
