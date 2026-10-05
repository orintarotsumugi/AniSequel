package com.example.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.delay

/**
 * The rotating icon rail on the sign-in screen.
 *
 * This replaces the single static brand icon the hero used to draw. A cold
 * sign-in screen is the one place the app has nothing to do but wait for the
 * user, so it earns a little motion - and the icons are the app's own vocabulary
 * (search a profile, the sequel leap, save to planning, the catalogue), which is
 * a better first impression than one logo repeated on every launch.
 *
 * ## Why a plain [AnimatedContent] and not a `HorizontalPager`
 *
 * A pager would put all four icons in the composition and translate a strip of
 * them, so every frame of the crossfade would draw and measure four 44dp icons
 * to show one, and the scroll state would have to be kept in step with the
 * timer. This is a decorative rail on the login screen: it should cost
 * essentially nothing on the frame the user is actually looking at. Advancing
 * one integer and crossfading two icons keeps it to a single icon drawn at a
 * time.
 *
 * ## The timer
 *
 * [LaunchedEffect] is keyed on the icon count and the interval, not on the
 * current index, so the delay loop is *not* restarted by its own tick -
 * restarting it every step is how a naive auto-advance turns into a stutter
 * under load. [delay] is cancellable, so when the composable leaves (the user
 * taps Sign In) the coroutine is cancelled with it and nothing keeps ticking in
 * the background.
 */
@Composable
fun IconCarousel(
    modifier: Modifier = Modifier,
    /** How long each icon is shown before the next crossfade begins. */
    intervalMillis: Long = 2600L
) {
    val icons: List<ImageVector> = remember {
        listOf(
            AppVectorIcons.Search,
            AppVectorIcons.SequelJump,
            AppVectorIcons.BookmarkAdd,
            AppVectorIcons.List
        )
    }

    var index by remember { mutableIntStateOf(0) }

    LaunchedEffect(icons.size, intervalMillis) {
        while (true) {
            delay(intervalMillis)
            index = (index + 1) % icons.size
        }
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .testTag("login_icon_carousel"),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer,
                            MaterialTheme.colorScheme.tertiaryContainer
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    // Fade plus a small scale, so the swap reads as a morph
                    // rather than a flicker. Fast on the way out, springy on the
                    // way in - springs come from the app's ExpressiveMotion
                    // tokens because this runs continuously rather than in
                    // response to a user action.
                    (
                        fadeIn(ExpressiveMotion.DefaultEffects) + scaleIn(
                            initialScale = 0.72f,
                            animationSpec = ExpressiveMotion.BouncySpatial
                        )
                        ) togetherWith (
                        fadeOut(ExpressiveMotion.FastEffects) + scaleOut(
                            targetScale = 0.82f,
                            animationSpec = ExpressiveMotion.FastSpatial
                        )
                        )
                },
                label = "login_icon_carousel"
            ) { current ->
                Icon(
                    imageVector = icons[current],
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        // Progress dots: four tiny capsules, the active one stretched.
        // The width is painted, not measured: it animates inside the draw
        // phase, so every tick of the spring costs a single draw rather than
        // a layout pass on the row.
        Row(verticalAlignment = Alignment.CenterVertically) {
            val density = LocalDensity.current
            icons.forEachIndexed { dotIndex, _ ->
                val selected = dotIndex == index
                val width by animateFloatAsState(
                    targetValue = if (selected) with(density) { 16.dp.toPx() }
                        else with(density) { 6.dp.toPx() },
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    ),
                    label = "carousel_dot_$dotIndex"
                )
                val color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
                }

                Box(
                    modifier = Modifier
                        .padding(horizontal = 3.dp)
                        .size(width = 16.dp, height = 6.dp)
                        .drawBehind {
                            drawRoundRect(
                                color = color,
                                topLeft = Offset.Zero,
                                size = Size(width = width, height = size.height),
                                cornerRadius = CornerRadius(size.height / 2f)
                            )
                        }
                )
            }
        }
    }
}
