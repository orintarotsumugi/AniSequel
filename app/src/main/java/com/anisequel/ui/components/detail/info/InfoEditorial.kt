package com.anisequel.ui.components.detail.info

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * A section heading with the small accent bar the title page's newer sections use.
 *
 * ## Why this exists beside [com.anisequel.ui.components.detail.SectionHeading]
 *
 * That one is an icon-and-label for a *row* of artwork that bleeds to the screen
 * edge. The reference for the redesigned page heads Categories, External Links and
 * Openings & Endings with a filled accent bar and a sub-line ("2 openings · 2
 * endings"), sitting directly on the page rather than inside a card. Both are
 * headings and both carry the heading semantic; only the dressing differs, which is
 * why this is a second composable rather than a parameter on the first.
 *
 * The bar is a plain rounded `Box`, not a `RoundedPolygon`: the shapes rule in
 * AGENTS.md applies to capsules, and a stretched polygon at 3dp wide is exactly the
 * ellipse it warns about.
 */
@Composable
fun InfoAccentHeading(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(22.dp)
                .background(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(percent = 50)
                )
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        trailing?.invoke()
    }
}

