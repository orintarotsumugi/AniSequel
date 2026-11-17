package com.anisequel.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MediaListEntryItem
import com.anisequel.domain.card.CardDesign
import com.anisequel.domain.card.MediaCardStyle
import com.anisequel.domain.ui.PosterOverlays
import com.anisequel.domain.ui.PosterSource
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveShapes
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
        title = "Type and rating",
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
 * A sketch of what btttr will print, drawn from the three switches.
 *
 * ## Why not the real poster
 *
 * The page cannot show the reader's own grid: the switches are global and the art
 * depends on a title and an IMDb lookup, so any single real poster would be a
 * promise about one card. The sketch answers the only question the switches ask -
 * what is on the picture - without a network call, a placeholder flash or a lie
 * about which title it belongs to. It uses the theme's own colours because it is a
 * settings control, not a second design system.
 */
@Composable
private fun PosterOverlayPreview(overlays: PosterOverlays) {
    val onArt = Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(124.dp)
                .aspectRatio(2f / 3f)
                .clip(MaterialTheme.shapes.medium)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.tertiary,
                            MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                )
                .testTag("poster_overlay_preview")
        ) {
            // Two soft discs stand in for key art, so the tile reads as a poster
            // rather than a colour chip and the overlays have something to sit on.
            Box(
                modifier = Modifier
                    .offset(x = 16.dp, y = 44.dp)
                    .size(62.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
            )
            Box(
                modifier = Modifier
                    .offset(x = 64.dp, y = 84.dp)
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
            )

            if (overlays.showTrendTags) {
                Text(
                    text = "New Series",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = onArt,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .clip(ExpressiveShapes.pill)
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }

            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "SAMPLE TITLE",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = onArt,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (overlays.showGenre) {
                    Text(
                        text = "Anime",
                        style = MaterialTheme.typography.labelSmall,
                        color = onArt.copy(alpha = 0.82f),
                        maxLines = 1
                    )
                }
                if (overlays.showRating) {
                    Text(
                        text = "★ 9.3",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = onArt,
                        maxLines = 1
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Live preview",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * The artwork, what btttr may print on it, and the two switches that undraw what the
 * card adds to it.
 *
 * btttr's poster is a finished poster: the name is set into the art in the show's own
 * type, and - depending on the variant asked for - a score, a genre line and a trend
 * label. A card that then prints its own caption under that picture says the same
 * title twice in two typefaces, so the reader who chose that art needs to be able to
 * stop the app from saying it again.
 *
 * There are two ways to remove a duplicate, and they are not the same switch: the
 * **poster** switches ask btttr for a different picture, because those words are
 * pixels in the artwork and the app cannot hide them; the **card** switches stop the
 * app from drawing its own caption underneath. A reader on AniList's cover has no
 * poster overlays at all, which is why the first group only appears for BetterPosters
 * and the second group is always here.
 *
 * The overlay switches start off (see `PosterOverlays.DEFAULT`), and the unique-season
 * switch is the one control here that is not an overlay: btttr has one series poster,
 * so it is the app's way of not repeating season one for every sequel.
 *
 * That switch is about **cards and lists** only. The full-screen viewer still offers
 * btttr's render to a sequel and labels it "series art", because a grid has no room
 * to explain that one picture covers three entries and the viewer does - hiding it
 * there as well removed the only place the series art could be seen whole.
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
    overlays: PosterOverlays,
    onOverlays: (PosterOverlays) -> Unit,
    uniqueSeasons: Boolean,
    onUniqueSeasons: (Boolean) -> Unit,
    onCommit: (CardDesign) -> Unit
) {
    SectionCard(
        title = "Source and caption",
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
            text = posterSource.blurb + " The card prints its own name and score " +
                "under the art by default, so nothing disappears when you switch " +
                "sources; turn either off below when the picture already carries it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Only for btttr: AniList's cover has nothing composited on it, so these
        // switches would be three controls over a picture that cannot change.
        if (posterSource == PosterSource.BETTER_POSTERS) {
            Spacer(modifier = Modifier.height(16.dp))

            // The switches below change a picture the reader cannot see from here,
            // so the picture is drawn first: a live sketch of the btttr frame that
            // grows and loses its score, genre and trend strip as they are toggled.
            // It is deliberately drawn rather than downloaded - a settings page must
            // not wait on a third party to explain a switch.
            Row(verticalAlignment = Alignment.CenterVertically) {
                PosterOverlayPreview(overlays = overlays)
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    SettingsFieldLabel(text = "Preview")
                    Spacer(modifier = Modifier.height(4.dp))
                    CollapsibleText(
                        text = "A sketch of the poster, not a download: the score, " +
                            "the genre line and the trend strip appear and disappear " +
                            "as you toggle them. The artwork and the show's name are " +
                            "always there - btttr prints the name in every variant.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            // The one switch that is not an overlay: btttr has one poster per IMDb
            // series, so seasons 2 and up repeat season one's picture. On, a sequel
            // keeps its own AniList cover - the art that carries its season name.
            // Cards and lists only: the viewer names the series render and shows it.
            SwitchRow(
                title = "Unique art for sequels",
                subtitle = "btttr draws one series poster, so Season 2 would repeat " +
                    "Season 1's art under a different name. On, a sequel season keeps " +
                    "its own AniList cover on cards and in lists, which is specific to " +
                    "that season. The full-screen viewer still offers the series art, " +
                    "labelled as such.",
                checked = uniqueSeasons,
                onCheckedChange = onUniqueSeasons,
                modifier = Modifier.testTag("poster_unique_seasons_switch")
            )

            Spacer(modifier = Modifier.height(16.dp))
            SettingsFieldLabel(text = "On the poster itself")
            CollapsibleText(
                text = "All three are off by default, so a new install draws the " +
                    "clean poster: artwork and its name, nothing else. btttr draws " +
                    "these into the artwork, so the app cannot hide them the way it " +
                    "hides its own caption - turning one on asks btttr for a " +
                    "different poster. A title btttr does not carry, or an offline " +
                    "lookup, keeps its AniList cover and ignores these switches.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SwitchRow(
                title = "Score on the poster",
                subtitle = "btttr's own star score, burned into the art. Off by " +
                    "default; the app's caption can show the score instead.",
                checked = overlays.showRating,
                onCheckedChange = { show ->
                    onOverlays(overlays.copy(showRating = show))
                },
                modifier = Modifier.testTag("poster_show_rating_switch")
            )
            SwitchRow(
                title = "Genre on the poster",
                subtitle = "The small genre line btttr prints along the bottom of the " +
                    "artwork. It is part of the picture, not a badge this app draws.",
                checked = overlays.showGenre,
                onCheckedChange = { show ->
                    onOverlays(overlays.copy(showGenre = show))
                },
                modifier = Modifier.testTag("poster_show_genre_switch")
            )
            SwitchRow(
                title = "Trend label",
                subtitle = "The \"New Series\" and \"Limited Series\" labels above the " +
                    "poster. Off by default, leaving the artwork and its name without " +
                    "the strip of text across the top.",
                checked = overlays.showTrendTags,
                onCheckedChange = { show ->
                    onOverlays(overlays.copy(showTrendTags = show))
                },
                modifier = Modifier.testTag("poster_trend_tags_switch")
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
        SettingsFieldLabel(text = "Under the artwork")
        SwitchRow(
            title = "Show card name",
            subtitle = "Draw the title under the artwork. On by default, so every " +
                "list keeps the caption it always had. Turn it off when the poster " +
                "already includes the name, such as BetterPosters art.",
            checked = design.showTitles,
            onCheckedChange = { show -> onCommit(design.copy(showTitles = show)) },
            modifier = Modifier.testTag("card_show_titles_switch")
        )
        SwitchRow(
            title = "Show card score",
            subtitle = "Draw the score the card knows - your rating or the community " +
                "average, chosen on the Name and score screen. On by default; turn " +
                "it off when the poster already shows a score and a second number " +
                "would be one too many.",
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
