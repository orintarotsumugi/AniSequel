package com.anisequel.ui.screens.login

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.data.repository.JokePreferences
import com.anisequel.data.repository.MainTabPreferences
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.OnboardingPreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.ui.AppLayout
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.screens.settings.AppLayoutPreviews
import com.anisequel.ui.screens.settings.SectionCard
import com.anisequel.ui.screens.settings.SwitchRow
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.viewmodel.AuthUiState
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

/**
 * One page of the setup, as the header and footer read it.
 *
 * The icon is the page's own identity rather than a decorative repeat of the card
 * below: each step asks about a different part of the app, and the same glyph the
 * Settings section wears is the fastest way to say which part.
 */
internal data class SetupStep(
    val label: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

internal val SETUP_STEPS = listOf(
    SetupStep(
        label = "Welcome",
        title = "Make AniSequel yours",
        subtitle = "Five quick choices, then your list is ready. Every one of them can " +
            "be changed later in Settings.",
        icon = AppVectorIcons.AnimeSparkle
    ),
    SetupStep(
        label = "Interface",
        title = "Choose a home",
        subtitle = "The same two layouts Settings offers, drawn with sample posters.",
        icon = AppVectorIcons.ViewGrid
    ),
    SetupStep(
        label = "Theme",
        title = "Pick a light",
        subtitle = "Follow the system, or keep the app light or dark whatever the phone does.",
        icon = AppVectorIcons.ThemeDark
    ),
    SetupStep(
        label = "Motion",
        title = "Set the feel",
        subtitle = "How pages arrive and controls respond. Chill keeps the spring, " +
            "Instant removes it.",
        icon = AppVectorIcons.SectionMotion
    ),
    SetupStep(
        label = "Jokes",
        title = "Ani-chan's one-liners",
        subtitle = "An occasional line on the way through. Off is a perfectly good answer.",
        icon = AppVectorIcons.Star
    ),
    SetupStep(
        label = "Shortcuts",
        title = "The two extra pages",
        subtitle = "Add the catalogue search and tier lists to the bar, or keep it to " +
            "Home, Library and Profile.",
        icon = AppVectorIcons.Search
    )
)

/**
 * First-run setup, as a full-screen walk rather than one long dialog.
 *
 * ## Why it is not one scroll any more
 *
 * The old setup was an `AlertDialog` with five `SectionCard`s stacked in a
 * `verticalScroll`: on a phone that was a wall of switches where the first thing a
 * new reader saw was the fourth thing they were being asked. It also had one
 * "Save choices" button for everything, so a choice made on the first card could
 * only be undone by backing out of the whole thing.
 *
 * A step is one question. Back returns to the previous one, Skip leaves the rest
 * at their defaults, and the final page says Finish. The draft lives here until
 * Finish because the DataStore writes are one batch: a half-configured setup left
 * behind by a killed process would be a worse first launch than either extreme.
 *
 * ## Why a dialog and not a route
 *
 * The host draws this above the app's graph while auth is still resolving, before
 * there is a destination to be on. A route would have to be entered and left by
 * the navigation graph, and the "leave" is what tells the app the setup is done -
 * a back press that pops a route silently would count as a finish. The dialog has
 * no back-stack entry, so Back means "previous step" and nothing is completed
 * until Finish or Skip is pressed.
 */
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
    // Shared with the Back button's own interaction source so the press spring
    // reads the platform's press state rather than a second pointer loop, and the
    // same spring the primary button beside it uses.
    val backSource = remember { MutableInteractionSource() }
    // Nova + system theme + normal jokes is the recommended first-run path: it
    // shows the app's current strengths without hiding any existing data, while
    // every choice remains editable in Settings.
    var layout by rememberSaveable { mutableStateOf(AppLayout.NOVA.storageValue) }
    var theme by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM.storageValue) }
    var motion by rememberSaveable { mutableStateOf(MotionStyle.DEFAULT.storageValue) }
    var jokesEnabled by rememberSaveable { mutableStateOf(true) }
    var jokeFrequency by rememberSaveable { mutableStateOf(JokeFrequency.NORMAL.storageValue) }
    var searchShortcut by rememberSaveable { mutableStateOf(true) }
    var tiersShortcut by rememberSaveable { mutableStateOf(false) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

    val finishWithDefaults: () -> Unit = {
        scope.launch { onboardingPreferences.setCompleted(true) }
    }

    val saveChoices: () -> Unit = {
        scope.launch {
            interfacePreferences.setLayout(AppLayout.fromStorage(layout))
            themePreferences.setThemeMode(ThemeMode.fromStorage(theme))
            themePreferences.setMotionStyle(MotionStyle.fromStorage(motion))
            jokePreferences.setEnabled(jokesEnabled)
            jokePreferences.setFrequency(JokeFrequency.fromStorage(jokeFrequency))
            // The setup asks one question per page ("add this page to the bar"),
            // and the preference layer now keeps the tab and the top-bar shortcut
            // independent. Write both here so the wizard still means what its
            // switches say; Settings is where the two are separated.
            mainTabPreferences.setSearchTab(searchShortcut)
            mainTabPreferences.setSearchTopBar(searchShortcut)
            mainTabPreferences.setTierListTab(tiersShortcut)
            mainTabPreferences.setTierTopBar(tiersShortcut)
            onboardingPreferences.setCompleted(true)
        }
    }

    val isLast = stepIndex == SETUP_STEPS.lastIndex
    // One value for the page's choice controls. The page is a separate composable
    // so the six-way "which question is this" branch is not counted as part of
    // the wizard's own control flow, and the draft keeps its parameter list down
    // to the one object those controls read.
    val draft = SetupDraft(
        layout = layout,
        theme = theme,
        motion = motion,
        jokesEnabled = jokesEnabled,
        jokeFrequency = jokeFrequency,
        searchShortcut = searchShortcut,
        tiersShortcut = tiersShortcut
    )
    val onDraftChange: (SetupDraft) -> Unit = { updated ->
        layout = updated.layout
        theme = updated.theme
        motion = updated.motion
        jokesEnabled = updated.jokesEnabled
        jokeFrequency = updated.jokeFrequency
        searchShortcut = updated.searchShortcut
        tiersShortcut = updated.tiersShortcut
    }

    Dialog(
        // Back is the previous page, not a dismissal. The setup is not "closed"
        // until Finish or Skip says so.
        onDismissRequest = {
            if (stepIndex > 0) stepIndex -= 1 else finishWithDefaults()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            decorFitsSystemWindows = false
        )
    ) {
        DialogImmersiveEffect()
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .testTag("first_run_setup"),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
            ) {
                SetupHeader(onSkip = finishWithDefaults)

                SetupProgressBar(step = stepIndex)

                SetupPage(
                    stepIndex = stepIndex,
                    draft = draft,
                    onDraftChange = onDraftChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                )

                SetupFooter(
                    isFirst = stepIndex == 0,
                    isLast = isLast,
                    backSource = backSource,
                    onBack = { stepIndex -= 1 },
                    onContinue = { if (isLast) saveChoices() else stepIndex += 1 }
                )
            }
        }
    }
}

