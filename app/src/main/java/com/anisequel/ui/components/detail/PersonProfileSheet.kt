package com.anisequel.ui.components.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.anisequel.data.model.VoiceActorCredit
import com.anisequel.data.model.VoicedCharacter
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveLoadingIndicator
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.markdown.AniListMarkdownParser
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
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
 *
 * ## The page opens on a cover, not on a capsule
 *
 * The header is the title page's own treatment at the size a person's artwork can
 * carry it: the portrait full-bleed behind a scrim, with the portrait itself as a
 * rounded cover and the name on the artwork. It used to be an 84dp face clipped to a
 * pill, sitting on the sheet's container colour - which is the one shape a portrait
 * cannot be: a face is a rectangle, and a pill drawn around one crops the corners
 * off it. The "Appears in" row below had the same problem for the same reason, at
 * 112x158: a stretched pill is an ellipse, and an ellipse is not a cover.
 *
 * ## "Voiced by" is the character's own answer
 *
 * A character is credited to the people who performed them - AniList hangs that on
 * each media edge, per language (see `ProfileAppearanceEdge`) - and a cast list
 * without it can only say who someone *is*, never who they *sound like*. The rows
 * are people, so a tap opens their own page in this same sheet; a host with no
 * repository passes no callback and the rows draw as captions, exactly like the
 * cast cards on a title.
 *
 * ## A staff page is the same page, one row longer
 *
 * A creator's titles carry their own credit and a voice actor's performed characters
 * get the "Voiced" row; both are drawn by `PersonStaffCredits`, which is card layout
 * rather than sheet chrome. The two kinds still share this body - that is the whole
 * point of the one composable - and a person who is both crew and cast simply shows
 * both rows.
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
    onRetry: () -> Unit = {},
    /**
     * Opens a voice actor's own page, from the "Voiced by" rows.
     *
     * The whole [VoiceActorCredit] rather than an id, because the name is already in
     * hand and it is what the sheet says while the next person loads. Null - a host
     * with no repository - draws those rows as captions rather than as controls that
     * could only fail; the same contract as the cast cards, and the same reason
     * `PersonProfileHost` makes its callbacks nullable.
     */
    onOpenStaff: ((VoiceActorCredit) -> Unit)? = null,
    /**
     * Opens one of the characters on the staff page's "Voiced" row.
     *
     * The whole [VoicedCharacter] rather than an id, for the same reason as
     * [onOpenStaff]: the name is already in hand and it is what the sheet says while
     * the next person loads. The character swaps in under the same open sheet - see
     * the note on the keyed body below. Null - a host with no repository - draws the
     * cards as captions rather than as controls that could only fail.
     */
    onOpenCharacter: ((VoicedCharacter) -> Unit)? = null
) {
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

                // Keyed on who the sheet is showing. Tapping a voice actor swaps the
                // person under a sheet that stays open, and without the key the new
                // page inherits the scroll position of the old one: the reader lands
                // halfway down somebody else's biography.
                is PersonProfileState.Loaded -> key(state.kind, state.id) {
                    // Created inside the key rather than handed in from the sheet's
                    // scope: one `LazyListState` object shared by every person put the
                    // new person's "Appears in" row at the previous person's offset,
                    // which is the same inheritance the key above exists to prevent -
                    // the biography reset, the row did not.
                    LoadedPersonProfile(
                        state = state,
                        appearancesState = rememberLazyListState(),
                        onOpenAppearance = onOpenAppearance,
                        onOpenStaff = onOpenStaff,
                        onOpenCharacter = onOpenCharacter
                    )
                }
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
 *
 * The cap is a **scrolling viewport**, not a clip. It used to be `heightIn` alone, on
 * the assumption that a biography would never be long enough to matter; a long one is
 * measured first and takes the height it wants, so the appearances row and the AniList
 * link were measured into whatever remained - zero - and could not be reached at all.
 * With the scroll, the sheet still opens on the portrait, and everything below it is
 * one swipe away.
 */
@Composable
private fun LoadedPersonProfile(
    state: PersonProfileState.Loaded,
    appearancesState: LazyListState,
    onOpenAppearance: (ProfileAppearance) -> Unit,
    onOpenStaff: ((VoiceActorCredit) -> Unit)?,
    onOpenCharacter: ((VoicedCharacter) -> Unit)?
) {
    // Hoisted, not read inside `onClick`. That lambda is a plain one, not a
    // composable, so a `LocalContext.current` read in its body has no composition
    // to read from and the compiler rejects it - "invocations can only happen from
    // the context of a @Composable function". This is the same trap the cast row
    // documents; the fix is the same, and it is the only place the read is legal.
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = PROFILE_SHEET_MAX_HEIGHT)
            .verticalScroll(rememberScrollState())
    ) {
        // The cover bleeds to the sheet's own edges, so the inset everything under it
        // needs is applied there rather than here - a full-bleed header inside a
        // padded column is not full-bleed.
        ProfileHero(state)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (state.facts.isNotEmpty()) {
                ProfileFacts(facts = state.facts)
            }

            // The biography leads on a creator's page and follows the works on a
            // character's.
            //
            // It used to be last on both, because a long biography measured first
            // and pushed the one row those pages exist for - who voiced this
            // character, what this creator worked on - below the sheet's viewport.
            // That is still the right answer for a character: the tap asks who
            // voiced them, and the works row is that answer. A creator's page is
            // the opposite question. The biography is who the person is, it is
            // what the reader came to read, and leaving it under a year-by-year
            // filmography buried it behind the longest section on the page.
            if (state.kind == PersonKind.STAFF) {
                ProfileBiography(description = state.description)
            }

            if (state.voiceActors.isNotEmpty()) {
                ProfileVoiceActors(credits = state.voiceActors, onOpenStaff = onOpenStaff)
            }

            if (state.appearances.isNotEmpty()) {
                if (state.kind == PersonKind.STAFF) {
                    // A creator's page exists for this list, and it is read by
                    // year: AniList answers the connection by popularity, so the
                    // row it used to be jumped between decades with nothing
                    // saying which title came first. See `ProfileAppearancesByYear`.
                    ProfileAppearancesByYear(
                        appearances = state.appearances,
                        onOpen = onOpenAppearance
                    )
                } else {
                    ProfileAppearancesRow(
                        appearances = state.appearances,
                        heading = "Appears in",
                        listState = appearancesState,
                        onOpen = onOpenAppearance
                    )
                }
            }

            // The staff page's mirror of "Voiced by": the characters this person
            // performed. A creator who is also a voice actor is common on AniList,
            // and a filmography alone never says who they sounded like.
            if (state.voicedCharacters.isNotEmpty()) {
                ProfileVoicedCharactersRow(
                    characters = state.voicedCharacters,
                    onOpen = onOpenCharacter
                )
            }

            if (state.kind != PersonKind.STAFF) {
                ProfileBiography(description = state.description)
            }

            // The escape hatch every in-app page carries. The sheet answers what AniList
            // knows; AniList's own page still has the comment thread and the edit history
            // this does not.
            TextButton(
                onClick = { state.siteUrl?.let { openExternalUrl(context, it) } },
                enabled = state.siteUrl != null,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            ) {
                Text("Open on AniList")
            }
        }
    }
}

