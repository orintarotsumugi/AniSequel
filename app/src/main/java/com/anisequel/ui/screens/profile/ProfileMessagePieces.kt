package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.viewmodel.MessageFilter

/**
 * What the Messages tab draws when a list is not on screen.
 *
 * Split from the tab for the same reason `ProfileSocialPieces` is split from
 * `ProfileSocialTab`: the tab's behaviour - what a filter tap loads, when the
 * next page is asked for, how a direction is decided - is one reason to change
 * a file, and these little state bodies are another.
 */

@Composable
internal fun MessagesLoading() {
    val phase = rememberShimmerPhase()
    Column(
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("messages_loading")
    ) {
        // The comment skeleton is the same row this card is - a face, a name and
        // two lines of text - so it is reused rather than a second, slightly
        // different grey sketch of the same thing.
        CommentRowsSkeleton(phase = phase)
    }
}

@Composable
internal fun MessagesError(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp)
            .testTag("messages_error"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .bouncyPress()
                .testTag("messages_retry_button")
        ) {
            Text("Try again")
        }
    }
}

@Composable
internal fun MessagesEmpty(filter: MessageFilter) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp)
            .testTag("messages_empty"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            // The envelope the Messages chip is named for, so the empty state and
            // the control that opened it are visibly the same thing.
            icon = AppVectorIcons.ProfileMessages,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = when (filter) {
                MessageFilter.ALL -> "No messages yet"
                MessageFilter.RECEIVED -> "No messages received"
                MessageFilter.SENT -> "No messages sent"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = when (filter) {
                MessageFilter.ALL -> "Private messages between you and other AniList accounts show up here."
                MessageFilter.RECEIVED -> "Anything another account sends you will appear here."
                MessageFilter.SENT -> "Anything you send from AniList will appear here."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .widthIn(max = 300.dp)
                .padding(horizontal = 16.dp)
        )
    }
}

/**
 * One half of the merged view failing while the other's rows are on screen.
 *
 * Drawn as a compact row, not the error page: the reader has messages to read,
 * and replacing them with a failure would lose the half that worked.
 */
@Composable
internal fun MessagesNotice(message: String, onRetry: () -> Unit) {
    Row(
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(start = 14.dp, top = 4.dp, end = 4.dp, bottom = 4.dp)
            .testTag("messages_source_error"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .bouncyPress()
                .testTag("messages_source_retry")
        ) {
            Text("Retry")
        }
    }
}

@Composable
internal fun MessagesPagingIndicator() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("messages_paging"),
        contentAlignment = Alignment.Center
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(22.dp),
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
internal fun MessagesPagingRetry(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag("messages_paging_retry"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
        TextButton(
            onClick = onRetry,
            modifier = Modifier
                .bouncyPress()
                .testTag("messages_paging_retry_button")
        ) {
            Text("Try again")
        }
    }
}

@Composable
internal fun MessagesFeedEnd() {
    Text(
        text = "You're all caught up",
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp)
            .testTag("messages_end")
    )
}
