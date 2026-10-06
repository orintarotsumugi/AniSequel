package com.example.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.domain.usecase.BucketTally
import com.example.domain.usecase.SegmentTally
import com.example.domain.usecase.StatusDistribution
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.theme.AniSequelTheme

/**
 * The palette the Stats tab's charts share.
 *
 * Six slots walked in order, so the first segment of the Status chart and the
 * first bar of the Format chart are the same colour - which is what makes the two
 * cards comparable at a glance. And they are the app's own roles rather than six
 * hardcoded hex values, because hardcoded ramps stop being readable on a light
 * theme with a wallpaper-derived palette.
 */
@Composable
internal fun chartSlotColor(slot: Int): Color {
    val scheme = MaterialTheme.colorScheme
    return when (slot % CHART_SLOTS) {
        0 -> scheme.primary
        1 -> scheme.secondary
        2 -> AniSequelTheme.statusColors.success
        3 -> scheme.tertiary
        4 -> AniSequelTheme.statusColors.info
        else -> scheme.primary
    }
}

private const val CHART_SLOTS = 6

/**
 * The Stats tab's card shell: an icon tile and a title.
 *
 * Every chart on that tab is this card with different content. Written once
 * because they were going to drift - four cards whose headers sat at three
 * different heights because three of them were written before the fourth.
 */
@Composable
fun ChartCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth,
    trailing: String? = null,
    content: @Composable () -> Unit
) {
    Card(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("chart_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(MaterialTheme.shapes.small)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (trailing != null) {
                    Text(
                        text = trailing,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            content()
        }
    }
}

/**
 * Status Distribution: a scrolling row of count chips and one strip beneath them.
 *
 * The strip rather than four more bars, because the question the card answers is
 * "what is this list made of" and the answer is a share of a whole. Four separate
 * bars would make the same 122-versus-108 comparison four times and hide that
 * together they are one number.
 *
 * The chips scroll horizontally instead of wrapping. A status label is one or two
 * words; wrapping would push the strip down by a whole line on one account and
 * not on the next, so the same card would be a different height for every user.
 */
@Composable
fun StatusDistributionCard(
    distribution: StatusDistribution,
    modifier: Modifier = Modifier
) {
    ChartCard(
        title = "Status Distribution",
        icon = AppVectorIcons.StatStatus,
        modifier = modifier
    ) {
        if (distribution.isEmpty) {
            EmptyChartNote("No list statuses to show")
            return@ChartCard
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            distribution.segments.forEach { SegmentChip(segment = it) }
        }

        Spacer(modifier = Modifier.height(14.dp))

        SegmentStrip(segments = distribution.segments)

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Total entries: ${distribution.total}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SegmentChip(
    segment: SegmentTally,
    modifier: Modifier = Modifier
) {
    val color = chartSlotColor(segment.slot)
    Text(
        text = "${segment.count} ${segment.label}",
        style = MaterialTheme.typography.labelLarge,
        color = color,
        maxLines = 1,
        modifier = modifier
            .clip(ExpressiveShapes.pill)
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("status_segment_chip")
    )
}

/**
 * One strip, one segment per status.
 *
 * Each segment's width is its share of the total, and every segment has a floor -
 * a status with one entry in three hundred is 0.3% of the strip, about a pixel on
 * a phone, and a zero-width segment is a segment that is not there. `weight`
 * rather than a fixed width, because a fixed width would make a 1% segment as
 * wide as a 40% one.
 */
@Composable
internal fun SegmentStrip(
    segments: List<SegmentTally>,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(14.dp)
            .clip(RoundedCornerShape(7.dp))
            .testTag("status_segment_strip")
    ) {
        segments.forEach { segment ->
            val weight by animateFloatAsState(
                targetValue = segment.share.coerceAtLeast(MIN_SEGMENT_SHARE),
                animationSpec = ExpressiveMotion.FastSpatial,
                label = "segment_${segment.slot}"
            )
            Box(
                modifier = Modifier
                    .weight(weight)
                    .fillMaxWidth()
                    .background(chartSlotColor(segment.slot))
            )
        }
    }
}

private const val MIN_SEGMENT_SHARE = 0.01f

/**
 * A horizontal bar chart: a label, a bar, and a count.
 *
 * The bar's width is [BucketTally.share] - its fraction of the largest row in the
 * same chart - computed in the use case rather than here, so every chart on the
 * tab scales the same way and a bar chart of years is directly comparable to a
 * bar chart of episodes.
 *
 * Bars below [MIN_BAR_SHARE] get that floor for the same reason segments do: one
 * entry beside a hundred still has to be visible.
 */
@Composable
fun BarChartCard(
    title: String,
    icon: ImageVector,
    rows: List<BucketTally>,
    modifier: Modifier = Modifier,
    labelWidth: Dp = 56.dp,
    subtitle: String? = null,
    trailing: String? = null,
    barColor: Color? = null
) {
    ChartCard(title = title, icon = icon, modifier = modifier, trailing = trailing) {
        if (rows.isEmpty()) {
            EmptyChartNote("Nothing recorded yet")
            return@ChartCard
        }

        rows.forEach { row ->
            BarRow(
                row = row,
                labelWidth = labelWidth,
                color = barColor ?: chartSlotColor(0)
            )
        }

        if (subtitle != null) {
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * One row of a bar chart. Shared with the score chart, which is the same row with
 * a different colour per row.
 */
@Composable
internal fun BarRow(
    row: BucketTally,
    labelWidth: Dp,
    color: Color,
    modifier: Modifier = Modifier
) {
    val width by animateFloatAsState(
        targetValue = row.share.coerceIn(MIN_BAR_SHARE, 1f),
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "bar_${row.label}"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .testTag("bar_row"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = row.label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(labelWidth)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Box(
            modifier = Modifier
                .weight(1f)
                .height(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(width)
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(color)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = row.count.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            modifier = Modifier.width(38.dp)
        )
    }
}

private const val MIN_BAR_SHARE = 0.02f

@Composable
private fun EmptyChartNote(message: String) {
    Text(
        text = message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("chart_empty_note")
    )
}