package com.anisequel.ui.components.detail

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The heading over a row or a block that is not itself a card: the cast and crew, the
 * related titles, a person's biography and appearances.
 *
 * The rows of artwork bleed to the screen edge instead of sitting in a card, and
 * artwork inside a card is artwork with a border drawn around the edge of the screen -
 * which is how the previous Specs tab framed every image it had. The heading reads as a
 * label for that row rather than as another card: an icon, a title, and nothing else.
 *
 * It lived in the Info page's talent file while the Info page was the only surface
 * with such rows. It moved down here when the person sheet needed the same heading
 * over its own two, because a second copy is how the two sheets start to disagree
 * about what a section heading looks like.
 *
 * The inset arrives on the modifier rather than being applied here, because it is
 * used two ways: inside `InfoSection`, where the page has already padded it, and
 * standalone above a `LazyRow` that bleeds to the screen edge. It used to hardcode
 * 20.dp, which silently doubled the inset in the first case.
 */
@Composable
fun SectionHeading(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            // A titled row on a long page is exactly what TalkBack's heading
            // navigation is for. Without it the title page's cast, crew, relations
            // and rankings are four more stops in a linear read rather than four
            // landmarks the reader can jump between.
            modifier = Modifier.semantics { heading() }
        )
    }
}
