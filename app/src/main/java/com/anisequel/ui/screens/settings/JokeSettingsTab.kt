package com.anisequel.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.JokePreferences
import com.anisequel.domain.jokes.DEFAULT_SPEAKER_NAME
import com.anisequel.domain.jokes.DEFAULT_STAMP
import com.anisequel.domain.jokes.JokeDwell
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.jokes.JokeMoment
import com.anisequel.domain.jokes.JokePosition
import com.anisequel.domain.jokes.JokeScope
import com.anisequel.domain.jokes.JokeSettings
import com.anisequel.domain.jokes.JokeTextEffect
import com.anisequel.domain.jokes.JokeTone
import com.anisequel.domain.jokes.MAX_SPEAKER_NAME_LENGTH
import com.anisequel.domain.jokes.MAX_STAMP_LENGTH
import com.anisequel.ui.components.AppJokes
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.JokeLineText
import com.anisequel.ui.components.JokePreviewBus
import com.anisequel.ui.components.expressive.ExpressiveMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * The joke toast's control panel.
 *
 * The cards answer, in the order a reader asks them: whether Ani-chan runs, how
 * often and for how long, who she is and what mark she wears, how her lines
 * arrive, where she appears, what she may talk about, which moments invite her,
 * and what has been kept. The preview button is near the end because it is the
 * proof, not a preference - it asks the real host in `AppNavigation` for one
 * line, so what the reader sees is the actual pill at the actual edge rather
 * than a mock drawn in a card. The line-style card carries its own sample
 * because a text effect is judged by watching it, not by reading its name.
 *
 * The master switch and everything else are separate on purpose. "Off but
 * Chatty" is a real state: turning jokes back on should restore the pace, dwell,
 * tones and moments the reader chose rather than silently resetting them.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun JokeSettingsTab(
    jokePreferences: JokePreferences,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val settings by jokePreferences.settings.collectAsState(initial = JokeSettings())
    val favourites by jokePreferences.favourites.collectAsState(initial = emptySet())
    // Pool order, with lines this build no longer knows about at the end - see
    // `AppJokes.orderedFavourites` for why a saved line is never dropped.
    val saved = remember(favourites) { AppJokes.orderedFavourites(favourites) }

    SettingsScrollColumn(modifier) {
        AniChanCard(
            settings = settings,
            onEnabledChange = { enabled ->
                scope.launch {
                    jokePreferences.setEnabled(enabled)
                    // "Off but Chatty" is worth preserving, but an *enabled*
                    // stale Off would have nothing to schedule. Normal is the
                    // honest fallback when the switch comes back on.
                    if (enabled && settings.frequency == JokeFrequency.OFF) {
                        jokePreferences.setFrequency(JokeFrequency.NORMAL)
                    }
                }
            }
        )

        PaceCard(settings = settings, scope = scope, preferences = jokePreferences)
        PersonaCard(settings = settings, scope = scope, preferences = jokePreferences)
        TextEffectCard(settings = settings, scope = scope, preferences = jokePreferences)
        PlacementCard(settings = settings, scope = scope, preferences = jokePreferences)
        TonesCard(settings = settings, scope = scope, preferences = jokePreferences)
        MomentsCard(settings = settings, scope = scope, preferences = jokePreferences)

        JokeFavouritesCard(
            lines = saved,
            onPreview = { line -> JokePreviewBus.requestPreview(line) },
            onRemove = { line -> scope.launch { jokePreferences.toggleFavourite(line) } },
            onClear = { scope.launch { jokePreferences.clearFavourites() } }
        )

        PreviewAndReset(settings = settings, scope = scope, preferences = jokePreferences)
    }
}

@Composable
private fun AniChanCard(settings: JokeSettings, onEnabledChange: (Boolean) -> Unit) {
    SectionCard(
        title = "Ani-chan",
        icon = AppVectorIcons.AnimeSparkle,
        subtitle = "A line about anime, now and then"
    ) {
        SwitchRow(
            title = "Jokes on their own",
            subtitle = "A small pill about backlogs and recaps, never more than one at a time.",
            checked = settings.enabled,
            onCheckedChange = onEnabledChange,
            modifier = Modifier.testTag("jokes_switch")
        )
    }
}

/**
 * The stamps offered beside the free field.
 *
 * Text glyphs on purpose, and the one place in the app that draws a symbol the
 * reader picked rather than a shipped vector: this is the pill's signature, not
 * chrome. Emoji are included because that is what "customise the star" means to
 * the person asking for it; the default stays the plain star.
 */
private val stampChoices = listOf(
    "\u2605", // ★
    "\u2606", // ☆
    "\u2726", // ✦
    "\u2727", // ✧
    "\u2665", // ♥
    "\u2620", // ☠
    "\u263E", // ☾
    "\u273F", // ✿
    "\u26A1", // ⚡
    "\uD83C\uDF80", // 🎀
    "\uD83D\uDC7B", // 👻
    "\uD83C\uDF5C", // 🍥
)

