package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.PannableBackdrop
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.viewmodel.UserOverviewUiState

/**
 * The banner, avatar, name, core stats and account join/update dates.
 *
 * ## What changed, and why
 *
 * The banner was 180dp with a scrim that faded to the page colour across its
 * whole height, so the artwork it exists to show was mostly hidden behind the
 * gradient meant to keep text legible over it. It is now a 200dp hero with the
 * scrim weighted to the bottom two-thirds: the top of the image reads as art, the
 * bottom reads as a surface for the identity line, and the two meet without the
 * hard band a single-stop gradient leaves.
 *
 * The avatar sits on the banner's bottom edge as a rounded squircle with a
 * surface ring *and* a hairline outline - the ring alone is invisible on a dark
 * banner with a dark theme, which is how the avatar used to lose its edge. The
 * join/update dates moved from plain rows into the same capsule treatment the
 * rest of the app uses for metadata, so the header has one visual language rather
 * than two.
 *
 * The two stat tiles keep their prominence but gain a coloured icon chip, so
 * Anime and Manga are distinguishable at a glance without reading the labels.
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
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Room for the half of the avatar that hangs below the banner, and
                // a little air before the username.
                .padding(bottom = 46.dp)
        ) {
            Banner(state = state)

            AsyncImage(
                model = state.avatarUrl,
                contentDescription = "${state.username}'s Avatar",
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // Nudged down so the avatar sits *on* the banner's bottom edge
                    // rather than flush inside it - the overlap is what ties the
                    // portrait to the art behind it instead of reading as two
                    // stacked blocks.
                    .offset(y = 22.dp)
                    .padding(start = 16.dp)
                    .size(92.dp)
                    // Not on the corner scale, and deliberately so.
                    //
                    // The sweep that put every surface radius onto
                    // `ExpressiveShapes` walked this too and moved the avatar from
                    // 24dp to the scale's 28dp. That is the wrong kind of change:
                    // a portrait is not a card, and its radius is a proportion of
                    // its own 92dp box (24/92) rather than a step on a family. At
                    // 28 it reads as a different shape sitting on the banner
                    // rather than the same one sitting on it.
                    .clip(RoundedCornerShape(24.dp))
                    .border(4.dp, MaterialTheme.colorScheme.surface, RoundedCornerShape(24.dp))
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(AppVectorIcons.SequelJump),
                error = rememberVectorPainter(AppVectorIcons.SequelJump),
                fallback = rememberVectorPainter(AppVectorIcons.SequelJump)
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        Text(
            text = state.username,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(6.dp))

        IdentityMetaRow(state = state)

        Spacer(modifier = Modifier.height(16.dp))

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

        Spacer(modifier = Modifier.height(10.dp))

        HeaderSummaryStrip(state = state)
    }
}

/**
 * The three derived numbers: episodes, time watched and mean score.
 *
 * Deliberately a strip rather than three more tiles. The two tiles above answer
 * "how many lists", which is a fact about the account; these answer "how much
 * did it amount to", which is a fact about the person's time - so they are one
 * quieter row rather than three more boxes competing with the head of the page.
 * The values go through the same formatters the Stats tab uses, so a header that
 * says "1d 5h" and a chart that says "1d 5h" are the same computation.
 */
@Composable
private fun HeaderSummaryStrip(state: UserOverviewUiState.Success) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SummaryCell(
                value = state.episodesWatched.toString(),
                label = "Episodes",
                modifier = Modifier.weight(1f)
            )
            SummaryDivider()
            SummaryCell(
                value = formatWatchTime(state.minutesWatched),
                label = "Time",
                modifier = Modifier.weight(1f)
            )
            SummaryDivider()
            SummaryCell(
                value = state.meanScore?.takeIf { it > 0.0 }?.let { formatMeanScore(it) } ?: "-",
                label = "Mean Score",
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun SummaryCell(value: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun SummaryDivider() {
    Box(
        modifier = Modifier
            .height(28.dp)
            .width(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/**
 * The banner artwork with its scrim.
 *
 * A cover-less account still gets something worth looking at: the themed
 * gradient and the sparkle mark, rather than the flat rectangle a null URL used
 * to leave behind.
 */
@Composable
private fun Banner(state: UserOverviewUiState.Success, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(260.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        val banner = state.bannerUrl
        if (banner != null) {
            PannableBackdrop(
                model = banner,
                contentDescription = "Profile Banner"
            )
            // Weighted toward the bottom so the artwork stays visible at the top
            // while the identity line below has a surface to sit on.
            //
            // The stops are `Copy` of black at a *low* alpha above the midpoint
            // rather than the previous 0.10/0.28: a scrim that darkens a banner the
            // reader can now drag sideways darkens whichever part of the picture is
            // currently on screen, so a heavy one darkens different artwork
            // depending on where they stopped scrolling.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.verticalGradient(
                            0.0f to Color.Black.copy(alpha = 0.06f),
                            0.40f to Color.Transparent,
                            0.72f to Color.Black.copy(alpha = 0.22f),
                            1.0f to MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
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
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.90f),
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
                    modifier = Modifier.size(52.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun IdentityMetaRow(state: UserOverviewUiState.Success) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetaChip(
            icon = AppVectorIcons.Star,
            text = if (state.isDemoMode) "AniSequel Demo Profile" else "AniList Member",
            emphasised = true
        )
        state.updatedAt?.let {
            MetaChip(
                icon = AppVectorIcons.Schedule,
                text = "Updated ${formatRelativeSeconds(it)}"
            )
        }
        state.createdAt?.let {
            MetaChip(
                icon = AppVectorIcons.Calendar,
                text = "Joined ${formatJoinYear(it)}"
            )
        }
    }
}

/**
 * One piece of header metadata as a capsule.
 *
 * The emphasised variant is the membership line, tinted with `primary` so the
 * identity reads before the dates - the same hierarchy the old stacked rows had,
 * without the two lines of vertical space they cost.
 */
@Composable
private fun MetaChip(
    icon: ImageVector,
    text: String,
    emphasised: Boolean = false
) {
    val container = if (emphasised) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (emphasised) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = content,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}
