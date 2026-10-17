package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.FavouriteCharacter
import com.anisequel.data.model.FavouriteStaff
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.StudioNode
import com.anisequel.data.model.formatLabel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveEmptyOrb
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.theme.AniSequelTheme

/**
 * Content width shared by every profile screen, matching the dashboard's.
 *
 * A favourites row is a horizontal `LazyRow`, so it does not have the
 * unbounded-width problem a vertical list does - but its *cards* do, and a row of
 * 200dp covers on a 1,200dp tablet is four covers with a gap of nothing.
 */
val ProfileMaxContentWidth = 640.dp

/**
 * A titled block with an icon tile, matching the Settings screen's section header.
 *
 * Shared rather than redeclared because the profile screen has seven of these
 * across four tabs and they were going to drift - a favourites header is a heading
 * for a page section and should look like the one above it in Settings.
 */
@Composable
fun ProfileSectionHeader(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    trailing: String? = null
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * A horizontal row of favourite media covers.
 *
 * The card is 128dp wide with the title under it rather than overlaid on it.
 * AniList covers carry the series' own typography, frequently vertical and
 * running to the edges; a two-line caption over that is unreadable, and an
 * overlay gradient over twelve covers at once is a stripe across the screen.
 */
@Composable
fun FavouriteMediaRow(
    items: List<MediaNode>,
    onOpen: (MediaNode) -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth
) {
    if (items.isEmpty()) {
        EmptyFavourites("Nothing pinned here yet")
        return
    }

    LazyRow(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .testTag("favourite_media_row"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        items(
            items = items,
            key = { it.id },
            contentType = { "media" }
        ) { media ->
            FavouriteMediaCard(media = media, onClick = { onOpen(media) })
        }
    }
}

@Composable
private fun FavouriteMediaCard(
    media: MediaNode,
    onClick: () -> Unit
) {
    // Shared with [bouncyPress] so the press cancels when a drag of the
    // `LazyRow` starts, instead of the card staying squashed under a finger that
    // is actually scrolling.
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(128.dp)
            .bouncyPress(pressedScale = 0.97f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(bottom = 6.dp)
            .testTag("favourite_media_card")
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp),
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            val placeholderIcon = if (media.type.equals("MANGA", ignoreCase = true)) {
                AppVectorIcons.FavouriteManga
            } else {
                AppVectorIcons.FavouriteAnime
            }
            AsyncImage(
                model = media.coverImage?.bestUrl ?: media.coverImage?.large,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(placeholderIcon),
                error = rememberVectorPainter(placeholderIcon),
                fallback = rememberVectorPainter(placeholderIcon)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = media.title?.displayTitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            minLines = 2,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = favouriteCaption(media),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * "TV • 84%", or the format alone when AniList has no score for it.
 *
 * The format word comes from [formatLabel], not from a local `when`: this function
 * used to carry a copy of the mapping, and a copy is exactly what the KDoc on
 * `ProfileAppearance.formatLabel` warns about - it printed "Music_video" where the
 * person sheet printed "Music Video", and disagreed with the shared mapper on every
 * format added after it was written.
 */
private fun favouriteCaption(media: MediaNode): String {
    val score = media.averageScore?.takeIf { it > 0 }?.let { "$it%" }
    return listOfNotNull(media.formatLabel, score).joinToString(" • ")
}

/**
 * A horizontal row of the people an account has pinned: characters and staff both.
 *
 * One function for both because they are the same shape - a portrait, a name, and
 * a second line - and two near-identical row implementations is how the staff row
 * ends up with a 68dp portrait and the character row with a 56dp one. The
 * difference is carried by [PersonRowItem] below rather than by two call sites, so
 * the caption each kind gets is defined once.
 *
 * The two lists are rendered into **one** row, characters first. AniList returns
 * them as separate connections and the app keeps them as separate lists, which is
 * right for the model; what the reader sees is one list of people, and an actor
 * and an animator are both people the account chose.
 *
 * `key` is namespaced by kind because AniList numbers characters and staff
 * independently - `character_21` and `staff_21` are different people, and a shared
 * `id` in the key made one of the two rows silently drop its portrait.
 */
@Composable
fun FavouritePeopleRow(
    characters: List<FavouriteCharacter>,
    staff: List<FavouriteStaff>,
    onOpen: (String?) -> Unit,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth
) {
    if (characters.isEmpty() && staff.isEmpty()) {
        EmptyFavourites("No favourites pinned")
        return
    }

    val people = buildList {
        characters.forEach { character ->
            add(
                PersonRowItem(
                    key = "character_${character.id}",
                    name = character.name?.displayName.orEmpty(),
                    // "N favourites" rather than a bare number: the number is
                    // AniList's count of *other* accounts that pinned this
                    // character, which is only meaningful with the noun attached.
                    subtitle = "${character.favourites ?: 0} favourites",
                    imageUrl = character.image?.bestUrl,
                    placeholder = AppVectorIcons.FavouriteCharacters,
                    onClick = { onOpen(character.siteUrl) }
                )
            )
        }
        staff.forEach { member ->
            add(
                PersonRowItem(
                    key = "staff_${member.id}",
                    name = member.name?.displayName.orEmpty(),
                    // AniList's `primaryOccupations` is the person's day job -
                    // "Story & Art", "Director", "Voice Actor" - which is the one
                    // line that tells a reader who this is. Fall back to the count,
                    // which every row now shows either way, rather than to the
                    // word "Staff", which told them nothing they could not see.
                    subtitle = member.occupation
                        ?: "${member.favourites ?: 0} favourites",
                    imageUrl = member.image?.bestUrl,
                    placeholder = AppVectorIcons.FavouriteStaff,
                    onClick = { onOpen(member.siteUrl) }
                )
            )
        }
    }

    LazyRow(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .testTag("favourite_people_row"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp)
    ) {
        items(items = people, key = { it.key }) { person ->
            FavouritePerson(
                name = person.name,
                subtitle = person.subtitle,
                imageUrl = person.imageUrl,
                placeholder = person.placeholder,
                onClick = person.onClick
            )
        }
    }
}

/**
 * One person in [FavouritePeopleRow], flattened out of the two model types.
 *
 * [key] is carried rather than derived from the id at the call site, because the
 * two namespaces number independently and only the caller knows which kind it is
 * holding.
 */
private data class PersonRowItem(
    val key: String,
    val name: String,
    val subtitle: String,
    val imageUrl: String?,
    val placeholder: ImageVector,
    val onClick: () -> Unit
)

/**
 * [testTag] is keyed on the name rather than a literal, because a row of twenty
 * people all tagged `favourite_person` is one tag the tests cannot address: the
 * merged semantics of a `LazyRow` collapse them and `onNodeWithTag` returns only
 * the first. Keyed by name, one row element can be asserted on at all.
 */
@Composable
private fun FavouritePerson(
    name: String,
    subtitle: String,
    imageUrl: String?,
    placeholder: ImageVector,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    Column(
        modifier = Modifier
            .width(84.dp)
            .bouncyPress(pressedScale = 0.96f, interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource, onClick = onClick)
            .padding(bottom = 6.dp)
            .testTag("favourite_person_${name.hashCode()}"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AsyncImage(
            model = imageUrl,
            contentDescription = null,
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentScale = ContentScale.Crop,
            placeholder = rememberVectorPainter(placeholder),
            error = rememberVectorPainter(placeholder),
            fallback = rememberVectorPainter(placeholder)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Favourite studios as capsules that open the studio's page.
 *
 * Studios have no cover and no portrait, so a row of them would be a row of
 * identical circles with different initials - which reads as a loading failure
 * rather than as a studio name. Capsules carry the name properly, and with four
 * to six of them they wrap rather than scroll off the end of the page.
 *
 * ## Why each one is a button now
 *
 * These were plain `Text` in a tinted capsule - a chip that looks exactly like a
 * control and does nothing. Every other favourites row on this page opens
 * something: anime and manga open the detail sheet, characters and staff hand off to
 * the browser, and the studios were the only row that went nowhere. A studio a
 * person chose to pin is the strongest statement on the profile about the kind of
 * thing they watch, and the studio page - a whole filmography, shelved by year - is
 * already the destination the Info page's own credits use.
 *
 * The row takes `(Int, String?)` rather than a [StudioNode] so this file does not
 * have to decide what a studio *is*: the sheet's credits hand the same pair to the
 * same route, and one definition of "open this studio" stays in one place.
 *
 * A credit with no id is drawn as text rather than as a button that cannot work, the
 * same rule the title page follows. AniList's favourites always carry one; a fixture
 * need not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavouriteStudioRow(
    studios: List<StudioNode>,
    /**
     * Opens the studio's page, by AniList id.
     *
     * The name rides along so the destination's bar has a title on its first frame
     * instead of an empty space while the fetch lands.
     */
    onOpenStudio: (Int, String?) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth
) {
    if (studios.isEmpty()) {
        EmptyFavourites("No studios pinned")
        return
    }

    FlowRow(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        studios.forEach { studio ->
            val label = studio.name ?: return@forEach
            val id = studio.id
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(ExpressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .then(
                        if (id != null) {
                            Modifier
                                .bouncyPress(pressedScale = 0.94f)
                                .clickable { onOpenStudio(id, label) }
                        } else {
                            Modifier
                        }
                    )
                    .padding(start = 12.dp, end = 16.dp, top = 9.dp, bottom = 9.dp)
                    .testTag("favourite_studio_chip")
            ) {
                // Animates or publishes. A name on its own does not say which, and the
                // studio page leads with the distinction - so the chip carries it
                // rather than making the reader open the studio to find out.
                if (studio.isAnimationStudio == true) {
                    Icon(
                        imageVector = AppVectorIcons.Studio,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.size(13.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The "nothing here yet" line every favourites row falls back to.
 */
@Composable
fun EmptyFavourites(message: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .testTag("empty_favourites"),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ExpressiveEmptyOrb(
            icon = AppVectorIcons.Star,
            containerColor = AniSequelTheme.statusColors.infoContainer,
            iconTint = AniSequelTheme.statusColors.info
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

