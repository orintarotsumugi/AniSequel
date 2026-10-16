package com.anisequel.ui.components.detail.info

import android.content.Intent
import android.net.Uri
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
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The things the page can do with this title, rather than tell you about it.
 *
 * Every row is an action the app really performs - the artwork opens, the trailer
 * opens, the chooser opens. The reference's Extras card is the same idea and this is
 * where the Info page stops being a reimplementation of it: an "Episodes" row or a
 * "Comments" row would be a control that leads nowhere, and the honest version of
 * that card for *this* app is the three things it can actually do.
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
