package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.domain.ui.ProgressBarStyle
import com.anisequel.ui.components.AppVectorIcons

/**
 * The three bar styles, expanded inside the progress card rather than over it.
 *
 * The first version of this picker was a modal bottom sheet. It worked, but it
 * covered the very bar it was changing, so the reader chose between drawings they
 * could not compare with the one on the page. This row sits under the bar, inside
 * the card that already owns it; every option previews itself at the same fraction
 * and the card grows to fit, then collapses as soon as a choice is made.
 *
 * The previews are static: the real bar is already animating above them, and three
 * more loops inside one card is exactly the unbounded work the motion rules keep
 * out of a scrolling page.
 */
@Composable
internal fun ProgressStyleChoices(
    selected: ProgressBarStyle,
    onSelect: (ProgressBarStyle) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.58f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Bar style",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ProgressBarStyle.entries.forEach { style ->
                    val active = style == selected
                    Surface(
                        onClick = { onSelect(style) },
                        shape = RoundedCornerShape(14.dp),
                        color = if (active) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.56f)
                        } else {
                            MaterialTheme.colorScheme.surfaceContainer
                        },
                        border = BorderStroke(
                            width = if (active) 2.dp else 1.dp,
                            color = if (active) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            }
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .semantics { role = Role.RadioButton }
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                InfoProgressBar(
                                    progress = 0.64f,
                                    style = style,
                                    modifier = Modifier.fillMaxWidth(),
                                    height = 30.dp,
                                    entranceKey = style,
                                    animate = false
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = style.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (active) {
                                    Box(modifier = Modifier.size(2.dp))
                                    Icon(
                                        imageVector = AppVectorIcons.Done,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(12.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
