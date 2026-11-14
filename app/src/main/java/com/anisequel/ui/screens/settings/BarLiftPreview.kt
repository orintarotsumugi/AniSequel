package com.anisequel.ui.screens.settings

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.anisequel.domain.ui.NavBarLift
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlin.math.roundToInt

/**
 * The bar-lift control's preview: a phone dummy with a floating bar and a
 * millimetre-style level beside it.
 *
 * ## Why this is not the old grey strip
 *
 * The old preview drew a capsule sliding inside a featureless rounded box. It
 * answered "does it move", but not "how far is 12dp" or "where is the bar
 * relative to the device's own navigation area" - the two questions the slider
 * actually asks. This draws the whole bottom of a phone: the screen, the device
 * gesture pill, and the app's bar above both. The scale beside it reads the same
 * value the slider writes, so the number is measured rather than guessed.
 *
 * ## Why the animated value is clamped
 *
 * `FastSpatial` is an expressive spring and therefore overshoots. On the way back
 * to zero it spends frames *below* zero, and a dp of padding cannot be negative:
 * Compose throws `Padding must be non-negative` and the Settings page closes.
 * That is the crash 1.9.7 shipped. The old preview passed the raw animated dp
 * straight to `padding(bottom = ...)`; this one clamps every frame with
 * [NavBarLift.clampDp] before it reaches layout. The spring still bounces; it just
 * cannot draw a position the bar does not have.
 */
@Composable
internal fun BottomBarLiftPreview(liftDp: Int, modifier: Modifier = Modifier) {
    val target = NavBarLift.clamp(liftDp)
    val animated by animateFloatAsState(
        targetValue = target.toFloat(),
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "bottom_bar_lift_preview"
    )
    val lift = NavBarLift.clampDp(animated)

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Preview · bottom bar",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = "${lift.roundToInt()} dp",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(248.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .padding(horizontal = 14.dp, vertical = 12.dp)
                .testTag("bar_lift_phone_preview"),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            PhoneDummy(
                lift = lift,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .widthIn(max = 196.dp)
            )
            LiftGauge(
                lift = lift,
                target = target,
                modifier = Modifier
                    .width(58.dp)
                    .fillMaxHeight()
            )
        }
    }
}

/**
 * The phone outline around the bar.
 *
 * Deliberately a sketch rather than a screenshot: three bars for a header, two
 * shortcut pills and a row of posters say "a phone screen" without pretending to
 * be the reader's actual Home, which would then be wrong for whichever layout
 * they had chosen.
 */
@Composable
private fun PhoneDummy(lift: Float, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.surfaceContainerHighest,
                        MaterialTheme.colorScheme.surfaceContainerHigh
                    )
                )
            )
            .padding(5.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(23.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.surface,
                            MaterialTheme.colorScheme.surfaceContainerLow
                        )
                    )
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 11.dp, end = 11.dp, top = 7.dp, bottom = 16.dp)
            ) {
                PhoneStatusBar()
                Spacer(modifier = Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.72f))
                    )
                    Spacer(modifier = Modifier.width(5.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        PhoneBar(fraction = 0.62f, height = 4.dp)
                        Spacer(modifier = Modifier.height(3.dp))
                        PhoneBar(fraction = 0.38f, height = 3.dp, alpha = 0.45f)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                PhoneBar(fraction = 0.78f, height = 7.dp)
                Spacer(modifier = Modifier.height(5.dp))
                PhoneBar(fraction = 0.52f, height = 5.dp, alpha = 0.5f)
                Spacer(modifier = Modifier.height(9.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(2) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(22.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.9f)
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    repeat(3) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    Brush.linearGradient(
                                        listOf(
                                            MaterialTheme.colorScheme.primaryContainer,
                                            MaterialTheme.colorScheme.tertiaryContainer
                                        )
                                    )
                                )
                        )
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
            }

            // The app's bar, measured from the same screen edge as the real one.
            // Nine dp of fixed gesture lane under it: at a stored lift of zero the
            // bar still sits above the device pill instead of being drawn on top of
            // it, which is what the real bar's navigation inset buys.
            // A clamped value only: see the file comment for the shipped crash.
            PhoneNavBar(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = lift.dp + 9.dp)
                    .padding(horizontal = 10.dp)
            )

            // The device's own gesture area, fixed to the screen edge so the
            // preview can show the daylight the lift creates above it.
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 5.dp)
                    .width(42.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
            )
        }
    }
}

