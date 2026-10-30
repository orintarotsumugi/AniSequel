package com.anisequel.ui.components.detail.info

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The controls that take over once the hero has scrolled away.
 *
 * The hero's own pill leaves the screen with the hero - which is what the reference
 * does, and it means that on a twelve-card page the only way out is the sheet's own
 * swipe. This is the same two actions, docked, arriving as the lockup hands its title
 * over: the two are timed against each other by [DOCKED_BAR_AT] and [LOCKUP_FADE_FROM]
 * in `InfoMotion.kt`. `visible` is computed from a `derivedStateOf` at the call site,
 * so the crossing recomposes this bar once rather than on every scrolled frame.
 *
 * ## The title pill is a control, and it carries the two things this page lacked
 *
 * A fourteen-section page has no orientation and no way back. The pill is both: the
 * label is a *button* that returns to the hero, and the same pill carries [progress]
 * as a wash that fills from the left as the reader descends. Both live inside the one
 * control rather than being added as a fourth floating element, because everything
 * floating over this page already competes with the content behind it.
 *
 * The wash is `scaleX` on a full-size box, read inside `graphicsLayer`, so it is a
 * draw-phase transform: the bar does not recompose on the frames the reader is
 * scrolling - the same reason the hero's parallax is written the way it is. [progress]
 * is a function rather than a value for the same reason the hero's `collapse` is one.
 *
 * ## The bug this replaces
 *
 * The title `Surface` took `weight(1f, fill = false)` and a `Spacer` then took
 * `weight(1f)` *after* it, so two weighted children split the free width in half:
 * the title was measured against roughly half the bar and ellipsized there no matter
 * how much room the bar really had. On screen that was a long title cut to
 * `MARRIAGETO...` with a third of the screen sitting empty beside it. With one
 * weighted child the title still hugs its content (`fill = false`, so a short title's
 * pill stays short) but is measured against every dp the controls are not using.
 */
@Composable
fun InfoFloatingBar(
    visible: Boolean,
    title: String,
    progress: () -> Float,
    onScrollToTop: () -> Unit,
    onOpenAniList: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val container = MaterialTheme.colorScheme.surfaceContainerHigh
    val onContainer = MaterialTheme.colorScheme.onSurface
    val wash = MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
    val pill = ExpressiveShapes.pill
    val titleInteraction = remember { MutableInteractionSource() }

    AnimatedVisibility(
        visible = visible,
        // It docks rather than blinks: it comes down from the edge it belongs to,
        // on the spatial spring, so the bar arrives with the feel of a control
        // settling into place. Under `INSTANT` both the slide and the fade are a
        // `snap`, which is the style saying exactly that.
        enter = slideInVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> -height } +
            fadeIn(animationSpec = ExpressiveMotion.FastEffects),
        exit = slideOutVertically(animationSpec = ExpressiveMotion.DefaultSpatialOffset) { height -> -height } +
            fadeOut(animationSpec = ExpressiveMotion.FastEffects),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = pill,
                color = container,
                shadowElevation = 6.dp,
                modifier = Modifier
                    // `fill = true`, not the old `fill = false`: a hugging pill
                    // left the bar visibly left-weighted and the title ran out of
                    // room while the right third of the screen sat empty. The
                    // actions keep their own pill, and the title takes every dp
                    // they are not using.
                    .weight(1f)
                    .heightIn(min = 48.dp)
                    .clip(pill)
                    // `onClickLabel` rather than a `contentDescription` on the icon:
                    // the pill's own text is what should be announced, and the action
                    // is what a screen reader needs said after it. Overwriting the
                    // description with "Scroll to top" would replace the title.
                    //
                    // The press spring is lighter than the icon buttons': this is a
                    // wide pill, and a 0.84 scale on a full-width capsule reads as the
                    // bar itself jumping.
                    .bouncyPress(pressedScale = 0.97f, interactionSource = titleInteraction)
                    .clickable(
                        interactionSource = titleInteraction,
                        role = Role.Button,
                        onClickLabel = "Scroll to top",
                        onClick = onScrollToTop
                    )
                    .testTag("info_bar_title")
            ) {
                Box {
                    // The progress is a line at the bottom edge, not a wash over
                    // the whole pill. The old wash was a rectangle clipped by the
                    // capsule, so its leading edge cut straight through the middle
                    // of the title and the bar read as two different pills glued
                    // together. A 3dp line is legible as progress and cannot sit
                    // under a glyph.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                            .height(3.dp)
                            .graphicsLayer {
                                scaleX = progress()
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .background(wash)
                            .testTag("info_bar_progress")
                    )
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.ExpandLess,
                            contentDescription = null,
                            tint = onContainer,
                            modifier = Modifier.size(17.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                        // Balances the leading chevron so the title is centred in
                        // the pill rather than in the space left after the icon.
                        Spacer(modifier = Modifier.width(23.dp))
                    }
                }
            }

            Spacer(modifier = Modifier.width(8.dp))

            Surface(
                shape = pill,
                color = container,
                shadowElevation = 6.dp
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    HeroIconButton(
                        icon = AppVectorIcons.OpenInBrowser,
                        label = "Open on AniList",
                        onClick = onOpenAniList,
                        tint = onContainer
                    )
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(22.dp)
                            .background(onContainer.copy(alpha = 0.18f))
                    )
                    HeroIconButton(
                        icon = AppVectorIcons.Close,
                        label = "Close",
                        onClick = onClose,
                        tint = onContainer
                    )
                }
            }
        }
    }
}

/**
 * One icon in a hero pill.
 *
 * Deliberately not an `IconButton`: the pill draws the surface, the divider and the
 * ripple clipping as one control, and Material's own button brings its own container
 * and padding that would leave the divider floating inside a gap.
 *
 * 48dp is the platform minimum touch target and this used to be 46dp, which put
 * both the hero's own controls and the docked bar's below it. The two that matter
 * most are the ones a reader reaches for one-handed at the bottom of the screen, so
 * the shortfall was felt rather than merely linted.
 *
 * `internal` rather than file-private because both surfaces draw it: the hero's own
 * action pill and the docked bar above.
 */
@Composable
internal fun HeroIconButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: Color = Color.White
) {
    // One source for the press spring and the click, so the spring actually sees
    // the press and the ripple is not drawn by a second, competing pipeline - the
    // same pairing `SpoilerChip` and `HeroChip` use.
    val interactionSource = remember { MutableInteractionSource() }
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .bouncyPress(pressedScale = 0.84f, interactionSource = interactionSource)
            .clickable(
                interactionSource = interactionSource,
                role = Role.Button,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = tint,
            modifier = Modifier.size(19.dp)
        )
    }
}
