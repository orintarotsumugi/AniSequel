package com.anisequel.ui.components.detail.info

import android.content.Context
import android.graphics.Color as AndroidColor
import android.view.View
import android.view.ViewGroup
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
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
import java.net.URLEncoder

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
                    openExternalUrlOrCopy(context, url)
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
    val html = remember(videoId) { novaTrailerEmbedHtml(videoId) }
    val player = remember(videoId) {
        createNovaTrailerPlayer(
            context = context,
            html = html,
            onReady = { loaded = true },
            onPlayerReady = { playerReady = true },
            onPlaybackStateChanged = { playbackState = it }
        )
    }

    LaunchedEffect(videoId) {
        entrance.animateTo(1f, ExpressiveMotion.SettleSpatial)
        // Finish at the exact endpoint so a spring's visibility threshold cannot
        // leave the dialog a fraction short of its settled state.
        entrance.snapTo(1f)
    }
    LaunchedEffect(videoId, isClosing) {
        if (!isClosing) return@LaunchedEffect
        entrance.animateTo(0f, ExpressiveMotion.SettleSpatial)
        entrance.snapTo(0f)
        onDismiss()
    }
    val requestClose: () -> Unit = { if (!isClosing) isClosing = true }

    Dialog(
        onDismissRequest = requestClose,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        // The platform draws a hard, undimmable scrim behind every dialog, so the
        // reader got a 60%-black cut on the frame the viewer opened while this
        // dialog's own scrim faded in behind it - and the same cut backwards on the
        // way out, which made the exit a fade followed by a flick. Zeroing the
        // window's dim leaves one scrim, and it is the one that animates. The cover
        // viewer makes the same call for the same reason.
        LaunchedEffect(Unit) {
            (dialogView.parent as? DialogWindowProvider)?.window?.setDimAmount(0f)
        }
        // A dialog is its own window, so the Activity's bars do not follow it: the
        // player came back with the status and navigation bars drawn over the video,
        // which is the one screen in the app that cannot have them. Every other
        // full-screen surface already opts in; this dialog was the exception.
        DialogImmersiveEffect()
        val immersive = LocalImmersiveMode.current

        // The scrim, the frame and the chrome move as one window: fading only the
        // player would leave a black page pretending to be closed.
        Box(
            modifier = Modifier
                .fillMaxSize()
                
                // Full bleed, then the cutout: the scrim has to cover the display,
                // while the chrome has to stay out from under a camera hole.
                .background(Color.Black.copy(alpha = 0.94f * entrance.value))
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
                            .clickable(role = Role.Button, onClickLabel = "Close the trailer") {
                                requestClose()
                            }
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

                // The frame is 16:9 at the window's width *while that fits*, and by the
                // room the chrome leaves otherwise. A full-width 16:9 rectangle on a
                // phone on its side is 450dp tall on a 360dp screen, so the bottom of
                // the picture and the escape hatch under it were off the display: the
                // one surface in the app that takes the whole screen was the one that
                // could not be watched in landscape. The weighted box is offered the
                // leftover height and, because it is not forced to fill it, portrait
                // keeps exactly the geometry it had - the aspect ratio fits the width
                // there and the box wraps the picture.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.Black)
                    ) {
                        // The WebView starts invisible, so the ring occupies the
                        // frame without being drawn over a live video surface. It leaves
                        // as soon as YouTube reports the player ready (or the watchdog
                        // reveals the fallback player).
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

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
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
                                onClickLabel = "Rewind 5 seconds"
                            ) {
                                player.evaluateJavascript("window.anisequelTrailer.seek(-5)", null)
                            }
                            .testTag("nova_trailer_rewind")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "−5s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = if (playerReady) 1f else 0.38f)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = if (playerReady) 0.2f else 0.08f),
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = playerReady,
                                role = Role.Button,
                                onClickLabel = if (playbackState == 1) "Pause trailer" else "Play trailer"
                            ) {
                                player.evaluateJavascript("window.anisequelTrailer.toggle()", null)
                            }
                            .testTag("nova_trailer_play_pause")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (playbackState == 1) AppVectorIcons.Pause else AppVectorIcons.Play,
                                contentDescription = if (playbackState == 1) "Pause trailer" else "Play trailer",
                                tint = Color.White.copy(alpha = if (playerReady) 1f else 0.38f),
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = if (playerReady) 0.14f else 0.06f),
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .clickable(
                                enabled = playerReady,
                                role = Role.Button,
                                onClickLabel = "Forward 5 seconds"
                            ) {
                                player.evaluateJavascript("window.anisequelTrailer.seek(5)", null)
                            }
                            .testTag("nova_trailer_forward")
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(
                                text = "+5s",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White.copy(alpha = if (playerReady) 1f else 0.38f)
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // The escape hatch. An embed can be refused - region locks,
                    // age gates, a build with no WebView provider - and a player
                    // with no way out of it is a dead end in the one screen that
                    // took over the whole display.
                    Text(
                        text = "Open on YouTube",
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White.copy(alpha = 0.86f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .clickable(
                                role = Role.Button,
                                onClickLabel = "Open the trailer on YouTube"
                            ) {
                                openExternalUrlOrCopy(context, fallbackUrl)
                                requestClose()
                            }
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                            .testTag("nova_trailer_external")
                    )
                }
            }
        }
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

