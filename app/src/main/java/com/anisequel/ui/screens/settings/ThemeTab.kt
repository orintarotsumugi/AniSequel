package com.anisequel.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropTint
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.MotionStyle
import com.anisequel.data.repository.RetainedTint
import com.anisequel.data.repository.ThemeMode
import com.anisequel.data.repository.ThemePreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.DarkColorScheme
import com.anisequel.ui.theme.DynamicThemeBuilder
import com.anisequel.ui.theme.LightColorScheme
import com.anisequel.ui.theme.ThemePalette
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The key the [rememberSaveableStateHolder] files the Theme shelf under. */
private const val THEME_HUB_KEY = "theme_hub"

/**
 * The dedicated Theme page.
 *
 * ## Why this is a shelf now
 *
 * The page was six full cards in one scroll - appearance, accent colour, background,
 * poster colour, anime pages, reset - and every one of them opens with pictures: a
 * row of scheme tiles, a field of swatches, eight canvas chips, a colour wheel. A
 * reader who came to turn OLED mode on was scrolling past a preview hero, three mode
 * tiles, a palette grid and a hue slider to find a switch, and the page's own summary
 * ("colour") had stopped describing most of what was on it.
 *
 * The same argument that split Settings into pages applies one level down, and the
 * page next door - Interface - already answers it the same way: each part gets a row
 * with a sentence under it and a sub-page of its own. Nothing was rewritten in the
 * move; every card below is the card that used to sit here, still writing the
 * preference it wrote.
 *
 * ## Why the preview stays on the shelf
 *
 * [ThemePreviewCard] is the page's identity, and the one answer a reader checks before
 * choosing which part to open: is the app the colour I meant? A live sample on the
 * shelf is worth more than one repeated at the top of four sub-pages.
 *
 * ## Why only one header is on screen
 *
 * This composable draws the header for whatever level is open - "Appearance" on the
 * shelf, the part's own title inside a part - and `SettingsScreen` stops drawing a
 * header around this page (`SettingsPage.ownsHeader`). It used to draw one above and
 * this page drew another below, so the reader saw two titles stacked before any
 * control. Back drops the sub-page before it drops the page: this composable's
 * `BackHandler` is registered after the one in `SettingsScreen`, and the dispatcher
 * gives the most recently added enabled callback the event.
 */
@Composable
internal fun ThemeTab(
    themePreferences: ThemePreferences,
    /**
     * The header this page shows on its own shelf, supplied by `SettingsScreen` from
     * the `SettingsPage` enum its shelf row read - the row and the page still cannot
     * describe different things.
     */
    pageTitle: String,
    pageSubtitle: String,
    /** Leaves the Theme page for the Settings shelf. */
    onClosePage: () -> Unit,
    /** A part a Settings search result asked for; consumed once, like Interface's. */
    initialSection: ThemeSection? = null,
    onInitialSectionConsumed: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val themeSettings by themePreferences.settings.collectAsStateWithLifecycle(
        initialValue = ThemePreferences.ThemeSettings(
            themeMode = ThemeMode.DEFAULT,
            useDynamicColor = false
        )
    )

    var section by rememberSaveable { mutableStateOf<ThemeSection?>(null) }

    LaunchedEffect(initialSection) {
        if (initialSection != null) {
            section = initialSection
            onInitialSectionConsumed()
        }
    }

    // The shelf and the sub-pages are separate compositions, and `AnimatedContent`
    // disposes the outgoing one - so a sub-page's scroll position would start at the
    // top every time the reader came back to it, and so would the shelf's.
    val sectionStateHolder = rememberSaveableStateHolder()

    // Only while a sub-page is open: disabled here, the event falls through to the
    // Settings page's own handler, which is what Back means on the shelf.
    BackHandler(enabled = section != null) { section = null }

    AnimatedContent(
        targetState = section,
        // The same push in and drop away the Settings pages use, so a level deeper
        // reads as a level deeper at both depths - see `settingsLevelTransition`.
        transitionSpec = { settingsLevelTransition() },
        label = "theme_section_transition",
        modifier = modifier
            .fillMaxSize()
            .testTag("theme_tab_content")
    ) { destination ->
        sectionStateHolder.SaveableStateProvider(
            key = destination?.name ?: THEME_HUB_KEY
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Exactly one header: the page's own on the shelf, the part's inside
                // a part. See the class documentation.
                SettingsSubHeader(
                    title = destination?.title ?: pageTitle,
                    subtitle = destination?.subtitle ?: pageSubtitle,
                    onBack = {
                        if (destination == null) onClosePage() else section = null
                    },
                    backDescription = if (destination == null) {
                        "Back to settings"
                    } else {
                        "Back to Theme"
                    },
                    backTestTag = if (destination == null) {
                        "settings_sub_back"
                    } else {
                        "theme_sub_back"
                    }
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    if (destination == null) {
                        ThemeHub(
                            themePreferences = themePreferences,
                            settings = themeSettings,
                            scope = scope,
                            onOpen = { section = it }
                        )
                    } else {
                        ThemeSectionBody(
                            section = destination,
                            themePreferences = themePreferences,
                            settings = themeSettings,
                            scope = scope
                        )
                    }
                }
            }
        }
    }
}

