package com.anisequel.ui.components.detail.info

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.WebView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.immersiveDisplayInsets
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
 * The official trailer: the card that advertises it, and the player it opens.
 *
 * ## Why the player is in the app now
 *
 * Tapping this card used to hand the video to whatever browser the device had -
 * which left the app, lost the title, and on a phone with no browser silently
 * copied a URL to the clipboard instead. A trailer is one of the two things a
 * reader of a title page came for, and leaving the app for it is the sort of
 * hand-off that makes an app feel like a directory of other apps. The card opens
 * [NovaTrailerViewer] now, and the external route is still there as the *escape
 * hatch* inside it: a player that will not play must never be the end of the
 * story, so "Open on YouTube" sits under the frame.
 *
 * ## Why a WebView and not a native player
 *
 * AniList gives the app a YouTube **id**, not a stream. Playing that id needs either
 * YouTube's embed (a web page) or a stream extractor, i.e. a dependency whose whole
 * job is to break when YouTube changes, to fetch content the API did not licence
 * this app to serve. The embed is the same answer the rest of the app already gives
 * for a live SVG card: the one leaf of Chromium that does the job nothing else can,
 * contained. It is also why this is the one `WebView` in the app with JavaScript
 * enabled - the player *is* a web app, and without scripting the embed is a black
 * rectangle. Everything a document could do with that is closed off instead: the
 * page is a fixed wrapper holding one iframe, the only hosts it may load are
 * YouTube's, and a navigation that is not one of the player's own pages is
 * cancelled here - a web page among them is handed to the external opener, and
 * anything that is not a web page is simply stopped.
 */
@Composable
internal fun NovaTrailerCard(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val url = media.trailerYoutubeUrl ?: return
    val trailerId = media.trailer?.id?.takeIf { it.isNotBlank() } ?: return
    // Only an id YouTube could have is a video. The embed builder drops anything
    // outside that alphabet, so a malformed id would open the viewer on an empty
    // frame - a black player with no way to tell it from a trailer that refused to
    // play. Those taps go to the browser instead: the card's own escape hatch,
    // taken before the dead player is opened rather than from inside it.
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
    // reloads from the start: a `WebView` belongs to the activity that created it
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

/**
 * The trailer, playing over the page.
 *
 * A `Dialog` and not a sheet: this is a video, and a sheet that can be dragged
 * down to a third of the screen is a way to watch a trailer in a letterbox the size
 * of a thumbnail. The window is the whole display, the page behind it is dimmed, and
 * the frame is 16:9 at the top of the safe area so the controls sit where a thumb
 * can reach them.
 *
 * ## Open and close are both animated, and the close finishes before the dismiss
 *
 * `playing` in the card goes false the moment the viewer is popped, so a dialog
 * that simply unmounted would blink out of existence - the one frame that reads as
 * a crash rather than as an exit. [entrance] runs one way for the open and the other
 * for the close, and only then does [onDismiss] tell the caller, which is the same
 * handover `SequelDetailSheet` makes for the title page itself.
 */
@Composable
internal fun NovaTrailerViewer(
    videoId: String,
    title: String,
    fallbackUrl: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val entrance = remember(videoId) { Animatable(0f) }
    var isClosing by remember(videoId) { mutableStateOf(false) }
    var loaded by remember(videoId) { mutableStateOf(false) }
    var playerReady by remember(videoId) { mutableStateOf(false) }
    var playbackState by remember(videoId) { mutableStateOf(-1) }
    var playerError by remember(videoId) { mutableStateOf<Int?>(null) }
    val html = remember(videoId) { novaTrailerEmbedHtml(videoId) }
    val player = remember(videoId) {
        createNovaTrailerPlayer(
            context = context,
            html = html,
            onReady = { loaded = true },
            onPlayerReady = { playerReady = true },
            onPlaybackStateChanged = { playbackState = it },
            onPlayerError = { playerError = it }
        )
    }

    LaunchedEffect(videoId) {
        entrance.animateTo(1f, ExpressiveMotion.SettleSpatial)
        entrance.snapTo(1f)
    }
    LaunchedEffect(videoId, isClosing) {
        if (isClosing) {
            entrance.animateTo(0f, ExpressiveMotion.SettleSpatial)
            entrance.snapTo(0f)
            onDismiss()
        }
    }

    NovaTrailerDialog(
        title = title,
        entranceProgress = entrance.value,
        player = player,
        loaded = loaded,
        playerReady = playerReady,
        playbackState = playbackState,
        playerError = playerError,
        onClose = { isClosing = true },
        onOpenYouTube = {
            openTrailerExternally(context, fallbackUrl)
            isClosing = true
        }
    )
}

@Composable
private fun NovaTrailerDialog(
    title: String,
    entranceProgress: Float,
    player: WebView,
    loaded: Boolean,
    playerReady: Boolean,
    playbackState: Int,
    playerError: Int?,
    onClose: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    Dialog(
        onDismissRequest = onClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        // Zero the platform dim so this window has a single animated scrim.
        LaunchedEffect(Unit) {
            (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        DialogImmersiveEffect()
        NovaTrailerViewerContent(
            title = title,
            entranceProgress = entranceProgress,
            player = player,
            loaded = loaded,
            playerReady = playerReady,
            playbackState = playbackState,
            playerError = playerError,
            onClose = onClose,
            onOpenYouTube = onOpenYouTube
        )
    }
}

@Composable
private fun NovaTrailerViewerContent(
    title: String,
    entranceProgress: Float,
    player: WebView,
    loaded: Boolean,
    playerReady: Boolean,
    playbackState: Int,
    playerError: Int?,
    onClose: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    val immersive = LocalImmersiveMode.current
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.94f * entranceProgress))
            .then(Modifier.immersiveDisplayInsets(immersive))
            .padding(horizontal = 12.dp)
            .testTag("nova_trailer_viewer")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.Center),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            NovaTrailerHeader(title = title, onClose = onClose)
            if (playerError == null) {
                NovaTrailerFrame(
                    modifier = Modifier.weight(1f, fill = false),
                    player = player,
                    loaded = loaded
                )
                NovaTrailerPlaybackControls(
                    player = player,
                    playerReady = playerReady,
                    playbackState = playbackState
                )
                NovaTrailerExternalLink(onClick = onOpenYouTube)
            } else {
                NovaTrailerUnavailable(code = playerError, onOpenYouTube = onOpenYouTube)
            }
        }
    }
}

