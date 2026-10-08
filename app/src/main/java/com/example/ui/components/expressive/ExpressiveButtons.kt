package com.example.ui.components.expressive

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private const val EXPRESSIVE_TAG = "expressive_"

/**
 * High-emphasis primary action button with contained morphing loading state and tactile spring press.
 */
@Composable
fun ExpressivePrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true
) {
    val cornerRadius by animateDpAsState(
        targetValue = if (loading) 24.dp else 16.dp,
        animationSpec = ExpressiveMotion.FastSpatialDp,
        label = "button_corner_radius"
    )

    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        modifier = modifier
            .height(48.dp)
            .bouncyPress(pressedScale = 0.96f)
            .testTag("${EXPRESSIVE_TAG}primary_button"),
        shape = RoundedCornerShape(cornerRadius)
    ) {
        if (loading) {
            ExpressiveContainedLoadingIndicator(
                modifier = Modifier.size(22.dp),
                containerColor = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.25f),
                indicatorColor = MaterialTheme.colorScheme.onPrimary
            )
        } else {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}

/**
 * Circular action icon button with badged count or status indicator.
 */
@Composable
fun ExpressiveIconBadge(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null
) {
    Box(modifier = modifier) {
        IconButton(
            onClick = onClick,
            modifier = Modifier
                .bouncyPress(pressedScale = 0.92f)
                .testTag("${EXPRESSIVE_TAG}icon_badge_$contentDescription")
        ) {
            Icon(imageVector = icon, contentDescription = contentDescription)
        }
        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 2.dp)
                    .clip(ExpressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.tertiary)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
