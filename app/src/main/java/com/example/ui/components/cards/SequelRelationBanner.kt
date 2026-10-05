package com.example.ui.components.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons

@Composable
fun SequelRelationBanner(
    sequel: MissedSequel,
    modifier: Modifier = Modifier
) {
    val relationColor = if (sequel.isEarlierInFranchise) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.primary
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.extraSmall)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (sequel.parentCoverUrl != null) {
            AsyncImage(
                model = sequel.parentCoverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(22.dp)
                    .clip(MaterialTheme.shapes.extraSmall)
                    .background(
                        sequel.parentCoverColor.toCoverColorOrNull()
                            ?: MaterialTheme.colorScheme.surfaceContainerHighest
                    )
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Icon(
            imageVector = if (sequel.isEarlierInFranchise) {
                AppVectorIcons.FranchiseBranch
            } else {
                AppVectorIcons.SequelJump
            },
            contentDescription = null,
            tint = relationColor,
            modifier = Modifier.size(15.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = sequel.relationLabel,
            style = MaterialTheme.typography.labelMedium,
            color = relationColor,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = sequel.parentTitle,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}