/** The body of one Theme part: the card that used to sit in the page's scroll. */
@Composable
private fun ThemeSectionBody(
    section: ThemeSection,
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    when (section) {
        ThemeSection.BACKGROUND -> SettingsScrollColumn {
            BackgroundSection(
                themePreferences = themePreferences,
                settings = settings,
                scope = scope
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        ThemeSection.ACCENT -> SettingsScrollColumn {
            AccentColourSection(
                themePreferences = themePreferences,
                settings = settings,
                scope = scope
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        ThemeSection.ARTWORK -> SettingsScrollColumn {
            AnimeColourSection(
                themePreferences = themePreferences,
                settings = settings,
                scope = scope
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
        ThemeSection.PAGES -> SettingsScrollColumn {
            InfoBackdropSection(
                themePreferences = themePreferences,
                settings = settings,
                scope = scope
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

/**
 * The parts of the Theme page, in the order the shelf draws them.
 *
 * An enum rather than four lambdas for the reason `SettingsPage` is one: the row's
 * title and the sub-page's header come from the same two strings, so a row cannot
 * promise a different page than the one it opens.
 */
internal enum class ThemeSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
) {
    BACKGROUND(
        title = "Background",
        subtitle = "OLED mode, the canvas colour and the Home glow",
        icon = AppVectorIcons.SectionBackground,
        testTag = "theme_row_background"
    ),
    ACCENT(
        title = "Accent colour",
        subtitle = "The palette the app is painted from",
        icon = AppVectorIcons.SectionAppearance,
        testTag = "theme_row_accent"
    ),
    ARTWORK(
        title = "Poster colour",
        subtitle = "Tint the app from the anime you open",
        icon = AppVectorIcons.AnimeSparkle,
        testTag = "theme_row_artwork"
    ),
    PAGES(
        title = "Anime pages",
        subtitle = "The backdrop an opened title sits on",
        icon = AppVectorIcons.SectionBackground,
        testTag = "theme_row_pages"
    )
}

/**
 * The shelf: the live sample, the four parts, and the one control that takes all of
 * them back.
 *
 * Reset sits on the shelf rather than inside a part because it resets every part - it
 * clears the mode, the palette, the canvas, the retained cover colour and the backdrop
 * in one write - so a button inside "Accent colour" would be a button that clears
 * controls the reader cannot see from there.
 */
@Composable
private fun ThemeHub(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope,
    onOpen: (ThemeSection) -> Unit
) {
    SettingsScrollColumn {
        ThemePreviewCard(themeSettings = settings)

        // The mode tiles used to live one level down, under an "Appearance" row
        // that opened a sub-page containing only them and the background canvas.
        // Light/Dark/System is the first thing a reader comes to this page for, and
        // making it the page's own second card removes a screen between the tap and
        // the switch. The canvas keeps its sub-page row below.
        Spacer(modifier = Modifier.height(16.dp))
        AppearanceSection(
            themePreferences = themePreferences,
            settings = settings,
            scope = scope
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsSectionLabel("Look", compact = true)
        SettingsHubGroup(
            entries = listOf(
                themeEntry(ThemeSection.ACCENT, onOpen),
                themeEntry(ThemeSection.BACKGROUND, onOpen)
            )
        )

        // Full screen moved to Device Manager, and motion to the Interface page:
        // neither is about what colour the app is, and this page is only about that.
        SettingsSectionLabel("Artwork", compact = true)
        SettingsHubGroup(
            entries = listOf(
                themeEntry(ThemeSection.ARTWORK, onOpen),
                themeEntry(ThemeSection.PAGES, onOpen)
            )
        )

        val appearanceChanged = settings.themeMode != ThemeMode.DEFAULT ||
            settings.useDynamicColor ||
            settings.paletteId != ThemePalette.ANISDK.id ||
            settings.motionStyle != MotionStyle.DEFAULT ||
            settings.trueBlack ||
            settings.customColorHex != null ||
            // The switch is stored, the layer is not, so testing the stored one is
            // what answers "is there anything here to reset" on a screen where no
            // sheet is open.
            settings.animeThemeEnabled ||
            settings.animeThemeActive ||
            // A retained cover colour is a theme override in its own right now: it
            // tints the scheme after the sheet, so Reset appearance has to be
            // offered for it even if the accent it wrote is cleared some other way.
            settings.retainedAnimeColorHex != null ||
            // Retain and revert are cleared by `reset()` like everything else here,
            // so leaving them out meant a reader who changed only those two had
            // something to reset and no button offering to reset it. Retention
            // defaults to on, which is why the test is negated.
            !settings.animeThemeRetainColor ||
            settings.retainedTint != RetainedTint.DEFAULT ||
            settings.animeThemeRevertDuration != DEFAULT_ANIME_REVERT_SECONDS ||
            settings.appBackgroundColor != null ||
            // Both are Reset appearance's to clear: the backdrop is part of the
            // app's look, not a per-title choice, and leaving it behind would make
            // the button restore everything but the page a title opens on.
            settings.infoBackdropSource != InfoBackdropSource.DEFAULT ||
            settings.infoBackdropBlur != InfoBackdropBlur.DEFAULT ||
            settings.infoBackdropTint != InfoBackdropTint.DEFAULT ||
            settings.novaBackdropBlur ||
            // Full screen is off by default; turning it on is a change, so the
            // reset row has to offer itself for exactly that case.
            settings.immersive

        // The button used to appear with a bare `if`, fully drawn on the next
        // frame - so the page jumped by a button's height the moment the reader
        // changed anything, and jumped back when they reset. The expansion makes
        // the movement the thing they see, matching the reset rows on the
        // Library page.
        AnimatedVisibility(
            visible = appearanceChanged,
            enter = fadeIn(animationSpec = ExpressiveMotion.FastEffects) +
                expandVertically(animationSpec = ExpressiveMotion.FastSpatialSize),
            exit = fadeOut(animationSpec = ExpressiveMotion.FastEffects) +
                shrinkVertically(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            Column {
                Spacer(modifier = Modifier.height(8.dp))
                SettingsButton(
                    text = "Reset appearance",
                    onClick = { scope.launch { themePreferences.reset() } },
                    icon = AppVectorIcons.Restore,
                    variant = SettingsButtonVariant.Outlined,
                    fillWidth = true,
                    testTag = "reset_appearance_button"
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** One shelf row, named and described by the sub-page it opens. */
private fun themeEntry(
    section: ThemeSection,
    onOpen: (ThemeSection) -> Unit
): SettingsHubEntry = SettingsHubEntry(
    icon = section.icon,
    title = section.title,
    subtitle = section.subtitle,
    testTag = section.testTag,
    onClick = { onOpen(section) }
)

@Composable
private fun AppearanceSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        // On the shelf rather than behind a row: this is the page's mode, and the
        // three tiles are the picture the row used to promise.
        title = "Mode",
        icon = AppVectorIcons.ThemeDark,
        subtitle = "Light, dark, or follow the device"
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("theme_mode_row")
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeModeTile(
                    mode = mode,
                    selected = mode == settings.themeMode,
                    onClick = {
                        if (mode != settings.themeMode) {
                            scope.launch { themePreferences.setThemeMode(mode) }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "System follows your phone's light or dark setting.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One appearance mode as a picture of what it does, not as a word for it.
 *
 * ## Why a mock window rather than a segmented bar
 *
 * The bar said "System / Light / Dark" and the reader had to know which of those
 * words described the app they were about to get. A tile shows a small window in
 * that scheme, so the choice is answered by looking. It is the same reason the
 * seasonal page draws a sky instead of a slider alone, and why the card style on the
 * Interface page is a preview: a setting whose effect is visual is chosen visually.
 *
 * ## Why the window is drawn from the base schemes
 *
 * `LightColorScheme` and `DarkColorScheme` are what the app falls back to before a
 * wallpaper, a palette or a custom hue overrides it. Showing those rather than the
 * live scheme is deliberate: the live scheme is what the preview card above already
 * shows, and a tile that rendered it could not show the *other* mode at all - the
 * one thing a mode chooser has to be able to say.
 */
@Composable
private fun ThemeModeTile(
    mode: ThemeMode,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val scheme = when (mode) {
        ThemeMode.LIGHT -> LightColorScheme
        ThemeMode.DARK -> DarkColorScheme
        // "Follow the phone" has no single scheme to show, so the window is painted
        // half in each: the only honest picture of a mode that answers to another app.
        ThemeMode.SYSTEM -> DarkColorScheme
    }

    // A constant 1.5dp outline whose *colour* animates, rather than 1dp growing
    // to 2dp on selection. The old pair changed the layout by half a dp as well
    // as the colour, so the tile under the finger nudged its neighbours and the
    // border could not crossfade. The selected colour still does all the talking.
    val borderColor by animateColorAsState(
        targetValue = if (selected) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.outlineVariant
        },
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "theme_mode_border_${mode.name}"
    )

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(1.5.dp, borderColor),
            modifier = Modifier
                .fillMaxWidth()
                .bouncyPress(pressedScale = 0.94f, interactionSource = interactionSource)
                .clickable(
                    interactionSource = interactionSource,
                    indication = null,
                    role = Role.RadioButton,
                    onClickLabel = "Use the ${mode.displayName.lowercase(java.util.Locale.ROOT)} appearance"
                ) { onClick() }
                .testTag("theme_mode_${mode.name.lowercase(java.util.Locale.ROOT)}")
                // `Role.RadioButton` alone says "a choice"; it does not say which
                // one is chosen. Without this the three tiles were announced
                // identically and the only evidence of the current mode was the
                // border a screen reader cannot see.
                .semantics { this.selected = selected }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(
                            // "Follow the phone" has no single scheme to show, so the
                            // window is painted half in each. Every other mode gets its
                            // own background outright.
                            if (mode == ThemeMode.SYSTEM) {
                                Brush.horizontalGradient(
                                    listOf(LightColorScheme.background, DarkColorScheme.background)
                                )
                            } else {
                                Brush.verticalGradient(listOf(scheme.background, scheme.background))
                            }
                        )
                        .padding(6.dp)
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        WindowDot(scheme.error)
                        WindowDot(scheme.tertiary)
                        WindowDot(scheme.primary)
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .fillMaxWidth()
                    ) {
                        WindowChip(scheme.primaryContainer, modifier = Modifier.weight(1f))
                        WindowChip(scheme.surfaceContainerHighest, modifier = Modifier.weight(1f))
                        WindowChip(scheme.secondaryContainer, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = mode.displayName,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            maxLines = 1
        )
    }
}

@Composable
private fun WindowDot(color: Color) {
    Box(
        modifier = Modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun WindowChip(color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .height(6.dp)
            .clip(ExpressiveShapes.pill)
            .background(color)
    )
}

@Composable
private fun BackgroundSection(
    themePreferences: ThemePreferences,
    settings: ThemePreferences.ThemeSettings,
    scope: CoroutineScope
) {
    SectionCard(
        title = "Background",
        icon = AppVectorIcons.SectionBackground,
        subtitle = "The canvas every surface is derived from"
    ) {
        // "OLED mode", which is the name a reader meets in the phone's own display
        // settings. The switch has always been about the panel; "true black" described
        // the colour and left the reason out.
        SwitchRow(
            title = "OLED mode",
            subtitle = "Pure black backgrounds drain less power on OLED screens.",
            checked = settings.trueBlack,
            onCheckedChange = { enabled ->
                scope.launch { themePreferences.setTrueBlack(enabled) }
            },
            modifier = Modifier.testTag("true_black_switch")
        )

        Spacer(modifier = Modifier.height(8.dp))
        // Off by default: `Modifier.blur` draws through an offscreen layer, and the
        // new layout's backdrop reads as depth without it. The switch exists so a
        // reader who wants the softer look can ask for it and pay for it knowingly.
        SwitchRow(
            title = "Home backdrop blur",
            subtitle = "Softens the glow behind the new Home and its pages. Off by " +
                "default because the blur costs an extra draw pass on every screen.",
            checked = settings.novaBackdropBlur,
            onCheckedChange = { enabled ->
                scope.launch { themePreferences.setNovaBackdropBlur(enabled) }
            },
            modifier = Modifier.testTag("nova_backdrop_blur_switch")
        )

        Spacer(modifier = Modifier.height(16.dp))
        // `SettingsFieldLabel`, not a hand-rolled bold `labelMedium`: this caption
        // and the ones on the Accent and Jokes cards do the same job, and three
        // private copies is how one of them ends up 12sp bold while the others are
        // 14sp medium.
        SettingsFieldLabel(text = "Canvas colour")
        Spacer(modifier = Modifier.height(10.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            backgroundColors.forEach { (hex, name) ->
                CanvasChip(
                    hex = hex,
                    name = name,
                    isSelected = settings.appBackgroundColor?.equals(hex, ignoreCase = true) == true ||
                        (hex == null && settings.appBackgroundColor == null),
                    onClick = {
                        scope.launch { themePreferences.setAppBackgroundColor(hex) }
                    }
                )
            }
        }
    }
}

/**
 * One canvas choice as a pill chip: a leading dot of the actual colour plus the
 * name. `null` is "follow the theme", drawn with the current surface colour.
 */
@Composable
private fun CanvasChip(
    hex: String?,
    name: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val dotColor = if (hex != null) {
        DynamicThemeBuilder.parseHexColor(hex) ?: Color.DarkGray
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
    val container by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.surfaceContainerHigh,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "canvas_chip_container"
    )
    val content by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "canvas_chip_content"
    )
    // One width, animated colour: see `ThemeModeTile` for why the selected state
    // must not resize the control it is marking.
    val outline by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        animationSpec = ExpressiveMotion.FastColorEffects,
        label = "canvas_chip_outline"
    )

    Surface(
        shape = ExpressiveShapes.pill,
        color = container,
        border = BorderStroke(1.dp, outline),
        modifier = Modifier
            .bouncyPress(pressedScale = 0.92f)
            .clickable(
                role = Role.RadioButton,
                onClickLabel = "Use the $name canvas colour",
                onClick = onClick
            )
            // The dot carries no text of its own, so without this the chip was a
            // pure-text control with no state: a reader could hear "Mist White"
            // but not whether the app was already wearing it.
            .semantics { this.selected = isSelected }
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(14.dp)
                    .clip(CircleShape)
                    .background(dotColor)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = content,
                maxLines = 1
            )
        }
    }
}

/**
 * The background canvas choices; `null` is "follow the theme".
 *
 * These used to be dark colours only, because the canvas override was gated on
 * dark mode and did nothing in light. `applyCanvasColour` now derives the text
 * and surface ramp from whichever chip is picked, so light canvases are honest
 * choices and the light-mode reader gets the same "this changed the whole app"
 * result as the dark-mode one.
 */
private val backgroundColors = listOf(
    null to "Default",
    "#000000" to "Pure Black",
    "#080C14" to "Midnight Navy",
    "#121212" to "Deep Charcoal",
    "#1C1410" to "Warm Espresso",
    "#101820" to "Slate Navy",
    "#F2F0F7" to "Mist White",
    "#F5EFE3" to "Warm Paper"
)

/**
 * Five minutes, which is the default `ThemePreferences.ThemeSettings` stores for
 * the anime revert timer.
 *
 * Repeated here rather than read from the repository because the question this
 * answers is only "has the reader changed anything worth resetting", and the
 * alternative - a public constant on the preferences class - would be a public
 * API for one comparison. `AnimeColourSection` offers the same three values, so
 * if the default moves, both files move with it.
 */
private const val DEFAULT_ANIME_REVERT_SECONDS = 300
