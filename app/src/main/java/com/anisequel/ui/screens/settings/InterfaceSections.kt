package com.anisequel.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.card.CardSize
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.DisplayDpi
import com.anisequel.domain.ui.HomeSequelStyle
import com.anisequel.domain.ui.LibraryViewMode
import com.anisequel.domain.ui.NavBarLift
import com.anisequel.domain.ui.UiSize
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.rememberBoundSliderState
import com.anisequel.ui.navigation.MainTab
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * The bodies behind the Interface page's shelf - one part per file section.
 *
 * Every card here used to sit in `InterfaceTab.kt`, stacked in one scroll. They were
 * moved, not rewritten: the controls, their tags and the preferences they write are
 * unchanged. The reason for the move is in [InterfaceTab]'s documentation - the page
 * had become six previews deep before it reached the control a reader came for.
 *
 * The rule that put these choices on the Interface page in the first place still
 * holds one level down: a part whose decision is only judgeable by looking at it (a
 * card, a layout, a bar gap, a motion feel) gets a sample above its picker, and the
 * sample is drawn by the component the app actually uses rather than a drawing of it.
 */

/**
 * Shelf or classic, the bar's gap and the Home rail.
 *
 * The rail switch stays beside the layout picker rather than moving to the sequel
 * sub-page: it is gated on the new layout, and a setting whose dependency is on a
 * different page is a switch that appears not to work.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterfaceLayoutSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>,
    /**
     * The bar the phone preview should draw.
     *
     * The preview used to draw four fixed glyphs, so a reader who had turned the tier
     * tab off met it again in the drawing of the bar they had just shortened. The list
     * is the one the real bar is built from, so the two cannot disagree.
     */
    barTabs: List<MainTab>
) {
    val scope = rememberCoroutineScope()
    val interfaceSettings by interfacePreferences.settings.collectAsState(
        initial = InterfacePreferences.InterfaceSettings()
    )
    // The slider's live value while the finger is down. The preview above reads
    // this, not the preference, so dragging moves the mini bar immediately; the
    // preference is written once on release rather than on every tick, because a
    // DataStore write per frame would make the drag the thing that stutters.
    // Saved as well as keyed: a rotation while the thumb is down kept the
    // preference but lost the draft, so the mini previews snapped back to the
    // stored value under a finger that had not lifted. The key still resets the
    // draft when an external write (Reset, or the preference after release)
    // changes the stored value.
    var liftDraft by rememberSaveable(interfaceSettings.mainBarLift) {
        mutableStateOf(interfaceSettings.mainBarLift.toFloat())
    }
    // The density draft, on the same rule for a sharper reason: density re-measures
    // every dp in the app, so a live value would resize the page - and the slider's
    // own track - while the finger is still on it. The preference is written on
    // release, and until then the number beside the label is what will be saved.
    var dpiDraft by rememberSaveable(interfaceSettings.displayDpi) {
        mutableStateOf(interfaceSettings.displayDpi.toFloat())
    }

    SettingsScrollColumn {
        SectionCard(
            title = "App layout",
            icon = AppVectorIcons.Settings,
            subtitle = "Classic is untouched. The new look is the shelf layout, in beta."
        ) {
            AppLayoutPreviews(
                selected = interfaceSettings.layout,
                entries = previewEntries,
                onSelect = { option ->
                    if (option != interfaceSettings.layout) {
                        scope.launch { interfacePreferences.setLayout(option) }
                    }
                },
                modifier = Modifier.testTag("app_layout_row"),
                barLift = NavBarLift.clamp(liftDraft.roundToInt())
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsFieldLabel(text = "Bottom bar lift")
                Text(
                    text = "${NavBarLift.clamp(liftDraft.roundToInt())} dp",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                // Bound, not owned: Reset writes this preference back to zero from
                // elsewhere, and a state that only read its first value would leave
                // the thumb where the reader left it.
                state = rememberBoundSliderState(
                    value = liftDraft,
                    // One tick every 2dp between 0 and 24. The divisor is the step,
                    // not a hand-written 11, so changing the range above keeps the
                    // ticks honest.
                    steps = ((NavBarLift.MAX_DP - NavBarLift.MIN_DP) / NavBarLift.STEP_DP - 1)
                        .coerceAtLeast(0),
                    trackRange = NavBarLift.MIN_DP.toFloat()..NavBarLift.MAX_DP.toFloat()
                ),
                onValueChange = { liftDraft = it },
                onValueChangeFinished = {
                    val lift = NavBarLift.clamp(liftDraft.roundToInt())
                    scope.launch { interfacePreferences.setMainBarLift(lift) }
                },
                modifier = Modifier.testTag("main_bar_lift_slider")
            )
            CollapsibleText(
                text = "The mini layouts above move with the slider, and the phone " +
                    "below shows the real gap from 0 to ${NavBarLift.MAX_DP} dp. " +
                    "The real bar floats this far above the bottom edge, and every " +
                    "list keeps room for it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            BottomBarLiftPreview(liftDp = liftDraft.roundToInt(), tabs = barTabs)

            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(
                title = "Continue Watching rail",
                subtitle = "Show in-progress anime above the sequel feed on the new Home.",
                checked = interfaceSettings.showContinueRail,
                enabled = interfaceSettings.layout == AppLayout.NOVA,
                onCheckedChange = { show ->
                    scope.launch { interfacePreferences.setShowContinueRail(show) }
                },
                modifier = Modifier.testTag("show_continue_rail_switch")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SectionCard(
            title = "UI size",
            icon = AppVectorIcons.Tune,
            subtitle = "How large every card, caption and gap is drawn - " +
                "the same on every screen until you say otherwise."
        ) {
            // The named sizes first, the slider second. A reader who wants the app
            // bigger should not have to know that the number going up means the
            // elements going down, and a preset is one tap where the slider is a
            // drag. The slider stays because 447 is not a size, it is a number.
            SettingsFieldLabel(text = "Size")
            Spacer(modifier = Modifier.height(8.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("ui_size_row")
            ) {
                UiSize.entries.forEach { option ->
                    ExpressiveStateChip(
                        label = option.displayName,
                        selected = UiSize.of(dpiDraft.roundToInt()) == option,
                        onClick = {
                            if (interfaceSettings.displayDpi != option.dpi) {
                                dpiDraft = option.dpi.toFloat()
                                scope.launch { interfacePreferences.setDisplayDpi(option.dpi) }
                            }
                        },
                        minTouchTargetHeight = 48.dp,
                        role = Role.RadioButton,
                        onClickLabel = "Draw the app at the ${option.displayName} UI size",
                        modifier = Modifier.testTag("ui_size_${option.name.lowercase()}")
                    )
                }
            }
            CollapsibleText(
                text = (UiSize.of(dpiDraft.roundToInt())?.blurb
                    ?: "Between two named sizes - the slider below has the exact value.") +
                    " This is the app's own size, not the phone's font setting: it scales " +
                    "cards, text and spacing together, so a layout that fits a small phone " +
                    "fits at every size you can pick.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsFieldLabel(text = "Exact size")
                Text(
                    text = "${DisplayDpi.clamp(dpiDraft.roundToInt())} dpi",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            Slider(
                // Bound, not owned: Reset writes this preference from the button below,
                // and a slider that only read its first value would keep the old thumb.
                state = rememberBoundSliderState(
                    value = dpiDraft,
                    // The same divisor rule as the lift slider: ticks follow the range
                    // and step in [DisplayDpi] rather than being counted by hand.
                    steps = ((DisplayDpi.MAX_DPI - DisplayDpi.MIN_DPI) / DisplayDpi.STEP_DPI - 1)
                        .coerceAtLeast(0),
                    trackRange = DisplayDpi.MIN_DPI.toFloat()..DisplayDpi.MAX_DPI.toFloat()
                ),
                onValueChange = { dpiDraft = it },
                onValueChangeFinished = {
                    scope.launch { interfacePreferences.setDisplayDpi(dpiDraft.roundToInt()) }
                },
                modifier = Modifier.testTag("display_density_slider")
            )
            CollapsibleText(
                // Said plainly because it is the surprising part: this is not the
                // phone's panel, and it applies to this very page.
                text = "Every length in the app is a dp, and a dp is only as big as the " +
                    "density says: a higher number fits more on the page, a lower one draws " +
                    "text, cards and gaps larger. The value applies to the whole app - this " +
                    "page included, the moment you let go - and does not depend on what the " +
                    "phone's own panel reports. ${DisplayDpi.DEFAULT_DPI} dpi is the shipped " +
                    "default.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            SettingsButton(
                text = "Reset size",
                onClick = {
                    scope.launch { interfacePreferences.setDisplayDpi(DisplayDpi.DEFAULT_DPI) }
                },
                icon = AppVectorIcons.Refresh,
                variant = SettingsButtonVariant.Outlined,
                // Disabled rather than hidden at the default: a control that vanishes
                // when it has nothing to do looks like a missing feature, and a reader
                // who does not know the default cannot tell where they are without it.
                enabled = interfaceSettings.displayDpi != DisplayDpi.DEFAULT_DPI,
                fillWidth = true,
                testTag = "display_density_reset"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** How the sequel feed draws each title: rows, cards or banners. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterfaceSequelSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>
) {
    val scope = rememberCoroutineScope()
    val interfaceSettings by interfacePreferences.settings.collectAsState(
        initial = InterfacePreferences.InterfaceSettings()
    )

    SettingsScrollColumn {
        SectionCard(
            title = "Home sequels",
            icon = AppVectorIcons.ViewGrid,
            subtitle = interfaceSettings.homeSequelStyle.blurb
        ) {
            SequelStylePreview(
                style = interfaceSettings.homeSequelStyle,
                entries = previewEntries
            )

            Spacer(modifier = Modifier.height(12.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("home_sequel_style_row")
            ) {
                HomeSequelStyle.entries.forEach { option ->
                    ExpressiveStateChip(
                        label = option.displayName,
                        selected = option == interfaceSettings.homeSequelStyle,
                        onClick = {
                            if (option != interfaceSettings.homeSequelStyle) {
                                scope.launch {
                                    interfacePreferences.setHomeSequelStyle(option)
                                }
                            }
                        },
                        minTouchTargetHeight = 48.dp,
                        role = Role.RadioButton,
                        onClickLabel = "Draw Home sequels as ${option.displayName}",
                        modifier = Modifier.testTag("home_sequel_style_${option.storageValue}")
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * How the Library draws the list, with a sample of the chosen mode.
 *
 * A card style and a library view are two decisions, not one: the style changes the
 * shape of a card everywhere it appears, the view changes how many of them share a
 * row. They are on separate sub-pages for that reason, even though both previews are
 * made of the reader's own covers.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterfaceLibrarySection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>
) {
    val scope = rememberCoroutineScope()
    val interfaceSettings by interfacePreferences.settings.collectAsState(
        initial = InterfacePreferences.InterfaceSettings()
    )

    SettingsScrollColumn {
        SectionCard(
            title = "Library view",
            icon = AppVectorIcons.ViewGrid,
            subtitle = interfaceSettings.libraryView.blurb
        ) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("library_view_row")
            ) {
                LibraryViewMode.entries.forEach { option ->
                    ExpressiveStateChip(
                        label = option.displayName,
                        selected = option == interfaceSettings.libraryView,
                        onClick = {
                            if (option != interfaceSettings.libraryView) {
                                scope.launch { interfacePreferences.setLibraryView(option) }
                            }
                        },
                        minTouchTargetHeight = 48.dp,
                        role = Role.RadioButton,
                        onClickLabel = "Draw the library as ${option.displayName}",
                        modifier = Modifier.testTag("library_view_${option.storageValue}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            LibraryViewPreview(
                mode = interfaceSettings.libraryView,
                entries = previewEntries
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The card, in three sub-pages: its shape, the words it prints, and the art under them.
 *
 * ## Why this became a shelf
 *
 * Every answer here describes one object, which is why the style picker, the two
 * sliders, the caption switches, the two rating switches, the poster source and the
 * card's size once shared a single card above one preview. That was still an honest
 * page when it held only the design; by the time the poster source moved in, a reader
 * who had come for the size chips was scrolling past a preview, ten style chips, two
 * sliders, four switches and a picker to reach them.
 *
 * The three parts answer three different questions - what shape is it, what does it
 * say, what does it show - and each keeps a preview at the top, because all three are
 * still judged by looking. The controls did not change: every chip, slider, switch,
 * test tag and preference write below is the one that was there before.
 *
 * ## Why the drafts live up here
 *
 * [titleDraft] and [ratingDraft] are held by this composable rather than by a part, so
 * opening another part does not lose a value the reader was mid-way through. The write
 * still happens on release, for the reason the bar-lift slider documents: a preference
 * written per frame would rebuild the grid behind the page under the finger.
 */
private enum class CardDesignPart(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
) {
    SHAPE(
        title = "Card shape",
        subtitle = "Style, and how many cards fit a row",
        icon = AppVectorIcons.ViewGrid,
        testTag = "card_design_row_shape"
    ),
    TEXT(
        title = "Name and score",
        subtitle = "Type size, weight, scrolling and which score",
        icon = AppVectorIcons.TextBold,
        testTag = "card_design_row_text"
    ),
    POSTER(
        title = "Poster art",
        subtitle = "AniList or BetterPosters, and what the card adds to it",
        icon = AppVectorIcons.SectionAppearance,
        testTag = "card_design_row_poster"
    )
}

/** The save key for the card design shelf, when no part is open. */
private const val CARD_DESIGN_HUB_KEY = "card_design_hub"

@Composable
internal fun InterfaceCardDesignSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>,
    /**
     * The bar to draw inside the preview.
     *
     * Passed in because the mock-up's bar has to be the reader's bar: a frame that
     * still shows a Search tab after the reader turned it off is the preview
     * contradicting the switch, one page away from the switch itself.
     */
    barTabs: List<MainTab>
) {
    val scope = rememberCoroutineScope()
    val style by interfacePreferences.cardStyle.collectAsState(
        initial = MediaCardStyle.DEFAULT
    )
    val interfaceSettings by interfacePreferences.settings.collectAsState(
        initial = InterfacePreferences.InterfaceSettings()
    )
    val design = interfaceSettings.cardDesign

    // Live values while a finger is down. Saved as well as keyed, for the reason the
    // bar-lift slider documents: a rotation mid-drag kept the preference and lost the
    // draft, so the preview snapped back under a thumb that had not lifted. The key
    // still resets the draft whenever a write from elsewhere - Reset, or the
    // preference after release - changes the stored value.
    var titleDraft by rememberSaveable(design.titleScale) {
        mutableStateOf(design.titleScale)
    }
    var ratingDraft by rememberSaveable(design.ratingScale) {
        mutableStateOf(design.ratingScale)
    }
    val draft = design.copy(titleScale = titleDraft, ratingScale = ratingDraft)

    // One write for the whole design: the page commits a drag on release, and the
    // reader moved a title size whose boldness, size and scrolling are already part of
    // the answer.
    val commit: (CardDesign) -> Unit = { updated ->
        scope.launch { interfacePreferences.setCardDesign(updated) }
    }

    var part by rememberSaveable { mutableStateOf<CardDesignPart?>(null) }

    // The shelf and the parts are separate compositions and `AnimatedContent` disposes
    // the outgoing one, so without this a trip to "Poster art" and back would return
    // the reader to the top of a preview they had scrolled past.
    val partStateHolder = rememberSaveableStateHolder()

    // Only while a part is open: disabled here, the event falls through to the
    // Interface shelf's own handler, which is what Back means on this shelf.
    BackHandler(enabled = part != null) { part = null }

    AnimatedContent(
        targetState = part,
        transitionSpec = { settingsLevelTransition() },
        label = "card_design_part_transition"
    ) { destination ->
        partStateHolder.SaveableStateProvider(
            key = destination?.name ?: CARD_DESIGN_HUB_KEY
        ) {
            if (destination == null) {
                CardDesignHub(
                    style = style,
                    design = draft,
                    entries = previewEntries,
                    barTabs = barTabs,
                    onOpen = { part = it },
                    onReset = { commit(CardDesign.DEFAULT) }
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    SettingsSubHeader(
                        title = destination.title,
                        subtitle = destination.subtitle,
                        onBack = { part = null },
                        // One page out, not two: the header above says "Interface", and
                        // this says which part of it the reader is looking at.
                        backDescription = "Back to card design",
                        backTestTag = "card_design_sub_back"
                    )
                    CardDesignPartBody(
                        part = destination,
                        interfacePreferences = interfacePreferences,
                        interfaceSettings = interfaceSettings,
                        style = style,
                        draft = draft,
                        entries = previewEntries,
                        barTabs = barTabs,
                        titleDraft = titleDraft,
                        ratingDraft = ratingDraft,
                        onTitleDraft = { titleDraft = it },
                        onRatingDraft = { ratingDraft = it },
                        onCommit = commit
                    )
                }
            }
        }
    }
}

/**
 * The shelf: the card as it stands, then the three parts that change it, then the one
 * control that takes all of them back.
 *
 * Reset sits here rather than inside a part because it resets every part - a button
 * labelled "Reset card design" on a page that only owns the name size would leave a
 * reader looking for the second reset they assume must exist.
 */
@Composable
private fun CardDesignHub(
    style: MediaCardStyle,
    design: CardDesign,
    entries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    onOpen: (CardDesignPart) -> Unit,
    onReset: () -> Unit
) {
    SettingsScrollColumn {
        CardDesignPreview(
            style = style,
            design = design,
            entries = entries,
            barTabs = barTabs
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsHubGroup(
            entries = listOf(
                cardDesignEntry(CardDesignPart.SHAPE, onOpen),
                cardDesignEntry(CardDesignPart.TEXT, onOpen),
                cardDesignEntry(CardDesignPart.POSTER, onOpen)
            )
        )

        Spacer(modifier = Modifier.height(16.dp))
        SettingsButton(
            text = "Reset card design",
            icon = AppVectorIcons.Refresh,
            variant = SettingsButtonVariant.Outlined,
            // Disabled rather than hidden: a control that vanishes leaves the reader
            // wondering whether the page lost a feature.
            enabled = design != CardDesign.DEFAULT,
            onClick = onReset,
            modifier = Modifier.testTag("card_design_reset")
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** One shelf row, named and described by the sub-page it opens. */
private fun cardDesignEntry(
    part: CardDesignPart,
    onOpen: (CardDesignPart) -> Unit
): SettingsHubEntry = SettingsHubEntry(
    icon = part.icon,
    title = part.title,
    subtitle = part.subtitle,
    testTag = part.testTag,
    onClick = { onOpen(part) }
)

/**
 * The body of one part.
 *
 * Drawn with the draft design so the sample above each control is the value the reader
 * is on, not the last one that was written - which is the difference between a slider
 * that works and a slider that explains itself after the fact.
 */
@Composable
private fun CardDesignPartBody(
    part: CardDesignPart,
    interfacePreferences: InterfacePreferences,
    interfaceSettings: InterfacePreferences.InterfaceSettings,
    style: MediaCardStyle,
    draft: CardDesign,
    entries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    titleDraft: Float,
    ratingDraft: Float,
    onTitleDraft: (Float) -> Unit,
    onRatingDraft: (Float) -> Unit,
    onCommit: (CardDesign) -> Unit
) {
    val scope = rememberCoroutineScope()
    SettingsScrollColumn {
        when (part) {
            CardDesignPart.SHAPE -> CardShapePart(
                style = style,
                design = draft,
                entries = entries,
                barTabs = barTabs,
                onStyle = { option ->
                    if (option != style) {
                        scope.launch { interfacePreferences.setCardStyle(option) }
                    }
                },
                onSize = { option -> onCommit(draft.copy(size = option)) }
            )
            CardDesignPart.TEXT -> CardTextPart(
                design = draft,
                entries = entries,
                barTabs = barTabs,
                style = style,
                titleDraft = titleDraft,
                ratingDraft = ratingDraft,
                onTitleDraft = onTitleDraft,
                onRatingDraft = onRatingDraft,
                showMyRatings = interfaceSettings.showMyRatings,
                showTheirRatings = interfaceSettings.showTheirRatings,
                onShowMyRatings = { show ->
                    scope.launch { interfacePreferences.setShowMyRatings(show) }
                },
                onShowTheirRatings = { show ->
                    scope.launch { interfacePreferences.setShowTheirRatings(show) }
                },
                onCommit = onCommit
            )
            CardDesignPart.POSTER -> CardPosterPart(
                design = draft,
                entries = entries,
                barTabs = barTabs,
                style = style,
                posterSource = interfaceSettings.posterSource,
                onPosterSource = { option ->
                    if (option != interfaceSettings.posterSource) {
                        scope.launch { interfacePreferences.setPosterSource(option) }
                    }
                },
                onCommit = onCommit
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** Style and size: the two answers that only change what a card's frame is. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardShapePart(
    style: MediaCardStyle,
    design: CardDesign,
    entries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    onStyle: (MediaCardStyle) -> Unit,
    onSize: (CardSize) -> Unit
) {
    SectionCard(
        title = "Card shape",
        icon = AppVectorIcons.ViewGrid,
        subtitle = "The frame every grid in the app draws"
    ) {
        CardDesignPreview(
            style = style,
            design = design,
            entries = entries,
            barTabs = barTabs
        )

        Spacer(modifier = Modifier.height(14.dp))
        SettingsFieldLabel(text = "Style")
        Spacer(modifier = Modifier.height(8.dp))

        // A wrapping row, not a scrolling one: a picker that hides two of its ten
        // options off the edge reads as those options having been removed, because
        // nothing about the row says it scrolls.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_style_row")
        ) {
            MediaCardStyle.entries.forEach { option ->
                ExpressiveStateChip(
                    label = option.displayName,
                    selected = option == style,
                    onClick = { onStyle(option) },
                    // This picker is the primary control of its sub-page, not a
                    // filter beside a grid: a 30dp chip is a fingertip target found
                    // by aiming, and the ten options are the whole decision.
                    minTouchTargetHeight = 48.dp,
                    // One style is active at a time, so this is a radio choice.
                    role = Role.RadioButton,
                    onClickLabel = "Use the ${option.displayName} card style",
                    modifier = Modifier.testTag("card_style_${option.storageValue}")
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsFieldLabel(text = "Card size")
        Spacer(modifier = Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("card_size_row")
        ) {
            CardSize.entries.forEach { option ->
                ExpressiveStateChip(
                    label = option.displayName,
                    selected = option == design.size,
                    onClick = { if (option != design.size) onSize(option) },
                    minTouchTargetHeight = 48.dp,
                    role = Role.RadioButton,
                    onClickLabel = "Draw cards at ${option.displayName} size",
                    modifier = Modifier.testTag("card_size_${option.storageValue}")
                )
            }
        }
        CollapsibleText(
            text = design.size.blurb + " Applies inside the app library and to every " +
                "poster grid that follows it, including somebody else's profile.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


/**
 * One labelled slider: a name, the value it is on, the track and a sentence about
 * what it changes.
 *
 * The preview above reads the draft, not the preference, so the number beside the
 * label and the card on the screen are the same number. Bound through
 * [rememberBoundSliderState] rather than owned: Reset writes this preference from a
 * button on the same page, and a slider that only read its first value would leave
 * its thumb where the reader left it.
 */
@Composable
internal fun CardDesignSlider(
    label: String,
    valueLabel: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    steps: Int,
    testTag: String,
    supporting: String,
    onValueChange: (Float) -> Unit,
    onCommit: () -> Unit,
    /** A word after the label, for a control whose name needs disambiguating. */
    labelDetail: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SettingsFieldLabel(text = label)
            if (labelDetail != null) {
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = labelDetail,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = valueLabel,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
    Slider(
        state = rememberBoundSliderState(value = value, steps = steps, trackRange = range),
        onValueChange = onValueChange,
        onValueChangeFinished = onCommit,
        modifier = Modifier.testTag(testTag)
    )
    CollapsibleText(
        text = supporting,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
