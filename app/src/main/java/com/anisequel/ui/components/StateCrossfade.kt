package com.anisequel.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * Crossfades a screen between its top-level states.
 *
 * ## Why this is keyed on the state's class
 *
 * A screen's `Success` state is replaced on every refresh, filter change and
 * dashboard keystroke. Keying `AnimatedContent` on the state instance would
 * restart a fade on each of those, so a page that was already on screen would
 * blink every time a widget sync or a search keystroke published - and the two
 * copies of the list would be composed at once. The class key makes only
 * Loading -> Error -> Success transitions animate, which is what a state change
 * actually is here.
 *
 * The size transform is deliberately null. These states are full-screen pages;
 * the default transform measures both the outgoing and incoming pages on every
 * frame of the fade to interpolate a size that does not change, which is a
 * layout pass per frame for no visible movement.
 *
 * [ExpressiveMotion.FastEffects] is the same fade pair the navigation bar and
 * the count badge use, so a page arriving and a tab label arriving feel like
 * one system rather than two speeds.
 */
@Composable
fun <T : Any> StateCrossfade(
    targetState: T,
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        targetState = targetState,
        modifier = modifier,
        transitionSpec = {
            ContentTransform(
                targetContentEnter = fadeIn(ExpressiveMotion.FastEffects),
                initialContentExit = fadeOut(ExpressiveMotion.FastEffects),
                sizeTransform = null
            )
        },
        contentKey = { it::class },
        label = label
    ) { state ->
        content(state)
    }
}
