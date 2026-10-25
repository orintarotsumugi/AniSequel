package com.anisequel.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes

/**
 * "Saved copy" - the one honest sentence a stale screen needs.
 *
 * ## Why this exists
 *
 * The app now paints the device's snapshot before the network answers, and keeps
 * it when the network does not answer at all. That is a much better cold start
 * than a spinner or a failure page, but it creates a new way to mislead somebody:
 * a list from yesterday, drawn exactly like a list from a minute ago, with no sign
 * that anything is different. This is the sign.
 *
 * ## Why it animates its own height
 *
 * The caller embeds this in a header column, and the reader is usually looking at
 * that header when the live answer lands. A fade alone hid the text but removed
 * its height in one frame, so every row below it jumped upward - the "saved copy
 * changes the whole page" complaint. `expandVertically`/`shrinkVertically` on
 * [ExpressiveMotion.FastSpatialSize] give the row it occupies back at the speed of
 * a press, so what the reader sees is the header settling rather than the page
 * being yanked.
 *
 * The fill is `surfaceContainerHighest` rather than a container colour: this is a
 * quiet annotation on somebody's name, not a banner competing with the stats under
 * it. The clock glyph rather than a Wi-Fi slash: the copy can be stale because of
 * a 500, a timeout or a dead host, and only one of those is about the reader's
 * connection. A clock says "as of earlier", which is true in all three.
 */
@Composable
fun SavedCopyPill(
    visible: Boolean,
    modifier: Modifier = Modifier,
    label: String = "Saved copy"
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(ExpressiveMotion.FastEffects) +
            expandVertically(
                expandFrom = Alignment.Top,
                animationSpec = ExpressiveMotion.FastSpatialSize
            ),
        exit = fadeOut(ExpressiveMotion.FastEffects) +
            shrinkVertically(
                shrinkTowards = Alignment.Top,
                animationSpec = ExpressiveMotion.FastSpatialSize
            )
    ) {
        // The caller's modifier belongs to the pill, not to the wrapper: padding
        // on `AnimatedVisibility` would reserve space for a badge that is not
        // being drawn, which is a permanent gap under the username for the whole
        // time the data is live.
        Row(
            modifier = modifier
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .testTag("saved_copy_pill"),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Schedule,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
