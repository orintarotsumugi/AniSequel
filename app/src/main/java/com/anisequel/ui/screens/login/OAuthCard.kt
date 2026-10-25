package com.anisequel.ui.screens.login

import android.content.Context
import android.net.Uri
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.RedirectUrlHint
import com.anisequel.ui.viewmodel.AuthUiState

@Composable
fun OAuthCard(
    authState: AuthUiState,
    authUrl: String,
    onRetry: () -> Unit,
    onShowManualToken: () -> Unit,
    onShowClientId: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    // One source per control, shared between the button and its `bouncyPress`.
    //
    // `bouncyPress` can find the press itself when it is not given a source, but
    // its fallback is a pointer loop that has no idea whether the gesture became
    // a scroll - so a finger that lands on the button and drags squashed it for
    // the whole drag and then sprang back, which reads as two presses. The card
    // sits in the login screen's scrolling column. Reading each button's own
    // source means the press is emitted on down and *cancelled* when the gesture
    // is claimed, and it keeps the ripple and the scale from disagreeing about
    // when the button is pressed.
    val connectButtonSource = remember { MutableInteractionSource() }
    val pasteTokenSource = remember { MutableInteractionSource() }
    val clientIdSource = remember { MutableInteractionSource() }

    Column(modifier = modifier.fillMaxWidth()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            border = CardDefaults.outlinedCardBorder()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FeatureRow(
                    icon = AppVectorIcons.CheckDouble,
                    title = "Personal List Sync",
                    desc = "Detect missed seasons from your personal completed lists"
                )
                Spacer(modifier = Modifier.height(12.dp))
                FeatureRow(
                    icon = AppVectorIcons.BookmarkAdd,
                    title = "1-Tap Add to Planning",
                    desc = "Add missing entries directly to your AniList account"
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        AnimatedVisibility(
            visible = authState is AuthUiState.Error,
            enter = fadeIn(ExpressiveMotion.FastEffects),
            exit = fadeOut(ExpressiveMotion.FastEffects)
        ) {
            if (authState is AuthUiState.Error) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Warning,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = authState.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }
        }

        // One branch, and the controls under it stay exactly where they are.
        //
        // This used to branch on `Loading` alone: the button became a bare 52dp
        // spinner and the Paste Token / Client ID row and the redirect hint
        // below it disappeared with it, so the card changed height twice during
        // a single sign-in. Worse, `Authenticated` fell into the same branch as
        // `Unauthenticated`, so for the frame or two between the token being
        // stored and the navigation that leaves this screen, a reader who had
        // *just* signed in was shown "Connect AniList Account" again. Both
        // in-flight states now draw the same card, at the button's own height,
        // and nothing below it moves.
        val isSigningIn = authState is AuthUiState.Loading ||
            authState is AuthUiState.Authenticated

        if (isSigningIn) {
            AuthProgressCard(
                authenticated = authState is AuthUiState.Authenticated
            )
        } else {
            Button(
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    // Clear the error before leaving for the browser. The card
                    // above the button explained why the last attempt failed,
                    // and that explanation has to go before the user can try
                    // again - it described a *previous* attempt, not this one.
                    //
                    // This button used to be `enabled = authState !is Error`,
                    // which combined with `Error` being a state nothing could
                    // leave to make a failed sign-in a dead end: declining the
                    // AniList consent screen - the single most likely outcome -
                    // disabled the only control that could restart the flow.
                    onRetry()
                    openCustomTab(context, authUrl)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bouncyPress(pressedScale = 0.96f, interactionSource = connectButtonSource)
                    .testTag("connect_anilist_button"),
                shape = MaterialTheme.shapes.small,
                interactionSource = connectButtonSource
            ) {
                Icon(
                    imageVector = AppVectorIcons.Login,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Connect AniList Account",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            TextButton(
                onClick = onShowManualToken,
                modifier = Modifier
                    .testTag("paste_token_button")
                    .bouncyPress(pressedScale = 0.94f, interactionSource = pasteTokenSource),
                interactionSource = pasteTokenSource
            ) {
                Text("Paste Token")
            }
            Text(
                text = "•",
                modifier = Modifier.padding(horizontal = 4.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(
                onClick = onShowClientId,
                modifier = Modifier
                    .testTag("custom_client_id_button")
                    .bouncyPress(pressedScale = 0.94f, interactionSource = clientIdSource),
                interactionSource = clientIdSource
            ) {
                Text("Client ID")
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        RedirectUrlHint(modifier = Modifier.fillMaxWidth())
    }
}

/**
 * What sits in the sign-in button's place while a sign-in is in flight.
 *
 * It is exactly as tall and as wide as the button it replaces, which is the
 * whole point: the two states swap in place, so the error card, the helper row
 * and the redirect hint under it never move, and a reader cannot press anything
 * while the app is asking AniList on their behalf.
 *
 * Two labels rather than one, because they describe different waits. `Loading`
 * is the browser round trip; `Authenticated` means the token is already stored
 * and only navigation is left. The second is short-lived - it ends the moment
 * the dashboard composes - but it is the state that used to flash the connect
 * button back at someone who had just finished signing in.
 */
@Composable
private fun AuthProgressCard(authenticated: Boolean) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (authenticated) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHighest
        },
        modifier = Modifier
            .fillMaxWidth()
            .height(50.dp)
            .testTag("auth_progress_card")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ExpressiveLoadingIndicator(
                modifier = Modifier.size(22.dp),
                color = if (authenticated) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.primary
                }
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = if (authenticated) {
                    "Signed in - opening your list\u2026"
                } else {
                    "Waiting for AniList\u2026"
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                // The card is the button's exact height, so the label has to fit
                // one line; at the largest font scales it ellipsizes rather than
                // being clipped by the fixed height.
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (authenticated) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun FeatureRow(
    icon: ImageVector,
    title: String,
    desc: String
) {
    Row(verticalAlignment = Alignment.Top, modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun openCustomTab(context: Context, url: String) {
    runCatching {
        val customTabsIntent = CustomTabsIntent.Builder().setShowTitle(true).build()
        customTabsIntent.launchUrl(context, Uri.parse(url))
    }.recoverCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}
