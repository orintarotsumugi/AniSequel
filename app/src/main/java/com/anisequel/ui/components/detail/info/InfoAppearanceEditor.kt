package com.anisequel.ui.components.detail.info

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.formatLabel
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.InfoBackdropBlur
import com.anisequel.data.repository.InfoBackdropSource
import com.anisequel.data.repository.InfoBackdropTint
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveMotion
import com.anisequel.ui.components.expressive.ExpressivePrimaryButton
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.ExpressiveStateChip
import com.anisequel.ui.components.expressive.bouncyPress

/**
 * The Info page's appearance, edited on the page it changes.
 *
 * ## Why the palette is here and not only in Settings
 *
 * Settings has the same three choices, and that is the right place to *set them
 * up*. It is the wrong place to *try them*: the thing being blurred, tinted and
 * sourced is the title the reader is looking at, and a switch three screens away
 * that changes a page you cannot see is a guess. The palette opens this editor
 * over the live page, so the preview is the answer rather than a picture of it.
 *
 * ## The preview is a miniature of the page, not a swatch
 *
 * A blurred rectangle in a settings row tells the reader the blur radius. This
 * preview also puts the title, its poster, a chip row and a translucent card on
 * the artwork, which is the combination that decides whether the page is readable.
 * The card surface is the same translucent `surface` the real page uses, and the
 * tint sits below the scrim for the same reason it does there.
 *
 * ## State is owned by the caller
 *
 * Every control calls a callback; nothing here writes a preference. That is what
 * lets the same composable preview an unsaved arm later, and it keeps the editor
 * from needing a `ThemePreferences` of its own when the sheet already has one.
 */
