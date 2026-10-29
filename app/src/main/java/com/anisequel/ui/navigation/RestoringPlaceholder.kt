package com.anisequel.ui.navigation

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator

/**
 * The brief hold while the stored session is read.
 *
 * Uses the app's own background colour rather than nothing, so the window does
 * not flash a lighter default before the themed content arrives - which on a
 * dark theme is its own brief flicker, and the very thing this was added to
 * stop.
 *
 * ## Why this is more than a spinner
 *
 * On a first launch this frame precedes the sign-in screen; on every launch
 * after one it precedes the reader's list. In both cases it is the app's first
 * impression, and a bare morphing dot on an empty page was indistinguishable
 * from a screen that had failed to load. The mark, the name and the sentence say
 * which of the two waits this is - "checking for a saved session" - so a cold
 * start that takes a moment reads as the app looking for something rather than
 * as the app being stuck. The mark is drawn in the same primary container the
 * sign-in hero uses, so the two screens feel like one app even though one is
 * Compose and the other is this placeholder.
 *
 * The same frame covers the stored-theme read in `MainActivity`, which used to
 * return an empty `Surface` and leave the first frame blank. [message] is a
 * parameter for that second wait; both are "the app is reading what it saved".
 */
@Composable
internal fun RestoringPlaceholder(
    modifier: Modifier = Modifier,
    message: String = "Checking for a saved session\u2026"
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(MaterialTheme.shapes.large)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = AppVectorIcons.SequelJump,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "AniSequel",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(22.dp))
            ExpressiveLoadingIndicator(
                modifier = Modifier.size(32.dp),
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}
