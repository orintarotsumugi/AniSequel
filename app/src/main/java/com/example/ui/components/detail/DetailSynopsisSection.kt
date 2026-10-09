package com.example.ui.components.detail

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.markdown.AniListMarkdownParser
import com.example.ui.components.markdown.AniListMarkdownView

/**
 * Synopsis section powered by AniList Markdown parsing.
 *
 * Renders rich inline text styling (bold, italic, links, spoilers, paragraphs)
 * with expandable reading mode.
 */
@Composable
fun DetailSynopsisSection(
    sequel: MissedSequel,
    isDetailLoading: Boolean,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember(sequel.sequelId) { mutableStateOf(false) }
    val desc = sequel.description
    val parsedNodes = remember(desc) {
        if (!desc.isNullOrBlank()) {
            AniListMarkdownParser.parse(desc)
        } else emptyList()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = AppVectorIcons.SourceBook,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(17.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Synopsis",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        if (isDetailLoading && desc.isNullOrBlank()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(vertical = 4.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "Loading synopsis from AniList...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else if (desc.isNullOrBlank()) {
            Text(
                text = "No synopsis description provided.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                AniListMarkdownView(
                    nodes = parsedNodes,
                    maxLines = if (isExpanded) Int.MAX_VALUE else 4
                )

                if (desc.length > 160 || parsedNodes.size > 2) {
                    TextButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        Text(if (isExpanded) "Show less" else "Read full synopsis")
                    }
                }
            }
        }
    }
}