@Composable
internal fun InfoAppearanceEditor(
    media: MediaNode,
    source: InfoBackdropSource,
    blur: InfoBackdropBlur,
    tint: InfoBackdropTint,
    onSelectSource: (InfoBackdropSource) -> Unit,
    onSelectBlur: (InfoBackdropBlur) -> Unit,
    onSelectTint: (InfoBackdropTint) -> Unit,
    onReset: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backdropOn = source != InfoBackdropSource.OFF

    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("info_appearance_editor"),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 16.dp, top = 10.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .bouncyPress(pressedScale = 0.9f)
                        .testTag("info_appearance_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to the title"
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Details page",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Changes are saved as you pick them",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onClose,
                    modifier = Modifier.testTag("info_appearance_done")
                ) {
                    Text("Done", fontWeight = FontWeight.Bold)
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = AppVectorIcons.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "LIVE PREVIEW",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                InfoAppearancePreview(
                    media = media,
                    source = source,
                    blur = blur,
                    tint = tint
                )

                Spacer(modifier = Modifier.height(22.dp))
                AppearanceGroup(
                    title = "Backdrop",
                    subtitle = "Which artwork the page keeps behind its cards."
                ) {
                    ChoiceRow(
                        options = InfoBackdropSource.entries.map { it.displayName },
                        selectedIndex = InfoBackdropSource.entries.indexOf(source),
                        testTagPrefix = "info_appearance_source",
                        storageKeys = InfoBackdropSource.entries.map { it.storageValue },
                        onSelect = { index -> onSelectSource(InfoBackdropSource.entries[index]) }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = when (source) {
                            InfoBackdropSource.BANNER ->
                                "The wide key visual AniList stores for the title. " +
                                    "Falls back to the poster when there is none."
                            InfoBackdropSource.COVER ->
                                "The poster itself, stretched under the whole page."
                            InfoBackdropSource.OFF ->
                                "The page stays on the app surface. The hero still " +
                                    "shows the title's artwork."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                AppearanceGroup(
                    title = "Softness",
                    subtitle = if (backdropOn) {
                        "How much detail survives the blur."
                    } else {
                        "Turn a backdrop on to choose how soft it is."
                    }
                ) {
                    ChoiceRow(
                        options = InfoBackdropBlur.entries.map { it.displayName },
                        selectedIndex = InfoBackdropBlur.entries.indexOf(blur),
                        testTagPrefix = "info_appearance_blur",
                        storageKeys = InfoBackdropBlur.entries.map { it.storageValue },
                        enabled = backdropOn,
                        onSelect = { index -> onSelectBlur(InfoBackdropBlur.entries[index]) }
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                AppearanceGroup(
                    title = "Colour tint",
                    subtitle = if (backdropOn) {
                        "Blend the active theme accent into the blur, or keep the " +
                            "cover's own palette."
                    } else {
                        "Turn a backdrop on to tint it."
                    }
                ) {
                    ChoiceRow(
                        options = InfoBackdropTint.entries.map { it.displayName },
                        selectedIndex = InfoBackdropTint.entries.indexOf(tint),
                        testTagPrefix = "info_appearance_tint",
                        storageKeys = InfoBackdropTint.entries.map { it.storageValue },
                        enabled = backdropOn,
                        onSelect = { index -> onSelectTint(InfoBackdropTint.entries[index]) }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                TextButton(
                    onClick = onReset,
                    modifier = Modifier.testTag("info_appearance_reset")
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Restore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset this page to its default")
                }

                Spacer(modifier = Modifier.height(18.dp))
                ExpressivePrimaryButton(
                    text = "Done",
                    onClick = onClose,
                    modifier = Modifier.fillMaxWidth(),
                    testTag = "info_appearance_primary_done"
                )
            }
        }
    }
}

/**
 * The miniature page: artwork, scrim, lockup and one card, in the real page's
 * order. The heights are deliberately smaller, not scaled - a poster is 64dp
 * because a preview that needs a magnifying glass is not a preview.
 */
@Composable
private fun InfoAppearancePreview(
    media: MediaNode,
    source: InfoBackdropSource,
    blur: InfoBackdropBlur,
    tint: InfoBackdropTint
) {
    val artwork = when (source) {
        InfoBackdropSource.BANNER -> media.bannerImage?.takeIf { it.isNotBlank() }
            ?: media.posterUrl
        InfoBackdropSource.COVER -> media.posterUrl
        InfoBackdropSource.OFF -> null
    }
    // A live radius rather than the enum's value: the three choices are far
    // enough apart that a snap reads as the artwork being replaced, and the
    // spring is the same one the real page uses.
    val radius by animateDpAsState(
        targetValue = blur.radiusDp.dp,
        animationSpec = ExpressiveMotion.FastSpatialDp,
        label = "info_appearance_preview_blur"
    )
    val surface = MaterialTheme.colorScheme.surface
    val onArtwork = if (artwork != null) Color.White else MaterialTheme.colorScheme.onSurface
    val coverFallback = media.posterColor.toCoverColorOrNull()
        ?: MaterialTheme.colorScheme.surfaceContainerHighest

    Surface(
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .fillMaxWidth()
            .height(408.dp)
            .testTag("info_appearance_live_preview")
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (artwork != null) {
                Crossfade(
                    targetState = artwork,
                    animationSpec = ExpressiveMotion.FastEffects,
                    label = "info_appearance_preview_artwork",
                    modifier = Modifier.fillMaxSize()
                ) { active ->
                    AsyncImage(
                        model = active,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        alignment = Alignment.TopCenter,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(radius)
                            .background(surface)
                    )
                }

                if (tint == InfoBackdropTint.TINTED) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.18f))
                    )
                }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                0f to surface.copy(alpha = 0.16f),
                                0.34f to surface.copy(alpha = 0.34f),
                                0.66f to surface.copy(alpha = 0.74f),
                                1f to surface.copy(alpha = 0.94f)
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                    surface
                                )
                            )
                        )
                )
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    PreviewChip("INFO", onArtwork)
                    PreviewChip(if (media.isManga) "MANGA" else "ANIME", onArtwork)
                }

                Column {
                    Row(verticalAlignment = Alignment.Bottom) {
                        AsyncImage(
                            model = media.posterUrl,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .width(66.dp)
                                .height(100.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(coverFallback)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = media.displayName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = onArtwork,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (media.formatLabel.isNotBlank()) {
                                    PreviewChip(media.formatLabel.uppercase(), onArtwork)
                                }
                                media.averageScore?.takeIf { it > 0 }?.let { score ->
                                    PreviewChip("$score%", onArtwork)
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = surface.copy(alpha = 0.82f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "The synopsis, the genres, the cast and your " +
                                    "progress all sit on this backdrop. Soft enough to " +
                                    "read, still the title's own colour.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PreviewChip(label: String, content: Color) {
    Surface(
        shape = ExpressiveShapes.pill,
        color = if (content == Color.White) {
            Color.Black.copy(alpha = 0.38f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        }
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = content,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/** One labelled block of choices, with the same card language as the page. */
@Composable
private fun AppearanceGroup(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}

/**
 * A wrapping-free row of single-choice chips.
 *
 * The lists here are three and two entries long, so a row is enough; a `FlowRow`
 * would wrap the third chip under the first two on a large font scale, which is
 * not wrong but makes the one-line answer to the question take two. `weight`
 * would stretch a two-option row into two huge targets, so the chips hug.
 */
@Composable
private fun ChoiceRow(
    options: List<String>,
    selectedIndex: Int,
    testTagPrefix: String,
    storageKeys: List<String>,
    enabled: Boolean = true,
    onSelect: (Int) -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        options.forEachIndexed { index, label ->
            ExpressiveStateChip(
                label = label,
                selected = index == selectedIndex,
                onClick = { if (enabled) onSelect(index) },
                minTouchTargetHeight = 48.dp,
                role = Role.RadioButton,
                onClickLabel = "Use $label",
                modifier = Modifier.testTag("${testTagPrefix}_${storageKeys[index]}")
            )
        }
    }
    if (!enabled) {
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Choose a backdrop first.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
