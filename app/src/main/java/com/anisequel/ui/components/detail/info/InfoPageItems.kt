package com.anisequel.ui.components.detail.info

import android.content.Context
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.anisequel.data.model.MediaExternalLink
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.MissedSequel
import com.anisequel.data.model.ThemeSong
import com.anisequel.data.model.alternativeTitleRows
import com.anisequel.data.model.characterList
import com.anisequel.data.model.genreList
import com.anisequel.data.model.hasProgressCard
import com.anisequel.data.model.hasRankings
import com.anisequel.data.model.relatedMedia
import com.anisequel.data.model.staffList
import com.anisequel.data.model.streamingLinks
import com.anisequel.data.model.studioCredits
import com.anisequel.data.model.synonymList
import com.anisequel.ui.components.detail.AnimeDetailSubject
import com.anisequel.ui.components.detail.AnimeRankingsSection
import com.anisequel.ui.components.detail.DetailActionRow
import com.anisequel.ui.viewmodel.QuickAddStatus

/**
 * What the Info page lists, and in which order.
 *
 * ## Why this is not inside [AnimeInfoPage]
 *
 * It used to be: the page function carried its state, its motion and all fifteen
 * `item { }` declarations in one body, and that body reached four hundred lines -
 * long enough that CodeFactor's Complex Method check failed the pull request for
 * it. Length was the symptom; the cause is that two different things had one
 * reason to change. [AnimeInfoPage] owns *how the page behaves* - what it derives
 * from scroll, what it fetches, what its floating bar does - and this file owns
 * *what the page draws and where*. Adding a section touches this file; retiming
 * the hero's collapse touches the page. When those are one function, every
 * change to either is a change to a 400-line body nobody can hold in their head.
 *
 * The split is a plain `LazyListScope` extension rather than a composable because
 * that is what a `LazyColumn`'s body already is: the items are declared against
 * the list, not composed by the page - the same shape as the `items { }` calls
 * this file is made of.
 *
 * ## Why sections pass `visible` instead of branching
 *
 * An item that draws nothing still costs a slot: `Arrangement.spacedBy` gives
 * every item its trailing 14dp gap whether or not its content produced anything,
 * so a section that returns early leaves a hole. Hence the gate on each below -
 * and hence the gate being an *argument* rather than an `if` around the call: with
 * the decision made at the call site, the list function itself has no branches to
 * count, and it stays a readable order-of-sections rather than a nesting problem.
 * The one exception is the parent card, which needs a non-null sequel and takes
 * the safe call instead - one branch, on the one item that cannot be drawn at all
 * without a value it may not have.
 *
 * The two helper functions at the bottom are the inset decision, which used to be
 * written out per section: a card takes the page's inset and joins the entrance
 * stagger, a row bleeds to the screen edge and draws its own heading inset - see
 * the note on [rowItem].
 */