@Composable
private fun PhoneStatusBar() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(22.dp)
                .height(4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f))
        )
        Spacer(modifier = Modifier.weight(1f))
        // A camera cutout in the middle is what makes the outline read as a phone
        // rather than a rounded card with a bar in it.
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f))
        )
        Spacer(modifier = Modifier.weight(1f))
        repeat(3) { index ->
            Box(
                modifier = Modifier
                    .padding(start = if (index == 0) 0.dp else 2.dp)
                    .width(if (index == 1) 8.dp else 3.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = if (index == 1) 0.7f else 0.42f)
                    )
            )
        }
    }
}

@Composable
private fun PhoneBar(
    fraction: Float,
    height: Dp,
    alpha: Float = 0.72f
) {
    Box(
        modifier = Modifier
            .fillMaxWidth(fraction)
            .height(height)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = alpha))
    )
}

/** The app's floating capsule, reduced to four glyphs and one label. */
@Composable
private fun PhoneNavBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(25.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PhoneNavItem(icon = AppVectorIcons.HomeTab, label = "Home", selected = true)
        PhoneNavItem(icon = AppVectorIcons.Library, label = "Library", selected = false)
        PhoneNavItem(icon = AppVectorIcons.Trophy, label = "Tiers", selected = false)
        PhoneNavItem(icon = AppVectorIcons.ProfilePerson, label = "Profile", selected = false)
    }
}

@Composable
private fun PhoneNavItem(
    icon: ImageVector,
    label: String,
    selected: Boolean
) {
    Row(
        modifier = Modifier
            .then(
                if (selected) {
                    Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 3.dp)
                } else {
                    Modifier.padding(horizontal = 3.dp, vertical = 3.dp)
                }
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            },
            modifier = Modifier.size(10.dp)
        )
        if (selected) {
            Spacer(modifier = Modifier.width(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 8.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}

/**
 * A vertical level: 0 at the device edge, 24 at the top of the range.
 *
 * The ticks are the slider's constants, not the preview's own numbers, so the
 * scale cannot describe a range the stored preference cannot hold. The moving
 * rule is the same clamped value the phone's bar is drawn at.
 */
@Composable
private fun LiftGauge(
    lift: Float,
    target: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "LEVEL",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = "$target dp",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(8.dp))
        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .testTag("bar_lift_level_gauge")
        ) {
            val tickColor = MaterialTheme.colorScheme.outlineVariant
            val accent = MaterialTheme.colorScheme.primary
            val labels = listOf("24", "16", "8", "0")
            val usable = maxHeight
            Canvas(modifier = Modifier.fillMaxSize()) {
                val x = size.width * 0.3f
                drawLine(
                    color = tickColor,
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 1.dp.toPx()
                )
                listOf(0, 8, 16, 24).forEach { value ->
                    val y = size.height * (1f - value / NavBarLift.MAX_DP.toFloat())
                    drawLine(
                        color = tickColor,
                        start = Offset(x - 4.dp.toPx(), y),
                        end = Offset(x + 4.dp.toPx(), y),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(end = 1.dp),
                horizontalAlignment = Alignment.End,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                labels.forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            val liftFraction = (lift / NavBarLift.MAX_DP.toFloat()).coerceIn(0f, 1f)
            val markerY = (usable - 1.dp) * (1f - liftFraction)
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .offset(y = markerY)
                    .height(2.dp)
                    .fillMaxWidth(0.72f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .height(2.dp)
                        .weight(1f)
                        .background(accent)
                )
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "0 = edge",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f)
        )
    }
}