@Composable
private fun NovaTrailerHeader(
    title: String,
    onClose: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = CircleShape,
            color = Color.White.copy(alpha = 0.12f),
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .clickable(role = Role.Button, onClickLabel = "Close the trailer", onClick = onClose)
                .testTag("nova_trailer_close")
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = AppVectorIcons.Close,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(19.dp)
                )
            }
        }
    }
}

@Composable
private fun NovaTrailerFrame(
    modifier: Modifier,
    player: WebView,
    loaded: Boolean
) {
    // Let the frame use the available height first on a phone held in landscape.
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.Black)
        ) {
            if (!loaded) {
                NovaWavyRing(
                    progress = null,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                )
            }
            NovaTrailerPlayer(
                player = player,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
private fun NovaTrailerPlaybackControls(
    player: WebView,
    playerReady: Boolean,
    playbackState: Int
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NovaTrailerSkipButton(
            label = "−5s",
            description = "Rewind 5 seconds",
            testTag = "nova_trailer_rewind",
            playerReady = playerReady
        ) {
            player.evaluateJavascript("window.anisequelTrailer.seek(-5)", null)
        }
        NovaTrailerPlayPauseButton(
            playerReady = playerReady,
            isPlaying = playbackState == 1,
            onClick = {
                player.evaluateJavascript("window.anisequelTrailer.toggle()", null)
            }
        )
        NovaTrailerSkipButton(
            label = "+5s",
            description = "Forward 5 seconds",
            testTag = "nova_trailer_forward",
            playerReady = playerReady
        ) {
            player.evaluateJavascript("window.anisequelTrailer.seek(5)", null)
        }
    }
}

@Composable
private fun NovaTrailerSkipButton(
    label: String,
    description: String,
    testTag: String,
    playerReady: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = if (playerReady) 0.14f else 0.06f),
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = playerReady,
                role = Role.Button,
                onClickLabel = description,
                onClick = onClick
            )
            .testTag(testTag)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = Color.White.copy(alpha = if (playerReady) 1f else 0.38f)
            )
        }
    }
}

