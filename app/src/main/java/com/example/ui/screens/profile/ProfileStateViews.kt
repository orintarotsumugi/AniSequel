package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveLoadingIndicator
import com.example.ui.components.expressive.bouncyPress

/**
 * The profile screen's waiting state.
 *
 * A spinner and a sentence, not the dashboard's shimmer cards. There is nothing
 * shaped like a list to sketch: the profile is one header and a few rows that
 * change shape entirely between a person who has pinned a lot and one who has
 * pinned nothing, and a skeleton of the wrong shape would be a lie about the
 * page for the length of the fetch.
 */
@Composable
fun ProfileLoadingView(
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("profile_loading"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(44.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * The profile screen's failure state.
 *
 * A dead session offers the way back to sign-in rather than a Retry button that
 * fails identically every time - the same rule the dashboard follows, and for
 * the same reason: a retry that cannot work reads as the app not knowing what is
 * wrong, and here the user has just been told their sign-in succeeded.
 */
@Composable
fun ProfileErrorView(
    message: String,
    canRetry: Boolean,
    isAuthError: Boolean,
    onRetry: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp)
            .testTag("profile_error"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = AppVectorIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(48.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = if (isAuthError) "Session expired" else "Couldn't load this profile",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = ProfileMaxContentWidth)
        )

        Spacer(modifier = Modifier.height(24.dp))

        if (isAuthError) {
            Button(
                onClick = onSignInAgain,
                modifier = Modifier
                    .testTag("profile_sign_in_again_button")
                    .bouncyPress(pressedScale = 0.96f)
            ) {
                Icon(
                    imageVector = AppVectorIcons.Login,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Sign in again")
            }
        } else if (canRetry) {
            Button(
                onClick = onRetry,
                modifier = Modifier
                    .testTag("profile_retry_button")
                    .bouncyPress(pressedScale = 0.96f)
            ) {
                Text("Try again")
            }
        }
    }
}

/**
 * "Still counting", for the two tabs whose numbers come from the media list.
 *
 * The profile is published as soon as AniList answers and the list - one request
 * per 500 entries - is merged in afterwards, so for a second or two the Stats and
 * Library tabs have a real, complete profile and an unfinished tally.
 *
 * Rendering that as zeroes would be the worst of the options: the summary card
 * would read "0 / 0" for a person with 900 entries, and it would read as an
 * answer rather than as a wait. A spinner over the whole tab would be the other
 * wrong answer, discarding the charts that *did* arrive with the profile. This is
 * the middle: the page states clearly which part is missing and continues.
 */
@Composable
internal fun PendingDataNote(
    message: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("profile_pending_data"),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ExpressiveLoadingIndicator(
            modifier = Modifier.size(18.dp),
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}