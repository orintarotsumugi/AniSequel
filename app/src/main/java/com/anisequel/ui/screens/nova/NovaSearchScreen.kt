package com.anisequel.ui.screens.nova

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.anisequel.data.model.MediaNode
import com.anisequel.data.model.displayName
import com.anisequel.data.model.isManga
import com.anisequel.data.model.isOnPlanningList
import com.anisequel.data.model.isOnWatchingList
import com.anisequel.data.model.posterColor
import com.anisequel.data.model.posterUrl
import com.anisequel.data.repository.AniListRepository
import com.anisequel.data.repository.QuickAddPreferences
import com.anisequel.ui.components.AppVectorIcons
import com.anisequel.ui.components.ShimmerBox
import com.anisequel.ui.components.cards.QuickAddPlanningButton
import com.anisequel.ui.components.cards.toCoverColorOrNull
import com.anisequel.ui.components.expressive.ExpressiveShapes
import com.anisequel.ui.components.expressive.bouncyPress
import com.anisequel.ui.components.nova.NovaBackdrop
import com.anisequel.ui.components.nova.NovaSurface
import com.anisequel.ui.components.rememberShimmerPhase
import com.anisequel.ui.navigation.mainBarClearance
import com.anisequel.ui.viewmodel.QuickAddStatus
import com.anisequel.ui.viewmodel.quickAddKey
import com.anisequel.ui.viewmodel.rememberPlanningAddHost
import kotlinx.coroutines.delay

/** How long the field waits after a keystroke before asking AniList. */
private const val SEARCH_DEBOUNCE_MILLIS = 350L

/** Two characters, because one is a prefix rather than a question. */
private const val SEARCH_MIN_LENGTH = 2

/**
 * The catalogue search: AniList's whole library, not the reader's list.
 *
 * ## Why this is a page and not a field on Home
 *
 * The dashboard's search narrows the titles the app has already found for you,
 * which is a different question from "does this show exist and what is it
 * called". Mixing them put a field on Home that silently changed meaning
 * depending on which list happened to be loaded, and it is the reason this page
 * has its own tab rather than a shortcut.
 *
 * ## No ViewModel
 *
 * The page owns two pieces of state - what was typed and what came back - and
 * nothing else in the app reads either. A ViewModel here would be a factory, a
 * lifecycle and a key for no second reader; the repository is the only thing
 * that outlives the composition, and it is handed in.
 *
 * The search is debounced and cancelled by `LaunchedEffect(query)`: every
 * keystroke restarts the coroutine, so only the last one in a burst reaches the
 * network. AniList allows roughly thirty requests a minute, and a request per
 * character is how the field would spend them before the reader finished typing.
 *
 * A title that is already on the reader's list draws the shared badge rather
 * than a button - the same rule every other card in the app follows, and the
 * reason the query asks for `mediaListEntry`.
 */
