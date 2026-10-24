package com.anisequel.ui.screens.settings

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.JokePreferences
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeScope
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.JokePreviewBus
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import kotlinx.coroutines.launch

/**
 * The joke toast's control panel.
 *
 * Three questions, in the order a reader asks them: whether it runs, how often,
 * and where. The preview button is last because it is the proof, not a
 * preference - it asks the real host in `AppNavigation` for one line, so what the
 * reader sees is the actual pill at the actual edge rather than a mock drawn in
 * a card.
 *
 * The master switch and the frequency are separate on purpose. "Off but Chatty"
 * is a real state: turning jokes back on should restore the pace the reader chose
 * rather than silently resetting it to Normal.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JokeSettingsTab(
    jokePreferences: JokePreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by jokePreferences.settings.collectAsState(initial = JokeSettings())

    SettingsScrollColumn(modifier) {
        SectionCard(
            title = "Ani-chan",
            icon = AppVectorIcons.AnimeSparkle,
            subtitle = "A line about anime, now and then."
        ) {
            SwitchRow(
                title = "Jokes on their own",
                subtitle = "A small pill about backlogs and recaps, never more than one at a time.",
                checked = settings.enabled,
                onCheckedChange = { enabled ->
                    scope.launch {
                        jokePreferences.setEnabled(enabled)
                        // "Off but Chatty" is worth preserving, but an *enabled*
                        // stale Off would have nothing to schedule. Normal is the
                        // honest fallback when the switch comes back on.
                        if (enabled && settings.frequency == JokeFrequency.OFF) {
                            jokePreferences.setFrequency(JokeFrequency.NORMAL)
                        }
                    }
                },
                modifier = Modifier.testTag("jokes_switch")
            )
        }

        SectionCard(
            title = "How often",
            icon = AppVectorIcons.Schedule,
            subtitle = "How long Ani-chan waits between lines."
        ) {
            Text(
                text = "The first line arrives sooner than the rest, so a session is not " +
                    "over before the feature appears. After that, Calm waits 10 to 22 " +
                    "minutes between lines, Normal 4 to 15, and Chatty 1.5 to 3.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(14.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("joke_frequency_row")
            ) {
                JokeFrequency.entries
                    .filter { it != JokeFrequency.OFF }
                    .forEach { frequency ->
                        JokeChoiceChip(
                            label = frequency.displayName,
                            selected = settings.frequency == frequency,
                            enabled = settings.enabled,
                            onClick = {
                                scope.launch { jokePreferences.setFrequency(frequency) }
                            },
                            testTag = "joke_frequency_${frequency.storageValue}"
                        )
                    }
            }
        }

        SectionCard(
            title = "Where she appears",
            icon = AppVectorIcons.Tune,
            subtitle = "The edge of the screen, and which pages."
        ) {
            SettingsFieldLabel(text = "Edge of the screen")
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("joke_position_row")
            ) {
                JokePosition.entries.forEach { position ->
                    JokeChoiceChip(
                        label = position.displayName,
                        selected = settings.position == position,
                        enabled = settings.enabled,
                        onClick = {
                            scope.launch { jokePreferences.setPosition(position) }
                        },
                        testTag = "joke_position_${position.storageValue}"
                    )
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            SettingsFieldLabel(text = "Pages")
            Spacer(modifier = Modifier.height(10.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.testTag("joke_scope_row")
            ) {
                JokeScope.entries.forEach { jokeScope ->
                    JokeChoiceChip(
                        label = jokeScope.displayName,
                        selected = settings.scope == jokeScope,
                        enabled = settings.enabled,
                        onClick = {
                            scope.launch { jokePreferences.setScope(jokeScope) }
                        },
                        testTag = "joke_scope_${jokeScope.storageValue}"
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Main pages keeps jokes over Home, Library and Profile. Everywhere " +
                    "also allows them over pages you open from there, such as Settings, the " +
                    "tier list and a title. The sign-in screen stays quiet either way.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        SettingsButton(
            text = "Preview a joke",
            onClick = { JokePreviewBus.requestPreview() },
            icon = AppVectorIcons.AnimeSparkle,
            variant = SettingsButtonVariant.Tonal,
            fillWidth = true,
            testTag = "joke_preview_button"
        )
        Text(
            text = "The preview uses the edge above, even while jokes are off.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 4.dp)
        )

        if (settings != JokeSettings()) {
            SettingsButton(
                text = "Reset joke settings",
                onClick = { scope.launch { jokePreferences.reset() } },
                icon = AppVectorIcons.Restore,
                variant = SettingsButtonVariant.Outlined,
                fillWidth = true,
                testTag = "reset_jokes_button"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * One pill choice.
 *
 * Colours ride the effects spring and the border width the spatial one, the same
 * division [JokeSettingsTab]'s sibling cards use: colour is not geometry and must
 * not overshoot. `RoundedCornerShape(percent = 50)` rather than a Material shape
 * because a stretched polygon becomes an ellipse - see `ExpressiveShapes.pill`.
 */
@Composable
private fun JokeChoiceChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    testTag: String
) {
    val haptics = LocalHapticFeedback.current
    val container by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.surfaceContainer
            selected -> MaterialTheme.colorScheme.primaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerHigh
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "joke_chip_container"
    )
    val content by animateColorAsState(
        targetValue = when {
            !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            selected -> MaterialTheme.colorScheme.onPrimaryContainer
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "joke_chip_content"
    )
    val borderWidth by animateDpAsState(
        targetValue = if (selected) 2.dp else 1.dp,
        animationSpec = ExpressiveMotion.FastSpatialDp,
        label = "joke_chip_border_width"
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "joke_chip_border_colour"
    )

    Row(
        modifier = Modifier
            .clip(ExpressiveShapes.pill)
            .background(container)
            .border(BorderStroke(width = borderWidth, color = borderColor), ExpressiveShapes.pill)
            .bouncyPress(pressedScale = 0.94f)
            .clickable(enabled = enabled) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(horizontal = 18.dp, vertical = 11.dp)
            .semantics {
                this.selected = selected
                role = Role.RadioButton
            }
            .testTag(testTag),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = content
        )
    }
}
