package com.anisequel.ui.components.novamarkdown

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.openExternalUrlOrCopy
import java.util.Locale

@Composable
internal fun NovaQuote(quote: NovaBlock.Quote) {
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
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // AniList styles quoted text italic. The scope is inherited by the
            // nested paragraphs, so every block inside the quote reads as one.
            CompositionLocalProvider(LocalNovaItalic provides true) {
                quote.blocks.forEach { NovaBlockContent(it) }
            }
        }
    }
}

@Composable
internal fun NovaCode(code: NovaBlock.Code) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = code.code,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(12.dp)
        )
    }
}

@Composable
internal fun NovaRule() {
    HorizontalDivider(
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
internal fun NovaList(list: NovaBlock.BulletList) {
    val context = LocalContext.current
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        list.items.forEachIndexed { index, item ->
            Row(modifier = Modifier.fillMaxWidth()) {
                if (item.checked != null) {
                    Icon(
                        imageVector = if (item.checked) {
                            AppVectorIcons.CheckCircle
                        } else {
                            AppVectorIcons.BookmarkDone
                        },
                        contentDescription = if (item.checked) "Done" else "Not done",
                        tint = if (item.checked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier
                            .padding(top = 3.dp)
                            .size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                } else {
                    Text(
                        text = if (list.ordered) "${list.start + index}." else "•",
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = FontWeight.Bold
                        ),
                        color = MaterialTheme.colorScheme.primary,
                        softWrap = false,
                        maxLines = 1,
                        modifier = Modifier.width(26.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    item.blocks.forEachIndexed { blockIndex, block ->
                        val paragraph = block as? NovaBlock.Paragraph
                        if (item.checked == true && blockIndex == 0 && paragraph != null) {
                            Text(
                                text = buildNovaAnnotatedString(
                                    paragraph.inlines,
                                    onLinkClick = { openExternalUrlOrCopy(context, it) }
                                ),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = TextDecoration.LineThrough
                                ),
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            NovaBlockContent(block)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NovaTable(table: NovaBlock.Table) {
    val context = LocalContext.current
    val scroll = rememberScrollState()
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scroll)
        ) {
            Row(
                modifier = Modifier
                    .background(
                        MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f)
                    )
                    .padding(vertical = 4.dp)
            ) {
                table.headers.forEach { cell ->
                    Box(
                        modifier = Modifier
                            .widthIn(min = 96.dp)
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = buildNovaAnnotatedString(
                                cell.inlines,
                                onLinkClick = { openExternalUrlOrCopy(context, it) }
                            ),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            textAlign = cell.align.toTextAlign()
                        )
                    }
                }
            }

            table.rows.forEachIndexed { rowIndex, row ->
                Row(
                    modifier = Modifier
                        .background(
                            if (rowIndex % 2 == 1) {
                                MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerLow
                            }
                        )
                        .padding(vertical = 2.dp)
                ) {
                    row.forEach { cell ->
                        Box(
                            modifier = Modifier
                                .widthIn(min = 96.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = buildNovaAnnotatedString(
                                    cell.inlines,
                                    onLinkClick = { openExternalUrlOrCopy(context, it) }
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = cell.align.toTextAlign()
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * A `~!…!~` or HTML spoiler, as a chip that opens into the full block renderer.
 *
 * The chip is the only control and always says what the tap will do. The revealed
 * content is deliberately not a toggle: it is usually several lines with links
 * in them, and a thumb resting on the card must not hide what is being read.
 */
@Composable
internal fun NovaSpoiler(spoiler: NovaBlock.Spoiler) {
    var revealed by rememberSaveable { mutableStateOf(false) }
    val action = if (revealed) "Hide spoiler" else "Show spoiler"
    val label = if (spoiler.label.isNullOrBlank()) {
        action
    } else {
        "$action: ${spoiler.label}"
    }
    val interactionSource = remember { MutableInteractionSource() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
    ) {
        Surface(
            shape = ExpressiveShapes.pill,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(
                    alpha = if (revealed) 0.32f else 0.16f
                )
            ),
            modifier = Modifier
                .clickable(
                    interactionSource = interactionSource,
                    onClick = { revealed = !revealed }
                )
                .bouncyPress(pressedScale = 0.95f, interactionSource = interactionSource)
                .testTag("nova_markdown_spoiler_chip")
        ) {
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

        if (revealed) {
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    spoiler.blocks.forEach { NovaBlockContent(it) }
                }
            }
        }
    }
}

/**
 * One AniList entity a document linked, as a card with its artwork.
 *
 * A missing cover is a complete card: the poster frame is always reserved, with
 * the type's own icon under it, so the card does not change shape when the
 * artwork arrives and a studio - which will never have a poster - still matches
 * the row.
 */
@Composable
internal fun NovaPreview(preview: NovaBlock.Preview) {
    val scope = LocalNovaRenderScope.current
    val context = LocalContext.current
    val cover = preview.mediaCoverKeyOrNull()?.let { scope.mediaCovers[it] }
    val entity = preview.entityPreviewKeyOrNull()?.let { scope.entityPreviews[it] }
    val title = entity?.name?.takeIf { it.isNotBlank() }
        ?: cover?.title?.takeIf { it.isNotBlank() }
        ?: preview.title
    val artUrl = entity?.imageUrl?.takeIf { it.isNotBlank() } ?: cover?.url
    val interactionSource = remember { MutableInteractionSource() }

    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = CardDefaults.outlinedCardBorder(),
        modifier = Modifier
            .fillMaxWidth()
            .bouncyPress(interactionSource = interactionSource)
            .clickable(interactionSource = interactionSource) {
                openExternalUrlOrCopy(context, preview.url)
            }
    ) {
        Row(
            modifier = Modifier.padding(10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(56.dp)
                    .aspectRatio(2f / 3f)
                    .clip(RoundedCornerShape(ExpressiveShapes.ExtraSmallCorner))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = novaPreviewFallback(preview.type),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp)
                )
                artUrl?.let { url ->
                    AsyncImage(
                        model = url,
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
                            text = preview.type.lowercase(Locale.ROOT)
                                .replaceFirstChar { it.uppercase(Locale.ROOT) },
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
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

private fun novaPreviewFallback(type: String) = when (type.uppercase(Locale.ROOT)) {
    "MANGA" -> AppVectorIcons.FavouriteManga
    "CHARACTER" -> AppVectorIcons.FavouriteCharacters
    "STAFF" -> AppVectorIcons.FavouriteStaff
    "STUDIO" -> AppVectorIcons.Studio
    "USER" -> AppVectorIcons.ProfilePerson
    else -> AppVectorIcons.FavouriteAnime
}