@Composable
private fun NovaTrailerPlayPauseButton(
    playerReady: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = if (playerReady) 0.2f else 0.08f),
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(
                enabled = playerReady,
                role = Role.Button,
                onClickLabel = if (isPlaying) "Pause trailer" else "Play trailer",
                onClick = onClick
            )
            .testTag("nova_trailer_play_pause")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) AppVectorIcons.Pause else AppVectorIcons.Play,
                contentDescription = if (isPlaying) "Pause trailer" else "Play trailer",
                tint = Color.White.copy(alpha = if (playerReady) 1f else 0.38f),
                modifier = Modifier.size(28.dp)
            )
        }
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
 * beforehand, because the attempt is the check: `startActivity` throws when no app is
 * installed for it, and that exception is the signal to use the browser instead.
 */
internal fun openTrailerExternally(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        .setPackage(YOUTUBE_APP_PACKAGE)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    if (runCatching { context.startActivity(intent) }.isSuccess) return
    openExternalUrlOrCopy(context, url)
}

/**
 * What the reader sees when YouTube refuses the video: the reason, and the way out.
 *
 * A frame that cannot play is not left on screen with its controls live over it.
 * Code 101 and 150 mean the owner has blocked embedded playback, which is the one
 * refusal a retry can never fix, so the wording says so.
 */
@Composable
private fun NovaTrailerUnavailable(
    code: Int,
    onOpenYouTube: () -> Unit
) {
    val reason = if (code == 101 || code == 150) {
        "The owner has turned off playback outside YouTube."
    } else {
        "YouTube could not play this trailer here."
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(Color.White.copy(alpha = 0.08f))
            .padding(horizontal = 20.dp, vertical = 24.dp)
            .testTag("nova_trailer_unavailable"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = reason,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.86f),
            textAlign = TextAlign.Center
        )
        Button(
            onClick = onOpenYouTube,
            modifier = Modifier.testTag("nova_trailer_external")
        ) {
            Text("Watch on YouTube")
        }
    }
}

@Composable
private fun NovaTrailerExternalLink(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // The escape hatch for region locks, age gates or a missing WebView provider.
        Text(
            text = "Open on YouTube",
            style = MaterialTheme.typography.labelLarge,
            color = Color.White.copy(alpha = 0.86f),
            textAlign = TextAlign.Center,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Open the trailer on YouTube",
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("nova_trailer_external")
        )
    }
}

/**
 * The embed, in the one `WebView` that may run scripting.
 *
 * Loaded from a wrapper page rather than pointed at the embed URL directly: the
 * wrapper is the document that can carry an origin and a referer, and they are the
 * app's own id - see [NOVA_TRAILER_ORIGIN] for why that is the whole fix for error
 * 152. A `WebView` asked for the embed with no referer is served "Video
 * unavailable" often enough that the direct route is not worth taking. The wrapper
 * is a string this function builds from a validated id - no remote document is ever
 * composed into it.
 *
 * The id is validated rather than escaped: AniList's ids are `[A-Za-z0-9_-]`, and
 * anything else is not a trailer. That is what keeps a malformed payload from
 * becoming markup in a page with JavaScript on.
 */
@Composable
private fun NovaTrailerPlayer(
    player: WebView,
    modifier: Modifier = Modifier
) {
    AndroidView(
        modifier = modifier.testTag("nova_trailer_player"),
        factory = { player },
        onRelease = { view ->
            // Stop before destroy: an embed still buffering when the reader closes
            // the viewer tears down a live media pipeline otherwise. Remove a
            // fullscreen surface first, or it would outlive its WebView in the dialog.
            (view.webChromeClient as? NovaTrailerChromeClient)?.hideCustomView()
            view.stopLoading()
            view.destroy()
        }
    )
}

