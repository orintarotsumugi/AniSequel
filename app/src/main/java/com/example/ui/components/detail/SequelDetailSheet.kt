package com.example.ui.components.detail

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.ui.components.cards.toCoverColorOrNull
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.MissedSequel
import com.example.ui.components.AppVectorIcons
import com.example.ui.components.expressive.ExpressiveTabBar
import com.example.ui.components.expressive.ExpressiveMotion
import com.example.ui.theme.AniSequelTheme

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SequelDetailSheet(
    sequel: MissedSequel?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onAddToPlanning: (MissedSequel) -> Unit,
    onLoadDetail: (MissedSequel) -> Unit,
    isDetailLoading: Boolean,
    canWriteToAniList: Boolean,
    modifier: Modifier = Modifier
) {
    if (sequel == null) return
    val context = LocalContext.current
    var isDescriptionExpanded by remember(sequel.sequelId) { mutableStateOf(false) }
    var detailTab by rememberSaveable(sequel.sequelId) { mutableIntStateOf(0) }
    val statusColors = AniSequelTheme.statusColors

    LaunchedEffect(sequel.sequelId) {
        onLoadDetail(sequel)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier.testTag("sequel_detail_sheet"),
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
        ) {
            DetailHeader(sequel = sequel)

            Spacer(modifier = Modifier.height(12.dp))

            ExpressiveTabBar(
                tabs = listOf("Story & Gaps", "Specs & Info"),
                selectedIndex = detailTab,
                onSelect = { detailTab = it },
                modifier = Modifier.padding(horizontal = 0.dp)
            )

            Spacer(modifier = Modifier.height(12.dp))

            AnimatedContent(
                targetState = detailTab,
                transitionSpec = {
                    fadeIn(ExpressiveMotion.FastEffects) togetherWith
                        fadeOut(ExpressiveMotion.FastEffects)
                },
                label = "detail_tab_switch"
            ) { tab ->
                if (tab == 1) {
                    DetailSpecsView(
                        sequel = sequel,
                        isDetailLoading = isDetailLoading
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = AppVectorIcons.CheckCircle,
                                    contentDescription = null,
                                    tint = statusColors.success,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                // The watched entry this one hangs off, with its own artwork. This is the
                                // relationship that explains why the entry is on the
                                // screen at all, so it gets an image rather than a
                                // bare title - and it costs nothing, because the list
                                // query already returned the parent's cover.
                                if (sequel.parentCoverUrl != null) {
                                    AsyncImage(
                                        model = sequel.parentCoverUrl,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(44.dp)
                                            .clip(RoundedCornerShape(10.dp))
                                            .background(
                                                sequel.parentCoverColor.toCoverColorOrNull()
                                                    ?: MaterialTheme.colorScheme.surfaceContainerHighest
                                            )
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                }

                                Column {
                                    Text(
                                        text = sequel.relationLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = sequel.parentTitle,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        DetailInfoGrid(sequel = sequel)

                        if (sequel.genres.isNotEmpty()) {
                            Text(
                                text = "Genres",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                sequel.genres.forEach { genre ->
                                    Surface(
                                        shape = MaterialTheme.shapes.extraSmall,
                                        color = MaterialTheme.colorScheme.surfaceContainerHigh
                                    ) {
                                        Text(
                                            text = genre,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }

                        val desc = sequel.description
                        if (isDetailLoading && desc.isNullOrBlank()) {
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Loading synopsis from AniList...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else if (!desc.isNullOrBlank()) {
                            Text(
                                text = "Synopsis",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = desc,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isDescriptionExpanded) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (desc.length > 160) {
                                TextButton(
                                    onClick = { isDescriptionExpanded = !isDescriptionExpanded },
                                    modifier = Modifier.padding(top = 2.dp)
                                ) {
                                    Text(if (isDescriptionExpanded) "Show less" else "Read more")
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(16.dp))

            DetailActionRow(
                sequel = sequel,
                canWriteToAniList = canWriteToAniList,
                onAddToPlanning = onAddToPlanning,
                context = context
            )
        }
    }
}
