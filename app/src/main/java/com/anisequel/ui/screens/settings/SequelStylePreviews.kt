package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem

/**
 * The two newest sequel styles, drawn at the size the picker has room for.
 *
 * Split out of `LayoutPreviews.kt`, which was over the line budget for a file of
 * previews - and these two are the ones with drawing of their own rather than a
 * row of bars re-arranged. Each is a miniature of the real card: the shape is what
 * the reader is choosing, and the geometry here is the geometry there, scaled.
 */

/** A ticket: artwork, a reading column, and a perforated stub on the right. */
@Composable
internal fun TicketStylePreview(entry: MediaListEntryItem?, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        MiniCover(
            entry = entry,
            modifier = Modifier
                .padding(6.dp)
                .width(44.dp)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            MiniBar(0.46f, 4.dp, MaterialTheme.colorScheme.primary)
            MiniBar(0.82f, 5.dp, MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(13.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.38f))
            )
        }
        // A row of dots rather than the real card's dashes: at this size a dash
        // pattern reads as a grey smear, and the reader is choosing a shape.
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .width(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly
        ) {
            repeat(6) {
                Box(
                    modifier = Modifier
                        .size(2.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
    }
}

/** An instant print: a cream frame with the caption margin underneath, tilted. */
@Composable
internal fun PolaroidStylePreview(entry: MediaListEntryItem?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.52f)
                .fillMaxHeight(0.92f)
                // The same fixed lean the real card gets, so the sample is the shape
                // the reader will see in the list rather than a straight-on card.
                .graphicsLayer { rotationZ = -2f }
                .clip(RoundedCornerShape(3.dp))
                .background(Color(0xFFF7F3EA))
                .padding(5.dp)
        ) {
            MiniCover(
                entry = entry,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            Spacer(modifier = Modifier.height(4.dp))
            MiniBar(0.75f, 4.dp, Color(0xFF23211F).copy(alpha = 0.8f))
            Spacer(modifier = Modifier.height(3.dp))
            MiniBar(0.4f, 3.dp, Color(0xFF23211F).copy(alpha = 0.45f))
        }
    }
}

/** A placeholder line of text, at preview scale. */
@Composable
internal fun MiniBar(fraction: Float, height: Dp, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(color)
    )
}
