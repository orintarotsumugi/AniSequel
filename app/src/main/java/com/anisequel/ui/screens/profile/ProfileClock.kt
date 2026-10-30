package com.anisequel.ui.screens.profile

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import com.anisequel.ui.components.rememberIsResumed
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * The clock the relative timestamps on the profile's feeds are read against.
 *
 * Hoisted out of `ProfileActivityTab` when the Messages tab arrived: both feeds
 * print "Nm ago", both were keeping their own minute ticker, and two tickers
 * restarting on different frames would let one tab say "5m ago" while the other
 * said "6m ago" about rows a second apart. One helper, called once per visible
 * feed, is the rule.
 *
 * Gated on `rememberIsResumed`, the same helper the toast and the orb use: while
 * the app is behind another one there is nothing on screen to update, so the
 * loop is cancelled rather than ticking into a hidden composition. The first
 * write happens on resume instead of a minute after it, because the clock may
 * have gone stale while the app was paused.
 *
 * A minute is the smallest unit [formatRelativeSeconds] prints, so anything
 * faster would recompose a `LazyColumn` for a label that cannot have changed.
 */
@Composable
internal fun rememberNowEpochSecond(): Long {
    val now = remember { mutableLongStateOf(Instant.now().epochSecond) }
    val isResumed = rememberIsResumed()
    LaunchedEffect(isResumed) {
        if (!isResumed) return@LaunchedEffect
        now.longValue = Instant.now().epochSecond
        while (true) {
            delay(60_000)
            now.longValue = Instant.now().epochSecond
        }
    }
    return now.longValue
}
