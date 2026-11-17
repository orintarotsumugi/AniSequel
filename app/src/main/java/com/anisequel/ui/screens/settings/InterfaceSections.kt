package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.data.repository.InterfacePreferences
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.card.CardSize
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.AppLayout
import com.anisequel.domain.ui.PosterSource
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
    SettingsScrollColumn {
        SectionCard(
            // Not "App layout": that is the header above this card now, and a card
            // title repeating the screen's own title is the stacking this page just
            // stopped doing one level up.
            title = "Layout and bar",
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
            title = "Feed shape",
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
            title = "Grid and rows",
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

            // What the chosen view actually promises, in the reader's words - and
            // the one thing it does not share with the card design. The two used to
            // fight: card size added a column to this grid, so "Big cards" picked
            // here could draw three across. The view owns the columns and the caption
            // policy now, and saying so here is cheaper than discovering it.
            val mode = interfaceSettings.libraryView
            CollapsibleText(
                text = mode.blurb + " " + when (mode) {
                    LibraryViewMode.LIST,
                    LibraryViewMode.GROUPED,
                    LibraryViewMode.TABLE ->
                        "A row is the layout in this mode, so the card design only " +
                            "changes the thumbnail and the type beside it."
                    LibraryViewMode.COVERS ->
                        "Cover wall is caption-free on purpose: the artwork names the " +
                            "title and the progress badge is the only thing printed " +
                            "on it, whatever the card design says."
                    LibraryViewMode.SHELF ->
                        "A strip of compact covers with no caption, for scanning a " +
                            "long list quickly."
                    else ->
                        "This view decides how many covers share a row and whether " +
                            "a caption is printed. The card design changes each " +
                            "card's type and cover shape; it cannot add or remove a " +
                            "column from this grid."
                } + " The choice follows the cards onto a profile's list too.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))
            LibraryViewPreview(
                mode = mode,
                entries = previewEntries
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The card, in three screens: its shape, the words it prints, and the art under them.
 *
 * ## Why these are peers of the Interface shelf
 *
 * They used to be a second shelf inside this one - Interface > Card design > Card
 * shape - and every level drew its own header under the one above, so a reader who
 * opened a part saw three breadcrumb rows and a sliver of the control they came for.
 * The three parts now open from the Card design shelf as siblings of "Card design" on
 * the Interface page, with exactly one header on screen at a time. Reset card design
 * stays on the shelf, because it resets every part.
 *
 * ## Why the drafts live in the text section
 *
 * [CardTextSection] keeps its own title and rating drafts, keyed on the stored
 * values: a rotation mid-drag used to keep the preference and lose the draft, and the
 * preview above the sliders has to read the value under the finger. The write still
 * happens on release, for the reason the bar-lift slider documents - a preference
 * written per frame would rebuild the grid behind the page under the thumb.
 */
internal enum class CardDesignPart(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val testTag: String
) {
    SHAPE(
        title = "Card shape",
        subtitle = "Style and the cover's own proportions",
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

/**
 * The shelf: the card as it is now, the three screens that change it, and one reset.
 *
 * The explanatory line above the preview is not padding. "Card design" sounds like it
 * owns the Library's layout, and it does not: the Library view decides the columns and
 * whether a mode prints captions at all, and the card design only decides what each
 * card is made of. Saying that here is cheaper than a reader discovering it after
 * choosing Big cards and finding three across.
 */
@Composable
internal fun CardDesignShelf(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    onOpen: (CardDesignPart) -> Unit
) {
    val scope = rememberCoroutineScope()
    val style by interfacePreferences.cardStyle.collectAsState(
        initial = MediaCardStyle.DEFAULT
    )
    val interfaceSettings by interfacePreferences.settings.collectAsState(
        initial = InterfacePreferences.InterfaceSettings()
    )
    val design = interfaceSettings.cardDesign

    SettingsScrollColumn {
        CollapsibleText(
            text = "These three screens change the card itself: its frame, the words " +
                "on it and the art behind them. They never change the Library's chosen " +
                "view - its column count, and a caption-free mode such as Cover wall, " +
                "stay exactly as the Library page set them - so a card can be restyled " +
                "without the list rearranging under it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        CardDesignPreview(
            style = style,
            design = design,
            entries = previewEntries,
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
            onClick = { scope.launch { interfacePreferences.setCardDesign(CardDesign.DEFAULT) } },
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

/** Style and cover shape: the two answers that only change what a card's frame is. */
@Composable
internal fun CardShapeSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>,
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

    SettingsScrollColumn {
        CardShapePart(
            style = style,
            design = design,
            entries = previewEntries,
            barTabs = barTabs,
            onStyle = { option ->
                if (option != style) {
                    scope.launch { interfacePreferences.setCardStyle(option) }
                }
            },
            onSize = { option ->
                if (option != design.size) {
                    scope.launch { interfacePreferences.setCardDesign(design.copy(size = option)) }
                }
            },
            onCorner = { radius ->
                scope.launch {
                    interfacePreferences.setCardDesign(design.copy(cornerRadius = radius))
                }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/** The words a card prints, on their own screen so the previews get the height. */
@Composable
internal fun CardTextSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>,
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
    // reader moved a title size whose boldness, size and scrolling are already part
    // of the answer.
    val commit: (CardDesign) -> Unit = { updated ->
        scope.launch { interfacePreferences.setCardDesign(updated) }
    }

    SettingsScrollColumn {
        CardTextPart(
            design = draft,
            entries = previewEntries,
            barTabs = barTabs,
            style = style,
            titleDraft = titleDraft,
            ratingDraft = ratingDraft,
            onTitleDraft = { titleDraft = it },
            onRatingDraft = { ratingDraft = it },
            showMyRatings = interfaceSettings.showMyRatings,
            showTheirRatings = interfaceSettings.showTheirRatings,
            onShowMyRatings = { show ->
                scope.launch { interfacePreferences.setShowMyRatings(show) }
            },
            onShowTheirRatings = { show ->
                scope.launch { interfacePreferences.setShowTheirRatings(show) }
            },
            onCommit = commit
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * The artwork, its source, and what btttr may print on it.
 *
 * The "Clear saved AniList covers?" dialog lives here rather than on the shelf because
 * this is the one control that changes the source; it is asked once per switch, not on
 * arrival, and its confirm action is the reader's, never automatic.
 */
@Composable
internal fun CardPosterSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>,
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
    val context = LocalContext.current
    var pendingPosterSource by remember { mutableStateOf<PosterSource?>(null) }

    SettingsScrollColumn {
        CardPosterPart(
            design = design,
            entries = previewEntries,
            barTabs = barTabs,
            style = style,
            posterSource = interfaceSettings.posterSource,
            onPosterSource = { option ->
                if (option != interfaceSettings.posterSource) {
                    scope.launch { interfacePreferences.setPosterSource(option) }
                    pendingPosterSource = option
                }
            },
            overlays = interfaceSettings.posterOverlays,
            onOverlays = { updated ->
                scope.launch { interfacePreferences.setPosterOverlays(updated) }
            },
            uniqueSeasons = interfaceSettings.posterUniqueSeasons,
            onUniqueSeasons = { unique ->
                scope.launch { interfacePreferences.setPosterUniqueSeasons(unique) }
            },
            onCommit = { updated ->
                scope.launch { interfacePreferences.setCardDesign(updated) }
            }
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    val asked = pendingPosterSource
    if (asked != null) {
        AlertDialog(
            onDismissRequest = { pendingPosterSource = null },
            title = { Text("Clear saved AniList covers?") },
            text = {
                Text(
                    "If you switched poster sources, you can clear the saved " +
                        "AniList cover images so the app reloads artwork. " +
                        "Clearing can use more data and AniList requests later " +
                        "because covers will be downloaded again."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    context.clearCoverCache()
                    pendingPosterSource = null
                }) { Text("Clear cache") }
            },
            dismissButton = {
                TextButton(onClick = { pendingPosterSource = null }) {
                    Text("Keep cache")
                }
            }
        )
    }
}

/**
 * The frame: the style, the cover's proportions, and the one sentence that keeps the
 * card design from claiming the Library's grid.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CardShapePart(
    style: MediaCardStyle,
    design: CardDesign,
    entries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    onStyle: (MediaCardStyle) -> Unit,
    onSize: (CardSize) -> Unit,
    onCorner: (Float) -> Unit
) {
    // The slider's live value while the finger is down. The preview reads this,
    // not the preference, so the corners move under the thumb; the preference is
    // written once on release, like the two type sliders.
    var cornerDraft by rememberSaveable(design.cornerRadius) {
        mutableStateOf(design.cornerRadius)
    }

    SectionCard(
        title = "Style and cover",
        icon = AppVectorIcons.ViewGrid,
        subtitle = "The frame every grid in the app draws"
    ) {
        CardDesignPreview(
            style = style,
            design = design.copy(cornerRadius = cornerDraft),
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
        SettingsFieldLabel(text = "Cover proportions")
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
                    onClickLabel = "Draw covers at ${option.displayName} proportions",
                    modifier = Modifier.testTag("card_size_${option.storageValue}")
                )
            }
        }
        CollapsibleText(
            text = design.size.blurb + " This changes the cover's own proportions only. " +
                "How many covers share a row is the Library view's decision: " +
                "\"Big cards\" stays two across and \"Cover wall\" three, whatever " +
                "size you pick here, and the same proportions follow the cards onto " +
                "a profile's list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(16.dp))
        CardDesignSlider(
            label = "Layout sharpness",
            valueLabel = "${cornerDraft.roundToInt()} dp",
            value = cornerDraft,
            range = CardDesign.MIN_CORNER_RADIUS..CardDesign.MAX_CORNER_RADIUS,
            steps = CORNER_RADIUS_STEPS,
            testTag = "card_corner_radius_slider",
            supporting = "How sharp the cover's corners are. 0 dp is a hard tile; " +
                "higher values soften the frame. The preview above and every grid, " +
                "shelf and profile list move together, so this is the one control " +
                "that decides how soft the whole layout looks.",
            onValueChange = { cornerDraft = it },
            onCommit = { onCorner(cornerDraft) }
        )
    }
}

/** 2dp between 0 and 28, as Slider's step count excludes both ends. */
private const val CORNER_RADIUS_STEPS = 13

/**
 * One labelled slider: a name, the value it is on, the track and a sentence about
 * what it changes.
 *
 * The preview above reads the draft, not the preference, so the number beside the
 * label and the card on the screen are the same number. Bound through
 * [rememberBoundSliderState] rather than owned: Reset writes this preference from a
 * button on another screen, and a slider that only read its first value would leave
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
