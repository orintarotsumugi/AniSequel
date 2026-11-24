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
import com.anisequel.data.repository.RefreshInterval
import com.anisequel.data.repository.RefreshIntervalPreferences
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.domain.jokes.JokeFrequency
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.DpiMode
import com.anisequel.domain.ui.PosterSource
import com.anisequel.domain.ui.UiSize
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.screens.settings.AppLayoutPreviews
import com.anisequel.ui.screens.settings.MotionSample
import com.anisequel.ui.screens.settings.RefreshIntervalPicker
import com.anisequel.ui.screens.settings.SectionCard
import com.anisequel.ui.screens.settings.SwitchRow
import com.anisequel.ui.screens.settings.ThemePreviewCard
import com.anisequel.ui.screens.settings.motionDescription
import com.anisequel.ui.theme.DialogImmersiveEffect
import com.anisequel.ui.theme.rememberDeviceDensityDpi
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
    mainTabPreferences: MainTabPreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences
) {
    if (!shouldShowFirstRunSetup(completed, authState)) return
    FirstRunSetupDialog(
        onboardingPreferences = onboardingPreferences,
        interfacePreferences = interfacePreferences,
        themePreferences = themePreferences,
        jokePreferences = jokePreferences,
        mainTabPreferences = mainTabPreferences,
        refreshIntervalPreferences = refreshIntervalPreferences
    )
}

/**
 * Pure gate for the first-run walk.
 *
 * It waits for the stored session to be read, and it stays off the login screen:
 * the setup is something a signed-in reader configures for their app, so it opens
 * after AniList auth completes rather than before the reader has an account to
 * land in. A demo or unauthenticated session does not count as "logged in".
 */
