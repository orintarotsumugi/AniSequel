package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import com.anisequel.data.model.relatedAnime
import com.anisequel.data.model.roleLabel
import com.anisequel.data.model.staffList
import com.anisequel.data.model.typeLabel
import com.anisequel.ui.components.AppVectorIcons
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
    Row(
        modifier = modifier.padding(horizontal = 20.dp),
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
            icon = AppVectorIcons.FavouriteCharacters
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
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

    Surface(
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.width(268.dp)
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
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = edge.roleLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
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

            if (actor?.image?.bestUrl != null) {
                Spacer(modifier = Modifier.width(8.dp))
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
        }
    }
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
        InfoSectionHeading(title = "Staff", icon = AppVectorIcons.FavouriteStaff)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
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
                .bouncyPress(pressedScale = 0.97f)
        ) {
            AsyncImage(
                model = edge.node?.image?.bestUrl,
                contentDescription = edge.node?.name?.full,
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
 * AniList's relation list is mostly *not* other seasons - it is the light novel the
 * show was adapted from, the guidebook, the character song - so the list is filtered
 * to anime before it gets here. See [com.anisequel.data.model.relatedAnime].
 *
 * The cards are deliberately not clickable: this page is already a sheet, and a
 * sheet opened from inside a sheet has no way back that the reader can see. The
 * relation is stated, not offered.
 */
@Composable
fun InfoRelationsSection(
    media: MediaNode,
    modifier: Modifier = Modifier
) {
    val related = media.relatedAnime
    if (related.isEmpty()) return

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        InfoSectionHeading(title = "Seasons & Relations", icon = AppVectorIcons.FranchiseBranch)
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(items = related, key = { edge -> "relation_${edge.node.id}" }) { edge ->
                RelationCard(edge)
            }
        }
    }
}

@Composable
private fun RelationCard(edge: MediaRelationEdge) {
    Column(modifier = Modifier.width(132.dp)) {
        Box(
            modifier = Modifier
                .width(132.dp)
                .height(186.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = edge.node.coverImage?.bestUrl,
                contentDescription = edge.node.displayName,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
            RolePill(
                label = edge.typeLabel,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = edge.node.displayName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
