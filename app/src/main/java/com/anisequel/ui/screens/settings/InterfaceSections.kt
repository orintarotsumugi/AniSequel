package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.anisequel.domain.ui.PosterSource
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.rememberBoundSliderState
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
@Composable
internal fun InterfaceLayoutSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>
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
            BottomBarLiftPreview(liftDp = liftDraft.roundToInt())

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
            title = "Display density",
            icon = AppVectorIcons.Tune,
            subtitle = "How large every card, caption and gap is drawn - " +
                "${DisplayDpi.DEFAULT_DPI} dpi unless you say otherwise."
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                SettingsFieldLabel(text = "Density")
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
                text = "Reset density",
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
 * The whole card, not just its shape: style, name size, rating size, size in the
 * grid, weight and scrolling.
 *
 * ## Why this replaced the style picker rather than sitting beside it
 *
 * All six answers describe one object. Split across two sub-pages, a reader would
 * have picked a style on one page and then discovered on the next that the title size
 * they wanted was somewhere else - with the same card drawn at the top of both. The
 * picker therefore moved here whole (the chips, their tags and the preference they
 * write are unchanged) and everything new is arranged under it, above one preview
 * that shows all of it at once.
 *
 * ## Why the preview is above the controls
 *
 * A card's design is judged by looking, and the thing being judged is on the screen
 * before the reader touches a control - so every change lands on a card they are
 * already looking at rather than on one they have to scroll back to. This is also
 * why the two sliders keep a draft: the preview reads the finger, the preference is
 * written on release, and the grid behind the page never sees a half-dragged value.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun InterfaceCardDesignSection(
    interfacePreferences: InterfacePreferences,
    previewEntries: List<MediaListEntryItem>
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

    SettingsScrollColumn {
        SectionCard(
            title = "Card design",
            icon = AppVectorIcons.ViewGrid,
            subtitle = "Style, poster, title, rating and size - on every card, everywhere"
        ) {
            CardDesignPreview(
                style = style,
                design = draft,
                entries = previewEntries
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
                        onClick = {
                            if (option != style) {
                                scope.launch { interfacePreferences.setCardStyle(option) }
                            }
                        },
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
            CardDesignSlider(
                label = "Title size",
                valueLabel = "${(titleDraft * 100f).roundToInt()}%",
                value = titleDraft,
                range = CardDesign.MIN_TITLE_SCALE..CardDesign.MAX_TITLE_SCALE,
                steps = TITLE_SIZE_STEPS,
                testTag = "card_title_size_slider",
                supporting = "How large the anime name is drawn on a card. Every card " +
                    "in the app follows it - the Library, a profile's list and the " +
                    "sequel rows on Home.",
                onValueChange = { titleDraft = it },
                onCommit = { commit(design.copy(titleScale = titleDraft, ratingScale = ratingDraft)) }
            )

            Spacer(modifier = Modifier.height(12.dp))
            CardDesignSlider(
                label = "Rating size",
                labelDetail = "the score",
                valueLabel = "${(ratingDraft * 100f).roundToInt()}%",
                value = ratingDraft,
                range = CardDesign.MIN_RATING_SCALE..CardDesign.MAX_RATING_SCALE,
                steps = RATING_SIZE_STEPS,
                testTag = "card_rating_size_slider",
                supporting = "The score on a card, as a corner badge, a band or a " +
                    "caption line depending on the style above - the pair of switches " +
                    "below chooses which score it is. Scaled apart from the name, " +
                    "because a score is scanned and a name is read.",
                onValueChange = { ratingDraft = it },
                onCommit = { commit(design.copy(titleScale = titleDraft, ratingScale = ratingDraft)) }
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingsFieldLabel(text = "Which rating")
            Spacer(modifier = Modifier.height(4.dp))
            // Two switches rather than one "personal score" toggle, because the two
            // numbers are read in different situations: your own score is a reminder on
            // your Library, somebody else's is the reason you opened their profile. A
            // reader may want to see theirs without their own list turning into a
            // scoreboard. Off means "the community average", which is what every card
            // drew before this setting existed - never a card with no number at all.
            SwitchRow(
                title = "Your rating, on your list",
                subtitle = "Print the score you gave a title instead of the community " +
                    "average. Applies to every list shape - grid, list, grouped and " +
                    "table - and to an entry you have not scored the average still " +
                    "shows.",
                checked = interfaceSettings.showMyRatings,
                onCheckedChange = { show ->
                    scope.launch { interfacePreferences.setShowMyRatings(show) }
                },
                modifier = Modifier.testTag("show_my_ratings_switch")
            )
            SwitchRow(
                title = "Their rating, on their profile",
                subtitle = "Print the score the profile's owner gave, when you open " +
                    "someone else's library. AniList only returns those scores for a " +
                    "public list, and an unscored entry keeps the community average.",
                checked = interfaceSettings.showTheirRatings,
                onCheckedChange = { show ->
                    scope.launch { interfacePreferences.setShowTheirRatings(show) }
                },
                modifier = Modifier.testTag("show_their_ratings_switch")
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingsFieldLabel(text = "Poster art")
            Spacer(modifier = Modifier.height(8.dp))
            // A picker rather than a switch, because these are two sources of one
            // picture: AniList's own cover, or btttr's rendered poster. The blurb under
            // it says what the reader will actually see, since "BetterPosters" alone
            // promises art the app cannot promise for a title btttr does not carry.
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("poster_source_row")
            ) {
                PosterSource.entries.forEach { option ->
                    ExpressiveStateChip(
                        label = option.displayName,
                        selected = option == interfaceSettings.posterSource,
                        onClick = {
                            if (option != interfaceSettings.posterSource) {
                                scope.launch { interfacePreferences.setPosterSource(option) }
                            }
                        },
                        minTouchTargetHeight = 48.dp,
                        role = Role.RadioButton,
                        onClickLabel = "Draw card posters from ${option.displayName}",
                        modifier = Modifier.testTag("poster_source_${option.storageValue}")
                    )
                }
            }
            CollapsibleText(
                text = interfaceSettings.posterSource.blurb,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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
                        onClick = {
                            if (option != design.size) {
                                commit(
                                    design.copy(
                                        titleScale = titleDraft,
                                        ratingScale = ratingDraft,
                                        size = option
                                    )
                                )
                            }
                        },
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

            Spacer(modifier = Modifier.height(8.dp))
            SwitchRow(
                title = "Bold titles",
                subtitle = "Draw every card's name a weight heavier. The shipped card is " +
                    "semibold; this is the step above it.",
                checked = design.boldTitles,
                onCheckedChange = { bold ->
                    commit(
                        design.copy(
                            titleScale = titleDraft,
                            ratingScale = ratingDraft,
                            boldTitles = bold
                        )
                    )
                },
                modifier = Modifier.testTag("card_bold_titles_switch")
            )
            SwitchRow(
                title = "Scroll long titles",
                subtitle = "A name too long for its line scrolls once it has rested, " +
                    "instead of being cut. The speed itself is on the Type page, under " +
                    "Long titles; an Instant motion style still stops every marquee.",
                checked = design.scrollTitles,
                onCheckedChange = { scroll ->
                    commit(
                        design.copy(
                            titleScale = titleDraft,
                            ratingScale = ratingDraft,
                            scrollTitles = scroll
                        )
                    )
                },
                modifier = Modifier.testTag("card_scroll_titles_switch")
            )

            Spacer(modifier = Modifier.height(12.dp))
            SettingsButton(
                text = "Reset card design",
                icon = AppVectorIcons.Refresh,
                variant = SettingsButtonVariant.Outlined,
                // Disabled rather than hidden: a control that vanishes leaves the
                // reader wondering whether the page lost a feature.
                enabled = design != CardDesign.DEFAULT,
                onClick = { commit(CardDesign.DEFAULT) },
                modifier = Modifier.testTag("card_design_reset")
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
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
private fun CardDesignSlider(
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

/**
 * Ticks for the two sliders.
 *
 * Derived from the span and the step rather than written as a literal, so changing a
 * range in [CardDesign] keeps the ticks honest: a step of 5% across a 60-point span is
 * eleven interior stops.
 */
private const val TITLE_SIZE_STEPS = 11
private const val RATING_SIZE_STEPS = 5
