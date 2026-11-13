package com.anisequel.ui.screens.login

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
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
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.components.expressive.ExpressiveShapes
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
private data class SetupStep(
    val label: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector
)

private val SETUP_STEPS = listOf(
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
    var layout by rememberSaveable { mutableStateOf(AppLayout.DEFAULT.storageValue) }
    var theme by rememberSaveable { mutableStateOf(ThemeMode.SYSTEM.storageValue) }
    var motion by rememberSaveable { mutableStateOf(MotionStyle.DEFAULT.storageValue) }
    var jokesEnabled by rememberSaveable { mutableStateOf(true) }
    var jokeFrequency by rememberSaveable { mutableStateOf(JokeFrequency.NORMAL.storageValue) }
    var searchShortcut by rememberSaveable { mutableStateOf(false) }
    var tiersShortcut by rememberSaveable { mutableStateOf(false) }
    var stepIndex by rememberSaveable { mutableIntStateOf(0) }

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

    fun finishWithDefaults() {
        scope.launch { onboardingPreferences.setCompleted(true) }
    }

    val isLast = stepIndex == SETUP_STEPS.lastIndex
    val stepProgress by animateFloatAsState(
        targetValue = (stepIndex + 1f) / SETUP_STEPS.size,
        animationSpec = ExpressiveMotion.FastSpatial,
        label = "onboarding_progress"
    )

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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = AppVectorIcons.AnimeSparkle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = "AniSequel setup",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(
                        onClick = ::finishWithDefaults,
                        modifier = Modifier.testTag("onboarding_defaults")
                    ) {
                        Text("Skip")
                    }
                }

                // Capsule track rather than a `LinearProgressIndicator`: this is a
                // six-page walk, not a download, and the shape is the app's own.
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .height(4.dp)
                        .clip(ExpressiveShapes.pill)
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(stepProgress)
                            .fillMaxHeight()
                            .clip(ExpressiveShapes.pill)
                            .background(MaterialTheme.colorScheme.primary)
                    )
                }

                AnimatedContent(
                    targetState = stepIndex.coerceIn(0, SETUP_STEPS.lastIndex),
                    transitionSpec = {
                        val forward = targetState > initialState
                        (
                            slideInHorizontally(
                                animationSpec = ExpressiveMotion.DefaultSpatialOffset
                            ) { width -> if (forward) width / 4 else -width / 4 } +
                                fadeIn(animationSpec = ExpressiveMotion.FastEffects)
                            ) togetherWith (
                            slideOutHorizontally(
                                animationSpec = ExpressiveMotion.DefaultSpatialOffset
                            ) { width -> if (forward) -width / 4 else width / 4 } +
                                fadeOut(animationSpec = ExpressiveMotion.FastEffects)
                            ) using null
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    label = "onboarding_step"
                ) { index ->
                    val page = SETUP_STEPS[index]
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp)
                    ) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = page.icon,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(17.dp)
                            )
                            Spacer(modifier = Modifier.width(7.dp))
                            Text(
                                text = "${index + 1} of ${SETUP_STEPS.size} · ${page.label}",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = page.title,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = page.subtitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(20.dp))

                        when (index) {
                            0 -> WelcomePerks()
                            1 -> SectionCard(
                                title = "Interface",
                                icon = AppVectorIcons.ViewGrid,
                                subtitle = "Drawn with the same previews Settings uses"
                            ) {
                                AppLayoutPreviews(
                                    selected = AppLayout.fromStorage(layout),
                                    entries = emptyList(),
                                    onSelect = { layout = it.storageValue },
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
                                    selected = theme,
                                    onSelect = { theme = it },
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
                                    selected = motion,
                                    onSelect = { motion = it },
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
                                    checked = jokesEnabled,
                                    onCheckedChange = { jokesEnabled = it },
                                    modifier = Modifier.testTag("onboarding_jokes_switch")
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                if (jokesEnabled) {
                                    ChoiceChips(
                                        values = JokeFrequency.entries.map {
                                            it.storageValue to it.displayName
                                        },
                                        selected = jokeFrequency,
                                        onSelect = { jokeFrequency = it },
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
                                    checked = searchShortcut,
                                    onCheckedChange = { searchShortcut = it },
                                    modifier = Modifier.testTag("onboarding_search_shortcut")
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                SwitchRow(
                                    title = "Tier lists",
                                    subtitle = "Add a Tiers tab and a trophy shortcut in both " +
                                        "layouts.",
                                    checked = tiersShortcut,
                                    onCheckedChange = { tiersShortcut = it },
                                    modifier = Modifier.testTag("onboarding_tiers_shortcut")
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(20.dp))
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (stepIndex > 0) {
                        OutlinedButton(
                            onClick = { stepIndex -= 1 },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("onboarding_back")
                        ) {
                            Text("Back")
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                    ExpressivePrimaryButton(
                        text = if (isLast) "Finish" else "Next",
                        onClick = {
                            if (isLast) saveChoices() else stepIndex += 1
                        },
                        modifier = Modifier.weight(1.4f),
                        icon = if (isLast) AppVectorIcons.CheckCircle else AppVectorIcons.SequelArrow,
                        testTag = if (isLast) "onboarding_save" else "onboarding_next"
                    )
                }
            }
        }
    }
}

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
