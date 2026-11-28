package com.anisequel.ui.components.detail.info

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.trailerYoutubeUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.unseamedCover
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy

/**
 * The official trailer: the card that advertises it, and the door it opens.
 *
 * ## Why the player is in the app
 *
 * Tapping this card used to hand the video to whatever browser the device had -
 * which left the app, lost the title, and on a phone with no browser silently
 * copied a URL to the clipboard instead. A trailer is one of the two things a
 * reader of a title page came for, and leaving the app for it is the sort of
 * hand-off that makes an app feel like a directory of other apps. The card opens
 * [NovaTrailerViewer] now, and the external route is still there as the *escape
 * hatch* inside it: a player that will not play must never be the end of the story,
 * so "Open on YouTube" sits under the frame and beside every refusal.
 *
 * ## The id is validated before a player is built
 *
 * AniList sends a YouTube **id**, not a stream, and only an id YouTube could have
 * issued is a video. The embed builder drops anything outside that alphabet, so a
 * malformed id would open the viewer on an empty frame - a black player with no way
 * to tell it from a trailer that refused to play. Those taps go to the browser
 * instead: the card's own escape hatch, taken before the dead player is opened rather
 * than from inside it. See [isNovaTrailerId].
 */
@Composable
internal fun NovaTrailerCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val url = media.trailerYoutubeUrl ?: return
    val trailerId = media.trailer?.id?.takeIf { it.isNotBlank() } ?: return
    val videoId = trailerId.takeIf { isNovaTrailerId(it) }
    val context = LocalContext.current
    // Keyed on [trailerId], not on the validated id: a trailer whose id YouTube could
    // not have issued opens the browser instead of the player, and its card still
    // draws - so the thumbnail has to be the one that was read, not the word "null"
    // in a URL.
    val thumbnail = remember(trailerId) { "https://img.youtube.com/vi/$trailerId/hqdefault.jpg" }
    val shape = MaterialTheme.shapes.large
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val playScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "nova_trailer_play_scale"
    )
    val playContainer by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.68f) else Color.Black.copy(alpha = 0.52f),
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_trailer_play_container"
    )
    val scrimBottom by animateColorAsState(
        targetValue = if (pressed) Color.Black.copy(alpha = 0.82f) else Color.Black.copy(alpha = 0.76f),
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "nova_trailer_scrim"
    )
    // Saved, not remembered: the reason to rotate a phone with this open is to
    // watch the trailer on its side, and a configuration change rebuilt the tree -
    // so the one moment the reader asked for landscape was the moment the player
    // closed, leaving the page they came from. The dialog reopens and the embed
    // reloads from the start: a WebView belongs to the activity that created it
    // and cannot outlive a recreation, which is a restart, not a resume.
    var playing by rememberSaveable(videoId) { mutableStateOf(false) }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.78f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .bouncyPress(pressedScale = 0.98f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClickLabel = "Play the official trailer"
            ) {
                if (videoId != null) {
                    playing = true
                } else {
                    openTrailerExternally(context, url)
                }
            }
            .testTag("nova_info_trailer_card")
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
        ) {
            AsyncImage(
                model = thumbnail,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .unseamedCover()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Black.copy(alpha = 0.05f),
                            0.55f to Color.Black.copy(alpha = 0.22f),
                            1f to scrimBottom
                        )
                    )
            )
            Surface(
                shape = CircleShape,
                color = playContainer,
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.42f)),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(54.dp)
                    .graphicsLayer {
                        scaleX = playScale
                        scaleY = playScale
                    }
                    .testTag("nova_info_trailer_play")
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = AppVectorIcons.Play,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier
                            .padding(start = 3.dp)
                            .size(26.dp)
                    )
                }
            }
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Official trailer",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    if (playing && videoId != null) {
        NovaTrailerViewer(
            videoId = videoId,
            title = media.displayName,
            fallbackUrl = url,
            onDismiss = { playing = false }
        )
    }
}

/** The YouTube app's package. A trailer goes there before it goes to a browser. */
private const val YOUTUBE_APP_PACKAGE = "com.google.android.youtube"

/**
 * Hands a trailer to the YouTube app when it is installed, and to the shared opener when not.
 *
 * This is what other Android apps do with a YouTube link: the YouTube app plays it
 * with its own controls, its own background rules and the reader's account, where a
 * browser tab shows a web page. The package is set on the intent rather than checked
 * beforehand, because the attempt is the check: startActivity throws when no app is
 * installed for it, and that exception is the signal to use the browser instead.
 */
internal fun openTrailerExternally(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setPackage(YOUTUBE_APP_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isSuccess) return
    openExternalUrlOrCopy(context, url)
}
