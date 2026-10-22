package com.anisequel.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.data.network.AniListErrorKind
import com.anisequel.data.network.ApiErrorCopy
import com.anisequel.data.network.friendlyCopy
import com.anisequel.data.network.isConnectionTrouble
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.viewmodel.ApiStatusUi
import kotlin.random.Random

/**
 * The whole "AniList is not answering" screen, in one place.
 *
 * ## Why this is shared
 *
 * The dashboard and a user profile both had their own copy of the same column -
 * warning icon, "AniList connection error", the raw exception message, one
 * button - and that is why the app had two slightly different ways of saying
 * one thing. This is now the dashboard's version: the heartbreak, the status
 * strip, the joke and the "go touch grass" exit all live here, and the Settings
 * dialog borrows the same artwork. The Profile and Library pages deliberately
 * do *not* use it - see
 * [com.anisequel.ui.screens.profile.ProfileErrorView] for why the same failure
 * is a sentence there rather than a scene.
 *
 * ## What "go touch grass" actually does
 *
 * It is not a link out and it does not close the app: both would be a trick at
 * the exact moment the reader cannot tell whether their action broke something.
 * It swaps the screen for a calmer, self-aware note and offers the way back.
 * That is the joke and the off-ramp at once - "retry forever" and "walk away for
 * a minute" are the only two honest options during an outage, and this is the
 * second one with a door back.
 */
