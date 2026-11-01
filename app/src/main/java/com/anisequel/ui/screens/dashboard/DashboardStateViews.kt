package com.anisequel.ui.screens.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.ApiFailureBody
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.ShimmerCard
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.viewmodel.ApiStatusUi
import com.anisequel.ui.viewmodel.DashboardUiState

@Composable
fun DashboardLoadingView(
    message: String,
    maxWidth: Dp,
    modifier: Modifier = Modifier
) {
    // One shimmer driver for the whole list rather than one per card. Five
    // `rememberInfiniteTransition`s would mean five independent sweeps running
    // over the same stack; hoisting it also phase-locks them, so it reads as one
    // highlight travelling down the skeletons.
    val shimmerPhase = rememberShimmerPhase()

    // The skeleton mirrors the shape of the loaded page - stats, arriving row,
    // search, filters, section header, cards - instead of drawing five identical
    // cards. The old version reserved the wrong height and the wrong rhythm, so
    // the real content landed as a jump rather than a fade: a reader on a slow
    // connection saw a stack of posters and then got a stats banner and a search
    // field. Matching the layout makes the wait read as the page loading rather
    // than as a different page.
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("dashboard_loading"),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item(key = "loading_status") {
            LoadingStatus(message = message, maxWidth = maxWidth)
        }
        item(key = "stats_skeleton") {
            StatsSkeleton(maxWidth = maxWidth, phase = shimmerPhase)
        }
        item(key = "arriving_skeleton") {
            ArrivingSkeleton(maxWidth = maxWidth, phase = shimmerPhase)
        }
        item(key = "search_skeleton") {
            ShimmerBox(
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(percent = 50),
                phase = shimmerPhase
            )
        }
        item(key = "filters_skeleton") {
            FilterSkeleton(maxWidth = maxWidth, phase = shimmerPhase)
        }
        item(key = "section_header_skeleton") {
            SectionHeaderSkeleton(maxWidth = maxWidth, phase = shimmerPhase)
        }
        items(3) {
            ShimmerCard(
                modifier = Modifier.widthIn(max = maxWidth),
                phase = shimmerPhase
            )
        }
    }
}

/** The one line of text in the waiting state; everything else is a skeleton. */
@Composable
private fun LoadingStatus(message: String, maxWidth: Dp) {
    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.size(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun StatsSkeleton(maxWidth: Dp, phase: State<Float>) {
    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(2) {
            SkeletonPanel(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ShimmerBox(
                        modifier = Modifier.size(40.dp),
                        shape = MaterialTheme.shapes.small,
                        phase = phase
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        ShimmerBox(
                            modifier = Modifier.size(width = 44.dp, height = 24.dp),
                            phase = phase
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        ShimmerBox(
                            modifier = Modifier.size(width = 78.dp, height = 12.dp),
                            phase = phase
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArrivingSkeleton(maxWidth: Dp, phase: State<Float>) {
    Column(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ShimmerBox(
                modifier = Modifier.size(width = 156.dp, height = 22.dp),
                phase = phase
            )
            ShimmerBox(
                modifier = Modifier.size(width = 40.dp, height = 22.dp),
                shape = RoundedCornerShape(percent = 50),
                phase = phase
            )
        }
        Spacer(modifier = Modifier.height(12.dp))

        // Clipped rather than scrolled: this is a still frame of the real
        // horizontal strip, and the third card being cut by the screen edge is
        // what tells the reader the row continues.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clipToBounds(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(3) {
                Column {
                    ShimmerBox(
                        modifier = Modifier.size(148.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    ShimmerBox(
                        modifier = Modifier.size(width = 118.dp, height = 14.dp),
                        phase = phase
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    ShimmerBox(
                        modifier = Modifier.size(width = 84.dp, height = 12.dp),
                        phase = phase
                    )
                }
            }
        }
    }
}

@Composable
private fun FilterSkeleton(maxWidth: Dp, phase: State<Float>) {
    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        listOf(64.dp, 84.dp, 72.dp, 60.dp).forEach { width ->
            ShimmerBox(
                modifier = Modifier.size(width = width, height = 32.dp),
                shape = RoundedCornerShape(percent = 50),
                phase = phase
            )
        }
    }
}

@Composable
private fun SectionHeaderSkeleton(maxWidth: Dp, phase: State<Float>) {
    Row(
        modifier = Modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(
            modifier = Modifier.size(width = 172.dp, height = 20.dp),
            phase = phase
        )
        ShimmerBox(
            modifier = Modifier.size(width = 56.dp, height = 16.dp),
            phase = phase
        )
    }
}

/** A card-shaped skeleton container, matching the stats cards above the list. */
@Composable
private fun SkeletonPanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        content()
    }
}

@Composable
fun DashboardErrorView(
    state: DashboardUiState.Error,
    apiStatus: ApiStatusUi,
    onCheckApiStatus: () -> Unit,
    onRetry: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("dashboard_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ApiFailureBody(
            kind = state.kind,
            message = state.message,
            isAuthError = state.isAuthError,
            canRetry = state.canRetry,
            status = apiStatus,
            onCheckStatus = onCheckApiStatus,
            onRetry = onRetry,
            onSignInAgain = onSignInAgain
        )
    }
}

@Composable
fun DashboardDemoBanner(modifier: Modifier = Modifier) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = "Sample data • sign in any time to scan your own list",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
