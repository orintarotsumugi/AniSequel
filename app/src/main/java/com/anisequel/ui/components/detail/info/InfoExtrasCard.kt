package com.anisequel.ui.components.detail.info

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.pageUrl
import com.anisequel.data.model.trailerYoutubeUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import kotlinx.coroutines.delay

/**
 * The things the page can do with this title, rather than tell you about it.
 *
 * Every row is an action the app really performs - the artwork opens, the trailer
 * opens, the link is copied, the chooser opens. The reference's Extras card is the
 * same idea and this is where the Info page stops being a reimplementation of it: an
 * "Episodes" row or a "Comments" row would be a control that leads nowhere, and the
 * honest version of that card for *this* app is the things it can actually do.
 *
 * **Copy link** is the row that was missing. Sharing a title hands it to whichever app
 * the chooser resolves to and then forgets it; copying the link is what the other half
 * of the readers want - to paste it into a note, a chat they are already in, or a
 * browser - and there was no way to get the URL out of the app at all.
 */
@Composable
fun InfoExtrasCard(
    media: MediaNode,
    onOpenCover: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val trailerUrl = media.trailerYoutubeUrl

    InfoCard(
        modifier = modifier.testTag("info_extras_card"),
        title = "Extras",
        icon = AppVectorIcons.Star
    ) {
        InfoExtraRow(
            icon = AppVectorIcons.FullscreenExpand,
            title = "Full artwork",
            subtitle = "The key visual, on its own",
            onClick = onOpenCover
        )

        if (trailerUrl != null) {
            InfoExtraRow(
                icon = AppVectorIcons.Trailer,
                title = "Watch the trailer",
                subtitle = "Opens on YouTube",
                onClick = {
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(trailerUrl)))
                    }
                }
            )
        }

        CopyLinkRow(title = media.displayName, url = media.pageUrl)

        InfoExtraRow(
            icon = AppVectorIcons.ActivitySend,
            title = "Share",
            subtitle = "Send this title to someone",
            onClick = {
                runCatching {
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TEXT, "${media.displayName}\n${media.pageUrl}")
                    }
                    context.startActivity(Intent.createChooser(send, null))
                }
            }
        )
    }
}

/**
 * One tap to put this title's AniList link on the clipboard, and one honest signal
 * that it landed.
 *
 * ## Why the row confirms at all
 *
 * Android only started drawing its own "Copied" toast in Android 13, and this app's
 * floor is Android 12 - so on the oldest devices it still supports, a copy with no
 * feedback is a tap that appears to do nothing. The confirmation is the row itself
 * changing, which works on every version and costs no dependency.
 *
 * ## Why the copy is not a `LocalClipboardManager`
 *
 * Compose's own clipboard is a deprecated-in-flight API mid-rename, and this row only
 * needs a plain-text clip; `ClipboardManager.setPrimaryClip` is the platform contract
 * and cannot break under a Compose upgrade. `as?` rather than a cast because a
 * clipboard is a service a device could theoretically not publish, and a copy row
 * that crashes the title page is worse than one that does nothing.
 *
 * The `LaunchedEffect` is keyed on [copied] rather than on the row being composed, so
 * the acknowledgement starts when the tap happens and a second tap restarts the
 * window instead of leaving the row stuck on "Link copied".
 */
@Composable
private fun CopyLinkRow(
    title: String,
    url: String,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var copied by remember { mutableStateOf(false) }

    LaunchedEffect(copied) {
        if (!copied) return@LaunchedEffect
        delay(COPY_CONFIRMATION_MILLIS)
        copied = false
    }

    // A crossfade rather than a swap: the row's icon, heading and subtitle all change
    // together, and replacing three strings in one frame reads as a different row
    // appearing. Both halves are the same height, so nothing below them moves.
    AnimatedContent(
        targetState = copied,
        transitionSpec = {
            fadeIn(animationSpec = ExpressiveMotion.FastEffects) togetherWith
                fadeOut(animationSpec = ExpressiveMotion.FastEffects)
        },
        label = "copy_link_confirmation",
        modifier = modifier
    ) { confirmed ->
        InfoExtraRow(
            icon = if (confirmed) AppVectorIcons.CheckCircle else AppVectorIcons.CopyLink,
            title = if (confirmed) "Link copied" else "Copy link",
            subtitle = if (confirmed) {
                "Paste it anywhere"
            } else {
                "Keep the AniList link to hand"
            },
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(title, url))
                copied = true
            }
        )
    }
}

/**
 * How long a copy control acknowledges a tap before it returns to being a control.
 *
 * Shared with the title sheet's copy buttons rather than duplicated: the two are
 * the same interaction, and two windows that drifted apart would make one of them
 * read as broken.
 */
internal const val COPY_CONFIRMATION_MILLIS = 1800L

@Composable
private fun InfoExtraRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .bouncyPress(pressedScale = 0.98f)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier
                    .padding(9.dp)
                    .size(18.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Icon(
            imageVector = AppVectorIcons.SequelArrow,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
    }
}
