package com.example.ui.components

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.data.model.MissedSequel

@OptIn(ExperimentalMaterial3Api::class)
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
    com.example.ui.components.detail.SequelDetailSheet(
        sequel = sequel,
        sheetState = sheetState,
        onDismiss = onDismiss,
        onAddToPlanning = onAddToPlanning,
        onLoadDetail = onLoadDetail,
        isDetailLoading = isDetailLoading,
        canWriteToAniList = canWriteToAniList,
        modifier = modifier
    )
}
