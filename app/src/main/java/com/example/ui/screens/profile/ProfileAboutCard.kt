package com.example.ui.screens.profile

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.bouncyPress
import com.example.ui.components.markdown.AniListMarkdownParser
import com.example.ui.components.markdown.AniListMarkdownView

/**
 * The person's own bio, rendered with expressive AniList Markdown support and an optional
 * expand toggle for long bios to maintain comfortable scrolling.
 */
@Composable
fun AboutCard(
    about: String,
    modifier: Modifier = Modifier,
    maxWidth: Dp = ProfileMaxContentWidth
) {
    val nodes = remember(about) { AniListMarkdownParser.parse(about) }
    var expanded by rememberSaveable { mutableStateOf(false) }
    val isLongBio = about.length > 280

    Card(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("profile_about_card"),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = CardDefaults.outlinedCardBorder()
    ) {
        Column(
            modifier = Modifier
                .padding(18.dp)
                .animateContentSize(animationSpec = ExpressiveMotion.FastSpatialSize)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = AppVectorIcons.ProfileAbout,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "About",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f)
                )
                if (isLongBio) {
                    Text(
                        text = if (expanded) "Show less" else "Show more",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .bouncyPress()
                            .clickable { expanded = !expanded }
                            .padding(4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            AniListMarkdownView(
                nodes = nodes,
                maxLines = if (isLongBio && !expanded) 6 else Int.MAX_VALUE
            )
        }
    }
}

@Composable
internal fun NoBioNote() {
    Text(
        text = "No bio on AniList",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .widthIn(max = ProfileMaxContentWidth)
            .fillMaxWidth()
            .padding(horizontal = 18.dp, vertical = 8.dp)
            .testTag("profile_no_bio")
    )
}

private val STRIP_LINK_REGEX = Regex("""\[([^\]]+)]\([^)]*\)""")
private val STRIP_IMAGE_REGEX = Regex("""!\[[^\]]*]\([^)]*\)""")
private val STRIP_HEADER_REGEX = Regex("""^[ \t]{0,3}#{1,6}[ \t]*""", RegexOption.MULTILINE)
private val STRIP_BLOCKQUOTE_REGEX = Regex("""^[ \t]{0,3}>[ \t]?""")
private val STRIP_BOLD_REGEX = Regex("""(\*\*|__)(.+?)\1""")
private val STRIP_ITALIC_REGEX = Regex("""(\*|_)(.+?)\1""")
private val STRIP_LIST_REGEX = Regex("""^[ \t]{0,3}[-*+][ \t]+""", RegexOption.MULTILINE)
private val STRIP_CODE_REGEX = Regex("""`([^`]+)`""")
private val STRIP_NEWLINES_REGEX = Regex("""\n{3,}""")

/**
 * Reduce Markdown to plain text.
 */
internal fun stripMarkdown(source: String): String = source
    .replace(STRIP_LINK_REGEX, "$1")
    .replace(STRIP_IMAGE_REGEX, "")
    .replace(STRIP_HEADER_REGEX, "")
    .replace(STRIP_BLOCKQUOTE_REGEX, "")
    .replace(STRIP_BOLD_REGEX, "$2")
    .replace(STRIP_ITALIC_REGEX, "$2")
    .replace(STRIP_LIST_REGEX, "")
    .replace(STRIP_CODE_REGEX, "$1")
    .replace(STRIP_NEWLINES_REGEX, "\n\n")
    .trim()
