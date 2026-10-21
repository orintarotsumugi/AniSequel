package com.anisequel.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.network.ApiReachability
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.viewmodel.ApiStatusUi

/**
 * "Can we reach AniList right now?", answered in one strip.
 *
 * ## Why this is not just an icon
 *
 * A status light with no explanation is a puzzle: a red dot beside a working
 * Wi-Fi symbol makes the reader think the app is broken, and a green dot during
 * a timeout makes them think their connection is. The strip therefore always
 * pairs the verdict with the evidence ([ApiReachability.detail]) and offers the
 * check itself, so "unknown" is a thing the reader can resolve rather than a
 * dead end.
 *
 * ## Why the manual check is a `TextButton`
 *
 * The strip is information first and an action second. A filled button here
 * would compete with the screen's real primary action - Sign in again or Try
 * again - which are the things that actually get the reader moving. "Check" is
 * a diagnostic, so it stays quiet.
 */
@Composable
fun ApiStatusCard(
    status: ApiStatusUi,
    onCheckAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    val tone = status.reachability.tone()

    Surface(
        shape = MaterialTheme.shapes.medium,
        color = tone.container,
        modifier = modifier
            .fillMaxWidth()
            .testTag("api_status_card")
    ) {
        Row(
            modifier = Modifier.padding(start = 14.dp, top = 10.dp, end = 6.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = if (status.reachability.isReachable) {
                    AppVectorIcons.SignalOn
                } else {
                    AppVectorIcons.SignalOff
                },
                contentDescription = null,
                tint = tone.content,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = status.reachability.label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = tone.content
                )
                Text(
                    text = status.reachability.detail(status.latencyMillis),
                    style = MaterialTheme.typography.bodySmall,
                    color = tone.content.copy(alpha = 0.82f)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            if (status.isChecking) {
                // A spinner in the same slot as the button, so the strip does
                // not change width when the check starts or finishes.
                ExpressiveLoadingIndicator(
                    modifier = Modifier
                        .padding(horizontal = 10.dp)
                        .size(18.dp),
                    color = tone.content
                )
            } else {
                TextButton(
                    onClick = onCheckAgain,
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "Check",
                        color = tone.content,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

/** One container/content pair per verdict, sourced from the app's status roles. */
private data class ApiStatusTone(val container: Color, val content: Color)

@Composable
private fun ApiReachability.tone(): ApiStatusTone {
    val status = AniSequelTheme.statusColors
    return when (this) {
        ApiReachability.ONLINE -> ApiStatusTone(status.successContainer, status.onSuccessContainer)
        // "Reachable but slow" is a warning, not a success: the request will
        // probably work, but the reader should expect a wait.
        ApiReachability.SLOW,
        ApiReachability.RATE_LIMITED -> ApiStatusTone(status.warningContainer, status.onWarningContainer)

        ApiReachability.OFFLINE,
        ApiReachability.DOWN,
        ApiReachability.NO_ANSWER -> ApiStatusTone(
            container = MaterialTheme.colorScheme.errorContainer,
            content = MaterialTheme.colorScheme.onErrorContainer
        )

        ApiReachability.UNKNOWN -> ApiStatusTone(
            container = MaterialTheme.colorScheme.surfaceContainerHighest,
            content = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
