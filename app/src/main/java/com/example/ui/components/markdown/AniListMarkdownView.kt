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

/**
 * Pure Compose renderer for parsed AniList markdown AST.
 */
@Composable
fun AniListMarkdownView(
    nodes: List<MarkdownNode>,
    modifier: Modifier = Modifier,
    maxLines: Int = Int.MAX_VALUE
) {
    Column(
        modifier = modifier.testTag("anilist_markdown_view"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        val displayNodes = if (maxLines < Int.MAX_VALUE && nodes.size > 4) nodes.take(4) else nodes
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
                is MarkdownNode.CenteredBlock -> {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AniListMarkdownView(nodes = node.children, maxLines = maxLines)
                    }
                }
                is MarkdownNode.Divider -> HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    modifier = Modifier.padding(vertical = 4.dp)
                )
            }
        }
    }
}
