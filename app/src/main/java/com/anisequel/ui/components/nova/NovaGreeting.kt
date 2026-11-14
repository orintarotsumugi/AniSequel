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
import androidx.compose.runtime.saveable.rememberSaveable
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
    "The watchlist has spoken" to "it wants one more episode.",
    "One more, surely" to "the credits are not a stopping point.",
    "The night is young" to "the next episode is not.",
    "Back on the couch" to "the remote remembers you.",
    "Rise and binge" to "the queue has been patient.",
    "Your seat is warm" to "the next arc never waited.",
    "The plot thickens" to "as if it needed help.",
    "A familiar face" to "the watchlist kept your place.",
    "Here again" to "the backlog has notes.",
    "The story is waiting" to "it has been dramatic about it.",
    "Ready for the next one" to "the cliffhanger is not going anywhere.",
    "Same time, same arc" to "the recap will not watch itself.",
    "Welcome to the queue" to "it has a very strict policy.",
    "Another episode" to "we can stop whenever we want. Allegedly.",
    "The shelf noticed" to "it is trying to look casual.",
    "You are early" to "the opening song is still playing.",
    "The episode dropped" to "so did your evening plans.",
    "Good to have you" to "the next chapter is already impatient.",
    "The playlist is ready" to "your watch history is judging you.",
    "You have returned" to "the filler arc brought snacks.",
    "Fresh episode energy" to "the fridge is on its own.",
    "One more chapter" to "the 'just one' part is a formality.",
    "A new arc awaits" to "the previous one left a mess.",
    "The watchlist stretches" to "you are doing great. Probably.",
    "Back for a visit" to "the stories kept the light on.",
    "Your stories are here" to "and they have been talking about you.",
    "Time to catch up" to "the spoilers are getting bold.",
    "The next scene is ready" to "it has been waiting politely.",
    "Anime o'clock" to "the obligations will have to wait.",
    "Manga o'clock" to "the chapters will not read themselves.",
    "The couch calls" to "the next episode answers.",
    "Binge responsibly" to "the credits are lying about the ending.",
    "Plot twist incoming" to "you are already holding the remote.",
    "Welcome to the intermission" to "there is no intermission.",
    "The season continues" to "your free time is leaving the chat.",
    "Back in the groove" to "the openings are still absolute bangers.",
    "The queue is flat" to "your standards are not.",
    "Your true ending" to "is one episode away. Always is.",
    "Ready for damage" to "this one is emotional, probably.",
    "The watchlist winked" to "it has been reading ahead.",
    "A quiet evening" to "the volume button disagrees.",
    "The arc begins" to "the budget does too. Somewhere.",
    "Soft launch into episode two" to "that is how it always starts.",
    "You kept the seat" to "the next episode is still warm.",
    "The recap has ended" to "the consequences have not.",
    "Welcome back, reader" to "the bookmark held the place.",
    "Ready for the reveal" to "it has been obvious for three episodes.",
    "The finale approaches" to "and the sequel has not been announced.",
    "Another world" to "same couch, better stakes.",
    "The shelf applauds" to "quietly, the way shelves do.",
    "You look prepared" to "for at least four more episodes.",
    "The hiatus is over" to "so is your free time.",
    "Back for the arc" to "it missed you more than it admits.",
    "Nice to see you" to "the next chapter has been practicing.",
    "Your eyes only" to "the next episode is already playing. Sorry.",
    "A good day for it" to "whatever 'it' turns out to be.",
    "The list survived" to "your sense of restraint did not.",
    "Here for the good stuff" to "the queue has a few ideas.",
    "Off the cliffhanger" to "and into another one.",
    "The season is stacked" to "your sleep schedule is not."
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
    // Saved rather than remembered: a rotation is not a new visit, and re-drawing
    // a different greeting because the phone turned read as the app losing the
    // line it had just shown. The pool is also walked without an immediate repeat
    // - see [nextRandomGreeting] - because with a pool this size a sequential
    // walk still spends the first few visits saying neighbouring jokes rather
    // than showing the range.
    var greetingIndex by rememberSaveable {
        mutableStateOf(greetings.indices.random())
    }
    var hasResumed by remember { mutableStateOf(false) }
    LifecycleResumeEffect(Unit) {
        if (hasResumed) {
            greetingIndex = nextRandomGreeting(greetingIndex)
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
                        // A name is the part of the greeting that belongs to the
                        // reader; when the viewer query has not answered yet there
                        // is no name, and "Hey, " with a dangling comma reads as a
                        // broken string rather than as a greeting.
                        append(current.first)
                        if (viewerName.isNotBlank()) {
                            append(", ")
                            withStyle(
                                SpanStyle(
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.ExtraBold
                                )
                            ) { append(viewerName) }
                        }
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

/**
 * The next greeting, chosen from anywhere in the pool except the current line.
 *
 * "Anywhere" rather than the next index: on a resume the sequential walk is a
 * fixed order, so the tenth visit is predictable from the first and every reader
 * sees the same sequence. The offset excludes zero, which is the one thing a
 * greeting on a new visit must not do - repeat the sentence still on screen.
 */
internal fun nextRandomGreeting(
    currentIndex: Int,
    count: Int = greetings.size,
    random: kotlin.random.Random = kotlin.random.Random.Default
): Int {
    require(count > 0)
    if (count == 1) return 0
    val offset = random.nextInt(count - 1) + 1
    return (currentIndex + offset).mod(count)
}
