package com.anisequel.ui.components.detail.info

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.expressive.ExpressiveShapes

/**
 * The hero's small pieces: the translucent fact chips and the copy
 * acknowledgement.
 *
 * Split from `InfoHero` because they are the part of it that never looks at the
 * media, the backdrop or the scroll offset - they draw one label each. Keeping
 * them beside the hero's layout put five unrelated small composables in a file
 * that is already carrying the poster, the banner and the collapsing lockup.
 */

/**
 * The acknowledgement for a copied title, over the artwork.
 *
 * Same pill vocabulary as the hero's action bar - black glass, white glyph - so
 * it reads as part of this surface rather than as a system toast that happened to
 * land on it.
 */
@Composable
internal fun TitleCopiedPill() {
    Surface(
        shape = ExpressiveShapes.pill,
        color = Color.Black.copy(alpha = 0.62f),
        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.18f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.CheckCircle,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Title copied",
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Puts [title] on the clipboard.
 *
 * The platform clipboard rather than Compose's, for the reason `CopyLinkRow`
 * records: it is the contract that cannot break under a Compose upgrade, and
 * plain text is all this needs. `as?` because a device that does not publish a
 * clipboard must not turn a hold on the title into a crash.
 */
internal fun copyTitle(context: Context, title: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    clipboard?.setPrimaryClip(ClipData.newPlainText("AniList title", title))
}

/** A translucent chip over artwork, so it stays legible on any still. */
@Composable
internal fun HeroChip(
    icon: ImageVector,
    label: String,
    accent: Boolean = false,
    /** Non-null makes the chip a button, with a press spring and a brighter rim. */
    onClick: (() -> Unit)? = null
) {
    val container = if (accent) {
        MaterialTheme.colorScheme.primary.copy(alpha = 0.92f)
    } else {
        Color.Black.copy(alpha = 0.38f)
    }
    val content = if (accent) MaterialTheme.colorScheme.onPrimary else Color.White

    Surface(
        shape = ExpressiveShapes.pill,
        color = container,
        border = BorderStroke(
            width = 1.dp,
            color = if (onClick != null) {
                Color.White.copy(alpha = 0.34f)
            } else {
                Color.White.copy(alpha = 0.14f)
            }
        ),
        modifier = if (onClick != null) {
            // The border is brighter and the whole chip springs: over artwork, a
            // tappable capsule has nothing else to distinguish it from the four
            // facts beside it.
            Modifier
                .bouncyPress(pressedScale = 0.94f)
                .clickable(role = Role.Button, onClick = onClick)
        } else {
            Modifier
        }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = content.copy(alpha = 0.85f),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(5.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = content,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp)
            )
        }
    }
}
