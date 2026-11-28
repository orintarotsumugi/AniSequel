package com.anisequel.ui.components.detail.info

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.rememberBoundSliderState
import com.anisequel.ui.components.rememberIsResumed
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.theme.LocalImmersiveMode
import com.anisequel.ui.theme.immersiveDisplayInsets
import kotlinx.coroutines.delay

/**
 * The trailer, playing over the page, in a window the reader can actually drive.
 *
 * ## The shape of it
 *
 * A `Dialog` and not a sheet: this is a video, and a sheet that can be dragged down to a
 * third of the screen is a way to watch a trailer in a letterbox the size of a thumbnail.
 * The window is the whole display, the page behind it is dimmed, and the frame is 16:9 in
 * the middle of the safe area with everything else *under* it rather than over it.
 *
 * The controls are below the frame on purpose, and they are the app's, not a second copy
 * of YouTube's. Drawing play/pause and a seek bar on top of the embed is what the previous
 * design did, and it put the same two controls a thumb's width apart - two pause buttons
 * that could disagree. The embed keeps its own chrome (its play button, its ad skip, its
 * settings) because those can only live inside the player; the app's bar adds what the
 * embed cannot know: how much of *this* trailer is left, ten-second jumps for a thumb on
 * a phone, and one place that says why a video will not play.
 *
 * ## Motion
 *
 * Open and close are one spring, run in opposite directions; the close finishes before the
 * caller is told, so the dialog never disappears on the frame the tap landed. Only the
 * chrome fades - the header, the bar, the frame's black backing. The `WebView` is never
 * alpha-composited: see [createNovaTrailerPlayer] for the audio-with-a-black-picture
 * failure that comes from it.
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
    val session = remember(videoId) { NovaTrailerSession() }
    var isClosing by remember(videoId) { mutableStateOf(false) }

    LaunchedEffect(videoId) {
        entrance.animateTo(1f, ExpressiveMotion.SettleSpatial)
        entrance.snapTo(1f)
    }

    // A dialog is not a destination, so the platform never stops it: the trailer kept
    // playing behind the home screen. The first reading of `rememberIsResumed` is not a
    // transition - it reports false until the activity's own ON_RESUME arrives - and
    // acting on it would pause the player on the frame it was opened.
    val resumed = rememberIsResumed()
    var lastResumed by remember(videoId) { mutableStateOf<Boolean?>(null) }
    LaunchedEffect(resumed) {
        val previous = lastResumed
        lastResumed = resumed
        if (previous == null) return@LaunchedEffect
        if (resumed) session.resumeFromBackground() else session.pauseForBackground()
    }

    LaunchedEffect(videoId, isClosing) {
        if (!isClosing) return@LaunchedEffect
        // Sound first: the exit spring runs for a few hundred milliseconds, and the
        // trailer used to play over all of it. Stopping here takes the picture out
        // whole (the surface cannot be faded), then the chrome fades around nothing.
        session.stop()
        entrance.animateTo(0f, ExpressiveMotion.SettleSpatial)
        entrance.snapTo(0f)
        onDismiss()
    }

    NovaTrailerDialog(
        title = title,
        videoId = videoId,
        session = session,
        entranceProgress = entrance.value,
        closing = isClosing,
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
    videoId: String,
    session: NovaTrailerSession,
    entranceProgress: Float,
    closing: Boolean,
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
            videoId = videoId,
            session = session,
            entranceProgress = entranceProgress,
            closing = closing,
            onClose = onClose,
            onOpenYouTube = onOpenYouTube
        )
    }
}

@Composable
private fun NovaTrailerViewerContent(
    title: String,
    videoId: String,
    session: NovaTrailerSession,
    entranceProgress: Float,
    closing: Boolean,
    onClose: () -> Unit,
    onOpenYouTube: () -> Unit
) {
    val immersive = LocalImmersiveMode.current
    // The chrome fades with the window around it, in both directions. It used to be
    // drawn at full strength from the first frame over a scrim that was still
    // transparent - the header and the controls popped in before the black behind them
    // existed - and on the way out it stayed solid until the window was removed, which
    // is what made the close read as a cut.
    val chromeFade = Modifier.graphicsLayer { alpha = entranceProgress }
    // The frame's black backing only fades on the way out. Fading it in would put the
    // loading ring over the page showing through the frame instead of over black. A
    // background is painted beneath its child and never composites it, so this is not
    // the video-plane fade that `createNovaTrailerPlayer` rules out.
    val backingFade = if (closing) entranceProgress else 1f
    val failed = session.phase as? NovaTrailerPhase.Failed
    var scrubSeconds by remember { mutableStateOf<Float?>(null) }

    // While the finger is on the timeline the page is not asked where it is: the thumb
    // belongs to the reader, and a reading landing mid-drag would pull it back out from
    // under them. Polling rather than the IFrame API's own progress callback, because
    // that callback is not exposed to an embed that only has the player handle.
    LaunchedEffect(session.isPlaying, scrubSeconds != null) {
        session.syncPosition()
        if (!session.isPlaying || scrubSeconds != null) return@LaunchedEffect
        while (true) {
            delay(NOVA_TRAILER_PROGRESS_TICK_MILLIS)
            session.syncPosition()
        }
    }

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
            NovaTrailerHeader(title = title, onClose = onClose, modifier = chromeFade)
            if (failed == null) {
                NovaTrailerStage(
                    videoId = videoId,
                    session = session,
                    closing = closing,
                    backingAlpha = backingFade,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Column(
                    modifier = chromeFade,
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    NovaTrailerTimeline(
                        session = session,
                        scrubSeconds = scrubSeconds,
                        onScrub = { scrubSeconds = it },
                        onScrubFinished = {
                            scrubSeconds?.let(session::seekTo)
                            scrubSeconds = null
                        }
                    )
                    NovaTrailerTransport(session = session)
                    NovaTrailerExternalLink(onClick = onOpenYouTube)
                }
            } else {
                // The frame is gone, not hidden behind a message: a player that cannot
                // play is unmounted, its `WebView` released, and the retry builds a new
                // one - see `NovaTrailerSession.retry`.
                NovaTrailerNotice(
                    code = failed.code,
                    onRetry = session::retry,
                    onOpenYouTube = onOpenYouTube
                )
            }
        }
    }
}

@Composable
private fun NovaTrailerHeader(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
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

/**
 * The 16:9 hole, the ring that fills it, and the page that replaces both.
 *
 * The frame is sized before its contents: [createNovaTrailerPlayer] loads the wrapper on
 * the first layout that has a size, because the embed takes the picture's dimensions from
 * the frame it was given. [aspectRatio] with `matchHeightConstraintsFirst` is what lets a
 * phone held in landscape give the video the height it has.
 */
