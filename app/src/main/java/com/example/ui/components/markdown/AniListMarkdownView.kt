package com.example.ui.components.markdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.MediaCover
import com.example.data.model.MediaCoverKey

/**
 * How many top-level blocks a *collapsed* render keeps.
 *
 * Public because the profile's bio card has to decide whether to offer a "Show
 * more" toggle at all, and it can only answer that from the same number the
 * renderer clips on. It used to hardcode a character count while this file
 * hardcoded `4`, so the two could disagree and the toggle would appear for a bio
 * that had nothing hidden behind it.
 */
const val MARKDOWN_COLLAPSED_BLOCK_LIMIT = 4

/**
 * Pure Compose renderer for parsed AniList markdown AST.
 *
 * [maxLines] below `Int.MAX_VALUE` is the collapsed signal. Alongside the per-text
 * line clamp it drops whole blocks past [MARKDOWN_COLLAPSED_BLOCK_LIMIT]: a
 * single six-line paragraph cannot be cut at a block boundary, but a bio of six
 * one-line paragraphs would otherwise cost a collapsed card the same height as
 * an expanded one. Callers that collapse should fade the bottom edge, because a
 * dropped block is otherwise indistinguishable from the author never writing it.
 *
 * [mediaCovers] is media key to cover for the AniList preview cards. Passed in
 * rather than fetched from here so a document's worth of links costs **one**
 * request: a bio is a dozen preview cards, and a fetch per card would spend a
 * dozen of AniList's 30-a-minute budget before the reader had scrolled. A miss
 * draws the badge alone, which is what these cards looked like before covers
 * existed.
 */
@Composable
fun AniListMarkdownView(
    nodes: List<MarkdownNode>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE,
    mediaCovers: Map<MediaCoverKey, MediaCover> = emptyMap()
) {
    Column(
        modifier = modifier.testTag("anilist_markdown_view"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val displayNodes = if (maxLines < Int.MAX_VALUE && nodes.size > MARKDOWN_COLLAPSED_BLOCK_LIMIT) {
            nodes.take(MARKDOWN_COLLAPSED_BLOCK_LIMIT)
        } else {
            nodes
        }
        displayNodes.forEach { node ->
            when (node) {
                is MarkdownNode.Header -> RenderHeader(node)
                is MarkdownNode.Paragraph -> RenderParagraph(node, maxLines)
                is MarkdownNode.Blockquote -> RenderBlockquote(node)
                is MarkdownNode.CodeBlock -> RenderCodeBlock(node)
                is MarkdownNode.ListBlock -> RenderList(node)
                is MarkdownNode.CheckListBlock -> RenderCheckList(node)
                is MarkdownNode.ImageBlock -> RenderImage(node)
                is MarkdownNode.VideoBlock -> RenderVideo(node)
                is MarkdownNode.SpoilerBlock -> RenderSpoilerBlock(node)
                is MarkdownNode.TableBlock -> RenderTable(node)
                is MarkdownNode.CenteredBlock -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AniListMarkdownView(nodes = node.children, maxLines = maxLines, mediaCovers = mediaCovers)
                    }
                }
                is MarkdownNode.AniListPreviewBlock -> RenderAniListPreview(
                    node,
                    // One lookup by key, so an anime and a manga that happen to
                    // share an id each draw their own poster.
                    cover = node.mediaCoverKeyOrNull()?.let { mediaCovers[it] }
                )
                is MarkdownNode.Divider -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
