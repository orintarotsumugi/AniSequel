package com.anisequel.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.PosterSource
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.navigation.MainTab
import kotlin.math.roundToInt

/**
 * The two card-design parts that are about what a card *says* and what it *shows*.
 *
 * Split out of `InterfaceSections.kt` when the card page became a shelf of its own: the
 * shape half and the shelf still live there, because the style chips and the page's
 * rows are one story, and these two are each a card and a preview with no decision
 * outside their own controls. Nothing was rewritten in the move - the sliders, the
 * switches, their tags and the preferences they write are the ones that were there.
 */

/** The words a card prints: one slider each for the two numbers, and four switches. */
@Composable
internal fun CardTextPart(
    design: CardDesign,
    entries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    style: MediaCardStyle,
    titleDraft: Float,
    ratingDraft: Float,
    onTitleDraft: (Float) -> Unit,
    onRatingDraft: (Float) -> Unit,
    showMyRatings: Boolean,
    showTheirRatings: Boolean,
    onShowMyRatings: (Boolean) -> Unit,
    onShowTheirRatings: (Boolean) -> Unit,
    onCommit: (CardDesign) -> Unit
) {
    SectionCard(
        title = "Name and score",
        icon = AppVectorIcons.TextBold,
        subtitle = "How the two numbers on a card are drawn"
    ) {
        CardDesignPreview(
            style = style,
            design = design,
            entries = entries,
            barTabs = barTabs
        )

        Spacer(modifier = Modifier.height(14.dp))
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
            onValueChange = onTitleDraft,
            onCommit = { onCommit(design.copy(titleScale = titleDraft)) }
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
                "caption line depending on the style - the pair of switches " +
                "below chooses which score it is. Scaled apart from the name, " +
                "because a score is scanned and a name is read.",
            onValueChange = onRatingDraft,
            onCommit = { onCommit(design.copy(ratingScale = ratingDraft)) }
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
            checked = showMyRatings,
            onCheckedChange = onShowMyRatings,
            modifier = Modifier.testTag("show_my_ratings_switch")
        )
        SwitchRow(
            title = "Their rating, on their profile",
            subtitle = "Print the score the profile's owner gave, when you open " +
                "someone else's library. AniList only returns those scores for a " +
                "public list, and an unscored entry keeps the community average.",
            checked = showTheirRatings,
            onCheckedChange = onShowTheirRatings,
            modifier = Modifier.testTag("show_their_ratings_switch")
        )

        Spacer(modifier = Modifier.height(12.dp))
        SwitchRow(
            title = "Bold names",
            subtitle = "Draw every card's name a weight heavier. The shipped card is " +
                "semibold; this is the step above it.",
            checked = design.boldTitles,
            onCheckedChange = { bold -> onCommit(design.copy(boldTitles = bold)) },
            modifier = Modifier.testTag("card_bold_titles_switch")
        )
        SwitchRow(
            title = "Scroll long names",
            subtitle = "A name too long for its line scrolls once it has rested, " +
                "instead of being cut. The speed itself is on the Type page, under " +
                "Long titles; an Instant motion style still stops every marquee.",
            checked = design.scrollTitles,
            onCheckedChange = { scroll -> onCommit(design.copy(scrollTitles = scroll)) },
            modifier = Modifier.testTag("card_scroll_titles_switch")
        )
    }
}

/**
 * The artwork, and the two switches that undraw what it already says.
 *
 * btttr's poster is a finished poster: the name and the score are set on the art, in
 * the show's own type, which is what makes it look like a streaming service rather than
 * a database row. A card that then prints its own caption under that picture says the
 * same title twice in two typefaces - so the reader who chose that art needs to be able
 * to stop the card from saying it again. Both switches live on the card design rather
 * than on the poster source, because the AniList cover carries no writing and a reader
 * who stays on it still wants a name.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CardPosterPart(
    design: CardDesign,
    entries: List<MediaListEntryItem>,
    barTabs: List<MainTab>,
    style: MediaCardStyle,
    posterSource: PosterSource,
    onPosterSource: (PosterSource) -> Unit,
    onCommit: (CardDesign) -> Unit
) {
    SectionCard(
        title = "Poster art",
        icon = AppVectorIcons.SectionAppearance,
        subtitle = "Where the picture comes from, and what the card adds to it"
    ) {
        CardDesignPreview(
            style = style,
            design = design,
            entries = entries,
            barTabs = barTabs
        )

        Spacer(modifier = Modifier.height(14.dp))
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
                    selected = option == posterSource,
                    onClick = { onPosterSource(option) },
                    minTouchTargetHeight = 48.dp,
                    role = Role.RadioButton,
                    onClickLabel = "Draw card posters from ${option.displayName}",
                    modifier = Modifier.testTag("poster_source_${option.storageValue}")
                )
            }
        }
        CollapsibleText(
            text = posterSource.blurb,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(4.dp))
        SwitchRow(
            title = "Card names",
            subtitle = "Draw the title under the artwork. Turn it off when the poster " +
                "already carries the name - BetterPosters sets it on the art - so the " +
                "card shows one title rather than two.",
            checked = design.showTitles,
            onCheckedChange = { show -> onCommit(design.copy(showTitles = show)) },
            modifier = Modifier.testTag("card_show_titles_switch")
        )
        SwitchRow(
            title = "Card scores",
            subtitle = "Draw the score on the card. BetterPosters prints its own on " +
                "the art; turning this off keeps that one and drops the second badge.",
            checked = design.showScores,
            onCheckedChange = { show -> onCommit(design.copy(showScores = show)) },
            modifier = Modifier.testTag("card_show_scores_switch")
        )
    }
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
