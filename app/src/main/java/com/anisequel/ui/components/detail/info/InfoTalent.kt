package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaCharacterEdge
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MediaRelationEdge
import com.anisequel.data.model.MediaStaffEdge
import com.anisequel.data.model.characterList
import com.anisequel.data.model.displayName
import com.anisequel.data.model.metaLabel
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.model.relatedMedia
import com.anisequel.data.model.roleLabel
import com.anisequel.data.model.staffList
import com.anisequel.data.model.typeLabel
import androidx.compose.ui.platform.LocalContext
import java.util.Locale
import com.anisequel.ui.components.openExternalUrl
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * A heading for the sections that are not cards.
 *
 * The cast, the crew and the related titles are rows of artwork, and artwork inside
 * a card is artwork with a border drawn around the edge of the screen - which is
 * how the previous Specs tab framed every image it had. These three bleed to the
 * screen edge instead, so the row visibly continues past it and reads as scrollable.
 */
@Composable
fun InfoSectionHeading(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    // The inset arrives on the modifier rather than being applied here, because this
    // heading is used two ways: inside `InfoSection`, where the page has already
    // padded it, and standalone above a `LazyRow` that bleeds to the screen edge. It
    // used to hardcode 20.dp, which silently doubled the inset in the first case.
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * The cast, each name next to the actor who plays them.
 *
 * Character and actor side by side is the whole point of this row - a cast list
 * without the actor is a list of names the reader can get from the synopsis, and the
 * pairing is what AniList's own data is organised around. Only the Japanese cast is
 * asked for: it is the one AniList has for essentially every title, and a second
 * language would double the row for a fact most viewers are not reading it for.
 */
@Composable
fun InfoCharactersSection(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val cast = media.characterList
    if (cast.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoSectionHeading(
            title = "Characters & Voice Actors",
            icon = AppVectorIcons.FavouriteCharacters,
            modifier = Modifier.padding(horizontal = PAGE_INSET)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_INSET),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = cast, key = { edge -> "cast_${edge.node?.id ?: edge.hashCode()}" }) { edge ->
                CharacterCard(edge)
            }
        }
    }
}

@Composable
private fun CharacterCard(edge: MediaCharacterEdge) {
    val character = edge.node
    val actor = edge.voiceActors?.firstOrNull()
    val placeholder = MaterialTheme.colorScheme.surfaceContainerHighest
    val url = character?.siteUrl
    // Read here rather than inside the `clickable` lambda. That lambda is a plain
    // one, not a composable, so a `LocalContext.current` read in its body has no
    // composition to read from and the compiler rejects it - "invocations can only
    // happen from the context of a @Composable function". The value is hoisted,
    // which is the only place it can legally be read.
    val context = LocalContext.current

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier
            .width(268.dp)
            // Tappable for the same reason the studio credits are: a character the
            // reader has just read about is the next thing they want to look at, and
            // AniList carries the rest of it - the full description, the appearances,
            // who else is in the show. Null url draws the card as a plain caption,
            // because a control that leads nowhere is worse than no control.
            .then(
                if (url != null) {
                    Modifier
                        .bouncyPress(pressedScale = 0.98f)
                        .clickable { openExternalUrl(context, url) }
                } else {
                    Modifier
                }
            )
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = character?.image?.bestUrl,
                contentDescription = character?.name?.full,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(64.dp)
                    .clip(MaterialTheme.shapes.medium)
                    .background(placeholder)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = character?.name?.full ?: "Unknown character",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                // The aliases, when AniList has any. Spike Spiegel carries "Spike
                // Spike" and "Swimming Bird" there and the card printed one line, so
                // a reader who knows the character by a nickname had no way to match
                // the two. Deliberately the *second* name and never a list: a card
                // 268dp wide has room for one, and three would push the role and the
                // actor off the card.
                character?.name?.alternative
                    ?.firstOrNull { it.isNotBlank() && it != character.name?.full }
                    ?.let { alias ->
                        Text(
                            text = "aka $alias",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = edge.roleLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                // Age and gender, from the same pair of fields the character page
                // leads with. Free text on AniList's side - "23 (Physical), 77
                // (Actual)" is a real value - so both are printed as written and
                // joined only when both are present.
                val facts = listOfNotNull(
                    character?.age?.takeIf { it.isNotBlank() },
                    character?.gender?.takeIf { it.isNotBlank() }
                )
                if (facts.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = facts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (actor?.name?.full != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppVectorIcons.FavouriteStaff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = actor.name.full,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (actor?.image?.bestUrl != null) {
                    AsyncImage(
                        model = actor.image.bestUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(52.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(placeholder)
                    )
                }
                // How many AniList users have this character in their favourites.
                // The same number the studio page leads with, and the reason a
                // supporting character with four thousand favourites is visibly a
                // different proposition from one with nine.
                character?.favourites?.takeIf { it > 0 }?.let { favourites ->
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = AppVectorIcons.Star,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = compactCount(favourites),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                    }
                }
            }
        }
    }
}

/**
 * "14.4K", so a five-digit favourites count fits the corner beside a portrait.
 *
 * One decimal under 100K because 14370 reads as "14.4K" and 99999 as "100K", and
 * both still say what the number is; above that the decimals are noise on a count
 * nobody is comparing precisely.
 */
private fun compactCount(value: Int): String = when {
    value < 1_000 -> value.toString()
    value < 100_000 -> String.format(Locale.ROOT, "%.1fK", value / 1000.0)
    else -> String.format(Locale.ROOT, "%.1fM", value / 1_000_000.0)
}

/**
 * The crew: portraits with the position they held.
 *
 * AniList orders staff by relevance, which puts the original creator and the
 * director first - the two credits a reader is actually looking for - so the row is
 * drawn in the order it arrives rather than sorted here.
 */
@Composable
fun InfoStaffSection(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val crew = media.staffList
    if (crew.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoSectionHeading(
            title = "Staff",
            icon = AppVectorIcons.FavouriteStaff,
            modifier = Modifier.padding(horizontal = PAGE_INSET)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_INSET),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = crew, key = { edge -> "staff_${edge.node?.id ?: edge.hashCode()}" }) { edge ->
                StaffCard(edge)
            }
        }
    }
}

