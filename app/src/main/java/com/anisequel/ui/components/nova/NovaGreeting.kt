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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.anisequel.ui.components.expressive.ExpressiveMotion

private val greetings = listOf(
    "Hey" to "what are we watching today?",
    "Welcome back" to "your next favourite is out there.",
    "Good to see you" to "ready for another episode?",
    "You're back" to "let's find something worth the time.",
    "Hi again" to "where should we pick up?",
    "The next story starts here" to "what are we in the mood for?",
    "Back for more" to "your watchlist missed you.",
    "A new day, a new episode" to "let's see what's waiting."
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
                    text = "${current.first}, $viewerName",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = current.second,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
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

/** Advances the greeting without repeating the one on the previous app visit. */
internal fun nextGreeting(currentIndex: Int, count: Int = greetings.size): Int {
    require(count > 0)
    return (currentIndex + 1).mod(count)
}