/** What this sheet calls a person while their data is still arriving. */
private fun personWord(kind: PersonKind): String =
    if (kind == PersonKind.CHARACTER) "character" else "person"

/** The sheet's ceiling: tall enough for a biography, short enough to fit a phone. */
private val PROFILE_SHEET_MAX_HEIGHT = 620.dp

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
        SectionHeading(title = "About", icon = AppVectorIcons.ProfileAbout)
        // The cover-aware renderer, not the bare one: an AniList bio is full of
        // links to titles, and this surface was the one markdown renderer nobody
        // had given a cover loader to - so every card drew the badge and no poster
        // while the profile's About card beside it drew them correctly. The loader
        // comes from `LocalMediaCoverLoader`, provided by whoever draws the sheet.
        AniListMarkdownWithCovers(nodes = nodes)
    }
}

/**
 * The people who voiced this character, one row each.
 *
 * Japanese before English, deduplicated per language by the model (see
 * `ProfileAppearances.voiceActorCredits`), and capped because a recast-heavy character
 * can be credited to more people than a sheet should list: past the fourth the rows
 * stop being "who is this character's voice" and start being a wiki.
 *
 * A row opens that actor's own page in this same sheet - the tap a reader already
 * expects from every other face in the app - unless the host has no repository, in
 * which case the rows are captions. See the KDoc on the sheet's own parameters.
 */
@Composable
private fun ProfileVoiceActors(
    credits: List<VoiceActorCredit>,
    onOpenStaff: ((VoiceActorCredit) -> Unit)?
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile_voice_actors")
    ) {
        SectionHeading(title = "Voiced by", icon = AppVectorIcons.Mic)
        Spacer(modifier = Modifier.height(6.dp))
        credits.take(VOICE_ACTOR_ROWS).forEach { credit ->
            VoiceActorRow(credit = credit, onOpenStaff = onOpenStaff)
        }
    }
}

@Composable
private fun VoiceActorRow(credit: VoiceActorCredit, onOpenStaff: ((VoiceActorCredit) -> Unit)?) {
    // Named `tapTarget`, not `clickable`: a local called `clickable` shadows the very
    // modifier function its own initializer is calling.
    val tapTarget = if (onOpenStaff != null) {
        Modifier
            .clip(ExpressiveShapes.pill)
            .clickable(onClickLabel = "Open voice actor") { onOpenStaff(credit) }
    } else {
        Modifier
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(tapTarget)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
        ) {
            AsyncImage(
                model = credit.imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = credit.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Surface(
            shape = ExpressiveShapes.pill,
            color = MaterialTheme.colorScheme.secondaryContainer
        ) {
            Text(
                text = credit.language,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
            )
        }
    }
}

/**
 * How many voice actors the sheet lists.
 *
 * Two languages and one performer per language is the ordinary case; four leaves room
 * for a recast or a second dub and still reads as an answer rather than a list.
 */
private const val VOICE_ACTOR_ROWS = 4