/**
 * The player view, and the one place that decides when it may be seen.
 *
 * ## Why it starts transparent
 *
 * The frame used to be a `WebView` painted opaque black from its first frame, with
 * the page's wavy ring composed *behind* it in the same `Box`. A `WebView` is a real
 * Android view in Compose's interop layer, and that layer draws above every Compose
 * node in the window - so the ring ran, span, and could not be seen: what the reader
 * got was the black rectangle the ring existed to explain. That is the "the circle
 * loading is not wavy" report; the wave was correct and one view layer too low. The
 * view now starts at `alpha` 0 and fades in when it has something to show, so the
 * ring *is* the loading state rather than a decoration under an opaque surface.
 *
 * That alpha is owned by the caller, not by this function: the dialog's entrance
 * fades the whole window, and a layer cannot fade an interop view - so the entrance,
 * the reveal and the ring's crossfade are one driver writing one property.
 *
 * ## What counts as loaded
 *
 * `onPageFinished` fires for the wrapper - the local HTML string - not for the
 * player inside it, so it hid the ring a frame after the viewer opened and left a
 * black frame for however long the embed took. The authoritative signal is the
 * iframe's own `load` event, which the wrapper reports with one console line: not an
 * `addJavascriptInterface` bridge, because a bridge is a callable surface handed to a
 * remote frame and all this needs is one string. The wrapper also reports on a timer,
 * and [NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS] is the last resort, so a player that never
 * reports cannot leave a spinner turning forever.
 */