/**
 * How a line arrives, with a sample that plays it.
 *
 * The reveal effects are opt-in, so the card leads with "off by default" and then
 * hands the decision to a preview rather than to a description: a typewriter judged
 * by its name is a guess, and the sample uses the toast's own renderer at the same
 * cadence.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TextEffectCard(
    settings: JokeSettings,
    scope: CoroutineScope,
    preferences: JokePreferences
) {
    // Keyed on the effect so switching the choice replays the sample in the new
    // style without a second tap; the counter is for replaying the same one.
    var replay by remember(settings.textEffect) { mutableIntStateOf(0) }

    SectionCard(
        title = "Line style",
        icon = AppVectorIcons.TextBold,
        subtitle = "How a joke arrives, and a sample to judge it by"
    ) {
        Text(
            text = "Off by default. A reveal delays the sentence, so pair a writer " +
                "with a longer dwell; the sample uses the real speed and the same " +
                "time budget the toast has.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("joke_text_effect_row")
        ) {
            JokeTextEffect.entries.forEach { effect ->
                JokeChoiceChip(
                    label = effect.displayName,
                    selected = settings.textEffect == effect,
                    enabled = true,
                    onClick = {
                        if (effect != settings.textEffect) {
                            scope.launch { preferences.setTextEffect(effect) }
                        }
                    },
                    testTag = "joke_text_effect_${effect.storageValue}"
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        Surface(
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier
                .fillMaxWidth()
                .clip(MaterialTheme.shapes.large)
                .clickable { replay++ }
                .testTag("joke_text_preview")
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Text(
                    text = "Tap to replay · ${settings.textEffect.displayName}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                )
                Spacer(modifier = Modifier.height(4.dp))
                // A real line from the pool, drawn by the toast's own renderer at
                // its own cadence - a mock here would be a second implementation.
                key(replay) {
                    JokeLineText(
                        line = "The sequel was announced with a key visual and no date. That was 2019.",
                        effect = settings.textEffect,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = settings.textEffect.subtitle,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Who the pill says it is, and the stamp beside the name.
 *
 * The toast used to hard-code "Ani-chan" and a fixed sparkle. Both are the app's
 * voice rather than a law - the line arrives on the reader's own phone - so they
 * are editable here. The writes are debounced: a name is typed one character at a
 * time and every keystroke does not need its own disk round trip.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PersonaCard(
    settings: JokeSettings,
    scope: CoroutineScope,
    preferences: JokePreferences
) {
    var name by remember(settings.speakerName) { mutableStateOf(settings.speakerName) }
    var stamp by remember(settings.stamp) { mutableStateOf(settings.stamp) }

    LaunchedEffect(name) {
        if (name == settings.speakerName) return@LaunchedEffect
        delay(450)
        preferences.setSpeakerName(name)
    }
    LaunchedEffect(stamp) {
        if (stamp == settings.stamp) return@LaunchedEffect
        delay(450)
        preferences.setStamp(stamp)
    }

    SectionCard(
        title = "Name and stamp",
        icon = AppVectorIcons.EditEntry,
        subtitle = "Who the pill speaks as, and the mark beside it"
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = { next ->
                if (next.length <= MAX_SPEAKER_NAME_LENGTH) name = next
            },
            label = { Text("Name") },
            placeholder = { Text(DEFAULT_SPEAKER_NAME) },
            singleLine = true,
            enabled = settings.enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("joke_speaker_name")
        )
        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "The stamp",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(6.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.testTag("joke_stamp_row")
        ) {
            stampChoices.forEach { choice ->
                JokeChoiceChip(
                    label = choice,
                    selected = settings.stamp == choice,
                    enabled = settings.enabled,
                    onClick = {
                        stamp = choice
                        scope.launch { preferences.setStamp(choice) }
                    },
                    testTag = "joke_stamp_$choice"
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        OutlinedTextField(
            value = stamp,
            onValueChange = { next ->
                if (next.length <= MAX_STAMP_LENGTH) stamp = next
            },
            label = { Text("Or type your own") },
            placeholder = { Text(DEFAULT_STAMP) },
            singleLine = true,
            enabled = settings.enabled,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("joke_stamp_custom")
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "The name and stamp are yours: they are stored on this device " +
                "and appear on every line. Preview a joke to see them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PaceCard(
    settings: JokeSettings,
    scope: CoroutineScope,
    preferences: JokePreferences
) {
    SectionCard(
        title = "Pace and dwell",
        icon = AppVectorIcons.Schedule,
        subtitle = "How often she speaks, and how long a line stays"
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
                        onClick = { scope.launch { preferences.setFrequency(frequency) } },
                        testTag = "joke_frequency_${frequency.storageValue}"
                    )
                }
        }

        Spacer(modifier = Modifier.height(18.dp))
        SettingsFieldLabel(text = "Time on screen")
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("joke_dwell_row")
        ) {
            JokeDwell.entries.forEach { dwell ->
                JokeChoiceChip(
                    label = dwell.displayName,
                    selected = settings.dwell == dwell,
                    enabled = settings.enabled,
                    onClick = { scope.launch { preferences.setDwell(dwell) } },
                    testTag = "joke_dwell_${dwell.storageValue}"
                )
            }
        }
        Spacer(modifier = Modifier.height(10.dp))
        Text(
            text = "Quick stays 3 seconds, Standard 5, Relaxed 8 and Long 12. Until " +
                "tapped waits for you instead, which suits the longest lines.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlacementCard(
    settings: JokeSettings,
    scope: CoroutineScope,
    preferences: JokePreferences
) {
    SectionCard(
        title = "Where she appears",
        icon = AppVectorIcons.Tune,
        subtitle = "The edge of the screen, the pages, and the arrival tick"
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
                    onClick = { scope.launch { preferences.setPosition(position) } },
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
                    onClick = { scope.launch { preferences.setScope(jokeScope) } },
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

        Spacer(modifier = Modifier.height(10.dp))
        SwitchRow(
            title = "Arrival tick",
            subtitle = "One light vibration when a line appears. Off means a silent pill.",
            checked = settings.haptics,
            onCheckedChange = { enabled -> scope.launch { preferences.setHaptics(enabled) } },
            enabled = settings.enabled,
            modifier = Modifier.testTag("joke_haptics_switch")
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TonesCard(
    settings: JokeSettings,
    scope: CoroutineScope,
    preferences: JokePreferences
) {
    val active = settings.activeTones
    val lineCount = AppJokes.countFor(active)

    SectionCard(
        title = "What she talks about",
        icon = AppVectorIcons.Tag,
        subtitle = "Switch off the kinds of line you would rather not read"
    ) {
        // Mixed is a preset, not a sixth tone: it is every tone switched on at
        // once, so it cannot open a separate pool that drifts from these five.
        // It is shown selected and disabled while it applies for the same reason
        // the last tone's switch is - the reader cannot unselect the entire pool.
        SettingsFieldLabel(text = "Quick pick")
        Spacer(modifier = Modifier.height(10.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("joke_tone_preset_row")
        ) {
            JokeChoiceChip(
                label = "Mixed",
                selected = settings.isMixed,
                enabled = settings.enabled && !settings.isMixed,
                onClick = {
                    scope.launch { preferences.setTones(JokeTone.entries.toSet()) }
                },
                testTag = "joke_tone_mixed"
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "Mixed turns every kind back on at once, so the bag holds the " +
                "whole pool. Switch one off below to narrow it down again.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(14.dp))

        JokeTone.entries.forEach { tone ->
            val on = tone in active
            SwitchRow(
                title = tone.displayName,
                subtitle = tone.subtitle,
                checked = on,
                onCheckedChange = { scope.launch { preferences.toggleTone(tone) } },
                // The last tone on cannot be switched off: an empty pool would
                // make the toast disappear with no explanation. The repository
                // refuses the same write, because a UI flag is not a guarantee.
                enabled = settings.enabled && !(on && active.size == 1),
                modifier = Modifier.testTag("joke_tone_${tone.storageValue}")
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Drawing from $lineCount lines across ${active.size} of " +
                "${JokeTone.entries.size} tones" +
                (if (settings.isMixed) ", mixed." else ".") +
                " At least one tone stays on.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MomentsCard(
    settings: JokeSettings,
    scope: CoroutineScope,
    preferences: JokePreferences
) {
    SectionCard(
        title = "When she appears",
        icon = AppVectorIcons.Refresh,
        subtitle = "The timer is the baseline. These are extra invitations"
    ) {
        JokeMoment.entries.forEach { moment ->
            SwitchRow(
                title = moment.displayName,
                subtitle = moment.subtitle,
                checked = moment in settings.moments,
                onCheckedChange = { scope.launch { preferences.toggleMoment(moment) } },
                enabled = settings.enabled,
                modifier = Modifier.testTag("joke_moment_${moment.storageValue}")
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Each moment still waits for a quiet gap after the last line, so a " +
                "busy tab is not a drum roll. Nothing appears over the sign-in screen.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PreviewAndReset(
    settings: JokeSettings,
    scope: CoroutineScope,
    preferences: JokePreferences
) {
    SettingsButton(
        text = "Preview a joke",
        onClick = { JokePreviewBus.requestPreview() },
        icon = AppVectorIcons.AnimeSparkle,
        variant = SettingsButtonVariant.Tonal,
        fillWidth = true,
        testTag = "joke_preview_button"
    )
    Text(
        text = "The preview uses the edge, dwell and tones above, even while jokes are off.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )

    // Same entry as the Theme and Library reset rows: the pair appears with a
    // spring expansion instead of shoving the page's footer down on one frame.
    AnimatedVisibility(
        visible = settings != JokeSettings(),
        enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
            expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
        exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
            shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            SettingsButton(
                text = "Reset joke settings",
                onClick = { scope.launch { preferences.reset() } },
                icon = AppVectorIcons.Restore,
                variant = SettingsButtonVariant.Outlined,
                fillWidth = true,
                testTag = "reset_jokes_button"
            )
            Text(
                text = "Saved lines are kept: they are yours, not a setting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(24.dp))
}