/**
 * One question's controls, and the welcome page at index zero.
 *
 * The six-way `when` lives here, in its own function: six branches of a page
 * dispatcher are not the wizard's control flow, and the wizard is the method the
 * complexity gate measures. Passing the draft as one object rather than seven
 * delegated variables keeps the extraction from trading a complexity finding for
 * a long-parameter one.
 */
@Composable
internal fun SetupStepChoices(
    index: Int,
    draft: SetupDraft,
    onDraftChange: (SetupDraft) -> Unit
) {
    when (index) {
        0 -> WelcomePerks()
        1 -> SectionCard(
            title = "Interface",
            icon = AppVectorIcons.ViewGrid,
            subtitle = "Drawn with the same previews Settings uses"
        ) {
            AppLayoutPreviews(
                selected = AppLayout.fromStorage(draft.layout),
                entries = emptyList(),
                onSelect = { onDraftChange(draft.copy(layout = it.storageValue)) },
                modifier = Modifier.testTag("onboarding_layout")
            )
        }
        2 -> SectionCard(
            title = "Theme",
            icon = AppVectorIcons.ThemeDark
        ) {
            ChoiceChips(
                values = listOf(
                    ThemeMode.SYSTEM.storageValue to "System",
                    ThemeMode.LIGHT.storageValue to "Light",
                    ThemeMode.DARK.storageValue to "Dark"
                ),
                selected = draft.theme,
                onSelect = { onDraftChange(draft.copy(theme = it)) },
                tag = "onboarding_theme"
            )
        }
        3 -> SectionCard(
            title = "Motion",
            icon = AppVectorIcons.SectionMotion
        ) {
            ChoiceChips(
                values = MotionStyle.entries.map {
                    it.storageValue to it.displayName
                },
                selected = draft.motion,
                onSelect = { onDraftChange(draft.copy(motion = it)) },
                tag = "onboarding_motion"
            )
        }
        4 -> SectionCard(
            title = "Jokes",
            icon = AppVectorIcons.Star
        ) {
            SwitchRow(
                title = "Ani-chan's jokes",
                subtitle = "Keep the app's occasional one-liners on.",
                checked = draft.jokesEnabled,
                onCheckedChange = { onDraftChange(draft.copy(jokesEnabled = it)) },
                modifier = Modifier.testTag("onboarding_jokes_switch")
            )
            Spacer(modifier = Modifier.height(10.dp))
            if (draft.jokesEnabled) {
                ChoiceChips(
                    values = JokeFrequency.entries.map {
                        it.storageValue to it.displayName
                    },
                    selected = draft.jokeFrequency,
                    onSelect = { onDraftChange(draft.copy(jokeFrequency = it)) },
                    tag = "onboarding_joke_frequency"
                )
            }
        }
        else -> SectionCard(
            title = "Top-bar shortcuts",
            icon = AppVectorIcons.Search
        ) {
            SwitchRow(
                title = "Catalogue search",
                subtitle = "Add a Search tab and a magnifier shortcut in " +
                    "both layouts.",
                checked = draft.searchShortcut,
                onCheckedChange = { onDraftChange(draft.copy(searchShortcut = it)) },
                modifier = Modifier.testTag("onboarding_search_shortcut")
            )
            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(
                title = "Tier lists",
                subtitle = "Add a Tiers tab and a trophy shortcut in both " +
                    "layouts.",
                checked = draft.tiersShortcut,
                onCheckedChange = { onDraftChange(draft.copy(tiersShortcut = it)) },
                modifier = Modifier.testTag("onboarding_tiers_shortcut")
            )
        }
    }
}

