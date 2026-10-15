package com.anisequel.ui.screens.login

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress

@Composable
fun UsernameScanCard(
    /**
     * The typed username, hoisted to the caller so it outlives this card. The
     * login tabs are an `AnimatedContent`, which disposes the tab it animates
     * away from - so state held here was wiped every time the user switched to
     * Sign In and back.
     */
    username: String,
    onUsernameChange: (String) -> Unit,
    onScanUsername: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    // Shared with `bouncyPress` on the button below: the card sits inside the
    // login screen's scrolling column, and without a source the press effect
    // came from `bouncyPress`'s own pointer loop, which keeps the button pressed
    // for as long as the finger is down - so beginning a scroll on it left the
    // button visibly squashed. The button's own `clickable` cancels the press as
    // soon as the gesture is claimed as a scroll.
    val scanButtonSource = remember { MutableInteractionSource() }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Scan Any Public AniList Profile",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Enter any AniList username to detect missed sequels in read-only mode without logging in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("AniList Username") },
                placeholder = { Text("e.g. MyAnimeList") },
                singleLine = true,
                shape = MaterialTheme.shapes.small,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("username_scan_input")
            )
            Spacer(modifier = Modifier.height(12.dp))
            Button(
                onClick = {
                    if (username.isNotBlank()) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        onScanUsername(username.trim())
                    }
                },
                enabled = username.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .bouncyPress(pressedScale = 0.96f, interactionSource = scanButtonSource)
                    .testTag("scan_username_button"),
                shape = MaterialTheme.shapes.small,
                interactionSource = scanButtonSource
            ) {
                Icon(
                    imageVector = AppVectorIcons.Search,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Scan Username", fontWeight = FontWeight.Bold)
            }
        }
    }
}
