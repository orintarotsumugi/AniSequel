package com.example.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.example.data.network.AniListOAuth
import com.example.ui.screens.settings.SettingsButton
import com.example.ui.screens.settings.SettingsButtonVariant

/**
 * Tells the user the exact Redirect URL to register with AniList.
 *
 * Sign-in failing silently is otherwise very hard to self-diagnose: the app
 * waits forever because the token went to a web page instead of to it, and all
 * the user sees is a browser tab that will not load. The URI is here, spelled
 * out, with a link to the page where it has to be set.
 */
@Composable
fun RedirectUrlHint(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Surface(
        modifier = modifier.testTag("redirect_url_hint"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.OpenInBrowser,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Setting up sign-in",
                    style = MaterialTheme.typography.titleSmall
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = "Register this as the Redirect URL on your AniList app, then paste its Client ID above. " +
                    "A mismatch means the browser opens a page that never returns here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = MaterialTheme.shapes.extraSmall,
                color = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = AniListOAuth.REDIRECT_URI,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .padding(horizontal = 10.dp, vertical = 8.dp)
                        .testTag("redirect_uri_value")
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // A Settings-styled link rather than a raw TextButton, so the one
            // action inside this panel matches every other action on the screen.
            SettingsButton(
                text = "Open AniList developer settings",
                onClick = {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(AniListOAuth.DEVELOPER_SETTINGS_URL))
                    runCatching { context.startActivity(intent) }
                },
                icon = Icons.AutoMirrored.Filled.OpenInNew,
                variant = SettingsButtonVariant.Text,
                fillWidth = true,
                testTag = "open_anilist_developer_button"
            )
        }
    }
}