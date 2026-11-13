package com.anisequel.ui.components.nova

import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anisequel.data.repository.MotionStyle
import com.anisequel.ui.components.expressive.ExpressiveMotion

private val greetings = listOf(
    "Hey" to "what are we watching today?",
    "Welcome back" to "your next favourite is out there.",
    "Good to see you" to "ready for another episode?",
    "You're back" to "let's find something worth the time.",
    "Hi again" to "where should we pick up?",
    "The next story starts here" to "what are we in the mood for?",
    "Back for more" to "your watchlist missed you.",
    "A new day, a new episode" to "let's see what's waiting.",
    "There you are" to "I kept the good ones for you.",
    "Missed you" to "the queue did not move without you.",
    "Evening, watcher" to "shall we continue where we stopped?",
    "Fresh page" to "one more chapter never hurt anyone.",
    "You made it" to "the shelf has been waiting.",
    "Look who's back" to "there is always room for one more.",
    "Ready when you are" to "the next episode is already queued.",
    "Another day, another arc" to "let's see how this one ends.",
    "The queue is awake" to "your sleep schedule is not.",
    "Welcome to the next arc" to "the recap is optional. Probably.",
    "Good timing" to "the plot is about to make a terrible decision.",
    "The shelf is calling" to "it has opinions about your backlog.",
    "You returned" to "the cliffhanger is still being dramatic.",
    "Let us pretend it is one episode" to "we both know that is a lie.",
    "A wild watcher appears" to "the watchlist has prepared a boss fight.",
    "The opening is ready" to "your emotional damage can begin.",
    "Welcome back, legend" to "there is still no finale date.",
    "The next chapter waits" to "unlike the sequel announcement.",
    "You brought taste" to "or at least a very committed opinion.",
    "Good evening, protagonist" to "the filler arc has snacks.",
    "Your backlog survived" to "somehow, it got longer.",
    "The watchlist has spoken" to "it wants one more episode."
)

/**
 * A greeting selected on each app resume, with one calm content transition.
 *
 * The whole block fades as one piece. It used to wrap only the first line in an
 * `AnimatedContent`, so on every resume "Hey, name" faded while "what are we
 * watching today?" changed underneath it without moving - two halves of one
 * sentence arriving at two different times. The pair is the target now.
 *
 * The first resume is the one this component is *composed* into, not a return
 * visit. Advancing there too made the freshly chosen greeting vanish on the frame
 * after it appeared, which read as a stutter on launch. `hasResumed` lets that
 * first callback through without consuming a greeting.
 *
 * `Crossfade` rather than `AnimatedContent`: every greeting is the same block of
 * three centred lines at the same width, so there is nothing to size-animate, and
 * the size transform `AnimatedContent` adds was measuring both blocks on every
 * resume to animate a height that does not change.
 */
@Composable
fun NovaGreeting(viewerName: String, modifier: Modifier = Modifier) {
    var greetingIndex by remember { mutableIntStateOf(greetings.indices.random()) }
    var hasResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (hasResumed) {
            greetingIndex = nextGreeting(greetingIndex)
        } else {
            hasResumed = true
        }
        onPauseOrDispose { }
    }
    val greeting = greetings[greetingIndex]
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Crossfade(
            targetState = greeting,
            animationSpec = ExpressiveMotion.FastEffects,
            label = "nova_greeting"
        ) { current ->
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    // The name is the one word on this screen that belongs to
                    // the reader, so it is the one word in the accent colour
                    // rather than another run of body text.
                    text = buildAnnotatedString {
                        append("${current.first}, ")
                        withStyle(
                            SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.ExtraBold
                            )
                        ) { append(viewerName) }
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                TypewriterLine(
                    text = current.second,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Find your favourite anime or manga, manhwa or whatever you like!",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(0.92f)
                )
            }
        }
    }
}

/**
 * The greeting's second line, typed on one character step per frame.
 *
 * One state change per frame rather than one per letter: the text is a `String`
 * slice, and a 60fps reveal needs 60 recompositions of a single `Text`, which is
 * cheaper than a per-letter `AnimatedContent` and does not animate the block's
 * width. Two characters per frame keeps a long line under a second.
 *
 * The caret is part of the drawn string so the space it occupies does not collapse
 * the line while the text is still arriving.
 *
 * A screen reader gets the finished sentence through semantics: it cannot meaningfully
 * follow a glyph-by-glyph reveal, and reading a partial sentence aloud and then
 * restarting it is worse than reading the answer once.
 */
@Composable
private fun TypewriterLine(
    text: String,
    style: androidx.compose.ui.text.TextStyle,
    color: androidx.compose.ui.graphics.Color
) {
    val instant = ExpressiveMotion.speed == MotionStyle.INSTANT
    var shown by remember(text) { mutableIntStateOf(0) }
    LaunchedEffect(text, instant) {
        if (instant) {
            shown = text.length
            return@LaunchedEffect
        }
        shown = 0
        while (shown < text.length) {
            withFrameNanos { }
            shown = (shown + 2).coerceAtMost(text.length)
        }
    }
    val typing = shown < text.length
    Text(
        text = text.take(shown) + if (typing) "▏" else "",
        style = style,
        fontWeight = FontWeight.Bold,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = text }
    )
}

/** Advances the greeting without repeating the one on the previous app visit. */
internal fun nextGreeting(currentIndex: Int, count: Int = greetings.size): Int {
    require(count > 0)
    return (currentIndex + 1).mod(count)
}
