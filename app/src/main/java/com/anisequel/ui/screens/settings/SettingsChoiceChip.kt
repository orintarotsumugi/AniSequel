package com.anisequel.ui.screens.settings

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.theme.AniSequelTheme
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.rememberStateColor
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * One pill choice, everywhere in Settings.
 *
 * ## Why this is one composable and not five
 *
 * Five pages had grown their own selected/unselected pill - the Jokes page's
 * `JokeChoiceChip`, the refresh intervals, the motion styles, the seasonal effects
 * and the seasonal quality - and they had already drifted apart in every
 * dimension a reader can see: three padding pairs (14/9, 16/10, 18/11), two
 * colour families (`primaryContainer` on three, `secondaryContainer` on one), one
 * with a 48dp minimum height and four without, two that fired a haptic and one
 * that did not, and two shapes of selection feedback (a border width change, a
 * 1.04 settle scale, or neither). A settings page is a page of chips; five answers
 * to "what does a chip look like" is five answers too many.
 *
 * So: one colour, one size, one press, one feedback. What a *choice* means stays
 * with the caller (its label, its tag, its icon); what a chip *looks like* is here.
 *
 * ## The details that are load-bearing
 *
 * - **One `MutableInteractionSource`**, shared by `bouncyPress` and `clickable`.
 *   `bouncyPress` given none installs its own pointer loop beside the clickable's,
 *   and a finger that lands on a chip and starts to scroll leaves the chip squashed
 *   for the whole drag.
 * - **`RoundedCornerShape(percent = 50)`'s token, not a Material shape.** A
 *   `RoundedPolygon` is authored on a square and stretched to its `Size`, so
 *   `MaterialShapes.Pill` on a wide chip is an ellipse. See `ExpressiveShapes.pill`
 *   and `ExpressiveShapesTest`.
 * - **Every animated value is drawn, not laid out.** The settle scale and the icon
 *   scale are read inside `graphicsLayer`, so a row of chips settling at once is one
 *   draw-phase invalidation each instead of a relayout per frame per chip. The
 *   border width is the one animated *layout* value, and it is a single dp step at
 *   the same moment the colours change.
 * - **Selected beats disabled.** "Mixed" is the active quick-pick on the Jokes page
 *   and is deliberately not clickable, and the last tone that is on cannot be
 *   switched off. Enabled-first meant the chip describing the current state was
 *   drawn in the disabled grey, so the most informative pill looked like the least
 *   available one.
 *
 * [glyph] is optional and is the only part of the chip that is a picture of the
 * choice: the motion styles' curve, the seasonal effects' shape. It springs up to
 * full size while the unselected marks sit slightly back, which is the one place a
 * second signal is worth having.
 */
@Composable
internal fun SettingsChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    testTag: String,
    enabled: Boolean = true,
    /**
     * A drawing of the choice, if the choice *is* a drawing.
     *
     * A slot rather than an `ImageVector` because the seasonal chips' glyph is the
     * sky's own `Canvas` code - the guarantee that a chip cannot promise a
     * snowflake the overlay does not draw - and a vector would be a second
     * illustration system beside it. The chip supplies the animated colour
     * (`LocalContentColor`) and the selected-scale spring; the slot supplies the
     * picture.
     */
    glyph: (@Composable () -> Unit)? = null,
    /** What the tap does, for a screen reader; defaults to the label. */
    onClickLabel: String? = null
) {
    val haptics = LocalHapticFeedback.current
    val scheme = MaterialTheme.colorScheme
    // One progress for the whole chip, and the endpoints read from the live
    // scheme on every frame. `animateColorAsState(targetValue =
    // MaterialTheme.colorScheme.X)` re-targets on every frame of a theme change -
    // the scheme itself is animated - so the chip used to arrive a beat after the
    // page it sits on and, on `SuperBouncy`, overshoot a colour the page had
    // already left. See `rememberStateColor`.
    val emphasis = AniSequelTheme.emphasis
    val neutralContainer = if (enabled) scheme.surfaceContainerHigh else scheme.surfaceContainer
    // 0.62 of `onSurfaceVariant` was 2.5:1 on a light card: the chip that described
    // the current state was the least legible label on the page. `disabled` is the
    // quietest tone of the scheme that still clears 3:1.
    val neutralContent = if (enabled) emphasis.muted else emphasis.disabled
    val neutralBorder = if (enabled) emphasis.boundary else emphasis.divider
    val container = rememberStateColor(
        active = selected,
        whenInactive = neutralContainer,
        whenActive = scheme.primaryContainer,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "settings_chip_container"
    )
    val content = rememberStateColor(
        active = selected,
        whenInactive = neutralContent,
        whenActive = scheme.onPrimaryContainer,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "settings_chip_content"
    )
    val borderColor = rememberStateColor(
        active = selected,
        whenInactive = neutralBorder,
        whenActive = scheme.primary,
        animationSpec = ExpressiveMotion.FastEffects,
        label = "settings_chip_border"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = ExpressiveMotion.FastSpatialDp,
        label = "settings_chip_border_width"
    )
    // The chosen pill sits a hair larger than its neighbours, on the bouncy token:
    // a selection that only changes colour is invisible to a reader glancing past
    // the row, and the overshoot is what makes it read as the page *accepting* the
    // tap.
    val settleScale by animateFloatAsState(
        targetValue = if (selected) 1.04f else 1f,
        animationSpec = ExpressiveMotion.SuperBouncy,
        label = "settings_chip_scale"
    )
    val iconScale by animateFloatAsState(
        targetValue = if (selected) 1f else 0.82f,
        animationSpec = ExpressiveMotion.SelectionScale,
        label = "settings_chip_icon_scale"
    )

    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .graphicsLayer {
                scaleX = settleScale
                scaleY = settleScale
            }
            .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
            .clip(ExpressiveShapes.pill)
            .background(container)
            .border(BorderStroke(width = borderWidth, color = borderColor), ExpressiveShapes.pill)
            // 48dp rather than what the padding alone gives: a chip is the whole of
            // a choice, so it is a primary target and not a tag.
            .defaultMinSize(minHeight = 48.dp)
            .clickable(
                interactionSource = interactionSource,
                // No ripple: the pill's own colour and press spring are the
                // feedback, and a ripple inside a 48dp capsule is noise.
                indication = null,
                enabled = enabled,
                role = Role.RadioButton,
                onClickLabel = onClickLabel
            ) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (glyph != null) {
            CompositionLocalProvider(LocalContentColor provides content) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .graphicsLayer {
                            scaleX = iconScale
                            scaleY = iconScale
                        },
                    contentAlignment = Alignment.Center
                ) {
                    glyph()
                }
            }
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}