@Suppress("SetJavaScriptEnabled", "OVERRIDE_DEPRECATION")
private fun createNovaTrailerPlayer(
    context: Context,
    html: String,
    onReady: () -> Unit,
    onPlayerReady: () -> Unit,
    onPlaybackStateChanged: (Int) -> Unit
): WebView {
    val webView = WebView(context)
    var revealed = false
    // Idempotent: three signals can fire the reveal and the first one wins.
    val reveal: () -> Unit = {
        if (!revealed) {
            revealed = true
            webView.visibility = View.VISIBLE
            onReady()
        }
    }
    webView.apply {
        // Zero until the caller's driver raises it. The driver owns this property
        // now (see `NovaTrailerPlayer`): an animator here would be a second,
        // uncancellable clock fighting the dialog's entrance for the same value,
        // and the exit could not reach it at all.
        alpha = 1f
        visibility = View.INVISIBLE
        setBackgroundColor(AndroidColor.BLACK)
        // A hardware layer, explicitly.
        //
        // The trailer played over a black rectangle: audio, the player's own chrome,
        // the title - and no picture. This view is a video surface inside a floating
        // dialog window, and without a layer of its own the platform composites it
        // through a path that drops the video plane while the rest of the page draws
        // normally, which is why it read as "the sound works and the picture does
        // not" rather than as a failed load. A hardware layer puts the WebView's
        // whole output, video included, through its render node as one texture.
        //
        // The opposite is the same report from the other side: `LAYER_TYPE_SOFTWARE`
        // cannot hold a video surface at all, so a player forced into one plays audio
        // and draws nothing. This is deliberately not that - and the app never sets a
        // software layer on a view that shows video.
        setLayerType(View.LAYER_TYPE_HARDWARE, null)
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.loadsImagesAutomatically = true
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.mediaPlaybackRequiresUserGesture = false
        // Explicit, because the navigation rule depends on it: with no popup
        // window a `target="_blank"` link - which is what "Watch on YouTube" is -
        // is loaded in the frame that asked for it, so it reaches
        // `shouldOverrideUrlLoading` as a subframe navigation and the watch-page
        // check can send it out. With windows enabled and no `onCreateWindow`, the
        // same tap would do nothing at all.
        settings.setSupportMultipleWindows(false)
        webChromeClient = NovaTrailerChromeClient(
            webView = this,
            onEmbedReady = reveal,
            onPlayerReady = onPlayerReady,
            onPlaybackStateChanged = onPlaybackStateChanged
        )
        settings.setSupportZoom(false)
        settings.builtInZoomControls = false
        settings.displayZoomControls = false
        isVerticalScrollBarEnabled = false
        isHorizontalScrollBarEnabled = false
        overScrollMode = View.OVER_SCROLL_NEVER
        webViewClient = object : WebViewClient() {
            // There is deliberately no `onPageFinished` reveal: it fires for the
            // wrapper, i.e. for the HTML this app wrote, and not for the player.
            override fun shouldOverrideUrlLoading(
                view: WebView?,
                request: WebResourceRequest?
            ): Boolean {
                val target = request?.url?.toString()
                if (request != null && staysInNovaTrailer(target.orEmpty(), request.isForMainFrame)) {
                    return false
                }
                // Only a web page is worth opening elsewhere. The player's own
                // `about:blank` / `data:` frames are not links, and handing them to the
                // opener copied their URL to the clipboard and announced it.
                if (target != null && isNovaTrailerWebUrl(target)) {
                    openExternalUrlOrCopy(context, target)
                }
                return true
            }
            // The deprecated `(WebView, String)` overload is gone rather
            // than suppressed: the platform stopped calling it in API 24
            // and `minSdk` is 31, so a second entry point to the same rule
            // could never fire.
        }
        // Not loaded here: a view built during composition has not been measured,
        // and this page's frame is sized in percentages of the viewport - so a load
        // that starts now lays the wrapper out against a frame with no size, and the
        // embed takes the picture's size from the frame it was given. It waits for
        // the first layout that has one.
        var loadPending = true
        // Nine parameters, not the four that matter: this is
        // `View.OnLayoutChangeListener`, and a shorter lambda is not a SAM
        // conversion - it is a compile error the debug build caught.
        addOnLayoutChangeListener { _, _, _, right, bottom, _, _, _, _ ->
            if (loadPending && right > 0 && bottom > 0) {
                loadPending = false
                webView.loadDataWithBaseURL(
                    NOVA_TRAILER_BASE_URL, html, "text/html", "UTF-8", null
                )
            }
        }
    }
    // A player that says nothing at all - no WebView provider, a script the page
    // declined to run - still has to stop being a spinner eventually.
    val timeout = Runnable { reveal() }
    webView.postDelayed(timeout, NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS)
    // A dialog can be closed before it fires, and releasing the player calls
    // `destroy()`. A callback still queued past that runs the reveal against a
    // view the composition has already thrown away, and holds the WebView - with
    // the page it decoded and its audio decoder - alive for the rest of the seven
    // seconds. Removing it on detach is the one hook that fires both for a close
    // and for the rotation that recreates the dialog.
    webView.addOnAttachStateChangeListener(
        object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit
            override fun onViewDetachedFromWindow(view: View) {
                view.removeCallbacks(timeout)
                view.removeOnAttachStateChangeListener(this)
            }
        }
    )
    return webView
}

/**
 * The origin the wrapper page is loaded under, and the one the player is told it
 * is embedded in.
 *
 * This must not be a YouTube host. The player identifies its embedder by the
 * page's origin, and a page whose origin is `https://www.youtube.com` is read as
 * YouTube embedding itself: every trailer came back as error 152, "This video is
 * unavailable", before a frame played. That was the whole trailer library, not one
 * bad id. The app id is a real `https://` origin, is unique to this caller, and is
 * the identifier Google documents for a WebView embed - `loadDataWithBaseURL`
 * turns it into the `Referer` header, which is the identification YouTube now
 * refuses embeds without (error 153). The iframe itself still loads from
 * youtube-nocookie.com: the origin names the *page*, the host names the *player*,
 * and they are not supposed to be the same value here.
 */
internal const val NOVA_TRAILER_ORIGIN = "https://com.aistudio.anisequel.app"
internal const val NOVA_TRAILER_BASE_URL = "$NOVA_TRAILER_ORIGIN/"
private val NOVA_TRAILER_ORIGIN_QUERY: String =
    URLEncoder.encode(NOVA_TRAILER_ORIGIN, Charsets.UTF_8.name())

/** Emitted only when YouTube's IFrame API reports that the player is ready. */
private const val NOVA_TRAILER_PLAYER_READY_MESSAGE = "anisequel:trailer-player-ready"

/** Playback-state events forwarded from the IFrame API to Compose. */
private const val NOVA_TRAILER_STATE_PREFIX = "anisequel:trailer-state:"

/** The last-resort reveal: a player that never reports must not spin forever. */
private const val NOVA_TRAILER_REVEAL_TIMEOUT_MILLIS = 7000L

