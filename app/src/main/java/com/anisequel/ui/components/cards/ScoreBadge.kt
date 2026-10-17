package com.anisequel.ui.components.cards

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.anisequel.ui.components.expressive.ExpressiveShapes

/**
 * AniList's community score over a poster, as a capsule.
 *
 * One component rather than the three private copies this replaces - the title page's
 * relation cards, a person's appearance cards, and a studio's title cards - because the
 * copies had already drifted: three paddings (7/3, 6/2, 5/2) and two shapes, one of
 * them unlike either of the others. All three are posters a reader can see within two
 * taps of each other, so the difference read as unreliability rather than as design.
 *
 * Deliberately not tinted with the accent colour the role pill uses: the pill labels a
 * row of artwork and this is a number off AniList, and tinting both the same way would
 * make the score read as another label rather than as a value.
 *
 * The scrim is `Color.Black` and not a theme colour because the artwork underneath it
 * is arbitrary: it has to darken under every poster, and the alpha is what keeps the
 * poster visible through it.
 */
@Composable
fun ScoreBadge(
    score: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = ExpressiveShapes.pill,
        color = Color.Black.copy(alpha = 0.62f),
        modifier = modifier
    ) {
        Text(
            text = "$score%",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
        )
    }
}
