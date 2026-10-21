package com.anisequel.ui.screens.dashboard

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.ApiFailureBody
import com.anisequel.ui.components.AppVectorIcons
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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp)
            .testTag("dashboard_loading"),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        item {
            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier
                    .widthIn(max = maxWidth)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(22.dp),
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.size(12.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        items(5) {
            ShimmerCard(
                modifier = Modifier.widthIn(max = maxWidth),
                phase = shimmerPhase
            )
        }
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
