package com.anisequel.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.model.VoicedCharacter
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.ScoreBadge
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The two horizontal credit rows on a person's profile, and the cards they draw.
 *
 * Split out of [PersonProfileSheet] because the rows share a reason to change that
 * the sheet's chrome, hero, facts and biography do not: the card layout, the cover
 * sizes and the tap targets. The sheet was at the 700-line ceiling, and a change to
 * a poster card should not require reading the sheet's scroll accounting to find the
 * row worth touching.
 *
 * ## The credit rides on the appearance card
 *
 * A staff page's "Appears in" row used to be bare posters: it could say a director
 * worked on a title and never what they did on it, while the same card on a
 * character's page was already carrying the character's role. The label under each
 * poster is AniList's `staffRole` - "Director", "ADR Script (English)" - copied onto
 * the node by `ProfileAppearances.titles` and hidden when the edge answered none.
 *
 * ## "Voiced" is the creator's other half
 *
 * `staffMedia` says what a person worked on; `characterMedia` says who they
 * performed. The cards are *characters*, so a tap opens the character's page in the
 * sheet that is already open - and a host with no repository draws captions instead
 * of controls whose taps could only fail, the contract every openable face in the
 * app carries.
 *
 * ## The titles, as a row of covers
 *
 * A row rather than a vertical list: a director has hundreds of credits, a bounded
 * selection of them on this screen, and a reader recognises a poster rather than
 * reading a list of lines. The score rides on the artwork because it is the one number
 * a poster cannot carry.
 */
@Composable
fun ProfileAppearancesRow(
    appearances: List<ProfileAppearance>,
    listState: LazyListState,
    heading: String = "Appears in",
    onOpen: (ProfileAppearance) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading(title = heading, icon = AppVectorIcons.List)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            state = listState,
            contentPadding = PaddingValues(end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("profile_appearances")
        ) {
            items(items = appearances, key = { "${it.type}_${it.id}" }) { appearance ->
                AppearanceCard(appearance = appearance, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun AppearanceCard(appearance: ProfileAppearance, onOpen: (ProfileAppearance) -> Unit) {
    val title = appearance.title?.displayTitle ?: "Unknown"
    Column(
        modifier = Modifier
            .width(APPEARANCE_CARD_WIDTH)
            .bouncyPress(pressedScale = 0.97f)
            .clickable { onOpen(appearance) }
            .testTag("profile_appearance_${appearance.id}")
    ) {
        Box(
            modifier = Modifier
                .width(APPEARANCE_CARD_WIDTH)
                .height(APPEARANCE_POSTER_HEIGHT)
                // A cover, not a capsule. This was `ExpressiveShapes.pill`, which on a
                // 112x158 box is an ellipse: every poster on the row had its corners
                // rounded away and read as a placeholder, while the same titles on the
                // dashboard are `MaterialTheme.shapes.medium`. One cover shape for the
                // app's artwork.
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = appearance.coverImage?.bestUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .width(APPEARANCE_CARD_WIDTH)
                    .height(APPEARANCE_POSTER_HEIGHT)
            )
            appearance.averageScore?.let { score ->
                ScoreBadge(
                    score = score,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // The credit, from a staff page's edge. It sits between the title and the
        // format because it is what this page is about: `TV` says what the title is
        // on anybody's page, `Director` says why this person is on this one. One
        // line with an ellipsis, because "ADR Script (English)" is a real value and
        // 112dp is not wide enough for it.
        appearance.staffRole?.takeIf { it.isNotBlank() }?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(
            text = appearance.formatLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The characters this person voiced, as a row of covers.
 *
 * Only a staff page has one, and only when the connection answered - the caller
 * hides it otherwise. A row rather than a list for the same reason "Appears in" is
 * one: twelve characters is a row a reader scans by face, not a list they read. The
 * heading carries the same mic the character page's "Voiced by" uses, because it is
 * the same relationship read in the other direction - there, who sounds like this
 * character; here, which characters this person sounds like.
 */
@Composable
fun ProfileVoicedCharactersRow(
    characters: List<VoicedCharacter>,
    onOpen: ((VoicedCharacter) -> Unit)?
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading(title = "Voiced", icon = AppVectorIcons.Mic)
        Spacer(modifier = Modifier.height(8.dp))
        LazyRow(
            contentPadding = PaddingValues(end = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.testTag("profile_voiced_characters")
        ) {
            items(items = characters, key = { it.id }) { character ->
                VoicedCharacterCard(character = character, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun VoicedCharacterCard(
    character: VoicedCharacter,
    onOpen: ((VoicedCharacter) -> Unit)?
) {
    // Null is "this host cannot open a profile", not "a tap that does nothing": the
    // card reads it and draws a caption, the same distinction the sheet makes for
    // the cast and crew cards it sits beside.
    val tapTarget = if (onOpen != null) {
        Modifier
            .bouncyPress(pressedScale = 0.97f)
            .clickable(onClickLabel = "Open character") { onOpen(character) }
    } else {
        Modifier
    }

    Column(
        modifier = Modifier
            .width(APPEARANCE_CARD_WIDTH)
            .then(tapTarget)
            .testTag("profile_voiced_character_${character.id}")
    ) {
        Box(
            modifier = Modifier
                .width(APPEARANCE_CARD_WIDTH)
                .height(APPEARANCE_POSTER_HEIGHT)
                // The same cover shape as the appearance posters beside it, for the
                // same reason: a portrait is a rectangle, and a pill drawn around one
                // takes the corners off the art.
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = character.imageUrl,
                contentDescription = character.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Spacer(modifier = Modifier.height(5.dp))
        Text(
            text = character.name,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // `MAIN`, `SUPPORTING`, `BACKGROUND`, as AniList spells them - hidden when the
        // edge did not answer, rather than printed as a line of empty space.
        character.characterRole?.takeIf { it.isNotBlank() }?.let { role ->
            Text(
                text = role,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private val APPEARANCE_CARD_WIDTH = 112.dp
private val APPEARANCE_POSTER_HEIGHT = 158.dp
