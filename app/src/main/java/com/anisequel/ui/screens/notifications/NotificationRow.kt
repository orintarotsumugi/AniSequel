package com.anisequel.ui.screens.notifications

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import com.anisequel.domain.notifications.NotificationGroup
import com.anisequel.ui.components.expressive.ExpressiveMotion
import java.time.Instant
import kotlinx.coroutines.delay

/**
 * How one inbox row moves, in one place because the list has two halves.
 *
 * ## Why the fade in is null
 *
 * [NotificationEntrance] already fades every card in, from its own alpha and on the
 * reader's motion style. `animateItem()`'s default fades it in as well, and the two
 * multiplied: a new row arrived at the *square* of the curve its neighbours were on,
 * visibly slower than the rows above it, and only for the cards new enough to still
 * be animating. The exit stays - a card on its way out has no entrance left to run,
 * the item is already gone from the list by then.
 *
 * ## Why the specs are named
 *
 * The bare `animateItem()` this replaced used its own defaults, which are inline
 * springs that know nothing about the app's motion tokens: a reader who chose Instant
 * motion still got list items sliding into place. These are the same three the rest
 * of the app uses, so an inbox row moves the way a library row does.
 */
internal fun LazyItemScope.notificationRowPlacement(): Modifier = Modifier.animateItem(
    placementSpec = ExpressiveMotion.DefaultSpatialOffset,
    fadeInSpec = null,
    fadeOutSpec = ExpressiveMotion.ListItemFadeOut
)

/**
 * One inbox row, and the entrance it plays when it is new.
 *
 * The list says where a row goes; this says how the row arrives. They are two jobs,
 * which is why they live in two files: the screen owns sections, filters and paging,
 * and a row's motion has no reason to change when any of those do.
 */
@Composable
internal fun NotificationEntrance(
    group: NotificationGroup,
    index: Int,
    now: Instant,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Saved, not merely remembered: a `LazyColumn` discards the composition of the
    // rows it scrolls past, so a plain `remember` replayed the fade-in every time a
    // card came back into view - a list that looked like it was re-arriving on every
    // scroll. The key stays the group, so a genuinely new notification still enters.
    var shown by rememberSaveable(group.key) { mutableStateOf(false) }
    LaunchedEffect(group.key) {
        if (index < 10) delay(index * 26L)
        shown = true
    }
    val alpha by animateFloatAsState(
        targetValue = if (shown) 1f else 0f,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "inbox_card_fade"
    )
    val lift by animateFloatAsState(
        targetValue = if (shown) 0f else 12f,
        animationSpec = ExpressiveMotion.DefaultSpatial,
        label = "inbox_card_lift"
    )
    // The card already faded and rose; the small scale closes the entrance on
    // the same spatial spring as the rest of the app instead of leaving the last
    // frame to a hard alpha cut. These are draw-phase values, so the list is not
    // re-measured on every frame.
    val scale by animateFloatAsState(
        targetValue = if (shown) 1f else 0.965f,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "inbox_card_scale"
    )
    NotificationCard(
        group = group,
        onClick = onOpen,
        now = now,
        modifier = modifier.graphicsLayer {
            this.alpha = alpha
            translationY = lift
            scaleX = scale
            scaleY = scale
        }
    )
}
