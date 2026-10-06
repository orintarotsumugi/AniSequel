package com.example.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import com.example.data.model.FavouriteCharacter
import com.example.data.model.FavouriteStaff
import com.example.data.model.MediaNode
import com.example.data.model.StudioNode
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveEmptyOrb
import com.example.ui.components.expressive.ExpressiveShapes
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.theme.AniSequelTheme

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
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items = items, key = { it.id }) { media ->
            FavouriteMediaCard(media = media, onClick = { onOpen(media) })
        }
    }
}

@Composable
private fun FavouriteMediaCard(
    media: MediaNode,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(128.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .bouncyPress(pressedScale = 0.97f)
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
            AsyncImage(
                model = media.coverImage?.large,
                contentDescription = null,
                modifier = Modifier.fillMaxWidth(),
                contentScale = ContentScale.Crop,
                placeholder = rememberVectorPainter(AppVectorIcons.FavouriteAnime),
                error = rememberVectorPainter(AppVectorIcons.FavouriteAnime),
                fallback = rememberVectorPainter(AppVectorIcons.FavouriteAnime)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = media.title?.displayTitle.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = favouriteCaption(media),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/** "TV • 84%", or the format alone when AniList has no score for it. */
private fun favouriteCaption(media: MediaNode): String {
    val format = media.format?.lowercase()?.replaceFirstChar { it.uppercase() }.orEmpty()
    val score = media.averageScore?.takeIf { it > 0 }?.let { "$it%" }
    return listOfNotNull(format.takeIf { it.isNotBlank() }, score).joinToString(" • ")
}

/**
 * A horizontal row of favourite characters or staff.
 *
 * One function for both because they are the same shape - a portrait, a name, and
 * a number of other people who agree - and the two calls differ only in the
 * portrait URL, the name and the secondary line. Two near-identical row
 * implementations is how the staff row ends up with a 68dp portrait and the
 * character row with a 56dp one.
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

    LazyRow(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .testTag("favourite_people_row"),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(items = characters, key = { "character_${it.id}" }) { character ->
            FavouritePerson(
                name = character.name?.displayName.orEmpty(),
                subtitle = "${character.favourites ?: 0} favourites",
                imageUrl = character.image?.bestUrl,
                placeholder = AppVectorIcons.FavouriteCharacters,
                onClick = { onOpen(character.siteUrl) }
            )
        }
        items(items = staff, key = { "staff_${it.id}" }) { member ->
            FavouritePerson(
                name = member.name?.displayName.orEmpty(),
                subtitle = member.occupation ?: "Staff",
                imageUrl = member.image?.bestUrl,
                placeholder = AppVectorIcons.FavouriteStaff,
                onClick = { onOpen(member.siteUrl) }
            )
        }
    }
}

@Composable
private fun FavouritePerson(
    name: String,
    subtitle: String,
    imageUrl: String?,
    placeholder: ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(84.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .bouncyPress(pressedScale = 0.96f)
            .testTag("favourite_person"),
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
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Favourite studios as capsules.
 *
 * Studios have no cover and no portrait, so a row of them would be a row of
 * identical circles with different initials - which reads as a loading failure
 * rather than as a studio name. Capsules carry the name properly, and with four
 * to six of them they wrap rather than scroll off the end of the page.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FavouriteStudioRow(
    studios: List<StudioNode>,
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
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier
                    .clip(ExpressiveShapes.pill)
                    .background(MaterialTheme.colorScheme.secondaryContainer)
                    .padding(horizontal = 16.dp, vertical = 9.dp)
                    .testTag("favourite_studio_chip")
            )
        }
    }
}

/**
 * The person's own bio, rendered as plain text.
 *
 * AniList bios are Markdown and there is no Markdown renderer in the app, so
 * [stripMarkdown] reduces them to something readable rather than showing a wall
 * of `###` and `[text](url)`. The alternative - shipping a Markdown library for
 * one card - is not a trade this screen is worth, and rendering the raw source
 * would make a person look like they had written a help file.
 */
@Composable
fun AboutCard(
    about: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth
) {
    val text = remember(about) { stripMarkdown(about) }

    Card(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("profile_about_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.ProfileAbout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
internal fun NoBioNote() {
    Text(
        text = "No bio on AniList",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("profile_no_bio")
    )
}

/**
 * The "nothing here yet" line every favourites row falls back to.
 *
 * One shared line rather than five, and phrased as a statement about the list
 * rather than about the app: a person with no favourite studios has not failed to
 * load their profile, and "No studios pinned" says that. The orb keeps it in the
 * same visual language as the dashboard's empty state.
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

/**
 * AniList's mean score out of 100, as the out-of-ten number people quote.
 *
 * One decimal, because a mean is a mean: 82.3 and 82 describe different lists.
 *
 * `Locale.ROOT` on the format rather than the platform default, so a device set
 * to a locale that writes decimals with a comma does not render "8,2/10" - which
 * is not a score, and reads as a formatting bug on the one card quoting a number
 * somebody chose.
 */
internal fun formatMeanScore(meanScore: Double): String {
    val outOfTen = meanScore / 10.0
    return if (outOfTen % 1.0 == 0.0) {
        "${outOfTen.toInt()}/10"
    } else {
        String.format(java.util.Locale.ROOT, "%.1f/10", outOfTen)
    }
}

/**
 * Reduce Markdown to plain text.
 *
 * Deliberately a substitution pass, not a parser: it un-does the four things that
 * actually appear in an AniList bio - headings, emphasis, links and bullets - and
 * leaves everything else exactly as the person wrote it.
 *
 * ## Why the line-start patterns match spaces and tabs, not `\s`
 *
 * `\s` includes a newline, so a heading pattern written as `#{1,6}\s*` at the head
 * of a line consumes the blank line *before* it - up to three characters' worth,
 * which is exactly the run of newlines the later `\n{3,}` pass was supposed to
 * collapse. The two passes fought each other and the result depended on how many
 * blank lines the person happened to leave: `### One`, blank, blank, `text`
 * rendered as "One" and "text" in the same paragraph. `[ \t]*` matches only what
 * is actually on the heading's own line, so the blank-line pass is the only thing
 * deciding how paragraphs separate.
 */
internal fun stripMarkdown(source: String): String = source
    .replace(Regex("""\[([^\]]+)]\([^)]*\)"""), "$1")
    .replace(Regex("""!\[[^\]]*]\([^)]*\)"""), "")
    .replace(Regex("""^[ \t]{0,3}#{1,6}[ \t]*""", RegexOption.MULTILINE), "")
    .replace(Regex("""^[ \t]{0,3}>[ \t]?"""), "")
    .replace(Regex("""(\*\*|__)(.+?)\1"""), "$2")
    .replace(Regex("""(\*|_)(.+?)\1"""), "$2")
    .replace(Regex("""^[ \t]{0,3}[-*+][ \t]+""", RegexOption.MULTILINE), "")
    .replace(Regex("""`([^`]+)`"""), "$1")
    .replace(Regex("""\n{3,}"""), "\n\n")
    .trim()