internal fun LazyListScope.infoPageItems(
    subject: AnimeDetailSubject,
    sections: InfoSections,
    themeSongs: List<ThemeSong>,
    isDetailLoading: Boolean,
    canWriteToAniList: Boolean,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    context: Context,
    quickAddStatus: QuickAddStatus,
    onAddMediaToPlanning: (MediaNode) -> Unit,
    onAddMediaToWatching: (MediaNode) -> Unit,
    onAddToPlanning: (MissedSequel) -> Unit,
    onAddToWatching: (MissedSequel) -> Unit,
    onOpenMedia: (MediaNode) -> Unit,
    onOpenStudio: (Int, String?) -> Unit,
    onOpenCharacter: ((Int) -> Unit)?,
    onOpenStaff: ((Int) -> Unit)?,
    onOpenCover: () -> Unit
) {
    val media = subject.media

    // Only a franchise recommendation has a parent; an arriving or activity row is
    // a show the viewer already tracks. The safe call is the one branch in this
    // list, and it earns itself: the card needs a non-null sequel, and there is no
    // honest `visible` flag for "there is no parent to draw".
    subject.sequel?.let { sequel ->
        cardItem(key = "parent", index = 1) { InfoParentCard(sequel = sequel, modifier = it) }
    }

    cardItem(key = "actions", index = 2) {
        DetailActionRow(
            subject = subject,
            canWriteToAniList = canWriteToAniList,
            quickAddStatus = quickAddStatus,
            onAddMediaToPlanning = onAddMediaToPlanning,
            onAddMediaToWatching = onAddMediaToWatching,
            onAddToPlanning = onAddToPlanning,
            onAddToWatching = onAddToWatching,
            isDetailLoading = isDetailLoading,
            holdDurationSeconds = holdDurationSeconds,
            swipeEnabled = swipeEnabled,
            context = context,
            modifier = it
        )
    }

    cardItem(
        key = "progress",
        index = 3,
        visible = sections.hasProgressCard
    ) { InfoProgressCard(media = media, modifier = it) }

    cardItem(
        key = "streaming",
        index = 4,
        visible = sections.streamingLinks.isNotEmpty()
    ) { InfoWhereToWatchCard(links = sections.streamingLinks, modifier = it) }

    cardItem(key = "synopsis", index = 5) {
        InfoSynopsisCard(media = media, isDetailLoading = isDetailLoading, modifier = it)
    }

    cardItem(key = "specs", index = 6) {
        InfoSpecsCard(
            media = media,
            isDetailLoading = isDetailLoading,
            onOpenStudio = onOpenStudio,
            modifier = it
        )
    }

    // The reference's "Categories": genres, weighted tags, the Expand control and
    // the collapsible spoiler strip, in one section rather than the two cards
    // genres and tags used to be.
    cardItem(
        key = "categories",
        index = 7,
        visible = sections.hasCategories
    ) { InfoCategoriesSection(media = media, modifier = it) }

    // Streaming links have their own card above; this is every *other* link
    // AniList files for the title.
    cardItem(
        key = "external_links",
        index = 8,
        visible = sections.hasExternalLinks
    ) { InfoExternalLinksSection(media = media, modifier = it) }

    // Only added when the fetch landed with something: an empty item still costs
    // the page's `spacedBy` gap. The row is cached per title, so a re-open draws it
    // on the first pass.
    rowItem(
        key = "theme_songs",
        index = 9,
        visible = themeSongs.isNotEmpty()
    ) { InfoThemeSongsSection(themes = themeSongs, modifier = it) }

    // Titles, Synonyms and Producers are disclosure rows rather than a card of
    // label/value pairs: each opens its own sheet with the complete list, which is
    // what keeps six producers and fifteen synonyms from doubling the page's length.
    cardItem(
        key = "meta_rows",
        index = 10,
        visible = sections.hasMetaRows
    ) { InfoMetaRowsSection(media = media, modifier = it) }

    // The one section the reference has no equivalent for, and the reason it is
    // kept: an all-time AniList ranking is a fact about this title that nothing
    // else on the page can state.
    cardItem(
        key = "rankings",
        index = 11,
        visible = sections.hasRankings
    ) { AnimeRankingsSection(media = media, modifier = it) }

    // The three artwork rows are `rowItem` rather than `cardItem`, and that is
    // deliberate: they are not cards, so they draw their own heading inset and
    // their own `LazyRow` content padding, and adding the page inset on top would
    // double it. See `InfoTalent.kt`.
    rowItem(
        key = "cast",
        index = 12,
        visible = sections.hasCast
    ) { InfoCharactersSection(media = media, onOpenCharacter = onOpenCharacter, modifier = it) }

    rowItem(
        key = "relations",
        index = 13,
        visible = sections.hasRelations
    ) { InfoRelationsSection(media = media, onOpen = onOpenMedia, modifier = it) }

    rowItem(
        key = "staff",
        index = 14,
        visible = sections.hasStaff
    ) { InfoStaffSection(media = media, onOpenStaff = onOpenStaff, modifier = it) }

    cardItem(key = "extras", index = 15) {
        InfoExtrasCard(media = media, onOpenCover = onOpenCover, modifier = it)
    }
}

/**
 * A section that is a **card**: it takes the page's inset and joins the entrance
 * stagger. The modifier is handed to the content rather than taken by it, because
 * the inset and the stagger are the page's decisions - a section that added its
 * own would double the inset, which is exactly how [PAGE_INSET] came to exist
 * twice in two files.
 *
 * [visible] skips the item entirely rather than drawing nothing, for the reason
 * [infoPageItems] gives: an empty slot is still a gap.
 */
