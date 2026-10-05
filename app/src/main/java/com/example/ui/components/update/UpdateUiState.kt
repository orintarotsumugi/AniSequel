package com.example.ui.components.update

import com.example.data.update.UpdateManifest
import java.io.File

/** What the updater is doing, for the UI to render. */
sealed interface UpdateUiState {
    /** Nothing has happened yet - the state a freshly opened tab sits in. */
    data object Idle : UpdateUiState

    data object Checking : UpdateUiState

    data class UpToDate(val installedVersion: String) : UpdateUiState

    data class Available(val manifest: UpdateManifest) : UpdateUiState

    data class Downloading(
        val manifest: UpdateManifest,
        val bytesRead: Long,
        val totalBytes: Long
    ) : UpdateUiState {
        /** 0f..1f, or null while the total is still unknown. */
        val progress: Float?
            get() = if (totalBytes > 0) {
                (bytesRead.toFloat() / totalBytes).coerceIn(0f, 1f)
            } else {
                null
            }
    }

    data class ReadyToInstall(val file: File, val manifest: UpdateManifest) : UpdateUiState

    data class Failed(val message: String) : UpdateUiState

    /**
     * The APK is downloaded but the app is not allowed to open the installer.
     *
     * A distinct state rather than an error, because the user has not done
     * anything wrong and the fix is one tap into Settings.
     *
     * [sentToSettings] records that the Settings hand-off has already been
     * offered for *this* download. It is what stops the row re-offering the
     * same button every time the user comes back without having granted it, and
     * what lets the copy drop from an instruction to a plain reminder.
     */
    data class NeedsInstallPermission(
        val file: File,
        val manifest: UpdateManifest,
        val sentToSettings: Boolean = false
    ) : UpdateUiState
}