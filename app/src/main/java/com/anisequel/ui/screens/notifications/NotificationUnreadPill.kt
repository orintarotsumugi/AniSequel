package com.anisequel.ui.screens.notifications

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveMotion

/**
 * The unread count in the inbox header.
 *
 * Split from the center screen so that file stays under the repository's
 * 900-line ceiling. The pill is self-contained: it owns two colour animations
 * and the count transition, and nothing else on the screen reads them.
 */
@Composable
internal fun UnreadPill(unreadCount: Int) {
    val description = when {
        unreadCount <= 0 -> "All notifications read"
        unreadCount == 1 -> "1 unread notification"
        else -> "$unreadCount unread notifications"
    }
    // The container and the label used to jump between two schemes while the
    // text cross-faded. Animating the colours makes the pill feel like one
    // Material surface changing state rather than two views swapping places.
    val pillColor by animateColorAsState(
        targetValue = if (unreadCount > 0) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "unread_pill_container"
    )
    val labelColor by animateColorAsState(
        targetValue = if (unreadCount > 0) {
            MaterialTheme.colorScheme.onPrimary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "unread_pill_label"
    )
    Surface(
        shape = CircleShape,
        color = pillColor,
        modifier = Modifier
            .clearAndSetSemantics { contentDescription = description }
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
    ) {
        AnimatedContent(
            targetState = unreadCount,
            transitionSpec = {
                // A small scale on the way in/out reads as the count settling into
                // the pill. The old pure cross-fade was the one place in this
                // screen with no spatial motion at all.
                fadeIn(ExpressiveMotion.FastEffects) +
                    scaleIn(
                        initialScale = 0.82f,
                        animationSpec = ExpressiveMotion.FastSpatial
                    ) togetherWith (
                    fadeOut(ExpressiveMotion.FastEffects) +
                        scaleOut(
                            targetScale = 0.82f,
                            animationSpec = ExpressiveMotion.FastSpatial
                        )
                    ).using(null)
            },
            label = "unread_pill_count"
        ) { count ->
            Text(
                text = when {
                    count <= 0 -> "All read"
                    count > 99 -> "99+ new"
                    else -> "$count new"
                },
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = labelColor,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.padding(horizontal = 11.dp, vertical = 5.dp)
            )
        }
    }
}