private fun LazyListScope.cardItem(
    key: String,
    index: Int,
    visible: Boolean = true,
    content: @Composable (Modifier) -> Unit
) {
    if (!visible) return
    item(key = key) { content(Modifier.infoSection(index)) }
}

/**
 * A section that is a **row**: artwork that bleeds to the screen edge, so it must
 * not take the page's inset on top of the one it draws itself. It still joins the
 * entrance stagger through `sectionReveal`, which is the same modifier
 * [cardItem] applies - only the inset differs.
 *
 * The heading and content padding belong to the row (see `InfoTalent.kt` and
 * `InfoThemeSongsSection.kt`); this function is only where that split is decided.
 */
private fun LazyListScope.rowItem(
    key: String,
    index: Int,
    visible: Boolean = true,
    content: @Composable (Modifier) -> Unit
) {
    if (!visible) return
    item(key = key) { content(Modifier.sectionReveal(index)) }
}

/**
 * The page's inset and its place in the entrance stagger, in one call.
 *
 * Every card but the hero takes exactly this, and writing it out fifteen times is
 * how they drift apart.
 *
 * [PAGE_INSET] is the one in `InfoCards.kt`, not a second literal: the artwork
 * rows draw their own inset from that same value, and it used to exist twice in
 * two files.
 */
@Composable
private fun Modifier.infoSection(index: Int): Modifier =
    padding(horizontal = PAGE_INSET).sectionReveal(index)

/**
 * The page's section decisions, taken once per [MediaNode].
 *
 * Every field is a derived read - the getters this walks all compute something -
 * and the page's body recomposes several times while the sheet opens. Nothing here
 * can change while the node it was derived from is the same object, so the work
 * belongs in one `remember(media)` on the page rather than in the item-listing
 * lambda that runs again on each pass. The booleans are stored rather than the
 * lists they were read from because that is all this file does with them - the one
 * exception is `streamingLinks`, which is kept as the list because the card is
 * handed it straight.
 *
 * Every field is a flag or that one list: which sections draw is decided here, what
 * they draw is each section's own business. The gates have to be able to answer
 * *before* the list is built, because `Arrangement.spacedBy` charges every item its
 * gap whether or not the item's content went on to draw anything (see
 * [infoPageItems]).
 */
internal data class InfoSections(
    val hasMetaRows: Boolean,
    val streamingLinks: List<MediaExternalLink>,
    val hasCategories: Boolean,
    val hasExternalLinks: Boolean,
    val hasProgressCard: Boolean,
    val hasRankings: Boolean,
    val hasCast: Boolean,
    val hasRelations: Boolean,
    val hasStaff: Boolean
)

/** Builds [InfoSections] for one title. Pure, and separate so the page can
 * remember it in one call: the decisions are walks over the node - genres, tags,
 * links, credits - and each one is a branch the page function used to carry. */
internal fun infoSectionsFor(media: MediaNode): InfoSections = InfoSections(
    hasMetaRows = media.alternativeTitleRows.isNotEmpty() ||
        media.synonymList.isNotEmpty() ||
        media.studioCredits.any { !it.isMain },
    streamingLinks = media.streamingLinks,
    // Genres and AniList's tag list share one section now, and either is enough to
    // draw it. The tags are read from `media.tags`, not from `weightedTags`: the
    // spoiler tags are part of the section's own collapsible strip, so a title
    // whose only tags are spoilers still has a Categories section worth showing.
    hasCategories = media.genreList.isNotEmpty() || !media.tags.isNullOrEmpty(),
    // Streaming links have their own card; this is every *other* link AniList
    // files for the title.
    hasExternalLinks = media.externalLinks.orEmpty().any {
        !it.site.isNullOrBlank() &&
            !it.url.isNullOrBlank() &&
            !it.type.equals("STREAMING", ignoreCase = true)
    },
    hasProgressCard = media.hasProgressCard,
    hasRankings = media.hasRankings,
    hasCast = media.characterList.isNotEmpty(),
    hasRelations = media.relatedMedia.isNotEmpty(),
    hasStaff = media.staffList.isNotEmpty()
)