internal fun shouldShowFirstRunSetup(completed: Boolean, authState: AuthUiState): Boolean =
    !completed && authState is AuthUiState.Authenticated

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
        subtitle = "A few quick choices, then your list is ready. Every one of them can " +
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
        label = "Size",
        title = "Set the app size",
        subtitle = "How large cards, text and gaps are drawn. You can fine-tune it later " +
            "in Settings > Type & reading.",
        icon = AppVectorIcons.Tune
    ),
    SetupStep(
        label = "Posters",
        title = "Choose your poster art",
        subtitle = "AniList covers, or BetterPosters art when it is available for a title.",
        icon = AppVectorIcons.SectionAppearance
    ),
    SetupStep(
        label = "Theme",
        title = "Pick a light",
        subtitle = "Follow the system, or keep the app light or dark whatever the phone does.",
        icon = AppVectorIcons.ThemeDark
    ),
    SetupStep(
        label = "Colours",
        title = "Keep a title's colour",
        subtitle = "Let the show you open tint the app - and keep that colour when " +
            "you leave its page.",
        icon = AppVectorIcons.SectionAppearance
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
        label = "Refresh",
        title = "Keep your list current",
        subtitle = "How often the app asks AniList for a fresh copy of your list. " +
            "You can change this later in Settings.",
        icon = AppVectorIcons.Refresh
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
    mainTabPreferences: MainTabPreferences,
    refreshIntervalPreferences: RefreshIntervalPreferences
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
    var displayDpi by rememberSaveable { mutableIntStateOf(DisplayDpi.DEFAULT_DPI) }
    var posterSource by rememberSaveable { mutableStateOf(PosterSource.DEFAULT.storageValue) }
    var theme by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM.storageValue) }
    var retainAnimeColor by rememberSaveable { mutableStateOf(true) }
    var motion by rememberSaveable { mutableStateOf(MotionStyle.DEFAULT.storageValue) }
    var jokesEnabled by rememberSaveable { mutableStateOf(true) }
    var jokeFrequency by rememberSaveable { mutableStateOf(JokeFrequency.NORMAL.storageValue) }
    var refreshInterval by rememberSaveable {
        mutableStateOf(RefreshInterval.DEFAULT.storageValue)
    }
    var searchShortcut by rememberSaveable { mutableStateOf(true) }
    var tiersShortcut by rememberSaveable { mutableStateOf(false) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }
    // Guard against double taps on Skip/Finish writing the batch twice or racing
    // the completion flag while the dialog is still on screen.
    var finishing by remember { mutableStateOf(false) }

    val finishWithDefaults: () -> Unit = finish@{
        if (finishing) return@finish
        finishing = true
        scope.launch { onboardingPreferences.setCompleted(true) }
    }

    val saveChoices: () -> Unit = save@{
        if (finishing) return@save
        finishing = true
        scope.launch {
            interfacePreferences.setLayout(AppLayout.fromStorage(layout))
            // The size chosen here is a number, so it only takes effect in Manual.
            // Automatic stays available in Settings > Display size as an advanced
            // choice for readers on tablets and foldables.
            interfacePreferences.setDpiMode(DpiMode.MANUAL)
            interfacePreferences.setDisplayDpi(displayDpi)
            interfacePreferences.setPosterSource(PosterSource.fromStorage(posterSource))
            themePreferences.setThemeMode(ThemeMode.fromStorage(theme))
            // Retention is the whole choice here: enabling it also gives a title
            // permission to recolour the app, so the switch means what it says.
            themePreferences.setAnimeThemeEnabled(retainAnimeColor)
            themePreferences.setAnimeThemeRetainColor(retainAnimeColor)
            themePreferences.setMotionStyle(MotionStyle.fromStorage(motion))
            jokePreferences.setEnabled(jokesEnabled)
            jokePreferences.setFrequency(JokeFrequency.fromStorage(jokeFrequency))
            refreshIntervalPreferences.setInterval(RefreshInterval.fromStorage(refreshInterval))
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
        displayDpi = displayDpi,
        posterSource = posterSource,
        theme = theme,
        retainAnimeColor = retainAnimeColor,
        motion = motion,
        jokesEnabled = jokesEnabled,
        jokeFrequency = jokeFrequency,
        refreshInterval = refreshInterval,
        searchShortcut = searchShortcut,
        tiersShortcut = tiersShortcut
    )
    val onDraftChange: (SetupDraft) -> Unit = { updated ->
        layout = updated.layout
        displayDpi = updated.displayDpi
        posterSource = updated.posterSource
        theme = updated.theme
        retainAnimeColor = updated.retainAnimeColor
        motion = updated.motion
        jokesEnabled = updated.jokesEnabled
        jokeFrequency = updated.jokeFrequency
        refreshInterval = updated.refreshInterval
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
 * The page dispatch lives here, in its own function: the branching across setup
 * steps is not the wizard's control flow, and the wizard is the method the
 * complexity gate measures. Passing the draft as one object keeps the extraction
 * from trading a complexity finding for a long-parameter one.
 */
@OptIn(ExperimentalLayoutApi::class)
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
            title = "UI size",
            icon = AppVectorIcons.Tune,
            subtitle = "Pick a comfortable size now. You can fine-tune the exact density later."
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_ui_size")
            ) {
                UiSize.entries.forEach { option ->
                    ExpressiveStateChip(
                        label = option.displayName,
                        selected = UiSize.of(draft.displayDpi) == option,
                        onClick = { onDraftChange(draft.copy(displayDpi = option.dpi)) },
                        role = Role.RadioButton,
                        minTouchTargetHeight = 48.dp,
                        onClickLabel = "Draw the app at the ${option.displayName} UI size",
                        modifier = Modifier.testTag("onboarding_ui_size_${option.name.lowercase(java.util.Locale.ROOT)}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = UiSize.of(draft.displayDpi)?.blurb
                    ?: "A custom density. You can adjust it in Settings > Type & reading.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Manual · ${DisplayDpi.clamp(draft.displayDpi)} dpi",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Your device reports ${rememberDeviceDensityDpi()} dpi. " +
                    "You can switch the app to Automatic density later in " +
                    "Settings > Display size.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        3 -> SectionCard(
            title = "Poster art",
            icon = AppVectorIcons.SectionAppearance,
            subtitle = "You can change this later in Settings > Interface > Card design"
        ) {
            ChoiceChips(
                values = PosterSource.entries.map { it.storageValue to it.displayName },
                selected = draft.posterSource,
                onSelect = { onDraftChange(draft.copy(posterSource = it)) },
                tag = "onboarding_poster_source"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = PosterSource.fromStorage(draft.posterSource).blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        4 -> SectionCard(
            title = "Theme",
            icon = AppVectorIcons.ThemeDark,
            subtitle = "A preview of how cards and accents look in the app"
        ) {
            ThemePreviewCard(
                themeSettings = ThemePreferences.ThemeSettings(
                    themeMode = ThemeMode.fromStorage(draft.theme),
                    useDynamicColor = true
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
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
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = when (ThemeMode.fromStorage(draft.theme)) {
                    ThemeMode.LIGHT -> "Always light, even if your phone is in dark mode."
                    ThemeMode.DARK -> "Always dark, even if your phone is in light mode."
                    else -> "Follows your phone's light or dark appearance."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        5 -> SectionCard(
            title = "Title colour",
            icon = AppVectorIcons.SectionAppearance,
            subtitle = "You can change this later in Settings > Appearance"
        ) {
            SwitchRow(
                title = "Keep a title's colour",
                subtitle = "Opening a show tints the app, and the colour stays " +
                    "after you leave its page.",
                checked = draft.retainAnimeColor,
                onCheckedChange = { onDraftChange(draft.copy(retainAnimeColor = it)) },
                modifier = Modifier.testTag("onboarding_retain_anime_color")
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = if (draft.retainAnimeColor) {
                    "On: the app takes each title's cover colour and keeps the last one."
                } else {
                    "Off: every page uses your own theme. You can turn this on later."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        6 -> SectionCard(
            title = "Motion",
            icon = AppVectorIcons.SectionMotion,
            subtitle = "Tap the preview to replay it with the selected style"
        ) {
            val selectedMotion = MotionStyle.fromStorage(draft.motion)
            MotionSample(style = selectedMotion)
            Spacer(modifier = Modifier.height(10.dp))
            ChoiceChips(
                values = MotionStyle.entries.map {
                    it.storageValue to it.displayName
                },
                selected = draft.motion,
                onSelect = { onDraftChange(draft.copy(motion = it)) },
                tag = "onboarding_motion"
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = motionDescription(selectedMotion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        7 -> SectionCard(
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
        8 -> SectionCard(
            title = "List refresh",
            icon = AppVectorIcons.Refresh,
            subtitle = "How old your saved list may get before the app asks again"
        ) {
            RefreshIntervalPicker(
                selected = RefreshInterval.fromStorage(draft.refreshInterval),
                onSelect = { onDraftChange(draft.copy(refreshInterval = it.storageValue)) },
                tag = "onboarding_refresh_interval",
                // Shorter than the Settings copy on purpose: this page asks which
                // interval to start with, and has no saved list yet to describe.
                detail = { interval ->
                    when (interval) {
                        RefreshInterval.ALWAYS ->
                            "Check every time you return to the app."
                        RefreshInterval.MANUAL_ONLY ->
                            "Only the refresh button asks AniList for a new copy."
                        else ->
                            "Ask again after ${interval.displayName} of being away. A " +
                                "shorter interval spends more requests."
                    }
                }
            )
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

/** The wizard's choices, bundled so one page can receive all of them in one value. */
internal data class SetupDraft(
    val layout: String,
    val displayDpi: Int,
    val posterSource: String,
    val theme: String,
    val retainAnimeColor: Boolean,
    val motion: String,
    val jokesEnabled: Boolean,
    val jokeFrequency: String,
    val refreshInterval: String,
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
                minTouchTargetHeight = 48.dp,
                modifier = Modifier.testTag("${tag}_$value")
            )
        }
    }
}
