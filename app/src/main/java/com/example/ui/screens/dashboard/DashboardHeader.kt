package com.example.ui.screens.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.data.model.FilterCriteria
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveCountBadge
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.components.expressive.bouncyPress

@Composable
fun DashboardSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    maxWidth: Dp,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current

    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .testTag("anime_search_bar"),
        placeholder = { Text("Search franchise or sequel") },
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedContainerColor = MaterialTheme.colorScheme.surface,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLowest
        ),
        leadingIcon = {
            Icon(
                imageVector = AppVectorIcons.Search,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        trailingIcon = {
            AnimatedVisibility(
                visible = query.isNotBlank(),
                enter = fadeIn(ExpressiveMotion.FastEffects) +
                    scaleIn(animationSpec = ExpressiveMotion.BouncySpatial),
                exit = fadeOut(ExpressiveMotion.FastEffects) +
                    scaleOut(animationSpec = ExpressiveMotion.FastSpatial)
            ) {
                IconButton(
                    onClick = {
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        onQueryChange("")
                    },
                    modifier = Modifier.bouncyPress(pressedScale = 0.9f)
                ) {
                    Icon(
                        imageVector = AppVectorIcons.Close,
                        contentDescription = "Clear search",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    )
}

@Composable
fun DashboardSectionHeader(
    count: Int,
    filterCriteria: FilterCriteria,
    maxWidth: Dp,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .widthIn(max = maxWidth)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Missed Sequels",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            ExpressiveCountBadge(count = count)
        }
        Text(
            text = filterCriteria.sortOption.displayName,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}