@Composable
private fun NovaTrailerStage(
    videoId: String,
    session: NovaTrailerSession,
    closing: Boolean,
    backingAlpha: Float,
    modifier: Modifier = Modifier
) {
    val html = remember(videoId) { novaTrailerEmbedHtml(videoId) }
    // The factory below runs once and keeps the lambda it was handed, so a captured
    // `closing` would be the value from the frame the player was built - false, for the
    // rest of the dialog's life - and every reveal would be treated as "not dismissing".
    // The state object is what the close writes; the lambda reads it when it is called.
    val dismissing by rememberUpdatedState(closing)
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .aspectRatio(16f / 9f, matchHeightConstraintsFirst = true)
                .clip(MaterialTheme.shapes.medium)
                .background(Color.Black.copy(alpha = backingAlpha))
        ) {
            if (!session.revealed) {
                NovaWavyRing(
                    progress = null,
                    color = Color.White,
                    trackColor = Color.White.copy(alpha = 0.2f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(44.dp)
                )
            }
            // Keyed on the attempt: a retry must build a new `WebView`, not reload the
            // one that just failed. The old view is released first, so its page is
            // destroyed before the new one is created.
            key(session.attempt) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("nova_trailer_player"),
                    factory = { context ->
                        createNovaTrailerPlayer(
                            context = context,
                            html = html,
                            isDismissing = { dismissing },
                            onReveal = session::onRevealed,
                            onSignal = session::onSignal
                        ).also { session.attach(it) }
                    },
                    onRelease = { view ->
                        session.detach(view)
                        // Remove a fullscreen surface first, or it would outlive its
                        // `WebView` in the dialog. Then stop before destroy: an embed
                        // still buffering when the reader closes the viewer would tear
                        // down a live media pipeline otherwise.
                        (view.webChromeClient as? NovaTrailerChromeClient)?.hideCustomView()
                        view.stopLoading()
                        view.destroy()
                    }
                )
            }
        }
    }
}

/**
 * Elapsed, a draggable track, and the total.
 *
 * The slider follows a draft only while it is being dragged: [scrubSeconds] is what the
 * finger says, and the committed seek happens on release, so a slow network cannot fight
 * the thumb. It is a Slider bound with `rememberBoundSliderState` rather than the retired
 * value-first form: Material 3 replaced that one because a state object which only reads
 * its initial value stops following an external write, and this slider is written by the
 * player four times a second.
 */
