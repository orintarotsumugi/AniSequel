package com.anisequel.ui.screens.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.OnboardingPreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.ui.AppLayout
import com.anisequel.ui.viewmodel.AuthUiState
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.screens.settings.AppLayoutPreviews
import com.anisequel.ui.screens.settings.SectionCard
import com.anisequel.ui.screens.settings.SwitchRow
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import kotlinx.coroutines.launch

/** Keeps the state-read gate out of the app navigation host. */
@Composable
fun FirstRunSetupHost(
    completed: Boolean,
    authState: AuthUiState,
    onboardingPreferences: OnboardingPreferences,
    interfacePreferences: InterfacePreferences,
    themePreferences: ThemePreferences,
    jokePreferences: JokePreferences,
    mainTabPreferences: MainTabPreferences
) {
    if (!shouldShowFirstRunSetup(completed, authState)) return
    FirstRunSetupDialog(
        onboardingPreferences = onboardingPreferences,
        interfacePreferences = interfacePreferences,
        themePreferences = themePreferences,
        jokePreferences = jokePreferences,
        mainTabPreferences = mainTabPreferences
    )
}

/** Pure gate so a restoring session can never flash first-run choices over Home. */
internal fun shouldShowFirstRunSetup(completed: Boolean, authState: AuthUiState): Boolean =
    !completed && authState !is AuthUiState.Restoring

/** First-run choices, grouped into the five things that most change the experience. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FirstRunSetupDialog(
    onboardingPreferences: OnboardingPreferences,
    interfacePreferences: InterfacePreferences,
    themePreferences: ThemePreferences,
    jokePreferences: JokePreferences,
    mainTabPreferences: MainTabPreferences
) {
    val scope = rememberCoroutineScope()
    var layout by rememberSaveable { mutableStateOf(AppLayout.DEFAULT.storageValue) }
    var theme by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM.storageValue) }
    var motion by rememberSaveable { mutableStateOf(MotionStyle.DEFAULT.storageValue) }
    var jokesEnabled by rememberSaveable { mutableStateOf(true) }
    var jokeFrequency by rememberSaveable { mutableStateOf(JokeFrequency.NORMAL.storageValue) }
    var searchShortcut by rememberSaveable { mutableStateOf(false) }
    var tiersShortcut by rememberSaveable { mutableStateOf(false) }

    fun saveChoices() {
        scope.launch {
            interfacePreferences.setLayout(AppLayout.fromStorage(layout))
            themePreferences.setThemeMode(ThemeMode.fromStorage(theme))
            themePreferences.setMotionStyle(MotionStyle.fromStorage(motion))
            jokePreferences.setEnabled(jokesEnabled)
            jokePreferences.setFrequency(JokeFrequency.fromStorage(jokeFrequency))
            mainTabPreferences.setSearchTopBar(searchShortcut)
            mainTabPreferences.setTierTopBar(tiersShortcut)
            onboardingPreferences.setCompleted(true)
        }
    }

    AlertDialog(
        onDismissRequest = {},
        modifier = Modifier.testTag("first_run_setup"),
        title = { Text("Make AniSequel yours") },
        text = {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = maxHeight * 0.78f)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        "Pick a starting point. You can change every choice later in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    SectionCard(
                        title = "Interface",
                        icon = AppVectorIcons.ViewGrid,
                        subtitle = "The same two layouts as Settings, drawn with sample posters"
                    ) {
                        // The chooser shows the shapes, not two names. This is the
                        // same preview Settings uses, so first-run cannot describe a
                        // layout the Interface page draws differently - and with no
                        // library yet, its covers fall back to tinted placeholders.
                        AppLayoutPreviews(
                            selected = AppLayout.fromStorage(layout),
                            entries = emptyList(),
                            onSelect = { layout = it.storageValue },
                            modifier = Modifier.testTag("onboarding_layout")
                        )
                    }
                    SectionCard(title = "Theme", icon = AppVectorIcons.ThemeDark) {
                        ChoiceChips(
                            values = listOf(
                                ThemeMode.SYSTEM.storageValue to "System",
                                ThemeMode.LIGHT.storageValue to "Light",
                                ThemeMode.DARK.storageValue to "Dark"
                            ),
                            selected = theme,
                            onSelect = { theme = it },
                            tag = "onboarding_theme"
                        )
                    }
                    SectionCard(title = "Motion", icon = AppVectorIcons.SectionMotion) {
                        ChoiceChips(
                            values = MotionStyle.entries.map { it.storageValue to it.displayName },
                            selected = motion,
                            onSelect = { motion = it },
                            tag = "onboarding_motion"
                        )
                    }
                    SectionCard(title = "Jokes", icon = AppVectorIcons.Star) {
                        SwitchRow(
                            title = "Ani-chan's jokes",
                            subtitle = "Keep the app's occasional one-liners on.",
                            checked = jokesEnabled,
                            onCheckedChange = { jokesEnabled = it },
                            modifier = Modifier.testTag("onboarding_jokes_switch")
                        )
                        Spacer(Modifier.height(8.dp))
                        ChoiceChips(
                            values = JokeFrequency.entries.map { it.storageValue to it.displayName },
                            selected = jokeFrequency,
                            onSelect = { jokeFrequency = it },
                            tag = "onboarding_joke_frequency"
                        )
                    }
                    SectionCard(title = "Top-bar shortcuts", icon = AppVectorIcons.Search) {
                        SwitchRow(
                            title = "Catalogue search",
                            subtitle = "Add a Search tab and a magnifier shortcut in both layouts.",
                            checked = searchShortcut,
                            onCheckedChange = { searchShortcut = it },
                            modifier = Modifier.testTag("onboarding_search_shortcut")
                        )
                        Spacer(Modifier.height(6.dp))
                        SwitchRow(
                            title = "Tier lists",
                            subtitle = "Add a Tiers tab and a trophy shortcut in both layouts.",
                            checked = tiersShortcut,
                            onCheckedChange = { tiersShortcut = it },
                            modifier = Modifier.testTag("onboarding_tiers_shortcut")
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = ::saveChoices, modifier = Modifier.testTag("onboarding_save")) {
                Text("Save choices")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    scope.launch { onboardingPreferences.setCompleted(true) }
                },
                modifier = Modifier.testTag("onboarding_defaults")
            ) {
                Text("Use defaults")
            }
        }
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChoiceChips(
    values: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    tag: String
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.testTag(tag)
    ) {
        values.forEach { (value, label) ->
            ExpressiveStateChip(
                label = label,
                selected = selected == value,
                onClick = { onSelect(value) },
                role = Role.RadioButton,
                minTouchTargetHeight = 40.dp,
                modifier = Modifier.testTag("${tag}_$value")
            )
        }
    }
}
