package com.anisequel.ui.components.nova

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
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

/** A greeting selected on each app resume, with a calm content transition. */
@Composable
fun NovaGreeting(viewerName: String, modifier: Modifier = Modifier) {
    var greetingIndex by remember { mutableIntStateOf(greetings.indices.random()) }
    LifecycleResumeEffect(Unit) {
        greetingIndex = nextGreeting(greetingIndex)
        onPauseOrDispose { }
    }
    val greeting = greetings[greetingIndex]
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedContent(
            targetState = greeting.first,
            transitionSpec = {
                fadeIn(ExpressiveMotion.FastEffects) togetherWith fadeOut(ExpressiveMotion.FastEffects)
            },
            label = "nova_greeting"
        ) { line ->
            Text(
                text = "$line, $viewerName",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
        Text(
            text = greeting.second,
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

/** Advances the greeting without repeating the one on the previous app visit. */
internal fun nextGreeting(currentIndex: Int, count: Int = 8): Int {
    require(count > 0)
    return (currentIndex + 1).mod(count)
}
