package com.example.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.MissedSequel
import com.example.ui.components.detail.AnimeDetailSubject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SequelDetailSheet(
    subject: AnimeDetailSubject?,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onAddToPlanning: (MissedSequel) -> Unit,
    onLoadDetail: (Int) -> Unit,
    isDetailLoading: Boolean,
    canWriteToAniList: Boolean,
    modifier: Modifier = Modifier,
    onAddToWatching: (MissedSequel) -> Unit = {},
    holdDurationSeconds: Int = 3,
    swipeEnabled: Boolean = true
) {
    com.example.ui.components.detail.SequelDetailSheet(
        subject = subject,
        sheetState = sheetState,
        onDismiss = onDismiss,
        onAddToPlanning = onAddToPlanning,
        onLoadDetail = onLoadDetail,
        isDetailLoading = isDetailLoading,
        canWriteToAniList = canWriteToAniList,
        modifier = modifier,
        onAddToWatching = onAddToWatching,
        holdDurationSeconds = holdDurationSeconds,
        swipeEnabled = swipeEnabled
    )
}
