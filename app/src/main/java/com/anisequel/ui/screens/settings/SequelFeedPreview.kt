package com.anisequel.ui.screens.settings

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * The two shapes the new Home's sequel list can take, drawn from the reader's
 * own covers.
 *
 * Shares [MiniCover] with the layout previews above, and the sample titles fall
 * back to the same place: a reader choosing between "Rows" and "Cards" is
 * choosing how much of a poster they get, which is exactly the thing a label
 * cannot tell them.
 */
@Composable
internal fun SequelStylePreview(
    style: HomeSequelStyle,
    entries: List<MediaListEntryItem>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "Preview \u00b7 ${style.displayName}",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // Taller than the 160dp diagram it replaces: at 160 a row could hold
                // a name and a chip but not the second line that says what the row is
                // *for*, and the card mode could not show its caption and its action
                // button at all. 232dp is one real sequel card's height, so what the
                // reader picks here is what they scroll past on Home.
                .height(232.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(10.dp)
                .testTag("home_sequel_style_preview")
        ) {
            Crossfade(
                targetState = style,
                animationSpec = ExpressiveMotion.FastEffects,
                label = "home_sequel_style_preview_mode"
            ) { current ->
                when (current) {
                    HomeSequelStyle.ROWS -> Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // The weight is applied here, in the Column, rather than
                        // inside MiniSequelRow: `Modifier.weight` only exists on a
                        // Column/Row scope, and the row's own body was reaching for
                        // it from outside one - which is a compile error the first
                        // CI run after this preview was written.
                        MiniSequelRow(
                            entry = previewEntry(entries, 0),
                            modifier = Modifier.weight(1f)
                        )
                        MiniSequelRow(
                            entry = previewEntry(entries, 1),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    HomeSequelStyle.CARDS -> Column(modifier = Modifier.fillMaxSize()) {
                        // A poster is 2:3. Filling the whole preview box with it
                        // stretched a portrait cover into a wide banner, which is
                        // the opposite of what "Cards" promises. Hold the poster
                        // aspect and centre it, so this reads as the same shape a
                        // reader gets on Home with the caption underneath.
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            MiniCover(
                                entry = previewEntry(entries, 0),
                                modifier = Modifier
                                    .fillMaxHeight(0.86f)
                                    .aspectRatio(2f / 3f)
                                    .clip(RoundedCornerShape(10.dp))
                            )
                        }
                        Spacer(modifier = Modifier.height(7.dp))
                        Text(
                            text = previewTitle(previewEntry(entries, 0)),
                            style = miniBodyStyle(),
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = "Airing \u00b7 episode 8 of 12 \u00b7 84%",
                            style = miniCaptionStyle(),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.height(7.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Text(
                                text = "Watch sequel",
                                style = miniCaptionStyle(),
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimary,
                                maxLines = 1,
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .padding(vertical = 4.dp),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "Later",
                                style = miniCaptionStyle(),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                    HomeSequelStyle.BANNER -> Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        MiniCover(
                            entry = previewEntry(entries, 0),
                            modifier = Modifier.fillMaxSize()
                        )
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.horizontalGradient(
                                        listOf(Color.Black.copy(alpha = 0.72f), Color.Transparent)
                                    )
                                )
                        )
                        Column(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .fillMaxWidth(0.72f)
                                .padding(10.dp)
                        ) {
                            Text(
                                text = previewTitle(previewEntry(entries, 0)),
                                style = miniTitleStyle(),
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(3.dp))
                            Text(
                                text = "New season \u00b7 12 episodes",
                                style = miniCaptionStyle(),
                                color = Color.White.copy(alpha = 0.8f),
                                maxLines = 1
                            )
                        }
                    }
                    HomeSequelStyle.SHELF -> Row(
                        modifier = Modifier.fillMaxSize(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        repeat(4) { index ->
                            MiniCover(
                                entry = previewEntry(entries, index),
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )
                        }
                    }
                    HomeSequelStyle.TICKET -> TicketStylePreview(entry = previewEntry(entries, 0))
                    HomeSequelStyle.POLAROID -> PolaroidStylePreview(entry = previewEntry(entries, 0))
                }
            }
        }
    }
}

@Composable
private fun MiniSequelRow(
    entry: MediaListEntryItem?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MiniCover(
            entry = entry,
            modifier = Modifier
                .width(30.dp)
                .fillMaxHeight()
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp)
        ) {
            Text(
                text = previewTitle(entry),
                style = miniBodyStyle(),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(modifier = Modifier.height(3.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Airing",
                    style = miniCaptionStyle(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
                Text(
                    text = "24 eps",
                    style = miniCaptionStyle(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                )
                Text(
                    text = "84%",
                    style = miniCaptionStyle(),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1
                )
            }
        }
    }
}