/** Routes YouTube's full-screen video surface above the Compose dialog. */
@Suppress("OVERRIDE_DEPRECATION")
private class NovaTrailerChromeClient(
    private val webView: WebView,
    private val onEmbedReady: () -> Unit,
    private val onPlayerReady: () -> Unit,
    private val onPlaybackStateChanged: (Int) -> Unit
) : WebChromeClient() {
    private var customView: View? = null
    private var customViewCallback: CustomViewCallback? = null

    override fun onShowCustomView(view: View, callback: CustomViewCallback) {
        val root = webView.rootView as? ViewGroup
        if (root == null || customView != null) {
            callback.onCustomViewHidden()
            return
        }
        customView = view
        customViewCallback = callback
        root.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
    }

    override fun onHideCustomView() = hideCustomView()

    // The player reports itself through the console rather than through a
    // JavaScript interface: a bridge is a callable surface exposed to a frame this
    // app does not control, and all this needs is for one string to arrive.
    override fun onConsoleMessage(message: ConsoleMessage): Boolean {
        val text = message.message()
        when {
            text == NOVA_TRAILER_PLAYER_READY_MESSAGE -> {
                onEmbedReady()
                onPlayerReady()
                return true
            }
            text.startsWith(NOVA_TRAILER_STATE_PREFIX) -> {
                val state = text.substringAfter(NOVA_TRAILER_STATE_PREFIX).toIntOrNull()
                    ?: return super.onConsoleMessage(message)
                onPlaybackStateChanged(state)
                return true
            }
        }
        return super.onConsoleMessage(message)
    }

    fun hideCustomView() {
        val view = customView ?: return
        (view.parent as? ViewGroup)?.removeView(view)
        customView = null
        customViewCallback?.onCustomViewHidden()
        customViewCallback = null
    }
}

/** The hosts the player frame is allowed to load. */
private val NOVA_TRAILER_HOSTS = listOf("youtube.com", "youtube-nocookie.com", "ytimg.com", "googlevideo.com")

/**
 * Whether [url] belongs to the embed rather than to the web at large.
 *
 * The authority is matched without its port and without user-info. Matching it whole
 * meant `https://www.youtube.com:443/embed/x` read as the host `youtube.com:443`, which
 * is not YouTube by this rule - so a navigation the player needed was handed to a
 * browser mid-playback. The dot is anchored on both ends: `youtube.com.example.net`
 * is not YouTube, and neither is a bare `notyoutube.com`.
 */
internal fun isNovaTrailerHost(url: String): Boolean {
    val authority = url.substringAfter("://", "")
        .substringBefore('/')
        .substringBefore('?')
        .substringBefore('#')
    val host = authority.substringAfterLast('@').substringBefore(':').lowercase()
    if (host.isEmpty()) return false
    return NOVA_TRAILER_HOSTS.any { host == it || host.endsWith(".$it") }
}

/**
 * Whether [url] is a page worth handing to another app: an `http(s)` document.
 *
 * Everything else a player navigates to is its own scaffolding - `about:blank` for an
 * ad slot, `data:` or `blob:` for a measurement frame. Those used to reach the shared
 * opener too, so each one wrote its URL onto the reader's clipboard and showed the
 * "nothing here can open that link, so it is copied instead" toast - about a link no
 * reader chose, and while the trailer was still playing.
 */
internal fun isNovaTrailerWebUrl(url: String): Boolean =
    url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)

/**
 * Whether [url] is YouTube's watch page rather than the player's own document.
 *
 * The player only ever lives at `youtube(-nocookie).com/embed/...`; the watch page is
 * what a link resolves to. It is a separate question from [staysInNovaTrailer]'s frame
 * test because the reported link is a `target="_blank"` one: this view has no popup
 * window, so the navigation arrives *inside the iframe* - a subframe by
 * `isForMainFrame` - and the frame rule alone followed it, putting the watch page in
 * the 16:9 hole anyway.
 */
internal fun isNovaTrailerWatchPage(url: String): Boolean {
    if (!isNovaTrailerHost(url)) return false
    val path = url.substringAfter("://", "")
        .substringAfter('/', "")
        .substringBefore('?')
        .substringBefore('#')
        .trim('/')
    return path.equals("watch", ignoreCase = true)
}