@Composable
private fun StaffCard(edge: MediaStaffEdge) {
    Column(modifier = Modifier.width(112.dp)) {
        Box(
            modifier = Modifier
                .width(112.dp)
                .height(154.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = edge.node?.image?.bestUrl,
                // The name is spoken by the `Text` immediately below this portrait, so
                // describing the image with the same string announces it twice in a row
                // as the reader swipes through the row.
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            val role = edge.role
            if (!role.isNullOrBlank()) {
                RolePill(
                    label = role,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = edge.node?.name?.full ?: "Unknown",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * The other titles in this franchise, labelled with how they relate.
 *
 * AniList's relation list is mostly *not* other seasons - it is the guidebook, the
 * character song, the light novel the show was adapted from - so the list is filtered
 * to this title's **own medium** before it gets here. See
 * [com.anisequel.data.model.relatedMedia] for why that is the subject's own type and
 * not `ANIME`: filtering to anime unconditionally meant a manga page listed its TV
 * adaptations and none of its own volumes.
 *
 * The cards are clickable, and tapping one *replaces* this page's subject rather
 * than opening a second sheet. That was the whole of the "universal info page"
 * question: a sheet stacked on a sheet has no back affordance the reader can see,
 * so the relation row was left inert and a franchise could only be walked one
 * title at a time by dismissing and starting again from the list.
 *
 * Replacing the subject in place gets both - a chain of related titles is
 * walkable, and there is still exactly one sheet with exactly one close control.
 * The cost is that a relation is one-way: the reader goes deeper, and back means
 * dismissing and finding the entry again. That is the trade the reference app
 * makes too, and it is a better one than a row of artwork that does nothing.
 *
 * The node handed to [onOpen] is the *relation's* own node, which carries `type` -
 * that is what lets the next open be scoped to the right AniList namespace
 * instead of resolving the id against both.
 */
@Composable
fun InfoRelationsSection(
    media: MediaNode,
    onOpen: (MediaNode) -> Unit,
    modifier: Modifier = Modifier
) {
    val related = media.relatedMedia
    if (related.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoSectionHeading(
            title = "Seasons & Relations",
            icon = AppVectorIcons.FranchiseBranch,
            modifier = Modifier.padding(horizontal = PAGE_INSET)
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = PAGE_INSET),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = related, key = { edge -> "relation_${edge.node.id}" }) { edge ->
                RelationCard(edge = edge, onOpen = onOpen)
            }
        }
    }
}

@Composable
private fun RelationCard(
    edge: MediaRelationEdge,
    onOpen: (MediaNode) -> Unit
) {
    val node = edge.node

    Column(modifier = Modifier.width(RELATION_CARD_WIDTH)) {
        Box(
            modifier = Modifier
                .width(RELATION_CARD_WIDTH)
                .height(RELATION_POSTER_HEIGHT)
                .clip(MaterialTheme.shapes.medium)
                // Tinted with the poster's own dominant colour until the art loads,
                // the same way the hero does: a grey rectangle tells the reader
                // nothing about which relation they are waiting on.
                .background(
                    node.posterColor.toCoverColorOrNull()
                        ?: MaterialTheme.colorScheme.surfaceContainerHighest
                )
                .clickable(onClick = { onOpen(node) })
                .bouncyPress(pressedScale = 0.97f)
                .testTag("info_relation_${node.id}")
        ) {
            AsyncImage(
                model = node.posterUrl,
                contentDescription = node.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            RolePill(
                label = edge.typeLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
            // Drawn over the artwork rather than in the text stack below: the
            // score is the fact that decides whether a reader taps a *related*
            // title they have never heard of, and it belongs where the eye already
            // is. It is also the one number AniList gives that the poster cannot.
            node.averageScore?.let { score ->
                ScoreBadge(
                    score = score,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = node.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        // `metaLabel` is the whole point of the widened relation query - see
        // [com.anisequel.data.model.metaLabel]. Before it, this row was the poster
        // and the name and nothing else.
        Text(
            text = edge.metaLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.testTag("info_relation_meta_${node.id}")
        )
    }
}

/**
 * A related title's community score, over its own artwork.
 *
 * Deliberately *not* the accent colour the relation pill uses: the pill labels the
 * row and the badge is a number off AniList, and tinting both with the same colour
 * would make the score read as another label rather than as a value.
 */
@Composable
private fun ScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ExpressiveShapes.pill,
        color = Color.Black.copy(alpha = 0.62f),
        modifier = modifier
    ) {
        Text(
            text = "$score%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}

/**
 * The relation card's width and poster height.
 *
 * Wider than the 132dp it used to be, because the card grew the metadata line under
 * the name and a line that always ellipsizes is not data. The ratio is kept from the
 * old card.
 */
private val RELATION_CARD_WIDTH = 144.dp

private val RELATION_POSTER_HEIGHT = 202.dp

/** A capsule label laid over artwork - a cast role, a crew position, a relation. */
@Composable
private fun RolePill(
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.92f),
        modifier = modifier
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 3.dp)
                .widthIn(max = 118.dp)
        )
    }
}