@Composable
fun ApiFailureBody(
    kind: AniListErrorKind?,
    message: String,
    isAuthError: Boolean,
    canRetry: Boolean,
    status: ApiStatusUi,
    onCheckStatus: () -> Unit,
    onRetry: () -> Unit,
    onSignInAgain: () -> Unit,
    modifier: Modifier = Modifier,
    retryTestTag: String = "retry_button",
    signInTestTag: String = "sign_in_again_button"
) {
    val copy = kind?.friendlyCopy()
    // The seed is drawn once per failure instance, not derived from the text.
    // Deriving it made the line stable under recomposition - which was the point -
    // but also stable across every *retry* of the same failure, so a reader
    // hammering Retry was shown the same joke each time: exactly the loop the pool
    // exists to avoid. `remember(kind, message)` keeps one line while this failure
    // is on screen, and a retry that passes through Loading composes a new body
    // with a new line.
    val quipSeed = remember(kind, message) { Random.nextInt() }
    val quip = copy?.quipFor("${kind?.name.orEmpty()}:$message:$quipSeed")
    // A broken heart for a connection that failed; a warning for an answer we
    // did not like (dead token, missing profile). Animating heartbreak at a
    // 404 would be a lie about what happened.
    val brokenHeart = !isAuthError && kind.isConnectionTrouble()
    // The strip may be checked while this screen is up. If a live probe says
    // AniList answers, the heart has to stop being broken there and then: the
    // glue animation is the reward for the check, and leaving a red cracked
    // heart beside a green "reachable" strip would be the screen contradicting
    // itself. The failure is still on screen - a retry is what reloads it - so
    // the copy changes with the scene rather than pretending the load worked.
    val healedByCheck = brokenHeart && status.reachability.isReachable
    val liveCopy = if (healedByCheck) status.reachability.friendlyCopy() else null
    var touchingGrass by rememberSaveable(kind, message) { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .widthIn(max = 440.dp)
            // A broken heart, a headline, a status strip and two buttons do not
            // fit a landscape phone at once. The column scrolls rather than
            // clipping the retry button - the one control the reader came for.
            .verticalScroll(rememberScrollState())
            .testTag("api_failure_body"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (touchingGrass) {
            GrassInterlude(
                status = status,
                onCheckStatus = onCheckStatus,
                onBack = {
                    touchingGrass = false
                    onRetry()
                },
                onNotNow = { touchingGrass = false }
            )
        } else {
            FailureArtwork(brokenHeart = brokenHeart, healedByCheck = healedByCheck)
            FailureMessage(
                copy = copy,
                liveCopy = liveCopy,
                isAuthError = isAuthError,
                healedByCheck = healedByCheck,
                message = message,
                quip = quip
            )
            Spacer(modifier = Modifier.height(20.dp))
            ApiStatusCard(status = status, onCheckAgain = onCheckStatus)
            Spacer(modifier = Modifier.height(20.dp))
            FailureActions(
                isAuthError = isAuthError,
                canRetry = canRetry,
                retryTestTag = retryTestTag,
                signInTestTag = signInTestTag,
                onRetry = onRetry,
                onSignInAgain = onSignInAgain,
                onTouchGrass = { touchingGrass = true }
            )
        }
    }
}

/**
 * The picture above the words: a heart that broke, a heart that just got glued,
 * or a warning for a failure that is not about reachability.
 *
 * Split out of [ApiFailureBody] because choosing it is three nested conditions,
 * and the screen's own body reads better as "artwork, words, strip, choices".
 */
@Composable
private fun FailureArtwork(brokenHeart: Boolean, healedByCheck: Boolean) {
    if (!brokenHeart) {
        Icon(
            imageVector = AppVectorIcons.Warning,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier.size(52.dp)
        )
        Spacer(modifier = Modifier.height(16.dp))
        return
    }
    if (healedByCheck) HeartRepairScene() else HeartbreakScene()
}

/**
 * The headline, the explanation and the joke - including the swap that happens
 * when a live check turns green while this screen is still showing the old
 * failure. [liveCopy] is non-null only in that case, so the screen stops
 * contradicting itself without pretending the load itself succeeded.
 */
@Composable
private fun FailureMessage(
    copy: ApiErrorCopy?,
    liveCopy: ApiErrorCopy?,
    isAuthError: Boolean,
    healedByCheck: Boolean,
    message: String,
    quip: String?
) {
    Text(
        text = liveCopy?.headline ?: copy?.headline
            ?: if (isAuthError) "Session expired" else "AniList connection error",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = if (healedByCheck) {
            "The test request came back, so AniList is answering again. " +
                "This screen is the old failure - tap Try again to reload it."
        } else {
            copy?.detail ?: message
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    val shownQuip = liveCopy?.quipFor("healed:$message") ?: quip
    if (shownQuip != null) {
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = shownQuip,
            style = MaterialTheme.typography.bodySmall,
            fontStyle = FontStyle.Italic,
            color = if (healedByCheck) {
                AniSequelTheme.statusColors.success
            } else {
                MaterialTheme.colorScheme.tertiary
            },
            textAlign = TextAlign.Center
        )
    }
}

/**
 * One honest choice, plus the off-ramp. A dead session gets "Sign in again";
 * everything else that can be retried gets "Try again" and "Go touch grass".
 */
@Composable
private fun FailureActions(
    isAuthError: Boolean,
    canRetry: Boolean,
    retryTestTag: String,
    signInTestTag: String,
    onRetry: () -> Unit,
    onSignInAgain: () -> Unit,
    onTouchGrass: () -> Unit
) {
    if (isAuthError) {
        Button(
            onClick = onSignInAgain,
            modifier = Modifier
                .testTag(signInTestTag)
                .bouncyPress(pressedScale = 0.96f)
        ) {
            Icon(
                imageVector = AppVectorIcons.Login,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Sign in again")
        }
    } else if (canRetry) {
        Button(
            onClick = onRetry,
            modifier = Modifier
                .testTag(retryTestTag)
                .bouncyPress(pressedScale = 0.96f)
        ) {
            Text("Try again")
        }
        TextButton(
            onClick = onTouchGrass,
            modifier = Modifier.testTag("touch_grass_button")
        ) {
            Icon(
                imageVector = AppVectorIcons.Grass,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.size(8.dp))
            Text("Go touch grass")
        }
    }
}

/**
 * The "go outside" card.
 *
 * Kept in this file because it is the same failure wearing a different hat, and
 * because the two halves of the joke have to agree on the status strip: leaving
 * the screen does not make AniList wake up, and pretending the check no longer
 * matters would be the one dishonest thing here.
 */
@Composable
private fun GrassInterlude(
    status: ApiStatusUi,
    onCheckStatus: () -> Unit,
    onBack: () -> Unit,
    onNotNow: () -> Unit
) {
    Icon(
        imageVector = AppVectorIcons.Grass,
        contentDescription = null,
        tint = AniSequelTheme.statusColors.success,
        modifier = Modifier.size(64.dp)
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
        text = "Go touch grass",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = "Fair enough. AniList will still be here when you get back - the list is " +
            "patient. The grass, on the other hand, is not going to touch itself.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(10.dp))
    Text(
        text = "We will keep the heart warm. Take your time.",
        style = MaterialTheme.typography.bodySmall,
        fontStyle = FontStyle.Italic,
        color = MaterialTheme.colorScheme.tertiary,
        textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(20.dp))
    ApiStatusCard(status = status, onCheckAgain = onCheckStatus)
    Spacer(modifier = Modifier.height(20.dp))
    Button(
        onClick = onBack,
        modifier = Modifier
            .testTag("back_from_grass_button")
            .bouncyPress(pressedScale = 0.96f)
    ) {
        Text("I'm back - try again")
    }
    TextButton(onClick = onNotNow) {
        Text("Not now")
    }
}
