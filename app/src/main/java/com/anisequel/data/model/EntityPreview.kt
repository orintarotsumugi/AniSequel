package com.anisequel.data.model

import com.squareup.moshi.JsonClass
import java.util.Locale

/**
 * What identifies a character, staff member or studio well enough to ask AniList
 * for a preview card.
 *
 * ## Why this is separate from [MediaCoverKey]
 *
 * These are three different id namespaces, and none of them is media: character
 * 1 is Spike Spiegel, studio 1 is a different entity again, and neither is the
 * anime or manga with that id. A single key type that let the two mix would
 * reintroduce the exact "confidently wrong poster" bug [MediaCoverKey] exists to
 * prevent, only across entity kinds instead of media types.
 *
 * Users are deliberately absent. A `/user/<name>` link carries a name rather
 * than a numeric id, and there is no batchable lookup for it that is worth a
 * request; those cards keep the type badge and the label.
 *
 * [type] is stored uppercase - the spelling the parser produces - and
 * [graphqlType] uppercases anyway so a key built by hand cannot quietly group
 * itself under the wrong type.
 */
data class EntityPreviewKey(
    val type: String,
    val id: Int
) {
    val graphqlType: String get() = type.uppercase(Locale.ROOT)

    companion object {
        /** The entity kinds AniList can answer for in one batched request. */
        val SUPPORTED_TYPES = setOf("CHARACTER", "STAFF", "STUDIO")
    }
}

/**
 * One entity link's answer: the name and face its preview card draws.
 *
 * Both fields are nullable because a card is still worth drawing without either
 * - the label the author wrote, or the slug, headings it, and the type icon
 * fills the portrait frame - and because the whole batch is a decorative
 * enrichment that must never turn a missing answer into an error.
 *
 * A studio has no image on AniList, so [imageUrl] is always null for one and its
 * card keeps the studio icon in the frame.
 */
@JsonClass(generateAdapter = true)
data class EntityPreview(
    val name: String? = null,
    val imageUrl: String? = null
)

/**
 * The batched entity query's answer, one root alias per requested type.
 *
 * Only the aliases the document asked for arrive; the repository builds the
 * document from the types present in its batch, so a bio that links only
 * characters never declares the staff or studio variables. Declaring a variable
 * a document does not use is itself a GraphQL validation error, which is why the
 * aliases and the declarations are built together.
 */
@JsonClass(generateAdapter = true)
data class EntityPreviewPages(
    val characters: CharacterPreviewPage? = null,
    val staff: StaffPreviewPage? = null,
    val studios: StudioPreviewPage? = null
)

@JsonClass(generateAdapter = true)
data class CharacterPreviewPage(val characters: List<CharacterProfile>? = null)

@JsonClass(generateAdapter = true)
data class StaffPreviewPage(val staff: List<StaffProfile>? = null)

@JsonClass(generateAdapter = true)
data class StudioPreviewPage(val studios: List<StudioNode>? = null)
