package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.viewmodel.UserOverviewUiState
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The banner, avatar, name, core stats and account join/update dates.
 *
 * Provides a crisp profile header with balanced 4-metric overview:
 * Anime, Manga, Episodes watched, and Mean Score.
 */
@Composable
fun ProfileHeaderCard(
    state: UserOverviewUiState.Success,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth
) {
    Column(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("profile_header")
    ) {
        // Banner Container with increased height, layered multi-stop scrim, and overlapping avatar layout
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 36.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .clip(MaterialTheme.shapes.extraLarge)
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                val banner = state.bannerUrl
                if (banner != null) {
                    AsyncImage(
                        model = banner,
                        contentDescription = "Profile Banner",
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        placeholder = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
                        error = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
                        fallback = rememberVectorPainter(AppVectorIcons.AnimeSparkle)
                    )
                    // Multi-stop gradient for readable contrast and seamless blend with surface
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.verticalGradient(
                                    0.0f to Color.Black.copy(alpha = 0.20f),
                                    0.45f to Color.Black.copy(alpha = 0.25f),
                                    0.75f to Color.Black.copy(alpha = 0.50f),
                                    1.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.95f),
                                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f),
                                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f)
                                    )
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.AnimeSparkle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.45f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
            }

            // Overlapping Avatar anchored at the bottom-start of the banner (rounded squircle, not circle)
            AsyncImage(
                model = state.avatarUrl,
                contentDescription = "${state.username}'s Avatar",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp)
                    .size(86.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .border(3.5.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(AppVectorIcons.SequelJump),
                error = rememberVectorPainter(AppVectorIcons.SequelJump),
                fallback = rememberVectorPainter(AppVectorIcons.SequelJump)
            )
        }

        Spacer(modifier = Modifier.height(2.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = state.username,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = if (state.isDemoMode) "AniSequel Demo Profile" else "AniList Member",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Normal,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Aligned metadata rows with clean vector icons
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (state.updatedAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppVectorIcons.Schedule,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Updated ${formatRelativeSeconds(state.updatedAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (state.createdAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppVectorIcons.Calendar,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Joined ${formatJoinYear(state.createdAt)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Two prominent Anime & Manga cards matching reference layout
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HeaderStat(
                label = "Anime",
                value = state.animeCount.toString(),
                icon = AppVectorIcons.Tv,
                modifier = Modifier.weight(1f)
            )
            HeaderStat(
                label = "Manga",
                value = state.mangaCount.toString(),
                icon = AppVectorIcons.SourceBook,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun HeaderStat(
    label: String,
    value: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}


