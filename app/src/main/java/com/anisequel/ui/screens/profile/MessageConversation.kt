package com.anisequel.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.anisequel.data.model.MessageNode
import com.anisequel.ui.components.markdown.AniListMarkdownWithCovers
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * A message conversation, oldest at the top and newest at the bottom.
 *
 * ## Why the sheet shows more than the message that was tapped
 *
 * AniList's inbox is a list of single messages, and a conversation is what a reader
 * opens one of them to read. Rendering only the tapped row left the sheet as a
 * sender with no record: the answer went in, and the question stayed back on the
 * list. The two halves of an exchange are fetched separately, because
 * `Page.activities` has no recipient filter, and
 * [com.anisequel.ui.viewmodel.conversationMessages] interleaves them here - which
 * is the whole reason the sheet can be a thread at all.
 *
 * ## Bubbles, and why this one place uses them
 *
 * Everything else in the app is a list of rows, because everything else *is* a
 * list: a feed, a library, a followers tray. A conversation is the one thing that
 * has two sides, and a left/right alignment is the only marking that says which
 * side wrote a line without reading a name on every row. The corner is flatter on
 * the sender's edge than the other three, which is how every messenger draws the
 * tail, and the container is the theme's own `primaryContainer` - a colour the
 * scheme reserves for "this is yours", so it holds in every palette and in Material
 * You alike.
 *
 * ## The highlight
 *
 * The tapped row is the one the reader meant to open, and in a thread assembled
 * from two sources it is not necessarily the newest. It gets a rule rather than a
 * banner: a message that is marked by *looking different from itself* is easier to
 * find in a column of bubbles than one with a caption over it.
 */
@Composable
internal fun ConversationBody(
    messages: List<MessageNode>,
    viewerId: Int?,
    highlightId: Int,
    hasEarlier: Boolean,
    onLoadEarlier: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (hasEarlier) {
            // Older messages are behind a page that has not been fetched, and the
            // only honest affordance for that is one that says so and fetches it.
            // Not an auto-load on scroll: this column is not lazy, and asking for
            // every page as it is measured would spend the request budget on a
            // conversation the reader may only ever skim once.
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TextButton(
                    onClick = onLoadEarlier,
                    modifier = Modifier.testTag("message_conversation_earlier")
                ) {
                    Text("Earlier messages")
                }
            }
        }

        messages.forEach { message ->
            ConversationRow(
                message = message,
                sent = message.messengerId != null && message.messengerId == viewerId,
                highlighted = message.id == highlightId
            )
        }
    }
}

/** One bubble and its time. */
@Composable
private fun ConversationRow(message: MessageNode, sent: Boolean, highlighted: Boolean) {
    val body = message.message ?: return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = if (sent) Arrangement.End else Arrangement.Start
    ) {
        Column(
            horizontalAlignment = if (sent) Alignment.End else Alignment.Start,
            modifier = Modifier
                .widthIn(max = 460.dp)
                .testTag("message_conversation_row")
        ) {
            Box(modifier = bubbleModifier(sent, highlighted)) {
                // The same renderer the comment thread and the cards use, so a
                // message with an image, a spoiler or an AniList link in it shows
                // the image, hides the spoiler and draws the poster - the body of a
                // message is AniList Markdown, not plain text.
                AniListMarkdownWithCovers(
                    markdown = body,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message.createdAt?.let { formatConversationTime(it) }.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Normal,
                textAlign = if (sent) TextAlign.End else TextAlign.Start,
                maxLines = 1
            )
        }
    }
}

/**
 * The bubble's own surface: the colour that says whose, the corner that says which
 * way it points, and - for the message that was tapped - a rule one pixel wide.
 *
 * Pulled out of the row because the row is about a message, and this is about which
 * of the two sides wrote it. The highlight is drawn as padding rather than as a
 * border on the text: a message marked by *looking different from itself* is easier
 * to find in a column of bubbles than one with a caption over it.
 */
@Composable
private fun bubbleModifier(sent: Boolean, highlighted: Boolean): Modifier {
    val shape = RoundedCornerShape(
        topStart = if (sent) 18.dp else 6.dp,
        topEnd = if (sent) 6.dp else 18.dp,
        bottomStart = 18.dp,
        bottomEnd = 18.dp
    )
    val surface = if (sent) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    return if (highlighted) {
        Modifier
            .padding(1.dp)
            .background(MaterialTheme.colorScheme.primary, shape)
            .padding(1.dp)
            .background(surface, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    } else {
        Modifier
            .background(surface, shape)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    }
}

/**
 * The time, absolute and short.
 *
 * A conversation needs the clock time rather than the inbox's "3h": two bubbles
 * four minutes apart are the point of reading a thread, and a relative stamp on
 * every one of them says "3h ago" four times.
 */
private fun formatConversationTime(epochSeconds: Int): String =
    CONVERSATION_TIME.format(
        Instant.ofEpochSecond(epochSeconds.toLong()).atZone(ZoneId.systemDefault())
    )

private val CONVERSATION_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM, HH:mm")
