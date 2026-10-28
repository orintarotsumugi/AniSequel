package com.anisequel.ui.components.markdown

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaCover
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy

@Composable
fun RenderHeader(header: MarkdownNode.Header) {
    val style = when (header.level) {
        1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold, fontSize = 20.sp)
        2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 18.sp)
        3 -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
        else -> MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold)
    }
    val context = LocalContext.current
    val annotated = buildInlineAnnotatedString(header.inlines, onLinkClick = { openExternalUrlOrCopy(context, it) })
    Text(
        text = annotated,
        style = style,
        color = MaterialTheme.colorScheme.onSurface
    )
}

@Composable
fun RenderBlockquote(quote: MarkdownNode.Blockquote) {
    val context = LocalContext.current
    val annotated = buildInlineAnnotatedString(quote.inlines, onLinkClick = { openExternalUrlOrCopy(context, it) })
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.5f))
            .padding(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .clip(ExpressiveShapes.pill)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = annotated,
            style = MaterialTheme.typography.bodyMedium.copy(fontStyle = FontStyle.Italic),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun RenderCodeBlock(block: MarkdownNode.CodeBlock) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = block.code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
fun RenderList(list: MarkdownNode.ListBlock) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        list.items.forEachIndexed { index, itemInlines ->
            Row(modifier = Modifier.fillMaxWidth()) {
                val bullet = if (list.ordered) "${index + 1}." else "•"
                // 26dp and `softWrap = false`, not 20dp: an ordered list past its
                // ninth item prints "10.", which does not fit the old column and
                // wrapped onto a second line - so the tenth item of every
                // numbered list in every bio was indented by one blank row.
                Text(
                    text = bullet,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                    softWrap = false,
                    maxLines = 1,
                    modifier = Modifier.width(26.dp)
                )
                val annotated = buildInlineAnnotatedString(itemInlines, onLinkClick = { openExternalUrlOrCopy(context, it) })
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun RenderCheckList(checklist: MarkdownNode.CheckListBlock) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        checklist.items.forEach { item ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (item.checked) AppVectorIcons.CheckCircle else AppVectorIcons.BookmarkDone,
                    contentDescription = null,
                    tint = if (item.checked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                val annotated = buildInlineAnnotatedString(item.inlines, onLinkClick = { openExternalUrlOrCopy(context, it) })
                Text(
                    text = annotated,
                    style = MaterialTheme.typography.bodyMedium.copy(
                        textDecoration = if (item.checked) TextDecoration.LineThrough else TextDecoration.None
                    ),
                    color = if (item.checked) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun RenderImage(image: MarkdownNode.ImageBlock) {
    val context = LocalContext.current
    val clickableModifier = if (image.targetUrl != null) {
        Modifier
            .bouncyPress()
            .clickable { openExternalUrlOrCopy(context, image.targetUrl) }
    } else {
        Modifier
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .then(clickableModifier),
        contentAlignment = if (image.centered) Alignment.Center else Alignment.CenterStart
    ) {
        AsyncImage(
            model = image.url,
            contentDescription = image.alt ?: "Markdown image",
            modifier = imageWidthModifier(image.width)
                .heightIn(max = MAX_RICH_IMAGE_HEIGHT_DP.dp)
                .clip(MaterialTheme.shapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            // `Fit`, not `FillWidth`: with the height cap in place, FillWidth
            // would scale a very tall image to the width and then crop it to the
            // cap. Fit keeps the whole image inside the same box.
            contentScale = ContentScale.Fit,
            placeholder = rememberVectorPainter(AppVectorIcons.AnimeSparkle),
            error = rememberVectorPainter(AppVectorIcons.AnimeSparkle)
        )
    }
}

/**
 * The width `img220(...)` / `img50%(...)` asked for.
 *
 * A percentage is that fraction of the width a size-less image would take, and a
 * plain number is the dp the author wrote. Neither can overflow: `fillMaxWidth`
 * is given a fraction, and `Modifier.width` reports its size through the
 * incoming constraints' `constrain`, so an `img900(...)` in a phone-width bio
 * comes out at the bio's width rather than running off the side - which is the
 * cap, without measuring the parent first. (The alternative, `BoxWithConstraints`,
 * is deliberately not used: its `maxWidth` accessor is on its way out, see
 * `TierBoardHost`.)
 *
 * A size-less image - every markdown `![alt](url)` and every inline image -
 * fills the width exactly as it always did, and a malformed size degrades to
 * that same full width rather than to something zero-sized and invisible.
 */
private fun imageWidthModifier(width: String?): Modifier {
    val percent = width?.takeIf { it.endsWith("%") }?.dropLast(1)?.toIntOrNull()
    if (percent != null) return Modifier.fillMaxWidth(percent.coerceIn(1, 100) / 100f)
    val pixels = clampedImageWidthDp(width)
    return if (pixels != null) Modifier.width(pixels.dp) else Modifier.fillMaxWidth()
}

@Composable
fun RenderVideo(video: MarkdownNode.VideoBlock) {
    val context = LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .clickable { openExternalUrlOrCopy(context, video.url) }
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = AppVectorIcons.Play,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (video.isYoutube) "Watch on YouTube" else "Watch Media Clip",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = video.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(
                imageVector = AppVectorIcons.OpenInNew,
                contentDescription = "Open",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * A `~!spoiler~!`, as a labelled chip that opens into the text and closes again.
 *
 * ## The shape
 *
 * A **chip sitting on the top edge** of the block, not a row of text inside a
 * container. The block is a surface with a control on it, and putting the control
 * *on* the surface made the two indistinguishable from the body text underneath — a
 * reader scanning a bio could not tell where the hidden part began, which is the only
 * thing a spoiler needs to say before it is opened. The chip is drawn above the
 * surface's top edge so it reads as the lid of a box rather than as the first line of
 * one, and it is the only thing on screen while the spoiler is closed.
 *
 * ## Why the revealed state is not tappable any more
 *
 * The whole block used to be one toggle, so *any* tap on a revealed spoiler closed it
 * again. That is the wrong gesture in the one state where reading happens: a spoiler is
 * usually several lines, it is often scrolled through with a thumb resting on the card,
 * and it can contain links — every one of which was swallowed by the toggle. Tapping a
 * link inside a spoiler did nothing visible except hide the text that contained it.
 *
 * So the block now has two parts with different jobs: the chip, which is the control
 * and always says what it will do, and, once revealed, the text itself, which behaves
 * like any other paragraph.
 *
 * ## Why the chip's label changes
 *
 * It reads "Show spoiler" while the text is hidden and "Hide spoiler" while it is
 * visible, so the state is in **words** and not only in a glyph: it survives a screen
 * reader, it reads correctly in greyscale, and it says what the tap will do rather
 * than what the block currently is. The eye stays the same glyph in both states —
 * an eye that changed shape would be the only signal that did not survive either of
 * those, and 16dp is too small to carry it alone. The previous header's only "you can
 * close this" cue was an unlabelled `Done` icon, which is a cue nobody guesses.
 */
@Composable
fun RenderSpoilerBlock(spoiler: MarkdownNode.SpoilerBlock) {
    // `rememberSaveable`, not `remember`.
    //
    // This renders inside a profile's `LazyColumn`, and a plain `remember` is discarded
    // as soon as the card leaves the viewport — so a revealed spoiler re-hid itself the
    // moment the reader scrolled past it and back. The state is small enough to save
    // (one boolean), and `rememberSaveable` also survives a configuration change, so
    // the reveal is not undone by a rotation either.
    var revealed by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current

    Column(
        // `animateContentSize` on the *outer* column: revealing swaps a one-line chip
        // for the hidden text, so the block changes height. Animating that here means
        // the surrounding list does not jump, and the token is the `IntSize` one for
        // exactly this reason.
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
    ) {
        SpoilerChip(
            revealed = revealed,
            onToggle = { revealed = !revealed },
            modifier = Modifier.padding(bottom = if (revealed) 8.dp else 0.dp)
        )

        if (revealed) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = buildInlineAnnotatedString(
                        spoiler.inlines,
                        onLinkClick = { openExternalUrlOrCopy(context, it) }
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }
    }
}

/**
 * The control for one spoiler: the lid on the block, and the only thing visible while
 * it is closed.
 *
 * Separate from [RenderSpoilerBlock] because it is the one part that is interactive,
 * and the chip is the whole gesture — the revealed text deliberately takes no clicks,
 * so a thumb resting on the card while reading cannot hide what is being read.
 */
@Composable
private fun SpoilerChip(
    revealed: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val label = if (revealed) "Hide spoiler" else "Show spoiler"
    // The clickable's own `InteractionSource`, handed to `bouncyPress` so the two
    // share one press signal.
    //
    // Without it, `bouncyPress` takes its documented fallback path and installs a
    // *second* pointer-input loop of its own - `awaitFirstDown`, then a loop over
    // `PointerEventPass.Main`. Two handlers on one element compete for the gesture,
    // and the chip stopped responding to the tap: the reveal simply did nothing.
    // Passing the source means that loop is never installed and the `clickable`
    // drives the press, which is the whole reason the parameter exists.
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = ExpressiveShapes.pill,
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        border = BorderStroke(
            width = 1.dp,
            // Brighter while it is open: over a dark container the two states are
            // otherwise told apart only by the glyph, and the eye icon is 16dp.
            color = if (revealed) {
                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.32f)
            } else {
                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.16f)
            }
        ),
        modifier = modifier
            .clickable(
                interactionSource = interactionSource,
                onClick = onToggle
            )
            .bouncyPress(pressedScale = 0.95f, interactionSource = interactionSource)
            .testTag("markdown_spoiler_chip")
    ) {
        // A `Row`, not a `FlowRow`. `FlowRow` measures its content against the widest
        // constraint it is given, and a `Surface` inside a `fillMaxWidth` Column hands it
        // the full width - so the chip stretched the length of the card and left the
        // label stranded at one end. A Row wraps to its content, which is what a chip
        // is. `wrapContentWidth` is stated rather than left implicit for the same
        // reason.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier
                .wrapContentWidth()
                .padding(horizontal = 12.dp, vertical = 7.dp)
        ) {
            Icon(
                imageVector = AppVectorIcons.Visibility,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * One AniList entity a bio linked, as a card with its cover art.
 *
 * [cover] is looked up by media key by the caller rather than fetched here,
 * because a bio can carry a dozen of these and one request each would spend a
 * dozen of AniList's 30-a-minute budget before the reader had scrolled. The
 * caller batches them and passes a map; a miss leaves the type icon sitting in
 * the poster frame rather than an empty hole, so the card keeps its shape.
 *
 * The card's title prefers [MediaCover.title] over the one parsed out of the URL.
 * A bio link of `https://anilist.co/anime/1` has no readable name in it, so the
 * fallback for it is `ANIME #1` - a debug row, on the single most common link
 * shape there is, because that is what the address bar hands you. The real title
 * arrives in the same response as the poster and is always right about what the
 * image depicts; the URL's contribution is only used when there is no fetched
 * cover at all.
 *
 * The poster keeps its 2:3 shape rather than being squashed into a square
 * thumbnail - a cropped portrait face loses the one thing a poster is good for,
 * which is being recognised.
 *
 * The poster slot is reserved whether or not [coverUrl] resolved. It used to be
 * omitted entirely on a miss, so the card started as a single column of text and
 * jumped to two the moment the cover arrived - which re-laid out the whole bio
 * under the reader's eyes. The type icon underneath keeps the frame from reading
 * as a broken image in the meantime, and for an entity that will never have a
 * cover (a studio, a staff member) the frame still matches every other card.
 */
@Composable
fun RenderAniListPreview(
    preview: MarkdownNode.AniListPreviewBlock,
    cover: MediaCover? = null
) {
    val context = LocalContext.current
    // An unblank fetched title wins. `ANIME #1` is a fallback, not a title, and
    // the whole point of fetching the cover in the first place is that AniList
    // told us what this media is actually called.
    val title = cover?.title?.takeIf { it.isNotBlank() } ?: preview.title

    Surface(
        // `small` (14dp), not `medium` (20dp): this card is mostly a 56dp poster
        // with a caption beside it, and at `medium` the card's own corner outran
        // the poster's and made a row of links look like a stack of lozenges. The
        // poster inside is `extraSmall`.
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress()
            .clickable { openExternalUrlOrCopy(context, preview.url) }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // One poster frame, always drawn, so the card has a single shape
            // whether the cover has loaded, is missing, or can never exist.
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(POSTER_ASPECT)
                    .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = previewFallback(preview.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                cover?.let { fetched ->
                    AsyncImage(
                        model = fetched.url,
                        contentDescription = title,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = ExpressiveShapes.pill,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Text(
                            text = preview.type.lowercase().replaceFirstChar { it.uppercase() },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    // The numeric id used to be printed here. It told the reader
                    // nothing they could act on and made every card look like a
                    // debug row; the badge plus the link icon says what this is
                    // and where tapping goes.
                    Text(
                        text = "AniList",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }

            Icon(
                imageVector = AppVectorIcons.OpenInNew,
                contentDescription = "Open on AniList",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/** The type-appropriate icon drawn while a cover loads, or when there is none. */
private fun previewFallback(type: String) =
    if (type.equals("MANGA", ignoreCase = true)) {
        AppVectorIcons.FavouriteManga
    } else {
        AppVectorIcons.FavouriteAnime
    }

/** AniList covers are 2:3. */
private const val POSTER_ASPECT = 2f / 3f

