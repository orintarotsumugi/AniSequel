package com.anisequel.ui.components.detail

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.PersonKind
import com.anisequel.data.model.PersonProfileState
import com.anisequel.data.model.ProfileAppearance
import com.anisequel.data.model.formatLabel
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownView
import com.anisequel.ui.components.openExternalUrl

/**
 * One person's profile: a character, or the person who made them.
 *
 * ## Why both kinds are one composable
 *
 * An actor and an animator are rendered the same way - a portrait, a name, the aliases
 * or the occupation, a handful of facts, a biography, and the titles they are on - and
 * the only differences are which rows exist and what the heading says. Two composables
 * would be the same layout written twice, and the drift starts immediately: a fact row
 * appears in one and not the other, and the page stops reading as one family.
 *
 * ## Why this is a sheet and not a route
 *
 * The studio page is a route because a filmography is long enough to scroll properly
 * and a studio is somewhere a reader *goes*. A person is something you look at *from*
 * a title page - you tap a face in the cast, read who they are, tap back - so this is a
 * sheet over the title page, and the way back is its own dismissal.
 *
 * It is drawn by the screen rather than by [InfoPage] for the reason the comments sheet
 * is: a `ModalBottomSheet` is its own window, so one opened from inside the detail
 * sheet would cover it.
 *
 * ## The biography goes through the markdown pipeline
 *
 * AniList writes a description as AniList-flavored Markdown - emphasis, links, the
 * occasional spoiler - so it is parsed and rendered by the same components the bio
 * uses. An empty biography renders nothing rather than an empty card: plenty of
 * characters have no description, and a card with a heading over blank space is worse
 * than no card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PersonProfileSheet(
    state: PersonProfileState,
    onDismiss: () -> Unit,
    /**
     * Opens one of the titles on this person.
     *
     * The [ProfileAppearance] rather than a media id, so the host can route it to the
     * detail sheet with the summary it already holds and the tap opens instantly
     * instead of fetching a title the reader can already see.
     */
    onOpenAppearance: (ProfileAppearance) -> Unit = {},
    onRetry: () -> Unit = {}
) {
    val appearancesState = rememberLazyListState()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = 16.dp)
                .testTag("person_profile_sheet")
        ) {
            when (state) {
                PersonProfileState.Closed -> Unit

                is PersonProfileState.Loading -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 44.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    ExpressiveLoadingIndicator(
                        modifier = Modifier.size(30.dp),
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "Loading ${state.name ?: personWord(state.kind)}…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                is PersonProfileState.Failed -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 28.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = state.message,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onRetry) { Text("Try again") }
                }

                is PersonProfileState.Loaded -> LoadedPersonProfile(
                    state = state,
                    appearancesState = appearancesState,
                    onOpenAppearance = onOpenAppearance
                )
            }
        }
    }
}

/**
 * The three body states, in one column.
 *
 * Split out so the height cap and the spacing are stated once rather than per branch:
 * a biography plus twelve covers is taller than a phone, and without a cap the portrait
 * and the name - the two things that identify whose face this is - are pushed off the
 * screen the sheet opened on.
 */
@Composable
private fun LoadedPersonProfile(
    state: PersonProfileState.Loaded,
    appearancesState: LazyListState,
    onOpenAppearance: (ProfileAppearance) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = PROFILE_SHEET_MAX_HEIGHT)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ProfileIdentity(state)

        if (state.facts.isNotEmpty()) {
            ProfileFacts(facts = state.facts)
        }

        ProfileBiography(description = state.description)

        if (state.appearances.isNotEmpty()) {
            ProfileAppearancesRow(
                appearances = state.appearances,
                listState = appearancesState,
                onOpen = onOpenAppearance
            )
        }

        // The escape hatch every in-app page carries. The sheet answers what AniList
        // knows; AniList's own page still has the comment thread and the edit history
        // this does not.
        TextButton(
            onClick = { state.siteUrl?.let { openExternalUrl(LocalContext.current, it) } },
            enabled = state.siteUrl != null,
            modifier = Modifier.align(Alignment.CenterHorizontally)
        ) {
            Text("Open on AniList")
        }
    }
}

/** What this sheet calls a person while their data is still arriving. */
private fun personWord(kind: PersonKind): String =
    if (kind == PersonKind.CHARACTER) "character" else "person"

/** The sheet's ceiling: tall enough for a biography, short enough to fit a phone. */
private val PROFILE_SHEET_MAX_HEIGHT = 620.dp

@Composable
private fun ProfileIdentity(state: PersonProfileState.Loaded) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = state.portraitUrl,
            contentDescription = state.name,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(84.dp)
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .testTag("profile_portrait")
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = state.name ?: "Unknown",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = personWord(state.kind).replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            if (state.aliases.isNotEmpty()) {
                Text(
                    text = state.aliases.joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * The facts, as label/value capsules.
 *
 * Capsules rather than the two-column grid the title page uses: a person has four or
 * five facts of wildly different widths, and a grid forces every cell to the width of
 * the widest pair. A capsule is its own width, so "Age 27" is short and
 * "Blood type A" is not.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ProfileFacts(facts: List<Pair<String, String>>) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        facts.forEach { (label, value) ->
            Surface(
                shape = ExpressiveShapes.pill,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.testTag("profile_fact_$label")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f),
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = value,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileBiography(description: String?) {
    if (description.isNullOrBlank()) return
    val nodes = remember(description) { AniListMarkdownParser.parse(description) }
    if (nodes.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_biography"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppVectorIcons.ProfileAbout,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(15.dp)
            )
            Spacer(modifier = Modifier.width(7.dp))
            Text(
                text = "About",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }
        AniListMarkdownView(nodes = nodes)
    }
}

/**
 * The titles, as a row of covers.
 *
 * A row rather than a vertical list: a director has hundreds of credits, twelve of them
 * on this screen, and a reader recognises a poster rather than reading twelve lines. The
 * score rides on the artwork because it is the one number a poster cannot carry.
 */
@Composable
private fun ProfileAppearancesRow(
    appearances: List<ProfileAppearance>,
    listState: LazyListState,
    onOpen: (ProfileAppearance) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Appears in",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
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
    val title = appearance.title.displayTitle
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
                .clip(ExpressiveShapes.pill)
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
                Surface(
                    shape = ExpressiveShapes.pill,
                    color = Color.Black.copy(alpha = 0.62f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                ) {
                    Text(
                        text = "$score%",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
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
        Text(
            text = appearance.formatLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private val APPEARANCE_CARD_WIDTH = 112.dp
private val APPEARANCE_POSTER_HEIGHT = 158.dp