package com.example.ui.components.markdown

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.ui.components.openExternalUrl

/**
 * Pure Compose renderer for Markdown tables in AniList reviews, forum posts, and user bios.
 */
@Composable
fun RenderTable(table: MarkdownNode.TableBlock) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

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
                .horizontalScroll(scrollState)
        ) {
            // Header Row
            if (table.headers.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.85f))
                        .padding(vertical = 4.dp)
                ) {
                    table.headers.forEach { headerTokens ->
                        val annotated = buildInlineAnnotatedString(headerTokens, onLinkClick = { openExternalUrl(context, it) })
                        Box(
                            modifier = Modifier
                                .widthIn(min = 96.dp)
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            // Data Rows
            table.rows.forEachIndexed { rowIndex, rowCells ->
                val rowBg = if (rowIndex % 2 == 1) {
                    MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.35f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                }

                Row(
                    modifier = Modifier
                        .background(rowBg)
                        .border(
                            width = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                        .padding(vertical = 2.dp)
                ) {
                    rowCells.forEach { cellTokens ->
                        val annotated = buildInlineAnnotatedString(cellTokens, onLinkClick = { openExternalUrl(context, it) })
                        Box(
                            modifier = Modifier
                                .widthIn(min = 96.dp)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = annotated,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Checks if lines starting at [startIndex] represent a Markdown table and parses it.
 * Returns the parsed [MarkdownNode.TableBlock] and the next line index, or null if not a table.
 */
fun tryParseTable(
    lines: List<String>,
    startIndex: Int,
    inlineParser: (String) -> List<InlineToken>
): Pair<MarkdownNode.TableBlock, Int>? {
    if (startIndex + 1 >= lines.size) return null
    val headerLine = lines[startIndex].trim()
    val separatorLine = lines[startIndex + 1].trim()

    if (!headerLine.contains('|') || !separatorLine.contains('|')) return null
    val separatorClean = separatorLine.replace("|", "").replace(":", "").replace("-", "").trim()
    if (separatorClean.isNotEmpty()) return null

    fun splitRow(line: String): List<String> {
        val trimmed = line.trim()
        val stripped = trimmed.removePrefix("|").removeSuffix("|")
        return stripped.split("|").map { it.trim() }
    }

    val headerCols = splitRow(headerLine)
    if (headerCols.isEmpty() || headerCols.all { it.isEmpty() }) return null

    val headers = headerCols.map { inlineParser(it) }
    val rows = mutableListOf<List<List<InlineToken>>>()

    var i = startIndex + 2
    while (i < lines.size) {
        val rowLine = lines[i].trim()
        if (rowLine.isBlank() || !rowLine.contains('|')) break
        val rowCols = splitRow(rowLine)
        val cells = rowCols.map { inlineParser(it) }
        rows.add(cells)
        i++
    }

    return Pair(MarkdownNode.TableBlock(headers = headers, rows = rows), i)
}