@Composable
fun NovaSearchScreen(
    repository: AniListRepository,
    canWrite: Boolean,
    quickAddPreferences: QuickAddPreferences,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val planningAdd = rememberPlanningAddHost(repository = repository, canWrite = canWrite)
    val statuses by planningAdd.statuses.collectAsState()
    val quickAdd by quickAddPreferences.settings.collectAsState(
        initial = QuickAddPreferences.QuickAddSettings()
    )

    var query by rememberSaveable { mutableStateOf("") }
    var results by remember { mutableStateOf<List<MediaNode>>(emptyList()) }
    var searchedTerm by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(query) {
        val term = query.trim()
        if (term.length < SEARCH_MIN_LENGTH) {
            results = emptyList()
            searchedTerm = ""
            isSearching = false
            error = null
            return@LaunchedEffect
        }

        isSearching = true
        delay(SEARCH_DEBOUNCE_MILLIS)
        repository.searchMedia(term)
            .onSuccess { found ->
                results = found
                searchedTerm = term
                error = null
            }
            .onFailure { failure ->
                results = emptyList()
                searchedTerm = term
                error = failure.message ?: "AniList did not answer."
            }
        isSearching = false
    }

    NovaBackdrop(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.statusBars)
                .navigationBarsPadding()
                .testTag("nova_search_screen")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, end = 20.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .bouncyPress(pressedScale = 0.9f)
                        .testTag("nova_search_back")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back"
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Search",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Any anime or manga on AniList",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .testTag("nova_search_input"),
                placeholder = {
                    Text(
                        text = "Search the catalogue",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                singleLine = true,
                shape = ExpressiveShapes.pill,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                ),
                leadingIcon = {
                    Icon(
                        imageVector = AppVectorIcons.Search,
                        contentDescription = null,
                        modifier = Modifier.size(19.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(
                            onClick = { query = "" },
                            modifier = Modifier.testTag("nova_search_clear")
                        ) {
                            Icon(
                                imageVector = AppVectorIcons.Close,
                                contentDescription = "Clear search",
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    query.trim().length < SEARCH_MIN_LENGTH -> SearchNote(
                        text = "Type at least $SEARCH_MIN_LENGTH characters to search " +
                            "every anime and manga AniList knows about. Nothing here " +
                            "changes your list until you add it."
                    )

                    isSearching -> SearchSkeleton()

                    error != null -> SearchNote(
                        text = "Couldn't search AniList - $error"
                    )

                    results.isEmpty() -> SearchNote(
                        text = "No title matches \"$searchedTerm\"."
                    )

                    else -> LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 20.dp,
                            end = 20.dp,
                            top = 4.dp,
                            bottom = mainBarClearance() + 20.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            items = results,
                            key = { media -> "${media.type}_${media.id}" },
                            contentType = { "search_result" }
                        ) { media ->
                            SearchResultRow(
                                media = media,
                                status = statuses[quickAddKey(media)],
                                holdDurationSeconds = quickAdd.holdDurationSeconds,
                                swipeEnabled = quickAdd.swipeEnabled,
                                onAddToPlanning = { planningAdd.onAddToPlanning(media) },
                                onAddToWatching = { planningAdd.onAddToWatching(media) }
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One result: the cover, what it is, and the shared quick-add control. */
@Composable
private fun SearchResultRow(
    media: MediaNode,
    status: QuickAddStatus?,
    holdDurationSeconds: Int,
    swipeEnabled: Boolean,
    onAddToPlanning: () -> Unit,
    onAddToWatching: () -> Unit
) {
    NovaSurface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("nova_search_result_${media.id}"),
        tonalElevation = true
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .width(62.dp)
                    .height(92.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(media.posterColor.toCoverColorOrNull() ?: Color.Transparent)
            ) {
                AsyncImage(
                    model = media.posterUrl,
                    contentDescription = media.displayName,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = media.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = listOfNotNull(
                        media.type?.lowercase()?.replaceFirstChar { it.uppercase() },
                        media.format?.takeIf { it.isNotBlank() },
                        media.averageScore?.takeIf { it > 0 }?.let { "★ $it" },
                        media.seasonYear?.toString()
                    ).joinToString("  ·  "),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))
                QuickAddPlanningButton(
                    mediaId = media.id,
                    isAddedToPlanning = media.isOnPlanningList() ||
                        status?.isAddedToPlanning == true,
                    isAddedToWatching = media.isOnWatchingList() ||
                        status?.isAddedToWatching == true,
                    isBusy = status?.isBusy == true,
                    onAddToPlanning = onAddToPlanning,
                    onAddToWatching = onAddToWatching,
                    holdDurationSeconds = holdDurationSeconds,
                    swipeEnabled = swipeEnabled,
                    modifier = Modifier.fillMaxWidth()
                )
                status?.error?.let { message ->
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

/** A sentence where the results would be, with no fake cards around it. */
@Composable
private fun SearchNote(text: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The shape of the results that are on the wire, drawn with the app's shimmer. */
@Composable
private fun SearchSkeleton() {
    val phase = rememberShimmerPhase()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        repeat(4) {
            ShimmerBox(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(112.dp),
                shape = RoundedCornerShape(26.dp),
                phase = phase
            )
        }
    }
}
