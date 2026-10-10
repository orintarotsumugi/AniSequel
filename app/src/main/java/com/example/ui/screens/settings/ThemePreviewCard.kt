package com.example.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.repository.ThemePreferences
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.theme.AniSequelTheme
import com.example.ui.theme.ThemePalette

/**
 * Interactive Material 3 theme preview card.
 *
 * Renders a miniature mockup of an AniSequel card in real-time with the active
 * color scheme, true black settings, and spring motion style. Users can interact
 * with sample buttons to experience tactile animation bounciness and confirm
 * visual readability before navigating away.
 */
@Composable
fun ThemePreviewCard(
    themeSettings: ThemePreferences.ThemeSettings,
    modifier: Modifier = Modifier
) {
    var isPlanned by remember { mutableStateOf(false) }
    var isWatching by remember { mutableStateOf(false) }

    val statusColors = AniSequelTheme.statusColors
    val activePaletteName = if (themeSettings.customColorHex != null) {
        "Custom (${themeSettings.customColorHex})"
    } else if (themeSettings.useDynamicColor) {
        "Dynamic Wallpaper"
    } else {
        ThemePalette.fromStorage(themeSettings.paletteId).displayName
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .testTag("theme_live_preview_card"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 2.dp,
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            // Header: Live Preview label + Active Palette tag
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Live Theme Preview",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    shape = ExpressiveShapes.pill,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.padding(2.dp)
                ) {
                    Text(
                        text = activePaletteName,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Mockup Card
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Poster placeholder
                        Box(
                            modifier = Modifier
                                .size(48.dp, 64.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Movie,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        // Details
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    shape = ExpressiveShapes.pill,
                                    color = statusColors.successContainer,
                                    modifier = Modifier.padding(end = 6.dp)
                                ) {
                                    Text(
                                        text = "Airing",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = statusColors.success,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp)
                                    )
                                }
                                Text(
                                    text = "TV • Fall 2026",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Spacer(modifier = Modifier.height(4.dp))

                            Text(
                                text = "Frieren: Beyond Journey's End S2",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1
                            )

                            Text(
                                text = "Finished S1 (28/28 eps)",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Interactive Action Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { isPlanned = !isPlanned },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .bouncyPress(pressedScale = 0.94f)
                                .testTag("preview_plan_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (isPlanned) {
                                ButtonDefaults.buttonColors(
                                    containerColor = statusColors.successContainer,
                                    contentColor = statusColors.success
                                )
                            } else {
                                ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlanned) AppVectorIcons.BookmarkDone else AppVectorIcons.BookmarkAdd,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isPlanned) "Planned" else "+ Plan",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        OutlinedButton(
                            onClick = { isWatching = !isWatching },
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .bouncyPress(pressedScale = 0.94f)
                                .testTag("preview_watch_button"),
                            shape = RoundedCornerShape(10.dp),
                            colors = if (isWatching) {
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = statusColors.infoContainer,
                                    contentColor = statusColors.info
                                )
                            } else {
                                ButtonDefaults.outlinedButtonColors(
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Play,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isWatching) "Watching" else "Watch",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Swatch palette dots showing roles
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                RoleSwatchDot("Primary", MaterialTheme.colorScheme.primary)
                RoleSwatchDot("Secondary", MaterialTheme.colorScheme.secondary)
                RoleSwatchDot("Tertiary", MaterialTheme.colorScheme.tertiary)
                RoleSwatchDot("Container", MaterialTheme.colorScheme.primaryContainer)
                RoleSwatchDot("Surface", MaterialTheme.colorScheme.surface)
            }
        }
    }
}

@Composable
private fun RoleSwatchDot(label: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(color)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