/** The wizard's choices, bundled so one page can receive all seven in one value. */
internal data class SetupDraft(
    val layout: String,
    val theme: String,
    val motion: String,
    val jokesEnabled: Boolean,
    val jokeFrequency: String,
    val searchShortcut: Boolean,
    val tiersShortcut: Boolean
)

/**
 * The welcome page's three promises.
 *
 * A settings card here would have been a fourth switch page; the first page is the
 * only place the setup can say what it is for, so it says it in the app's own
 * vocabulary rather than with a paragraph of prose.
 */
@Composable
private fun WelcomePerks() {
    Surface(
        shape = MaterialTheme.shapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            OnboardingPerk(
                icon = AppVectorIcons.SequelJump,
                title = "Find what you missed",
                detail = "Sequels, films and side stories from the shows you finished."
            )
            OnboardingPerk(
                icon = AppVectorIcons.ViewGrid,
                title = "A home that reads like yours",
                detail = "Two layouts, a card style and a bar you can rearrange."
            )
            OnboardingPerk(
                icon = AppVectorIcons.Star,
                title = "Nothing is permanent",
                detail = "Every choice here lives in Settings afterwards."
            )
        }
    }
}

@Composable
private fun OnboardingPerk(icon: ImageVector, title: String, detail: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
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
