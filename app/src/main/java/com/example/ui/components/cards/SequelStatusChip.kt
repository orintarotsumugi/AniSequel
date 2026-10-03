package com.example.ui.components.cards

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.theme.AniSequelTheme

@Composable
fun SequelStatusChip(
    status: String,
    modifier: Modifier = Modifier
) {
    val statusColors = AniSequelTheme.statusColors
    val scheme = MaterialTheme.colorScheme

    val (bgColor, textColor, label) = when (status.uppercase()) {
        "RELEASING" -> Triple(
            statusColors.successContainer,
            statusColors.onSuccessContainer,
            "Airing"
        )
        "NOT_YET_RELEASED" -> Triple(
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            "Upcoming"
        )
        "HIATUS" -> Triple(
            statusColors.warningContainer,
            statusColors.onWarningContainer,
            "Hiatus"
        )
        "FINISHED" -> Triple(
            scheme.surfaceContainerHighest,
            scheme.onSurfaceVariant,
            "Finished"
        )
        "CANCELLED" -> Triple(
            scheme.errorContainer,
            scheme.onErrorContainer,
            "Cancelled"
        )
        else -> Triple(
            scheme.surfaceContainerHighest,
            scheme.onSurfaceVariant,
            status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() }
        )
    }

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = bgColor,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (status.equals("RELEASING", ignoreCase = true)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(textColor)
                )
                Spacer(modifier = Modifier.width(5.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SequelInfoChip(
    icon: ImageVector,
    label: String,
    modifier: Modifier = Modifier
) {
    val tint by animateColorAsState(
        targetValue = MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(150),
        label = "chip_tint"
    )

    Surface(
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(12.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