@Composable
private fun NovaTrailerTimeline(
    session: NovaTrailerSession,
    scrubSeconds: Float?,
    onScrub: (Float) -> Unit,
    onScrubFinished: () -> Unit
) {
    val end = session.durationSeconds
    val limit = if (end > 0f) end else 1f
    val shown = (scrubSeconds ?: session.positionSeconds).coerceIn(0f, limit)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = formatNovaTrailerTime(shown),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
            // A fixed lane on both clocks: "0:09" growing into "0:10" while the thumb
            // is down would move the track under the finger.
            modifier = Modifier.width(NOVA_TRAILER_TIME_LANE)
        )
        Slider(
            state = rememberBoundSliderState(value = shown, trackRange = 0f..limit),
            onValueChange = onScrub,
            onValueChangeFinished = onScrubFinished,
            enabled = session.seekEnabled,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.24f),
                activeTickColor = Color.Transparent,
                inactiveTickColor = Color.Transparent,
                disabledThumbColor = Color.White.copy(alpha = 0.38f),
                disabledActiveTrackColor = Color.White.copy(alpha = 0.38f),
                disabledInactiveTrackColor = Color.White.copy(alpha = 0.16f)
            ),
            modifier = Modifier
                .weight(1f)
                .testTag("nova_trailer_timeline")
        )
        Text(
            text = if (end > 0f) formatNovaTrailerTime(end) else "--:--",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White.copy(alpha = 0.82f),
            textAlign = TextAlign.End,
            modifier = Modifier.width(NOVA_TRAILER_TIME_LANE)
        )
    }
}

@Composable
private fun NovaTrailerTransport(session: NovaTrailerSession) {
    val enabled = session.controlsEnabled
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        NovaTrailerSkipButton(
            label = "−10s",
            description = "Rewind 10 seconds",
            testTag = "nova_trailer_rewind",
            enabled = enabled
        ) {
            session.seekBy(-NOVA_TRAILER_SKIP_SECONDS)
        }
        NovaTrailerPlayPauseButton(
            enabled = enabled,
            isPlaying = session.isPlaying,
            hasEnded = session.hasEnded,
            onClick = session::togglePlayback
        )
        NovaTrailerSkipButton(
            label = "+10s",
            description = "Forward 10 seconds",
            testTag = "nova_trailer_forward",
            enabled = enabled
        ) {
            session.seekBy(NOVA_TRAILER_SKIP_SECONDS)
        }
    }
}

@Composable
private fun NovaTrailerSkipButton(
    label: String,
    description: String,
    testTag: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = if (enabled) 0.14f else 0.06f),
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
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
                color = Color.White.copy(alpha = if (enabled) 1f else 0.38f)
            )
        }
    }
}

@Composable
private fun NovaTrailerPlayPauseButton(
    enabled: Boolean,
    isPlaying: Boolean,
    hasEnded: Boolean,
    onClick: () -> Unit
) {
    val label = when {
        hasEnded -> "Replay the trailer"
        isPlaying -> "Pause trailer"
        else -> "Play trailer"
    }
    Surface(
        shape = CircleShape,
        color = Color.White.copy(alpha = if (enabled) 0.2f else 0.08f),
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClickLabel = label,
                onClick = onClick
            )
            .testTag("nova_trailer_play_pause")
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (isPlaying) AppVectorIcons.Pause else AppVectorIcons.Play,
                contentDescription = label,
                tint = Color.White.copy(alpha = if (enabled) 1f else 0.38f),
                modifier = Modifier.size(28.dp)
            )
        }
    }
}

/**
 * What the reader sees when YouTube refuses the video: the reason, and the ways out.
 *
 * A frame that cannot play is not left on screen with its controls live over it - that
 * was the old behaviour, and it made a refusal look like a player with a broken picture.
 * The retry is only offered when it could plausibly answer differently
 * ([isNovaTrailerRetryable]); "the owner has turned off playback outside YouTube" is the
 * one refusal that can never be retried, and the button beside it is the way that always
 * works.
 */
@Composable
private fun NovaTrailerNotice(
    code: Int,
    onRetry: () -> Unit,
    onOpenYouTube: () -> Unit
) {
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
            text = novaTrailerFailureReason(code),
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.86f),
            textAlign = TextAlign.Center
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isNovaTrailerRetryable(code)) {
                TextButton(
                    onClick = onRetry,
                    colors = ButtonDefaults.textButtonColors(contentColor = Color.White),
                    modifier = Modifier.testTag("nova_trailer_retry")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Try again")
                }
            }
            Button(
                onClick = onOpenYouTube,
                modifier = Modifier.testTag("nova_trailer_external")
            ) {
                Text("Watch on YouTube")
            }
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
        Row(
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(
                    role = Role.Button,
                    onClickLabel = "Open the trailer on YouTube",
                    onClick = onClick
                )
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("nova_trailer_external"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = AppVectorIcons.YouTube,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.86f),
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = "Open on YouTube",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.86f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Ten seconds: long enough to skip a recap, short enough to land inside a scene. */
private const val NOVA_TRAILER_SKIP_SECONDS = 10f

/**
 * How often the page is asked where playback is.
 *
 * Four readings a second is the coarsest clock that still looks continuous under a
 * thumb, and each one is an `evaluateJavascript` round trip - a faster poll would spend
 * the reader's battery redrawing a number nobody can read.
 */
private const val NOVA_TRAILER_PROGRESS_TICK_MILLIS = 250L

/** One lane for each clock, so the track does not move while a digit changes. */
private val NOVA_TRAILER_TIME_LANE: Dp = 44.dp
