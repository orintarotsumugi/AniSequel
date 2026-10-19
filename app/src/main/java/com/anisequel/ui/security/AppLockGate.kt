package com.anisequel.ui.security

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons

/**
 * The screen that stands between the launcher and the reader's own list when the
 * optional app lock is on.
 *
 * ## Why it covers the whole tree
 *
 * The lock is an app gate, not a screen. Applying it over `AppNavigation` means
 * every route is covered by the same gate - a deep link, a widget intent, or a
 * restored back stack all land under it. A per-screen check would have to be
 * remembered on every new destination, and the first one that forgot would be a
 * screenshot of someone's watch history in the recents switcher.
 *
 * ## The content stays composed underneath
 *
 * The overlay is opaque and covers the whole window, but the navigation tree is
 * not torn down. Disposing it on every lock would reset the app to its start
 * destination, so unlocking after a glance at another app would land the reader
 * back on Home with their scroll position and open sheet gone. A lock is a
 * curtain, not a restart.
 *
 * ## Back must not dismiss it
 *
 * `BackHandler` sends the task to the background instead. The default would pop
 * the Activity off the stack and let a second launch start clean - which is the
 * same as unlocking it, from the perspective of someone holding the phone.
 */
@Composable
fun AppLockGate(
    locked: Boolean,
    onUnlock: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        // The tree beneath must stay composed to keep its scroll positions and
        // open sheets, but it must not stay *readable*: an accessibility service
        // can traverse hidden-behind-a-sibling content, and a screen reader
        // announcing the list under a lock screen would defeat the lock. The
        // semantics are cleared only while locked.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(if (locked) Modifier.clearAndSetSemantics {} else Modifier)
        ) {
            content()
        }

        if (locked) {
            val context = LocalContext.current
            BackHandler {
                (context as? Activity)?.moveTaskToBack(true)
            }

            Surface(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("app_lock_screen"),
                color = MaterialTheme.colorScheme.background
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .safeDrawingPadding()
                        .padding(horizontal = 32.dp, vertical = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        modifier = Modifier.size(88.dp)
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.Privacy,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    Text(
                        text = "AniSequel is locked",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Unlock with your fingerprint, face or screen lock to see your list.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(28.dp))

                    Button(
                        onClick = onUnlock,
                        modifier = Modifier.testTag("app_lock_unlock_button")
                    ) {
                        Text("Unlock")
                    }
                }
            }
        }
    }
}
