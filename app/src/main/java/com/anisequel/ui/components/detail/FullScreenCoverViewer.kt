package com.anisequel.ui.components.detail

import androidx.compose.animation.AnimatedVisibility
import com.anisequel.ui.components.expressive.ExpressiveMotion
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.rememberAsyncImagePainter
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.englishName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.romajiName
import com.anisequel.data.model.statusLabel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull

/**
 * Full-screen immersive anime cover artwork viewer.
 * Supports pinch-to-zoom (up to 4x), double-tap zoom toggle, pan, and HD display.
 */
@Composable
fun FullScreenCoverViewer(
    media: MediaNode,
    onDismiss: () -> Unit
) {
    var isVisible by remember { mutableStateOf(false) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    // Measured rather than assumed: the pan limits used to be the constants 300f and
    // 400f, so a tall cover could be dragged past its own edge on a narrow phone and
    // a wide one could not be dragged far enough on a tablet.
    var coverSize by remember { mutableStateOf(IntSize.Zero) }
    val painter = rememberAsyncImagePainter(model = media.posterUrl)
    val intrinsic = painter.intrinsicSize
    // AniList covers are 2:3, and that is the right box until the real image says
    // otherwise. Reading the painter's own size is what lets a rare non-2:3 cover -
    // or a cropped upload - draw whole instead of letterboxing inside a fixed frame.
    val coverAspect = if (intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        DEFAULT_COVER_ASPECT
    }

    LaunchedEffect(Unit) {
        isVisible = true
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = true
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {
                        if (scale > 1.05f) {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            onDismiss()
                        }
                    }
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = isVisible,
                enter = fadeIn(ExpressiveMotion.DefaultEffects) +
                    scaleIn(
                        initialScale = 0.82f,
                        animationSpec = ExpressiveMotion.DefaultSpatial
                    ),
                exit = fadeOut(ExpressiveMotion.FastEffects) +
                    scaleOut(
                        targetScale = 0.85f,
                        animationSpec = ExpressiveMotion.DefaultSpatial
                    ),
                modifier = Modifier.fillMaxSize()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Top Bar: Format Badge, HD tag & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                tonalElevation = 4.dp
                            ) {
                                Text(
                                    text = "${media.formatLabel} · ${media.statusLabel}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.tertiaryContainer,
                                tonalElevation = 2.dp
                            ) {
                                Text(
                                    text = "HD",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                    fontWeight = FontWeight.ExtraBold,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                                )
                            }
                        }

                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.18f))
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Close,
                                contentDescription = "Close full screen",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Center Anime Cover Artwork with interactive Zoom & Pan
                    val coverBg = media.posterColor.toCoverColorOrNull() ?: MaterialTheme.colorScheme.surfaceContainerHighest
                    // The frame is fitted to the artwork rather than to a 340x440
                    // window: a 2:3 cover in that window letterboxed on one axis, so
                    // the "full screen" viewer drew a poster smaller than the hero
                    // it was opened from. `BoxWithConstraints` gives the space the
                    // header and footer leave, and the cover takes the largest box
                    // that fits it at its own ratio.
                    BoxWithConstraints(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val frameWidth = minOf(maxWidth, maxHeight * coverAspect)
                        val frameHeight = frameWidth / coverAspect
                        Box(
                            modifier = Modifier
                                .width(frameWidth)
                                .height(frameHeight)
                                .shadow(
                                    elevation = 24.dp,
                                    shape = MaterialTheme.shapes.large,
                                    ambientColor = coverBg,
                                    spotColor = Color.Black
                                )
                                .clip(MaterialTheme.shapes.large)
                                .background(coverBg)
                                .onSizeChanged { coverSize = it }
                                .pointerInput(coverSize) {
                                    detectTapGestures(
                                        onDoubleTap = {
                                            if (scale > 1.2f) {
                                                scale = 1f
                                                offsetX = 0f
                                                offsetY = 0f
                                            } else {
                                                scale = 2.4f
                                            }
                                        }
                                    )
                                }
                                .pointerInput(coverSize) {
                                    detectTransformGestures { _, pan, zoom, _ ->
                                        val newScale = (scale * zoom).coerceIn(1f, 4.5f)
                                        scale = newScale
                                        if (newScale > 1f) {
                                            // Half the overflow on each side: the
                                            // image scales around its centre, so this
                                            // is exactly how far it can move before
                                            // an edge shows.
                                            val maxX = coverSize.width * (newScale - 1f) / 2f
                                            val maxY = coverSize.height * (newScale - 1f) / 2f
                                            offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                            offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                                        } else {
                                            offsetX = 0f
                                            offsetY = 0f
                                        }
                                    }
                                }
                                .graphicsLayer {
                                    scaleX = scale
                                    scaleY = scale
                                    translationX = offsetX
                                    translationY = offsetY
                                }
                        ) {
                            Image(
                                painter = painter,
                                contentDescription = media.displayName,
                                modifier = Modifier.matchParentSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }

                    // Bottom Details Card: Titles & Dismiss Hint
                    Surface(
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.94f),
                        tonalElevation = 8.dp,
                        modifier = Modifier
                            .fillMaxWidth()
                            .widthIn(max = 480.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = media.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.ExtraBold,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Romaji & English Titles
                            val romaji = media.romajiName
                            val english = media.englishName

                            if (!romaji.isNullOrBlank() || !english.isNullOrBlank()) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    if (!romaji.isNullOrBlank()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "ROMAJI",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.primary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = romaji,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }

                                    if (!english.isNullOrBlank()) {
                                        Surface(
                                            shape = MaterialTheme.shapes.extraSmall,
                                            color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Column(modifier = Modifier.padding(8.dp)) {
                                                Text(
                                                    text = "ENGLISH",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.secondary,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Text(
                                                    text = english,
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurface,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = if (scale > 1.05f) "Double-tap to reset zoom · Tap anywhere to close" else "Pinch or double-tap to zoom · Tap anywhere to close",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** AniList's cover shape, used until the image's own dimensions arrive. */
private const val DEFAULT_COVER_ASPECT = 2f / 3f
