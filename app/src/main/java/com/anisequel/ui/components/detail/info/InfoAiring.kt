package com.anisequel.ui.components.detail.info

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.airingAtSeconds
import com.anisequel.data.model.airingCountdown
import com.anisequel.ui.components.rememberIsResumed
import kotlinx.coroutines.delay

/**
 * How often the airing countdown is recomputed.
 *
 * A minute, because [com.anisequel.data.model.AiringCountdown] speaks in whole
 * days, hours and minutes: recomputing faster would redraw the card to print the
 * same string.
 */
private const val COUNTDOWN_TICK_MILLIS = 60_000L

/**
 * Time until the next episode, recomputed while the reader is looking at it.
 *
 * ## Why this is a clock and not a string
 *
 * The Info page used to call `media.airingCountdown()` once, in composition. That
 * is a value with an expiry date and no clock: a sheet left open across the
 * boundary went on saying "airs in 5m" after the episode had aired, and a page
 * opened an hour before an episode said the same thing an hour later. This is a
 * `produceState` now, so the line expires with the thing it describes.
 *
 * ## Why it stops with the screen
 *
 * Gated on `rememberIsResumed`, like every other clock in the app: a page behind a
 * stopped Activity has nobody reading its countdown, and a minute-by-minute
 * recomposition of a card is not something to spend on a screen that is not
 * there. Nothing is lost by pausing - the producer recomputes from
 * `System.currentTimeMillis()` on resume, so it does not "catch up" through a
 * backlog of ticks, it simply states the right number.
 *
 * ## Why there is only ever one of these on the page
 *
 * Both callers are gated on the same condition, from opposite sides: the progress
 * card asks whenever it is drawn, and the details card asks only when there is no
 * progress card. Two countdowns a scroll apart would tick out of step and disagree
 * by up to a minute, which reads as a bug in whichever one the reader looked at
 * second.
 *
 * Null means "there is nothing to count down to" - no scheduled episode, or one
 * that has already aired - and every caller hides its line on null.
 */
@Composable
internal fun rememberAiringCountdown(media: MediaNode): String? {
    val resumed = rememberIsResumed()
    val airingAt = media.airingAtSeconds

    val countdown by produceState(
        initialValue = if (resumed) media.airingCountdown() else null,
        airingAt,
        resumed
    ) {
        if (!resumed || airingAt == null) return@produceState
        while (true) {
            value = media.airingCountdown()
            delay(COUNTDOWN_TICK_MILLIS)
        }
    }

    return countdown
}