/**
 * Whether a navigation belongs to the player and may stay inside its `WebView`.
 *
 * It has to be the player's own document: a **subframe**, on YouTube's hosts, that is
 * not the watch page. Each of those three conditions is a way this went wrong.
 *
 *  - A **subframe** is the player itself - its error card, a consent interstitial, the
 *    IFrame API's own pages. Nothing inside the embed works if those are not followed.
 *  - A **main frame** is somewhere the reader chose to go: "Watch on YouTube" on a
 *    refused video, the channel name, an ad. Following one replaced the video with the
 *    watch page inside a 16:9 hole that has no back button - the reported dead link.
 *  - A subframe **watch page** is that same reader choice arriving one layer lower. The
 *    button is a `target="_blank"` link and this view has no popup window, so the
 *    navigation happens in the iframe and passes the frame test; see
 *    [isNovaTrailerWatchPage].
 */
internal fun staysInNovaTrailer(url: String, isForMainFrame: Boolean): Boolean =
    !isForMainFrame && isNovaTrailerHost(url) && !isNovaTrailerWatchPage(url)

/** Whether [id] is a YouTube video id and therefore safe to put in a page. */
internal fun isNovaTrailerId(id: String): Boolean =
    id.isNotEmpty() && id.length <= 20 && id.all { it.isLetterOrDigit() || it == '_' || it == '-' }

/**
 * The wrapper page: one iframe, filling it, and nothing else.
 *
 * `playsinline` is what keeps the video inside the frame instead of handing it to
 * the system's fullscreen player, `rel=0` stops the embed offering other videos on
 * top of the trailer once it ends, and `modestbranding` drops the badge that sits
 * over the picture. The frame is `allow="autoplay; encrypted-media"` because without
 * those the player starts muted or refuses to start at all.
 */
internal fun novaTrailerEmbedHtml(videoId: String): String {
    val safeId = if (isNovaTrailerId(videoId)) videoId else ""
    return buildString {
        append("<!doctype html><html><head>")
        append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,maximum-scale=1\">")
        append("<style>")
        append("html,body{margin:0;padding:0;background:#000;height:100%;overflow:hidden;}")
        append("iframe{display:block;width:100%;height:100%;border:0;}")
        append("</style></head><body>")
        if (safeId.isEmpty()) {
            // An empty frame rather than an error string: the card only offers a
            // viewer for an id it just read, so this is the malformed case, and
            // the escape hatch under the frame is where the reader goes next.
            append("<div></div>")
        } else {
            append("<iframe id=\"p\" src=\"https://www.youtube-nocookie.com/embed/")
            append(safeId)
            append("?autoplay=1&playsinline=1&rel=0&modestbranding=1&fs=1")
            append("&enablejsapi=1&origin=")
            append(NOVA_TRAILER_ORIGIN_QUERY)
            // The widget referrer is the same origin by definition; the embed
            // API takes it separately, and it is the value the player uses for
            // attribution when the page is not a public site.
            append("&widget_referrer=")
            append(NOVA_TRAILER_ORIGIN_QUERY)
            append("\"")
            // `strict-origin-when-cross-origin` is the policy YouTube documents
            // and the one a browser sends by default. It still sends only the
            // origin cross-origin, which is all YouTube needs, and it stopped
            // being `origin` here so the attribute matches what the platform now
            // recommends rather than a value that happened to work.
            append(" referrerpolicy=\"strict-origin-when-cross-origin\"")
            append(" allow=\"autoplay; encrypted-media; picture-in-picture\"")
            append(" allowfullscreen frameborder=\"0\"></iframe>")
            // Readiness is the IFrame API's onReady event, not the iframe's earlier
            // network load. Until then the WebView stays invisible and the ring stays
            // visible; the independent WebView watchdog remains the last-resort escape.
            append("<script>(function(){")
            append("window.anisequelTrailer={player:null,")
            append("seek:function(offset){var p=this.player;if(!p){return;}")
            append("var duration=p.getDuration()||0;if(duration<=0){return;}")
            append("var target=Math.max(0,Math.min(duration,p.getCurrentTime()+offset));")
            append("p.seekTo(target,true);},")
            append("toggle:function(){var p=this.player;if(!p){return;}")
            append("if(p.getPlayerState()===1){p.pauseVideo();}else{p.playVideo();}}};")
            append("window.onYouTubeIframeAPIReady=function(){")
            append("window.anisequelTrailer.player=new YT.Player('p',{events:{")
            append("onReady:function(){console.log('")
            append(NOVA_TRAILER_PLAYER_READY_MESSAGE)
            append("');},onStateChange:function(event){console.log('")
            append(NOVA_TRAILER_STATE_PREFIX)
            append("'+event.data);}}});};")
            append("var api=document.createElement('script');")
            append("api.src='https://www.youtube.com/iframe_api';")
            append("var first=document.getElementsByTagName('script')[0];")
            append("first.parentNode.insertBefore(api,first);")
            append("})();</script>")
        }
        append("</body></html>")
    }
}
